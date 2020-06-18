package com.ibm.research.kar.reefer.actors;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.model.Order;

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
    
    public enum ReeferAllocationStatus {EMPTY, PARTIALLY_ALLOCATED, ALLOCATED};
    public enum ReeferCondition {NORMAL, FAILED, ON_MAINTENANCE};
  
    @Activate
    public void init() {
//       System.out.println("ReeferActor.init() - ID:"+this.getId());
        // Map<String, JsonValue> stateMap =  actorGetAllState(this);
        // if ( !stateMap.isEmpty() ) {

        // }
    }

    @Remote
    /**
     * Called once to initialize reefer state. 
     * 
     * @param reeferProperties
     */
    public void configure(JsonObject reeferProperties) {
        System.out.println("ReeferActor.configure() - ID:"+this.getId()); 
        int maxCapacity = reeferProperties.getInt(ReeferMaxCapacityKey);
        Map<String, JsonValue> stateMap = new HashMap<>();

        stateMap.put(ReeferMaxCapacityKey, Json.createValue(maxCapacity));
        stateMap.put(ReeferAvailCapacityKey, Json.createValue(maxCapacity));
        stateMap.put(ReeferAllocationStatusKey, Json.createValue(ReeferAllocationStatus.EMPTY.name()));
        stateMap.put(ReeferConditionKey, Json.createValue(ReeferCondition.NORMAL.name()));
        actorSetMultipleState(this, stateMap);

    }
    @Remote
    public void reserve(JsonObject message) {
        //JsonObject result = Json.createObjectBuilder().
        System.out.println("ReeferActor.reserve() called - Id:"+this.getId());
        Order order = new Order(message.getJsonObject(Order.OrderKey));
 
        //ActorRef voyageActor =  actorRef("voyage","444");
  
    }
}