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
        reeferState = new HashMap<>(Actors.State.getAll(reefer));
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
        Actors.State.set(reefer, reeferState);
    }
}