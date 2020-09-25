package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;

import io.jaegertracing.internal.samplers.ConstSampler;

@Actor
public class OrderActor extends BaseActor {

    @Activate
    public void init() {
        System.out.println("OrderActor.init() called id:" + getId() + " type:" + this.getType());
        
        JsonValue state = super.get(this, Constants.ORDER_STATUS_KEY);
        System.out.println(
                "OrderActor.init() - Order Id:" + getId() + " cached state:" + state + " type:" + this.getType());
        
        Map<String, JsonValue> reeferMap = super.getSubMap(this, Constants.REEFER_MAP_KEY);
        System.out.println("OrderActor.init() - Order Id:" + getId() + " fetched submap of size:" + reeferMap.size());
    }

    @Remote
    public JsonObject delivered(JsonObject message) {
        JsonValue voyageId = Kar.actorGetState(this, "voyageId");
        System.out.println(voyageId + " orderActor.delivered() called- Actor ID:" + getId()
                + " type:" + this.getType());
        try {
            // fetch all reefers in this order
            Map<String, JsonValue> reeferMap = super.getSubMap(this,  Constants.REEFER_MAP_KEY);

            System.out.println(voyageId + " Ended ::: OrderActor.delivered() - Order Id:" + getId() + " reefer map size:"
                    + reeferMap.size());

            JsonArrayBuilder reefersToRelease = Json.createArrayBuilder(reeferMap.keySet()); 
             // message the ReeferProvisionerActor to release reefers in a given list
            actorCall( actorRef(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                        "unreserveReefers", Json.createObjectBuilder().add(Constants.REEFERS_KEY, reefersToRelease).build());
            // as soon as the order is delivered and reefers are released we clear actor state 
            Kar.actorDeleteAllState(this);

            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "VOYAGE_ID_MISSING")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
    }

    @Remote
    public JsonObject departed(JsonObject message) {
        JsonValue voyage = get(this, "voyageId");
        System.out.println("OrderActor.departed() called- Actor ID:" + getId()
                + " type:" + this.getType() + " voyage:" + voyage);
        try {
            Map<String, JsonValue> reeferMap = super.getSubMap(this, Constants.REEFER_MAP_KEY);
            saveOrderStatus(OrderStatus.INTRANSIT);
             // Notify ReeferProvisioner that the order is in-transit
            if (!reeferMap.isEmpty()) {

                ActorRef reeferProvisionerActor = Kar.actorRef(ReeferAppConfig.ReeferProvisionerActorName,
                        ReeferAppConfig.ReeferProvisionerId);
                JsonObject params = Json.createObjectBuilder().add("in-transit", reeferMap.size()).build();
                actorCall(reeferProvisionerActor, "updateInTransit", params);

                System.out.println(voyage + " OrderActor.departed() - Order Id:" + getId()
                        + " in-transit reefer list size:" + reeferMap.size());
            } else {
                System.out.println(" OrderActor.departed() - Order Id:" + getId()
                        + " has no booked reefers");
            }
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
    }

    @Remote
    public JsonObject anomaly(JsonObject message) {
        // Reefer notifies the order on anomaly. The order returns its current state which will
        // propagate to the ReeferProvisioner where the decision is made to either spoil the
        // reefer or assign it to maintenance. 
        JsonValue state = super.get(this, Constants.ORDER_STATUS_KEY);
        System.out.println("OrderActor.anomaly() called- Actor ID:" + getId() + " type:"
                + this.getType() + " state:" + ((JsonString) state).getString());
        return Json.createObjectBuilder().add(Constants.ORDER_STATUS_KEY, ((JsonString) state).getString())
                .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();

    }

    @Remote
    /*
     * Replace spoilt reefer with a good one. This is a use case where an order has not yet departed 
     * but one of more of its reefers spoilt.
     */
    public JsonObject replaceReefer(JsonObject message) {
        JsonValue state = super.get(this, Constants.ORDER_STATUS_KEY);
        System.out.println("OrderActor.replaceReefer() called- Actor ID:" + getId()
                + " state:" + ((JsonString) state).getString());
       
        int spoiltReeferId = message.getJsonNumber(Constants.REEFER_ID_KEY).intValue();
        int replacementReeferId = message.getJsonNumber(Constants.REEFER_REPLACEMENT_ID_KEY).intValue();
        System.out.println("OrderActor.replaceReefer() called- Actor ID:" + getId()
                + " replacing spoilt reefer:" + spoiltReeferId + " with a new one:" + replacementReeferId);
        super.removeFromSubMap(this,Constants.REEFER_MAP_KEY,String.valueOf(spoiltReeferId));
        super.addToSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(replacementReeferId), Json.createValue(replacementReeferId));

        return Json.createObjectBuilder().build();

    }

    @Remote
    public JsonObject reeferCount(JsonObject message) {
        Map<String, JsonValue> reeferMap = super.getSubMap(this, Constants.REEFER_MAP_KEY);
        return Json.createObjectBuilder().add(Constants.TOTAL_REEFER_COUNT_KEY, reeferMap.size()).build();
    }

    @Remote
    public JsonObject createOrder(JsonObject message) {
        System.out.println("OrderActor.createOrder() called- Actor ID:" + this.getId() + " type:" + this.getType()
                + " message:" + message);
        // Java wrapper around Json payload
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
        saveOrderStatus(OrderStatus.PENDING);

        try {
            // voyageId is mandatory
            if (order.containsKey(JsonOrder.VoyageIdKey)) {
                JsonValue state = super.get(this, Constants.ORDER_STATUS_KEY);
                
                super.set(this, Constants.VOYAGE_ID_KEY, Json.createValue(order.getVoyageId()));
                JsonObject voyageBookingResult = bookVoyage(order.getVoyageId(), order);
                System.out.println("OrderActor.createOrder() - Voyage Booked - Reply:" + voyageBookingResult);
                if (voyageBooked(voyageBookingResult)) {
                    saveOrderReefers(voyageBookingResult);
                    saveOrderStatus(OrderStatus.BOOKED);
        
                    System.out
                            .println("OrderActor.createOrder() - Order Booked - saved order " + getId() + " state:" + state.toString());
                    return Json.createObjectBuilder().add(JsonOrder.OrderBookingKey, voyageBookingResult).build();
                } else {
                    return voyageBookingResult;
                }
            } else {
                System.out.println("OrderActor.createOrder() Failed - Missing voyageId");
                return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "VOYAGE_ID_MISSING")
                        .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();

            }

        } catch (Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();

        }

    }

