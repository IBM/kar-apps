package com.ibm.research.kar.reefer.model;

public class OrderStats {
    int inTransitOrderCount;
    int futureOrderCount;
    int spoiltOrderCount;

    public OrderStats(int inTransitOrderCount, int futureOrderCount, int spoiltOrderCount) {
        this.inTransitOrderCount = inTransitOrderCount;
        this.futureOrderCount = futureOrderCount;
        this.spoiltOrderCount = spoiltOrderCount;
    }

    public int getInTransitOrderCount() {
        return inTransitOrderCount;
    }

    public void setInTransitOrderCount(int inTransitOrderCount) {
        this.inTransitOrderCount = inTransitOrderCount;
    }

    public int getFutureOrderCount() {
        return futureOrderCount;
    }

    public void setFutureOrderCount(int futureOrderCount) {
        this.futureOrderCount = futureOrderCount;
    }

    public int getSpoiltOrderCount() {
        return spoiltOrderCount;
    }

    public void setSpoiltOrderCount(int spoiltOrderCount) {
        this.spoiltOrderCount = spoiltOrderCount;
    }

    
    
}