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

package com.ibm.research.kar.reefer.common;

import com.ibm.research.kar.reefer.common.error.VoyageNotFoundException;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Vessel;
import com.ibm.research.kar.reefer.model.Voyage;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.json.JsonArray;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class ScheduleService {

    public static final int THRESHOLD_IN_DAYS = 100; //30; //100;
    public static final int SCHEDULE_DAYS = 365; //60; //365;
    public static final int ARRIVED_THRESHOLD_IN_DAYS = 2; //1;

    private ShippingScheduler scheduler;

    private Set<Voyage> masterSchedule = new TreeSet<>();
    private List<Route> routes = new ArrayList<>();
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(ScheduleService.class.getName());

    public ScheduleService(ShippingScheduler scheduler) {
        this.scheduler = scheduler;
    }

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
    public List<Vessel> getVessels() throws Exception {
        return scheduler.getVessels();
    }
    public Voyage getVoyage(final String voyageId) throws VoyageNotFoundException {
        Optional<Voyage> voyage =
                masterSchedule.stream().filter(v -> v.getId().equals(voyageId)).findFirst();
        if (voyage.isPresent()) {
            return voyage.get();
        }
        throw new VoyageNotFoundException("ScheduleService.getVoyage() - voyage:" + voyageId + " not found in MasterSchedule");
    }

    /**
     * Called when REST starts either cold or warm. In case of warm start the schedule
     * may contain arrived voyages which will be trimmed.
     *
     * @param baseScheduleDate
     */
    public Instant generateShipSchedule(Instant baseScheduleDate, Instant currentDate, Instant lastVoyageDate) {
        masterSchedule = scheduler.generateSchedule(baseScheduleDate, lastVoyageDate, currentDate);
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,"ScheduleService.generateShipSchedule() - generated schedule - size:" + masterSchedule.size()+" dumping schedule ....");
            try {
                for( Voyage v: masterSchedule ) {
                    logger.log(Level.FINE, "Master Schedule Voyage:" + v.getId() + " departure:" + v.getSailDateObject() + " arrival:" + v.getArrivalDate());
                }
            } catch ( Exception e) {
                String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
                logger.log(Level.SEVERE,"ScheduleService.generateShipSchedule() - Error: "+ stacktrace);
            }
        }
        if ( masterSchedule.isEmpty()) {
            return lastVoyageDate;
        }
        return ((TreeSet<Voyage>) masterSchedule).last().getSailDateObject();

    }

    public Instant lastVoyageDepartureDate() {
        Voyage lastVoyage = ((TreeSet<Voyage>) masterSchedule).last();
        return lastVoyage.getSailDateObject();
    }

    /**
     * This is called while REST is running to make sure the schedule doesn't run out of voyages.
     * The schedule start date is always the same (cold start date), the end date is extended by
     * value from SCHEDULE_DAYS. While generating a new schedule, all voyages that have arrived
     * up to ARRIVED_THRESHOLD_IN_DAYS before now will be excluded.
     *
     * @param currentScheduleEndDate - date of the last voyage departure in the current schedule
     * @param currentDate            - today
     */
    public Instant extendSchedule(Instant baseDate, Instant currentScheduleEndDate, Instant currentDate) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ScheduleService() - extendSchedule() ============================================ ");
        }
        // add N (where N=SCHEDULE_DAYS) days to the end of the current schedule.
        Instant endDate = TimeUtils.getInstance().futureDate(currentScheduleEndDate, SCHEDULE_DAYS);
        // every schedule starts on the same date (date of the REST cold start)

        List<Voyage> activeScheduleBefore = getActiveSchedule(currentDate);
        //Instant baseDate = scheduleBaseDate.get();
        // hold on to the current schedule. Need it to copy active and booked voyages
        // order counts, progress and free capacities
        Set<Voyage> previousSchedule = masterSchedule;
        // generate new schedule for a given range of dates. It will trim arrived
        // voyages to reduce schedule size.
        masterSchedule = scheduler.generateSchedule(baseDate, endDate, currentDate);
        // update current active and booked voyages with data from previous schedule
        masterSchedule.forEach(v -> {
            updateVoyage(v, previousSchedule);
        });
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ScheduleService.extendSchedule() >>>> currentDate:" +
                    currentDate.toString().replace("T00:00:00Z", "") +
                    " baseDate:" + baseDate.toString().replace("T00:00:00Z", "") +
                    " endDate:" + endDate.toString().replace("T00:00:00Z", "") +
                    " trimDate:" + currentDate.minus(ScheduleService.ARRIVED_THRESHOLD_IN_DAYS, ChronoUnit.DAYS).toString().replace("T00:00:00Z", "") +
                    " previous schedule size:" + previousSchedule.size() +
                    " current schedule size:" + masterSchedule.size());
        }
        previousSchedule.clear();
        // persist last voyage departure date which will be used to restore schedule after
        // REST restart
        if (logger.isLoggable(Level.FINE)) {
            masterSchedule.forEach(v -> System.out.println(">>>> Voyage:" +
                    v.getId() +
                    " Departure:" +
                    v.getSailDateObject() +
                    " Arrival:" + v.getArrivalDate()));
        }
        // new active schedule (created from new schedule) must match previous active schedule
        validateSchedule(activeScheduleBefore, "extension", currentDate, baseDate);
       // dumpVoyages(masterSchedule);
        return ((TreeSet<Voyage>) masterSchedule).last().getSailDateObject();
    }

    private void updateVoyage(Voyage voyage, Set<Voyage> previousSchedule) {
        for (Voyage v : previousSchedule) {
            if (v.getId().equals(voyage.getId())) {
                voyage.setOrderCount(v.getOrderCount());
                voyage.setFreeCapacity(v.getRoute().getVessel().getFreeCapacity());
                voyage.setProgress(v.getProgress());
                break;
            }
        }

    }

    private void validateSchedule(List<Voyage> originalActiveSchedule, String lbl, Instant currentDate, Instant scheduleBaseDate) {
        List<Voyage> activeScheduleNow = getActiveSchedule(currentDate);

        if (!activeListsMatch(originalActiveSchedule, activeScheduleNow)) {
            logger.log(Level.FINE, "ScheduleService.validateSchedule() - After " + lbl + " active schedule does not match pre-" + lbl + " schedule ");
            logger.log(Level.FINE, "Before " + lbl + " Active List:");
            dumpVoyages(originalActiveSchedule);
            logger.log(Level.FINE, "After " + lbl + " Active List:");
            if ( logger.isLoggable(Level.FINE)) {
                dumpVoyages(activeScheduleNow);
            }
        }
    }

    private void dumpVoyages(Collection<Voyage> schedule) {
        for( Voyage v: schedule ) {
            logger.log(Level.FINE, "Master Schedule Voyage:" + v.getId() + " departure:" + v.getSailDateObject() + " arrival:" + v.getArrivalDate());
        }
    }

    private boolean activeListsMatch(List<Voyage> actives1, List<Voyage> actives2) {
        if (actives1.size() != actives2.size()) {
            return false;
        }
        return actives1.stream().allMatch(v -> actives2.contains(v));
    }

    public Voyage updateDaysAtSea(String voyageId, int daysOutAtSea) throws VoyageNotFoundException {

        for (Voyage voyage : masterSchedule) {
            if (voyage.getId().equals(voyageId)) {
                voyage.getRoute().getVessel().setPosition(daysOutAtSea);
                int progress = Math.round((daysOutAtSea / (float) voyage.getRoute().getDaysAtSea()) * 100);
                voyage.setProgress(progress);
                return voyage;
            }
        }
        throw new VoyageNotFoundException("Voyage " + voyageId + " Not Found - current date: "+TimeUtils.getInstance().getCurrentDate());
    }

    public List<Voyage> getMatchingSchedule(Instant startDate, Instant endDate) {
        return masterSchedule.
                stream().
                filter(voyage -> voyage.getSailDateObject().equals(startDate) || voyage.getSailDateObject().isAfter(startDate)).
                filter(voyage -> voyage.getSailDateObject().equals(endDate) || voyage.getSailDateObject().isBefore(endDate)).
                collect(Collectors.toList());
    }


    public List<Voyage> getMatchingSchedule(String origin, String destination, Instant date) {
        return masterSchedule.
                stream().
                filter(voyage -> voyage.getSailDateObject().equals(date) || voyage.getSailDateObject().isAfter(date)).
                filter(voyage -> voyage.getRoute().getOriginPort().equals(origin)
                        && voyage.getRoute().getDestinationPort().equals(destination)).
                collect(Collectors.toList());
    }

    /*
     * Returns voyages with ships currently are active which means voyages that left before current date
     * and have not yet arrived or have arrived and are being unloaded
     */
    public List<Voyage> getActiveSchedule() {
        return getActiveSchedule(TimeUtils.getInstance().getCurrentDate());
    }

    public List<Voyage> getActiveVoyages() {
        Instant currentDate = TimeUtils.getInstance().getCurrentDate();
        List<Voyage> scheduledVoyages = getActiveSchedule(currentDate);
        StringBuilder sb = new StringBuilder();
        if ( logger.isLoggable(Level.FINE)) {
            for( Voyage v : scheduledVoyages ) {
                sb.append("+++++ ").append(v.getId()).append(" progress:").append(v.getProgress()).append("\n");
            }
        }
        return scheduledVoyages;
    }
    private List<Voyage> getActiveSchedule(Instant currentDate) {
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
            if (voyage.getSailDateObject().isAfter(currentDate)) {
                // masterSchedule is sorted by sailDate, so if voyage sailDate > currentDate
                // we just stop iterating since all voyages beyond this point sail in the future.
                break;
            }
            // active voyage = sailed before current date AND
            // not arrived yet OR arrived but still at port.
            // Each ship stays at port for configurable amount
            // of time ( currently two days).
            if ( !voyage.shipArrived() || atPortStill(currentDate, voyage)  ) {
                activeSchedule.add(voyage);
            }
        }
        return activeSchedule;
    }
    private boolean atPortStill(Instant currentDate, Voyage voyage) {
        Instant returnVoyageDate = TimeUtils.getInstance().futureDate(Instant.parse(voyage.getArrivalDate()), voyage.getRoute().getDaysAtPort());
        return ( voyage.shipArrived() && ( TimeUtils.getInstance().isSameDay(returnVoyageDate, currentDate) || currentDate.isBefore(returnVoyageDate)) );
    }
    public Set<Voyage> findVoyagesBeyondArrivalDate(JsonArray activeOrders) {
        Instant today = TimeUtils.getInstance().getCurrentDate();
        return activeOrders.
                stream().
                map(jv -> {
                    try {
                        return getVoyage(jv.asJsonObject().getString(Constants.VOYAGE_ID_KEY));
                    } catch (VoyageNotFoundException e) {
                        return null;
                    }
                }).
                filter(Objects::nonNull).
                filter(v -> TimeUtils.getInstance().getDaysBetween(Instant.parse(v.getArrivalDate()), today) > 5).
                collect(Collectors.toSet());
    }

    public List<Voyage> get() {

        return new ArrayList<Voyage>(masterSchedule);

    }

    public void updateFreeCapacity(String voyageId, int freeCapacity)
            throws VoyageNotFoundException {
        Voyage voyage = getVoyage(voyageId);
        voyage.getRoute().getVessel()
                .setFreeCapacity(freeCapacity);
    }
}