    private void saveOrderStatus(OrderStatus status) {
        super.set(this, Constants.ORDER_STATUS_KEY, Json.createValue(status.name()));
    }

    private boolean voyageBooked(JsonObject orderBookingStatus) {
        return orderBookingStatus.getString(Constants.STATUS_KEY).equals(Constants.OK);
    }

    private void saveOrderReefers(JsonObject orderBookingStatus) throws Exception {
        JsonArray reefers = orderBookingStatus.getJsonArray(Constants.REEFERS_KEY);
        System.out.println("OrderActor.createOrder() - Order Booked - Reefers:" + reefers.size());
        if (reefers != null && reefers != JsonValue.NULL) {
            // copy assigned reefer id's to a map and save it in kar storage
            Map<String, JsonValue> reeferMap = new HashMap<>();
            reefers.forEach(reeferId -> {
                reeferMap.put(String.valueOf(((JsonNumber) reeferId).intValue()), reeferId);
            });
            addSubMap(this, Constants.REEFER_MAP_KEY, reeferMap);
            System.out.println(
                    "OrderActor.createOrder() saved order " + getId() + " reefer list - size " + reeferMap.size());
        }
        
    }

    private JsonObject bookVoyage(String voyageId, JsonOrder order) {
        try {
            JsonObject params = Json.createObjectBuilder().add(JsonOrder.OrderKey, order.getAsObject()).build();
            ActorRef voyageActor = actorRef(ReeferAppConfig.VoyageActorName, voyageId);
            JsonValue reply = actorCall(voyageActor, "reserve", params);
            return reply.asJsonObject();
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, OrderStatus.FAILED.name()).add("ERROR", "INVALID_CALL")
                    .add(JsonOrder.IdKey, order.getId()).build();
        }
    }
}