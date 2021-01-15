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

package com.ibm.research.kar.reefer;


public class ReeferAppConfig {
    //public static final String VoyageActor = "voyage";
    // size of each reefer in terms of product units. For
    // simplification, each product has the same size
    public static final int ReeferMaxCapacityValue = 1000;
    public static final int ReeferInventorySize=1000000;
    public static final String ReeferProvisionerId = "ReeferManager";

    public static final String ReeferActorName="reefer";
    public static final String ReeferProvisionerActorName="reefer-provisioner";
    public static final String VoyageActorName="voyage";
    public static final String OrderActorName="order";

    // If the reefer capacity is greater or equal 60, it is considered full (ie. ALLOCATED)
    public static final int CapacityThresholdFloor = 60;

    public static final String PackingAlgoStrategy = "simple";

}