package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferAllocator;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.common.ReeferState.State;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;
import com.ibm.research.kar.reefer.model.ReeferDTO;

@Actor
public class ReeferProvisionerActor extends BaseActor {

    private ReeferDTO[] reeferMasterInventory = null;
    // private JsonValue totalReeferInventory = null;

    @Activate
    public void init() {
        System.out.println("ReeferProvisionerActor.init() called -----------------------------");
        StringBuilder sb = new StringBuilder();
        try {
            JsonValue totalReeferInventory = get(this, Constants.TOTAL_REEFER_COUNT_KEY);
            if (totalReeferInventory != null) {
                Map<String, JsonValue> reeferInventory = super.getSubMap(this, Constants.REEFER_MAP_KEY);
                System.out.println("ReeferProvisionerActor.init() - Fetched size of the reefer inventory:"
                        + reeferInventory.size());

                // JsonValue state = get(this, Constants.REEFER_PROVISIONER_STATE_KEY);
                // if (state != null) {
                // if ( !reeferInventory.isEmpty() ) {
                reeferMasterInventory = new ReeferDTO[((JsonNumber) totalReeferInventory).intValue()];

                for (JsonValue value : reeferInventory.values()) {
                    if (value == null) {
                        continue;
                    }
                    JsonObject reefer = value.asJsonObject();
                    //System.out.println("ReeferProvisionerActor.init() - JsoN Reefer Object:" + reefer);
                    reeferMasterInventory[reefer.getInt(Constants.REEFER_ID_KEY)] = jsonObjectToReeferDTO(reefer);
                   // sb.append("\nid:").append(reeferMasterInventory[reefer.getInt(Constants.REEFER_ID_KEY)].getId())
                   //         .append("release date").append(reeferMasterInventory[reefer.getInt(Constants.REEFER_ID_KEY)]
                   //                 .getMaintenanceReleaseDate());

                }
            }

            //System.out.println("+++++++++++++++++++++++++++++++++++++ Reefers Fetched from Store:" + sb.toString());
            // }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Deactivate
    public void saveState() {
        if (reeferMasterInventory == null) {
            System.out.println("ReeferProvisionerActor.saveState() - reeferMasterInventory is null");
            return;
        }
        set(this, Constants.TOTAL_REEFER_COUNT_KEY, Json.createValue(reeferMasterInventory.length));
        System.out.println(
                "ReeferProvisionerActor.saveState() - reeferMasterInventory size:" + reeferMasterInventory.length);
        /*
         * JsonArrayBuilder stateBuilder = Json.createArrayBuilder(); try { int count =
         * 0; for (ReeferDTO dto : reeferMasterInventory) { if (dto != null) {
         * JsonObject reefer = reeferToJsonObject(dto); stateBuilder.add(reefer);
         * count++; }
         * 
         * } System.out.println( "ReeferProvisionerActor.saveState() - Reefers in use:"
         * + count); } catch (Exception e) { e.printStackTrace(); }
         * 
         * set(this, Constants.REEFER_PROVISIONER_STATE_KEY, stateBuilder.build());
         */
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

    private void initMasterInventory(int inventorySize) {
        reeferMasterInventory = new ReeferDTO[inventorySize];
        set(this, Constants.TOTAL_REEFER_COUNT_KEY, Json.createValue(inventorySize));
    }

    private int getReeferInventorySize() {
        Response response = Kar.restGet("reeferservice", "reefers/inventory/size");
        JsonValue size = response.readEntity(JsonValue.class);

        System.out.println("ReeferProvisionerActor.getReeferInventorySize() - Inventory Size:" + (JsonNumber) size);
        return ((JsonNumber) size).intValue();
    }

    private JsonObject messageOrderActorReplaceReefer(String orderId, int spoliedReeferId, int replacementReeferId) {
        ActorRef orderActor = Kar.actorRef(ReeferAppConfig.OrderActorName, orderId);
        JsonObject params = Json.createObjectBuilder().add(Constants.REEFER_ID_KEY, spoliedReeferId)
                .add(Constants.REEFER_REPLACEMENT_ID_KEY, replacementReeferId).build();
        return actorCall(orderActor, "replaceReefer", params).asJsonObject();
    }

    private void messageReeferActorReserve(ReeferDTO reefer) {
        JsonObject params = Json.createObjectBuilder().add(ReeferState.ORDER_ID_KEY, reefer.getOrderId())
                .add(ReeferState.MAX_CAPACITY_KEY, ReeferAppConfig.ReeferMaxCapacityValue)
                .add(ReeferState.VOYAGE_ID_KEY, reefer.getVoyageId())
                .add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.ALLOCATED.name())).build();
        messageReeferActor(reefer, params, "reserve");
    }

    private void messageReeferActorRelease(ReeferDTO reefer) {
        messageReeferActor(reefer, Json.createObjectBuilder().build(), "unreserve");
    }

    private void messageReeferActorOffMaintenance(ReeferDTO reefer) {
        messageReeferActor(reefer, Json.createObjectBuilder().build(), "offMaintenance");
    }

    private void messageReeferActorOnMaintenance(ReeferDTO reefer) {
        JsonObjectBuilder params = Json.createObjectBuilder().add(ReeferState.STATE_KEY,
                Json.createValue(ReeferState.State.MAINTENANCE.name()));
        messageReeferActor(reefer, params.build(), "onMaintenance");
    }

    private void messageReeferActor(ReeferDTO reefer, JsonObject params, String actorMethod) {
        ActorRef reeferActor = Kar.actorRef(ReeferAppConfig.ReeferActorName, String.valueOf(reefer.getId()));

        try {
            actorCall(reeferActor, actorMethod, params);
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        } catch (Exception ee) {
            ee.printStackTrace();
        }
    }

    @Remote
    public JsonObject getStats(JsonObject message) {
        return getReeferStats();
    }

    private JsonObject getReeferStats() {
        int booked = 0;
        int intransit = 0;
        if (reeferMasterInventory == null) {
            initMasterInventory(getReeferInventorySize());
        }

        JsonValue total = get(this, Constants.TOTAL_REEFER_COUNT_KEY);
        if (total == null) {
            total = Json.createValue(0);
        }
        JsonValue totalBooked = get(this, Constants.TOTAL_BOOKED_KEY);
        if (totalBooked == null) {
            totalBooked = Json.createValue(0);
        } else {
            booked = ((JsonNumber) totalBooked).intValue();
        }
        JsonValue totalInTransit = get(this, Constants.TOTAL_INTRANSIT_KEY);
        if (totalInTransit == null) {
            totalInTransit = Json.createValue(0);
        } else {
            intransit = ((JsonNumber) totalInTransit).intValue();
        }
        JsonValue totalSpoilt = get(this, Constants.TOTAL_SPOILT_KEY);
        if (totalSpoilt == null) {
            totalSpoilt = Json.createValue(0);
        }

        int totalOnMaintenance = Kar.actorSubMapSize(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST);

        /*
         * JsonValue totalOnMaintenance = get(this, Constants.TOTAL_ONMAINTENANCE_KEY);
         * if (totalOnMaintenance == null) { totalOnMaintenance = Json.createValue(0); }
         */
        System.out.println("ReeferProvisionerActor.getReeferStats() - totalBooked:" + booked + " in-transit:"
                + intransit + " spoilt:" + totalSpoilt + " on-maintenance:" + totalOnMaintenance);
        return Json.createObjectBuilder().add("total", total).add("totalBooked", totalBooked)
                .add("totalInTransit", totalInTransit).add("totalSpoilt", totalSpoilt)
                .add("totalOnMaintenance", totalOnMaintenance).build();
    }

    public void updateRest() {

        try {
            Kar.restPost("reeferservice", "/reefers/stats/update", getReeferStats());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean releaseFromMaintenanceToday(ReeferDTO reefer, Instant today) {

        if (reefer == null) {
            return true;
        }
        if (reefer.getMaintenanceReleaseDate() == null) {
            System.out.println(
                    "ReeferProvisionerActor.releaseFromMaintenance() - maintenance release date not set for reefer:"
                            + reefer.getId() + " state:" + reefer.getState().name() + " not booked yet");
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

        System.out.println("ReeferProvisioner.releaseReefersfromMaintenance() - releasing reefer:" + reefer.getId()
                + " from maintenance. Today:" + today + " reefer release date:" + reefer.getMaintenanceReleaseDate()
                + " state:" + reefer.getState().name());
        reefer.setState(State.UNALLOCATED);
        // clear off maintenance date
        reefer.setMaintenanceReleaseDate(null);
        updateStore(reefer);

        super.removeFromSubMap(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, String.valueOf(reefer.getId()));
        // super.decrementAndSave(this, Constants.TOTAL_ONMAINTENANCE_KEY, 1);
        messageReeferActorOffMaintenance(reefer);
        System.out.println("ReeferProvisioner.releaseReefersfromMaintenance() - released reefer:" + reefer.getId()
                + " from maintenance. Today:" + today);
    }

    @Remote
    public JsonObject releaseReefersfromMaintenance(JsonObject message) {
        System.out.println("ReeferProvisionerActor.releaseReefersfromMaintenance() - message:" + message);

        try {
            Map<String, JsonValue> onMaintenanceMap = super.getSubMap(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST);
            if (!onMaintenanceMap.isEmpty()) {
                System.out.println(
                        "ReeferProvisionerActor.releaseReefersfromMaintenance() - Fetched OnMaintenance List of size:"
                                + onMaintenanceMap.size() + " List:" + onMaintenanceMap.values());
                onMaintenanceMap.values().forEach(reeferId -> {
                    Instant today = Instant.parse(message.getString(Constants.DATE_KEY));
                    ReeferDTO reefer = reeferMasterInventory[((JsonNumber) reeferId).intValue()];
                    System.out.println("ReeferProvisionerActor.releaseReefersfromMaintenance() - Checking if reefer ID:"
                            + reeferId + " should go off maintenance today");
                    if (releaseFromMaintenanceToday(reefer, today)) {
                        releaseFromMaintenance(reefer, today);
                    }
                });
                updateRest();
            } else {
                System.out.println(
                        "ReeferProvisionerActor.releaseReefersfromMaintenance() - onMaintenance list is empty");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return Json.createObjectBuilder().build();
    }

    @Remote
    public JsonObject updateInTransit(JsonObject message) {
        System.out.println("ReeferProvisionerActor.updateInTransit() - message:" + message);
        JsonObjectBuilder reply = Json.createObjectBuilder();
        int newInTransit = message.getInt("in-transit");
        super.decrementAndSave(this, Constants.TOTAL_BOOKED_KEY, newInTransit);
        super.incrementAndSave(this, Constants.TOTAL_INTRANSIT_KEY, newInTransit);
        updateRest();
        return reply.build();
    }

    private void setReeferOnMaintenance(ReeferDTO reefer, String today) {
      //  changeReeferState(reefer, reefer.getId(), ReeferState.State.MAINTENANCE, Constants.TOTAL_ONMAINTENANCE_KEY);
        
        // assign reefer off maintenance date which is N days from today. Currently N=2
        reefer.setMaintenanceReleaseDate(today);
        // reeferMasterInventory[reefer.getId()].setState(State.MAINTENANCE);
        reefer.setState(State.MAINTENANCE);
        updateStore(reefer);
        super.addToSubMap(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, String.valueOf(reefer.getId()),
                Json.createValue(reefer.getId()));
        // Map<String, JsonValue> onMaintenanceMap = super.getSubMap(this,
        // Constants.ON_MAINTENANCE_PROVISIONER_LIST);
        messageReeferActorOnMaintenance(reefer);
        // System.out.println("ReeferProvisionerActor.setReeferOnMaintenance() -
        // ActorId:"
        // + getId() + " onMaintenance list:" + onMaintenanceMap.values());
    }

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
            // if ( reefer != null && reefer.getState().equals(State.ALLOCATED)) {
            System.out.println("ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId);

            ActorRef reeferActor = Kar.actorRef(ReeferAppConfig.ReeferActorName, String.valueOf(reeferId));
            // placeholder for future params
            JsonObject params = Json.createObjectBuilder().build();
            JsonValue reply = actorCall(reeferActor, "anomaly", params);
            System.out.println(
                    "ReeferProvisionerActor.reeferAnomaly() - reeferId:" + reeferId + " Order Actor Reply:" + reply);

            ReeferState.State state = ReeferState.State
                    .valueOf(reply.asJsonObject().getString(Constants.REEFER_STATE_KEY));
            // reefer becomes spoilt only while its on a voyage. On arrival, the reefer
            // moves
            // from spoilt to maintenance
            if (reeferSpoilt(state)) {
                // check if spoilt order has been booked (but not on a voyage yet)
                if (orderBooked(reply)) {
                    setReeferOnMaintenance(reefer, message.getString(Constants.DATE_KEY));
                    // Order has been booked but a reefer in it is spoilt. Remove spoilt reefer from
                    // the order and replace with a new one.
                    replaceSpoiltReefer(reefer);
                    // decrement booked
                    super.decrementAndSave(this, Constants.TOTAL_BOOKED_KEY, 1);

                } else {
                    // reefer has spoilt while on a voyage
                    changeReeferState(reefer, reeferId, ReeferState.State.SPOILT, Constants.TOTAL_SPOILT_KEY);
                    JsonObject orderId = Json.createObjectBuilder().add(Constants.ORDER_ID_KEY, reefer.getOrderId())
                            .build();
                    Kar.restPost("reeferservice", "/orders/spoilt", orderId);

                }

            } else {
                setReeferOnMaintenance(reefer, message.getString(Constants.DATE_KEY));
                System.out.println("ReeferProvisionerActor.reeferAnomaly() - id:" + getId() + " added reefer:"
                        + reeferId + " to " + Constants.ON_MAINTENANCE_PROVISIONER_LIST + " Map"
                        + " onMaintenance date:" + message.getString(Constants.DATE_KEY));
            }
            updateStore(reefer);
            updateRest();
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        } catch (Exception ee) {
            ee.printStackTrace();
        }
    }

    private void updateStore(ReeferDTO reefer) {
        JsonObject jo = reeferToJsonObject(reefer);
        super.addToSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(reefer.getId()), jo);
    }

    private boolean orderBooked(JsonValue orderStatus) {
        return OrderStatus.BOOKED
                .equals(OrderStatus.valueOf(orderStatus.asJsonObject().getString(Constants.ORDER_STATUS_KEY)));
    }

    private void replaceSpoiltReefer(ReeferDTO reefer) {
        List<ReeferDTO> replacementReefer = ReeferAllocator.allocateReefers(reeferMasterInventory,
                Constants.REEFER_CAPACITY, reefer.getOrderId(), reefer.getVoyageId());
        // there should only be one reefer replacement
        if (replacementReefer.size() > 0) {
            messageReeferActorReserve(replacementReefer.get(0));
            System.out
                    .println("ReeferProvisionerActor.replaceSpoiltReefer() - notifying order actor to replace reeferId:"
                            + reefer.getId() + " with:" + replacementReefer.get(0).getId());
            messageOrderActorReplaceReefer(reefer.getOrderId(), reefer.getId(), replacementReefer.get(0).getId());
        }

    }

    private void changeReeferState(ReeferDTO reefer, int reeferId, ReeferState.State newState, String key) {
        JsonValue v = get(this, key);
        int total = 0;
        if (v != null) {
            total = ((JsonNumber) get(this, key)).intValue();
        }

        if (reefer != null) {
            reefer.setState(newState);

        } else {
            reeferMasterInventory[reeferId] = new ReeferDTO(reeferId, newState, "", "");
        }
        set(this, key, Json.createValue(++total));
        updateRest();
        System.out.println("ReeferProvisionerActor.changeReeferState() - reeferId:" + reeferId + " newState:" + newState
                + " total:" + total);
    }

    private boolean reeferSpoilt(ReeferState.State state) {
        if (state.equals(ReeferState.State.SPOILT)) {
            return true;
        }
        return false;
    }

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
        System.out.println("ReeferProvisionerActor.bookReefers() called - Order:" + order.getId());

        if (order.containsKey(JsonOrder.ProductQtyKey)) {
            // allocate enough reefers to cary products in the order
            List<ReeferDTO> orderReefers = ReeferAllocator.allocateReefers(reeferMasterInventory, order.getProductQty(),
                    order.getId(), order.getVoyageId());
            // need an array to hold reeferIds which will be included in the reply
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            // book each reefer
            for (ReeferDTO reefer : orderReefers) {
                arrayBuilder.add(reefer.getId());
                super.addToSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(reefer.getId()),
                        reeferToJsonObject(reefer));

                messageReeferActorReserve(reefer);
            }
            super.incrementAndSave(this, Constants.TOTAL_BOOKED_KEY, orderReefers.size());
            System.out.println("ReeferProvisionerActor.bookReefers())- Order:" + order.getId() + " reefer count:"
                    + orderReefers.size());
            updateRest();

            return Json.createObjectBuilder().add("status", "OK").add("reefers", arrayBuilder.build())
                    .add(JsonOrder.OrderKey, order.getAsObject()).build();
        } else {
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "ProductQuantityMissing")
                    .add(JsonOrder.IdKey, order.getId()).build();
        }
    }

    @Remote
    public JsonObject unreserveReefers(JsonObject message) {
        System.out.println("ReeferProvisioner.unreserveReefers() called - message:" + message);
        JsonObjectBuilder reply = Json.createObjectBuilder();

        JsonArray reeferIds = message.getJsonArray(Constants.REEFERS_KEY);
        for (JsonValue reeferId : reeferIds) {
            unreserveReefer(Integer.valueOf(((JsonString) reeferId).getString()));
        }
        updateRest();
        JsonValue totalBooked = get(this, Constants.TOTAL_BOOKED_KEY);
        JsonValue totalInTransit = get(this, Constants.TOTAL_INTRANSIT_KEY);
        System.out.println("ReeferProvisionerActor.unreserverReefers() - released reefers " + " total booked: "
                + totalBooked + " totalInTransit:" + totalInTransit);
        return reply.build();
    }

    private void unreserveReefer(int reeferId) {
        if (reeferMasterInventory[Integer.valueOf(reeferId)] != null) {
            // Reefers can be marked as spoilt only during the voyage. When a voyage ends
            // all spoilt reefers are automatically put on maintenance.
            if (reeferMasterInventory[reeferId].getState().equals(State.SPOILT)) {

                setReeferOnMaintenance(reeferMasterInventory[reeferId],
                        TimeUtils.getInstance().getCurrentDate().toString());
                super.decrementAndSave(this, Constants.TOTAL_SPOILT_KEY, 1);
                System.out.println("ReeferProvisioner.unreserveReefer() - spoilt reefer:" + reeferId
                        + " arrived - changed state to OnMaintenance");
            } else {
                reeferMasterInventory[reeferId].setState(State.UNALLOCATED);
                // remove reefer from the kar storage
                super.removeFromSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(reeferId));
                // message the Reefer actor to release its state
                messageReeferActorRelease(reeferMasterInventory[reeferId]);
                super.decrementAndSave(this, Constants.TOTAL_INTRANSIT_KEY, 1);
            }
        }

    }

}