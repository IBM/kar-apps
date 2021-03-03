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

package com.ibm.research.kar.reeferserver.scheduler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import com.ibm.research.kar.reeferserver.service.ScheduleService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.FileCopyUtils;

@Component
public class ShippingScheduler {
    private static List<Route> routes = new ArrayList<Route>();
    @Value("classpath:routes.json")
    private Resource routesJsonResource;
    private static final Logger logger = Logger.getLogger(ScheduleService.class.getName());
    private static Set<Voyage> sortedShipSchedule = new TreeSet<>();
    @Value("classpath:ships.txt")
    private static Resource ships;
    /**
     * Load voyages into memory from a config file
     *
     * @param routeConfigFile - voyage configuration file
     * @throws Exception
     */
    public void initialize(final InputStream routeConfigFile) throws Exception {

        final ObjectMapper mapper = new ObjectMapper();
        final TypeReference<List<Route>> typeReference = new TypeReference<List<Route>>() {
        };

        try {
            routes = mapper.readValue(routeConfigFile, typeReference);
            if (logger.isLoggable(Level.INFO)) {
                routes.forEach(route -> logger.info("ShippingScheduler.initialize - Origin Port: " + route.getOriginPort()
                        + " Ship:" + route.getVessel().getName() + " Ship Capacity:" + route.getVessel().getMaxCapacity())
                );
            }
        } catch (IOException e) {
            logger.log(Level.WARNING,"",e);
        }

    }

    /**
     *
     * @return
     * @throws Exception
     */
    public List<Route> getRoutes() throws Exception {
        if ( routes.isEmpty() ) {
            this.initialize(routesJsonResource.getInputStream());
        }
        /*
        InputStream resource = new ClassPathResource(
                "ships.txt").getInputStream();
        try ( BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource)) ) {
            String ships = reader.
                    lines().
                    map(line -> line.split("\t")).
                    map(v -> "\n{\n\t\"ship\" : {\n\t\t\"name\":\""+v[1].replaceAll("\\s","-")+"\",\n\t\t\"capacity\":\""+v[6]+"\" \n\t} \n}").
                    collect(Collectors.joining(","));

            System.out.println(ships);

        } catch (IOException e) {
            e.printStackTrace();
        }

         */
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }

    /**
     *
     * @param route
     * @param departureDate
     * @param endDate
     * @return
     */
    public static Set<Voyage> generateShipSchedule(Route route, Instant departureDate, Instant endDate) {
        Instant arrivalDate;
        Set<Voyage> schedule = new TreeSet<>();
        while (departureDate.isBefore(endDate)) {
           // get the ship arrival date at destination port (departureDate+transitTime)
            arrivalDate = TimeUtils.getInstance().futureDate(departureDate, route.getDaysAtSea());
            // add voyage to a sorted (by departure date) schedule
             schedule.add(newScheduledVoyage(route, departureDate, false));
            // the ship returns back to origin port after it is unloaded and loaded up again
            departureDate = TimeUtils.getInstance().futureDate(arrivalDate, route.getDaysAtPort());
            // add return voyage to a sorted (by departure date) schedule
             schedule.add(newScheduledVoyage(route, departureDate, true));
            // calculate departure date for next voyage from origin to destination
            departureDate = TimeUtils.getInstance().futureDate(departureDate,
                    route.getDaysAtSea() + route.getDaysAtPort());
        }
        // subtract days at port. When generating new future schedule that would be added
        route.setLastArrival(departureDate);
        // return the last arrival date for this ship. Needed to generate future schedule when
        // we run out of voyages 
        return schedule;
    }

    /**
     *
     * @param departureDate
     * @return
     */
    public static Set<Voyage> generateSchedule(Instant departureDate, final Instant lastVoyageDate) {
        TreeSet<Voyage> schedule = new TreeSet<>();
        int staggerInitialShipDepartures = 0;
        for (final Route route : routes) {
            // generate voyages for each route for a given range [departureDate, lastVoyageDate] and
            // add them to the schedule which sorts by departure date
            schedule.addAll(generateShipSchedule(route, departureDate, lastVoyageDate));
             // initial ship departures staggered by 2 days (change this if necessary)
            staggerInitialShipDepartures += 2;
            // reset departure date to today+stagger (calculated above) so that the ships
            // dont depart on the same day
            departureDate = TimeUtils.getInstance().futureDate(Instant.now(), staggerInitialShipDepartures);
        }
        return schedule;
    }

    /**
     *
     * @param route
     * @param departureDate
     * @param returnVoyage
     * @return
     */
    private static Voyage newScheduledVoyage(final Route route, final Instant departureDate, final boolean returnVoyage) {
        Instant arrivalDate = TimeUtils.getInstance().futureDate(departureDate, route.getDaysAtSea());
        String originPort = route.getOriginPort();
        String destinationPort = route.getDestinationPort();

        if (returnVoyage) {
            // swap for return trip
            originPort = route.getDestinationPort();
            destinationPort = route.getOriginPort();
        }

        // for return voyage reverse origin and destination ports
        return new Voyage(new Route(route.getVessel().clone(), originPort, destinationPort, route.getDaysAtSea(),
                route.getDaysAtPort()), departureDate, arrivalDate.toString());
    }
 
}