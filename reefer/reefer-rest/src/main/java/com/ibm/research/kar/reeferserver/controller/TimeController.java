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

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.reeferserver.service.TimeService;
import com.ibm.research.kar.reeferserver.service.VoyageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@CrossOrigin("*")
public class TimeController {
    @Autowired
    private VoyageService voyageService;
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private TimeService timeService;
    private  ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorName, ReeferAppConfig.ScheduleManagerId);
    private static final Logger logger = Logger.getLogger(TimeController.class.getName());

    @PostMapping("/time/startDate")
    public Instant getStartDate() {
        JsonValue reply = Kar.Actors.call(scheduleActor, "startDate");
        return Instant.parse( ((JsonString)reply).getString());
        //return TimeUtils.getInstance().getStartDate();
    }

    @PostMapping("/time/currentDate")
    public Instant getCurrentDate() {
        JsonValue reply = Kar.Actors.call(scheduleActor, "currentDate");
        return Instant.parse( ((JsonString)reply).getString());
        //return TimeUtils.getInstance().getCurrentDate();
    }

    @PostMapping("/time/tomorrowsDate")
    public Instant getTomorrowsDate() {
        try {
            JsonValue reply = Kar.Actors.call(scheduleActor, "tomorrowsDate");
            return Instant.parse(((JsonString) reply).getString());
            //   Instant currentDate = TimeUtils.getInstance().getCurrentDate();
            //   Instant t = currentDate.plus(1, ChronoUnit.DAYS);
            //   return t;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
    /*
     * Called by the GUI to advance time while in manual mode.
     *
     */
    @PostMapping("/time/nextDay")
    public Instant nextDay() {
      //  voyageService.nextDay();
        Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/advancetime", JsonValue.NULL);
        JsonValue respValue = response.readEntity(JsonValue.class);
        if ( logger.isLoggable(Level.INFO)) {
            logger.info("TimeController.nextDay() -------------------------------- simulator reply:"+respValue);
        }


        JsonValue reply = Kar.Actors.call(scheduleActor, "currentDate");
        return Instant.parse(((JsonString) reply).getString());
 //       Instant currentDate = getCurrentDate();
 //       System.out.println("TimeController.nextDay() - new date:" + currentDate);
   //     try {
            /*
            JsonObject message = Json.createObjectBuilder()
                    .add(Constants.DATE_KEY, Json.createValue(TimeUtils.getInstance().getCurrentDate().toString()))
                    .build();

             */
            /*
            JsonObject message = Json.createObjectBuilder()
                    .add(Constants.DATE_KEY, Json.createValue(currentDate.toString()))
                    .build();
            messageReeferProvisioner("releaseReefersfromMaintenance", message);

             */
  //      } catch (Exception e) {
 //           logger.log(Level.WARNING, "", e);
 //           e.printStackTrace();
  //      }

       //return currentDate; //TimeUtils.getInstance().getCurrentDate();
    }

    @PostMapping("/time/advance")
    public Instant advance() {
        Instant today = null;
        // Advance date by one day
        //Instant today = TimeUtils.getInstance().advanceDate(1);
        try {

            JsonValue reply = Kar.Actors.call(scheduleActor, "advanceDate");
            System.out.println("TimeController.advance() - new date:" + reply);
            today = Instant.parse(reply.asJsonObject().getString(Constants.CURRENT_DATE_KEY).toString());
            System.out.println("TimeController.advance() - new date:" + reply + " today:" + today);
  /*
            // persist current date
            timeService.saveDate(today, Constants.CURRENT_DATE_KEY);

            if (logger.isLoggable(Level.INFO)) {
                logger.info("TimeController.advance() ***************************************** NEXT DAY "
                        + today.toString() + " ***************************************************************");
            }

            Instant lastDepartureDate = scheduleService.lastVoyageDepartureDate();
            long daysBetween = TimeUtils.getInstance().getDaysBetween(today, lastDepartureDate);
            if (daysBetween < scheduleService.THRESHOLD_IN_DAYS) {
                // On a day change generate a future schedule if necessary. The new schedule is generated if
                // we reached a configured threshold of days before the end of current schedule.
                scheduleService.extendSchedule(lastDepartureDate, today);
                // System.out.println("TimeController.advance() >>>>>>>>>>>>>>> extended schedule");
            } else {
                //  System.out.println("TimeController.advance() >>>>>>>>>>>>>>> extending schedule in "+daysBetween+" days");
            }
            JsonObject message = Json.createObjectBuilder().add(Constants.DATE_KEY, Json.createValue(today.toString()))
                    .build();
            messageReeferProvisioner("releaseReefersfromMaintenance", message);

   */
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
            e.printStackTrace();
        }

        return today;
    }

    private void messageReeferProvisioner(String method, JsonObject message) {
        try {
            // Reefers on maintenance are freed automatically after a configurable number of days passes.
            Kar.Actors.tell(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    method, message);

        } catch (Exception e) {
            logger.warning("TimeController.messageReeferProvisioner() - ReeferProvisioner." + method + "() call failed - reason:" + e.getMessage());
        }

    }

}