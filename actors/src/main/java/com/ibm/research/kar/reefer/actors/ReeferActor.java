package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorGetAllState;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.model.OrderStatus;

@Actor
public class ReeferActor extends BaseActor {
    public static final String ReeferMaxCapacityKey = "reeferMaxCapacity";
    public static final String ReeferAvailCapacityKey = "reeferAvailableCapacity";
    public static final String ReeferAllocationStatusKey = "reeferAllocationStatus";
    public static final String ReeferConditionKey = "reeferCondition";
    public static final String ReeferVoyageIdKey = "reeferVoyageId";
    public static final String ReeferLocationKey = "reeferLocation";

    public enum ReeferAllocationStatus {
        EMPTY, PARTIALLY_ALLOCATED, ALLOCATED
    };

    public enum ReeferCondition {
        NORMAL, FAILED, ON_MAINTENANCE
    };

    Map<String, JsonValue> stateMap = new HashMap<>();

    @Activate
    public void init() {
        stateMap = actorGetAllState(this);
    }

    /**
     * Called to change reefer state.
     * 
     * @param reeferProperties
     */
    public void setState(JsonObject reeferProperties) {
        try {
            if (reeferProperties.containsKey(ReeferState.MAX_CAPACITY_KEY)) {
                set(this, ReeferState.MAX_CAPACITY_KEY,
                        Json.createValue(reeferProperties.getInt(ReeferState.MAX_CAPACITY_KEY)));
            }
            if (reeferProperties.containsKey(ReeferState.ORDER_ID_KEY)) {
                set(this, ReeferState.ORDER_ID_KEY,
                        Json.createValue(reeferProperties.getString(ReeferState.ORDER_ID_KEY)));
            }
            if (reeferProperties.containsKey(ReeferState.VOYAGE_ID_KEY)) {
                set(this, ReeferState.VOYAGE_ID_KEY,
                        Json.createValue(reeferProperties.getString(ReeferState.VOYAGE_ID_KEY)));
            }
            if (reeferProperties.containsKey(ReeferState.STATE_KEY)) {
                set(this, ReeferState.STATE_KEY, Json.createValue(reeferProperties.getString(ReeferState.STATE_KEY)));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Called when this reefer is associated with an order
     * @param message
     */
    @Remote
    public void reserve(JsonObject message) {
        System.out.println("ReeferActor.reserve() called - Id:" + this.getId() + " message:" + message.toString());
        setState(message);
    }
    /**
     * Called when an order containing this reefer is delivered
     * @param message
     */
    @Remote
    public void unreserve(JsonObject message) {
        System.out.println("ReeferActor.unreserve() called - Id:" + this.getId());
        Kar.actorDeleteAllState(this);
    }
    /**
     * Called when this reefer is assigned to maintenance
     * @param message
     */
    @Remote
    public void onMaintenance(JsonObject message) {
        System.out.println("ReeferActor.onMaintenance() called - Id:" + this.getId() + " message:" + message.toString());
        setState(message);
    }
    /**
     * Called when this reefer is removed from maintenance
     * @param message
     */
    @Remote
    public void offMaintenance(JsonObject message) {
        System.out.println("ReeferActor.offMaintenance() called - Id:" + this.getId() + " message:" + message.toString());
        Kar.actorDeleteAllState(this);
    }
    /**
     * Called when the reefer anomaly occurs (controlled by the simulator).
     * 
     * @param message
     * @return
     */
    @Remote
    public JsonValue anomaly(JsonObject message) {
        System.out.println("ReeferActor.anomaly() called - Id:" + this.getId());
        JsonObjectBuilder propertiesBuilder = Json.createObjectBuilder();
        JsonObjectBuilder reply = Json.createObjectBuilder();
        // A reefer with an orderId is on a ship
        JsonValue jsonOrderId = get(this, ReeferState.ORDER_ID_KEY);
        if (jsonOrderId != null && jsonOrderId != JsonValue.NULL && jsonOrderId.toString().length() > 0) {
            System.out.println("ReeferActor.anomaly() - Id:" + this.getId()
                    + " Assigned to Order Id:" + ((JsonString) jsonOrderId).getString()
                    + " - Spoiled Reefer - Notifying Order Actor");
            // Notify the Order actor that this reefer is spoilt. The Order actor returns its current
            // state (either in-transit or booked). 
            JsonObject orderReply = notifyOrderOfSpoilage(((JsonString) jsonOrderId).getString());
            System.out.println("ReeferActor.anomaly() - Id:" + this.getId()
                    + " Order Actor:" + ((JsonString) jsonOrderId).getString() + " reply:" + orderReply);
            if ( orderInTransit(orderReply) ) {
                // reefer in-transit becomes spoilt and will transition to on-maintenance when ship arrives in port
                propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.SPOILT.name()));
                reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.SPOILT.name()).add(Constants.ORDER_STATUS_KEY,
                        OrderStatus.INTRANSIT.name());
            } else if ( orderBooked(orderReply)) {
                // Booked orders have not yet departed. Spoiled reefers must be replaced in such
                // case.
                propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.SPOILT.name()));
                reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.SPOILT.name()).add(Constants.ORDER_STATUS_KEY,
                        OrderStatus.BOOKED.name());
            } else {
                // reefer neither booked nor in-transit goes on maintenance 
                propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.MAINTENANCE.name()));
                reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.MAINTENANCE.name())
                        .add(Constants.ORDER_STATUS_KEY, orderReply.getString(Constants.ORDER_STATUS_KEY));
            }
        } else {
            System.out.println("ReeferActor.anomaly() called - Id:" + this.getId()
                    + " Not Assigned to Order - Moving to OnMaintenance");
            reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.MAINTENANCE.name());
            propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.MAINTENANCE.name()));
        }
        setState(propertiesBuilder.build());
        return reply.build();
    }
    private boolean orderBooked( JsonObject orderReply) {
        return orderReply.getString(Constants.ORDER_STATUS_KEY).equals(OrderStatus.BOOKED.name());
    }
    private boolean orderInTransit(JsonObject orderReply) {
        return orderReply.getString(Constants.ORDER_STATUS_KEY).equals(OrderStatus.INTRANSIT.name());
    }
    /**
     * Notify Order actor about anomaly
     * @param orderId
     * @return
     */
    private JsonObject notifyOrderOfSpoilage(String orderId) {
        ActorRef orderActor = Kar.actorRef(ReeferAppConfig.OrderActorName, orderId);
        JsonObject params = Json.createObjectBuilder().add("reeferId", getId()).build();
        return actorCall(orderActor, "anomaly", params).asJsonObject();
    }
}