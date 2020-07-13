package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.*;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.supervisor.ActorSupervisor;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

@Actor
public class VoyageActor extends BaseActor {
    @Inject
    ActorSupervisor supervisor;
    
    @Activate
    public void init() {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        actorSetState(this, "reefers", arrayBuilder.build());
    }
    @Remote
    public JsonValue changePosition(JsonObject message) {
        System.out.println("VoyageActor.changePosition() called "+message.toString());

        int daysAtSea = message.getInt("daysAtSea");
        JsonObject params = Json.createObjectBuilder()
        .add("id",getId())
        .add("daysAtSea",daysAtSea)
        .build();
        try {
 
           return (JsonValue) call("reeferserver","/voyage/update", params);
         //  return (JsonValue) call("reeferserver","/voyage/update", Json.createValue(10));
         //   return (JsonValue) Kar.call("reeferserver", "time/advance", Json.createValue(0));
        } catch( Exception e) {
            e.printStackTrace();
        }
 
        return Json.createObjectBuilder()
          .add("status", "OK")
          .build();

    }
    @Remote
    public JsonObject reserve(JsonObject message) {
       Order order = new Order(message.getJsonObject(Order.OrderKey));
 
        System.out.println("VoyageActor.reserve() called "+message.toString());
        JsonObject params = Json.createObjectBuilder()
                .add(Order.OrderKey,  order.getAsObject())
			    .build();
        try {
            // Book reefers for this order thru the ReeferProvisioner
            JsonValue reply = actorCall(  actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId),"bookReefers", params);

            JsonValue v = actorGetState(this, "reefers");
            JsonArray reefers = v.asJsonArray();
            reefers.addAll(reefers.size(), reefers);
            actorSetState(this, "reefers", reefers);

            return Json.createObjectBuilder()
                .add("status", "OK")
                .add("reefers",  reply.asJsonObject().getJsonArray("reefers")) 
                .add(Order.OrderKey,  order.getAsObject())
                .build();
           
        } catch( ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","INVALID_CALL").add("orderId", String.valueOf(this.getId())).build();

        } catch( Exception ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","Exception").add("orderId", String.valueOf(this.getId())).build();

        }
     }
     @Remote
     public void reeferBookingStatus(JsonObject message) {
        
         System.out.println("VoyageActor.reeferBookingStatus() called");
        JsonValue v = actorGetState(this, "reefers");
        JsonArray reefers = v.asJsonArray();
        
         JsonArray reeferIds = message.getJsonArray("reefers");
         for (JsonString reeferId : reeferIds.getValuesAs(JsonString.class)) {
            System.out.println("VoyageActor.reeferBookingStatus() - reeferId:"+reeferId.getString());
            reefers.add(reeferId);
        }
        actorSetState(this, "reefers", reefers);
 
      }

    @Remote
    public void completed(JsonObject message) {

    }

    @Deactivate
    public void kill() {
        
    }
}