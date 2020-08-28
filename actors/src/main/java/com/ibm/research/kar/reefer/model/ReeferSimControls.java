package com.ibm.research.kar.reefer.model;

public class ReeferSimControls {
    private int failureRate;
    private int updateFrequency;
    public ReeferSimControls(int failureRate, int updateFrequency) {
        this.failureRate = failureRate;
        this.updateFrequency = updateFrequency;
    }
    
    public int getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(int failureRate) {
        this.failureRate = failureRate;
    }

    public int getUpdateFrequency() {
        return updateFrequency;
    }

    public void setUpdateFrequency(int updateFrequency) {
        this.updateFrequency = updateFrequency;
    }


    
}