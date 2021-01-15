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

package com.ibm.research.kar.reeferserver.controller;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.reeferserver.service.SimulatorService;
import com.ibm.research.kar.reeferserver.service.VoyageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class TimeController {
    @Autowired
    private VoyageService voyageService;
    @Autowired
    private ScheduleService schduleService;

    private static final Logger logger = Logger.getLogger(TimeController.class.getName());
    @PostMapping("/time/startDate")
    public Instant getStartDate() {
        return TimeUtils.getInstance().getStartDate();
    }

    @PostMapping("/time/currentDate")
    public Instant getCurrentDate() {
        return TimeUtils.getInstance().getCurrentDate();
    }

    /*
     * Called by the GUI to advance time while in manual mode.
     * 
     */
    @PostMapping("/time/nextDay")
    public Instant nextDay() {
        voyageService.nextDay();
        String date = "";
        try {
            date = TimeUtils.getInstance().getCurrentDate().toString().substring(0, 10);
            System.out.println("nextDay() - Returning Date >>>>>>>" + date);
            JsonObject message = Json.createObjectBuilder()
                    .add(Constants.DATE_KEY, Json.createValue(TimeUtils.getInstance().getCurrentDate().toString()))
                    .build();
            Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "releaseReefersfromMaintenance", message);
        } catch (Exception e) {
            logger.log(Level.WARNING,"",e);
        }

        return TimeUtils.getInstance().getCurrentDate();
    }

    @PostMapping("/time/advance")
    public Instant advance() {
        Instant time = TimeUtils.getInstance().advanceDate(1);
        if ( logger.isLoggable(Level.INFO)) {
            logger.info("TimeController.advance() ***************************************** NEXT DAY "
                    + time.toString() + " ***************************************************************");
        }
        try {
            // On a day change generate a future schedule if necessary. The new schedule is generated if
            // we reached a configured threshold of days before the end of current schedule.
            schduleService.generateNextSchedule(time);
            JsonObject message = Json.createObjectBuilder().add(Constants.DATE_KEY, Json.createValue(time.toString()))
                    .build();
            // Reefers on maintenance are freed automatically after a configurable number of days passes.
            Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "releaseReefersfromMaintenance", message);
        } catch (Exception e) {
            logger.log(Level.WARNING,"",e);
        }

        return time;
    }

}