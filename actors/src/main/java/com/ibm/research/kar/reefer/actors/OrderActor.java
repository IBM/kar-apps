package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.OrderStatus;
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

    //public enum OrderStatus {Pending, ReefersAllocated, VoyageBooked, Rejected, Delivered};
     @Activate
    public void init() {

    }

    @Remote
    public JsonObject createOrder(JsonObject message) {
        System.out.println(
            "OrderActor.createOrder() called- Actor ID:" + this.getId()+" message:"+message.getJsonObject(Order.OrderKey));
        Order order = new Order(message.getJsonObject(Order.OrderKey));
       
        try {
            // voyageId is mandatory
            if ( order.containsKey(Order.VoyageIdKey) ) {
                String voyageId = order.getVoyageId();
                JsonObject reply = bookVoyage(voyageId, order);

                if ( reply.getString("status").equals("OK")) {
                    System.out.println("OrderActor.createOrder() - Order Booked");
                    return Json.createObjectBuilder().add(Order.OrderBookingKey, reply).build();
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

    private JsonObject bookVoyage(String voyageId, Order order) {
        try {
            JsonObject params = 
                Json.createObjectBuilder().add(Order.OrderKey, order.getAsObject()).build();
            ActorRef voyageActor = actorRef(ReeferAppConfig.VoyageActorName, voyageId);

            JsonValue reply = actorCall(voyageActor, "reserve", params);

            return reply.asJsonObject();

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        }
    }
}