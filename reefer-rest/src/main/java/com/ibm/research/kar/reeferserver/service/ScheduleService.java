package com.ibm.research.kar.reeferserver.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reeferserver.model.*;
import com.ibm.research.kar.reeferserver.scheduler.*;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;

import com.ibm.research.kar.reefer.common.time.TimeUtils;
@Component
public class ScheduleService {
    @Value("classpath:routes.json")
    Resource resourceFile;
    ShippingScheduler scheduler = new ShippingScheduler();
    
    private LinkedList<Voyage> masterSchedule = new LinkedList<Voyage>();

    public List<Route> getRoutes() {
        List<Route> routes = new ArrayList<>();
        try {
            routes = scheduler.getRoutes(resourceFile.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return routes;

    }

    /*
        Returns voyages with ships currently at sea.
    */
    public List<Voyage> getActiveSchedule() {
        Instant currentDate = TimeUtils.getInstance().getCurrentDate();
        List<Voyage> activeSchedule = new ArrayList<>();
        if ( masterSchedule.isEmpty() ) {
            try {
                scheduler.initialize(resourceFile.getInputStream());
                masterSchedule =scheduler.generateSchedule();
            } catch( Exception e) {
                // !!!!!!!!!!!!!!!!!!!!!! HANDLE THIS
                e.printStackTrace();
            }
        }
        for( Voyage voyage : masterSchedule ) {
            Instant arrivalDate = 
              TimeUtils.getInstance().futureDate(voyage.getSailDate(), voyage.getRoute().getDaysAtSea()+voyage.getRoute().getDaysAtPort());
            System.out.println("getActiveSchedule() - CurrentDate: "+currentDate.toString()+ " Voyage "+voyage.getId()+
            " SailDate: "+
            voyage.getSailDateAsString()+" ArrivalDate: "+arrivalDate.toString()); 
            if ( voyage.getSailDate().isAfter(currentDate) ) {
                // masterSchedule is sorted by sailDate, so if voyage sailDate > currentDate
                // we just stop iterating since all voyagaes sail in the future.
                break;
            }

            // find active voyage which is one that started before current date and
            // has not yet completed
            if (TimeUtils.getInstance().isSameDay(voyage.getSailDate(), currentDate) ||
                (voyage.getSailDate().isBefore(currentDate) &&
                arrivalDate.isAfter(currentDate) ) ) {
                activeSchedule.add(voyage);
            }
        }
        return activeSchedule;
   }

    public List<Voyage> get() {
         try {
            scheduler.initialize(resourceFile.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }

        LinkedList<Voyage> sortedSchedule = scheduler.generateSchedule();
        if ( masterSchedule.size() == 0 ) {
            masterSchedule.addAll(sortedSchedule);
        } else {
            Voyage lastVoyageFromMasterSchedule = masterSchedule.getLast();
            
            for ( Voyage voyage : sortedSchedule ) {
                if ( voyage.getSailDate().isAfter(lastVoyageFromMasterSchedule.getSailDate())) {
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

    
}