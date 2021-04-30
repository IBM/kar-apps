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

import com.ibm.research.kar.reefer.model.Route;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FleetCapacity {
    private static final Logger logger = Logger.getLogger(FleetCapacity.class.getName());
    public static int totalSize(List<Route> routes) {
        Set<String> fleet = new LinkedHashSet<>();
        // using each ship capacity compute the total fleet reefer inventory size
        long fleetMaxCapacity = 0;
        for (Route route : routes) {
            fleetMaxCapacity += route.getVessel().getMaxCapacity();
            fleet.add(route.getVessel().getName());
        }
        if ( logger.isLoggable(Level.INFO)) {
            logger.info("RestController.init() - Fleet Size:"+fleet.size()+" Max Fleet Capacity:"+fleetMaxCapacity);
        }
        // increase total by additional 30% to ensure we always have reefers available
        return Long.valueOf(fleetMaxCapacity * 3).intValue();
        //return Double.valueOf(fleet.size() * fleetMaxCapacity * 0.3).intValue();
    }
}
