package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorGetAllState;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import javax.json.JsonValue;

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

     @Activate
    public void init() {

    }

    @Remote
    public JsonObject createOrder(JsonObject message) {
        System.out.println(
            "OrderActor.createOrder() called- Actor ID:" + this.getId()+" message:"+message.getJsonObject(JsonOrder.OrderKey));
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
       
        try {
            // voyageId is mandatory
            if ( order.containsKey(JsonOrder.VoyageIdKey) ) {
                String voyageId = order.getVoyageId();
                JsonObject reply = bookVoyage(voyageId, order);

                if ( reply.getString("status").equals("OK")) {
                    System.out.println("OrderActor.createOrder() - Order Booked");
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
            
//            Map<String, JsonValue> stateMap = actorGetAllState(this);
            
            ActorRef voyageActor = actorRef(ReeferAppConfig.VoyageActorName, voyageId);

            JsonValue reply = actorCall(voyageActor, "reserve", params);

            return reply.asJsonObject();

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(JsonOrder.IdKey, order.getId()).build();
  
        }
        
    }
}