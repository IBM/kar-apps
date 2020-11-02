package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;

import java.time.Instant;
import java.util.*;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;

import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferAllocator;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.common.ReeferState.State;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;
import com.ibm.research.kar.reefer.model.ReeferDTO;

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
    private Map<String, String> onMaintenanceMap = new HashMap<>();// super.getSubMap(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST);
    private JsonValue totalReeferInventory;
    private static final Logger logger = Logger.getLogger(ReeferProvisionerActor.class.getName());

    @Activate
    public void activate() {
        // fetch actor state from Kar storage
        Map<String, JsonValue> state = Kar.actorGetAllState(this);
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
                    Map<String, JsonValue> reeferInventory = super.getSubMap(this, Constants.REEFER_MAP_KEY);
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
            }

            // update thread. Sends reefer count updates to the REST
            TimerTask timerTask = new RestUpdateTask();
            // running timer task as daemon thread. It updates
            // REST reefer counts at regular intervals (currently 100ms)
            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(timerTask, 0, 100);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }

    }

    /**
     * Called when this instance is passivated. Saves current state in the Kar's persistent storage.
     */
    @Deactivate
    public void deactivate() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount.intValue())).
                add(Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount.intValue())).
                add(Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount.intValue())).
                add(Constants.TOTAL_REEFER_COUNT_KEY, totalReeferInventory);
        Kar.actorSetMultipleState(this, job.build());
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
            logger.fine("ReeferProvisionerActor.releaseReefersfromMaintenance() - message:" + message);
        }
        try {
            if (!onMaintenanceMap.isEmpty()) {
                Iterator<String> it = onMaintenanceMap.values().iterator();
                while (it.hasNext()) {
                    // get current date. Its a date that the simulator advances at regular intervals.
                    Instant today = Instant.parse(message.getString(Constants.DATE_KEY));
                    ReeferDTO reefer = reeferMasterInventory[Integer.parseInt(it.next())];
                    // release reefer from maintenance if today matches reefer's assigned release date
                    if (releaseFromMaintenanceToday(reefer, today)) {
                        releaseFromMaintenance(reefer, today);
                        // remove reefer from onMaintenance map
                        it.remove();
                    }
                }
                // forces update thread to send reefer counts
                valuesChanged.set(true);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
        return Json.createObjectBuilder().build();
    }

    /**
     * Given numbers of reefers currently in-transit update total booked and total in-transit counts
     *
     * @param message
     * @return
     */
    @Remote
    public JsonObject updateInTransit(JsonObject message) {
        JsonObjectBuilder reply = Json.createObjectBuilder();
        int newInTransit = message.getInt("in-transit");
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ReeferProvisionerActor.updateInTransit() - message:" + message + " update reefers in transit:" + newInTransit);
        }
        if ((bookedTotalCount.get() - newInTransit) >= 0) {
            // subtract from booked and add to in-transit
            bookedTotalCount.addAndGet(-newInTransit);
        } else {
            bookedTotalCount.set(0);
        }

        inTransitTotalCount.addAndGet(newInTransit);
        valuesChanged.set(true);
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
        // lazily initialize master reefer inventory list on the first call.
        // This is fast since all we do is just creating an array of
        // fixed size
        if (reeferMasterInventory == null) {
            initMasterInventory(getReeferInventorySize());
        }
        // wrap Json with POJO
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
        if (order.containsKey(JsonOrder.ProductQtyKey)) {
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
            Kar.actorSetMultipleState(this,Constants.REEFER_MAP_KEY,map );
            bookedTotalCount.addAndGet(orderReefers.size());
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
    }

    /**
     * Release given reefers back to inventory
     *
     * @param message
     * @return
     */
    @Remote
    public JsonObject unreserveReefers(JsonObject message) {
        JsonObjectBuilder reply = Json.createObjectBuilder();
        try {
            // extract reefer ids we need to release
            JsonArray reeferIds = message.getJsonArray(Constants.REEFERS_KEY);
            for (JsonValue reeferId : reeferIds) {
                unreserveReefer(Integer.valueOf(((JsonString) reeferId).getString()));
            }
            // forces update thread to send reefer counts
            valuesChanged.set(true);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferProvisionerActor.unreserveReefers() - released reefers " + " total booked: "
                        + bookedTotalCount.get() + " totalInTransit:" + inTransitTotalCount.get());
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "", e);
        }

        return reply.build();
    }

    /**
     * Handle reefer anomaly
     *
     * @param message
     */
    @Remote
    public void reeferAnomaly(JsonObject message) {
        int reeferId = message.getInt(Constants.REEFER_ID_KEY);

        try {
            // lazily initialize master reefer inventory list on the first call.
            // This is fast since all we do is just creating an array of
            // fixed size
            if (reeferMasterInventory == null) {
                initMasterInventory(getReeferInventorySize());
            }
            if (reeferMasterInventory[reeferId] == null) {
                reeferMasterInventory[reeferId] = new ReeferDTO(reeferId, ReeferState.State.UNALLOCATED, "", "");
                super.addToSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(reeferId),
                        reeferToJsonObject(reeferMasterInventory[reeferId]));
            }
            ReeferDTO reefer = reeferMasterInventory[reeferId];
            JsonObject reply = handleAnomaly(reefer.getOrderId());
            if (logger.isLoggable(Level.INFO)) {
                logger.info(
                        "ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId + " order actor Id: "+reefer.getOrderId()+" booking reply:" + reply);
            }
            ReeferState.State state = ReeferState.State
                    .valueOf(reply.getString(Constants.REEFER_STATE_KEY));
            // reefer becomes spoilt only while its on a voyage. On arrival, the reefer
            // moves from spoilt to maintenance
            if (state.equals(ReeferState.State.SPOILT)) {
                // check if the spoilt order has been booked (but not on a voyage yet)
                if (reply.getString(Constants.ORDER_STATUS_KEY).equals(OrderStatus.BOOKED.name())) {
                    setReeferOnMaintenance(reefer, message.getString(Constants.DATE_KEY));
                    // Order has been booked but a reefer in it is spoilt. Remove spoilt reefer from
                    // the order and replace with a new one.
                    replaceSpoiltReefer(reefer);
                    bookedTotalCount.decrementAndGet();
                } else {
                    spoiltTotalCount.incrementAndGet();
                    // reefer has spoilt while on a voyage
                    changeReeferState(reefer, reeferId, ReeferState.State.SPOILT);//, Constants.TOTAL_SPOILT_KEY);
                    JsonObject orderId = Json.createObjectBuilder().add(Constants.ORDER_ID_KEY, reefer.getOrderId())
                            .build();
                    Kar.restPost("reeferservice", "/orders/spoilt", orderId);
                }
            } else {
                setReeferOnMaintenance(reefer, message.getString(Constants.DATE_KEY));
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("ReeferProvisionerActor.reeferAnomaly() - id:" + getId() + " added reefer:"
                            + reeferId + " to " + Constants.ON_MAINTENANCE_PROVISIONER_LIST + " Map"
                            + " onMaintenance date:" + message.getString(Constants.DATE_KEY));
                }
            }
            updateStore(reefer);
            // forces update thread to send reefer counts
            valuesChanged.set(true);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    private JsonObject handleAnomaly(String orderId) {
        JsonObjectBuilder propertiesBuilder = Json.createObjectBuilder();
        JsonObjectBuilder reply = Json.createObjectBuilder();
        if (orderId != null && orderId.length() > 0) {
            JsonObject orderReply = notifyOrderOfSpoilage(orderId);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferActor.handleAnomaly() - Id:" + this.getId()
                        + " Order Actor:" + orderId + " reply:" + orderReply);
            }
            // check if order has spoilt (true if order spoils while in transit)
            if (orderReply.getString(Constants.ORDER_STATUS_KEY).equals(OrderStatus.SPOILT.name())) {
                // reefer in-transit becomes spoilt and will transition to on-maintenance when ship arrives in port
                propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.SPOILT.name()));
                reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.SPOILT.name()).add(Constants.ORDER_STATUS_KEY,
                        OrderStatus.INTRANSIT.name());
            } else // check if order has been booked
                if ( orderReply.getString(Constants.ORDER_STATUS_KEY).equals(OrderStatus.BOOKED.name()) ){
                // Booked orders have not yet departed. Spoiled reefers must be replaced in such
                // case.
                propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.SPOILT.name()));
                reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.SPOILT.name()).add(Constants.ORDER_STATUS_KEY,
                        OrderStatus.BOOKED.name());
            } else {
                // reefer neither booked nor in-transit goes on maintenance
                propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.MAINTENANCE.name()));
                reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.MAINTENANCE.name());
            }
        } else {
            // reefer not allocated yet
            propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.MAINTENANCE.name()));
            reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.MAINTENANCE.name());
        }
        return reply.build();
    }

    private JsonObject notifyOrderOfSpoilage(String orderId) {
        ActorRef orderActor = Kar.actorRef(ReeferAppConfig.OrderActorName, orderId);
        JsonObject params = Json.createObjectBuilder().build();
        return actorCall(orderActor, "anomaly", params).asJsonObject();
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
        Response response = Kar.restGet("reeferservice", "reefers/inventory/size");
        totalReeferInventory = response.readEntity(JsonValue.class);
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ReeferProvisionerActor.getReeferInventorySize() - Inventory Size:" + totalReeferInventory);
        }
        return ((JsonNumber) totalReeferInventory).intValue();
    }

    /**
     * Calls Order actor to replace spoilt reefer
     *
     * @param orderId             - Id of an order which has spoilt
     * @param spoiltReeferId     - Id of a reefer which has spoilt
     * @param replacementReeferId - Id of reefer which will replace the spoilt one
     * @return
     */
    private JsonObject messageOrderActorReplaceReefer(String orderId, int spoiltReeferId, int replacementReeferId) {
        ActorRef orderActor = Kar.actorRef(ReeferAppConfig.OrderActorName, orderId);
        JsonObject params = Json.createObjectBuilder().add(Constants.REEFER_ID_KEY, spoiltReeferId)
                .add(Constants.REEFER_REPLACEMENT_ID_KEY, replacementReeferId).build();
        return actorCall(orderActor, "replaceReefer", params).asJsonObject();
    }

    private JsonObject getReeferStats() {
        if (reeferMasterInventory == null) {
            initMasterInventory(getReeferInventorySize());
        }
        int totalOnMaintenance = onMaintenanceMap.size(); //Kar.actorSubMapSize(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST);
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ReeferProvisionerActor.getReeferStats() - totalBooked:" + bookedTotalCount.get() + " in-transit:"
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
            if (logger.isLoggable(Level.INFO)) {
                logger.info(
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ReeferProvisioner.releasefromMaintenance() - releasing reefer:" + reefer.getId()
                    + " from maintenance. Today:" + today + " reefer release date:" + reefer.getMaintenanceReleaseDate()
                    + " state:" + reefer.getState().name());
        }
        reefer.setState(State.UNALLOCATED);
        // clear off maintenance date
        reefer.setMaintenanceReleaseDate(null);
        updateStore(reefer);

        super.removeFromSubMap(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, String.valueOf(reefer.getId()));
    }

    private void setReeferOnMaintenance(ReeferDTO reefer, String today) {
        // assign reefer off maintenance date which is N days from today. Currently N=2
        reefer.setMaintenanceReleaseDate(today);
        reefer.setState(State.MAINTENANCE);
        updateStore(reefer);
        super.addToSubMap(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, String.valueOf(reefer.getId()),
                Json.createValue(reefer.getId()));
        onMaintenanceMap.put(String.valueOf(reefer.getId()), String.valueOf(reefer.getId()));
    }

    private void updateStore(ReeferDTO reefer) {
        JsonObject jo = reeferToJsonObject(reefer);
        super.addToSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(reefer.getId()), jo);
    }

    private void replaceSpoiltReefer(ReeferDTO reefer) {
        List<ReeferDTO> replacementReefer = ReeferAllocator.allocateReefers(reeferMasterInventory,
                Constants.REEFER_CAPACITY, reefer.getOrderId(), reefer.getVoyageId());
        // there should only be one reefer replacement
        if (replacementReefer.size() > 0) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ReeferProvisionerActor.replaceSpoiltReefer() - notifying order actor to replace reeferId:"
                        + reefer.getId() + " with:" + replacementReefer.get(0).getId());
            }
            messageOrderActorReplaceReefer(reefer.getOrderId(), reefer.getId(), replacementReefer.get(0).getId());
        }

    }

    private void changeReeferState(ReeferDTO reefer, int reeferId, ReeferState.State newState) {
        if (reefer != null) {
            reefer.setState(newState);
        } else {
            reeferMasterInventory[reeferId] = new ReeferDTO(reeferId, newState, "", "");
        }
        valuesChanged.set(true);
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ReeferProvisionerActor.changeReeferState() - reeferId:" + reeferId + " newState:" + newState
                    + " total:" + totalReeferInventory);
        }
    }

    private void unreserveReefer(int reeferId) {
        if (reeferMasterInventory[Integer.valueOf(reeferId)] != null) {
            // Reefers can be marked as spoilt only during the voyage. When a voyage ends
            // all spoilt reefers are automatically put on maintenance.
            if (reeferMasterInventory[reeferId].getState().equals(State.SPOILT)) {

                setReeferOnMaintenance(reeferMasterInventory[reeferId],
                        TimeUtils.getInstance().getCurrentDate().toString());
                spoiltTotalCount.decrementAndGet();
                inTransitTotalCount.decrementAndGet();
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("ReeferProvisioner.unreserveReefer() - spoilt reefer:" + reeferId
                            + " arrived - changed state to OnMaintenance");
                }
            } else {
                reeferMasterInventory[reeferId].reset();
                // remove reefer from kar storage
                super.removeFromSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(reeferId));
                inTransitTotalCount.decrementAndGet();
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
                    Kar.restPost("reeferservice", "/reefers/stats/update", getReeferStats());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                valuesChanged.set(false);
            }

        }
    }
}