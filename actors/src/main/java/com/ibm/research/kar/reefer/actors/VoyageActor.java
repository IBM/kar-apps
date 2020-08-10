package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.*;

import java.util.Map;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.supervisor.ActorSupervisor;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

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
        String currentDate = message.getString("currentDate");

        JsonObject params = Json.createObjectBuilder()
        .add("voyageId",getId())
        .add("daysAtSea",daysAtSea)
        .build();
        try {
 
            restPost("reeferservice","/voyage/update", params);
            return Json.createObjectBuilder()
            .add("status", "OK")
            .build();
        } catch( Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder()
            .add("status", "Failed")
            .add("error",e.getMessage())
            .build();
        }
        
 
     

    }

    private boolean shipSecured() {
        JsonObject params = Json.createObjectBuilder()
        .add("voyageId",getId())
        .build();
        try {
 
            Response response = restPost("reeferservice","/voyage/bookship", params);
            //return Json.createObjectBuilder()
            //.add("status", "OK")
            //.build();
        } catch( Exception e) {
            e.printStackTrace();
            //return Json.createObjectBuilder()
            //.add("status", "Failed")
            //.add("error",e.getMessage())
            //.build();
        }

        return true;
    }
    @Remote
    public JsonObject reserve(JsonObject message) {
       JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
 
        System.out.println("VoyageActor.reserve() called "+message.toString());

        JsonObject params = Json.createObjectBuilder()
                .add(JsonOrder.OrderKey,  order.getAsObject())
			    .build();
        try {
            // Book reefers for this order thru the ReeferProvisioner
            JsonValue reply = actorCall(  actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId),"bookReefers", params);
            if ( reply.asJsonObject().getString("status").equals("OK") ) {

                JsonArray reefers = reply.asJsonObject().getJsonArray("reefers");

                JsonObject params2 = Json.createObjectBuilder()
                .add("voyageId",getId())
                .add("reeferCount",reefers.size())
                .build();
                try {
         
                    Response response = restPost("reeferservice","/voyage/update", params2);
                    return Json.createObjectBuilder()
                    .add("status", "OK")
                    .add("reefers",  reply.asJsonObject().getJsonArray("reefers")) 
                    .add(JsonOrder.OrderKey,  order.getAsObject())
                    .build();
                } catch( Exception e) {
                    e.printStackTrace();
                    return Json.createObjectBuilder()
                    .add("status", "Failed")
                    .add("error",e.getMessage())
                    .build();
                }
            } else {
                return reply.asJsonObject();
            }
 
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