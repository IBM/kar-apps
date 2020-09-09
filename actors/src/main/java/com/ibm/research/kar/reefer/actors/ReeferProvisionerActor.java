package com.ibm.research.kar.reefer.actors;
 
import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferAllocator;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.common.ReeferState.State;
import com.ibm.research.kar.reefer.common.packingalgo.PackingAlgo;
import com.ibm.research.kar.reefer.common.packingalgo.SimplePackingAlgo;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;
import com.ibm.research.kar.reefer.model.ReeferDTO;
import com.ibm.research.kar.reefer.model.ReeferStats;

import org.apache.thrift.TProcessor;

import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

@Actor
public class ReeferProvisionerActor extends BaseActor {
    
    private ReeferDTO[] reeferMasterInventory = null;
    private JsonValue totalReeferInventory=null;

  //  private Map<String,JsonValue> inventory = new HashMap<>();

  //  private Map<Integer,ActorRef> reeferInventory = new HashMap<>();
  //  private PackingAlgo packingAlgo;

    @Activate
    public void init() {
        System.out.println(
            "ReeferProvisionerActor.init() called- Actor ID:" + this.getId());
        totalReeferInventory = get(this,"total");

        JsonValue state = get(this, Constants.REEFER_PROVISIONER_STATE_KEY);
        if ( state != null ) {
            System.out.println(
                "******************************* ReeferProvisionerActor.init() - Fetched State of size:"+((JsonNumber)totalReeferInventory).intValue());
            reeferMasterInventory = new ReeferDTO[((JsonNumber)totalReeferInventory).intValue()];
            
            for( JsonValue value : state.asJsonArray()) {
                if ( value == null ) {
                    continue;
                }
                JsonObject reefer = value.asJsonObject();
                reeferMasterInventory[reefer.getInt(Constants.REEFER_ID_KEY)] = 
                    new ReeferDTO(reefer.getInt(Constants.REEFER_ID_KEY), 
                    ReeferState.State.valueOf(reefer.getString(Constants.REEFER_STATE_KEY)), 
                    reefer.getString(Constants.ORDER_ID_KEY), 
                    reefer.getString(Constants.VOYAGE_ID_KEY));
            }
            
            // System.out.println("OrderActor.init() - Order Id:"+getId()+" cached reefer list size:"+reeferList.size());
        }
            //state = get(this, STATE_KEY);
            
            /*
        try {
            
            if ( ReeferAppConfig.PackingAlgoStrategy.equals("simple")) {
                packingAlgo = new SimplePackingAlgo();
            }
            
            System.out.println(
                "ReeferProvisionerActor.init() Fetching Inventory from Actor State" );
     
            Map<String,JsonValue> currentInventory = 
                Kar.actorGetAllState(this);
            System.out.println(
                    "ReeferProvisionerActor.init() Fetched Inventory from Actor State" );
           
            if ( currentInventory == null || currentInventory.isEmpty()) {
                System.out.println(
                "ReeferProvisionerActor.init() - inventory not available");
                addReefers(10000);
               
                inventory.forEach( (key,value) -> {
                   Kar.actorSetState(this, key, value);
                 });
                System.out.println("ReeferProvisionerActor.init() - saved reefer inventory ");

            } else {
                System.out.println(
                    "ReeferProvisionerActor.init() - inventory available ");
                inventory = currentInventory;
            }
 

        } catch( Exception e) {
            e.printStackTrace();
        }
 */
    }
    @Deactivate
    public void saveState() {
        if ( reeferMasterInventory == null ) {
            return;
        }
        JsonArrayBuilder stateBuilder = Json.createArrayBuilder();
        try {
            int count=0;
            for (ReeferDTO dto : reeferMasterInventory) {
                if ( dto != null ) {
                    JsonObject reefer = Json.createObjectBuilder().
                    add(Constants.REEFER_ID_KEY, dto.getId()). 
                    add(Constants.REEFER_STATE_KEY, dto.getState().name()).
                    add(Constants.ORDER_ID_KEY, dto.getOrderId()).
                    add(Constants.VOYAGE_ID_KEY, dto.getVoyageId()).
                    build();
                    stateBuilder.add(reefer);
                    count++;
                }

            }
            System.out.println("******************************ReeferProvisionerActor.saveState() - Reefers in use:"+count);
        } catch( Exception e) {
            e.printStackTrace();
        }

        System.out.println("******************************ReeferProvisionerActor.saveState() - SAVING STATE *********************************");
        set(this, Constants.REEFER_PROVISIONER_STATE_KEY, stateBuilder.build());
    }
/*
    private void addReefers(int howMany) {
        JsonObject params = Json.createObjectBuilder()
            .add(ReeferActor.ReeferMaxCapacityKey, ReeferAppConfig.ReeferMaxCapacityValue )
            .build();


        for( int i=0; i < howMany; i++ ) {
            JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
            String id = UUID.randomUUID().toString();
            jsonBuilder.add(ReeferState.REEFER_ID_KEY, id).add(ReeferState.VOYAGE_ID_KEY,"").
                add(ReeferState.ALLOCATION_STATUS_KEY, ReeferAllocationStatus.EMPTY.toString()).
                add(ReeferState.MAX_CAPACITY_KEY, 1000).add(ReeferState.REMAINING_CAPACITY_KEY, 1000);
            inventory.put(id, jsonBuilder.build());
        }
        System.out.println("ReeferProvisioner.addReefers() - created "+howMany+" Actors");
    }
    @Remote
    public JsonObject updateReeferLocation(JsonObject message) {
        JsonArray reefers = message.getJsonArray("reefers");
        String location = message.getString("location");
        String allocationStatus = message.getString(ReeferState.ALLOCATION_STATUS_KEY);
        reefers.forEach(reefer -> {
            String reeferId = reefer.asJsonObject().toString();
            JsonObject params = Json.createObjectBuilder()
            .add("location",  location)
            .add(ReeferState.ALLOCATION_STATUS_KEY, allocationStatus )
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
    */
    private void initMasterInventory(int inventorySize) {
        reeferMasterInventory = new ReeferDTO[inventorySize]; 
        set(this,"total",Json.createValue(inventorySize));
    }
    private int getReeferInventorySize() {
        Response response = Kar.restGet("reeferservice", "reefers/inventory/size");
        JsonValue size = response.readEntity(JsonValue.class);
       
        System.out.println("ReeferProvisionerActor.getReeferInventorySize() - Inventory Size:"+ (JsonNumber)size);
        return ((JsonNumber)size).intValue();
    }
    private void createReeferActor(ReeferDTO reefer) {
        ActorRef reeferActor =  Kar.actorRef(ReeferAppConfig.ReeferActorName,String.valueOf(reefer.getId()));

        try {
            JsonObject params = Json.createObjectBuilder()
                .add(ReeferState.ORDER_ID_KEY,  reefer.getOrderId())
                .add(ReeferState.MAX_CAPACITY_KEY, ReeferAppConfig.ReeferMaxCapacityValue)
                .add(ReeferState.VOYAGE_ID_KEY, reefer.getVoyageId() )
                .add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.ALLOCATED.name()))
                .build();
            actorCall( reeferActor, "reserve", params);
            JsonValue booked = get(this,Constants.TOTAL_BOOKED_KEY);
            int totalBooked;
            if ( booked == null ) {
                totalBooked = 0;
            } else {
                totalBooked = ((JsonNumber)booked).intValue();
            }
            totalBooked++;
            System.out.println("............................. Total Booked Reefers:"+totalBooked);
            set(this,Constants.TOTAL_BOOKED_KEY,Json.createValue( totalBooked));
            updateRest();
        } catch( ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        } catch( Exception ee) {
            ee.printStackTrace();
        }
    }
    @Remote
    public JsonObject getStats(JsonObject message) {
        return getReeferStats();
    }
    private JsonObject getReeferStats() {
        int booked=0;
        int intransit=0;
        if ( reeferMasterInventory == null) {
            initMasterInventory(getReeferInventorySize());
        }
        JsonObjectBuilder stats = Json.createObjectBuilder();
        JsonValue total = get(this, "total");
        if ( total == null ) {
            total = Json.createValue(0);
        }
        JsonValue  totalBooked = get(this, Constants.TOTAL_BOOKED_KEY);
        if ( totalBooked == null ) {
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~ReeferProvisionerActor.getReeferStats() - totalBooked NOT SET");
            totalBooked = Json.createValue(0);
        } else {
            booked = ((JsonNumber)totalBooked).intValue();
        }
        JsonValue totalInTransit = get(this, Constants.TOTAL_INTRANSIT_KEY);
        if ( totalInTransit == null ) {
            totalInTransit = Json.createValue(0);
        } else {
            intransit = ((JsonNumber)totalInTransit).intValue();
        }
        JsonValue totalSpoilt = get(this, Constants.TOTAL_SPOILT_KEY);
        if ( totalSpoilt == null ) {
            totalSpoilt = Json.createValue(0);
        }
        JsonValue totalOnMaintenance = get(this, Constants.TOTAL_ONMAINTENANCE_KEY);
        if ( totalOnMaintenance == null ) {
            totalOnMaintenance = Json.createValue(0);
        }
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~ReeferProvisionerActor.getReeferStats() - totalBooked:"+booked+" in-transit:"+intransit+" spoilt:"+totalSpoilt+" on-maintenance:"+totalOnMaintenance);
        return Json.createObjectBuilder()
        .add("total",total)
        .add("totalBooked", totalBooked)
        .add("totalInTransit", totalInTransit)
        .add("totalSpoilt", totalSpoilt)
        .add("totalOnMaintenance", totalOnMaintenance)
        .build();
    }
    public void updateRest() {

        try {
 
            Kar.restPost("reeferservice","/reefers/stats/update", getReeferStats());

        } catch( Exception e) {
            e.printStackTrace();
        }
    }
    @Remote
    public JsonObject updateInTransit(JsonObject message ) {
        System.out.println("ReeferProvisionerActor.updateInTransit() - message:"+message);
        JsonObjectBuilder reply = Json.createObjectBuilder();
        JsonValue inTransitAlready = get(this, Constants.TOTAL_INTRANSIT_KEY);
        int newInTransit = message.getInt("in-transit");
        int totalInTransit = 0;
        //int totalInTransit = ((JsonNumber)inTransit).intValue();
        if ( inTransitAlready != null ) {
            totalInTransit = ((JsonNumber)inTransitAlready).intValue();
            totalInTransit += newInTransit;
        } else {
            totalInTransit = newInTransit;
        }
        set(this, Constants.TOTAL_INTRANSIT_KEY, Json.createValue(totalInTransit));
        updateRest();
        return reply.build();
    }
    @Remote
    public JsonObject unreserveReefer(JsonObject message ) {
        JsonObjectBuilder reply = Json.createObjectBuilder();
    
        String reeferId = message.getString(Constants.REEFER_ID_KEY).trim();
        if ( reeferMasterInventory[Integer.valueOf(reeferId)] != null) {
            // Reefers can be marked as spoilt only during the voyage. When a voyage ends
            // all spoilt reefers are automatically put on maintenance.
            if ( reeferMasterInventory[Integer.valueOf(reeferId)].getState().equals(State.SPOILT)) {
                reeferMasterInventory[Integer.valueOf(reeferId)].setState(State.MAINTENANCE);
                changeReeferState(reeferMasterInventory[Integer.valueOf(reeferId)], Integer.valueOf(reeferId),  ReeferState.State.MAINTENANCE,Constants.TOTAL_ONMAINTENANCE_KEY);

                JsonValue totalSpoilt = get(this, Constants.TOTAL_SPOILT_KEY);
                if ( totalSpoilt != null ) {
                    int spoilt = ((JsonNumber)totalSpoilt).intValue();
                    if ( spoilt > 0 ) {
                        spoilt--;
                    }
                    set(this,Constants.TOTAL_SPOILT_KEY,Json.createValue(spoilt));

                    System.out.println("ReeferProvisioner.unreserveReefer() - spoilt reefer:"+reeferId+" arrived - changed state to OnMaintenance");
                }
            } else {
                reeferMasterInventory[Integer.valueOf(reeferId)].setState(State.UNALLOCATED);
            }
            
        }
        int totalBooked = 0;
        JsonValue booked = get(this, Constants.TOTAL_BOOKED_KEY);
        if ( booked != null ) {
            totalBooked = ((JsonNumber)booked).intValue()-1;
            set(this, Constants.TOTAL_BOOKED_KEY, Json.createValue(totalBooked));
         }
        int totalInTransit=0;
        JsonValue inTransit = get(this, Constants.TOTAL_INTRANSIT_KEY);
        if ( inTransit != null ) {
            totalInTransit = ((JsonNumber)inTransit).intValue()-1;
            if ( totalInTransit < 0 ) {
                totalInTransit = 0;
            }
            set(this, Constants.TOTAL_INTRANSIT_KEY, Json.createValue(totalInTransit));
        }
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> ReeferProvisionerActor.unreserverReefer() - released reefer "+reeferId+" total booked"+totalBooked+" totalInTransit:" + totalInTransit+ " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        updateRest();

        return reply.build();
    }
    @Remote
    public void reeferAnomaly(JsonObject message) {
        int reeferId = message.getInt(Constants.REEFER_ID_KEY);

        try {
            // lazily initialize master reefer inventory list on the first call.
            // This is fast since all we do is just creating an array of
            // fixed size
            if ( reeferMasterInventory == null) {
                initMasterInventory(getReeferInventorySize());
            }
            ReeferDTO reefer = reeferMasterInventory[reeferId];
         //   if ( reefer != null && reefer.getState().equals(State.ALLOCATED)) {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferProvisionerActor.reeferAnomaly() - reeferId:"+reeferId);
           
            ActorRef reeferActor =  Kar.actorRef(ReeferAppConfig.ReeferActorName,String.valueOf(reeferId));
            // placeholder for future params
            JsonObject params = Json.createObjectBuilder().build();
            JsonValue reply = actorCall( reeferActor, "anomaly", params);
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferProvisionerActor.reeferAnomaly() - reeferId:"+reeferId+" Order Actor Reply:"+reply);

            ReeferState.State state = ReeferState.State.valueOf(reply.asJsonObject().getString(Constants.REEFER_STATE_KEY) );
            
            if ( reeferSpoilt( state) ) {
                
                if ( OrderStatus.BOOKED.equals( OrderStatus.valueOf(reply.asJsonObject().getString(Constants.ORDER_STATUS_KEY))) ) {
                    changeReeferState(reefer, reeferId, ReeferState.State.MAINTENANCE,Constants.TOTAL_ONMAINTENANCE_KEY);
                    // Order has been booked but a reefer in it is spoilt. Remove spoilt reefer from the order and replace with a new one. 
                    List<ReeferDTO> replacementReefer = ReeferAllocator.allocateReefers(reeferMasterInventory, Constants.REEFER_CAPACITY, String.valueOf(reefer.getId()), reefer.getVoyageId());
                    //JsonArrayBuilder arrayBuilder = Json.createArrayBuilder(orderReefers);
                    // there should only be one reefer replacement
                    if ( replacementReefer.size() > 0 ) {
                        createReeferActor(replacementReefer.get(0));
                        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferProvisionerActor.reeferAnomaly() - notifying order actor to replace reeferId:"+reeferId+" with:"+replacementReefer.get(0).getId());
                        notifyOrderOfReeferReplacement(reefer.getOrderId(), reefer.getId(), replacementReefer.get(0).getId());
                    }
                    //updateRest();
                } else {
                    changeReeferState(reefer, reeferId, ReeferState.State.SPOILT,Constants.TOTAL_SPOILT_KEY);
                }
                
            } else { //if ( reeferMaintenance( state) ) {
                changeReeferState(reefer, reeferId, ReeferState.State.MAINTENANCE,Constants.TOTAL_ONMAINTENANCE_KEY);
            }
 
        
        } catch( ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        } catch( Exception ee) {
            ee.printStackTrace();
        }
    }
    private JsonObject notifyOrderOfReeferReplacement(String orderId, int spoliedReeferId, int replacementReeferId) {
        ActorRef orderActor =  Kar.actorRef(ReeferAppConfig.OrderActorName,orderId);
        JsonObject params = Json.createObjectBuilder().add(Constants.REEFER_ID_KEY,spoliedReeferId).add(Constants.REEFER_REPLACEMENT_ID_KEY,replacementReeferId).build();
       // JsonObject params = Json.createObjectBuilder().add(Constants.REEFER_ID_KEY,String.valueOf(spoliedReeferId)).add(Constants.REEFER_REPLACEMENT_ID_KEY,String.valueOf(replacementReeferId)).build();
        return actorCall( orderActor, "replaceReefer", params).asJsonObject();
    }
    private void changeReeferState(ReeferDTO reefer, int reeferId, ReeferState.State newState, String key) {
        JsonValue v  = get(this, key);
        int total  = 0;
        if ( v != null ) {
            total = ((JsonNumber)get(this, key)).intValue();
        }
 
        if ( reefer != null ) {
            reefer.setState(newState);
            
        } else {
            reeferMasterInventory[reeferId] = new ReeferDTO(reeferId, newState, "", "");
        }
        set(this, key, Json.createValue(++total));
        updateRest();
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferProvisionerActor.changeReeferState() - reeferId:"+reeferId+" newState:"+newState+" total:"+total);
    }
    private boolean reeferSpoilt(ReeferState.State state) {
        if ( state.equals(ReeferState.State.SPOILT)) {
            return true;
        }
        return false;
    }
    private boolean reeferMaintenance(ReeferState.State state) {
        if ( state.equals(ReeferState.State.MAINTENANCE)) {
            return true;
        }
        return false;
    }
    @Remote
    public JsonObject bookReefers(JsonObject message) {

        // lazily initialize master reefer inventory list on the first call.
        // This is fast since all we do is just creating an array of
        // fixed size
        if ( reeferMasterInventory == null) {
            initMasterInventory(getReeferInventorySize());
        }

        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
        System.out.println("ReeferProvisionerActor.bookReefers() called - Order:"+order.getId());
  
        if ( order.containsKey(JsonOrder.ProductQtyKey)) {
            
            int qty = order.getProductQty();
            List<ReeferDTO> orderReefers = ReeferAllocator.allocateReefers(reeferMasterInventory, qty, order.getId(), order.getVoyageId());
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

            for( ReeferDTO reefer : orderReefers ) {
                arrayBuilder.add(reefer.getId());
                createReeferActor(reefer);
            }

            updateRest();

/*
            List<ReeferState> allocatedReefers = 
                ReeferAllocator.allocateReefers(packingAlgo, new ArrayList<JsonValue>(inventory.values()), qty, order.getVoyageId());

            System.out.println("ReeferProvisionerActor.bookReefers() product qty:"+qty +" Allocated Reefers:"+allocatedReefers.size());
            if ( allocatedReefers.size() == 0 ) {
                return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","FailedToAllocateReefers").add(JsonOrder.IdKey, order.getId()).build();
            }

            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();

            for( ReeferState reefer : allocatedReefers ) {
                arrayBuilder.add(reefer.getId());
            }
*/
            // Uncomment below when supporting reefers
            //JsonArrayBuilder arrayBuilder = reserveReefers(allocatedReefers, order);

            return  Json.createObjectBuilder()
                .add("status", "OK")
                .add("reefers",  arrayBuilder)
                .add(JsonOrder.OrderKey, order.getAsObject() )
                    .build();
       //     return reply;

        } else {
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","ProductQuantityMissing").add(JsonOrder.IdKey, order.getId()).build();
        }
    }

}