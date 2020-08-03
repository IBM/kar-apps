package com.ibm.research.kar.reefer.common;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.servlet.jsp.JspApplicationContext;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.actors.ReeferActor;
import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;

import static com.ibm.research.kar.Kar.*;
public class ActorReeferState implements ReeferState{
    private Map<String, JsonValue> reeferState; 
    private ActorRef reefer;

    public ActorReeferState(ActorRef reefer ) {
        this.reefer = reefer;
        // shallow copy actor state since actorGetAllState() returns
        // immutable Collection. We need to be able to modify reefer
        // actor state.
        reeferState = new HashMap<>(actorGetAllState(reefer));   
    }
    public String getId() {
        return reefer.getId();
    }
    public String getVoyageId() {
        return ((JsonString)reeferState.get(ReeferActor.ReeferVoyageIdKey)).getString();
    }
    public void setVoyageId(String voyageId) {
        if ( !reeferState.containsKey(ReeferActor.ReeferVoyageIdKey) ||
             reeferState.get(ReeferActor.ReeferVoyageIdKey) == null ||
             ((JsonString)reeferState.get(ReeferActor.ReeferVoyageIdKey)).getString().trim().length() == 0 ) {
                reeferState.put(ReeferActor.ReeferVoyageIdKey, Json.createValue(voyageId));
             }
     
    }
    public boolean alreadyAllocated() {
        return getAllocationStatus().equals(ReeferAllocationStatus.ALLOCATED);
    }
    public boolean partiallyAllocatedToAnotherVoyage(String voyageId) {
        return !getAllocationStatus().equals(ReeferAllocationStatus.EMPTY) && !getVoyageId().equals(voyageId);
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