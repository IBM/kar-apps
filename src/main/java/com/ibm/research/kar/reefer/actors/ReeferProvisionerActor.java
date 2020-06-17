package com.ibm.research.kar.reefer.actors;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.ReeferAllocator;

import static com.ibm.research.kar.Kar.*;

import java.util.ArrayList;
//import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
//import java.util.concurrent.CompletionStage;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
//import javax.json.JsonString;
//import javax.json.JsonValue;
@Actor
public class ReeferProvisionerActor extends BaseActor {

    Map<String,ActorRef> reeferInventory = new HashMap<>();

    @Activate
    public void init() {
        addReefers(100);
    }
    private void addReefers(int howMany) {
        JsonObject params = Json.createObjectBuilder()
            .add(ReeferActor.ReeferMaxCapacityKey, ReeferAppConfig.ReeferMaxCapacityValue )
            .build();
        for( int i=0; i < howMany; i++ ) {
            String reeferId = UUID.randomUUID().toString();
            // Actors are instantiated lazily
            ActorRef reeferActor =  actorRef(ReeferAppConfig.ReeferActorName,reeferId);
            try {
                actorCall(reeferActor, "configure", params);
            } catch (ActorMethodNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.out.println("ReeferProvisioner.addReefers() - created new reefer - ID:"+reeferActor.getId());
            reeferInventory.put(reeferId, reeferActor);
        }
    }

    @Remote
    public JsonObject bookReefers(JsonObject message) {
        int qty = 0;
        //JsonObject result = Json.createObjectBuilder().
        String callerId = message.getString("callerId");
        JsonObject order = message.getJsonObject("body");
//        List<String> newReefers = new ArrayList<>();
        System.out.println("ReeferProvisionerActor.bookReefers() called - callerId:"+callerId);
//        ActorRef voyageActor =  actorRef("voyage",callerId);
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        if ( order.containsKey("productQty")) {
            qty = order.getInt("productQty");
        } else {

        }
        System.out.println("ReeferProvisionerActor.bookReefers() product qty:"+qty);
        List<ActorRef> allocatedReefers = 
            ReeferAllocator.allocate(new ArrayList<ActorRef>(reeferInventory.values()), qty);
            System.out.println("ReeferProvisionerActor.bookReefers() product qty:"+qty +" Allocated Reefers:"+allocatedReefers.size());

        for( ActorRef reefer : allocatedReefers ) {
 //           String reeferId = UUID.randomUUID().toString();
 //           ActorRef reeferActor =  actorRef("reefer",reeferId);
 
            JsonObject params = Json.createObjectBuilder()
            .add("reeferid",  reefer.getId())
            .add("body", order )
                .build();
            try {
               // CompletionStage<JsonValue> cf = actorCallAsync( reeferActor, "reserve", params);
                actorCall( reefer, "reserve", params);
                //JsonValue reply = cf.toCompletableFuture().get();
                arrayBuilder.add(reefer.getId());
            } catch( ActorMethodNotFoundException ee) {
               // e//e.printStackTrace();
            } catch( Exception ee) {
                ee.printStackTrace();
            }
        }
        JsonObject reply =  Json.createObjectBuilder()
              .add("reefers",  arrayBuilder)
              .add("body", order )
                  .build();
        return reply;
                  
        // try {
            
        //     JsonObject params = Json.createObjectBuilder()
        //     .add("reefers",  arrayBuilder)
        //     .add("body", order )
        //         .build();
        //     System.out.println("ReeferProvisionerActor.bookReefers() calling Voyage Actor - Reefer Booked");
        //     actorCallAsync( voyageActor, "reeferBookingStatus", params);
        //     //CompletionStage<JsonValue> cf = actorCallAsync( reeferActor, "reserve", params);
        //       //  JsonValue reply = cf.toCompletableFuture().get();
        //    // newReefers.add(reeferId);
        // } catch( ActorMethodNotFoundException ee) {
        //     ee.printStackTrace();
        // } catch( Exception ee) {
        //     ee.printStackTrace();
        // }
       
    }
    private List<ActorRef> createReefer(JsonObject order) {
        List<ActorRef> newReefers = new ArrayList<>();

        return newReefers;
    }
}