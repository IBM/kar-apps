package com.ibm.research.kar.reefer.common;

import javax.json.JsonObject;
import javax.json.JsonValue;

import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;

public class SimpleReeferState implements ReeferState {
    private boolean alreadyAllocated;
    private String id;
    private int maxCapacity;
    private int remainingCapacity;
    private ReeferAllocationStatus allocationStatus;
    private String voyageId;

    SimpleReeferState(JsonValue state) {
        JsonObject reeferState = state.asJsonObject();
        id = reeferState.getString(ReeferState.REEFER_ID_KEY);
        maxCapacity = reeferState.getInt(ReeferState.MAX_CAPACITY_KEY);
        remainingCapacity = reeferState.getInt(ReeferState.REMAINING_CAPACITY_KEY);
        voyageId = reeferState.getString(ReeferState.VOYAGE_ID_KEY);
        allocationStatus = ReeferAllocationStatus.valueOf(reeferState.getString(ReeferState.ALLOCATION_STATUS_KEY));
    }
    SimpleReeferState(String reeferId, int maxCapacity, String voyageId) {
        this.id = reeferId;
        this.maxCapacity = this.remainingCapacity = maxCapacity;
        this.voyageId = voyageId;
        allocationStatus = ReeferAllocationStatus.EMPTY;
    }
    @Override
    public boolean alreadyAllocated() {
        return getAllocationStatus().equals(ReeferAllocationStatus.ALLOCATED);
    }

    @Override
    public ReeferAllocationStatus getAllocationStatus() {

        return allocationStatus;
    }

    @Override
    public String getId() {
         return id;
    }

    @Override
    public int getMaxCapacity() {

        return maxCapacity;
    }

    @Override
    public int getRemainingCapacity() {
        return remainingCapacity;
    }

    @Override
    public String getVoyageId() {
    
        return voyageId;
    }

    @Override
    public boolean partiallyAllocatedToAnotherVoyage(String voyageId) {
       return !getAllocationStatus().equals(ReeferAllocationStatus.EMPTY) && !getVoyageId().equals(voyageId);
    }

    @Override
    public void save() {


    }

    @Override
    public void setAllocationStatus(ReeferAllocationStatus allocationStatus) {
        this.allocationStatus = allocationStatus;

    }

    @Override
    public void setRemainingCapacity(int remainingCapacity) {
        this.remainingCapacity = remainingCapacity;

    }

    @Override
    public void setVoyageId(String voyageId) {
        this.voyageId = voyageId;

    }
    
}