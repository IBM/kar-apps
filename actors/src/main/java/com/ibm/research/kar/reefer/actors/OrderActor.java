package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
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
            saveOrderStatus(OrderStatus.DELIVERED);

            Map<String, JsonValue> reeferMap = super.getSubMap(this,  Constants.REEFER_MAP_KEY);
            reeferMap.values().forEach(reefer -> {
                unreserveReefer(((JsonNumber) reefer).intValue());
            });
            System.out.println(voyageId + "OrderActor.delivered() - Order Id:" + getId() + " cached reefer list size:"
                    + reeferMap.size());

            return Json.createObjectBuilder().add("status", "OK").add("orderId", String.valueOf(this.getId())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR", "VOYAGE_ID_MISSING")
                    .add("orderId", String.valueOf(this.getId())).build();
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
            if (reeferMap.size() > 0) {

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
            return Json.createObjectBuilder().add("status", "OK").add("orderId", String.valueOf(this.getId())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR", e.getMessage())
                    .add("orderId", String.valueOf(this.getId())).build();
        }
    }

    @Remote
    public JsonObject anomaly(JsonObject message) {
        JsonValue state = super.get(this, Constants.ORDER_STATUS_KEY);
        System.out.println("OrderActor.anomaly() called- Actor ID:" + getId() + " type:"
                + this.getType() + " state:" + ((JsonString) state).getString());
        return Json.createObjectBuilder().add(Constants.ORDER_STATUS_KEY, ((JsonString) state).getString())
                .add("orderId", String.valueOf(this.getId())).build();

    }

    @Remote
    /*
     * Replace spoilt reefer with a good one.
     */
    public JsonObject replaceReefer(JsonObject message) {
        JsonValue state = super.get(this, Constants.ORDER_STATUS_KEY);
        System.out.println("OrderActor.replaceReefer() called- Actor ID:" + getId()
                + " state:" + ((JsonString) state).getString());

        
        Map<String, JsonValue> reeferMap = super.getSubMap(this, Constants.REEFER_MAP_KEY);
        int spoiltReeferId = message.getJsonNumber(Constants.REEFER_ID_KEY).intValue();
        int replacementReeferId = message.getJsonNumber(Constants.REEFER_REPLACEMENT_ID_KEY).intValue();
        System.out.println("OrderActor.replaceReefer() called- Actor ID:" + getId()
                + " replacing spoilt reefer:" + spoiltReeferId + " with a new one:" + replacementReeferId);
        reeferMap.remove(String.valueOf(spoiltReeferId));
        super.removeFromSubMap(this,"reefers-map",String.valueOf(spoiltReeferId));

        reeferMap.put(String.valueOf(replacementReeferId),Json.createValue(replacementReeferId));
        super.addToSubMap(this, "reefers-map", String.valueOf(replacementReeferId), Json.createValue(replacementReeferId));

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
                
                super.set(this, "voyageId", Json.createValue(order.getVoyageId()));
                JsonObject orderBooking = bookVoyage(order.getVoyageId(), order);
                System.out.println("OrderActor.createOrder() - Order Booked -Reply:" + orderBooking);
                if (voyageBooked(orderBooking)) {
                    saveOrderReefers(orderBooking);
                    saveOrderStatus(OrderStatus.BOOKED);
        
                    System.out
                            .println("OrderActor.createOrder() saved order " + getId() + " state:" + state.toString());
                    return Json.createObjectBuilder().add(JsonOrder.OrderBookingKey, orderBooking).build();
                } else {
                    return orderBooking;
                }
            } else {
                System.out.println("OrderActor.createOrder() Failed - Missing voyageId");
                return Json.createObjectBuilder().add("status", "FAILED").add("ERROR", "VOYAGE_ID_MISSING")
                        .add("orderId", String.valueOf(this.getId())).build();

            }

        } catch (Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR", "Exception")
                    .add("orderId", String.valueOf(this.getId())).build();

        }

    }

    private void unreserveReefer(int reeferId) {
        ActorRef reeferActor = Kar.actorRef(ReeferAppConfig.ReeferActorName, String.valueOf(reeferId));
        JsonObject params = Json.createObjectBuilder().build();
        actorCall(reeferActor, "unreserve", params);
    }

    private void saveOrderStatus(OrderStatus status) {
        super.set(this, Constants.ORDER_STATUS_KEY, Json.createValue(status.name()));
    }

    private boolean voyageBooked(JsonObject orderBookingStatus) {
        return orderBookingStatus.getString("status").equals("OK");
    }

    private void saveOrderReefers(JsonObject orderBookingStatus) throws Exception {
        JsonArray reefers = orderBookingStatus.getJsonArray("reefers");
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
            return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR", "INVALID_CALL")
                    .add(JsonOrder.IdKey, order.getId()).build();
        }
    }
}