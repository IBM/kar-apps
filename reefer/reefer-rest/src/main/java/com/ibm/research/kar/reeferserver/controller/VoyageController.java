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
import com.ibm.research.kar.reefer.common.error.VoyageNotFoundException;
import com.ibm.research.kar.reefer.common.json.RouteJsonSerializer;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.model.ShippingSchedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import javax.json.*;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
public class VoyageController {
    @Autowired
    private GuiController gui;
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(VoyageController.class.getName());
    private ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);

    /**
     * Returns voyages matching given originPort, destinationPort and departure date
     *
     * @param message - json encoded message with query params
     * @return - list of voyages matching the query
     */

    @PostMapping("/voyage/matching")
    public List<Voyage> getMatchingVoyages(@RequestBody String message) {
        String originPort = "";
        String destinationPort = "";
        Instant date = null;

        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            JsonObject req = jsonReader.readObject();
            originPort = req.getString("origin");
            destinationPort = req.getString("destination");
            String departureDate = req.getString("departureDate");
            job.add("origin", req.getString("origin")).
                    add("destination", req.getString("destination")).
                    add("departureDate", req.getString("departureDate"));
            date = Instant.parse(departureDate);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("VoyageController.getMatchingVoyages() - origin:" + originPort + " destination:"
                        + destinationPort + " date:" + departureDate);
            }
            JsonValue reply = Kar.Actors.call(scheduleActor, "matchingVoyages", job.build());
            return getVoyages(reply);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw e;
        }
    }


    /**
     * Returns voyages which are in-transit within a given date range
     *
     * @param message - json encoded query params
     * @return - list of voyages
     */

    @PostMapping("/voyage/inrange")
    public List<Voyage> getVoyagesInRange(@RequestBody String message) {

        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            JsonObject req = jsonReader.readObject();
            job.add("startDate", req.getString("startDate")).add("endDate", req.getString("endDate"));
            JsonValue reply = Kar.Actors.call(scheduleActor, "voyagesInRange", job.build());

            JsonArray ja = reply.asJsonArray();
            return getVoyages(reply);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            return new ArrayList<Voyage>();
        }

    }


    private List<Voyage> getVoyages(JsonValue jv) {
        JsonArray ja = jv.asJsonArray();
        return ja.stream().map(v -> v.asJsonObject()).
                map(VoyageJsonSerializer::deserialize).
                collect(Collectors.toList());
    }

    /**
     * Returns a list of active voyages which are currently at sea
     *
     * @return - list of voyages
     */
    @GetMapping("/voyage/active")
    public List<Voyage> getActiveVoyages() {
        try {
            JsonValue reply = Kar.Actors.call(scheduleActor, "activeVoyages");
            JsonArray ja = reply.asJsonArray();
            return ja.stream().map(v -> v.asJsonObject()).
                    map(VoyageJsonSerializer::deserialize).
                    collect(Collectors.toList());
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw e;
        }

    }


    @GetMapping("/voyage/state/{id}")
    public Voyage getVoyageState(@PathVariable("id") String id) throws VoyageNotFoundException {
        try {
            JsonValue reply = Kar.Actors.call(scheduleActor, "voyageState");
            return VoyageJsonSerializer.deserialize(reply.asJsonObject());
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/voyage/info/{id}")
    public Voyage getVoyage(@PathVariable("id") String id) throws VoyageNotFoundException {
        try {
            JsonValue reply = Kar.Actors.call(scheduleActor, "voyage", Json.createValue(id));
            return VoyageJsonSerializer.deserialize(reply.asJsonObject());
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Returns all routes
     *
     * @return - list of routes
     */

    @GetMapping("/voyage/routes")
    public List<Route> getRoutes() {
        try {
            JsonValue reply = Kar.Actors.call(scheduleActor, "routes");
            JsonArray ja = reply.asJsonArray();
            List<Route> routes =
                    ja.stream().map(jv -> jv.asJsonObject()).map(RouteJsonSerializer::deserialize).collect(Collectors.toList());
            return routes;
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            throw e;
        }

    }

    private ShippingSchedule shippingSchedule() {
        JsonValue reply = Kar.Actors.call(scheduleActor, "activeSchedule");
        String currentDate = reply.asJsonObject().getString(Constants.CURRENT_DATE_KEY);

        JsonArray ja = reply.asJsonObject().getJsonArray(Constants.ACTIVE_VOYAGES_KEY);

        List<Voyage> voyages = ja.stream().map(v -> v.asJsonObject()).
                map(VoyageJsonSerializer::deserialize).
                collect(Collectors.toList());
        voyages.sort(Comparator.comparing(v -> v.getRoute().getVessel().getName()));
        return new ShippingSchedule(voyages, currentDate);
    }

    public void updateGuiSchedule() {
        try {
            ShippingSchedule schedule = shippingSchedule();
            gui.sendActiveVoyageUpdate(schedule);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void scheduleGuiUpdate() {
        updateGuiSchedule();
    }

}
