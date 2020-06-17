package com.ibm.research.kar.reefer.common.packingalgo;

import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;
import com.ibm.research.kar.reefer.common.ReeferState;

public class SimplePackingAlgo implements PackingAlgo {
    
    public int pack(ReeferState reeferState, int productQuantity) {
        int remainingCapacity = reeferState.getRemainingCapacity();
        int remainingProductQuantity = productQuantity;
        int maxCapacity = reeferState.getMaxCapacity();
        // Trivial, one dimensional packing based on product quantity and fixed reefer capacity.
        //
        if ( remainingCapacity >= productQuantity ) {
            System.out.println("SimplePackingAlgo.pack() - ReeferId:"+reeferState.getId()+" filled with "+productQuantity+" product units");
            remainingCapacity -= productQuantity;
            // all products fit in this reefer, nothing to split
            remainingProductQuantity = 0;
            reeferState.setRemainingCapacity(remainingCapacity);
            //actorSetState(reefer, ReeferActor.ReeferAvailCapacityKey, Json.createValue(remainingCapacity));
            double percentFreeCapacity = ((double)remainingCapacity/ maxCapacity) * 100;
            // if reefer is 60%+ full, mark it as ALLOCATED. No additional product will be placed there
            if ( percentFreeCapacity >= ReeferAppConfig.CapacityThresholdFloor) {
               // actorSetState(reefer, ReeferActor.ReeferAllocationStatusKey, Json.createValue(ReeferAllocationStatus.ALLOCATED.name()));
                reeferState.setAllocationStatus(ReeferAllocationStatus.ALLOCATED);
            }
        } else {
            // split product into multiple reefers. Fill current reefer to the max capacity
            // and add remaining product to the next reefer
            remainingProductQuantity = productQuantity - remainingCapacity;
            System.out.println("SimplePackingAlgo.pack() - ReeferId:"+reeferState.getId()+" filled with "+remainingCapacity+" product units");
            //actorSetState(reefer, ReeferActor.ReeferAvailCapacityKey,Json.createValue(0));
            reeferState.setRemainingCapacity(0);
            reeferState.setAllocationStatus(ReeferAllocationStatus.ALLOCATED);
        }
        return remainingProductQuantity;
    }
}