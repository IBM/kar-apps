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

package com.ibm.research.kar.reeferserver.service;

import java.time.Instant;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.research.kar.reefer.common.error.RouteNotFoundException;
import com.ibm.research.kar.reefer.common.error.ShipCapacityExceeded;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.controller.OrderController;
import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reeferserver.scheduler.ShippingScheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class ScheduleService {

    private static final int THRESHOLD_IN_DAYS = 100;

    @Autowired
    private ShippingScheduler scheduler;

    private LinkedList<Voyage> masterSchedule = new LinkedList<>();
    private List<Route> routes = new ArrayList<>();
    private static final Logger logger = Logger.getLogger(ScheduleService.class.getName());

    public List<Route> getRoutes() {

        try {
            routes = scheduler.getRoutes();
        } catch (Exception e) {
            logger.log(Level.WARNING,"",e);
        }

        return routes;

    }
    public Voyage getVoyage(String voyageId ) throws VoyageNotFoundException {
        Iterator<Voyage> it = masterSchedule.iterator();
        while(it.hasNext()) {
            Voyage voyage = it.next();
            if ( voyageId.equals(voyage.getId())) {
                return voyage;
            }
        }
        throw new VoyageNotFoundException("ScheduleService.getVoyage() - voyage:"+voyageId+" not found in MasterSchedule");
    }
    public void generateNextSchedule(Instant date) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ScheduleService() - generateNextSchedule() ============================================ ");
        }
        long start = System.currentTimeMillis();
        // generate future schedule if number of days from a given date and the last
        // voyage in the
        // current master schedule is less than a threshold.
        try {
            if (routes.isEmpty()) {
                getRoutes();
            }
            LinkedList<Voyage> sortedSchedule = new LinkedList<>();
            StringBuilder sb = new StringBuilder("date " + date + "\n");
            for (Route route : routes) {
                long daysBetween = TimeUtils.getInstance().getDaysBetween(date, route.getLastArrival());
                if (logger.isLoggable(Level.FINE)) {
                    sb.append(route.getVessel().getId()).append(" last arrival date:").
                            append(route.getLastArrival()).append(" daysBetween: ").
                            append(daysBetween).append("\n");
                }

                if (route.getLastArrival() != null && daysBetween < THRESHOLD_IN_DAYS) {
                    Instant endDate = TimeUtils.getInstance().futureDate(route.getLastArrival(), 60);
                    Instant departureDate = TimeUtils.getInstance().futureDate(route.getLastArrival(), 2);
                    Instant lastDate =
                            scheduler.generateShipSchedule(route, departureDate, sortedSchedule, endDate);
                    route.setLastArrival(lastDate);
                }
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(sb.toString());
            }


            if (!sortedSchedule.isEmpty()) {
                masterSchedule.addAll(sortedSchedule);
                Collections.sort(masterSchedule, new SchedulerComp());
                if (logger.isLoggable(Level.FINE)) {
                    sb.setLength(0);
                    masterSchedule.forEach(voyage -> {
                        sb.append("\nMasterSchedule------->").append(voyage.getId()).append(" SailDate: ").append(voyage.getSailDate()).
                                append(" arrivalDate: ").append(voyage.getArrivalDate()).append("\n");
                        logger.fine(sb.toString());
                    });
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING,"",e);
        } finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ScheduleService() - generateNextSchedule() - total time spent generating new schedule: "+ (System.currentTimeMillis() - start));
            }
        }

    }

    public Instant getLastVoyageDateForRoute(Route route) throws RouteNotFoundException {

        ListIterator<Voyage> it = masterSchedule.listIterator(masterSchedule.size());
        Instant lastVoyageArrivalDate;
        while (it.hasPrevious()) {
            Voyage voyage = it.previous();
            // Find the last return trip for a given route
            if (voyage.getRoute().getVessel().getName().equals(route.getVessel().getName())) {
                lastVoyageArrivalDate = Instant.parse(voyage.getArrivalDate());
                return TimeUtils.getInstance().futureDate(lastVoyageArrivalDate, route.getDaysAtPort());
            }
        }
        throw new RouteNotFoundException("Unable to find the last voyage for vessel:" + route.getVessel().getName());
    }

    public Voyage updateDaysAtSea(String voyageId, int daysOutAtSea) throws VoyageNotFoundException {
        for (Voyage voyage : masterSchedule) {
            if (voyage.getId().equals(voyageId)) {
                voyage.getRoute().getVessel().setPosition(daysOutAtSea);
                int progress = Math.round((daysOutAtSea / (float) voyage.getRoute().getDaysAtSea()) * 100);
                voyage.getRoute().getVessel().setProgress(progress);
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("ScheduleService.updateDaysAtSea() - voyage:" + voyage.getId() + " daysOutAtSea:"
                            + voyage.getRoute().getVessel().getPosition() + " Progress:"
                            + voyage.getRoute().getVessel().getProgress());
                }
                return voyage;
            }
        }
        throw new VoyageNotFoundException("Voyage " + voyageId + " Not Found");
    }

    public List<Voyage> getMatchingSchedule(Instant startDate, Instant endDate) {
        List<Voyage> schedule = new ArrayList<>();
        for (Voyage voyage : masterSchedule) {
            if ((voyage.getSailDateObject().equals(startDate) || voyage.getSailDateObject().isAfter(startDate))
                    && (voyage.getSailDateObject().equals(endDate) || voyage.getSailDateObject().isBefore(endDate))) {
                schedule.add(voyage);
            }
        }

        return schedule;
    }

    public List<Voyage> getMatchingSchedule(String origin, String destination, Instant date) {
        List<Voyage> schedule = new ArrayList<>();
        for (Voyage voyage : masterSchedule) {

            if ((voyage.getSailDateObject().equals(date) || voyage.getSailDateObject().isAfter(date))
                    && voyage.getRoute().getOriginPort().equals(origin)
                    && voyage.getRoute().getDestinationPort().equals(destination)) {
                schedule.add(voyage);
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("getMatchingSchedule - Found voyage id:" + voyage.getId() + " Free Capacity:"
                            + voyage.getRoute().getVessel().getFreeCapacity());
                }
            }

        }

        return schedule;
    }

    /*
     * Returns voyages with ships currently at sea.
     */
    public List<Voyage> getActiveSchedule() {
        Instant currentDate = TimeUtils.getInstance().getCurrentDate();
        List<Voyage> activeSchedule = new ArrayList<>();
        if (logger.isLoggable(Level.FINE)) {
            StringBuilder sb = new StringBuilder();
            masterSchedule.forEach(voyage -> {
                sb.append("\n/////// master schedule - voyage:").append(voyage.getId()).append(" Current Date:").
                        append(currentDate).append(" SailDate:").append(voyage.getSailDateObject()).append(" DaysAtSea:").
                        append(voyage.getRoute().getDaysAtSea()).append(" Origin:").append(voyage.getRoute().getOriginPort()).
                        append(" Destination:").append(voyage.getRoute().getDestinationPort());

            });
            logger.fine(sb.toString());
        }


        for (Voyage voyage : masterSchedule) {
            Instant arrivalDate = TimeUtils.getInstance().futureDate(voyage.getSailDateObject(),
                    voyage.getRoute().getDaysAtSea() + voyage.getRoute().getDaysAtPort());
            if (voyage.getSailDateObject().isAfter(currentDate)) {
                // masterSchedule is sorted by sailDate, so if voyage sailDate > currentDate
                // we just stop iterating since all voyagaes sail in the future.
                break;
            }
            // find active voyage which is one that started before current date and
            // has not yet completed
            if (TimeUtils.getInstance().isSameDay(voyage.getSailDateObject(), currentDate)
                    || (voyage.getSailDateObject().isBefore(currentDate) && arrivalDate.isAfter(currentDate))) {
                activeSchedule.add(voyage);
            }
        }
        return activeSchedule;
    }
    public void generateShipSchedule() {
        try {
            scheduler.getRoutes();
            masterSchedule = scheduler.generateSchedule();
            System.out.println("ScheduleService.generateShipSchedule() - generated initial master schedule");
        } catch (Exception e) {
            logger.log(Level.WARNING,"",e);
        }
    }

    public List<Voyage> get() {
        try {
            scheduler.getRoutes();
        } catch (Exception e) {
            logger.log(Level.WARNING,"",e);
        }
        LinkedList<Voyage> sortedSchedule;
        synchronized(ScheduleService.class) {
            sortedSchedule = scheduler.generateSchedule();
            if (masterSchedule.isEmpty()) {
                masterSchedule.addAll(sortedSchedule);
            } else {
                Voyage lastVoyageFromMasterSchedule = masterSchedule.getLast();

                for (Voyage voyage : sortedSchedule) {
                    if (voyage.getSailDateObject().isAfter(lastVoyageFromMasterSchedule.getSailDateObject())) {
                        masterSchedule.add(voyage);
                    }
                }
            }
        }


        return new ArrayList<Voyage>(sortedSchedule);
    }

    public int updateFreeCapacity(String voyageId, int reeferCount)
            throws VoyageNotFoundException, ShipCapacityExceeded {
        Voyage voyage = getVoyage(voyageId);
        if (voyage.getRoute().getVessel().getFreeCapacity() - reeferCount >= 0) {
            voyage.getRoute().getVessel()
                    .setFreeCapacity(voyage.getRoute().getVessel().getFreeCapacity() - reeferCount);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ScheduleService.updateFreeCapacity() - Vessel " + voyage.getRoute().getVessel().getName() + " Updated Free Capacity "
                        + voyage.getRoute().getVessel().getFreeCapacity());
            }
            return voyage.getRoute().getVessel().getFreeCapacity();
        }
        throw new ShipCapacityExceeded(
                "VoyageID:" + voyageId + " Unable to book ship due to lack of capacity. Current capacity:"
                        + voyage.getRoute().getVessel().getFreeCapacity() + " Order reefer count:" + reeferCount);
    }

    class SchedulerComp implements Comparator<Voyage> {

        @Override
        public int compare(Voyage v1, Voyage v2) {
            if (v1.getSailDateObject().isAfter(v2.getSailDateObject())) {
                return 1;
            } else {
                return -1;
            }
        }
    }
}