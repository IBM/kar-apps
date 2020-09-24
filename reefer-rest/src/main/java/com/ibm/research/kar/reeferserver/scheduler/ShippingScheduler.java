package com.ibm.research.kar.reeferserver.scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;

import java.util.LinkedList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;

@Component
public class ShippingScheduler {
    private List<Route> routes = new ArrayList<Route>();

    public void initialize(final InputStream routeConfigFile) throws Exception {

        final ObjectMapper mapper = new ObjectMapper();
        final TypeReference<List<Route>> typeReference = new TypeReference<List<Route>>() {
        };

        try {
            routes = mapper.readValue(routeConfigFile, typeReference);
            routes.forEach(route -> System.out.println("................Origin Port: " + route.getOriginPort()
                    + " Ship:" + route.getVessel().getName() + " Ship Capacity:" + route.getVessel().getMaxCapacity())

            );
        } catch (final IOException e) {
            System.out.println("Unable to save users: " + e.getMessage());
        }

    }

    public List<Route> getRoutes(final InputStream routeConfigFile) throws Exception {
        if (routes.isEmpty()) {
            this.initialize(routeConfigFile);
        }
        return routes;
    }

    public LinkedList<Voyage> generateSchedule() {
        // generate new schedule for one year ahead begining from next day
        return generateSchedule(TimeUtils.getInstance().getCurrentDate()); // .
    }

    public LinkedList<Voyage> generateSchedule(Instant departureDate) {
        Instant arrivalDate;

         // the shipping schedule is generated for one year from now
        final Instant yearFromNow = TimeUtils.getInstance().getDateYearFrom(departureDate);
        int staggerInitialShipDepartures = 0;
        LinkedList<Voyage> sortedSchedule = new LinkedList<>();
        for (final Route route : routes) {
             // generate current ship schedule for the whole year
            while (departureDate.isBefore(yearFromNow)) {
                // get the ship arrival date at destination port (departureDate+transitTime)
                arrivalDate = TimeUtils.getInstance().futureDate(departureDate, route.getDaysAtSea());
                // add voyage to a sorted (by departure date) schedule
                addVoyageToSchedule(sortedSchedule, route, departureDate, false);
                // the ship returns back to origin port after it is unloaded and loaded up again
                departureDate = TimeUtils.getInstance().futureDate(arrivalDate, route.getDaysAtPort());
                // add return voyage to a sorted (by departure date) schedule
                addVoyageToSchedule(sortedSchedule, route, departureDate, true);
                // calculate departure date for next voyage from origin to destination
                departureDate = TimeUtils.getInstance().futureDate(departureDate,
                        route.getDaysAtSea() + route.getDaysAtPort());
            }
            // initial ship departures staggered by 2 days (change this if necessary)
            staggerInitialShipDepartures += 2;
            // reset departure date to today+stagger (calculated above) so that the ships
            // dont depart on the same day
            departureDate = TimeUtils.getInstance().futureDate(Instant.now(), staggerInitialShipDepartures);

        }

        return sortedSchedule;
    }

    private void addVoyageToSchedule(LinkedList<Voyage> sortedSchedule, final Route route, final Instant departureDate,
            final boolean returnVoyage) {
        int next = 0;
        while (next < sortedSchedule.size()) {
            final Voyage voyage = sortedSchedule.get(next);
            if (voyage.getSailDateObject().isBefore(departureDate)) {
                next++;
            } else {
                break;
            }
        }
        sortedSchedule.add(next, newScheduledVoyage(route, departureDate, returnVoyage));
    }

    private Voyage newScheduledVoyage(final Route route, final Instant departureDate, final boolean returnVoyage) {
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