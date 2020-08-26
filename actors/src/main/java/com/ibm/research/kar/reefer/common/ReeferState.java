package com.ibm.research.kar.reefer.common;

import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;

public interface ReeferState {
    //state = unallocated | allocated | spoilt | maintenance
    public static enum State {ALLOCATED, UNALLOCATED, SPOILT, MAINTENANCE};

    public static final String ALLOCATION_STATUS_KEY="allocationStatus";
    public static final String REMAINING_CAPACITY_KEY = "remainingCapacity";
    public static final String MAX_CAPACITY_KEY = "remainingCapacity";
    public static final String VOYAGE_ID_KEY = "voyageId";
    public static final String ORDER_ID_KEY = "orderId";
    public static final String REEFER_ID_KEY = "id";
    public static final String STATE_KEY = "state";

    public String getId();
    public String getVoyageId();
    public void setVoyageId(String voyageId);
    public boolean alreadyAllocated();
    public boolean partiallyAllocatedToAnotherVoyage(String voyageId);
    public ReeferAllocationStatus getAllocationStatus();
    public void setAllocationStatus(ReeferAllocationStatus allocationStatus);
    public int getRemainingCapacity();
    public void setRemainingCapacity(int remainingCapacity);
    public int getMaxCapacity();
    public void save();
}