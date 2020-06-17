package com.ibm.research.kar.reefer.common;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.actors.ReeferActor;
import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;

import static com.ibm.research.kar.Kar.*;
public class ReeferState {
    private Map<String, JsonValue> reeferState; // = actorGetAllState(reefer); 
    private ActorRef reefer;

    public ReeferState(ActorRef reefer ) {
        this.reefer = reefer;
        // shallow copy actor state since actorGetAllState() returns
        // immutable Collection. We need to be able to modify reefer
        // actor state.
        reeferState = new HashMap<>(actorGetAllState(reefer));   
    }
    public String getId() {
        return reefer.getId();
    }
    public ReeferAllocationStatus getAllocationStatus() {
        String allocation = ((JsonString)reeferState.get(ReeferActor.ReeferAllocationStatusKey)).getString();
        //ReeferAllocationStatus reeferAllocation = 
        return ReeferAllocationStatus.valueOf(allocation);
    }
    public void setAllocationStatus(ReeferAllocationStatus allocationStatus) {
        reeferState.put(ReeferActor.ReeferAllocationStatusKey, Json.createValue(allocationStatus.name()));
    }
    public int getRemainingCapacity() {
        return ((JsonNumber)reeferState.get(ReeferActor.ReeferAvailCapacityKey)).intValue();
 
    }
    public void setRemainingCapacity(int remainingCapacity) {
        reeferState.put(ReeferActor.ReeferAvailCapacityKey, Json.createValue(remainingCapacity));
    }
    public int getMaxCapacity() {
        return ((JsonNumber)reeferState.get(ReeferActor.ReeferMaxCapacityKey)).intValue();
           

    }
    public void save() {
        actorSetMultipleState(reefer, reeferState);
    }
}