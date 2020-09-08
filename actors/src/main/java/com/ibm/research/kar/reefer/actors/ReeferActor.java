package com.ibm.research.kar.reefer.actors;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;

import static com.ibm.research.kar.Kar.*;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

@Actor
public class ReeferActor extends BaseActor {
    public static final String ReeferMaxCapacityKey = "reeferMaxCapacity";
    public static final String ReeferAvailCapacityKey = "reeferAvailableCapacity";
    public static final String ReeferAllocationStatusKey = "reeferAllocationStatus";
    public static final String ReeferConditionKey = "reeferCondition";
    public static final String ReeferVoyageIdKey = "reeferVoyageId";
    public static final String ReeferLocationKey = "reeferLocation";
    public enum ReeferAllocationStatus {EMPTY, PARTIALLY_ALLOCATED, ALLOCATED};
    public enum ReeferCondition {NORMAL, FAILED, ON_MAINTENANCE};
    Map<String, JsonValue> stateMap = new HashMap<>();

    @Activate
    public void init() {
        stateMap = actorGetAllState(this);
     }

    //@Remote
    /**
     * Called once to initialize reefer state. 
     * 
     * @param reeferProperties
     */
    public void setState(JsonObject reeferProperties) {
        System.out.println("ReeferActor.setState() - ID:"+this.getId()); 
   
//        Map<String, JsonValue> newStateMap = new HashMap<>();

        try {
            if ( reeferProperties.containsKey(ReeferState.MAX_CAPACITY_KEY)) {
                set(this,ReeferState.MAX_CAPACITY_KEY,Json.createValue(reeferProperties.getInt(ReeferState.MAX_CAPACITY_KEY)));
            }
            if ( reeferProperties.containsKey(ReeferState.ORDER_ID_KEY)) {
                set(this,ReeferState.ORDER_ID_KEY,Json.createValue(reeferProperties.getString(ReeferState.ORDER_ID_KEY)));
            }
            if ( reeferProperties.containsKey(ReeferState.VOYAGE_ID_KEY)) {
                set(this,ReeferState.VOYAGE_ID_KEY,Json.createValue(reeferProperties.getString(ReeferState.VOYAGE_ID_KEY)));
            }
            if ( reeferProperties.containsKey(ReeferState.STATE_KEY)) {
                set(this,ReeferState.STATE_KEY,Json.createValue(reeferProperties.getString(ReeferState.STATE_KEY)));
            }

/*

            newStateMap.put(ReeferState.MAX_CAPACITY_KEY, Json.createValue(reeferProperties.getInt(ReeferState.MAX_CAPACITY_KEY)));
            newStateMap.put(ReeferState.ORDER_ID_KEY, Json.createValue(reeferProperties.getString(ReeferState.ORDER_ID_KEY)));
            newStateMap.put(ReeferState.VOYAGE_ID_KEY, Json.createValue(reeferProperties.getString(ReeferState.VOYAGE_ID_KEY)));
            newStateMap.put(ReeferState.VOYAGE_ID_KEY, Json.createValue(reeferProperties.getString(ReeferState.STATE_KEY)));
            */
           
           // stateMap.put(ReeferAvailCapacityKey, Json.createValue(maxCapacity));
           // stateMap.put(ReeferAllocationStatusKey, Json.createValue(ReeferAllocationStatus.EMPTY.name()));
           // stateMap.put(ReeferConditionKey, Json.createValue(ReeferCondition.NORMAL.name()));
 //           actorSetMultipleState(this, newStateMap);
  //          stateMap = newStateMap;

        } catch( Exception e) {
            e.printStackTrace();
        }
    }
    @Remote
    public void reserve(JsonObject message) {
        //JsonObject result = Json.createObjectBuilder().
        System.out.println("ReeferActor.reserve() called - Id:"+this.getId());
       // JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
        setState(message);
    }
    @Remote
    public void unreserve(JsonObject message) {
        //JsonObject result = Json.createObjectBuilder().
        System.out.println(" >>>>>>>>>>>>>>>>>>>>>>>>>>>>> ReeferActor.unreserve() called - Id:"+this.getId());
        JsonObject properties = Json.createObjectBuilder().
            add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.UNALLOCATED.name())).
            add(ReeferState.ORDER_ID_KEY,Json.createValue("")).
            add(ReeferState.VOYAGE_ID_KEY,Json.createValue("")).build();
        setState(properties);
        JsonObject provisionerMessage = Json.createObjectBuilder().add("reeferId", getId()).build();
        JsonValue reply = actorCall(  actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId),"unreserveReefer", provisionerMessage); 
        System.out.println("ReeferActor.unreserve() - reply from the ReeferProvisionerActor - "+reply);
    //    if ( reply.asJsonObject().getString("status").equals("OK") ) {
     //   }
    }

    
    @Remote
    public JsonValue anomaly(JsonObject message) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferActor.anomaly() called - Id:"+this.getId());
        JsonObjectBuilder propertiesBuilder = Json.createObjectBuilder();
        JsonObjectBuilder reply = Json.createObjectBuilder();
        // A reefer with an orderId is on a ship
        JsonValue jsonOrderId = get(this,ReeferState.ORDER_ID_KEY);
        if ( jsonOrderId != null && jsonOrderId != JsonValue.NULL && jsonOrderId.toString().length() > 0 ) {
            // Java JsonValue.toStrong() returns a quoted String so string the quotes
            //String orderId =  jsonOrderId.toString().replace("\"", "");
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferActor.anomaly() - Id:"+this.getId()+" Assigned to Order Id:"+((JsonString)jsonOrderId).getString()+" - Spoiled Reefer - Notifying Order Actor");
            
            JsonObject orderReply = notifyOrderOfSpoilage(((JsonString)jsonOrderId).getString());
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferActor.anomaly() - Id:"+this.getId()+" Order Actor:"+((JsonString)jsonOrderId).getString()+" reply:"+orderReply);
             if ( orderReply.getString(Constants.ORDER_STATUS_KEY).equals("INTRANSIT")) {
                propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.SPOILT.name()));
                reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.SPOILT.name());
            } else {
                propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.MAINTENANCE.name()));
                reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.MAINTENANCE.name());
            }
        } else {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ReeferActor.anomaly() called - Id:"+this.getId()+" Not Assigned to Order - Moving to OnMaintenance");
            reply.add(Constants.REEFER_STATE_KEY, ReeferState.State.MAINTENANCE.name());
            propertiesBuilder.add(ReeferState.STATE_KEY, Json.createValue(ReeferState.State.MAINTENANCE.name()));
        }
        setState(propertiesBuilder.build());
        return reply.build();
    }
    private JsonObject notifyOrderOfSpoilage(String orderId) {
        ActorRef orderActor =  Kar.actorRef(ReeferAppConfig.OrderActorName,orderId);
        JsonObject params = Json.createObjectBuilder().add("reeferId",getId()).build();
        return actorCall( orderActor, "anomaly", params).asJsonObject();
    }
}