/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.kar.reefer.common;

import com.ibm.research.kar.reefer.actors.ReeferActor;

public interface ReeferState {
    //state = unallocated | allocated | spoilt | maintenance
    public static enum State {ALLOCATED, UNALLOCATED, INTRANSIT, SPOILT, MAINTENANCE};

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
    public ReeferActor.ReeferAllocationStatus getAllocationStatus();
    public void setAllocationStatus(ReeferActor.ReeferAllocationStatus allocationStatus);
    public int getRemainingCapacity();
    public void setRemainingCapacity(int remainingCapacity);
    public int getMaxCapacity();
    public void save();
}