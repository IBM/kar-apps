package com.ibm.research.kar.reefer.model;

public class ReeferStats {
    int total;
    int totalInTransit;
    int totalBooked;
    int totalSpoilt;
    int totalOnMaintenance;
    public ReeferStats(int total, int totalInTransit, int totalBooked, int totalSpoilt, int totalOnMaintenance) {
        this.total = total;
        this.totalInTransit = totalInTransit;
        this.totalBooked = totalBooked;
        this.totalSpoilt = totalSpoilt;
        this.totalOnMaintenance = totalOnMaintenance;
    }
    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getTotalInTransit() {
        return totalInTransit;
    }

    public void setTotalInTransit(int totalInTransit) {
        this.totalInTransit = totalInTransit;
    }

    public int getTotalBooked() {
        return totalBooked;
    }

    public void setTotalBooked(int totalBooked) {
        this.totalBooked = totalBooked;
    }

    public int getTotalSpoilt() {
        return totalSpoilt;
    }

    public void setTotalSpoilt(int totalSpoilt) {
        this.totalSpoilt = totalSpoilt;
    }

    public int getTotalOnMaintenance() {
        return totalOnMaintenance;
    }

    public void setTotalOnMaintenance(int totalOnMaintenance) {
        this.totalOnMaintenance = totalOnMaintenance;
    }


}