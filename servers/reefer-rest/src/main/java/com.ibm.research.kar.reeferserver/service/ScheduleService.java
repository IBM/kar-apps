package com.ibm.research.kar.reeferserver.service;

import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import com.ibm.research.kar.reeferserver.model.*;
import com.ibm.research.kar.reeferserver.scheduler.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
@Component
public class ScheduleService {
    private static final SimpleDateFormat dateFormat 
    = new SimpleDateFormat("MM/dd/yyyy");

    @Value("classpath:routes.json")
    Resource resourceFile;
    private LinkedList<Voyage> sortedSchedule = new LinkedList<Voyage>();
/*
    private List<ShippingSchedule> shippingSchedule = 
    new ArrayList<ShippingSchedule>(){
        
        private static final long serialVersionUID = -698446158140971475L;

                {
        add( new ShippingSchedule("122", "12.333:36.444", "Abyss", "Elizabeth, NJ", "London, GB", getDate(1), 12, 200));
        add( new ShippingSchedule("100", "12.333:36.444","Atlantis", "Oakland, CA", "Shanghai, CN", getDate(2), 13, 300));
        add( new ShippingSchedule("2003", "12.333:36.444","Black Pearl", "Miami, FL", "Bremen, DE", getDate(4), 14, 400));
        add( new ShippingSchedule("170", "12.333:36.444","Santa Maria", "Boston, MA", "Antwerp, BE", getDate(7), 12, 500));
        add( new ShippingSchedule("55", "12.333:36.444","Andrea Gail", "Elizabeth, NJ", "London, GB", getDate(10), 15, 600));
        add( new ShippingSchedule("99", "12.333:36.444","Victoria", "Elizabeth, NJ", "London, GB", getDate(15), 14, 700));
        add( new ShippingSchedule("34", "12.333:36.444","Trinidad", "Elizabeth, NJ", "London, GB", getDate(20), 13, 800));
    }
};
*/
/*
    private List<Voyage> voyages = new ArrayList<Voyage>() {
        {
           add( new Voyage( new Ship("Abyss", ":", 500, 500, "AtPort"),"Elizabeth, NJ", "London, GB", 14, 2 ) );
           add( new Voyage( new Ship("Atlantis", ":", 200, 200, "AtPort"),"Oakland, CA", "Shanghai, CN", 19, 2 ) );
           add( new Voyage( new Ship("Black Pearl", ":", 150, 150, "AtPort"),"Miami, FL", "Bremen, DE", 13, 2 ) );
           add( new Voyage( new Ship("Santa Maria", ":", 225, 225, "AtPort"),"Boston, MA", "London, GB", 12, 2 ) );
           add( new Voyage( new Ship("Andrea Gail", ":", 100, 100, "AtPort"),"Elizabeth, NJ", "London, GB", 14, 2) );
           add( new Voyage( new Ship("Victoria", ":", 250, 250, "AtPort"),"Elizabeth, NJ", "London, GB", 12, 2 ) );
           add( new Voyage( new Ship("Trinidad", ":", 500, 500, "AtPort"),"Elizabeth, NJ", "London, GB", 13, 2 ) );
        }
    };
    */


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
    private static final String getDate( int days) {
        
        Calendar cal = Calendar.getInstance(); 
        cal.setTime(new Date()  ); 
        cal.add(Calendar.DATE, days);

    return dateFormat.format(cal.getTime());
    
    }
    /*
    public List<ShippingSchedule> get() {
        return shippingSchedule;
    }
*/
    public List<Voyage> get() {
        ShippingScheduler scheduler = new ShippingScheduler();
        try {
            Path file = ResourceUtils.getFile("../src/main/resources/routes.json").toPath();
            System.out.println(">>>>>>>>>>>>>> File: "+file.toString()+" Exists:"+resourceFile.exists());
            scheduler.initialize(new File("/home/cwiklik/dev/ibm/data/routes.json")); //"src/main/resources/routes.json");
        } catch( Exception e) {
            e.printStackTrace();
        }

        return scheduler.generateSchedule();
    //    return shippingSchedule;
    }

    private static final Date getDate( Date date, int days) {
	        
        Calendar cal = Calendar.getInstance(); 
        cal.setTime(date  ); 
        cal.add(Calendar.DATE, days);
                   return cal.getTime();
    }

    /*
    private List<ShippingSchedule> generateSchedules() {
        List<Voyage> schedule = new ArrayList<Voyage>();
        // get today's date
        Date departureDate = new Date();
        Date arrivalDate;
        // the shipping schedule is generated for one year from now
        int daysInCurrentYear = LocalDate.now().lengthOfYear();
        Date yearFromNow = getDate(departureDate, daysInCurrentYear);
        
        int staggerInitialShipDepartures=0;
        
        for( Route route: routes ) {
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
        return schedule;
    }
    private void addVoyageToSchedule( Route route, Date departureDate, boolean returnVoyage) {
        if ( sortedSchedule.size() > 0 ) {
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
        }
    }
    private Voyage newScheduledVoyage( Route route,Date departureDate, boolean returnVoyage ) {
        if ( returnVoyage ) {
            // for return voyage reverse origin and destination ports
            new Voyage( new Route(route.getShip(), route.getDestinationPort(), route.getOriginPort(), route.getDaysAtSea(), route.getDaysAtPort()), departureDate);
        } else {
            return new Voyage( route, departureDate);
        }
      */ 
        /*
        return new Voyage(String.valueOf(idGen.addAndGet(1)), ":", 
                v.getShip().getName(), 
                returnVoyage ? v.getDestinationPort() : v.getOriginPort(), 
                returnVoyage ? v.getOriginPort() : v.getDestinationPort(), 
                departureDate, 
                v.getTransitTimeInDays(),
                v.getShip().getFreeCapacity());
                */
 //   }


/*
    private static final Date getDate( Date date, int days) {
        
        Calendar cal = Calendar.getInstance(); 
        cal.setTime(date  ); 
        cal.add(Calendar.DATE, days);
        return cal.getTime();
    }
    private List<Voyage> generateSchedules() {
        List<ShippingSchedule> schedule = new ArrayList<ShippingSchedule>();
        Date departureDate = new Date();
        Date arrivalDate;
 
        for( Voyage v: voyages ) {
            for(int i=0; i < 365; i++ ) {
                arrivalDate = getDate(departureDate, v.getTransitTimeInDays());
                schedule.add(new ShippingSchedule(i, ":", v.getShip().getName(), v.getOriginPort(), v.getDestinationPort(), dateFormat.format(departure.getTime()), v.getTransitTimeInDays(), v.getShip().getFreeCapacity()));
                departureDate = getDate(arrivalDate, v.getUnloadTimeInDays());
    
            }
        }
        return schedule;
    }
*/



}