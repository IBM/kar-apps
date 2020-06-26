package com.ibm.research.kar.reefer.model;

public class Reefer {
    private int maxCapacity;
    private int remainingCapacity;

    public Reefer(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public int getRemainingCapacity() {
        return remainingCapacity;
    }

    public void setRemainingCapacity(int remainingCapacity) {
        this.remainingCapacity = remainingCapacity;
    }
}