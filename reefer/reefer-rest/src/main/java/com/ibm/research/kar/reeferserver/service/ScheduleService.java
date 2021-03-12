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
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
public class ScheduleService extends AbstractPersistentService {
/*
        public static final int THRESHOLD_IN_DAYS = 100;
        private static final int SCHEDULE_DAYS = 365;


 */
    public static final int THRESHOLD_IN_DAYS = 330;
    private static final int SCHEDULE_DAYS = 35;

    @Autowired
    private ShippingScheduler scheduler;
    @Autowired
    private TimeService timeService;

    private Set<Voyage> masterSchedule = new TreeSet<>();
    SortedSet<Voyage> schedule = null;
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

    /**
     * Called when REST starts either cold or warm. In case of warm start the schedule
     * may contain arrived voyages which will be trimmed.
     *
     * @param baseScheduleDate
     */
    public void generateShipSchedule(Instant baseScheduleDate) {
        try {
            Instant scheduleTrimDate = TimeUtils.getInstance().getCurrentDate().minus(20, ChronoUnit.DAYS);
            synchronized (ScheduleService.class) {
                masterSchedule = scheduler.generateSchedule(baseScheduleDate, getLastVoyageDate());
                // save last voyage departure date to be able to restore schedule after REST service restarts
                timeService.saveDate(((TreeSet<Voyage>) masterSchedule).last().getSailDateObject(), Constants.SCHEDULE_END_DATE_KEY);
                System.out.println("ScheduleService.generateShipSchedule ++++ Saved End Date:" + ((TreeSet<Voyage>) masterSchedule).last().getSailDateObject());
                // The schedule generated above may include voyages which have arrived already.
                // Remove all arrived voyages up to 10 days ago.
                trimArrivedVoyages(scheduleTrimDate, TimeUtils.getInstance().getCurrentDate());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public Instant lastVoyageDepartureDate() {
        synchronized (ScheduleService.class) {
            Voyage lastVoyage = ((TreeSet<Voyage>) masterSchedule).last();
            return lastVoyage.getSailDateObject();
        }
    }

    /**
     * This is called while the REST is running and when its determined that the schedule
     * needs to be extended to make sure we don't run out of voyages.
     *
     * @param currentScheduleEndDate - date of the last voyage departure in the current schedule
     */
    public void extendSchedule(Instant currentScheduleEndDate) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ScheduleService() - extendSchedule() ============================================ ");
        }
        // upper date range for new schedule
        Instant endDate = TimeUtils.getInstance().futureDate(currentScheduleEndDate, SCHEDULE_DAYS);
        // lower date range for new schedule
        Optional<Instant> scheduleBaseDate = timeService.recoverDate(Constants.SCHEDULE_BASE_DATE_KEY);

        synchronized (ScheduleService.class) {
            Instant currentDate = TimeUtils.getInstance().getCurrentDate();
            Instant scheduleTrimDate = currentDate.minus(20, ChronoUnit.DAYS);
            // ####
            Set<Voyage> currentScheduleCopy = new TreeSet<>(masterSchedule);

            List<Voyage> activeScheduleBefore = getActiveSchedule(currentDate);
            Instant baseDate = scheduleBaseDate.get();
            System.out.println("ScheduleService.extendSchedule() >>>> extending schedule - currentDate:"+currentDate+" baseDate:"+baseDate+" endDate:"+endDate);
            // generate new schedule for a given range of dates
            masterSchedule = scheduler.generateSchedule(baseDate, endDate);
            // persist last voyage departure date which will be used to restore schedule after
            // REST restart
            timeService.saveDate(((TreeSet<Voyage>) masterSchedule).last().getSailDateObject(), Constants.SCHEDULE_END_DATE_KEY);
            if (logger.isLoggable(Level.INFO)) {
                masterSchedule.forEach(v -> System.out.println(">>>> Voyage:" +
                        v.getId() +
                        " Departure:" +
                        v.getSailDateObject() +
                        " Arrival:" + v.getArrivalDate()));
            }
            validateSchedule(activeScheduleBefore, "extension", currentDate, scheduleBaseDate.get(),currentScheduleCopy);

            // The schedule generated above includes voyages which have arrived already.
            // Remove all arrived voyages up to 20 days ago.
            trimArrivedVoyages(scheduleTrimDate, currentDate);

            validateSchedule(activeScheduleBefore, "trimming", currentDate, scheduleBaseDate.get(),currentScheduleCopy);
        }
    }

    private void validateSchedule(List<Voyage> originalActiveSchedule, String lbl, Instant currentDate, Instant scheduleBaseDate, Set<Voyage> previousSchedule) {
        List<Voyage> activeScheduleNow = getActiveSchedule(currentDate);
        /*
        Voyage v = activeScheduleNow.get(0);
        activeScheduleNow.remove(0);
        activeScheduleNow.add(0, new Voyage("FOO-BAR", v.getRoute(),v.getSailDateObject(), v.getArrivalDate()));
         */
        if (!activeListsMatch(originalActiveSchedule, activeScheduleNow)) {
            System.out.println("ScheduleService.validateSchedule() - After " + lbl + " active schedule does not match pre-" + lbl + " schedule ");
            System.out.println("Before " + lbl + " Active List:");
            dumpVoyages(originalActiveSchedule);
            System.out.println("After " + lbl + " Active List:");
            dumpVoyages(activeScheduleNow);
            System.out.println("ScheduleService.validateSchedule() - current date:" +
                    currentDate +
                    " schedule base date:" +
                    scheduleBaseDate );
            StringBuilder sb = new StringBuilder("\n ***********************\n").append("\t PREVIOUS SCHEDULE:\n\t");
            previousSchedule.forEach(v -> sb.append("\tVoyageID:\t ").append(v.getId()).append("\tDeparts:\t").append(v.getSailDateObject()).append("\tArrives:\t").append(v.getArrivalDate()));
            System.out.println(sb.toString());

            StringBuilder sb2 = new StringBuilder("\n ***********************\n").append("\t CURRENT SCHEDULE:\n\t");
            masterSchedule.forEach(v -> sb2.append("\tVoyageID:\t ").append(v.getId()).append("\tDeparts:\t").append(v.getSailDateObject()).append("\tArrives:\t").append(v.getArrivalDate()));
            System.out.println(sb2.toString());

        }
    }

    private void dumpVoyages(Collection<Voyage> list) {
        list.forEach(v -> System.out.println("\t Voyage:" + v.getId() + " departure:" + v.getSailDateObject() + " arrival:" + v.getArrivalDate()));
    }

    private boolean activeListsMatch(List<Voyage> actives1, List<Voyage> actives2) {
        if (actives1.size() != actives2.size()) {
            return false;
        }
        return actives1.stream().allMatch(v -> actives2.contains(v));
    }

    private void trimArrivedVoyages(Instant scheduleTrimDate, Instant currentDate) {
        int sizeBefore = masterSchedule.size();
        // remove old, arrived voyages
        masterSchedule.removeIf(v -> v.shipArrived(scheduleTrimDate));
        System.out.println("ScheduleService() - trimArrivedVoyages() - current date:" +
                currentDate +
                " schedule trim date:" +
                scheduleTrimDate +
                " - schedule size before trim:" + sizeBefore + " - size after trim:" + masterSchedule.size());
    }

    public Instant getLastVoyageDate() {
        JsonValue jv = super.get(Constants.SCHEDULE_END_DATE_KEY);
        if (jv == null || jv == JsonValue.NULL) {
            return TimeUtils.getInstance().getDateYearFrom(TimeUtils.getInstance().getCurrentDate());
        }
        return Instant.parse(((JsonString) jv).getString());
    }

    public Voyage updateDaysAtSea(String voyageId, int daysOutAtSea) throws VoyageNotFoundException {
        synchronized (ScheduleService.class) {
            for (Voyage voyage : masterSchedule) {
                if (voyage.getId().equals(voyageId)) {
                    voyage.getRoute().getVessel().setPosition(daysOutAtSea);
                    int progress = Math.round((daysOutAtSea / (float) voyage.getRoute().getDaysAtSea()) * 100);

                    //voyage.getRoute().getVessel().setProgress((int) (((progress + 5) / 10) * 10));
                    //voyage.getRoute().getVessel().setProgress(progress);
                    voyage.setProgress(progress);
                    if (logger.isLoggable(Level.INFO)) {
                        logger.info("ScheduleService.updateDaysAtSea() - voyage:" + voyage.getId() + " daysOutAtSea:"
                                + voyage.getRoute().getVessel().getPosition() + " Progress:"
                                + voyage.getProgress());
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
        return getActiveSchedule(TimeUtils.getInstance().getCurrentDate());
    }

    /*
     * Returns voyages with ships currently at sea.
     */
    private List<Voyage> getActiveSchedule(Instant currentDate) {
        //Instant currentDate = TimeUtils.getInstance().getCurrentDate();
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
/*
                if (voyage.getSailDateObject().equals(currentDate)
                        || (voyage.getSailDateObject().isBefore(currentDate)
                           && (arrivalDate.equals(currentDate) || arrivalDate.isAfter(currentDate))) ) {
                    activeSchedule.add(voyage);
                }

 */
            }

        }
        return activeSchedule;
    }

    public List<Voyage> get() {
        synchronized (ScheduleService.class) {
            return new ArrayList<Voyage>(masterSchedule);
        }
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
}