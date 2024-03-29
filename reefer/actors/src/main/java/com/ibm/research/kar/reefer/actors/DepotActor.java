/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.*;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.ReeferDTO;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.json.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This actor manages reefer inventory allocating reefers to new orders
 * and releasing reefers back to inventory when an order is delivered.
 * It maintains its inventory in a global array keyed with reefer ids.
 */
@Actor
public class DepotActor extends BaseActor {
    private ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
    private static boolean ALLOCATION_FAILED=true;
    // global array to hold reefers
    private ReeferDTO[] reeferMasterInventory = null;

    private int bookedTotalCount = 0;
    private Map<Integer, Integer> onMaintenanceMap = new ConcurrentHashMap<>();
    // contains Order-Reefers mapping needed to reduce latency of calls
    private Map<String, Set<String>> order2ReeferMap = new HashMap<>();
    private JsonValue totalReeferInventory = Json.createValue(0);
    private JsonValue depotSize;
    private JsonValue currentInventorySize;
    private  Instant currentDate = null;
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(DepotActor.class.getName());
    private InventoryConfig ic;
    @Activate
    public void activate() {
        long t = System.currentTimeMillis();
        long t2 = 0, t3 = 0, t4 = 0, t5 = 0, t6 = 0;
        try {
            // fetch actor state from Kar storage
            Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
            t2 = System.currentTimeMillis();

            if (!state.isEmpty()) {

                if (state.containsKey(Constants.REEFER_METRICS_KEY)) {
                    String reeferMetrics = (((JsonString) state.get(Constants.REEFER_METRICS_KEY)).getString());
                    String[] values = reeferMetrics.split(":");
                    logger.info("DepotActor.activate() " + getId() + "............. restored metrics:" + reeferMetrics);
                    bookedTotalCount = Integer.parseInt(values[0].trim());
                    totalReeferInventory = Json.createValue(Integer.parseInt(values[4].trim()));
                    currentInventorySize = Json.createValue(Integer.parseInt(values[5].trim()));
                    depotSize = Json.createValue(Integer.parseInt(values[6].trim()));
                }

                if (((JsonNumber) totalReeferInventory).intValue() > 0) {
                    restoreInventoryConfig();
                    restoreReeferInventory(state);
                    restoreOrderToReefersMap();
                    restoreOnMaintenanceMap(state);
                }

            } else {
                t3 = System.currentTimeMillis();
                ic = getReeferInventory();
                saveInventoryConfig(ic);
                t4 = System.currentTimeMillis();
                initMasterInventory(ic);
                t5 = System.currentTimeMillis();
                saveMetrics();
                t6 = System.currentTimeMillis();
            }
        } catch (Throwable e) {
            logger.log(Level.SEVERE,"DepotActor.activate()", e);
        }
        logger.info("DepotActor.activate " + getId() + "- total time:" + (System.currentTimeMillis() - t) + " Fetch All State:" + (t2 - t) + " fetch inv:" + (t4 - t3) + " init:" + (t5 - t4) + " save:" + (t6 - t5));

    }
    private void saveInventoryConfig(InventoryConfig inventoryConfig) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.TOTAL_REEFER_COUNT_KEY, inventoryConfig.getGlobalInventorySize()).
                add(Constants.DEPOT_SIZE_KEY, inventoryConfig.getDepotSize());
        JsonArrayBuilder jab = Json.createArrayBuilder();
        JsonObjectBuilder shardBuilder = Json.createObjectBuilder();
        if ( inventoryConfig.getShards() != null) {
            for( Shard shard : inventoryConfig.getShards()) {
                shardBuilder.add("reefer-id-lower-bound", Json.createValue(shard.getLowerBound())).
                        add("reefer-id-upper-bound", Json.createValue(shard.getUpperBound()) );
                jab.add(shardBuilder);
            }
            job.add(Constants.SHARDS_KEY, jab);
        }
        Kar.Actors.State.set(this, Constants.DEPOT_KEY, job.build());
    }
    private void restoreInventoryConfig() {
        JsonObject inventoryJson = Kar.Actors.State.get(this, Constants.DEPOT_KEY).asJsonObject();
        JsonNumber depotSize = inventoryJson.getJsonNumber(Constants.DEPOT_SIZE_KEY);
        JsonNumber globalReeferCount = inventoryJson.getJsonNumber(Constants.TOTAL_REEFER_COUNT_KEY);
        ic = new InventoryConfig(depotSize.intValue(), globalReeferCount.intValue());

    }
    private void restoreReeferInventory(Map<String, JsonValue> state)  {
        long t = System.currentTimeMillis();
        JsonValue jv2 = state.get(Constants.REEFER_MAP_KEY);
        Map<String, JsonValue> reeferInventory = jv2.asJsonObject();

        if (logger.isLoggable(Level.INFO)) {
            logger.info("DepotActor.restoreReeferInventory() " + getId() + "- Fetched size of the reefer inventory:"
                    + reeferInventory.size());
        }
        // allocate reefer array which is used to allocate/deallocate reefers
        reeferMasterInventory = new ReeferDTO[((JsonNumber) totalReeferInventory).intValue()];
        for (Map.Entry<String, JsonValue> entry : reeferInventory.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            try {
                JsonObject jo = entry.getValue().asJsonObject();
                reeferMasterInventory[jo.getInt(Constants.REEFER_ID_KEY)] = jsonObjectToReeferDTO(jo);
            } catch (Exception e) {
                logger.log(Level.SEVERE,"DepotActor.restoreReeferInventory() ", e);
            }
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.info("DepotActor.restoreReeferInventory - " + getId() + " inventory size:" + reeferInventory.size() +
                    "  completed in .........." + (System.currentTimeMillis() - t));
        }
    }

    private void restoreOrderToReefersMap() {
        long t = System.currentTimeMillis();
        // restore in-memory cache which manages order-reefers association.
        // with the cache its fast to check for idempotence when handling reefer booking
        //
        order2ReeferMap = Arrays.stream(reeferMasterInventory).filter(Objects::nonNull).
                collect(Collectors.groupingBy(ReeferDTO::getOrderId, Collectors.mapping(r -> String.valueOf(r.getId()), Collectors.toSet())));
        logger.info("DepotActor.restoreOrderToReefersMap - " + getId() + "completed in .........." + (System.currentTimeMillis() - t));
    }

    private void restoreOnMaintenanceMap(Map<String, JsonValue> state) {
        long t = System.currentTimeMillis();
        onMaintenanceMap = Arrays.stream(reeferMasterInventory).
                filter(Objects::nonNull).
                filter(reefer -> reefer.getState().equals(ReeferState.State.MAINTENANCE)).
                collect(Collectors.toMap(ReeferDTO::getId, ReeferDTO::getId));
        logger.info("DepotActor.restoreOnMaintenanceMap - " + getId() + "completed in .........." + (System.currentTimeMillis() - t));
    }

    @Remote
    public JsonObject getOrders() {
        Set<String> orders = order2ReeferMap.keySet().stream().collect(Collectors.toSet());

        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).
                add(Constants.ORDERS_KEY, Json.createArrayBuilder(orders).build())
                .build();
    }

    /**
     * Called to get current reefer counts.
     *
     * @param message
     * @return - reefer counts
     */
    @Remote
    public JsonObject getStats(JsonObject message) {
        return getReeferStats();
    }

    /**
     * Release all reefers whose release date matches today's date
     *
     * @param message
     * @return
     */
    @Remote
    public void releaseReefersFromMaintenance(JsonObject message) {
        try {
            if (!onMaintenanceMap.isEmpty()) {
                // get current date. Its a date that the simulator advances at regular intervals.
                currentDate = Instant.parse(message.getString(Constants.DATE_KEY));
                List<String> reefers2Remove = getReefersToRemoveFromMaintenance(currentDate);
                List<ReeferDTO> updateList = new LinkedList<>();
                for (String reeferId : reefers2Remove) {
                    onMaintenanceMap.remove(Integer.parseInt(reeferId));
                    reeferMasterInventory[Integer.parseInt(reeferId)].reset();
                    updateList.add(reeferMasterInventory[Integer.parseInt(reeferId)]);
                }
                updateStore( Collections.emptyMap(), reeferMap(updateList));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DepotActor.releaseReefersfromMaintenance() - Error ", e);
            throw e;
        }

    }

    private List<String> getReefersToRemoveFromMaintenance(Instant today) {
        List<String> reefers2Remove = new ArrayList<>();
        Iterator<Integer> it = onMaintenanceMap.values().iterator();
        while (it.hasNext()) {
            ReeferDTO reefer = reeferMasterInventory[it.next()];
            // release reefer from maintenance if today matches reefer's assigned release date
            if (reefer != null && reefer.releaseFromMaintenanceToday(today)) {
                reefer.reset();
                reefers2Remove.add(String.valueOf(reefer.getId()));
            }
        }
        return reefers2Remove;
    }

    /**
     * Called when a ship departs its origin port with reefers aboard.
     * Changes state of voyage reefers to INTRANSIT and updates accounting.
     * It increments inTransitTotalCount and decrements bookedTotalCount by
     * the same amount.
     *
     * @param message - includes voyage id which just departed
     * @return
     */
    @Remote
    public void voyageReefersDeparted(JsonObject message) {
        try {
            String voyageId = message.getString(Constants.VOYAGE_ID_KEY);
            List<ReeferDTO> voyageReefers = voyageAllocatedReefers(voyageId);

            Set<String> depotOrders = new HashSet<>();
            if (voyageReefers.size() > 0) {
                StringBuilder builder = new StringBuilder();
                for (ReeferDTO reefer : voyageReefers) {
                    // remove departing reefers from inventory
                    reeferMasterInventory[reefer.getId()] = null;
                    if (order2ReeferMap.containsKey(reefer.getOrderId())) {
                        if (  !depotOrders.contains(reefer.getOrderId())) {
                            depotOrders.add(reefer.getOrderId());
                        }
                        order2ReeferMap.remove(reefer.getOrderId());
                    }
                    builder.append(reefer.getId()).append(",");
                }
                int orderCount = depotOrders.size();
                // if there is excess of reefers in inventory, transfer some to the voyage to rebalance depots
                List<ReeferDTO> empties = getEmptyReefersOnExcessInventory(message.getInt(Constants.VOYAGE_FREE_CAPACITY_KEY), voyageId);
                for( ReeferDTO reefer : empties ) {
                    reeferMasterInventory[reefer.getId()] = null;
                    builder.append(reefer.getId()).append(",");
                }
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("DepotActor.voyageReefersDeparted() >>>> Depot:"+getId()+" Voyage:"+voyageId+" reefer count:"+orderCount+" empties:"+empties.size());
                }
                Inventory inventory = getReeferInventoryCounts();
                bookedTotalCount = inventory.getBooked();
                currentInventorySize = Json.createValue(inventory.getTotal());
                messageAnomalyManager(voyageId, AnomalyManagerActor.ReeferLocation.LocationType.VOYAGE.getType(),
                        builder.toString(), "voyageDeparted", voyageId);
                // combine loaded reefers with empties
                List<ReeferDTO> combinedList = ListUtils.union(voyageReefers, empties);
                // remove reefers in combined list from this depot inventory
                updateStore(deleteMap(combinedList), Collections.emptyMap());
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("DepotActor.voyageReefersDeparted() >>>> \t%25s \tVoyage:%20s \tDeparted:%7d \t%s \tempties:%7d",
                            getId(),voyageId,voyageReefers.size(),inventory.toString(), empties.size()) );
                }
                Set<String> rids = empties.stream().map(ReeferDTO::getId).map(String::valueOf).collect(Collectors.toSet());
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("DepotActor.voyageReefersDeparted() >>>> Depot:"+getId()+" Voyage:"+voyageId+" voyage order count:"+orderCount+" rids.size:"+rids.size());
                }
                JsonObjectBuilder replyJob =  Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).
                        add(Constants.DEPOT_KEY, getId()).
		                  add(Constants.REEFERS_KEY, Json.createValue(rids.size()));
                if ( rids.isEmpty() ) {
		              replyJob.add(Constants.VOYAGE_EMPTY_REEFERS_KEY, Json.createValue(""));
		          } else {
   		           replyJob.add(Constants.VOYAGE_EMPTY_REEFERS_KEY, Json.createValue(String.join(",", rids)));
                    Actors.Builder.instance().target(ReeferAppConfig.VoyageActorType, voyageId).
                            method("addEmptyReefers").arg(replyJob.build()).tell();
		          }
            }
        } catch (Exception e) {
            String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
            logger.log(Level.SEVERE, "DepotActor.voyageReefersDeparted() - "+getId()+" Error ", stacktrace);
            e.printStackTrace();
            throw e;
        }

    }
    private void messageAnomalyManager(String targetId, int targetType, String reefers, String method, String voyageId) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.ANOMALY_TARGET_KEY, targetId).
                add(Constants.ANOMALY_TARGET_TYPE_KEY, Json.createValue(targetType)).
                add(Constants.REEFERS_KEY, reefers).
                add(Constants.VOYAGE_ID_KEY,Json.createValue(voyageId) );
        ActorRef anomalyManagerActor = Kar.Actors.ref(ReeferAppConfig.AnomalyManagerActorType, ReeferAppConfig.AnomalyManagerId);
        Kar.Actors.tell(anomalyManagerActor, method, job.build());
    }
    private List<ReeferDTO> getEmptyReefersOnExcessInventory(int shipRemainingCapacity, String voyageId) {
        List<ReeferDTO> empties = new ArrayList<>();
        Inventory inventory = getReeferInventoryCounts();
        // check if there is excess reefer capacity in this depot. The ic.depotSize()
        // returns the initial depot size
        if (inventory.available > ic.depotSize) {
            int excessInventory = inventory.available - ic.depotSize;
            int emptiesNeeded = 0;
            if ( excessInventory > 0 ) {
                if (excessInventory > shipRemainingCapacity) {
                    emptiesNeeded = shipRemainingCapacity;
                } else {
                    emptiesNeeded = excessInventory;
                }
            }
            // allocate empty reefers to re-balance inventory between two depots. Empties are not associated
            // with orders.
            int reefersNeeded = ReeferAppConfig.ReeferMaxCapacityValue * emptiesNeeded;
            empties = ReeferAllocator.allocateReefers(reeferMasterInventory, reefersNeeded,
                    "", voyageId, inventory.available,  getId());
            if (logger.isLoggable(Level.INFO)) {
                logger.info("DepotActor.getEmptyReefersOnExcessInventory()- "+getId()+" Available:"+inventory.available+" ReeferAllocator allocated empties:"+empties.size());
            }
        }
        return empties;
    }

    private int getUnallocatedReeferCount() {
        int count=0;
        for( ReeferDTO reefer : reeferMasterInventory) {
            if ( reefer != null && reefer.getState().equals(ReeferState.State.UNALLOCATED)) {
                count++;
            }
        }
        return count;
    }

    private Inventory getReeferInventoryCounts() {
        int rbooked=0, rfree=0, rbad=0, total=0;
        for( ReeferDTO reefer : reeferMasterInventory) {
            if (reefer != null ) {
                switch( reefer.getState()) {
                    case ALLOCATED:
                        rbooked++;
                        total++;
                        break;
                    case UNALLOCATED:
                        rfree++;
                        total++;
                        break;
                    case MAINTENANCE:
                        rbad++;
                        total++;
                        break;
                    default:
                        logger.warning("DepotActor.showReeferInventory - unexpected reefer state:"+reefer.getState()+" voyage:"+reefer.getVoyageId());
                }

            }

        }
        return new Inventory(total, rbooked, rfree, rbad);
    }

    @Remote
    public void voyageReefersArrived(JsonObject message) {
        try {
            String arrivalDate = message.getString(Constants.VOYAGE_ARRIVAL_DATE_KEY);
            String voyageId = message.getString(Constants.VOYAGE_ID_KEY);
            // get arrived reefer ids
            String[] reeferIds = message.getString(Constants.REEFERS_KEY).split(",");
            String[] spoiltInventory = message.getString(Constants.SPOILT_REEFERS_KEY).split(",");
            String[] emptyReeferIds = new String[0];
	         if ( message.containsKey(Constants.VOYAGE_EMPTY_REEFERS_KEY) ) {
		         String emptyReefers = message.getString(Constants.VOYAGE_EMPTY_REEFERS_KEY);
                if ( emptyReefers.trim().length()==0 ) {
                    emptyReefers ="";
                } else {
                    emptyReeferIds = emptyReefers.split(",");
                }
	         }
            // combine reefers with products and empties as a new reefer inventory
            String[] newInventory = (String[]) ArrayUtils.addAll(reeferIds, emptyReeferIds);
            List<ReeferDTO> updateList = receiveInventory(newInventory);
            receiveSpoiltInventory(spoiltInventory, arrivalDate);
            messageAnomalyManager(getId(), AnomalyManagerActor.ReeferLocation.LocationType.DEPOT.getType(),
                    String.join(",", newInventory), "voyageArrived", voyageId);
            Inventory inventory = getReeferInventoryCounts();
            currentInventorySize = Json.createValue(inventory.getTotal());
            bookedTotalCount = inventory.getBooked();
            updateStore(Collections.emptyMap(), reeferMap(updateList));
            if (logger.isLoggable(Level.INFO)) {
                logger.info(String.format("DepotActor.voyageReefersArrived()  <<<< \t%25s \tVoyage:%20s \tArrived:%8d \t%s \tEmpties:%6d \tArrival Date:%s \tUpdateList:%d \tnewInventory:%d",
                        getId(), voyageId, reeferIds.length, getReeferInventoryCounts().toString(), emptyReeferIds.length,arrivalDate.toString(), updateList.size(), newInventory.length));
            }
        } catch (Exception e) {
            String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
            logger.log(Level.SEVERE,"DepotActor.voyageReefersArrived() - Error: "+ stacktrace);
            throw e;
        }

    }
    private List<ReeferDTO> receiveInventory(String[] reeferIds) {
        List<ReeferDTO> updateList = new ArrayList<>(reeferIds.length);
        int i=0;
        // transfer empty reefers
        for(String reeferId : reeferIds ) {
            if ( reeferId.trim().length() == 0) {
                continue;
            }
            i++;
            int idx = Integer.parseInt(reeferId);
            if ( reeferMasterInventory[idx] == null ) {
                reeferMasterInventory[idx] = new ReeferDTO(Integer.parseInt(reeferId), ReeferState.State.UNALLOCATED);
                updateList.add(reeferMasterInventory[idx]);
            }
        }
        return updateList;
    }


    private void receiveSpoiltInventory(String[] spoiltReeferIds,String arrivalDate) {
        // now, all arrived spoilt reefer go on maintenance
        for( String reeferId : spoiltReeferIds) {
            if ( reeferId != null && reeferId.trim().length() == 0) {
                continue;
            }
            int idx = Integer.valueOf(reeferId);
            if ( reeferMasterInventory[idx] != null && !reeferMasterInventory[idx].getState().equals(ReeferState.State.ALLOCATED)) {
                reeferMasterInventory[idx] = new ReeferDTO(Integer.valueOf(reeferId), ReeferState.State.SPOILT);
                Map<String, JsonValue> arrivedOnMaintenanceMap = new HashMap<>();
                unReserveReefer(reeferMasterInventory[idx], arrivedOnMaintenanceMap, arrivalDate);
            }

        }
    }
    @Remote
    public void rollbackOrder(JsonObject message) {
        // wrap Json with POJO
        Order order = new Order(message);
        if (order2ReeferMap.containsKey(order.getId())) {
            logger.warning("DepotActor.rollbackOrder - depot:"+getId()+" voyageId:"+order.getVoyageId() +" orderId:"+order.getId());
            Set<String> rids = order2ReeferMap.get(order.getId());
            String[] reefersToRollback = new String[rids.size()];
            List<ReeferDTO> updateList = receiveInventory(rids.toArray(reefersToRollback));
            order2ReeferMap.remove(order.getId());
            Inventory inventory = getReeferInventoryCounts();
            bookedTotalCount = inventory.getBooked();
            if (logger.isLoggable(Level.INFO)) {
                logger.info("DepotActor.rollbackOrder() - depot:" +getId()+" voyage:"+order.getVoyageId()+" reefersToRollback:"+reefersToRollback.length);
            }
            messageAnomalyManager(getId(), AnomalyManagerActor.ReeferLocation.LocationType.DEPOT.getType(),
                    String.join(",", reefersToRollback), "voyageOrderRollback", order.getVoyageId());
            updateStore(Collections.emptyMap(), reeferMap(updateList));
        }
    }
    /**
     * Reserve enough reefers to fill with order products.
     *
     * @param bookingRequest
     * @return
     */
    @Remote
    public Kar.Actors.TailCall bookReefers(JsonObject bookingRequest) {
        Order order = null;
        Set<String> rids;
        List<ReeferDTO> orderReefers = null;
        try {
            // wrap Json with POJO
            order = new Order(bookingRequest);
            order.setDepot(this.getId());
            // idempotence check.
            if (order2ReeferMap.containsKey(order.getId())) {
                logger.info("DepotActor.bookReefers - "+getId()+" voyage:"+order.getVoyageId() +" idempotence check triggered for order Id:"+order.getId());
                return null;
            }
            // allocate reefers to the order.
            ReeferAllocationStatus reeferAllocation = allocateReefers(order);
            if ( reeferAllocation.failed() ) {
                return new Kar.Actors.TailCall( this,
                        "handleFailedAllocationAndSaveState", order.getAsJsonObject());
             } else {
                Inventory inventory = getReeferInventoryCounts();
                return new Kar.Actors.TailCall( this,
                        "handleSuccessfulAllocationAndSaveState", reeferAllocation.reefersToJsonArray(),
                        order.getAsJsonObject(), Json.createValue(inventory.getTotal()), Json.createValue(inventory.getBooked()));
            }
        } catch (Exception e) {
            // undo reefer allocation
            if ( orderReefers != null && !orderReefers.isEmpty() ) {
                rollbackOrder(bookingRequest);
            }
            logFailure(order, e);
            return null;
        }
    }
    @Remote
    public Kar.Actors.TailCall handleFailedAllocationAndSaveState(JsonObject orderAsJson) {
        Order order = new Order(orderAsJson);
        order.setMsg("Failed to allocate reefers to order");
        order.setBookingFailed();
        updateStore(Collections.emptyMap(), reeferMap(Collections.emptyList()));
        JsonObject reply = createReply(Collections.emptySet(),order.getAsJsonObject(), Constants.FAILED);
        return new Kar.Actors.TailCall( Kar.Actors.ref(ReeferAppConfig.VoyageActorType, order.getVoyageId()),
                "processReefersBookingResult", reply );

    }
    @Remote
    public Kar.Actors.TailCall handleSuccessfulAllocationAndSaveState(JsonArray reefers, JsonObject orderAsJson, JsonNumber invSize, JsonNumber bookedCount) {
        Order order = new Order(orderAsJson);
        List<ReeferDTO> orderReefers = new LinkedList<>();
        Set<String> rids = new LinkedHashSet<>();
        for( JsonValue reeferJson : reefers ) {
            ReeferDTO reefer = jsonObjectToReeferDTO(reeferJson.asJsonObject());
            orderReefers.add(reefer);
            rids.add(String.valueOf(reefer.getId()));
        }
        // create order to reefers mapping for in-memory cache to reduce latency
        order2ReeferMap.put(order.getId().trim(), rids);
        currentInventorySize = invSize;
        bookedTotalCount = bookedCount.intValue();
        updateStore(Collections.emptyMap(), reeferMap(orderReefers));
        return new Kar.Actors.TailCall( Kar.Actors.ref(ReeferAppConfig.VoyageActorType, order.getVoyageId()),
                "processReefersBookingResult", createReply(rids,order.getAsJsonObject(), Constants.OK));
    }
    private void logFailure(Order order, Exception e) {
        int actual = 0, bad = 0;
        for (ReeferDTO reeferDTO : reeferMasterInventory) {
            if (reeferDTO != null) {
                if (reeferDTO.getState().equals(ReeferState.State.UNALLOCATED)) {
                    actual++;
                } else if (reeferDTO.getState().equals(ReeferState.State.MAINTENANCE)) {
                    bad++;
                }
            }
        }
        logger.log(Level.SEVERE, "DepotActor.bookReefers() FAILED !!!!!!!!!!!!!!! - Depot:" + getId() +
                " current Inventory:" + ((JsonNumber) currentInventorySize).intValue() +
                " - Actual Available Reefer Count:" + actual +
                " - Currently onMaintenance:" + bad +
                " - Total available (avail + maintenance):"+(actual+bad) +
                " Order reefers:" + order.getProductQty() / 1000 + " Error ", e);
    }
    private JsonObject updateStore(Map<String, List<String>> deleteMap, Map<String, JsonValue> updateMap, JsonObject reply) {
        updateStore(deleteMap, updateMap);
        return reply;
    }
    private ReeferAllocationStatus allocateReefers(Order order ) {
        List<ReeferDTO> orderReefers = null;
        int currentAvailableReeferCount = getUnallocatedReeferCount();
        ReeferAllocationStatus allocationStatus;
        try {
            // allocate enough reefers to cary products in the order
            orderReefers = ReeferAllocator.allocateReefers(reeferMasterInventory, order.getProductQty(),
                    order.getId(), order.getVoyageId(), currentAvailableReeferCount, getId());
            if ( orderReefers == null || orderReefers.isEmpty() ) {
                 allocationStatus = new ReeferAllocationStatus();  // ctor sets internal allocation failure flag
            } else {
                allocationStatus = new ReeferAllocationStatus(orderReefers);
            }
        } catch( Error er) {
            logger.severe("DepotActor.allocateReefers - "+getId()+" voyage:"+order.getVoyageId() +" Error while allocating reefers to order - cause:"+er.getMessage());
            order.setMsg(er.getMessage());
            order.setBookingFailed();
            allocationStatus = new ReeferAllocationStatus();  // ctor sets internal allocation failure flag
        }
        return allocationStatus;
    }
    private JsonObject createReply(Set<String> reeferIds, JsonObject order, String bookingStatus) {
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, bookingStatus).
                add(Constants.DEPOT_KEY, getId()).
                add(Constants.REEFERS_KEY, Json.createValue(reeferIds.size())).
                add(Constants.ORDER_REEFERS_KEY, Json.createValue(String.join(",", reeferIds))).
                add(JsonOrder.OrderKey, order).build();
    }
    private Map<String, JsonValue> reeferMap(List<ReeferDTO> orderReefers) {
        Map<String, JsonValue> map = new HashMap<>(orderReefers.size());
        for (ReeferDTO reefer : orderReefers) {
            map.put(String.valueOf(reefer.getId()), reeferToJsonObject(reefer));
        }
        return map;
    }

    private Map<String, List<String>> deleteMap(List<ReeferDTO> reefers) {
        Map<String, List<String>> map = new HashMap<>();
        List<String> reeferIdsToDelete = new LinkedList<>();
        for (ReeferDTO reefer : reefers) {
            reeferIdsToDelete.add(String.valueOf(reefer.getId()));
        }
        map.put(Constants.REEFER_MAP_KEY, reeferIdsToDelete);
        return map;
    }

    /**
     * Handle reefer anomaly
     * Ignore if bad reefer ID or ID already marked maintenance/spoilt
     * else If not associated with an order, mark maintenance
     * else Tell order that reefer has gone bad
     *
     * @param message
     */
    @Remote
    public void reeferAnomaly(JsonObject message) {
        int reeferId = message.getInt(Constants.REEFER_ID_KEY);
        if (reeferMasterInventory[reeferId] == null) {
            logger.info("DepotActor.reeferAnomaly() - " + getId() + " >>>>>>>>>>>> REEFER:" + reeferId +
                    " Not in inventory - departed already - sending back to Anomaly Manager");

            // forward the anomaly back to the Anomaly Manager. The anomaly should be sent to the voyage actor.
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.REEFER_ID_KEY, reeferId).add(Constants.DEPOT_KEY, getId()).add(Constants.TARGET_KEY, Constants.VOYAGE_TARGET_TYPE);
            Actors.Builder.instance().target(ReeferAppConfig.AnomalyManagerActorType,  ReeferAppConfig.AnomalyManagerId).
                    method("reeferAnomaly").arg(job.build()).tell();
            return;
        }
        if (reeferMasterInventory[reeferId].alreadyBad()) {
            return;
        }

        if (reeferMasterInventory[reeferId].assignedToOrder()) {
            JsonObject orderReplaceMessage = Json.createObjectBuilder()
                    .add(Constants.REEFER_ID_KEY,reeferId).build();
            Actors.Builder.instance().target(ReeferAppConfig.OrderActorType,  reeferMasterInventory[reeferId].getOrderId()).
                    method("replaceReefer").arg(orderReplaceMessage).tell();
        } else {
            Instant today;
            if ( message.containsKey(Constants.DATE_KEY)) {
                today = Instant.parse(message.getString(Constants.DATE_KEY));
            } else if ( currentDate == null ) {
                JsonValue reply = Kar.Actors.call(this, scheduleActor, "currentDate");
                today = Instant.parse(((JsonString) reply).getString());
            } else {
                today = currentDate;
            }
            setReeferOnMaintenance(reeferMasterInventory[reeferId], today.toString());
            Map<String, JsonValue> updateMap = new HashMap<>();
            updateMap.put(String.valueOf(reeferId), reeferToJsonObject(reeferMasterInventory[reeferId]));
            updateStore(Collections.emptyMap(), updateMap);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("DepotActor.reeferAnomaly() - id:" + getId()
                        + " added reefer:" + reeferId + " to "
                        + Constants.ON_MAINTENANCE_PROVISIONER_LIST + " Map");
            }
        }
    }

    /**
     * Handle request for reefer replacement
     *
     * @param message
     */
    @Remote
    public JsonObject reeferReplace(JsonObject message) {
       try {
           int reeferId = message.getInt(Constants.REEFER_ID_KEY);
           if (reeferMasterInventory[reeferId] == null) {
               logger.log(Level.WARNING, "DepotActor.reeferReplace() - depot:"+getId()+" Reefer " + reeferId + " no longer in the inventory - request to replace it is invalid");
               return Json.createObjectBuilder()
                       .add(Constants.STATUS_KEY, Constants.FAILED).add(Constants.ERROR, "Depot "+getId()+" - request to replace reefer is invalid - reefer "+reeferId+" no longer in the inventory").build();
           }
           ReeferDTO reefer = reeferMasterInventory[reeferId];
           List<ReeferDTO> replacementReeferList = ReeferAllocator.allocateReefers(reeferMasterInventory,
                   Constants.REEFER_CAPACITY, reefer.getOrderId(), reefer.getVoyageId(), getUnallocatedReeferCount(), getId());
           if (replacementReeferList.isEmpty()) {
               logger.log(Level.WARNING, "DepotActor.reeferReplace() - depot:"+getId()+" Unable to allocate replacement reefer for " + reeferId);
               return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.FAILED).add(Constants.ERROR,"Unable to allocate replacement reefer for " + reeferId).build();
           }
           if (logger.isLoggable(Level.INFO)) {
               logger.info("DepotActor.reeferReplace() - replacing reeferId:"
                       + reefer.getId() + " with:" + replacementReeferList.get(0).getId());
           }
           if (order2ReeferMap.containsKey(reefer.getOrderId())) {
               Set<String> reeferIds = order2ReeferMap.get(reefer.getOrderId());
               // remove spoilt
               reeferIds.remove(String.valueOf(reeferId));
               // add replacement
               reeferIds.add(String.valueOf(replacementReeferList.get(0).getId()));
               order2ReeferMap.remove(reefer.getOrderId());
               order2ReeferMap.put(reefer.getOrderId(),reeferIds);
           }
           JsonValue currentDate = Kar.Actors.call(this, scheduleActor, "currentDate");
           setReeferOnMaintenance(reefer, ((JsonString) currentDate).getString());
           reefer.removeFromVoyage();
           // persist changes applied to spoilt and replace reefers
           Map<String, JsonValue> updateMap = new HashMap<>();
           updateMap.put(String.valueOf(reefer.getId()), reeferToJsonObject(reefer));
           updateMap.put(String.valueOf(replacementReeferList.get(0).getId()), reeferToJsonObject(replacementReeferList.get(0)));
           updateStore(Collections.emptyMap(), updateMap);
           return Json.createObjectBuilder()
                   .add(Constants.REEFER_REPLACEMENT_ID_KEY, replacementReeferList.get(0).getId())
                   .add(Constants.STATUS_KEY, Constants.OK).build();
       } catch( Exception e) {
           logger.log(Level.WARNING,"DepotActor.reeferReplace() : Error ", e);
           return Json.createObjectBuilder()
                   .add(Constants.STATUS_KEY, Constants.FAILED).add(Constants.ERROR,e.getMessage()).build();
       }
    }
    /**
     * Returns a list of unique voyage ids reefers have been assigned to.
     *
     * @return - JsonArray containing voyage ids
     */
    @Remote
    public JsonValue voyageList() {
        List<String> voyageIdList =
                Arrays.stream(reeferMasterInventory).
                        filter(Objects::nonNull).
                        map(ReeferDTO::getVoyageId).
                        filter(id -> !(id.trim().isEmpty())).
                        distinct().
                        collect(Collectors.toList());
        return Json.createArrayBuilder(voyageIdList).build();
    }

    private ReeferDTO jsonObjectToReeferDTO(JsonObject reefer) {
        ReeferDTO reeferDTO = new ReeferDTO(reefer.getInt(Constants.REEFER_ID_KEY),
                ReeferState.State.valueOf(reefer.getString(Constants.REEFER_STATE_KEY)),
                reefer.getString(Constants.ORDER_ID_KEY), reefer.getString(Constants.VOYAGE_ID_KEY));
        if (reefer.containsKey(Constants.REEFERS_MAINTENANCE_DATE) && reefer.getString(Constants.REEFERS_MAINTENANCE_DATE).trim().length() > 0) {
            reeferDTO.setMaintenanceReleaseDate(reefer.getString(Constants.REEFERS_MAINTENANCE_DATE));
        }
        return reeferDTO;
    }

    private JsonObject reeferToJsonObject(ReeferDTO reefer) {
        return reeferToJsonObject(reefer, Json.createObjectBuilder());
    }

    private JsonObject reeferToJsonObject(ReeferDTO reefer, JsonObjectBuilder reeferObjectBuilder) {
        reeferObjectBuilder.add(Constants.REEFER_ID_KEY, reefer.getId())
                .add(Constants.REEFER_STATE_KEY, reefer.getState().name())
                .add(Constants.ORDER_ID_KEY, reefer.getOrderId())
                .add(Constants.VOYAGE_ID_KEY, reefer.getVoyageId());
        if (reefer.getMaintenanceReleaseDate() != null) {
            reeferObjectBuilder.add(Constants.REEFERS_MAINTENANCE_DATE, reefer.getMaintenanceReleaseDate());
        }
        return reeferObjectBuilder.build();
    }

    private void initMasterInventory(InventoryConfig config) {
        try {
            reeferMasterInventory = new ReeferDTO[config.getGlobalInventorySize()];
            JsonObjectBuilder job = Json.createObjectBuilder();
            Map<String, JsonValue> map = new HashMap<>(config.getDepotSize() );
            JsonObjectBuilder reeferObjectBuilder = Json.createObjectBuilder();
            for( Shard shard : config.getShards()) {
                for (int id = Long.valueOf(shard.getLowerBound()).intValue(); id <= shard.getUpperBound(); id++) {
                    reeferMasterInventory[id] = new ReeferDTO(id, ReeferState.State.UNALLOCATED);

                    // JsonObjectBuilder is expensive object to create. Reuse single instance to improve
                    // performance. In the <code>reeferToJsonObject()</code> the instance is used to
                    // create a JsonObject and when its build() is called it internally clears its state
                    // and thus allows for reuse.
                    map.put(String.valueOf(id), reeferToJsonObject(reeferMasterInventory[id], reeferObjectBuilder));
                }
            }
            long t = System.currentTimeMillis();
            updateStore(Collections.emptyMap(), map);
         } catch (Exception e) {
            logger.log(Level.SEVERE,"DepotActor.reeferSpoilt() - Error ", e);
        }

    }

    /**
     * Calls DepotManagerActor to fetch total number of reefers
     *
     * @return Total number of reefers
     */
    private InventoryConfig getReeferInventory() {
        InventoryConfig inv = null;
        try {
            JsonValue reply = Actors.Builder.instance().target(ReeferAppConfig.DepotManagerActorType,  ReeferAppConfig.DepotManagerId).
                    method("depotInventory").arg(Json.createValue(getId())).call(this);
            totalReeferInventory = reply.asJsonObject().getJsonNumber(Constants.TOTAL_REEFER_COUNT_KEY);
            depotSize = reply.asJsonObject().getJsonNumber(Constants.DEPOT_SIZE_KEY);
            currentInventorySize = depotSize;
            JsonArray jsonShards = reply.asJsonObject().getJsonArray(Constants.SHARDS_KEY);
            InventoryConfig config = new InventoryConfig(((JsonNumber) depotSize).intValue(), ((JsonNumber) totalReeferInventory).intValue());
            if ( jsonShards != null ) {
                for ( JsonValue jsonShard : jsonShards) {
                    Shard shard = new Shard(jsonShard.asJsonObject().getInt("reefer-id-lower-bound"),
                            jsonShard.asJsonObject().getInt("reefer-id-upper-bound"));
                    config.addShard(shard);
                }
            }
            return config;
        } catch (Exception e) {
            logger.log(Level.SEVERE,"DepotActor.getReeferInventory() -  failed - cause: " + e.getMessage());
            throw e;
        }

    }

    private JsonObject getReeferStats() {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("DepotActor.getReeferStats() - totalBooked:" + bookedTotalCount + " in-transit:"
                    + 0 + " spoilt:" + 0 + " on-maintenance:" + onMaintenanceMap.size());
        }
        return Json.createObjectBuilder().add("total", totalReeferInventory).add("totalBooked", bookedTotalCount)
                .add("totalInTransit", 0).add("totalSpoilt", 0)
                .add("totalOnMaintenance", onMaintenanceMap.size()).build();
    }

    private void setReeferOnMaintenance(ReeferDTO reefer, String today) {
        // assign reefer off maintenance date which is N days from today. Currently N=2
        reefer.setMaintenanceReleaseDate(today);
        reefer.setState(ReeferState.State.MAINTENANCE);

        onMaintenanceMap.put(reefer.getId(), reefer.getId());
    }

    private void updateStore(Map<String, List<String>> deleteMap, Map<String, JsonValue> updateMap) {
        String metrics = getMetricsString();

        // Save this depot reefer metrics in DepotManager Map
        ActorRef depotManagerActor = Kar.Actors.ref(ReeferAppConfig.DepotManagerActorType, ReeferAppConfig.DepotManagerId);
        Kar.Actors.State.Submap.set(depotManagerActor, Constants.REEFER_METRICS_MAP_KEY, getId(), Json.createValue(metrics));

        Map<String, JsonValue> actorStateMap = new HashMap<>();
        actorStateMap.put(Constants.REEFER_METRICS_KEY, Json.createValue(metrics));

        Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
        subMapUpdates.put(Constants.REEFER_MAP_KEY, updateMap);
        Kar.Actors.State.update(this, Collections.emptyList(), deleteMap, actorStateMap, subMapUpdates);
    }


    private void unReserveReefer(ReeferDTO reefer, Map<String, JsonValue> onmr, String arrivalDate) {
        if (reefer.getState().equals(ReeferState.State.MAINTENANCE)) {
            logger.warning(
                    "DepotActor.unreserveReefer() - reefer >>>>>>> " + reefer.getId() + " is on-maintenance unexpectedly - it should be spoilt instead");
        }
        // Reefers can be marked as spoilt only during the voyage. When a voyage ends
        // all spoilt reefers are placed on maintenance.
        if (reefer.getState().equals(ReeferState.State.SPOILT)) {
            reefer.removeFromVoyage();
            reefer.setMaintenanceReleaseDate(arrivalDate);
            reefer.setState(ReeferState.State.MAINTENANCE);
            onMaintenanceMap.put(reefer.getId(), reefer.getId());
            onmr.put(String.valueOf(reefer.getId()), reeferToJsonObject(reefer));
        } else {
            reefer.reset();
        }
    }

    private List<ReeferDTO> voyageAllocatedReefers(String voyageId) {
        return Arrays.stream(reeferMasterInventory).
                filter(Objects::nonNull).
                filter(reefer -> reefer.getVoyageId().equals(voyageId)).
                peek(reefer -> {
                    // don't overwrite reefer state if it's spoilt
                    if (reefer.getState().equals(ReeferState.State.ALLOCATED)) {
                        // The INTRANSIT state is not currently being used in DepotActor. If it needs to be
                        // checked save the reefer map to persist the change by first deleting reefer map in
                        // kar storage and saving the updated map.
                        reefer.setState(ReeferState.State.INTRANSIT);
                    }
                }).collect(Collectors.toList());
    }

    private void saveMetrics() {
        String metrics = getMetricsString();
        Kar.Actors.State.set(this, Constants.REEFER_METRICS_KEY, Json.createValue(metrics));
    }

    @Remote
    public JsonValue getMetrics() {
        return Json.createValue(getMetricsString());
    }

    private String getMetricsString() {
        return String.format("%d:%d:%d:%d:%d:%d:%d", bookedTotalCount, 0,
                0, onMaintenanceMap.size(), ((JsonNumber) totalReeferInventory).intValue(),
                ((JsonNumber) currentInventorySize).intValue(), ((JsonNumber) depotSize).intValue());
    }

    private class Inventory {
        int booked=0, available = 0, onMaintenance=0, total=0;

        public Inventory(int total, int booked, int available, int onMaintenance) {
            this.total = total;
            this.booked = booked;
            this.available = available;
            this.onMaintenance = onMaintenance;
        }

        public int getBooked() {
            return booked;
        }

        public int getAvailable() {
            return available;
        }

        public int getOnMaintenance() {
            return onMaintenance;
        }
        public int getTotal() {
            return total;
        }
        public String toString() {
            return String.format("Booked=%d\t Free=%d\t Maintenance=%d\t Actual Total=%d",booked,available,onMaintenance, total);
        }
    }
    private class InventoryConfig {
        private int totalSize;
        private int depotSize;
        private List<Shard> shards = new LinkedList<>();

        public InventoryConfig(int depotSize, int totalSize) {
            this.depotSize = depotSize;
            this.totalSize = totalSize;
        }

        public int getDepotSize() {
            return depotSize;
        }

        public int getGlobalInventorySize() {
            return totalSize;
        }

        public void addShard(Shard shard) {
            shards.add(shard);
        }
        public List<Shard> getShards() {
            return shards;
        }

    }
    private class ReeferAllocationStatus {

        private List<ReeferDTO> orderReefersList = null;
        private boolean failed;

        protected ReeferAllocationStatus() {
            failed = ALLOCATION_FAILED;
        }
        protected ReeferAllocationStatus(List<ReeferDTO> orderReefers) {
            this.orderReefersList = orderReefers;
            this.failed = false;
        }
        public List<ReeferDTO> getOrderReefersList() {
            return orderReefersList;
        }
        public boolean failed() {
            return failed == true;
        }
        public void setFailed() {
            failed = true;
        }

        public JsonArray reefersToJsonArray() {
            if ( !failed ) {
                JsonArrayBuilder jab = Json.createArrayBuilder();
                for( ReeferDTO reefer: orderReefersList ) {
                   jab.add(reeferToJsonObject(reefer));
                }
                return jab.build();
            }
            return Json.createArrayBuilder().build();
        }
    }
}
