package com.ibm.research.kar.reeferserver.service;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import javax.xml.crypto.Data;

import com.ibm.research.kar.reeferserver.model.*;
import com.ibm.research.kar.reeferserver.scheduler.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import com.ibm.research.kar.reeferserver.ReeferServerApplication;
@Component
public class ScheduleService {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
    private static TimeZone timeZone = TimeZone.getTimeZone("UTC");
    private static Calendar calendar = Calendar.getInstance(timeZone);
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
    private Date futureDate(Date date, final int daysIntoTheFuture) {
        calendar.setTime(date ); 
		calendar.add(Calendar.DATE, daysIntoTheFuture);
		return calendar.getTime();
	}
    /*
        Returns voyages with ships currently at sea.
    */
    public List<Voyage> getActiveSchedule() {
        Date currentDate = ReeferServerApplication.getCurrentDate();
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
            if ( voyage.getSailDate().compareTo(currentDate) > 0 ) {
                // masterSchedule is sorted by sailDate, so if voyage sailDate > currentDate
                // we just stop iterating since all voyagaes sail in the future.
                break;
            }
            Date arrivalDate = futureDate(voyage.getSailDate(), voyage.getRoute().getDaysAtSea()+voyage.getRoute().getDaysAtPort());
            System.out.println("getActiveSchedule() - CurrentDate: "+currentDate.toString()+ " Voyage "+voyage.getId()+
            " SailDate: "+
            voyage.getSailDateAsString()+" ArrivalDate: "+arrivalDate.toString()); 
            // find active voyage which is one that started before current date and
            // has not yet completed
            if (voyage.getSailDate().compareTo(currentDate) <= 0 &&
                arrivalDate.compareTo(currentDate) >= 0 ) {
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
                if (lastVoyageFromMasterSchedule.getSailDate().compareTo(voyage.getSailDate()) <= 0) {
                    continue;
                } else {
                    masterSchedule.add(voyage);
                }
            }
        }
        
        return new ArrayList<Voyage>(sortedSchedule);
        //return schedule;
    }

    
}