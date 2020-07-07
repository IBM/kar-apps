package com.ibm.research.kar.reeferserver.scheduler;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.DeserializationFeature;

import com.ibm.research.kar.reeferserver.model.*;
import com.ibm.research.kar.reeferserver.ReeferServerApplication;

public class ShippingScheduler {
    private LinkedList<Voyage> sortedSchedule = new LinkedList<Voyage>();
    private List<Route> routes = new ArrayList<Route>();
    /*
    private List<Route> routes = new ArrayList<Route>() {
        {
           add( new Route( new Ship("Abyss", ":", 500, 500, "AtPort"), "Elizabeth, NJ", "London, GB", 14, 2 ) );
           add( new Route( new Ship("Atlantis", ":", 200, 200, "AtPort"), "Oakland, CA", "Shanghai, CN", 19, 2 ) );
           add( new Route( new Ship("Black Pearl", ":", 150, 150, "AtPort"), "Miami, FL", "Bremen, DE", 13, 2 ) );
           add( new Route( new Ship("Santa Maria", ":", 225, 225, "AtPort"),"Boston, MA", "London, GB", 12, 2 ) );
           add( new Route( new Ship("Andrea Gail", ":", 100, 100, "AtPort"),"Elizabeth, NJ", "London, GB", 14, 2) );
           add( new Route( new Ship("Victoria", ":", 250, 250, "AtPort"),"Elizabeth, NJ", "London, GB", 12, 2 ) );
           add( new Route( new Ship("Trinidad", ":", 500, 500, "AtPort"), "Elizabeth, NJ", "London, GB", 13, 2 ) );
        }
    };
*/
    public void initialize(File routeConfigFile) throws Exception {

            // create object mapper instance
            ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);;
            TypeFactory typeFactory = mapper.getTypeFactory();
            CollectionType collectionType = typeFactory.constructCollectionType(
                                                List.class, Route[].class);
            Route[] r = mapper.readValue(routeConfigFile,Route[].class);
            System.out.println("................ Found "+r.length +" routes");
            routes = Arrays.asList(r);
            for( Route route: routes) {
                System.out.println("................Origin Port: "+route.getOriginPort()+" Ship:"+route.getVessel().getName()+" Ship Capacity:"+route.getVessel().getMaxCapacity());
            }
            
 
    }
    public List<Voyage> generateSchedule() {
        return generateSchedule(ReeferServerApplication.getCurrentDate());
    }
    public List<Voyage> generateSchedule(Date departureDate) {
       // List<Route> schedule = new ArrayList<Route>();
     
        Date arrivalDate;
        // the shipping schedule is generated for one year from now
        int daysInCurrentYear = LocalDate.now().lengthOfYear();
        Date yearFromNow = getDate(departureDate, daysInCurrentYear);
        
        int staggerInitialShipDepartures=0;

        for( Route route: routes ) {
            System.out.println("ScheduleGenerator new route - from:"+route.getOriginPort()+" To:"+route.getDestinationPort());
                // generate current ship schedule for the whole year 
                while( departureDate.compareTo(yearFromNow) < 0 ) {
                    // get the ship arrival date at destination port (departureDate+transitTime)
                    arrivalDate = getDate(departureDate, route.getDaysAtSea());
                    // add voyage to a sorted (by departure date) schedule
                    addVoyageToSchedule(route, departureDate, false);
                    // the ship returns back to origin port after it is unloaded and loaded up again
                    departureDate = getDate(arrivalDate, route.getDaysAtPort());
                    // add return voyage to a sorted (by departure date) schedule
                    addVoyageToSchedule(route, departureDate, true);
                    // calculate departure date for next voyage from origin to destination
                    departureDate = getDate(departureDate, route.getDaysAtSea()+route.getDaysAtPort());
                }
                // initial ship departures staggered by 2 days (change this if necessary)
                staggerInitialShipDepartures += 2;
                // reset departure date to today+stagger (calculated above) so that the ships
                // dont depart on the same day
                departureDate = getDate(new Date(), staggerInitialShipDepartures);
                
        }
        System.out.println("ScheduleGenerator - generated:"+sortedSchedule.size()+" Voyages");
        return new ArrayList<Voyage>(sortedSchedule);
    }
    private void addVoyageToSchedule( Route route, Date departureDate, boolean returnVoyage) {
 //       if ( sortedSchedule.size() > 0 ) {
            int next=0;
            while(next < sortedSchedule.size()) {
                Voyage voyage = sortedSchedule.get(next);
                if (voyage.getSailDate().compareTo(departureDate) < 0 ) {
                    next++;
                } else {
                    break;
                }
            }
            sortedSchedule.add(next, newScheduledVoyage(route,departureDate,returnVoyage));
        //}
    }
    private Voyage newScheduledVoyage( Route route,Date departureDate, boolean returnVoyage ) {
        Voyage voyage;

        if ( returnVoyage ) {
            // for return voyage reverse origin and destination ports
            voyage = new Voyage( new Route(route.getVessel(), route.getDestinationPort(), route.getOriginPort(), route.getDaysAtSea(), route.getDaysAtPort()), departureDate);
        } else {
            voyage = new Voyage( route, departureDate);
        }
        return voyage;
    }
    private static final Date getDate( Date date, int days) {
	        
        Calendar cal = Calendar.getInstance(); 
        cal.setTime(date  ); 
        cal.add(Calendar.DATE, days);
        return cal.getTime();
    }

}