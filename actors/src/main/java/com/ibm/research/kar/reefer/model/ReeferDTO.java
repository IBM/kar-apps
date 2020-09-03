package com.ibm.research.kar.reefer.model;

import com.ibm.research.kar.reefer.common.ReeferState.State;

public class ReeferDTO {
 //   public enum State {EMPTY, ALLOCATED, PARTIALLY_ALLOCATED, SPOILT, ON_MAINTENANCE};

    private int id;
    private State state;
    private String orderId;
    private String voyageId;

    public ReeferDTO(int id, State state, String orderId, String voyageId) {
        this.id = id;
        this.state = state;
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
    }


    public String getOrderId() {
        return orderId;
    }


    public String getVoyageId() {
        return voyageId;
    }





}