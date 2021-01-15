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
import java.time.Instant;
public class Route {
    private Ship vessel;
    private String originPort;
    private String destinationPort;
    private int daysAtSea;
    private int daysAtPort;
    private Instant lastArrival;
    public Route() {
        super();
    }
    public Route(Ship vessel, String originPort, String destinationPort, int daysAtSea, int daysAtPort) {
        this.vessel = vessel;
        this.originPort = originPort;
        this.destinationPort = destinationPort;
        this.daysAtSea = daysAtSea;
        this.daysAtPort = daysAtPort;
    }
    public void setLastArrival(Instant date) {
        this.lastArrival = date;
    }
    public Instant getLastArrival() {
        return this.lastArrival;
    }
    public Ship getVessel() {
        return vessel;
    }
    public String getOriginPort() {
        return originPort;
    }


    public String getDestinationPort() {
        return destinationPort;
    }

 
    public int getDaysAtSea() {
        return daysAtSea;
    }


    public int getDaysAtPort() {
        return daysAtPort;
    }

    public void setVessel(Ship vessel) {
        this.vessel = vessel;
    }

    public void setOriginPort(String originPort) {
        this.originPort = originPort;
    }

    public void setDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort;
    }

    public void setDaysAtSea(int daysAtSea) {
        this.daysAtSea = daysAtSea;
    }

    public void setDaysAtPort(int daysAtPort) {
        this.daysAtPort = daysAtPort;
    }

 
}