package com.ibm.research.kar.reeferserver.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;

import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reefer.model.*;
import com.ibm.research.kar.reeferserver.scheduler.*;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;

import com.ibm.research.kar.reefer.common.error.ShipCapacityExceeded;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
@Component
public class ScheduleService {
    @Value("classpath:routes.json")
    private Resource routesJsonResource;
    @Autowired
    private ShippingScheduler scheduler;
    
    private LinkedList<Voyage> masterSchedule = new LinkedList<Voyage>();

    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<>();
        try {
            routes = scheduler.getRoutes(routesJsonResource.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return routes;

    }
    
    public void updateDaysAtSea(String voyageId, int daysOutAtSea) {
        for( Voyage voyage : masterSchedule ) {
           // System.out.println("ScheduleService.updateDaysAtSea() - daysOutAtSea:"+daysOutAtSea);
            if ( voyage.getId().equals(voyageId)) {
                voyage.getRoute().getVessel().setPosition(daysOutAtSea);
                int progress = Math.round((daysOutAtSea/(float)voyage.getRoute().getDaysAtSea())*100);
                voyage.getRoute().getVessel().setProgress(progress);
                System.out.println("ScheduleService.updateDaysAtSea() - voyage:"+voyage.getId()+"daysOutAtSea:"+
                voyage.getRoute().getVessel().getPosition()+" Progress:"+
                voyage.getRoute().getVessel().getProgress());
                break;
            }
        }
    }
    public List<Voyage> getMatchingSchedule( Instant startDate, Instant endDate) {
        List<Voyage> schedule = new ArrayList<>();
        if ( masterSchedule.isEmpty() ) {
            try {
                scheduler.initialize(routesJsonResource.getInputStream());
                masterSchedule =scheduler.generateSchedule();
            } catch( Exception e) {
                // !!!!!!!!!!!!!!!!!!!!!! HANDLE THIS
                e.printStackTrace();
            }
        }
        for( Voyage voyage : masterSchedule ) {
            if ( (voyage.getSailDateObject().equals(startDate) || voyage.getSailDateObject().isAfter(startDate)) &&
                 (voyage.getSailDateObject().equals(endDate)  || voyage.getSailDateObject().isBefore(endDate) ) ) {
                    schedule.add(voyage);
            }
        }

        return schedule;
    }
    public List<Voyage> getMatchingSchedule(String origin, String destination, Instant date) {
        List<Voyage> schedule = new ArrayList<>();
        if ( masterSchedule.isEmpty() ) {
            try {
                System.out.println("getMatchingSchedule - Master Schedule is empty, generating new one");
                scheduler.initialize(routesJsonResource.getInputStream());
                masterSchedule =scheduler.generateSchedule();
            } catch( Exception e) {
                // !!!!!!!!!!!!!!!!!!!!!! HANDLE THIS
                e.printStackTrace();
            }
        }
        for( Voyage voyage : masterSchedule ) {
 
            if ( (voyage.getSailDateObject().equals(date) ||
                 voyage.getSailDateObject().isAfter(date)) &&
                 voyage.getRoute().getOriginPort().equals(origin) &&
                 voyage.getRoute().getDestinationPort().equals(destination) ) {
                    schedule.add(voyage);
                    System.out.println("getMatchingSchedule - Found voyage id:"+voyage.getId()+" Free Capacity:"+voyage.getRoute().getVessel().getFreeCapacity());
            }

        }

        return schedule;
    }
    /*
        Returns voyages with ships currently at sea.
    */
    public List<Voyage> getActiveSchedule() {
        Instant currentDate = TimeUtils.getInstance().getCurrentDate();
        List<Voyage> activeSchedule = new ArrayList<>();
        if ( masterSchedule.isEmpty() ) {
            try {
                scheduler.initialize(routesJsonResource.getInputStream());
                masterSchedule =scheduler.generateSchedule();
            } catch( Exception e) {
                // !!!!!!!!!!!!!!!!!!!!!! HANDLE THIS
                e.printStackTrace();
            }
        }
        for( Voyage voyage : masterSchedule ) {
            Instant arrivalDate = 
              TimeUtils.getInstance().futureDate(voyage.getSailDateObject(), voyage.getRoute().getDaysAtSea());

            if ( voyage.getSailDateObject().isAfter(currentDate) ) {
                // masterSchedule is sorted by sailDate, so if voyage sailDate > currentDate
                // we just stop iterating since all voyagaes sail in the future.
                break;
            }
            System.out.println("getActiveSchedule() - CurrentDate: "+currentDate.toString()+ " Voyage "+voyage.getId()+
            " SailDate: "+
            voyage.getSailDate()+" ArrivalDate: "+arrivalDate.toString()); 
            // find active voyage which is one that started before current date and
            // has not yet completed
            if (TimeUtils.getInstance().isSameDay(voyage.getSailDateObject(), currentDate) ||
                (voyage.getSailDateObject().isBefore(currentDate) &&
                arrivalDate.isAfter(currentDate) ) ) {
                //    long noOfDaysBetween = ChronoUnit.DAYS.between(voyage.getSailDateObject(), currentDate);

                //    voyage.getRoute().getVessel().setPosition(noOfDaysBetween);
                //    int progress = Math.round((noOfDaysBetween/(float)voyage.getRoute().getDaysAtSea())*100);
                //    voyage.getRoute().getVessel().setProgress(progress);
                activeSchedule.add(voyage);
            }
        }
        return activeSchedule;
   }

    public List<Voyage> get() {
         try {
            scheduler.initialize(routesJsonResource.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }

        LinkedList<Voyage> sortedSchedule = scheduler.generateSchedule();
        if ( masterSchedule.size() == 0 ) {
            masterSchedule.addAll(sortedSchedule);
        } else {
            Voyage lastVoyageFromMasterSchedule = masterSchedule.getLast();
            
            for ( Voyage voyage : sortedSchedule ) {
                if ( voyage.getSailDateObject().isAfter(lastVoyageFromMasterSchedule.getSailDateObject())) {
                    masterSchedule.add(voyage);
                }
            }
        }
        
        return new ArrayList<Voyage>(sortedSchedule);
    }
    public Voyage getVoyage(String voyageId) throws VoyageNotFoundException {
        for( Voyage voyage : masterSchedule ) {
            if ( voyage.getId().equals(voyageId)) {
                return voyage;
            }
        }
        throw new VoyageNotFoundException("Unable to find voyage with ID:"+voyageId);
    }

    public int updateFreeCapacity(String voyageId, int reeferCount) throws VoyageNotFoundException, ShipCapacityExceeded {
        Voyage voyage = getVoyage(voyageId);
        if ( voyage.getRoute().getVessel().getFreeCapacity() - reeferCount >= 0 ) {
            voyage.getRoute().getVessel().setFreeCapacity(voyage.getRoute().getVessel().getFreeCapacity()- reeferCount);
            return voyage.getRoute().getVessel().getFreeCapacity();
        }
        throw new ShipCapacityExceeded("VoyageID:"+voyageId+" Unable to book ship due to lack of capacity. Current capacity:"+voyage.getRoute().getVessel().getFreeCapacity()+" Order reefer count:"+reeferCount);
    }
    
}