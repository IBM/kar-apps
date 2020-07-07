package com.ibm.research.kar.reeferserver.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class Voyage {
    private transient final SimpleDateFormat dateFormat 
        = new SimpleDateFormat("MM/dd/yyyy");
    private static transient AtomicLong idGen = new AtomicLong(1000);
    private String id;
    private Route route;
    /*
    private String originPort;
    private String destinationPort;
    private Ship ship;
    */
    // skip this field when serializing into json
    private transient Date sailDateObject;
    private String sailDate;
    /*
    private int daysAtSea;
    private int daysAtPort;

    public Voyage(Ship ship, String originPort, String destinationPort, Date sailDateObject, int daysAtSea, int daysAtPort) {
        this.ship = ship;
        this.originPort = originPort;
        this.destinationPort = destinationPort;
        this.sailDateObject = sailDateObject;
        this.sailDate = dateFormat.format(v.getSailDate(sailDateObject));
        this.daysAtSea = daysAtSea;
        this.daysAtPort = daysAtPort;
    }
    
    public Voyage(Route route, String sailDate) {
        this.route = route;
        this.sailDate = sailDate;
        this.id = String.valueOf(idGen.addAndGet(1));
    }
    */
    public Voyage(Route route, Date sailDate) {
        this.route = route;
        this.sailDateObject = sailDate;
        this.sailDate = dateFormat.format(sailDateObject);
        this.id = String.valueOf(idGen.addAndGet(1));
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
    
    /*
    public String getOriginPort() {
        return originPort;
    }

 
    public String getDestinationPort() {
        return destinationPort;
    }

 

    public int getDaysAtSea() {
        return daysAtSea;
    }

    public Ship getShip() {
        return ship;
    }


    public int getDaysAtPort() {
        return daysAtPort;
    }

 */

    
}