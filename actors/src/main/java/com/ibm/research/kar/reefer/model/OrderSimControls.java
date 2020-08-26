package com.ibm.research.kar.reefer.model;

public class OrderSimControls {
    int target;
    int window;
    int updateTarget;

    public OrderSimControls(int target, int window, int updateTarget) {
        this.target = target;
        this.window = window;
        this.updateTarget = updateTarget;
    }
    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public int getWindow() {
        return window;
    }

    public void setWindow(int window) {
        this.window = window;
    }

    public int getUpdateTarget() {
        return updateTarget;
    }

    public void setUpdateTarget(int updateTarget) {
        this.updateTarget = updateTarget;
    }



}