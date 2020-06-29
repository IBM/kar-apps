package com.ibm.research.kar.reeferserver.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.ibm.research.kar.reeferserver.model.ShippingSchedule;

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
    private static final String getDate( int days) {
        
        Calendar cal = Calendar.getInstance(); 
        cal.setTime(new Date()  ); 
        cal.add(Calendar.DATE, days);

    return dateFormat.format(cal.getTime());
    
    }
    public List<ShippingSchedule> get() {
        return shippingSchedule;
    }
}