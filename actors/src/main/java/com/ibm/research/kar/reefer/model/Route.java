
package com.ibm.research.kar.reefer.model;
import java.time.Instant;
public class Route {
    private Ship vessel;
    private String originPort;
    private String destinationPort;
    private int daysAtSea;
    private int daysAtPort;
    private Instant lastArrival;
    public Route() {
        super();
    }
    public Route(Ship vessel, String originPort, String destinationPort, int daysAtSea, int daysAtPort) {
        this.vessel = vessel;
        this.originPort = originPort;
        this.destinationPort = destinationPort;
        this.daysAtSea = daysAtSea;
        this.daysAtPort = daysAtPort;
    }
    public void setLastArrival(Instant date) {
        this.lastArrival = date;
    }
    public Instant getLastArrival() {
        return this.lastArrival;
    }
    public Ship getVessel() {
        return vessel;
    }
    public String getOriginPort() {
        return originPort;
    }


    public String getDestinationPort() {
        return destinationPort;
    }

 
    public int getDaysAtSea() {
        return daysAtSea;
    }


    public int getDaysAtPort() {
        return daysAtPort;
    }

    public void setVessel(Ship vessel) {
        this.vessel = vessel;
    }

    public void setOriginPort(String originPort) {
        this.originPort = originPort;
    }

    public void setDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort;
    }

    public void setDaysAtSea(int daysAtSea) {
        this.daysAtSea = daysAtSea;
    }

    public void setDaysAtPort(int daysAtPort) {
        this.daysAtPort = daysAtPort;
    }

 
}