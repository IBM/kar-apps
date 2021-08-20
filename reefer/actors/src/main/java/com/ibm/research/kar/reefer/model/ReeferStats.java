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

    @Override
    public String toString() {
        return "ReeferStats{" +
                "total=" + total +
                ", totalInTransit=" + totalInTransit +
                ", totalBooked=" + totalBooked +
                ", totalSpoilt=" + totalSpoilt +
                ", totalOnMaintenance=" + totalOnMaintenance +
                '}';
    }
}