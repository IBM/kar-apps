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

import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.error.ShipCapacityExceeded;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reeferserver.scheduler.ShippingScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.json.JsonString;
import javax.json.JsonValue;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
public class ScheduleService extends AbstractPersistentService {

    private static final int THRESHOLD_IN_DAYS = 100;

    @Autowired
    private ShippingScheduler scheduler;
    @Autowired
    private TimeService timeService;

    private Set<Voyage> masterSchedule = new TreeSet<>();
    private List<Route> routes = new ArrayList<>();
    private static final Logger logger = Logger.getLogger(ScheduleService.class.getName());

    public List<Route> getRoutes() {
        if (routes.isEmpty()) {
            try {
                routes = scheduler.getRoutes();
            } catch (Exception e) {
                logger.log(Level.WARNING, "", e);
            }
        }
        return routes;

    }

    public Voyage getVoyage(final String voyageId) throws VoyageNotFoundException {
        synchronized (ScheduleService.class) {
            Optional<Voyage> voyage =
                    masterSchedule.stream().filter(v -> v.getId().equals(voyageId)).findFirst();
            if (voyage.isPresent()) {
                return voyage.get();
            }
        }

        throw new VoyageNotFoundException("ScheduleService.getVoyage() - voyage:" + voyageId + " not found in MasterSchedule");
    }

    public void generateShipSchedule() {

        try {
            masterSchedule = scheduler.generateSchedule();
            //           System.out.println("ScheduleService.generateShipSchedule() - generated initial master schedule");
            masterSchedule.forEach(v -> System.out.println("##### Voyage:" + v.getId() + " Departure:" + v.getSailDateObject() + " Arrival:" + v.getArrivalDate()));

            timeService.saveDate(((TreeSet<Voyage>) masterSchedule).last().getSailDateObject(), Constants.SCHEDULE_END_DATE_KEY);
            routes.forEach(r -> System.out.println("ScheduleService.generateShipSchedule ---- Ship: " + r.getVessel().getName() + " Last Arrival:" + r.getLastArrival()));
            System.out.println("ScheduleService.generateShipSchedule ---- Saved End Date:" + ((TreeSet<Voyage>) masterSchedule).last().getSailDateObject());
        } catch (Exception e) {

            logger.log(Level.WARNING, "", e);
        }


    }

