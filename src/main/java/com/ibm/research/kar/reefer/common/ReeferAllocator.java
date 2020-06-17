package com.ibm.research.kar.reefer.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonString;
import javax.json.JsonValue;
import static com.ibm.research.kar.Kar.*;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.ReeferActor;
import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;

public class ReeferAllocator {
    public static List<ActorRef> allocate( List<ActorRef> availableReefers, int productQuantity) {
        int remainingCapacity;
        List<ActorRef>  reefers = new ArrayList<>();
        System.out.println("ReeferAllocator.allocate() - Reefer Count:"+availableReefers.size());
        try {
            for( ActorRef reefer : availableReefers ) {
                System.out.println("Retrieving Reefer Available Capacity");
                
                Map<String, JsonValue> reeferState = actorGetAllState(reefer); //, ReeferActor.ReeferAvailCapacityKey);
                String allocation = ((JsonString)reeferState.get(ReeferActor.ReeferAllocationStatusKey)).getString();
                 ReeferAllocationStatus reeferAllocation = 
                    ReeferAllocationStatus.valueOf(allocation);
                
                System.out.println("Retrieved Reefer Available Capacity "+reeferState.get(ReeferActor.ReeferAvailCapacityKey)+ " ReeferAllocation:"+reeferAllocation);
                // skip full reefers
                if ( reeferAllocation.equals(ReeferAllocationStatus.ALLOCATED)) {
                    continue;
                }
                remainingCapacity = ((JsonNumber)reeferState.get(ReeferActor.ReeferAvailCapacityKey)).intValue();
                int maxCapacity = ((JsonNumber)reeferState.get(ReeferActor.ReeferMaxCapacityKey)).intValue();
                   
                 // 
                if ( remainingCapacity >= productQuantity ) {
                    reefers.add(reefer);
                    remainingCapacity -= productQuantity;
                    actorSetState(reefer, ReeferActor.ReeferAvailCapacityKey, Json.createValue(remainingCapacity));
                    double percentFreeCapacity = ((double)remainingCapacity/ maxCapacity) * 100;
                    // if reefer is 60%+ full, mark it as ALLOCATED. No additional product will be placed there
                    if ( percentFreeCapacity >= ReeferAppConfig.capacityThresholdFloor) {
                       // actorSetState(reefer, ReeferActor.ReeferAllocationStatusKey, Json.createValue(ReeferAllocationStatus.ALLOCATED.name()));
                        changeReeferAllocationState(reefer,ReeferAllocationStatus.ALLOCATED);
                    }

                    break;  // product quantity fits in the current reefer
                } else {
                    // split product into multiple reefers. Fill current reefer to max 
                    // capacity and add remaining product to the next reefer
                    productQuantity -= remainingCapacity;
                    reefers.add(reefer);
                    actorSetState(reefer, ReeferActor.ReeferAvailCapacityKey,Json.createValue(0));
                    changeReeferAllocationState(reefer,ReeferAllocationStatus.ALLOCATED);
                }
            }
    
        } catch( Exception e) {
            e.printStackTrace();
        }
         return reefers;
    }
    private static void changeReeferAllocationState(ActorRef reefer, ReeferAllocationStatus allocationStatus) {
        actorSetState(reefer, ReeferActor.ReeferAllocationStatusKey, Json.createValue(allocationStatus.name()));
    }
}