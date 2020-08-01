package com.ibm.research.reefer.simulator;

public class FutureVoyage {
	int daysBefore;
	int maxCapacity;
	int freeCapacity;
	int orderCapacity;
	int utilization;

	public FutureVoyage(int db, int mc, int fc, int oc, int ut) {
		daysBefore = db;
		maxCapacity = mc;
		freeCapacity = fc;
		orderCapacity = oc;
		utilization = ut;
	}

    @Override
    public String toString(){
      return "daysBefore="+daysBefore+" maxCapacity="+maxCapacity+" freeCapacity="+freeCapacity
    		  +" orderCapacity="+orderCapacity+" utilization="+utilization+"\n";
    }

    public void setDaysBefore(int db) {
		daysBefore = db;
	}
	public void setMaxCapacity(int mc) {
		maxCapacity = mc;
	}
	public void setFreeCapacity(int fc) {
		freeCapacity = fc;
	}
	public void setOrderCapacity(int oc) {
		orderCapacity = oc;
	}
	public void setUtilization(int ut) {
		utilization = ut;
	}

	public int getDaysBefore() {
		return daysBefore;
	}
	public int getMaxCapacity() {
		return maxCapacity;
	}
	public int getFreeCapacity() {
		return freeCapacity;
	}
	public int getOrderCapacity() {
		return orderCapacity;
	}
	public int getUtilization() {
		return utilization;
	}

}
