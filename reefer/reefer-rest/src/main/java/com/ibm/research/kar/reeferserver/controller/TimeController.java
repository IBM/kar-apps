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
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@CrossOrigin("*")
public class TimeController {
    @Autowired
    private SimpMessagingTemplate template;
    private ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(TimeController.class.getName());

    @PostMapping("/time/startDate")
    public Instant getStartDate() {
        JsonValue reply = Kar.Actors.rootCall(scheduleActor, "startDate");
        return Instant.parse(((JsonString) reply).getString());
    }

    @PostMapping("/time/currentDate")
    public Instant getCurrentDate() {
        JsonValue reply = Kar.Actors.rootCall(scheduleActor, "currentDate");
        return Instant.parse(((JsonString) reply).getString());
    }

    @PostMapping("/time/tomorrowsDate")
    public Instant getTomorrowsDate() {
        try {
            JsonValue reply = Kar.Actors.rootCall(scheduleActor, "tomorrowsDate");
            return Instant.parse(((JsonString) reply).getString());
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
        Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/advancetime", JsonValue.NULL);
        JsonValue respValue = response.readEntity(JsonValue.class);
        if (logger.isLoggable(Level.INFO)) {
            logger.info("TimeController.nextDay() -------------------------------- simulator reply:" + respValue);
        }


        JsonValue reply = Kar.Actors.rootCall(scheduleActor, "currentDate");
        return Instant.parse(((JsonString) reply).getString());
    }

    @PostMapping("/time/advance")
    public Instant advance() {
        Instant today = null;

        try {
            JsonValue reply = Kar.Actors.rootCall(scheduleActor, "advanceDate");
            today = Instant.parse(reply.asJsonObject().getString(Constants.CURRENT_DATE_KEY).toString());
            template.convertAndSend("/topic/time",today.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
            e.printStackTrace();
        }

        return today;
    }
}
