package com.ibm.research.kar.reeferserver.model;

import java.time.Instant;

public class Voyage {
    private String id;
    private Route route;

    private Instant sailDateObject;
    private String sailDate;
    
    public Voyage(Route route, Instant sailDateObject) {
        this.route = route;
        this.sailDateObject = sailDateObject;
        this.sailDate = sailDateObject.toString();
        this.id = String.format("%s-%s",route.getVessel().getName(),this.sailDate).replaceAll("/","-");
    }
    public String getId() {
        return id;
    }
    public Route getRoute() {
        return route;
    }
    public Instant getSailDate() {
        return sailDateObject;
    }
    
    public String getSailDateAsString() {
        return sailDate;
    }
 
    
}