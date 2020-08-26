package com.ibm.research.kar.reefer.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.ReeferActor.ReeferAllocationStatus;
import com.ibm.research.kar.reefer.common.error.ReeferInventoryExhaustedException;
import com.ibm.research.kar.reefer.common.packingalgo.PackingAlgo;
import com.ibm.research.kar.reefer.model.Reefer;
import com.ibm.research.kar.reefer.model.ReeferDTO;
import com.ibm.research.kar.reefer.model.ReeferDTO.State;

import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class ReeferAllocator {
/*
    public static List<ActorRef> allocate( PackingAlgo packingStrategy, List<ActorRef> availableReefers, int productQuantity, String voyageId) {
        int remainingProductQuantity = productQuantity;

        List<ActorRef>  reefers = new ArrayList<>();
        System.out.println("ReeferAllocator.allocate() - Reefer Count:"+availableReefers.size());
        
        for( ActorRef reefer : availableReefers ) {
            try {
                ReeferState reeferState = new ActorReeferState(reefer);
                // skip Allocated reefers and those partially allocated to another voyage
                if ( reeferState.alreadyAllocated() ||
                     reeferState.partiallyAllocatedToAnotherVoyage(voyageId)) {
                    System.out.println("ReeferAllocator.allocate() - Reefer:"+
                    reefer.getId()+
                    " not avaialable. Allocated:"
                    +reeferState.alreadyAllocated()
                    +" Allocated to another voyage:"+reeferState.partiallyAllocatedToAnotherVoyage(voyageId)
                    +"- Skipping");
                    continue;
                }
                remainingProductQuantity = packingStrategy.pack(reeferState, remainingProductQuantity, voyageId);
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
    */

    public static List<ReeferDTO> allocateReefers( ReeferDTO[] reeferInventory, int productQuantity, String orderId, String voyageId) {
        List<ReeferDTO>  reefers = new ArrayList<>();
        // simple calculation for how many reefers are needed for the order. 
        int howManyReefersNeeded = Double.valueOf(Math.ceil(productQuantity/(double)ReeferAppConfig.ReeferMaxCapacityValue)).intValue();
        try {
            while(howManyReefersNeeded-- > 0 ) {
                int index = findInsertionIndexForReefer(reeferInventory);
                ReeferDTO reefer = new ReeferDTO(index, State.ALLOCATED, orderId, voyageId);
                reeferInventory[index] = reefer;
                reefers.add(reefer);
                System.out.println("+++++++++++++++++++++ ReeferId:"+index+" Added to order:"+orderId);
            }
        } catch(ReeferInventoryExhaustedException e) {

            System.out.println("+++++++++++++++++++++ Reefer Inventory Size:"+reeferInventory.length);
            e.printStackTrace();
        }
 

        return reefers;
    }
    private static int randomIndex(int inventorySize) {
        XoRoShiRo128PlusRandom xoroRandom = new XoRoShiRo128PlusRandom();
        return xoroRandom.nextInt(inventorySize);
    }
    
    private static int findInsertionIndexForReefer(ReeferDTO[] reeferInventory) throws ReeferInventoryExhaustedException{
        int index = randomIndex(reeferInventory.length);
        // max number of lookup steps before we jump (randomly)
        int maxSteps = 3;
        // if lookup hits unassigned spot, we've found an insertion index for a new reefer
        if ( reeferInventory[index] == null ) {
            return index;
        } else {
            // the random index hit an assigned spot in the list. Do maxSteps to find an unassigned spot
            for( int i = index; i < reeferInventory.length; i++) {

                if ( reeferInventory[index] == null ) {
                    return i;
                }
                // after maxSteps lookups an insertion index has not been found in the availableReefers list. Choose
                // another random index and try again.
                if ( (i-index) == maxSteps ) {
                    break;
                } 
            }
            return findInsertionIndexForReefer(reeferInventory);
        }
      // throw new ReeferInventoryExhaustedException();  
    }

    /*
    This method is temporary until Reefer support is added
   
    public static List<ReeferState> allocateReefers( PackingAlgo packingStrategy, List<JsonValue> availableReefers, int productQuantity, String voyageId) {
        int remainingProductQuantity = productQuantity;
        List<ReeferState>  reefers = new ArrayList<>();
        System.out.println("ReeferAllocator.allocate() - Reefer Count:"+availableReefers.size());
        
        for( JsonValue reefer : availableReefers ) {
            try {
                ReeferState reeferState = new SimpleReeferState(reefer);
                // skip Allocated reefers and those partially allocated to another voyage
                if ( reeferState.alreadyAllocated() ||
                     reeferState.partiallyAllocatedToAnotherVoyage(voyageId)) {
                    System.out.println("ReeferAllocator.allocate() - Reefer:"+
                    reeferState.getId()+
                    " not avaialable. Allocated:"
                    +reeferState.alreadyAllocated()
                    +" Allocated to another voyage:"+reeferState.partiallyAllocatedToAnotherVoyage(voyageId)
                    +"- Skipping");
                    continue;
                }
                remainingProductQuantity = packingStrategy.pack(reeferState, remainingProductQuantity, voyageId);

                reefers.add(reeferState);
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
     */
}