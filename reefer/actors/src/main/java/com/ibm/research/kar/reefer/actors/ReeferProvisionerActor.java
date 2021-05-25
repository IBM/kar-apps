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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferAllocator;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.ReeferDTO;

import javax.json.*;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This actor manages reefer inventory allocating reefers to new orders
 * and releasing reefers back to inventory when an order is delivered.
 * It maintains its inventory in a global array keyed with reefer ids.
 */
@Actor
public class ReeferProvisionerActor extends BaseActor {
    // global array to hold reefers
    private ReeferDTO[] reeferMasterInventory = null;

    private int bookedTotalCount = 0;
    private int inTransitTotalCount = 0;
    private int spoiltTotalCount=0;
    private int onMaintenanceTotalCount=0;
    // ConcurrentHashMap is necessary since there is an internal thread
    // which needs access to this map when publishing changes to the REST
    private Map<Integer, Integer> onMaintenanceMap = new ConcurrentHashMap<>();
    // contains Order-Reefers mapping needed to reduce latency of calls
    private Map<String, Set<String>> order2ReeferMap = new HashMap<>();

    private JsonValue totalReeferInventory;
    private static final Logger logger = Logger.getLogger(ReeferProvisionerActor.class.getName());

    @Activate
    public void activate() {
        long t = System.currentTimeMillis();
        try {
            // fetch actor state from Kar storage
            Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
            System.out.println("ReeferProvisionerActor.activate() .............getAll() took:"+(System.currentTimeMillis() -t)+" millis state size:"+state.size());

            if (!state.isEmpty()) {
                if ( state.containsKey(Constants.REEFER_STATS_MAP_KEY)) {
                    long t1 = System.currentTimeMillis();
                    JsonValue jv2 = state.get(Constants.REEFER_STATS_MAP_KEY);
                    Map<String, JsonValue> reeferStatsMap = jv2.asJsonObject();
                    System.out.println("ReeferProvisionerActor.activate() .............jv2.asJsonObject() took:"+(System.currentTimeMillis() -t1)+" millis ");
                    // restore counts from Kar persistent storage
                    if (reeferStatsMap.containsKey(Constants.TOTAL_BOOKED_KEY)) {
                        bookedTotalCount = ((JsonNumber) reeferStatsMap.get(Constants.TOTAL_BOOKED_KEY)).intValue();
                    }
                    if (reeferStatsMap.containsKey(Constants.TOTAL_INTRANSIT_KEY)) {
                        inTransitTotalCount = ((JsonNumber) reeferStatsMap.get(Constants.TOTAL_INTRANSIT_KEY)).intValue();
                    }
                    if (reeferStatsMap.containsKey(Constants.TOTAL_SPOILT_KEY)) {
                        spoiltTotalCount =((JsonNumber) reeferStatsMap.get(Constants.TOTAL_SPOILT_KEY)).intValue();
                    }
                    if (reeferStatsMap.containsKey(Constants.TOTAL_ONMAINTENANCE_KEY)) {
                        onMaintenanceTotalCount =((JsonNumber) reeferStatsMap.get(Constants.TOTAL_ONMAINTENANCE_KEY)).intValue();
                    }
                    if (reeferStatsMap.containsKey(Constants.TOTAL_REEFER_COUNT_KEY)) {
                        totalReeferInventory = reeferStatsMap.get(Constants.TOTAL_REEFER_COUNT_KEY);
                    }
                }

                if (((JsonNumber) totalReeferInventory).intValue() > 0) {
                    restoreReeferInventory(state);
                    restoreOrderToReefersMap();
                    restoreOnMaintenanceMap(state);
                }

            } else {
                initMasterInventory(getReeferInventorySize());
                Kar.Actors.State.Submap.set(this, Constants.REEFER_STATS_MAP_KEY,Constants.TOTAL_REEFER_COUNT_KEY, totalReeferInventory );
            }
        }   catch( Throwable e) {
            e.printStackTrace();
        }
       System.out.println("ReeferProvisionerActor.activate - completed in .........."+(System.currentTimeMillis() - t));

    }
    private void restoreReeferInventory(Map<String, JsonValue> state ) throws Exception {
        long t = System.currentTimeMillis();
        JsonValue jv2 = state.get(Constants.REEFER_MAP_KEY);
        Map<String, JsonValue> reeferInventory = jv2.asJsonObject();
        System.out.println("ReeferProvisionerActor.restoreReeferInventory - deserialization took  .........."+(System.currentTimeMillis() - t));
        // fetch reefer map
         if (logger.isLoggable(Level.INFO)) {
            logger.info("ReeferProvisionerActor.init() - Fetched size of the reefer inventory:"
                    + reeferInventory.size());
        }
        // allocate reefer array which is used to allocate/deallocate reefers
        reeferMasterInventory = new ReeferDTO[((JsonNumber) totalReeferInventory).intValue()];
        reeferInventory.
                values().
                stream().
                filter(Objects::nonNull).
                map(jv -> jv.asJsonObject()).
                forEach(reefer -> reeferMasterInventory[reefer.getInt(Constants.REEFER_ID_KEY)] = jsonObjectToReeferDTO(reefer));
        System.out.println("ReeferProvisionerActor.restoreReeferInventory - inventory size:"+reeferInventory.size()+"  completed in .........."+(System.currentTimeMillis() - t));
    }
    private void restoreOrderToReefersMap() {
        long t = System.currentTimeMillis();
        // restore in-memory cache which manages order-reefers association. This cache provides performance
        // optimization which reduces latency of voyage arrival processing and idempotence check when booking
        // reefers to order.
        // (1) on voyage arrival, code does not need to scan reefer inventory for reefers associated with
        //     orders in a voyage. The cache contains all reefer ids for each order which can be used to
        //     remove them from persistent store as a batch
        // (2) with the cache its fast to check for idempotence when processing reefer booking for an order
        //
        order2ReeferMap = Arrays.stream(reeferMasterInventory).filter(Objects::nonNull).
                collect(Collectors.groupingBy(ReeferDTO::getOrderId, Collectors.mapping(r -> String.valueOf(r.getId()), Collectors.toSet())));
       System.out.println("ReeferProvisionerActor.restoreOrderToReefersMap - completed in .........."+(System.currentTimeMillis() - t));
    }
    private void restoreOnMaintenanceMap(Map<String, JsonValue> state) {
        long t = System.currentTimeMillis();
        onMaintenanceMap = Arrays.stream(reeferMasterInventory).
                filter(Objects::nonNull).
                filter(reefer -> reefer.getState().equals(ReeferState.State.MAINTENANCE)).
                collect(Collectors.toMap(ReeferDTO::getId, ReeferDTO::getId));
       System.out.println("ReeferProvisionerActor.restoreOnMaintenanceMap - completed in .........."+(System.currentTimeMillis() - t));
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
    public JsonObject releaseReefersfromMaintenance(JsonObject message) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "ReeferProvisionerActor.releaseReefersfromMaintenance() - entry  message:" + message);
        }

        try {
            if (!onMaintenanceMap.isEmpty()) {
                // get current date. Its a date that the simulator advances at regular intervals.
                Instant today = Instant.parse(message.getString(Constants.DATE_KEY));
                List<String> reefers2Remove = getReefersToRemoveFromMaintenance(today);
                Kar.Actors.State.Submap.removeAll(this, Constants.REEFER_MAP_KEY, reefers2Remove);
                for( String reeferId : reefers2Remove ) {
                    onMaintenanceMap.remove(Integer.parseInt(reeferId));
                    onMaintenanceTotalCount--;
                }
                Kar.Actors.State.Submap.set(this, Constants.REEFER_STATS_MAP_KEY,Constants.TOTAL_ONMAINTENANCE_KEY, Json.createValue(onMaintenanceTotalCount ));
            }
        } catch( Exception e) {
            logger.log(Level.WARNING, "ReeferProvisioner.releaseReefersfromMaintenance() - Error ", e);
            throw e;
        }

        return Json.createObjectBuilder().build();
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
        long t = System.currentTimeMillis();
        String voyageId = message.getString(Constants.VOYAGE_ID_KEY);
        // get the number of reefers assigned to a given voyage
        Long voyageReefersInTransit = voyageReeferCount(voyageId);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.voyageReefersDeparted() - message:" + message + " update reefers in transit:" + voyageReefersInTransit);
        }

        if (voyageReefersInTransit > 0) {
            if ((bookedTotalCount - voyageReefersInTransit.intValue()) >= 0) {
                bookedTotalCount -= voyageReefersInTransit.intValue();
            } else {
                logger.log(Level.WARNING, "ReeferProvisioner.voyageReefersDeparted() - unexpected underflow of bookedTotalCount which will result in negative value");
                bookedTotalCount = 0;
            }
            inTransitTotalCount += voyageReefersInTransit.intValue();

            Map<String, JsonValue> updateMap = new HashMap<>();
            updateMap.put(Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount));
            updateMap.put(Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount));

            Kar.Actors.State.Submap.set(this, Constants.REEFER_STATS_MAP_KEY, updateMap );
        }
     }

    /**
     * Reserve enough reefers to fill with order products.
     *
     * @param message
     * @return
     */
    @Remote
    public JsonObject bookReefers(JsonObject message) {
        long t = System.currentTimeMillis();
        Order order = null;
        try {
            // wrap Json with POJO
            order = new Order(message);
            // idempotence check if this method is being called more than once for the same order
            if ( order2ReeferMap.containsKey(order.getId())) {
                Set<String> ids = order2ReeferMap.get(order.getId());
                if (!ids.isEmpty()) {
                    return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).add(Constants.REEFERS_KEY, Json.createValue(ids.size()))
                            .add(JsonOrder.OrderKey, order.getAsJsonObject()).build();
                }

            }
            // allocate enough reefers to cary products in the order
            List<ReeferDTO> orderReefers = ReeferAllocator.allocateReefers(reeferMasterInventory, order.getProductQty(),
                    order.getId(), order.getVoyageId());
            // create order to reefers mapping for in-memory cache to reduce latency
            Set<String> rids = orderReefers.stream().map(ReeferDTO::getId).map(String::valueOf).collect(Collectors.toSet());

            order2ReeferMap.put(order.getId().trim(), rids);
            // persists allocated reefers
            save(orderReefers);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisionerActor.bookReefers())- Order:" + order.getId() + " reefer count:"
                        + orderReefers.size());
            }
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).
                    add(Constants.REEFERS_KEY, Json.createValue(orderReefers.size())).
                    add(JsonOrder.OrderKey, order.getAsJsonObject()).build();

        } catch (Throwable e) {
            logger.log(Level.WARNING, "ReeferProvisioner.bookReefers() - Error ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, "").build();
        }  finally {
           // System.out.println("ReeferProvisioner.bookReefers() - order:"+order.getId()+" time spent here - " + (System.currentTimeMillis()-t)+" ms");
        }
    }
    private void save(List<ReeferDTO> orderReefers) {
        // need an array to hold reeferIds which will be included in the reply
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        Map<String, JsonValue> map = new HashMap<>();
        // book each reefer
        for (ReeferDTO reefer : orderReefers) {
            arrayBuilder.add(reefer.getId());
            map.put(String.valueOf(reefer.getId()), reeferToJsonObject(reefer));
        }
        Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, map);

        bookedTotalCount += orderReefers.size();
        Kar.Actors.State.Submap.set(this, Constants.REEFER_STATS_MAP_KEY,Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount ));
    }

    /**
     * Release given reefers back to inventory
     *
     * @param message
     * @return
     */
    @Remote
    public void releaseVoyageReefers(JsonObject message) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "ReeferProvisionerActor.releaseVoyageReefers() - entry");
        }
        long t1= System.currentTimeMillis();
        JsonArray orders = message.getJsonArray(Constants.VOYAGE_ORDERS_KEY);
        Map<String, JsonValue> arrivedOnMaintenanceMap = new HashMap<>();
        // get a list of reefers associated with a given voyage. Exclude reefers
        // which arrived in spoilt state. Those will be placed on maintenance
        // and need to remain in inventory.
        List<String> reefers2Remove = new LinkedList<>();
        if ( orders != null ) {
            reefers2Remove = getArrivedReefers(orders, arrivedOnMaintenanceMap);
        }

        Map<String, JsonValue> updateMap = new HashMap<>();
        updateMap.put(Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount));
        updateMap.put(Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount));

        Map<String, JsonValue> maintenanceMap = new HashMap<>();
        arrivedOnMaintenanceMap.keySet().forEach(reeferId -> maintenanceMap.put(reeferId, Json.createValue(reeferId)));
        if ( !arrivedOnMaintenanceMap.isEmpty()) {
            updateMap.put(Constants.TOTAL_ONMAINTENANCE_KEY, Json.createValue(onMaintenanceTotalCount));
            // add onMaintenance reefers
            Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, arrivedOnMaintenanceMap);
        }
        Kar.Actors.State.Submap.set(this, Constants.REEFER_STATS_MAP_KEY, updateMap);

        if ( !reefers2Remove.isEmpty()) {
            // remove reefers which just arrived. The reefer inventory should only contain
            // reefers which are booked, in-transit, and on-maintenance.
            Kar.Actors.State.Submap.removeAll(this, Constants.REEFER_MAP_KEY, reefers2Remove);
            List<String> orderList = orders.stream().filter(Objects::nonNull).map(jv -> ((JsonString)jv).getString()).collect(Collectors.toList());
            for( String orderId : orderList ) {
                if ( order2ReeferMap.containsKey(orderId.trim())) {
                    order2ReeferMap.remove(orderId.trim());
                }
            }
            order2ReeferMap.keySet().removeAll(orderList);
         }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.releaseVoyageReefers() - released reefers " + " total booked: "
                    + bookedTotalCount + " totalInTransit:" + inTransitTotalCount);
        }
    }

    /**
     * returns a list of reefers allocated to given orders. Uses in-memory map to find all reefers allocated
     * to an order and adds them to a list. If a reefer arrives spoilt, the unreserveReefer() will change
     * its state to on-maintenance and such reefer will be excluded from a list to be returned.
     * @param orders - list of arrived orders
     * @param arrivedOnMaintenanceMap -
     * @return
     */
    private List<String> getArrivedReefers(JsonArray orders, Map<String, JsonValue> arrivedOnMaintenanceMap) {
        List<String> reefers2Remove = new LinkedList<>();
        orders.forEach(orderId -> {
            String id = ((JsonString) orderId).getString();
            if ( order2ReeferMap.containsKey(id)) {
                Set<String> reefers = order2ReeferMap.get(id);
                reefers.forEach(reeferId -> {
                    int inx = Integer.parseInt(reeferId);
                    unreserveReefer(reeferMasterInventory[inx], arrivedOnMaintenanceMap);
                    if ( !reeferMasterInventory[inx].getState().equals(ReeferState.State.MAINTENANCE)) {
                        reefers2Remove.add(reeferId);
                    }
                });
            }
        });
        return reefers2Remove;
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
        // the master inventory contains "active" reefers. If a given anomaly is for an unassigned
        // reefer, create a new entry in the inventory for it, and set it on-maintenance
        if (reeferMasterInventory[reeferId] == null) {
            reeferMasterInventory[reeferId] = new ReeferDTO(reeferId, ReeferState.State.MAINTENANCE);
            Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, String.valueOf(reeferId),
                    reeferToJsonObject(reeferMasterInventory[reeferId]));
            setReeferOnMaintenance(reeferMasterInventory[reeferId], message.getString(Constants.DATE_KEY));
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId
                        + " allocated on Maintenance");
            }
        } else if (reeferMasterInventory[reeferId].alreadyBad()) {
            // either on maintenance already or spoilt
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferProvisioner.reeferAnomaly() - " + reeferId + " already bad");
            }
        } else if (reeferMasterInventory[reeferId].assignedToOrder()) {
            // notify order actor of anomaly
            // the order will call back with request to replace it or mark it spoilt
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId
                        + " assigned to order: " + reeferMasterInventory[reeferId].getOrderId());
            }
            ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderActorName, reeferMasterInventory[reeferId].getOrderId());
            Kar.Actors.tell(orderActor, "anomaly", message);
        } else {
            setReeferOnMaintenance(reeferMasterInventory[reeferId], message.getString(Constants.DATE_KEY));

            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferProvisionerActor.reeferAnomaly() - id:" + getId()
                        + " added reefer:" + reeferId + " to "
                        + Constants.ON_MAINTENANCE_PROVISIONER_LIST + " Map"
                        + " onMaintenance date:" + message.getString(Constants.DATE_KEY));
            }
        }
    }

    /**
     * Handle Order request for reefer replacement
     *
     * @param message
     */
    @Remote
    public JsonObject reeferReplacement(JsonObject message) {
        int reeferId = message.getInt(Constants.REEFER_ID_KEY);
        if (reeferMasterInventory[reeferId] == null) {
            logger.log(Level.WARNING, "ReeferProvisioner.reeferReplacement() - Reefer " + reeferId + " not allocated - request to replace it is invalid");
            throw new IllegalStateException("Reefer " + reeferId + " not allocated - request to replace it is invalid");
        }
        if (reeferMasterInventory[reeferId].getState().equals(ReeferState.State.MAINTENANCE)) {
            logger.log(Level.WARNING, "ReeferProvisioner.reeferReplacement() - reefer " + reeferId + " is already on-maintenance - invalid state");
            return Json.createObjectBuilder()
                    .add(Constants.STATUS_KEY, Constants.FAILED).build();
        }
        ReeferDTO reefer = reeferMasterInventory[reeferId];

        List<ReeferDTO> replacementReeferList = ReeferAllocator.allocateReefers(reeferMasterInventory,
                Constants.REEFER_CAPACITY, reefer.getOrderId(), reefer.getVoyageId());
        if (replacementReeferList.isEmpty()) {
            logger.log(Level.WARNING, "ReeferProvisioner.reeferReplacement() - Unable to allocate replacement reefer for " + reeferId);
            throw new RuntimeException("Unable to allocate replacement reefer for " + reeferId);
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.replaceSpoiltReefer() - replacing reeferId:"
                    + reefer.getId() + " with:" + replacementReeferList.get(0).getId());
        }
        if ( order2ReeferMap.containsKey(reefer.getOrderId())) {
            Set<String> reeferIds = order2ReeferMap.get(reefer.getOrderId());
            // remove spoilt
            reeferIds.remove(String.valueOf(reeferId));
            // add replacement
            reeferIds.add(String.valueOf(replacementReeferList.get(0).getId()));
        }
        reefer.removeFromVoyage();
        setReeferOnMaintenance(reefer, message.getString(Constants.DATE_KEY));
        // save replacement reefer
        updateStore(replacementReeferList.get(0));
        // forces update thread to send reefer counts

        return Json.createObjectBuilder()
                .add(Constants.REEFER_REPLACEMENT_ID_KEY, replacementReeferList.get(0).getId())
                .add(Constants.STATUS_KEY, Constants.OK).build();
    }


    /**
     * Handle Order request to mark reefer spoilt
     *
     * @param message
     */
    @Remote
    public JsonObject reeferSpoilt(JsonObject message) {
        try {
            int reeferId = message.getInt(Constants.REEFER_ID_KEY);

            if (reeferMasterInventory[reeferId] == null) {
                throw new IllegalStateException("Reefer " + reeferId + " not allocated - request to spoil it is invalid");
            }
            if (reeferMasterInventory[reeferId].getState().equals(ReeferState.State.SPOILT)) {
                return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
            }
            // reefer has spoilt while on a voyage
            ReeferDTO reefer = reeferMasterInventory[reeferId];
            reefer.setState(ReeferState.State.SPOILT);
            updateStore(reefer);
            spoiltTotalCount++;
            Kar.Actors.State.Submap.set(this, Constants.REEFER_STATS_MAP_KEY,Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount ));

  //          JsonObject orderId = Json.createObjectBuilder()
   //                 .add(Constants.ORDER_ID_KEY, reefer.getOrderId()).build();
        } catch( Exception e) {
            e.printStackTrace();
        }

        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
    }

    /**
     * Returns a list of reefers associated with a given order
     *
     * @param orderId
     * @return
     */
    private List<String> orderReeferList(String orderId) {
        return Arrays.stream(reeferMasterInventory).
                filter(Objects::nonNull).
                map(ReeferDTO::getOrderId).
                filter(id -> !(id.trim().isEmpty())).
                filter(id -> id.equals(orderId)).
                collect(Collectors.toList());
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
    private List<ReeferDTO> voyageReefers(String voyageId) {
        return Arrays.stream(reeferMasterInventory).
                filter(Objects::nonNull).
                filter(reefer -> voyageId.equals(reefer.getVoyageId())).
                collect(Collectors.toList());
    }
    private ReeferDTO jsonObjectToReeferDTO(JsonObject reefer) {
        ReeferDTO reeferDTO = new ReeferDTO(reefer.getInt(Constants.REEFER_ID_KEY),
                ReeferState.State.valueOf(reefer.getString(Constants.REEFER_STATE_KEY)),
                reefer.getString(Constants.ORDER_ID_KEY), reefer.getString(Constants.VOYAGE_ID_KEY));
        if (reefer.containsKey(Constants.REEFERS_MAINTENANCE_DATE)) {
            reeferDTO.setMaintenanceReleaseDate(reefer.getString(Constants.REEFERS_MAINTENANCE_DATE));
        }
        return reeferDTO;
    }

    private JsonObject reeferToJsonObject(ReeferDTO reefer) {
        JsonObjectBuilder reeferObjectBuilder = Json.createObjectBuilder();
        reeferObjectBuilder.add(Constants.REEFER_ID_KEY, reefer.getId())
                .add(Constants.REEFER_STATE_KEY, reefer.getState().name())
                .add(Constants.ORDER_ID_KEY, reefer.getOrderId())
                .add(Constants.VOYAGE_ID_KEY, reefer.getVoyageId());
        if (reefer.getMaintenanceReleaseDate() != null) {
            reeferObjectBuilder.add(Constants.REEFERS_MAINTENANCE_DATE, reefer.getMaintenanceReleaseDate());
        }
        return reeferObjectBuilder.build();
    }

    /**
     * @param inventorySize
     */
    private void initMasterInventory(int inventorySize) {
        reeferMasterInventory = new ReeferDTO[inventorySize];
    }

    /**
     * Calls REST service to fetch total number of reefers
     *
     * @return Total number of reefers
     */
    private int getReeferInventorySize() {
        try {
            ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorName, ReeferAppConfig.ScheduleManagerId);
            totalReeferInventory = Kar.Actors.call(scheduleActor, "reeferInventorySize");

        } catch (Exception e) {
            logger.warning("ReeferProvisionerActor.getReeferInventorySize() -  failed - cause:" + e.getMessage());
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.getReeferInventorySize() - Inventory Size:" + totalReeferInventory);
        }
        return ((JsonNumber) totalReeferInventory).intValue();
    }

    private JsonObject getReeferStats() {
        //int totalOnMaintenance = onMaintenanceMap.size();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.getReeferStats() - totalBooked:" + bookedTotalCount + " in-transit:"
                    + inTransitTotalCount + " spoilt:" + spoiltTotalCount + " on-maintenance:" +onMaintenanceTotalCount);
        }
        return Json.createObjectBuilder().add("total", totalReeferInventory).add("totalBooked", bookedTotalCount)
                .add("totalInTransit", inTransitTotalCount).add("totalSpoilt", spoiltTotalCount)
                .add("totalOnMaintenance",onMaintenanceTotalCount).build();
    }

    private void setReeferOnMaintenance(ReeferDTO reefer, String today) {
        // assign reefer off maintenance date which is N days from today. Currently N=2
        reefer.setMaintenanceReleaseDate(today);
        reefer.setState(ReeferState.State.MAINTENANCE);
        updateStore(reefer);
        onMaintenanceMap.put(reefer.getId(), reefer.getId());
        onMaintenanceTotalCount++;

        Kar.Actors.State.Submap.set(this, Constants.REEFER_STATS_MAP_KEY,Constants.TOTAL_ONMAINTENANCE_KEY, Json.createValue(onMaintenanceTotalCount ));

    }

    private void updateStore(ReeferDTO reefer) {
        JsonObject jo = reeferToJsonObject(reefer);
        Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, String.valueOf(reefer.getId()), jo);
    }

    private void unreserveReefer(ReeferDTO reefer, Map<String, JsonValue> onmr) {
        if (reefer.getState().equals(ReeferState.State.MAINTENANCE)) {
            logger.warning(
                    "ReeferProvisionerActor.unreserveReefer() - reefer >>>>>>> " + reefer.getId() + " is on-maintenance unexpectedly - it should be spoilt instead");
        }
        // Reefers can be marked as spoilt only during the voyage. When a voyage ends
        // all spoilt reefers are placed on maintenance.
        if (reefer.getState().equals(ReeferState.State.SPOILT)) {
            reefer.removeFromVoyage();
            reefer.setMaintenanceReleaseDate( TimeUtils.getInstance().getCurrentDate().toString());
            reefer.setState(ReeferState.State.MAINTENANCE);
            onMaintenanceMap.put(reefer.getId(), reefer.getId());
            onMaintenanceTotalCount++;
             onmr.put(String.valueOf(reefer.getId()), reeferToJsonObject(reefer));
             if ( spoiltTotalCount > 0 ) {
                 spoiltTotalCount--;
             }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisioner.unreserveReefer() - spoilt reefer:" + reefer.getId()
                        + " arrived - changed state to OnMaintenance - total spoilt reefers:" + spoiltTotalCount);
            }
        } else {
            reefer.reset();
        }
        if ( inTransitTotalCount > 0 ) {
            inTransitTotalCount--;
        }
    }
    private Long voyageReeferCount(String voyageId) {
        return Arrays.stream(reeferMasterInventory).
                filter(Objects::nonNull).
                filter(reefer -> reefer.getVoyageId().equals(voyageId)).
                peek(reefer -> {
                    // don't overwrite reefer state if it's spoilt
                    if (reefer.getState().equals(ReeferState.State.ALLOCATED)) {
                        // The INTRANSIT state is not currently being used in ReeferProvisioner. If it needs to be
                        // checked save the reefer map to persist the change by first deleting reefer map in
                        // kar storage and saving the updated map.
                        reefer.setState(ReeferState.State.INTRANSIT);
                    }
                }).count();
    }

}
