package com.ibm.research.kar.reeferserver.service;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

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
        Date currentDate = ReeferServerApplication.getCurrentDate();
        List<Voyage> activeSchedule = new ArrayList<>();

        for( Voyage voyage : masterSchedule ) {
            if (voyage.getSailDate().compareTo(currentDate) <= 0 &&
                voyage.getSailDate().compareTo(currentDate) >= 0 ) {

            }
        }
        return scheduler.generateSchedule();
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