    public void generateShipSchedule(Instant baseScheduleDate) {
        try {
            Optional<Instant> lastVoyageDate = getLastVoyageDate();

            if (lastVoyageDate.isPresent()) {
                masterSchedule = scheduler.generateSchedule(baseScheduleDate, lastVoyageDate.get());
            } else {
                masterSchedule = scheduler.generateSchedule(baseScheduleDate, TimeUtils.getInstance().getDateYearFrom(TimeUtils.getInstance().getCurrentDate()));
            }

            //           System.out.println("ScheduleService.generateShipSchedule() - generated master schedule from base date:"+baseScheduleDate);
            masterSchedule.forEach(v -> System.out.println("xxxx Voyage:" + v.getId() + " Departure:" + v.getSailDateObject() + " Arrival:" + v.getArrivalDate()));
            timeService.saveDate(((TreeSet<Voyage>) masterSchedule).last().getSailDateObject(), Constants.SCHEDULE_END_DATE_KEY);
            routes.forEach(r -> System.out.println("ScheduleService.generateShipSchedule ++++ Ship: " + r.getVessel().getName() + " Last Arrival:" + r.getLastArrival()));
            System.out.println("ScheduleService.generateShipSchedule ++++ Saved End Date:" + ((TreeSet<Voyage>) masterSchedule).last().getSailDateObject());

            // generate schedule within a given range of dates

        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
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
            LinkedList<Voyage> sortedSchedule = new LinkedList<>();
            TreeSet<Voyage> schedule = new TreeSet<>();
            StringBuilder sb = new StringBuilder("date " + date + "\n");
            Instant lastVoyageDate = null;
            for (Route route : routes) {
                long daysBetween = TimeUtils.getInstance().getDaysBetween(date, route.getLastArrival());
                if (logger.isLoggable(Level.FINE)) {
                    sb.append(route.getVessel().getId()).append(" last arrival date:").
                            append(route.getLastArrival()).append(" daysBetween: ").
                            append(daysBetween).append("\n");
                }
                if (route.getLastArrival() != null && daysBetween < THRESHOLD_IN_DAYS) {
                    // generate new schedule for the whole year
                    Instant endDate = TimeUtils.getInstance().futureDate(route.getLastArrival(), 365);
                    //Instant departureDate = TimeUtils.getInstance().futureDate(route.getLastArrival(), 2);
                    Instant departureDate = route.getLastArrival();
                    //lastVoyageDate = scheduler.generateShipSchedule(route, departureDate, sortedSchedule, endDate);
                    schedule.addAll(scheduler.generateShipSchedule(route, departureDate, endDate));
                    //System.out.println("ScheduleService.generateNextSchedule() - schedule range - begin:"+departureDate+" end:"+endDate);
                    //route.setLastArrival(lastVoyageDate);
                    lastVoyageDate = route.getLastArrival();
                }
            }
            if (lastVoyageDate != null) {
                //Instant lastVoyageDate = masterSchedule.get(masterSchedule.size()-1).getSailDateObject();
                // persist the last voyage date to be able to recover schedule after REST restarts
                timeService.saveDate(lastVoyageDate, Constants.SCHEDULE_END_DATE_KEY);
                // System.out.println("ScheduleService.generateNextSchedule() - Saved new schedule last voyage date:"+lastVoyageDate);
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(sb.toString());
            }

            synchronized (ScheduleService.class) {
                if (!schedule.isEmpty()) {
                    masterSchedule.addAll(schedule);
                    if (logger.isLoggable(Level.FINE)) {
                        sb.setLength(0);
                        masterSchedule.forEach(voyage -> {
                            sb.append("\nMasterSchedule------->").append(voyage.getId()).append(" SailDate: ").append(voyage.getSailDate()).
                                    append(" arrivalDate: ").append(voyage.getArrivalDate()).append("\n");
                            logger.fine(sb.toString());
                        });
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        } finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ScheduleService() - generateNextSchedule() - total time spent generating new schedule: " + (System.currentTimeMillis() - start));
            }
        }

    }

    public Optional<Instant> getLastVoyageDate() {
        JsonValue jv = super.get(Constants.SCHEDULE_END_DATE_KEY);
        if (jv == null || jv == JsonValue.NULL) {
            return Optional.empty();
        }
        return Optional.of(Instant.parse(((JsonString) jv).getString()));
    }

    public Voyage updateDaysAtSea(String voyageId, int daysOutAtSea) throws VoyageNotFoundException {
        synchronized (ScheduleService.class) {
            for (Voyage voyage : masterSchedule) {
                if (voyage.getId().equals(voyageId)) {
                    voyage.getRoute().getVessel().setPosition(daysOutAtSea);
                    int progress = Math.round((daysOutAtSea / (float) voyage.getRoute().getDaysAtSea()) * 100);

                    // progress = (int) (Math.ceil(progress/10)*10);
                    voyage.getRoute().getVessel().setProgress((int) (((progress + 5) / 10) * 10));
                    if (logger.isLoggable(Level.INFO)) {
                        logger.info("ScheduleService.updateDaysAtSea() - voyage:" + voyage.getId() + " daysOutAtSea:"
                                + voyage.getRoute().getVessel().getPosition() + " Progress:"
                                + voyage.getRoute().getVessel().getProgress());
                    }
                    return voyage;
                }
            }
        }

        throw new VoyageNotFoundException("Voyage " + voyageId + " Not Found");
    }

    public List<Voyage> getMatchingSchedule(Instant startDate, Instant endDate) {
        synchronized (ScheduleService.class) {
            return masterSchedule.
                    stream().
                    filter(voyage -> voyage.getSailDateObject().equals(startDate) || voyage.getSailDateObject().isAfter(startDate)).
                    filter(voyage -> voyage.getSailDateObject().equals(endDate) || voyage.getSailDateObject().isBefore(endDate)).
                    collect(Collectors.toList());
        }
    }


    public List<Voyage> getMatchingSchedule(String origin, String destination, Instant date) {
        synchronized (ScheduleService.class) {
            return masterSchedule.
                    stream().
                    filter(voyage -> voyage.getSailDateObject().equals(date) || voyage.getSailDateObject().isAfter(date)).
                    filter(voyage -> voyage.getRoute().getOriginPort().equals(origin)
                            && voyage.getRoute().getDestinationPort().equals(destination)).
                    collect(Collectors.toList());

        }
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
        synchronized (ScheduleService.class) {
            for (Voyage voyage : masterSchedule) {
                if (voyage.getSailDateObject().isAfter(currentDate)) {
                    // masterSchedule is sorted by sailDate, so if voyage sailDate > currentDate
                    // we just stop iterating since all voyages sail in the future.
                    break;
                }
                Instant arrivalDate = TimeUtils.getInstance().futureDate(voyage.getSailDateObject(),
                        voyage.getRoute().getDaysAtSea() + voyage.getRoute().getDaysAtPort());

                // find active voyage which is one that started before current date and
                // has not yet completed
                if (TimeUtils.getInstance().isSameDay(voyage.getSailDateObject(), currentDate)
                        || (voyage.getSailDateObject().isBefore(currentDate) && arrivalDate.isAfter(currentDate))) {
                    activeSchedule.add(voyage);
                }
            }

        }

        return activeSchedule;
    }

    public List<Voyage> get() {
        return new ArrayList<Voyage>(masterSchedule);
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