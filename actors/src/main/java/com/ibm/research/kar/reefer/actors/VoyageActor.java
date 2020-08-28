package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    //@Inject
   // ActorSupervisor supervisor;
    JsonArray orders;
    JsonValue status;

    @Activate
    public void init() {
        System.out.println("VoyageActor.init() actorID:"+this.getId());
        loadStatus();
        loadOrders();

    }
    private void loadStatus() {
        JsonValue value = actorGetState(this, "status");
        if ( value != JsonValue.NULL ) {
            status = value;
        } 
    }
    private void loadOrders() {
        try {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            JsonValue value = actorGetState(this, "orders");
            if ( value != JsonValue.NULL ) {
                System.out.println("++++++ VoyageActor.init() actorId:"+this.getId()+"- Orders: "+value);
    
                value.asJsonArray().forEach(orderIdValue -> {
                    arrayBuilder.add(orderIdValue);
                });
                orders = arrayBuilder.build();
            } else {
                System.out.println("++++++ VoyageActor.init() actorId:"+this.getId()+" - No State Found - Creating new");
                orders = arrayBuilder.build();
                actorSetState(this, "orders", orders);
            }
          
        } catch( Exception e ) {
            e.printStackTrace();
        }
 
    }
    @Remote
    public JsonValue changePosition(JsonObject message) {
        System.out.println(getId()+" VoyageActor.changePosition() called "+message.toString());

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
    public JsonObject getVoyageOrders() {
       JsonArrayBuilder orderIdsBuilder = Json.createArrayBuilder();
        orders.forEach(orderId -> {
            orderIdsBuilder.add(orderId);
        });
        
        return Json.createObjectBuilder()
        .add("status", "OK")
        .add("orders",  orderIdsBuilder.build()) 
        .build();
    }
    @Remote
    public JsonObject getVoyageOrderCount(JsonObject message) {

        return Json.createObjectBuilder()
            .add("status", "OK")
            .add("orders",  orders.size()) 
            .build();
            
    }
    @Remote
    public JsonObject changeState(JsonObject message) {
        JsonValue value =  Json.createValue(message.getString("status"));
        actorSetState(this, "status", value);
        return Json.createObjectBuilder()
        .add("status", "OK")
        .build();
    }
    @Remote
    public JsonObject reserve(JsonObject message) {
       JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
       int orderCount=0;
       if ( orders != JsonValue.NULL ) {
        orderCount = orders.size();
       }

        System.out.println(getId()+" VoyageActor.reserve() called "+message.toString()+" OrderID:"+order.getId()+" Orders size="+orderCount);
/*
        JsonObject params = Json.createObjectBuilder()
                .add(JsonOrder.OrderKey,  order.getAsObject())
                .build();
                */
        try {
            // Book reefers for this order thru the ReeferProvisioner
            JsonValue reply = actorCall(  actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId),"bookReefers", message); 
            if ( reply.asJsonObject().getString("status").equals("OK") ) {
                // save actor state
                saveOrders(order);


                JsonArray reefers = reply.asJsonObject().getJsonArray("reefers");

                actorSetState(this, "status", Json.createValue("Pending"));
/*
                JsonObject params2 = Json.createObjectBuilder()
                .add("voyageId",getId())
                .add("reeferCount",reefers.size())
                .build();
                */
//                try {
         
                //    Response response = restPost("reeferservice","/voyage/update", params2);
                    return Json.createObjectBuilder()
                    .add("status", "OK")
                    .add("reefers",  reply.asJsonObject().getJsonArray("reefers")) 
                    .add(JsonOrder.OrderKey,  order.getAsObject())
                    .build();
                    /*
                } catch( Exception e) {
                    e.printStackTrace();
                    return Json.createObjectBuilder()
                    .add("status", "Failed")
                    .add("error",e.getMessage())
                    .build();
                }
                */
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
     /*
        Store actor state in the kar store
     */
     private void saveOrders(JsonOrder order){
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        orders.forEach(orderIdValue -> {
            arrayBuilder.add(orderIdValue);
        });
        arrayBuilder.add(Json.createValue(order.getId()));

        orders =  arrayBuilder.build();
        actorSetState(this, "orders", orders);
        System.out.println("VoyageActor.reserve() -ActorID:"+this.getId()+" saved orders:"+orders.toString());
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