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
import com.ibm.research.kar.reefer.common.error.ShipCapacityExceeded;
import com.ibm.research.kar.reefer.common.json.JsonUtils;
import com.ibm.research.kar.reefer.common.json.RouteJsonSerializer;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reeferserver.model.ShippingSchedule;
import com.ibm.research.kar.reeferserver.service.OrderService;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.reeferserver.service.VoyageService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private ScheduleService shipScheduleService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private VoyageService voyageService;
    @Autowired
    private GuiController gui;
    private static final Logger logger = Logger.getLogger(VoyageController.class.getName());
    private ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorName, ReeferAppConfig.ScheduleManagerId);
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
                    add("departureDate",req.getString("departureDate") );
            date = Instant.parse(departureDate);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("VoyageController.getMatchingVoyages() - origin:" + originPort + " destination:"
                        + destinationPort + " date:" + departureDate);
            }
            JsonValue reply = Kar.Actors.call(scheduleActor, "matchingVoyages", job.build());
            return getVoyages(reply);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
        }
        return shipScheduleService.getMatchingSchedule(originPort, destinationPort, date);
    }

    /**
     * Returns voyages which are in-transit within a given date range
     *
     * @param message - json encoded query params
     * @return - list of voyages
     */
    @PostMapping("/voyage/inrange")
    public List<Voyage> getVoyagesInRange(@RequestBody String message) {
        Instant startDate;
        Instant endDate;

        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            JsonObject req = jsonReader.readObject();
            job.add("startDate", req.getString("startDate")).add("endDate",req.getString("endDate"));
            JsonValue reply = Kar.Actors.call(scheduleActor, "voyagesInRange", job.build());
/*
            JsonObject req = jsonReader.readObject();
            startDate = Instant.parse(req.getString("startDate"));
            endDate = Instant.parse(req.getString("endDate"));
            if (logger.isLoggable(Level.INFO)) {
                logger.info("VoyageController.getVoyagesInRange() - startDate:" + startDate.toString() + " endDate:"
                        + endDate.toString());
            }

 */
            JsonArray ja = reply.asJsonArray();
            return getVoyages(reply);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
            return new ArrayList<Voyage>();
        }
      //  return shipScheduleService.getMatchingSchedule(startDate, endDate);
    }
    private List<Voyage> getVoyages(JsonValue jv) {
        JsonArray ja = jv.asJsonArray();
        return ja.stream().map(v -> v.asJsonObject()).
                map(VoyageJsonSerializer::deserialize).
               // peek(System.out::println).
                collect(Collectors.toList());
    }
    /**
     * Returns a list of active voyages which are currently at sea
     *
     * @return - list of voyages
     */
    @GetMapping("/voyage/active")
    public List<Voyage> getActiveVoyages() {
        System.out.println("VoyageController.getActiveVoyages() called --------------------");
        try {
           // JsonValue reply = Kar.Actors.call(scheduleActor, "activeVoyages");
          //  return getVoyages(reply);
            return activeVoyages();
        } catch ( Exception e) {
            e.printStackTrace();
            throw e;
        }

       // return shipScheduleService.getActiveSchedule();
    }

    @GetMapping("/voyage/state/{id}")
    public Voyage getVoyageState(@PathVariable("id") String id) throws VoyageNotFoundException {
        try {
            JsonValue reply = Kar.Actors.call(scheduleActor, "voyageState");
            return VoyageJsonSerializer.deserialize(reply.asJsonObject());
            //return shipScheduleService.getVoyage(id);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @GetMapping("/voyage/info/{id}")
    public Voyage getVoyage(@PathVariable("id") String id) throws VoyageNotFoundException {
        try {
        JsonValue reply = Kar.Actors.call(scheduleActor, "voyage", Json.createValue(id));
        return VoyageJsonSerializer.deserialize(reply.asJsonObject());
        //return shipScheduleService.getVoyage(id);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Update voyage as arrived.
     *
     * @param message - json encoded params: daysAtSea, voyageId
     * @throws VoyageNotFoundException
     */
    @PostMapping("/voyage/update/arrived")
    public void arrived(@RequestBody String message) {
        try {
            String voyageId = JsonUtils.getVoyageId(message);
            List<String> voyageOrderList = JsonUtils.getVoyageOrders(message);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("VoyageController.delivered() - id:" + voyageId + " message:" + message);
            }
            shipScheduleService.updateDaysAtSea(voyageId, JsonUtils.getDaysAtSea(message));
            voyageService.voyageEnded(voyageId);
            orderService.updateOrderStatus(voyageId, OrderStatus.DELIVERED, voyageOrderList);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
        }

    }

    /**
     * Update voyage as in-transit to the destination.
     *
     * @param message - json encoded params: daysAtSea and voyageId
     * @throws VoyageNotFoundException
     */
    @PostMapping("/voyage/update/departed")
    public void departed(@RequestBody String message) {
        try {
            String voyageId = JsonUtils.getVoyageId(message);
            List<String> voyageOrderList = JsonUtils.getVoyageOrders(message);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("VoyageController.departed() - id:" + voyageId + " message:" + message);
            }
            shipScheduleService.updateDaysAtSea(voyageId, JsonUtils.getDaysAtSea(message));
            voyageService.voyageDeparted(voyageId);
            orderService.updateOrderStatus(voyageId, OrderStatus.INTRANSIT, voyageOrderList);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
        }

    }

    /**
     * Updates ship current position
     *
     * @param message - json encoded params: daysAtSea, voyageId
     * @throws VoyageNotFoundException
     */
    @PostMapping("/voyage/update/position")
    public void updateShipPosition(@RequestBody String message) {
        try {
            String voyageId = JsonUtils.getVoyageId(message);
            int daysAtSea = JsonUtils.getDaysAtSea(message);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("VoyageController.updateShipPosition() voyageId=" + voyageId + " Voyage Status:"
                        + voyageService.getVoyageStatus(voyageId) + " daysAtSea: " + daysAtSea);
            }
            if (daysAtSea > 0) {
                shipScheduleService.updateDaysAtSea(voyageId, daysAtSea);

            }
            updateGuiSchedule(TimeUtils.getInstance().getCurrentDate().toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
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
            System.out.println("VoyageController.getRoutes() --------------------------"+ reply);
            JsonArray ja = reply.asJsonArray();
            List<Route> routes =
                    ja.stream().map(jv -> jv.asJsonObject()).map(RouteJsonSerializer::deserialize).collect(Collectors.toList());
            //return shipScheduleService.getRoutes();
            return routes;
        } catch( Exception e) {
            e.printStackTrace();
            throw e;
        }

    }

    private void updateSimulator(String voyageId, int freeCapacity) {
        JsonObject params = Json.createObjectBuilder().add("voyageId", voyageId).add("freeCapacity", freeCapacity).build();
        try {
            Kar.Services.post("simservice", "/simulator/updatevoyagecapacity", params);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }

    }

    /**
     * Returns a list of voyages currently at sea
     *
     * @return
     */
    private List<Voyage> activeVoyages() {
        JsonValue reply = Kar.Actors.call(scheduleActor, "activeVoyages");
        JsonArray ja = reply.asJsonArray();
        return ja.stream().map(v -> v.asJsonObject()).
                map(VoyageJsonSerializer::deserialize).
                //peek(System.out::println).
                collect(Collectors.toList());
    }

    private ShippingSchedule shippingSchedule() {
        JsonValue reply = Kar.Actors.call(scheduleActor, "activeSchedule");
        String currentDate = reply.asJsonObject().getString(Constants.CURRENT_DATE_KEY);
        StringBuilder sb = new StringBuilder("Active Schedule:::");
        JsonArray ja = reply.asJsonObject().getJsonArray(Constants.ACTIVE_VOYAGES_KEY);
        List<Voyage> voyages = ja.stream().map(v -> v.asJsonObject()).
                map(VoyageJsonSerializer::deserialize).
                peek(voyage -> sb.append(voyage.toString())).
                collect(Collectors.toList());
        voyages.sort(Comparator.comparing(v -> v.getRoute().getVessel().getName()));
        return new ShippingSchedule(voyages, currentDate);
    }
    /**
     * Update GUI order counts
     */

    @PostMapping("/voyage/updateGui")
    public void updateGui(@RequestBody String currentDate) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("VoyageController.updateGui() - updating GUI with active schedule - currentDate:" + currentDate);
        }
        try {
           // gui.sendActiveVoyageUpdate(shipScheduleService.getActiveSchedule(), currentDate);
            OrderStats stats = orderService.getOrderStats();
            gui.updateOrderCounts(stats);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }
    public void updateGuiSchedule(String currentDate) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("VoyageController.updateSchedule() - updating GUI with active schedule - currentDate:" + currentDate);
        }
        try {
            ShippingSchedule schedule = shippingSchedule();
            //List<Voyage> activeSchedule = activeVoyages(); //shipScheduleService.getActiveSchedule();
            //activeSchedule.sort(Comparator.comparing(v -> v.getRoute().getVessel().getName()));
            gui.sendActiveVoyageUpdate(schedule); //activeSchedule, currentDate);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }
}