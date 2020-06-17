package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.actor.ActorRef;
import static com.ibm.research.kar.Kar.*;

import java.util.Optional;
//import java.util.concurrent.CompletionStage;
//import javax.inject.Inject;

import javax.json.Json;
import javax.json.JsonObject;
//import javax.json.JsonString;
import javax.json.JsonValue;
@Actor
public class OrderActor extends BaseActor {

    public enum OrderStatus {Pending, ReefersAllocated, VoyageBooked, Rejected, Delivered};
     @Activate
    public void init() {

    }

    @Remote
    public JsonObject createOrder(JsonObject order) {
        System.out.println(
                "OrderActor.createOrder() called- Actor ID:" + this.getId() + " order id:" + order.getString("id"));
   
        try {
            // voyageId is mandatory
            if ( order.containsKey("voyageId") ) {
                String voyageId = order.getString("voyageId");

                if (Optional.of(voyageId).isPresent()) {
                    System.out.println(
                        "OrderActor.createOrder() voyageId:"+voyageId);
                    JsonObject reply = bookVoyage(voyageId, order);
                    if ( reply.getString("status").equals("OK")) {
                        System.out.println("OrderActor.createOrder() - Order Booked");
                        return Json.createObjectBuilder().add("status", "OK").add("orderId", String.valueOf(this.getId())).add("body", reply).build();
                    } else {
                        return reply;
                    }
                } else {
                    System.out.println(
                        "OrderActor.createOrder() Failed - voyageId is null");
                    return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","VOYAGE_ID_MISSING").add("orderId", String.valueOf(this.getId())).build();
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

    private JsonObject bookVoyage(String voyageId, JsonObject order) {
        try {
            JsonObject params = Json.createObjectBuilder().add("callerId", String.valueOf(this.getId()))
                    .add("session", session).add("body", order).build();
            ActorRef voyageActor = actorRef(ReeferAppConfig.VoyageActorName, voyageId);

            JsonValue reply = actorCall(voyageActor, "reserve", params);

            return reply.asJsonObject();
            // actorCallAsync( voyageActor, "reserve", params);
            // JsonValue reply = cf.toCompletableFuture().get();

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","INVALID_CALL").add("orderId", String.valueOf(this.getId())).build();
  
        }
    }
}