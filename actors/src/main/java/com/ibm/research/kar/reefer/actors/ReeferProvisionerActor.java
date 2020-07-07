package com.ibm.research.kar.reefer.actors;
 
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.ReeferAllocator;
import com.ibm.research.kar.reefer.common.packingalgo.PackingAlgo;
import com.ibm.research.kar.reefer.common.packingalgo.SimplePackingAlgo;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Reefer;

import static com.ibm.research.kar.Kar.*;

import java.util.ArrayList;
//import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
//import java.util.concurrent.CompletionStage;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
//import javax.json.JsonString;
//import javax.json.JsonValue;
@Actor
public class ReeferProvisionerActor extends BaseActor {

    private Map<String,ActorRef> reeferInventory = new HashMap<>();
    private PackingAlgo packingAlgo;

    @Activate
    public void init() {
        if ( ReeferAppConfig.PackingAlgoStrategy.equals("simple")) {
            packingAlgo = new SimplePackingAlgo();
        }
        System.out.println(
            "ReeferProvisionerActor.init() called- Actor ID:" + this.getId());
        addReefers(10);
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
                e.printStackTrace();
            }
            System.out.println("ReeferProvisioner.addReefers() - created new reefer - ID:"+reeferActor.getId());
            reeferInventory.put(reeferId, reeferActor);
        }
    }
    @Remote
    public JsonObject updateReeferLocation(JsonObject message) {
        JsonArray reefers = message.getJsonArray("reefers");
        String location = message.getString("location");
        String allocationStatus = message.getString("allocationStatus");
        reefers.forEach(reefer -> {
            String reeferId = reefer.asJsonObject().toString();
            JsonObject params = Json.createObjectBuilder()
            .add("location",  location)
            .add("allocationStatus", allocationStatus )
                .build();
            ActorRef reeferActor =  actorRef(ReeferAppConfig.ReeferActorName,reeferId);
            try {
                actorCall( reeferActor, "changeLocation", params);
               
            } catch( ActorMethodNotFoundException ee) {
                ee.printStackTrace();
            } catch( Exception ee) {
                ee.printStackTrace();
            }
        });
        return Json.createObjectBuilder().add("status", "OK").build();
    }
    @Remote
    public JsonObject bookReefers(JsonObject message) {
        Order order = new Order(message.getJsonObject(Order.OrderKey));
     
        System.out.println("ReeferProvisionerActor.bookReefers() called ");
       // JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        if ( order.containsKey(Order.ProductQtyKey)) {
            int qty = order.getProductQty();
            List<ActorRef> allocatedReefers = 
                ReeferAllocator.allocate(packingAlgo, new ArrayList<ActorRef>(reeferInventory.values()), qty, order.getVoyageId());

            System.out.println("ReeferProvisionerActor.bookReefers() product qty:"+qty +" Allocated Reefers:"+allocatedReefers.size());
            if ( allocatedReefers.size() == 0 ) {
                return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","FailedToAllocateReefers").add(Order.IdKey, order.getId()).build();
            }
            JsonArrayBuilder arrayBuilder = reserveReefers(allocatedReefers, order);

            JsonObject reply =  Json.createObjectBuilder()
                .add("status", "OK")
                .add("reefers",  arrayBuilder)
                .add(Order.OrderKey, order.getAsObject() )
                    .build();
            return reply;

        } else {
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","ProductQuantityMissing").add(Order.IdKey, order.getId()).build();
        }
    }
    private JsonArrayBuilder reserveReefers(List<ActorRef> allocatedReefers, Order order ) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for( ActorRef reefer : allocatedReefers ) {

            JsonObject params = Json.createObjectBuilder()
            .add("reeferid",  reefer.getId())
            .add(Order.OrderKey, order.getAsObject() )
                .build();
            try {
                actorCall( reefer, "reserve", params);
                arrayBuilder.add(reefer.getId());
            } catch( ActorMethodNotFoundException ee) {
                ee.printStackTrace();
            } catch( Exception ee) {
                ee.printStackTrace();
            }
        }
        return arrayBuilder;
    }
    private List<ActorRef> createReefer(JsonObject order) {
        List<ActorRef> newReefers = new ArrayList<>();

        return newReefers;
    }
}