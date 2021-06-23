/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
