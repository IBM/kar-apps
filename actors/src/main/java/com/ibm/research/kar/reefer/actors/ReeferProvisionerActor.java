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
import com.ibm.research.kar.reefer.common.packingalgo.PackingAlgo;
import com.ibm.research.kar.reefer.common.packingalgo.SimplePackingAlgo;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.ReeferDTO;
import com.ibm.research.kar.reefer.model.ReeferStats;
import com.ibm.research.kar.reefer.model.ReeferDTO.State;

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
                        State.valueOf(reefer.getString(Constants.REEFER_STATE_KEY)), 
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
        System.out.println("ReeferProvisionerActor.getReeferInventorySize() - Inventory Size:"+size);
        return Integer.valueOf(size.toString());
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
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~ReeferProvisionerActor.getReeferStats() - totalBooked:"+booked+" in-transit:"+intransit);
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
    
        String reeferId = message.getString("reeferId").trim();
        reeferMasterInventory[Integer.valueOf(reeferId)] = null;
        int totalBooked = 0;
        JsonValue booked = get(this, Constants.TOTAL_BOOKED_KEY);
        if ( booked != null ) {
            totalBooked = ((JsonNumber)booked).intValue()-1;
            set(this, Constants.TOTAL_BOOKED_KEY, Json.createValue(totalBooked));
           // System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> ReeferProvisionerActor.unreserverReefer() - freeing reefer from booked "+reeferId+" ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ booked:"+totalBooked);

        }
        int totalInTransit=0;
        JsonValue inTransit = get(this, Constants.TOTAL_INTRANSIT_KEY);
        if ( inTransit != null ) {
            totalInTransit = ((JsonNumber)inTransit).intValue()-1;
            if ( totalInTransit < 0 ) {
                totalInTransit = 0;
            }
            set(this, Constants.TOTAL_INTRANSIT_KEY, Json.createValue(totalInTransit));
            //System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> ReeferProvisionerActor.unreserverReefer() - freeing reefer from intransit "+reeferId+" ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ inTransit:"+totalInTransit);

        }
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>> ReeferProvisionerActor.unreserverReefer() - released reefer "+reeferId+" total booked"+totalBooked+" totalInTransit:" + totalInTransit+ " ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        updateRest();

        return reply.build();
    }
    @Remote
    public void reeferAnomaly(JsonObject message) {
        int reeferId = message.getInt(Constants.REEFER_ID_KEY);
        try {
            ActorRef reeferActor =  Kar.actorRef(ReeferAppConfig.ReeferActorName,String.valueOf(reeferId));
            // placeholder for future params
            JsonObject params = Json.createObjectBuilder()
                .build();
            if ( reeferSpoilt( actorCall( reeferActor, "anomaly", params) ) ) {
                ReeferDTO reefer = reeferMasterInventory[reeferId];
                if ( reefer != null ) {
                    reefer.setState(State.SPOILT);

                    int totalSpoilt = ((JsonNumber)get(this, Constants.TOTAL_SPOILT_KEY)).intValue();
                    set(this, Constants.TOTAL_SPOILT_KEY, Json.createValue(totalSpoilt++));
                    updateRest();
                }
            }

        
        } catch( ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        } catch( Exception ee) {
            ee.printStackTrace();
        }
    }
    private boolean reeferSpoilt(JsonValue response) {
        if ( response.toString().equals("SPOILT")) {
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
            int count = 0;
            for( int i=0; i < reeferMasterInventory.length; i++ ) {
                if ( reeferMasterInventory[i] != null ) {
                    count++;
                }
            }
            System.out.println(":::::: Reserved Reefer Count:"+count);

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
    /*
    private JsonArrayBuilder reserveReefers(List<ActorRef> allocatedReefers, JsonOrder order ) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for( ActorRef reefer : allocatedReefers ) {

            JsonObject params = Json.createObjectBuilder()
            .add("reeferid",  reefer.getId())
            .add(JsonOrder.OrderKey, order.getAsObject() )
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
    
    private int randomIndex() {
        XoRoShiRo128PlusRandom xoroRandom = new XoRoShiRo128PlusRandom();
        return xoroRandom.nextInt(ReeferAppConfig.ReeferInventorySize);
    }
    */
}