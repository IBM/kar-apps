package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.actor.ActorRef;
import static com.ibm.research.kar.Kar.*;

import java.util.concurrent.CompletionStage;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.supervisor.ActorSupervisor;

import javax.inject.Inject;
/*
import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;
import static com.ibm.research.kar.Kar.actorTell;
*/
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

@Actor
public class VoyageActor extends BaseActor {
    @Inject
    ActorSupervisor supervisor;
    
    @Activate
    public void init() {

    }

    @Remote
    public JsonObject reserve(JsonObject message) {
        String callerId = message.getString("callerId");
       JsonObject order = message.getJsonObject("body");

        System.out.println("VoyageActor.reserve() called "+message.toString());
        JsonObject params = Json.createObjectBuilder()
				.add("callerId", String.valueOf(this.getId()))
                //.add("session",  session)
                .add("body",  order)
			        .build();
        try {
            //JsonValue reply = actorCall( reeferProvisioner, "bookReefers", order);
            JsonValue reply = actorCall(  actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId),"bookReefers", params);
            System.out.println("VoyageActor.reserve()"+reply.toString());
            JsonObject o = reply.asJsonObject();
            JsonArray reeferIds = o.getJsonArray("reefers");
             for (JsonString j : reeferIds.getValuesAs(JsonString.class)) {
                System.out.println("VoyageActor.reserve() - reeferId:"+j.getString());
            }
            return Json.createObjectBuilder()
            .add("status", "OK")
            .add("reefers",o.getJsonArray("reefers"))
            .add("body",  order)
                .build();
           
            //return o;
            // CompletionStage<JsonValue> cf = actorCallAsync(  actorRef("reefer-provisioner","33333"),"bookReefers", params);
            // JsonValue reply = cf.toCompletableFuture().get();
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

         JsonArray reeferIds = message.getJsonArray("reefers");
         for (JsonString j : reeferIds.getValuesAs(JsonString.class)) {
            System.out.println("VoyageActor.reeferBookingStatus() - reeferId:"+j.getString());
        }
 
      }
    @Deactivate
    public void kill() {
        
    }
}