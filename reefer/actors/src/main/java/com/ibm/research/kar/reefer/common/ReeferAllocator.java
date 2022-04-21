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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.AnomalyManagerActor;
import com.ibm.research.kar.reefer.common.ReeferState.State;
import com.ibm.research.kar.reefer.common.error.ReeferInventoryExhaustedException;
import com.ibm.research.kar.reefer.model.ReeferDTO;


import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class ReeferAllocator {
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(ReeferAllocator.class.getName());
    public static int howManyReefersNeeded(int productQuantity) {
        return Double.valueOf(Math.ceil(productQuantity/(double)ReeferAppConfig.ReeferMaxCapacityValue)).intValue();
    }
    public static List<Integer> allocateReefers( ReeferState.State[] reeferStateList, int productQuantity) {
        List<Integer>  reefers = new ArrayList<>();
        // simple calculation for how many reefers are needed for the order.
        int howManyReefersNeeded = Double.valueOf(Math.ceil(productQuantity/(double)ReeferAppConfig.ReeferMaxCapacityValue)).intValue();
        try {
            while(howManyReefersNeeded-- > 0 ) {
                int index = findInsertionIndexForReefer(reeferStateList);
                reefers.add(index);
            }
        } catch(ReeferInventoryExhaustedException e) {
            logger.log(Level.WARNING,"ReeferAllocator.allocateReefers()",e);
        }


        return reefers;
    }

    public static List<ReeferDTO> allocateReefers( ReeferDTO[] reeferInventory, int productQuantity, String orderId, String voyageId, int availableReeferCount) {
        List<ReeferDTO>  reefers = new ArrayList<>();
        // simple calculation for how many reefers are needed for the order. 
        int howManyReefersNeeded = Double.valueOf(Math.ceil(productQuantity/(double)ReeferAppConfig.ReeferMaxCapacityValue)).intValue();

        if ( howManyReefersNeeded > availableReeferCount ) {
            logger.log(Level.SEVERE,"ReeferAllocator.allocateReefers - not enough reefers in inventory to fill order "+orderId+
                    " - rejecting request for "+howManyReefersNeeded+" reefers since only " + availableReeferCount+" are available");
            return reefers;
        }
        try {
            StringBuilder sb = new StringBuilder();
            while(howManyReefersNeeded-- > 0 ) {
                int index = findInsertionIndexForReefer(reeferInventory);
                ReeferDTO reefer = reeferInventory[index];
                reefer.allocateToOrder(orderId, voyageId);
                reefers.add(reefer);
            }
        } catch(ReeferInventoryExhaustedException e) {
            logger.log(Level.WARNING,"ReeferAllocator.allocateReefers()",e);
        }
 

        return reefers;
    }
    private static int randomIndex(int inventorySize) {
        XoRoShiRo128PlusRandom xoroRandom = new XoRoShiRo128PlusRandom();
        return xoroRandom.nextInt(inventorySize);
    }
    
    private static int findInsertionIndexForReefer(ReeferState.State[] reeferStateList) throws ReeferInventoryExhaustedException{
        int index = randomIndex(reeferStateList.length);
        // max number of lookup steps before we jump (randomly)
        int maxSteps = 10;
        // if lookup hits unassigned spot, we've found an insertion index for a new reefer
        if ( reeferStateList[index].equals(State.UNALLOCATED) ) {
            return index;
        } else {
            // the random index hit an assigned spot in the list. Do maxSteps to find an unassigned spot
            for( int i = index; i < reeferStateList.length; i++) {
                if (  reeferStateList[index].equals(State.UNALLOCATED) ) {
                    return i;
                }
                // after maxSteps lookups an insertion index has not been found in the availableReefers list. Choose
                // another random index and try again.
                if ( (i-index) == maxSteps ) {
                    break;
                } 
            }
            return findInsertionIndexForReefer(reeferStateList);
        }
    }
    private static int findInsertionIndexForReefer(ReeferDTO[] reeferInventory) throws ReeferInventoryExhaustedException{
        int index = randomIndex(reeferInventory.length);
        // max number of lookup steps before we jump (randomly)
        int maxSteps = 10;
        // if lookup hits unassigned spot, we've found an insertion index for a new reefer
        if (  reeferInventory[index] != null && reeferInventory[index].getState().equals(State.UNALLOCATED) ) {
            return index;
        } else {
            // the random index hit an assigned spot in the list. Do maxSteps to find an unassigned spot
            for( int i = index; i < reeferInventory.length; i++) {
                if ( reeferInventory[i] != null && reeferInventory[i].getState().equals(State.UNALLOCATED) ) {
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
    }
}