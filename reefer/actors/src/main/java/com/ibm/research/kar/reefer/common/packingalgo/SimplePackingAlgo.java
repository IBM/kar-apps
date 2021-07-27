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

package com.ibm.research.kar.reefer.common.packingalgo;

import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.ReeferActor;
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
                 reeferState.setAllocationStatus(ReeferActor.ReeferAllocationStatus.ALLOCATED);
            } else {
                reeferState.setAllocationStatus(ReeferActor.ReeferAllocationStatus.PARTIALLY_ALLOCATED);
            }
            System.out.println("SimplePackingAlgo.pack() - ReeferId:"+reeferState.getId()+" filled with "+productQuantity+" product units. Allocation status:"+reeferState.getAllocationStatus()+" Capacity(%):"+percentFull+" full");

        } else {
            // split product into multiple reefers. Fill current reefer to the max capacity
            // and add remaining product to the next reefer
            remainingProductQuantity = productQuantity - remainingCapacity;
            reeferState.setRemainingCapacity(0);
            reeferState.setAllocationStatus(ReeferActor.ReeferAllocationStatus.ALLOCATED);
            System.out.println("SimplePackingAlgo.pack() - ReeferId:"+reeferState.getId()+" filled with "+remainingCapacity+" product units. Allocation status:"+reeferState.getAllocationStatus());
        }
        reeferState.setVoyageId(voyageId);

        return remainingProductQuantity;
    }
}