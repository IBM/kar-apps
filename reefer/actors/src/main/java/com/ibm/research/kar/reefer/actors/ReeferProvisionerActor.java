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
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferAllocator;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.ReeferDTO;

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
public class ReeferProvisionerActor extends BaseActor {
    // global array to hold reefers
    private ReeferDTO[] reeferMasterInventory = null;

    private int bookedTotalCount = 0;
    private int inTransitTotalCount = 0;
    private int spoiltTotalCount = 0;
    private int onMaintenanceTotalCount = 0;
    // ConcurrentHashMap is necessary since there is an internal thread
    // which needs access to this map when publishing changes to the REST
    private Map<Integer, Integer> onMaintenanceMap = new ConcurrentHashMap<>();
    // contains Order-Reefers mapping needed to reduce latency of calls
    private Map<String, Set<String>> order2ReeferMap = new HashMap<>();

    private JsonValue totalReeferInventory;
    private JsonValue depotSize;
    private JsonValue currentInventorySize;
    private static final Logger logger = Logger.getLogger(ReeferProvisionerActor.class.getName());

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
                    System.out.println("ReeferProvisionerActor.activate() " + getId() + "............. restored metrics:" + reeferMetrics);
                    bookedTotalCount = Integer.valueOf(values[0].trim());
                    inTransitTotalCount = Integer.valueOf(values[1].trim());
                    spoiltTotalCount = Integer.valueOf(values[2].trim());
                    onMaintenanceTotalCount = Integer.valueOf(values[3].trim());
                    totalReeferInventory = Json.createValue(Integer.parseInt(values[4].trim()));
                    currentInventorySize = Json.createValue(Integer.parseInt(values[5].trim()));
                    depotSize = Json.createValue(Integer.parseInt(values[6].trim()));
                }
                if (((JsonNumber) totalReeferInventory).intValue() > 0) {
                    restoreReeferInventory(state);
                    restoreOrderToReefersMap();
                    restoreOnMaintenanceMap(state);
                }

            } else {
                t3 = System.currentTimeMillis();
                InventoryConfig ic = getReeferInventory();
                t4 = System.currentTimeMillis();
                initMasterInventory(ic);
                t5 = System.currentTimeMillis();
                saveMetrics();
                t6 = System.currentTimeMillis();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println("ReeferProvisionerActor.activate " + getId() + "- total time:" + (System.currentTimeMillis() - t) + " Fetch All State:" + (t2 - t) + " fetch inv:" + (t4 - t3) + " init:" + (t5 - t4) + " save:" + (t6 - t5));

    }

    private void restoreReeferInventory(Map<String, JsonValue> state) throws Exception {
        long t = System.currentTimeMillis();
        JsonValue jv2 = state.get(Constants.REEFER_MAP_KEY);
        Map<String, JsonValue> reeferInventory = jv2.asJsonObject();
        System.out.println("ReeferProvisionerActor.restoreReeferInventory " + getId() + "- deserialization took  .........." + (System.currentTimeMillis() - t) + " inventory size:" + reeferInventory.size());

        if (logger.isLoggable(Level.INFO)) {
            logger.info("ReeferProvisionerActor.restoreReeferInventory() " + getId() + "- Fetched size of the reefer inventory:"
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
                e.printStackTrace();
            }
        }

        System.out.println("ReeferProvisionerActor.restoreReeferInventory - " + getId() + " inventory size:" + reeferInventory.size() + "  completed in .........." + (System.currentTimeMillis() - t));
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
        System.out.println("ReeferProvisionerActor.restoreOrderToReefersMap - " + getId() + "completed in .........." + (System.currentTimeMillis() - t));
    }

    private void restoreOnMaintenanceMap(Map<String, JsonValue> state) {
        long t = System.currentTimeMillis();
        onMaintenanceMap = Arrays.stream(reeferMasterInventory).
                filter(Objects::nonNull).
                filter(reefer -> reefer.getState().equals(ReeferState.State.MAINTENANCE)).
                collect(Collectors.toMap(ReeferDTO::getId, ReeferDTO::getId));
        System.out.println("ReeferProvisionerActor.restoreOnMaintenanceMap - " + getId() + "completed in .........." + (System.currentTimeMillis() - t));
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
                Instant today = Instant.parse(message.getString(Constants.DATE_KEY));
                List<String> reefers2Remove = getReefersToRemoveFromMaintenance(today);
                for (String reeferId : reefers2Remove) {
                    onMaintenanceMap.remove(Integer.parseInt(reeferId));
                    onMaintenanceTotalCount--;
                }

                Map<String, List<String>> deleteMap = new HashMap<>();
                deleteMap.put(Constants.REEFER_MAP_KEY, reefers2Remove);
                updateStore(deleteMap, Collections.emptyMap()); //new HashMap<String, JsonValue>());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "ReeferProvisioner.releaseReefersfromMaintenance() - Error ", e);
            e.printStackTrace();
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
            // idempotence check - get a list of voyage reefers that are in ALLOCATED state.
            // If kar repeats this call as part of fault tolerance (at least once contract)
            // the list may be empty depending what happened when the process died.
            List<ReeferDTO> voyageReefers = voyageAllocatedReefers(voyageId);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisionerActor.voyageReefersDeparted() " + getId() + "- message:" + message + " update reefers in transit:" + voyageReefers.size());
            }
            if (voyageReefers.size() > 0) {

                if ((bookedTotalCount - voyageReefers.size()) >= 0) {
                    bookedTotalCount -= voyageReefers.size();
                } else {
                    logger.log(Level.WARNING, "ReeferProvisioner.voyageReefersDeparted() " + getId() + "- unexpected underflow of bookedTotalCount which will result in negative value - voyage:" + voyageId + " booked:" + bookedTotalCount + " voyage reefer count:" + voyageReefers.size());
                    bookedTotalCount = 0;
                }
                inTransitTotalCount += voyageReefers.size();
                updateStore(deleteMap(voyageReefers), Collections.emptyMap());

                Map<String, Set<Integer>> orders = new HashMap<>();
                for (ReeferDTO reefer : voyageReefers) {
                    reeferMasterInventory[reefer.getId()] = null;
                    Set<Integer> reefers;
                    if (orders.containsKey(reefer.getOrderId())) {
                        reefers = orders.get(reefer.getOrderId());
                    } else {
                        reefers = new HashSet<>();
                        orders.put(reefer.getOrderId(), reefers);
                    }
                    reefers.add(reefer.getId());
                }
                JsonArrayBuilder jab = Json.createArrayBuilder();
                StringBuilder builder = new StringBuilder();
                for (Map.Entry<String, Set<Integer>> entry : orders.entrySet()) {
                    builder.setLength(0);
                    JsonObjectBuilder job = Json.createObjectBuilder();
                    for (Integer reeferId : entry.getValue()) {
                        builder.append(reeferId).append(",");
                    }
                    job.add(Constants.ANOMALY_TARGET_KEY, entry.getKey()).
                            add(Constants.ANOMALY_TARGET_TYPE_KEY, Json.createValue(AnomalyManagerActor.ReeferLocation.LocationType.ORDER.getType())).
                            add(Constants.REEFERS_KEY, builder.toString());
                    jab.add(job);
                }

                JsonObjectBuilder job = Json.createObjectBuilder();
                job.add(Constants.TARGETS_KEY, jab.build());
                ActorRef anomalyManagerActor = Kar.Actors.ref(ReeferAppConfig.AnomalyManagerActorName, ReeferAppConfig.AnomalyManagerId);
                Kar.Actors.tell(anomalyManagerActor, "voyageDeparted", job.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }

    @Remote
    public void voyageReefersArrived(JsonObject message) {
        try {
            String voyageId = message.getString(Constants.VOYAGE_ID_KEY);
            String arrivalDate = message.getString(Constants.VOYAGE_ARRIVAL_DATE_KEY);
            String[] reeferIds = message.getString(Constants.REEFERS_KEY).split(",");
            String[] spoiltReeferIds = message.getString(Constants.SPOILT_REEFERS_KEY).split(",");

            List<ReeferDTO> updateList = new ArrayList<>(reeferIds.length);
            int collisionCount = 0;
            StringBuilder builder = new StringBuilder();
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for (String reeferId : reeferIds) {
                int idx = Integer.valueOf(reeferId);
                if (reeferMasterInventory[idx] != null &&
                        reeferMasterInventory[idx].getState().equals(ReeferState.State.ALLOCATED)) {
                    collisionCount++;
                }
                builder.append(Integer.valueOf(reeferId)).append(",");
                if (arrivedSpoilt(reeferId, spoiltReeferIds)) {
                    reeferMasterInventory[idx] = new ReeferDTO(Integer.valueOf(reeferId), ReeferState.State.SPOILT);
                    Map<String, JsonValue> arrivedOnMaintenanceMap = new HashMap<>();
                    unreserveReefer(reeferMasterInventory[idx], arrivedOnMaintenanceMap, arrivalDate);
                } else {
                    reeferMasterInventory[idx] = new ReeferDTO(Integer.valueOf(reeferId), ReeferState.State.UNALLOCATED);
                }
                updateList.add(reeferMasterInventory[idx]);
            }
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.ANOMALY_TARGET_KEY, getId()).
                    add(Constants.ANOMALY_TARGET_TYPE_KEY, Json.createValue(AnomalyManagerActor.ReeferLocation.LocationType.DEPOT.getType())).
                    add(Constants.REEFERS_KEY, builder.toString());
            jab.add(job);
            if (collisionCount > 0) {
                System.out.println("ReeferProvisionerActor.voyageReefersArrived() !!!!!!!!!!! " + getId() + " detected " + collisionCount + " when transferring reefers from voyage:" + voyageId);
            }
            updateStore(Collections.emptyMap(), reeferMap(updateList));
            currentInventorySize = Json.createValue(((JsonNumber) currentInventorySize).intValue() + reeferIds.length);
            saveMetrics();

            JsonObjectBuilder anomalyMgrMsg = Json.createObjectBuilder();
            anomalyMgrMsg.add(Constants.TARGETS_KEY, jab.build());
            ActorRef anomalyManagerActor = Kar.Actors.ref(ReeferAppConfig.AnomalyManagerActorName, ReeferAppConfig.AnomalyManagerId);
            Kar.Actors.tell(anomalyManagerActor, "voyageArrived", anomalyMgrMsg.build());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }

    private boolean arrivedSpoilt(String reeferId, String[] spoiltReefers) {
        for (String spoiltReefer : spoiltReefers) {
            if (spoiltReefer.equals(reeferId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reserve enough reefers to fill with order products.
     *
     * @param message
     * @return
     */
    @Remote
    public JsonObject bookReefers(JsonObject message) {
        Order order = null;
        try {
            // wrap Json with POJO
            order = new Order(message);
            // idempotence check if this method is being called more than once for the same order
            if (order2ReeferMap.containsKey(order.getId())) {
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
            currentInventorySize = Json.createValue(((JsonNumber) currentInventorySize).intValue() - rids.size());
            order2ReeferMap.put(order.getId().trim(), rids);
            // persists allocated reefers
            bookedTotalCount += orderReefers.size();
            updateStore(Collections.emptyMap(), reeferMap(orderReefers));
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisionerActor.bookReefers())- Order:" + order.getId() + " reefer count:"
                        + orderReefers.size());
            }
            JsonArrayBuilder jsonReefers = Json.createArrayBuilder(rids);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).
                    add(Constants.DEPOT_KEY, getId()).
                    add(Constants.REEFERS_KEY, Json.createValue(orderReefers.size())).
                    add(Constants.ORDER_REEFERS_KEY, jsonReefers.build()).
                    add(JsonOrder.OrderKey, order.getAsJsonObject()).build();

        } catch (Throwable e) {

            int actual = 0;
            for (int i = 0; i < reeferMasterInventory.length; i++) {
                if (reeferMasterInventory[i] != null && reeferMasterInventory[i].getState().equals(ReeferState.State.UNALLOCATED)) {
                    actual++;
                }
            }
            logger.log(Level.WARNING, "ReeferProvisioner.bookReefers() FAILED !!!!!!!!!!!!!!! - Depot:" + getId() + " current Inventory:" + ((JsonNumber) currentInventorySize).intValue() + " - Actual Available Reefer Count:" + actual + " Order reefers:" + order.getProductQty() / 1000 + " Error ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, "").build();
        }
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
     * Release given reefers back to inventory
     *
     * @param message
     * @return
     */
    @Remote
    public void releaseVoyageReefers(JsonObject message) {

        try {
            String voyageId = message.getString(Constants.VOYAGE_ID_KEY);
            JsonArray orders = message.getJsonArray(Constants.VOYAGE_ORDERS_KEY);
            String arrivalDate = message.getString(Constants.VOYAGE_ARRIVAL_DATE_KEY);
            Map<String, JsonValue> arrivedOnMaintenanceMap = new HashMap<>();
            // get a list of reefers associated with a given voyage. Exclude reefers
            // which arrived in spoilt state. Those will be placed on maintenance
            // and need to remain in inventory.
            List<String> reefers2Remove = new LinkedList<>();
            if (orders != null) {
                reefers2Remove = getArrivedReefers(orders, arrivedOnMaintenanceMap, arrivalDate);
            }


            if (!reefers2Remove.isEmpty()) {
                List<String> orderList = orders.stream().filter(Objects::nonNull).map(jv -> ((JsonString) jv).getString()).collect(Collectors.toList());
                for (String orderId : orderList) {
                    if (order2ReeferMap.containsKey(orderId.trim())) {
                        order2ReeferMap.remove(orderId.trim());
                    }
                }
                order2ReeferMap.keySet().removeAll(orderList);
            }
            Map<String, List<String>> subMapRemove = new HashMap<>();
            subMapRemove.put(Constants.REEFER_MAP_KEY, reefers2Remove);
            updateStore(subMapRemove, arrivedOnMaintenanceMap);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
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
     *
     * @param orders                  - list of arrived orders
     * @param arrivedOnMaintenanceMap -
     * @return
     */
    private List<String> getArrivedReefers(JsonArray orders, Map<String, JsonValue> arrivedOnMaintenanceMap, String arrivalDate) {
        List<String> reefers2Remove = new LinkedList<>();
        orders.forEach(orderId -> {
            String id = ((JsonString) orderId).getString();
            if (order2ReeferMap.containsKey(id)) {
                Set<String> reefers = order2ReeferMap.get(id);
                reefers.forEach(reeferId -> {
                    int inx = Integer.parseInt(reeferId);
                    unreserveReefer(reeferMasterInventory[inx], arrivedOnMaintenanceMap, arrivalDate);
                    if (!reeferMasterInventory[inx].getState().equals(ReeferState.State.MAINTENANCE)) {
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
            System.out.println("ReeferProvisionerActor.reeferAnomaly() - " + getId() + " >>>>>>>>>>>> REEFER:" + reeferId + " Not in inventory - DEPARTED ALREADY");
        } else if (reeferMasterInventory[reeferId].alreadyBad()) {
            // either on maintenance already or spoilt
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferProvisioner.reeferAnomaly() - " + reeferId + " already bad");
            }
            System.out.println("ReeferProvisioner.reeferAnomaly() - " + getId() + reeferId + " already bad");
        } else if (reeferMasterInventory[reeferId].assignedToOrder()) {
            // notify order actor of anomaly
            // the order will call back with request to replace it or mark it spoilt
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId
                        + " assigned to order: " + reeferMasterInventory[reeferId].getOrderId());
            }
        } else {
            setReeferOnMaintenance(reeferMasterInventory[reeferId], message.getString(Constants.DATE_KEY));
            Map<String, JsonValue> updateMap = new HashMap<>();
            updateMap.put(String.valueOf(reeferId), reeferToJsonObject(reeferMasterInventory[reeferId]));
            updateStore(Collections.emptyMap(), updateMap);

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
        int reeferId = message.getInt(Constants.SPOILT_REEFER_KEY);
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
        if (order2ReeferMap.containsKey(reefer.getOrderId())) {
            Set<String> reeferIds = order2ReeferMap.get(reefer.getOrderId());
            // remove spoilt
            reeferIds.remove(String.valueOf(reeferId));
            // add replacement
            reeferIds.add(String.valueOf(replacementReeferList.get(0).getId()));
        }
        reefer.removeFromVoyage();
        setReeferOnMaintenance(reefer, message.getString(Constants.DATE_KEY));

        Map<String, JsonValue> updateMap = new HashMap<>();
        updateMap.put(String.valueOf(reeferId), reeferToJsonObject(replacementReeferList.get(0)));
        updateStore(Collections.emptyMap(), updateMap);
        System.out.println("ReeferProvisioner.reeferReplacement() - reefer " + reeferId + " replaced with:" + replacementReeferList.get(0).getId());
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
            spoiltTotalCount++;
            Map<String, JsonValue> updateMap = new HashMap<>();
            updateMap.put(String.valueOf(reefer.getId()), reeferToJsonObject(reefer));
            updateStore(Collections.emptyMap(), updateMap);
        } catch (Exception e) {
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
    /**
     * @param inventorySize
     */
    private void initMasterInventory(int inventorySize) {
        reeferMasterInventory = new ReeferDTO[inventorySize];
    }

    private void initMasterInventory(InventoryConfig config) {
        try {
            reeferMasterInventory = new ReeferDTO[config.getGlobalInventorySize()];
            JsonObjectBuilder job = Json.createObjectBuilder();
            Map<String, JsonValue> map = new HashMap<>(config.getUpperBound() - config.getLowerBound() + 1);
            JsonObjectBuilder reeferObjectBuilder = Json.createObjectBuilder();
            for (int id = config.getLowerBound(); id <= config.getUpperBound(); id++) {
                reeferMasterInventory[id] = new ReeferDTO(id, ReeferState.State.UNALLOCATED);
                // JsonObjectBuilder is expensive object to create. Reuse single instance to improve
                // performance. In the <code>reeferToJsonObject()</code> the instance is used to
                // create a JsonObject and when its build() is called it internally clears its state
                // and thus allows for reuse.
                map.put(String.valueOf(id), reeferToJsonObject(reeferMasterInventory[id], reeferObjectBuilder));
            }
            long t = System.currentTimeMillis();
            updateStore(Collections.emptyMap(), map);
            System.out.println("ReeferProvisionerActor.initMasterInventory::::::" + getId() + " initializing inventory ::: lower:" + config.lowerBound + " upper:" + config.upperBound + " redis save time:" + (System.currentTimeMillis() - t));
        } catch (Exception e) {
            e.printStackTrace();
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
            ActorRef depotManagerActor = Kar.Actors.ref(ReeferAppConfig.DepotManagerActorName, ReeferAppConfig.DepotManagerId);
            JsonValue reply = Kar.Actors.call(depotManagerActor, "depotInventory", Json.createValue(getId()));
            System.out.println("ReeferProvisionerActor.getReeferInventory() ID:" + getId() + "- Depot Configuration:" + reply);
            totalReeferInventory = reply.asJsonObject().getJsonNumber(Constants.TOTAL_REEFER_COUNT_KEY);
            depotSize = reply.asJsonObject().getJsonNumber(Constants.DEPOT_SIZE_KEY);
            currentInventorySize = depotSize;
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisionerActor.getReeferInventory() - Inventory Size:" + totalReeferInventory);
            }
            return new InventoryConfig(((JsonNumber) depotSize).intValue(), ((JsonNumber) totalReeferInventory).intValue(),
                    reply.asJsonObject().getInt("reefer-id-lower-bound"),
                    reply.asJsonObject().getInt("reefer-id-upper-bound"));
        } catch (Exception e) {
            logger.warning("ReeferProvisionerActor.getReeferInventory() -  failed - cause:" + e.getMessage());
            throw e;
        }

    }

    private JsonObject getReeferStats() {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.getReeferStats() - totalBooked:" + bookedTotalCount + " in-transit:"
                    + inTransitTotalCount + " spoilt:" + spoiltTotalCount + " on-maintenance:" + onMaintenanceTotalCount);
        }
        return Json.createObjectBuilder().add("total", totalReeferInventory).add("totalBooked", bookedTotalCount)
                .add("totalInTransit", inTransitTotalCount).add("totalSpoilt", spoiltTotalCount)
                .add("totalOnMaintenance", onMaintenanceTotalCount).build();
    }

    private void setReeferOnMaintenance(ReeferDTO reefer, String today) {
        // assign reefer off maintenance date which is N days from today. Currently N=2
        reefer.setMaintenanceReleaseDate(today);
        reefer.setState(ReeferState.State.MAINTENANCE);

        onMaintenanceMap.put(reefer.getId(), reefer.getId());
        onMaintenanceTotalCount++;

    }


    private void updateStore(Map<String, List<String>> deleteMap, Map<String, JsonValue> updateMap) {
        String metrics = getMetricsString();
        Map<String, JsonValue> actorStateMap = new HashMap<>();
        actorStateMap.put(Constants.REEFER_METRICS_KEY, Json.createValue(metrics));

        Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
        subMapUpdates.put(Constants.REEFER_MAP_KEY, updateMap);
        Kar.Actors.State.update(this, Collections.emptyList(), deleteMap, actorStateMap, subMapUpdates);
    }


    private void unreserveReefer(ReeferDTO reefer, Map<String, JsonValue> onmr, String arrivalDate) {
        if (reefer.getState().equals(ReeferState.State.MAINTENANCE)) {
            logger.warning(
                    "ReeferProvisionerActor.unreserveReefer() - reefer >>>>>>> " + reefer.getId() + " is on-maintenance unexpectedly - it should be spoilt instead");
        }
        // Reefers can be marked as spoilt only during the voyage. When a voyage ends
        // all spoilt reefers are placed on maintenance.
        if (reefer.getState().equals(ReeferState.State.SPOILT)) {
            reefer.removeFromVoyage();
            reefer.setMaintenanceReleaseDate(arrivalDate);
            reefer.setState(ReeferState.State.MAINTENANCE);
            onMaintenanceMap.put(reefer.getId(), reefer.getId());
            onMaintenanceTotalCount++;
            onmr.put(String.valueOf(reefer.getId()), reeferToJsonObject(reefer));
            if (spoiltTotalCount > 0) {
                spoiltTotalCount--;
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisioner.unreserveReefer() - spoilt reefer:" + reefer.getId()
                        + " arrived - changed state to OnMaintenance - total spoilt reefers:" + spoiltTotalCount);
            }
        } else {
            reefer.reset();
        }
        if (inTransitTotalCount > 0) {
            inTransitTotalCount--;
        }
    }

    private List<ReeferDTO> voyageAllocatedReefers(String voyageId) {
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
                }).collect(Collectors.toList());
    }

    private void saveMetrics() {
        String metrics = getMetricsString();
        Kar.Actors.State.set(this, Constants.REEFER_METRICS_KEY, Json.createValue(metrics));
    }

    private String getMetricsString() {
        return String.format("%d:%d:%d:%d:%d:%d:%d", bookedTotalCount, inTransitTotalCount,
                spoiltTotalCount, onMaintenanceTotalCount, ((JsonNumber) totalReeferInventory).intValue(),
                ((JsonNumber) currentInventorySize).intValue(), ((JsonNumber) depotSize).intValue());
    }

    private class InventoryConfig {
        private int totalSize;
        private int depotSize;
        private int lowerBound;
        private int upperBound;

        public InventoryConfig(int depotSize, int totalSize, int lowerBound, int upperBound) {
            this.depotSize = depotSize;
            this.totalSize = totalSize;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        public int getDepotSize() {
            return depotSize;
        }

        public int getGlobalInventorySize() {
            return totalSize;
        }

        public int getLowerBound() {
            return lowerBound;
        }

        public int getUpperBound() {
            return upperBound;
        }
    }
}
