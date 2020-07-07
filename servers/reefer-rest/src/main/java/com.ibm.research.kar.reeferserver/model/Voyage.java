package com.ibm.research.kar.reeferserver.model;

public class Voyage {
    private String originPort;
    private String destinationPort;
    private Ship ship;
    private int transitTimeInDays;
    private int unloadTimeInDays;

    public Voyage(Ship ship, String originPort, String destinationPort, int transitTimeInDays, int unloadTimeInDays) {
        this.ship = ship;
        this.originPort = originPort;
        this.destinationPort = destinationPort;
        this.transitTimeInDays = transitTimeInDays;
        this.unloadTimeInDays = unloadTimeInDays;
    }

    public String getOriginPort() {
        return originPort;
    }

 
    public String getDestinationPort() {
        return destinationPort;
    }

 

    public int getTransitTimeInDays() {
        return transitTimeInDays;
    }

    public Ship getShip() {
        return ship;
    }


    public int getUnloadTimeInDays() {
        return unloadTimeInDays;
    }

    
}