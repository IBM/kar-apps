package com.ibm.research.kar.reefer.actors;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.model.JsonOrder;

import static com.ibm.research.kar.Kar.*;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
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

    @Remote
    /**
     * Called once to initialize reefer state. 
     * 
     * @param reeferProperties
     */
    public void setState(JsonObject reeferProperties) {
        System.out.println("ReeferActor.setState() - ID:"+this.getId()); 
   
        Map<String, JsonValue> newStateMap = new HashMap<>();

        try {
            newStateMap.put(ReeferState.MAX_CAPACITY_KEY, Json.createValue(reeferProperties.getInt(ReeferState.MAX_CAPACITY_KEY)));
            newStateMap.put(ReeferState.ORDER_ID_KEY, Json.createValue(reeferProperties.getString(ReeferState.ORDER_ID_KEY)));
            newStateMap.put(ReeferState.VOYAGE_ID_KEY, Json.createValue(reeferProperties.getString(ReeferState.VOYAGE_ID_KEY)));
           // stateMap.put(ReeferAvailCapacityKey, Json.createValue(maxCapacity));
           // stateMap.put(ReeferAllocationStatusKey, Json.createValue(ReeferAllocationStatus.EMPTY.name()));
           // stateMap.put(ReeferConditionKey, Json.createValue(ReeferCondition.NORMAL.name()));
            actorSetMultipleState(this, newStateMap);
            stateMap = newStateMap;

        } catch( Exception e) {
            e.printStackTrace();
        }
    }
    @Remote
    public void reserve(JsonObject message) {
        //JsonObject result = Json.createObjectBuilder().
        System.out.println("ReeferActor.reserve() called - Id:"+this.getId());
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
 
    }
    @Remote
    public void anomaly(JsonObject message) {
        System.out.println("ReeferActor.anomaly() called - Id:"+this.getId()+"\n"+message.toString());
 //       String location = message.getString(ReeferLocationKey);
 //       String allocationStatus = message.getString(ReeferAllocationStatusKey);
 //       actorSetState(this, ReeferLocationKey, Json.createValue(location));
  //      actorSetState(this, ReeferAllocationStatusKey, Json.createValue(allocationStatus));
    }
}