package com.ibm.research.kar.reefer.model;

import java.time.Instant;

import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferState.State;
import com.ibm.research.kar.reefer.common.time.TimeUtils;

public class ReeferDTO {

    private int id;
    private State state;
    private String orderId;
    private String voyageId;
    private String maintenanceReleaseDate;

    public ReeferDTO(int id, State state, String orderId, String voyageId) {
        this.id = id;
        setState(state);
        this.orderId = orderId;
        this.voyageId = voyageId;
    }

    public int getId() {
        return id;
    }


    public State getState() {
        return state;
    }
    public void setState(State state) {
        this.state = state;
        if ( State.MAINTENANCE.equals(state)) {
            maintenanceReleaseDate = TimeUtils.getInstance().futureDate( Instant.now(), Constants.REEFER_DAYS_ON_MAINTENANCE).toString();
           
        }
    }


    public String getOrderId() {
        return orderId;
    }


    public String getVoyageId() {
        return voyageId;
    }

    public String getMaintenanceReleaseDate() {
        return maintenanceReleaseDate;
    }

    public void setMaintenanceReleaseDate(String maintenanceReleaseDate) {
        this.maintenanceReleaseDate = maintenanceReleaseDate;
    }





}