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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class ShippingScheduler {
    private static List<Route> routeList = new ArrayList<Route>();
    @Value("classpath:routes.json")
    private Resource routesJsonResource;
    private static final Logger logger = Logger.getLogger(ScheduleService.class.getName());
    private static Set<Voyage> sortedShipSchedule = new TreeSet<>();
    @Autowired
    private Routes routes;
    /**
     * Load voyages into memory from a config file
     *
     * @param routeConfigFile - voyage configuration file
     * @throws Exception
     */
    /*
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

     */

    /**
     *
     * @return - List of Routes
     * @throws Exception
     */
    public List<Route> getRoutes() throws Exception {
        if ( routeList.isEmpty() ) {
            System.out.println("ShippingSchedule.getRoutes() ------------------------------ calling generate");
            //this.initialize(routesJsonResource.getInputStream());
            routeList = routes.generate();
            //routeList.forEach(r -> System.out.println(">>>>"+r));
        }
        return routeList;
    }
    /**
     * Generate route/ship schedule for a given range of dates. Each ship sails from origin port
     * to a destination port, stays at the destination port for a number of days (unload/reload) and sails back to the
     * origin port. Each leg is a separate voyage which is added to the ship schedule which sorts on departure date.
     *
     * @param route - ship route
     * @param firstDepartureDate - date when the first voyage should depart
     * @param endDate - no voyages beyond this date
     * @param arrivedDateThreshold - voyage trimming date
     * @return - sorted route/ship schedule
     */
    public static Set<Voyage> generateShipSchedule(final Route route, final Instant firstDepartureDate, final Instant endDate, final Instant arrivedDateThreshold) {
        Instant arrivalDate;
        Instant departureDate = firstDepartureDate;
        Set<Voyage> schedule = new TreeSet<>();
        while(departureDate.isBefore(endDate) ) {
           // get the ship arrival date at destination port (departureDate+transitTime)
            arrivalDate = TimeUtils.getInstance().futureDate(departureDate, route.getDaysAtSea());
            // add voyage to a sorted schedule if its arrival date is after the arrivedDateThreshold
            addVoyageToSchedule(newScheduledVoyage(route, departureDate, route.getOriginPort(), route.getDestinationPort()), schedule, arrivedDateThreshold);
            // the ship returns back to origin port after it is unloaded and loaded up again
            departureDate = TimeUtils.getInstance().futureDate(arrivalDate, route.getDaysAtPort());
            if ( departureDate.isBefore(endDate) ) {
                // add return voyage to a sorted schedule - swap origin with destination port, the ship is going back
                addVoyageToSchedule(newScheduledVoyage(route, departureDate, route.getDestinationPort(), route.getOriginPort()), schedule, arrivedDateThreshold);
                // calculate departure date for next voyage from origin to destination
                departureDate = TimeUtils.getInstance().futureDate(departureDate,
                        route.getDaysAtSea() + route.getDaysAtPort());
            }
        }
        // subtract days at port. When generating new future schedule that would be added
        route.setLastArrival(departureDate);
        // return the last arrival date for this ship. Needed to generate future schedule when
        // we run out of voyages 
        return schedule;
    }
    /**
     * Adds a voyage to a given schedule. Ignore voyages that already arrived before a given date
     *
     * @param voyage - voyage to add
     * @param schedule - schedule to add voyage to
     * @param date - don't add voyage if its arrival date is before this threshold date
     */
    private static void addVoyageToSchedule(Voyage voyage, Set<Voyage> schedule, Instant date) {
        if (!voyage.shipArrived(date)) {
            // add voyage to a schedule which is sorted by departure date
            schedule.add(voyage);
        }
    }
    /**
     * Generate full schedule for a given range of dates [start,end]. It generates
     * schedule for each route/ship and adds it to a master schedule sorted by
     * departure date.
     *
     * @param firstDepartureDate - first voyage in the schedule departs on this date
     * @param lastVoyageDate - no voyages are added beyond this threshold
     * @param currentDate - today's date
     * @return sorted Set of Voyages
     */
    public static Set<Voyage> generateSchedule( final Instant firstDepartureDate, final Instant lastVoyageDate, final Instant currentDate) {
        TreeSet<Voyage> schedule = new TreeSet<>();
        int staggerInitialShipDepartures = 0;
        Instant departureDate = firstDepartureDate;
        // skip over voyages that arrived before this date
        Instant arrivedDateThreshold =
                currentDate.minus(ScheduleService.ARRIVED_THRESHOLD_IN_DAYS, ChronoUnit.DAYS);

        for (final Route route : routeList) {
            //System.out.println("::::"+route);
            // generate voyages for each route for a given range [firstDepartureDate, lastVoyageDate] and
            // add them to the schedule which sorts by departure date
            schedule.addAll(generateShipSchedule(route, departureDate, lastVoyageDate, arrivedDateThreshold));
             // initial ship departures staggered by 2 days (change this if necessary)
            staggerInitialShipDepartures += 2;
            // change departure date to today+stagger so that the ships don't depart on the same day
            departureDate = TimeUtils.getInstance().futureDate(firstDepartureDate, staggerInitialShipDepartures);
        }
        return schedule;
    }

    /**
     * Returns initialized instance of Voyage
     *
     * @param route - route the voyage belongs
     * @param departureDate - day of departure
     * @param originPort - port of departure
     * @param destinationPort - destination port
     * @return Voyage instance
     */
    private static Voyage newScheduledVoyage(final Route route, final Instant departureDate, String originPort, String destinationPort) {
        Instant arrivalDate = TimeUtils.getInstance().futureDate(departureDate, route.getDaysAtSea());
        return new Voyage(new Route(route.getVessel().clone(), originPort, destinationPort, route.getDaysAtSea(),
                route.getDaysAtPort()), departureDate, arrivalDate.toString());
    }


}