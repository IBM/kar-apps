package com.ibm.research.reefer.simulator;

public class FutureVoyage {
	int daysBefore;
	int maxCapacity;
	int freeCapacity;

	public FutureVoyage(int db, int mc, int fc) {
		daysBefore = db;
		maxCapacity = mc;
		freeCapacity = fc;
	}

    @Override
    public String toString(){
      return " daysBefore="+daysBefore+" maxCapacity="+maxCapacity+" freeCapacity="+freeCapacity+"\n";
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

	public int getDaysBefore() {
		return daysBefore;
	}
	public int getMaxCapacity() {
		return maxCapacity;
	}
	public int getFreeCapacity() {
		return freeCapacity;
	}

}
