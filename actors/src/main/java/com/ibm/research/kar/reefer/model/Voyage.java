package com.ibm.research.kar.reefer.model;

import java.time.Instant;

public class Voyage {
    
    private String id;
    private Route route;

    private Instant sailDateObject;
    private String sailDate;
    private String arrivalDate;
    private String displayArrivalDate;
    private int orderCount=0;
    
    public Voyage(Route route, Instant sailDateObject, String arrivalDate) {
        this.route = route;
        this.sailDateObject = sailDateObject;
        this.arrivalDate = arrivalDate;
        this.displayArrivalDate = arrivalDate.substring(0,10);
        this.sailDate = sailDateObject.toString().substring(0,10);
        this.id = String.format("%s-%s",route.getVessel().getName(),this.sailDateObject.toString()).replaceAll("/","-");
        System.out.println("Voyage.ctor() - voyage:"+id+" From:"+route.getOriginPort()+" To:"+route.getDestinationPort()+" SailDate:"+sailDate+" ArrivalDate:"+arrivalDate+" DaysAtSea:"+route.getDaysAtSea());

    }
    public String getId() {
        return id;
    }
    public Route getRoute() {
        return route;
    }
    public String getSailDate() {
        return sailDate;
    }
    public String getArrivalDate() {
        return arrivalDate;
    }
    public Instant getSailDateObject() {
        return sailDateObject;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(int orderCount) {
        this.orderCount = orderCount;
    }

    public String getDisplayArrivalDate() {
        return displayArrivalDate;
    }

    
 
    
}