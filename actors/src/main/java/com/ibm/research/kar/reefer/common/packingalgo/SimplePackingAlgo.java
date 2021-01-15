package com.ibm.research.kar.reefer.common.packingalgo;

import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;
import com.ibm.research.kar.reefer.common.ReeferState;

public class SimplePackingAlgo implements PackingAlgo {
    
    public int pack(ReeferState reeferState, int productQuantity, String voyageId) {
        int remainingCapacity = reeferState.getRemainingCapacity();
        int remainingProductQuantity = productQuantity;
        int maxCapacity = reeferState.getMaxCapacity();
        // Trivial, one dimensional packing based on product quantity and fixed reefer capacity.
        if ( remainingCapacity >= productQuantity ) {
            remainingCapacity -= productQuantity;
            // all products fit in this reefer, nothing to split
            remainingProductQuantity = 0;
            reeferState.setRemainingCapacity(remainingCapacity);
             double percentFull = ((double)(maxCapacity-remainingCapacity)/ maxCapacity) * 100;
            // if reefer is 60%+ full, mark it as ALLOCATED. No additional product will be placed there
            if ( percentFull >= ReeferAppConfig.CapacityThresholdFloor) {
                 reeferState.setAllocationStatus(ReeferAllocationStatus.ALLOCATED);
            } else {
                reeferState.setAllocationStatus(ReeferAllocationStatus.PARTIALLY_ALLOCATED);
            }
            System.out.println("SimplePackingAlgo.pack() - ReeferId:"+reeferState.getId()+" filled with "+productQuantity+" product units. Allocation status:"+reeferState.getAllocationStatus()+" Capacity(%):"+percentFull+" full");

        } else {
            // split product into multiple reefers. Fill current reefer to the max capacity
            // and add remaining product to the next reefer
            remainingProductQuantity = productQuantity - remainingCapacity;
            reeferState.setRemainingCapacity(0);
            reeferState.setAllocationStatus(ReeferAllocationStatus.ALLOCATED);
            System.out.println("SimplePackingAlgo.pack() - ReeferId:"+reeferState.getId()+" filled with "+remainingCapacity+" product units. Allocation status:"+reeferState.getAllocationStatus());
        }
        reeferState.setVoyageId(voyageId);

        return remainingProductQuantity;
    }
}