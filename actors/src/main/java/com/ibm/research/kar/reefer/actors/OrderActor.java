package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;
@Actor
public class OrderActor extends BaseActor {
    private final String REEFERS_KEY = "reefers";
    private final String STATE_KEY = "state";
    JsonArray reeferList;
    JsonValue state;
     @Activate
    public void init() {
       JsonValue reefers = get(this, REEFERS_KEY);
        if ( reefers != null ) {
            JsonArray reeferList = reefers.asJsonArray();
            System.out.println("OrderActor.init() - Order Id:"+getId()+" cached reefer list size:"+reeferList.size());
        }
        state = get(this, STATE_KEY);
    }
    private void unreserveReefer(String reeferId) {


        ActorRef reeferActor =  Kar.actorRef(ReeferAppConfig.ReeferActorName,reeferId);
        JsonObject params = Json.createObjectBuilder().build();
        actorCall( reeferActor, "unreserve", params);

    }
    @Remote
    public JsonObject delivered(JsonObject message) {
        System.out.println("OrderActor.delivered() called- Actor ID:" +getId());
        try {
            JsonValue orderState = Json.createValue(OrderStatus.DELIVERED.ordinal());
            set(this,STATE_KEY, orderState);
            if ( reeferList == null ) {
                JsonValue reefers = get(this, REEFERS_KEY);
                reeferList = reefers.asJsonArray();
                System.out.println("OrderActor.delivered() - unreserving reefers:"+reeferList);
                reeferList.forEach(reefer -> {
                    unreserveReefer(String.valueOf(reefer.toString()));
                });
                System.out.println("OrderActor.delivered() - Order Id:"+getId()+" cached reefer list size:"+reeferList.size());
            }
            return Json.createObjectBuilder().add("status", "OK").add("orderId", String.valueOf(this.getId())).build();
        } catch( Exception e ) {
            e.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","VOYAGE_ID_MISSING").add("orderId", String.valueOf(this.getId())).build();
        }
    }
    @Remote
    public JsonObject createOrder(JsonObject message) {
        System.out.println(
            "OrderActor.createOrder() called- Actor ID:" + this.getId()+" message:"+message);//.getJsonObject(JsonOrder.OrderKey));
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
       
        try {
            // voyageId is mandatory
            if ( order.containsKey(JsonOrder.VoyageIdKey) ) {
                String voyageId = order.getVoyageId();
                JsonObject reply = bookVoyage(voyageId, order);
                System.out.println("OrderActor.createOrder() - Order Booked -Reply:"+reply);
                if ( reply.getString("status").equals("OK")) {

                    JsonArray reefers = reply.getJsonArray("reefers");
                    System.out.println("OrderActor.createOrder() - Order Booked - Reefers:"+reefers.size());
                    if ( reefers != null ) {
                       set(this,"reefers", reefers);
                       System.out.println("OrderActor.createOrder() saved order "+getId()+" reefer list - size"+reefers.size());
                    }
                    return Json.createObjectBuilder().add(JsonOrder.OrderBookingKey, reply).build();
                } else {
                    return reply;
                }
             } else {
                System.out.println(
                    "OrderActor.createOrder() Failed - Missing voyageId");
                return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","VOYAGE_ID_MISSING").add("orderId", String.valueOf(this.getId())).build();

            }
         
        } catch( Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","Exception").add("orderId", String.valueOf(this.getId())).build();

        }

 
    }

    private JsonObject bookVoyage(String voyageId, JsonOrder order) {
        try {
            JsonObject params = 
                Json.createObjectBuilder().add(JsonOrder.OrderKey, order.getAsObject()).build();
            ActorRef voyageActor = actorRef(ReeferAppConfig.VoyageActorName, voyageId);
            JsonValue reply = actorCall(voyageActor, "reserve", params);
            return reply.asJsonObject();
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(JsonOrder.IdKey, order.getId()).build();
        }
    }
}