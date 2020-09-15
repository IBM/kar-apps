package com.ibm.research.kar.reefer.actors;
 
import static com.ibm.research.kar.Kar.actorCall;

import java.time.Instant;
import java.util.List;

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
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferAllocator;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.common.ReeferState.State;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;
import com.ibm.research.kar.reefer.model.ReeferDTO;

@Actor
public class ReeferProvisionerActor extends BaseActor {
    
    private ReeferDTO[] reeferMasterInventory = null;
    private JsonValue totalReeferInventory=null;

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
            
        }

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
                    JsonObject reefer = reeferJsonObject(dto);
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
    private JsonObject reeferJsonObject(ReeferDTO reefer) {
        JsonObjectBuilder reeferObjectBuilder = Json.createObjectBuilder();
        if ( reefer != null ) {
            //JsonObject reefer = Json.createObjectBuilder().
            reeferObjectBuilder.add(Constants.REEFER_ID_KEY, reefer.getId()). 
            add(Constants.REEFER_STATE_KEY, reefer.getState().name()).
            add(Constants.ORDER_ID_KEY, reefer.getOrderId()).
            add(Constants.VOYAGE_ID_KEY, reefer.getVoyageId());
            //add(Constants.DATE_KEY, reefer.getMaintenanceReleaseDate());
        }
        return reeferObjectBuilder.build();
    }

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
    public JsonObject releaseReefersfromMaintenance(JsonObject message ) {
        System.out.println("ReeferProvisionerActor.releaseReefersfromMaintenance() - message:"+message);

        try {

            JsonValue onMaintenanceList =  Kar.actorGetState(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST);
         
            if (onMaintenanceList != null && onMaintenanceList != JsonValue.NULL && onMaintenanceList.asJsonArray().size() > 0 ) {
                System.out.println(
                    "******************************* ReeferProvisionerActor.releaseReefersfromMaintenance() - Fetched OnMaintenance List of size:"+onMaintenanceList.asJsonArray().size());
                Instant today = Instant.parse(message.getString(Constants.DATE_KEY));
                JsonArrayBuilder builder = Json.createArrayBuilder();
                for( JsonValue reeferId: onMaintenanceList.asJsonArray() ) {
                    ReeferDTO r = reeferMasterInventory[((JsonNumber)reeferId).intValue()];
                    if ( releaseFromMaintenance(today, r)) {
                        System.out.println("ReeferProvisioner.releaseReefersfromMaintenance() - releasing reefer:"+reeferId+" from maintenance. Today:"+today+" reefer release date:"+r.getMaintenanceReleaseDate()+" state:"+r.getState().name());
                        r.setState(State.UNALLOCATED);
                        r.setMaintenanceReleaseDate(null);
                        Kar.actorDeleteState(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, String.valueOf(r.getId()));
                        JsonValue totalOnMainteJsonValue = get(this, Constants.TOTAL_ONMAINTENANCE_KEY);
                        if ( totalOnMainteJsonValue != null ) {
                            int onMaintenance = ((JsonNumber)totalOnMainteJsonValue).intValue();
                            if ( onMaintenance > 0 ) {
                                onMaintenance--;
                            }
                            set(this,Constants.TOTAL_ONMAINTENANCE_KEY,Json.createValue(onMaintenance));
        
                            System.out.println("ReeferProvisioner.releaseReefersfromMaintenance() - released reefer:"+reeferId+" from maintenance. Today:"+today);
                        }
                    } else {
                       
                        builder.add(Json.createValue(r.getId()));
                    }
                }
                Kar.actorSetState(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, builder.build());
            } else {
                System.out.println("ReeferProvisionerActor.releaseReefersfromMaintenance() - onMaintenance list is empty");
            }
            
        
        } catch( Exception e ) {
            e.printStackTrace();
        }
 
     
        return Json.createObjectBuilder().build();
    }
    private boolean releaseFromMaintenance(Instant today, ReeferDTO reefer) {
        if ( reefer.getMaintenanceReleaseDate() == null ) {
            System.out.println("ReeferProvisionerActor.releaseFromMaintenance() - maintenance release date not set for reefer:"+reefer.getId()+" state:"+reefer.getState().name());
            return false;
        }
        if ( today.equals(Instant.parse(reefer.getMaintenanceReleaseDate())) || today.isAfter(Instant.parse(reefer.getMaintenanceReleaseDate()))) {
            return true;
        }
        return false;
    }
    @Remote
    public JsonObject updateInTransit(JsonObject message ) {
        System.out.println("ReeferProvisionerActor.updateInTransit() - message:"+message);
        JsonObjectBuilder reply = Json.createObjectBuilder();
        int newInTransit = message.getInt("in-transit");

        JsonValue booked = get(this,Constants.TOTAL_BOOKED_KEY);
        int totalBooked;
        if ( booked == null ) {
            totalBooked = 0;
        } else {
            
            totalBooked = ((JsonNumber)booked).intValue() - newInTransit;
            if ( totalBooked < 0 ) {
                System.out.println("ReeferProvisionerActor.updateInTransit() - bookkeeping error. Total booked is less than new in transit - totalBooked:"+((JsonNumber)booked).intValue()+" newInTransit:"+newInTransit);
                totalBooked = 0;
            }
        }
        set(this, Constants.TOTAL_BOOKED_KEY, Json.createValue(totalBooked));

        JsonValue inTransitAlready = get(this, Constants.TOTAL_INTRANSIT_KEY);

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
    private void reeferOnMaintenance(String reeferId ) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        JsonValue onMaintenanceList = Kar.actorGetState(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST);
        if ( onMaintenanceList != null && onMaintenanceList != JsonValue.NULL) {
            
            onMaintenanceList.asJsonArray().forEach(reefer-> {
                builder.add(reefer);
            });
            
        }
        builder.add(Json.createValue(Integer.valueOf(reeferId)));
        JsonArray array = builder.build();

        Kar.actorSetState(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, array);
        System.out.println("############################### ReeferProvisionerActor.reeferOnMaintenance() - ActorId:"+getId()+" saved new onMaintenance list:"+array.toString());
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
                reeferMasterInventory[Integer.valueOf(reeferId)].setMaintenanceReleaseDate(TimeUtils.getInstance().getCurrentDate().toString());
                reeferOnMaintenance(reeferId);

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
        /*
        int totalBooked = 0;
        JsonValue booked = get(this, Constants.TOTAL_BOOKED_KEY);
        if ( booked != null ) {
            totalBooked = ((JsonNumber)booked).intValue()-1;
            set(this, Constants.TOTAL_BOOKED_KEY, Json.createValue(totalBooked));
         }
         */
        int totalInTransit=0;
        JsonValue inTransit = get(this, Constants.TOTAL_INTRANSIT_KEY);
        if ( inTransit != null ) {
            totalInTransit = ((JsonNumber)inTransit).intValue()-1;
            if ( totalInTransit < 0 ) {
                totalInTransit = 0;
            }
            set(this, Constants.TOTAL_INTRANSIT_KEY, Json.createValue(totalInTransit));
        }
        JsonValue totalBooked = get(this,Constants.TOTAL_BOOKED_KEY);
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
                    String today = message.getString(Constants.DATE_KEY);
                    changeReeferState(reefer, reeferId, ReeferState.State.MAINTENANCE,Constants.TOTAL_ONMAINTENANCE_KEY);
                    reefer.setMaintenanceReleaseDate(today);
                    reeferMasterInventory[Integer.valueOf(reeferId)].setState(State.MAINTENANCE);
                    reeferOnMaintenance(String.valueOf(reeferId));
                    //Kar.actorSetState(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, String.valueOf(reeferId), Json.createValue(reeferId));
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
                    JsonObject orderId = Json.createObjectBuilder().add(Constants.ORDER_ID_KEY,reefer.getOrderId()).build();
                    Kar.restPost("reeferservice","/orders/spoilt", orderId);
                   
                }
                
            } else { //if ( reeferMaintenance( state) ) {
                String today = message.getString(Constants.DATE_KEY);
                changeReeferState(reefer, reeferId, ReeferState.State.MAINTENANCE,Constants.TOTAL_ONMAINTENANCE_KEY);
                
                reeferMasterInventory[Integer.valueOf(reeferId)].setMaintenanceReleaseDate(today);
                //reefer.setMaintenanceReleaseDate(TimeUtils.getInstance().getCurrentDate().toString());
                reeferMasterInventory[Integer.valueOf(reeferId)].setState(State.MAINTENANCE);
                reeferOnMaintenance(String.valueOf(reeferId));
                //Kar.actorSetState(this, Constants.ON_MAINTENANCE_PROVISIONER_LIST, String.valueOf(reeferId), Json.createValue(reeferId));
                System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferProvisionerActor.reeferAnomaly() - id:"+getId()+" added reefer:"+reeferId+" to "+Constants.ON_MAINTENANCE_PROVISIONER_LIST+" Map");
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
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> ReeferProvisionerActor.bookReefers())- Order:"+order.getId() + " reefer count:"+orderReefers.size());
            updateRest();

            return  Json.createObjectBuilder()
                .add("status", "OK")
                .add("reefers",  arrayBuilder)
                .add(JsonOrder.OrderKey, order.getAsObject() )
                    .build();
        } else {
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","ProductQuantityMissing").add(JsonOrder.IdKey, order.getId()).build();
        }
    }

}