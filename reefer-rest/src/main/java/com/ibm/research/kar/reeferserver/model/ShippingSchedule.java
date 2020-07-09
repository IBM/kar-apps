package com.ibm.research.kar.reeferserver.model;
import java.io.Serializable;
import java.util.List;
import com.ibm.research.kar.reefer.common.*;

public class ShippingSchedule implements Serializable {
    private static final long serialVersionUID = 1366354424038297589L;
    private List<Route> schedule;

    public ShippingSchedule() {
        super();
    //    ReeferAllocator allocator;
    }
    public List<Route> getSchedule() { 
        return schedule; 
    } 
    public void setSchedule(List<Route> schedule) { 
        this.schedule = schedule; 
    }

 }