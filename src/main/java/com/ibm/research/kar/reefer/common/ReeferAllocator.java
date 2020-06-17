package com.ibm.research.kar.reefer.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;
import com.ibm.research.kar.reefer.common.packingalgo.PackingAlgo;

public class ReeferAllocator {

    public static List<ActorRef> allocate( PackingAlgo packingStrategy, List<ActorRef> availableReefers, int productQuantity) {
        int remainingProductQuantity = productQuantity;

        List<ActorRef>  reefers = new ArrayList<>();
        System.out.println("ReeferAllocator.allocate() - Reefer Count:"+availableReefers.size());
        
        for( ActorRef reefer : availableReefers ) {
            try {
                ReeferState reeferState = new ReeferState(reefer);
                // skip full reefers
                if ( reeferState.getAllocationStatus().equals(ReeferAllocationStatus.ALLOCATED)) {
                    System.out.println("ReeferAllocator.allocate() - Reefer:"+reefer.getId()+" Already Allocated - Skipping");
                    continue;
                }
                remainingProductQuantity = packingStrategy.pack(reeferState, remainingProductQuantity);
                // save reefer state changes made in in the packing algo
                reeferState.save();
                reefers.add(reefer);
                // we are done if all products packed into reefers
                if ( remainingProductQuantity == 0 ) {
                    break;
                }
            } catch( Exception e) {
                e.printStackTrace();
                // this should result in rejected order due to reefer allocation problem
                return Collections.emptyList();

            }
        }
        return reefers;
    }
}