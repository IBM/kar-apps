package com.ibm.research.kar.reeferserver.model;
import java.io.Serializable;
import java.util.List;

//import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;

public class ShippingSchedule implements Serializable {
    private static final long serialVersionUID = 1366354424038297589L;
    private List<Voyage> voyages;
    private String currentDate;

    public ShippingSchedule(List<Voyage> voyages, String currentDate) {
        super();
        this.voyages = voyages;
        this.currentDate = currentDate;
    }

    public List<Voyage> getVoyages() {
        return voyages;
    }

    public void setVoyages(List<Voyage> voyages) {
        this.voyages = voyages;
    }

    public String getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(String currentDate) {
        this.currentDate = currentDate;
    }

 }