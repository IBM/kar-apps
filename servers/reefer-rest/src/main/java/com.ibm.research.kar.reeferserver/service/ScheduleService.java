package com.ibm.research.kar.reeferserver.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.ibm.research.kar.reeferserver.model.ShippingSchedule;
import com.ibm.research.kar.reeferserver.model.Voyage;
import com.ibm.research.kar.reeferserver.model.Ship;
import org.springframework.stereotype.Component;
@Component
public class ScheduleService {
    private static final SimpleDateFormat dateFormat 
	= new SimpleDateFormat("MM/dd/yyyy");
    private List<ShippingSchedule> shippingSchedule = 
    new ArrayList<ShippingSchedule>(){
        /**
         *
          */
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
    
    private static final String getDate( int days) {
        
        Calendar cal = Calendar.getInstance(); 
        cal.setTime(new Date()  ); 
        cal.add(Calendar.DATE, days);

    return dateFormat.format(cal.getTime());
    
    }
    public List<ShippingSchedule> get() {
        return shippingSchedule;
    }
    private static final Date getDate( Date date, int days) {
        
        Calendar cal = Calendar.getInstance(); 
        cal.setTime(date  ); 
        cal.add(Calendar.DATE, days);
        return cal.getTime();
    }
    private List<ShippingSchedule> generateSchedules() {
        List<ShippingSchedule> schedule = new ArrayList<ShippingSchedule>();
        Date departureDate = new Date();
        Date arrivalDate;
//    public ShippingSchedule(String voyageId, String position, String name, String origin, String destination, String sailDate, int transitTime, int freeCapacity ) {
 
        for( Voyage v: voyages ) {
            for(int i=0; i < 365; i++ ) {
                arrivalDate = getDate(departureDate, v.getTransitTimeInDays());
                schedule.add(new ShippingSchedule(i, ":", v.getShip().getName(), v.getOriginPort(), v.getDestinationPort(), dateFormat.format(departure.getTime()), v.getTransitTimeInDays(), v.getShip().getFreeCapacity()));
                departureDate = getDate(arrivalDate, v.getUnloadTimeInDays());
    
            }
        }
        return schedule;
    }
    public static void main(String[] args ) {
        ScheduleService ss = new ScheduleService();
        List<ShippingSchedule> schedule =
            ss.generateSchedules();
    }
}