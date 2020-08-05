package com.ibm.research.kar.reefer.model;

public class DelayTarget {
    private int delay;
    private int target;
    
    public DelayTarget(){}
    public DelayTarget(int delay, int target){
        this.delay = delay;
        this.target = target;
    }
 
    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

}