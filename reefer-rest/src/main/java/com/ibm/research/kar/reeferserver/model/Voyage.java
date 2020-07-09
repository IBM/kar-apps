package com.ibm.research.kar.reeferserver.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class Voyage {
    private transient final SimpleDateFormat dateFormat 
        = new SimpleDateFormat("MM/dd/yyyy");
   private String id;
    private Route route;

    private transient Date sailDateObject;
    private String sailDate;
    
    public Voyage(Route route, Date sailDateObject) {
        this.route = route;
        this.sailDateObject = sailDateObject;
        this.sailDate = dateFormat.format(sailDateObject);
        this.id = String.format("%s-%s",route.getVessel().getName(),this.sailDate).replaceAll("/","-");
    }
    public String getId() {
        return id;
    }
    public Route getRoute() {
        return route;
    }
    public Date getSailDate() {
        return sailDateObject;
    }
    
    public String getSailDateAsString() {
        return sailDate;
    }
 
    
}