package com.ibm.research.kar.reeferserver.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import java.util.Collections;
import java.util.Comparator;

import com.ibm.research.kar.reefer.common.error.RouteNotFoundException;
import com.ibm.research.kar.reefer.common.error.ShipCapacityExceeded;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
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

    public List<Route> getRoutes() {

        try {
            routes = scheduler.getRoutes();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return routes;

    }

    public void generateNextSchedule(Instant date) {
        System.out.println("ScheduleService() - generateNextSchedule() ============================================ ");
        // generate future schedule if number of days from a given date and the last
        // voyage in the
        // current master schedule is less than a threshold.
        try {
            if (routes.isEmpty()) {
                getRoutes();
            }
            LinkedList<Voyage> sortedSchedule = new LinkedList<>();
//            StringBuilder sb = new StringBuilder("date "+date+"\n");
            for( Route route : routes ) {
                long daysBetween = TimeUtils.getInstance().getDaysBetween(date, route.getLastArrival());
//                sb.append(route.getVessel().getId()).append(" last arrival date:").
//                append(route.getLastArrival()).append(" daysBetween: ").
//                append(daysBetween).append("\n");
                if (route.getLastArrival() != null && daysBetween < THRESHOLD_IN_DAYS) {
                    Instant endDate = TimeUtils.getInstance().futureDate( route.getLastArrival(), 60);
                    Instant departureDate = TimeUtils.getInstance().futureDate(route.getLastArrival(), 2);
                    Instant lastDate = 
                        scheduler.generateShipSchedule(route, departureDate, sortedSchedule, endDate);
                    route.setLastArrival(lastDate);
                }
            }
//            System.out.println(sb.toString());
            
//            sb.setLength(0);

            if ( !sortedSchedule.isEmpty() ) {
                masterSchedule.addAll(sortedSchedule);
                Collections.sort(masterSchedule,new SchedulerComp());
//                masterSchedule.forEach(voyage -> {
//                    sb.append("\nMasterSchedule------->").append(voyage.getId()).append(" SailDate: ").append(voyage.getSailDate()).
//                    append(" arrivalDate: ").append(voyage.getArrivalDate()).append("\n");
//                });
//                System.out.println(sb.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                System.out.println("ScheduleService.updateDaysAtSea() - voyage:" + voyage.getId() + " daysOutAtSea:"
                        + voyage.getRoute().getVessel().getPosition() + " Progress:"
                        + voyage.getRoute().getVessel().getProgress());
                return voyage;
            }
        }
        throw new VoyageNotFoundException("Voyage " + voyageId + " Not Found");
    }

    public List<Voyage> getMatchingSchedule(Instant startDate, Instant endDate) {
        List<Voyage> schedule = new ArrayList<>();
        if (masterSchedule.isEmpty()) {
            try {
                scheduler.getRoutes();
                masterSchedule = scheduler.generateSchedule();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
        if (masterSchedule.isEmpty()) {
            try {
                System.out.println("getMatchingSchedule - Master Schedule is empty, generating new one");
                scheduler.getRoutes();
                masterSchedule = scheduler.generateSchedule();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (Voyage voyage : masterSchedule) {

            if ((voyage.getSailDateObject().equals(date) || voyage.getSailDateObject().isAfter(date))
                    && voyage.getRoute().getOriginPort().equals(origin)
                    && voyage.getRoute().getDestinationPort().equals(destination)) {
                schedule.add(voyage);
                System.out.println("getMatchingSchedule - Found voyage id:" + voyage.getId() + " Free Capacity:"
                        + voyage.getRoute().getVessel().getFreeCapacity());
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
        if (masterSchedule.isEmpty()) {
            try {
                scheduler.getRoutes();
                masterSchedule = scheduler.generateSchedule();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        /*
        StringBuilder sb2 = new StringBuilder();
        masterSchedule.forEach(voyage -> {
            sb2.append("\n/////// master schedule - voyage:").append(voyage.getId()).append(" Current Date:").
            append(currentDate).append(" SailDate:").append(voyage.getSailDateObject()).append(" DaysAtSea:").
            append(voyage.getRoute().getDaysAtSea()).append(" Origin:").append(voyage.getRoute().getOriginPort()).
            append(" Destination:").append(voyage.getRoute().getDestinationPort());

        });

        System.out.println(sb2.toString());
        */
        for (Voyage voyage : masterSchedule) {
            Instant arrivalDate = TimeUtils.getInstance().futureDate(voyage.getSailDateObject(),
                    voyage.getRoute().getDaysAtSea() + voyage.getRoute().getDaysAtPort());

//            System.out.println(".... getActiveSchedule() - voyage:"+voyage.getId()+" Current Date:"+currentDate+" SailDate:"+voyage.getSailDateObject());
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

    public List<Voyage> get() {
        try {
            scheduler.getRoutes();
        } catch (Exception e) {
            e.printStackTrace();
        }

        LinkedList<Voyage> sortedSchedule = scheduler.generateSchedule();
        if (masterSchedule.size() == 0) {
            masterSchedule.addAll(sortedSchedule);
        } else {
            Voyage lastVoyageFromMasterSchedule = masterSchedule.getLast();

            for (Voyage voyage : sortedSchedule) {
                if (voyage.getSailDateObject().isAfter(lastVoyageFromMasterSchedule.getSailDateObject())) {
                    masterSchedule.add(voyage);
                }
            }
        }

        return new ArrayList<Voyage>(sortedSchedule);
    }

    public Voyage getVoyage(String voyageId) throws VoyageNotFoundException {
        for (Voyage voyage : masterSchedule) {
            if (voyage.getId().equals(voyageId)) {
                return voyage;
            }
        }
        throw new VoyageNotFoundException("Unable to find voyage with ID:" + voyageId);
    }

    public int updateFreeCapacity(String voyageId, int reeferCount)
            throws VoyageNotFoundException, ShipCapacityExceeded {
        Voyage voyage = getVoyage(voyageId);
        if (voyage.getRoute().getVessel().getFreeCapacity() - reeferCount >= 0) {

//            System.out
//                    .println("ScheduleService.updateFreeCapacity() - Free Capacity Before:"
//                            + voyage.getRoute().getVessel().getFreeCapacity() + " Reefers to cargo:" + reeferCount);
            voyage.getRoute().getVessel()
                    .setFreeCapacity(voyage.getRoute().getVessel().getFreeCapacity() - reeferCount);
            System.out
                    .println("ScheduleService.updateFreeCapacity() - Vessel " +voyage.getRoute().getVessel().getName()+ " Updated Free Capacity "
                            + voyage.getRoute().getVessel().getFreeCapacity());
            return voyage.getRoute().getVessel().getFreeCapacity();
        }
        throw new ShipCapacityExceeded(
                "VoyageID:" + voyageId + " Unable to book ship due to lack of capacity. Current capacity:"
                        + voyage.getRoute().getVessel().getFreeCapacity() + " Order reefer count:" + reeferCount);
    }
    class SchedulerComp implements Comparator<Voyage>{
 
        @Override
        public int compare(Voyage v1, Voyage v2) {
            if ( v1.getSailDateObject().isAfter(v2.getSailDateObject()) ) {
                return 1;
            } else {
                return -1;
            }
        }
    }
}