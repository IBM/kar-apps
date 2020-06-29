package com.ibm.research.kar.reeferserver.model;
import java.io.Serializable;
public class ShippingSchedule implements Serializable {
    private static final long serialVersionUID = 1366354424038297589L;
    String voyageId;
    String position;
    String name;
    String origin;
    String destination;
    String sailDate;
    int transitTime;
    int freeCapacity;

    public ShippingSchedule(String voyageId, String position, String name, String origin, String destination, String sailDate, int transitTime, int freeCapacity ) {
        this.voyageId = voyageId;
        this.position = position;
        this.name = name;
        this. origin = origin;
        this.destination = destination;
        this.sailDate = sailDate;
        this.transitTime = transitTime;
        this. freeCapacity = freeCapacity;
    }
    public String getVoyageId() {
        return voyageId;
    }
    public String getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

     public String getOrigin() {
        return origin;
    }

     public String getDestination() {
        return destination;
    }

    public String getSailDate() {
        return sailDate;
    }

     public int getTransitTime() {
        return transitTime;
    }

     public int getFreeCapacity() {
        return freeCapacity;
    }
}