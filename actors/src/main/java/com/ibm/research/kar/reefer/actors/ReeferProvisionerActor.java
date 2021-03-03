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
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.ReeferDTO;

import javax.json.*;
import javax.ws.rs.core.Response;
import java.time.Instant;
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
    private AtomicBoolean valuesChanged = new AtomicBoolean();
    private AtomicInteger bookedTotalCount = new AtomicInteger();
    private AtomicInteger inTransitTotalCount = new AtomicInteger();
    private AtomicInteger spoiltTotalCount = new AtomicInteger();
    private Map<String, String> onMaintenanceMap = new ConcurrentHashMap<>();
    //private Map<String, String> onMaintenanceMap = new ConcurrentHashMap<>();
    private JsonValue totalReeferInventory;
    private static final Logger logger = Logger.getLogger(ReeferProvisionerActor.class.getName());

    @Activate
    public void activate() {
        //     if inventory size unknown, get it from Rest if possible and init all state
        //     else set size to 0, try to lazily init state later

        // fetch actor state from Kar storage
        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        try {
            if (!state.isEmpty()) {
                // restore counts from Kar persistent storage
                if (state.containsKey(Constants.TOTAL_BOOKED_KEY)) {
                    bookedTotalCount = new AtomicInteger(((JsonNumber) state.get(Constants.TOTAL_BOOKED_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_INTRANSIT_KEY)) {
                    inTransitTotalCount = new AtomicInteger(((JsonNumber) state.get(Constants.TOTAL_INTRANSIT_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_SPOILT_KEY)) {
                    spoiltTotalCount = new AtomicInteger(((JsonNumber) state.get(Constants.TOTAL_SPOILT_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_REEFER_COUNT_KEY)) {
                    totalReeferInventory = state.get(Constants.TOTAL_REEFER_COUNT_KEY);
                    // fetch reefer map
                    Map<String, JsonValue> reeferInventory = Kar.Actors.State.Submap.getAll(this, Constants.REEFER_MAP_KEY);
                    if (logger.isLoggable(Level.INFO)) {
                        logger.info("ReeferProvisionerActor.init() - Fetched size of the reefer inventory:"
                                + reeferInventory.size());
                    }
                    // allocate reefer array which is used to allocate/deallocate reefers
                    reeferMasterInventory = new ReeferDTO[((JsonNumber) totalReeferInventory).intValue()];
                    // copy reefer objects to reefer array. The array is needed to allocate/deallocate reefers
                    for (JsonValue value : reeferInventory.values()) {
                        if (value == null) {
                            continue;
                        }
                        JsonObject reefer = value.asJsonObject();
                        // use reeferId as an index in the array
                        reeferMasterInventory[reefer.getInt(Constants.REEFER_ID_KEY)] = jsonObjectToReeferDTO(reefer);
                    }
                }
                // restore onMaintenanceMap
                if (state.containsKey(Constants.ON_MAINTENANCE_PROVISIONER_LIST)) {
                    JsonValue jv = state.get(Constants.ON_MAINTENANCE_PROVISIONER_LIST);
                    // since we already have entire state by calling actorGetAllState() above we can
                    // deserialize on maintenance reefers using Jackson's ObjectMapper. Alternatively, one can
                    // use Kar.actorSubMapGet() which is an extra call.
                    ObjectMapper mapper = new ObjectMapper();
                    // deserialize json reefers into a HashMap
                    onMaintenanceMap = mapper.readValue(jv.toString(), HashMap.class);
                }
            } else {
                initMasterInventory(getReeferInventorySize());
                Kar.Actors.State.set(this, Constants.TOTAL_REEFER_COUNT_KEY, totalReeferInventory);
            }

            // update thread. Sends reefer count updates to the REST
            TimerTask timerTask = new RestUpdateTask();
            // running timer task as daemon thread. It updates
            // REST reefer counts at regular intervals (currently 100ms)
            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(timerTask, 0, 100);
        } catch (Exception e) {
            logger.log(Level.WARNING, "ReeferProvisioner.activate() - Error ", e);
        }

    }

    /**
     * Called to get current reefer counts.
     *
     * @param message
     * @return - reefer counts
     */
    @Remote
    public JsonObject getStats(JsonObject message) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "ReeferProvisionerActor.getStats() - entry");
        }
        try {
            return getReeferStats();
        } catch (Exception e) {
            logger.log(Level.WARNING, "ReeferProvisioner.getStats() - Error ", e);
            return Json.createObjectBuilder().add("total", -1).add("totalBooked", -1)
                    .add("totalInTransit", -1).add("totalSpoilt", -10)
                    .add("totalOnMaintenance", -1).build();
        } finally {

            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "ReeferProvisionerActor.getStats() - exit");
            }

        }
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

                Iterator<String> it = onMaintenanceMap.values().iterator();
                while (it.hasNext()) {
                    ReeferDTO reefer = reeferMasterInventory[Integer.parseInt(it.next())];
                    // release reefer from maintenance if today matches reefer's assigned release date
                    if (reefer != null && releaseFromMaintenanceToday(reefer, today)) {
                        releaseFromMaintenance(reefer, today);
                        // remove reefer from onMaintenance map
                        it.remove();
                    }
                }
                // forces update thread to send reefer counts
                valuesChanged.set(true);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "ReeferProvisioner.releaseReefersfromMaintenance() - Error ", e);
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "ReeferProvisionerActor.releaseReefersfromMaintenance() - exit");
            }
        }
        return Json.createObjectBuilder().build();
    }

    @Remote
    public JsonObject voyageReefersDeparted(JsonObject message) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "ReeferProvisionerActor.voyageReefersDeparted() - entry");
        }
        JsonObjectBuilder reply = Json.createObjectBuilder();
        try {
            String voyageId = message.getString(Constants.VOYAGE_ID_KEY);
            /*
            // count voyage reefers in transit
            int voyageReefersInTransitCount = 0;
            for (ReeferDTO reefer : reeferMasterInventory) {
                if (reefer != null) {
                    if (reefer.getVoyageId().equals(voyageId)) {
                        if (reefer.getState().equals(ReeferState.State.ALLOCATED)) {
                            voyageReefersInTransitCount++;
                            // The INTRANSIT state is not currently being used in ReeferProvisioner. If it needs to be
                            // checked save the reefer map to persist the change by first deleting reefer map in
                            // kar storage and saving the updated map.
                            reefer.setState(ReeferState.State.INTRANSIT);
                        }
                    }
                }
            }

             */
            List<ReeferDTO> voyageReefersInTransitCount = Arrays.stream(reeferMasterInventory).
                    filter(Objects::nonNull).
                    filter(reefer -> reefer.getVoyageId().equals(voyageId)).
                    filter(reefer ->reefer.getState().equals(ReeferState.State.ALLOCATED)).
                    map( reefer -> {
                        // The INTRANSIT state is not currently being used in ReeferProvisioner. If it needs to be
                        // checked save the reefer map to persist the change by first deleting reefer map in
                        // kar storage and saving the updated map.
                        reefer.setState(ReeferState.State.INTRANSIT);
                        return reefer;
                    }).collect(Collectors.toList());

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisionerActor.voyageReefersDeparted() - message:" + message + " update reefers in transit:" + voyageReefersInTransitCount);
            }

             if ( !voyageReefersInTransitCount.isEmpty() ) {
                 if ((bookedTotalCount.get() - voyageReefersInTransitCount.size()) >= 0) {
                     // subtract from booked and add to in-transit
                     bookedTotalCount.addAndGet(-voyageReefersInTransitCount.size());
                 } else {
                     logger.log(Level.WARNING, "ReeferProvisioner.voyageReefersDeparted() - unexpected underflow of bookedTotalCount which will result in negative value");
                     bookedTotalCount.set(0);
                 }
                 inTransitTotalCount.addAndGet(voyageReefersInTransitCount.size());
                 JsonObjectBuilder job = Json.createObjectBuilder();
                 job.add(Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount.intValue())).
                         add(Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount.intValue()));
                 Kar.Actors.State.set(this, job.build());
                 valuesChanged.set(true);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "ReeferProvisioner.voyageReefersDeparted() - Error ", e);
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "ReeferProvisionerActor.voyageReefersDeparted() - exit");
            }
        }

        return reply.build();
    }

    /**
     * Reserve enough reefers to fill with order products.
     *
     * @param message
     * @return
     */
    @Remote
    public JsonObject bookReefers(JsonObject message) {
        try {
            // wrap Json with POJO
            JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
            // check if this method is being called more than once for the same order
            List<String> ids = orderReeferList(order.getId());
            if ( !ids.isEmpty() ) {
                return Json.createObjectBuilder().add("status", "OK").add("reefers", Json.createArrayBuilder(ids).build())
                        .add(JsonOrder.OrderKey, order.getAsObject()).build();
            }
            if (order.containsKey(JsonOrder.ProductQtyKey) ) {
                ReeferState.State[] allocationStatus = new ReeferState.State[reeferMasterInventory.length];
                for( int i=0; i < allocationStatus.length; i++) {
                    if ( reeferMasterInventory[i] != null ) {
                        allocationStatus[i] = reeferMasterInventory[i].getState();
                    }
                }
                // allocate enough reefers to cary products in the order
                List<ReeferDTO> orderReefers = ReeferAllocator.allocateReefers(reeferMasterInventory, order.getProductQty(),
                        order.getId(), order.getVoyageId());
                // need an array to hold reeferIds which will be included in the reply
                JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                Map<String, JsonValue> map = new HashMap<>();
                // book each reefer
                for (ReeferDTO reefer : orderReefers) {
                    arrayBuilder.add(reefer.getId());
                    map.put(String.valueOf(reefer.getId()), reeferToJsonObject(reefer));
                }
                Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, map);
                bookedTotalCount.addAndGet(orderReefers.size());
                Kar.Actors.State.set(this, Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount.intValue()));
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("ReeferProvisionerActor.bookReefers())- Order:" + order.getId() + " reefer count:"
                            + orderReefers.size());
                }
                // forces update thread to send reefer counts
                valuesChanged.set(true);
                return Json.createObjectBuilder().add("status", "OK").add("reefers", arrayBuilder.build())
                        .add(JsonOrder.OrderKey, order.getAsObject()).build();
            } else {
                return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "ProductQuantityMissing")
                        .add(JsonOrder.IdKey, order.getId()).build();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "ReeferProvisioner.bookReefers() - Error ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(JsonOrder.IdKey, "").build();
        }
    }

    /**
     * Release given reefers back to inventory
     *
     * @param message
     * @return
     */
    @Remote
    public JsonObject releaseVoyageReefers(JsonObject message) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "ReeferProvisionerActor.releaseVoyageReefers() - entry");
        }
        JsonObjectBuilder reply = Json.createObjectBuilder();
        try {
            String voyageId = message.getString(Constants.VOYAGE_ID_KEY);
            List<String> reefers2Remove = Arrays.stream(reeferMasterInventory).
                    filter(Objects::nonNull).
                    filter(reefer -> reefer.getVoyageId().equals(voyageId)).
                    map(reefer -> {
                        unreserveReefer(reefer.getId());
                        return String.valueOf(reefer.getId());
                    }).
                    filter(rid -> !ReeferState.State.MAINTENANCE.equals(reeferMasterInventory[Integer.parseInt(rid)].getState())).
                    collect(Collectors.toList());

            // remove reefers which just arrived. The reefer inventory should only contain
            // reefers which are booked or in-transit.
            Kar.Actors.State.Submap.removeAll(this, Constants.REEFER_MAP_KEY, reefers2Remove);
            // forces update thread to send reefer counts
            valuesChanged.set(true);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisionerActor.releaseVoyageReefers() - released reefers " + " total booked: "
                        + bookedTotalCount.get() + " totalInTransit:" + inTransitTotalCount.get());
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "ReeferProvisioner.releaseVoyageReefers() - Error ", e);
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "ReeferProvisionerActor.releaseVoyageReefers() - exit");
            }
        }

        return reply.build();
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
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.reeferAnomaly() - entry with reeferId " + reeferId);
        }

        try {
            // check if given reeferIs is within valid range. The reeferId is used as an index in
            // reeferMasterInventory array which is 0-based.
            if (reeferId >= 0 && reeferId < reeferMasterInventory.length) {
                // check if reefer has never been allocated. If so just mark it on maintenance
                if (reeferMasterInventory[reeferId] == null) {
                    reeferMasterInventory[reeferId] = new ReeferDTO(reeferId, ReeferState.State.MAINTENANCE,
                            "", "");
                    Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, String.valueOf(reeferId),
                            reeferToJsonObject(reeferMasterInventory[reeferId]));
                    setReeferOnMaintenance(reeferMasterInventory[reeferId], message.getString(Constants.DATE_KEY));
                    if (logger.isLoggable(Level.INFO)) {
                        logger.info("ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId
                                + " allocated on Maintenance");
                    }
                    valuesChanged.set(true);
                } else {
                    // check if reefer went bad already. The simulator randomly selects reefers for
                    // anomalies and may select same reefer within a short amount of time
                    ReeferDTO reefer = reeferMasterInventory[reeferId];
                    if (reefer.getState().equals(ReeferState.State.MAINTENANCE) || reefer.getState().equals(ReeferState.State.SPOILT)) {
                        logger.log(Level.WARNING, "ReeferProvisioner.reeferAnomaly() - " + reeferId + " already bad");
                    } else {
                        // Check if reefer is not assigned to an order
                        if (reefer.getOrderId() == null || reefer.getOrderId().length() == 0) {
                            if (logger.isLoggable(Level.FINE)) {
                                logger.fine("ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId
                                        + " not currently assigned to order");
                            }

                            setReeferOnMaintenance(reefer, message.getString(Constants.DATE_KEY));
                            valuesChanged.set(true);

                            if (logger.isLoggable(Level.INFO)) {
                                logger.info("ReeferProvisionerActor.reeferAnomaly() - id:" + getId()
                                        + " added reefer:" + reeferId + " to "
                                        + Constants.ON_MAINTENANCE_PROVISIONER_LIST + " Map"
                                        + " onMaintenance date:" + message.getString(Constants.DATE_KEY));
                            }
                        } else {
                            // notify order of anomaly for reeferId
                            // the order will call back with request to replace it or mark it spoilt
                            if (logger.isLoggable(Level.INFO)) {
                                logger.info("ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId
                                        + " assigned to order: " + reefer.getOrderId());
                            }
                            ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderActorName, reefer.getOrderId());
                            Kar.Actors.tell(orderActor, "anomaly", message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "ReeferProvisioner.reeferAnomaly() - Error ", e);
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ReeferProvisionerActor.reeferAnomaly() - exit");
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
            throw new IllegalStateException("Reefer " + reeferId + " not allocated - request to replace it is invalid");
        }
        ReeferDTO reefer = reeferMasterInventory[reeferId];
        if (!reefer.getState().equals(ReeferState.State.MAINTENANCE)) {
            List<ReeferDTO> replacementReeferList = ReeferAllocator.allocateReefers(reeferMasterInventory,
                    Constants.REEFER_CAPACITY, reefer.getOrderId(), reefer.getVoyageId());
            if (!replacementReeferList.isEmpty()) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("ReeferProvisionerActor.replaceSpoiltReefer() - replacing reeferId:"
                            + reefer.getId() + " with:" + replacementReeferList.get(0).getId());
                }
                setReeferOnMaintenance(reefer, message.getString(Constants.DATE_KEY));
                updateStore(replacementReeferList.get(0));
                // forces update thread to send reefer counts
                valuesChanged.set(true);
                return Json.createObjectBuilder()
                        .add(Constants.REEFER_REPLACEMENT_ID_KEY, replacementReeferList.get(0).getId())
                        .add(Constants.STATUS_KEY, Constants.OK).build();
            } else {
                throw new IllegalStateException("Unable to allocated replacement reefer for " + reeferId );
            }
        }

        return Json.createObjectBuilder()
                .add(Constants.STATUS_KEY, Constants.FAILED).build();
    }


    /**
     * Handle Order request to mark reefer spoilt
     *
     * @param message
     */
    @Remote
    public JsonObject reeferSpoilt(JsonObject message) {
        // check if given reeferIs is within valid range. The reeferId is used as an index in
        // reeferMasterInventory array which is 0-based.
        int reeferId = message.getInt(Constants.REEFER_ID_KEY);

        if (reeferMasterInventory[reeferId] == null) {
            throw new IllegalStateException("Reefer " + reeferId + " not allocated - request to spoil it is invalid");
        }
        // reefer has spoilt while on a voyage
        ReeferDTO reefer = reeferMasterInventory[reeferId];
        if (!reefer.getState().equals(ReeferState.State.SPOILT)) {
            try {

                changeReeferState(reefer, reeferId, ReeferState.State.SPOILT);
                updateStore(reefer);
                spoiltTotalCount.incrementAndGet();
                Kar.Actors.State.set(this, Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount.intValue()));
                JsonObject orderId = Json.createObjectBuilder()
                        .add(Constants.ORDER_ID_KEY, reefer.getOrderId()).build();
                Kar.Services.post(Constants.REEFERSERVICE, "/orders/spoilt", orderId);
                // forces update thread to send reefer counts
                valuesChanged.set(true);
                return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
            } catch (Exception e) {
                logger.log(Level.WARNING, "ReeferProvisioner.reeferSpoilt() - Error ", e);
            }
        } else {
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
        }
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.FAILED).build();
    }
    private List<String> orderReeferList(String orderId) {
       // List<String> voyageIdList =
                return Arrays.stream(reeferMasterInventory).
                        filter(Objects::nonNull).
                        map(ReeferDTO::getOrderId).
                        filter(id -> !(id.trim().isEmpty())).
                        filter(id -> id.equals(orderId)).
                        collect(Collectors.toList());
      //  return voyageIdList.isEmpty();
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
        if (reefer.containsKey(Constants.REEFERS_MAINTENANCE_DATE)) {
            reeferDTO.setMaintenanceReleaseDate(reefer.getString(Constants.REEFERS_MAINTENANCE_DATE));
        }
        return reeferDTO;
    }

    private JsonObject reeferToJsonObject(ReeferDTO reefer) {
        JsonObjectBuilder reeferObjectBuilder = Json.createObjectBuilder();
        if (reefer != null) {
            reeferObjectBuilder.add(Constants.REEFER_ID_KEY, reefer.getId())
                    .add(Constants.REEFER_STATE_KEY, reefer.getState().name())
                    .add(Constants.ORDER_ID_KEY, reefer.getOrderId())
                    .add(Constants.VOYAGE_ID_KEY, reefer.getVoyageId());
            if (reefer.getMaintenanceReleaseDate() != null) {
                reeferObjectBuilder.add(Constants.REEFERS_MAINTENANCE_DATE, reefer.getMaintenanceReleaseDate());
            }

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
            Response response = Kar.Services.get(Constants.REEFERSERVICE, "reefers/inventory/size");
            totalReeferInventory = response.readEntity(JsonValue.class);
        } catch (Exception e) {
            logger.warning("ReeferProvisionerActor.getReeferInventorySize() - REST call reefers/inventory/size failed - cause:" + e.getMessage());
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.getReeferInventorySize() - Inventory Size:" + totalReeferInventory);
        }
        return ((JsonNumber) totalReeferInventory).intValue();
    }

    private JsonObject getReeferStats() {
        int totalOnMaintenance = onMaintenanceMap.size(); //Kar.actorSubMapSize(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.getReeferStats() - totalBooked:" + bookedTotalCount.get() + " in-transit:"
                    + inTransitTotalCount.get() + " spoilt:" + spoiltTotalCount.get() + " on-maintenance:" + totalOnMaintenance);
        }
        return Json.createObjectBuilder().add("total", totalReeferInventory).add("totalBooked", bookedTotalCount.get())
                .add("totalInTransit", inTransitTotalCount.get()).add("totalSpoilt", spoiltTotalCount.get())
                .add("totalOnMaintenance", totalOnMaintenance).build();
    }

    private boolean releaseFromMaintenanceToday(ReeferDTO reefer, Instant today) {

        if (reefer == null) {
            return true;
        }
        if (reefer.getMaintenanceReleaseDate() == null) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning(
                        "ReeferProvisionerActor.releaseFromMaintenanceToday() - maintenance release date not set for reefer:"
                                + reefer.getId() + " state:" + reefer.getState().name() + " not booked yet");
            }
            return false;
        }
        // reefer assigned to maintenance gets a date when it is taken off maintenance
        if (today.equals(Instant.parse(reefer.getMaintenanceReleaseDate()))
                || today.isAfter(Instant.parse(reefer.getMaintenanceReleaseDate()))) {
            return true;
        }
        return false;
    }

    private void releaseFromMaintenance(ReeferDTO reefer, Instant today) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisioner.releasefromMaintenance() - releasing reefer:" + reefer.getId()
                    + " from maintenance. Today:" + today + " reefer release date:" + reefer.getMaintenanceReleaseDate()
                    + " state:" + reefer.getState().name());
        }
        reefer.reset();
        Kar.Actors.State.Submap.remove(this, Constants.REEFER_MAP_KEY, String.valueOf(reefer.getId()));
        Kar.Actors.State.Submap.remove(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, String.valueOf(reefer.getId()));
    }

    private void setReeferOnMaintenance(ReeferDTO reefer, String today) {
        // assign reefer off maintenance date which is N days from today. Currently N=2
        reefer.setMaintenanceReleaseDate(today);
        reefer.setState(ReeferState.State.MAINTENANCE);
        updateStore(reefer);
        String reeferId = String.valueOf(reefer.getId());
        Kar.Actors.State.Submap.set(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, reeferId,
                Json.createValue(reeferId));
        onMaintenanceMap.put(reeferId, reeferId);
    }

    private void updateStore(ReeferDTO reefer) {
        JsonObject jo = reeferToJsonObject(reefer);
        Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, String.valueOf(reefer.getId()), jo);
    }

    private void changeReeferState(ReeferDTO reefer, int reeferId, ReeferState.State newState) {
        if (reefer != null) {
            reefer.setState(newState);
        } else {
            reeferMasterInventory[reeferId] = new ReeferDTO(reeferId, newState, "", "");
        }
        valuesChanged.set(true);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.changeReeferState() - reeferId:" + reeferId + " newState:" + newState
                    + " total:" + totalReeferInventory);
        }
    }

    private void unreserveReefer(int reeferId) {
        if (reeferMasterInventory[Integer.valueOf(reeferId)] != null) {
            // Reefers can be marked as spoilt only during the voyage. When a voyage ends
            // all spoilt reefers are automatically put on maintenance.
            if (reeferMasterInventory[reeferId].getState().equals(ReeferState.State.SPOILT)) {

                setReeferOnMaintenance(reeferMasterInventory[reeferId],
                        TimeUtils.getInstance().getCurrentDate().toString());
                spoiltTotalCount.decrementAndGet();
                Kar.Actors.State.set(this, Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount.intValue()));
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("ReeferProvisioner.unreserveReefer() - spoilt reefer:" + reeferId
                            + " arrived - changed state to OnMaintenance - total spoilt reefers:" + spoiltTotalCount.get());
                }
            } else if (!reeferMasterInventory[reeferId].getState().equals(ReeferState.State.MAINTENANCE)) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("ReeferProvisioner.unreserveReefer() - releasing reefer:" + reeferId);
                }
                reeferMasterInventory[reeferId].reset();
            }
            inTransitTotalCount.decrementAndGet();
            Kar.Actors.State.set(this, Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount.intValue()));
        } else {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("ReeferProvisioner.unreserveReefer() - reefer:" + reeferId
                        + " arrived but it has not been allocated");
            }
        }

    }

    /**
     * Timer task to call REST to update its reefer counts
     */
    private class RestUpdateTask extends TimerTask {
        @Override
        public void run() {

            if (valuesChanged.get()) {
                try {
                    Kar.Services.post(Constants.REEFERSERVICE, "/reefers/stats/update", getReeferStats());
                    valuesChanged.set(false);
                } catch (Exception e) {
                    logger.warning("ReeferProvisioner- REST call /reefers/stats/update failed - cause:" + e.getMessage());
                }
            }
        }
    }
}
