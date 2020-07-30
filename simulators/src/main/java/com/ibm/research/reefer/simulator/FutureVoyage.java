package com.ibm.research.reefer.simulator;

public class FutureVoyage {
	int daysBefore;
	int maxCapacity;
	int freeCapacity;
	int orderCapacity;

	public FutureVoyage(int db, int mc, int fc, int oc) {
		daysBefore = db;
		maxCapacity = mc;
		freeCapacity = fc;
		orderCapacity = oc;
	}

    @Override
    public String toString(){
      return "daysBefore="+daysBefore+" maxCapacity="+maxCapacity+" freeCapacity="+freeCapacity
    		  +" orderCapacity="+orderCapacity+"\n";
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

}
