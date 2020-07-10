package com.ibm.research.kar.reeferserver.model;
import java.io.Serializable;
import java.util.List;

public class ShippingSchedule implements Serializable {
    private static final long serialVersionUID = 1366354424038297589L;
    private List<Route> schedule;

    public ShippingSchedule() {
        super();
    }
    public List<Route> getSchedule() { 
        return schedule; 
    } 
    public void setSchedule(List<Route> schedule) { 
        this.schedule = schedule; 
    }

 }