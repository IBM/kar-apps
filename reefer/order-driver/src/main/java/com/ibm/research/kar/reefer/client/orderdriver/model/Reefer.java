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

package com.ibm.research.kar.reefer.client.orderdriver.model;

import java.util.UUID;

public class Reefer {
    //private static final AtomicLong idGen = new AtomicLong(5555);
    private String reeferId;
    private String port;
    private int maxCapacity;
    private int freeCapacity;
    private String status;
    private String loadingStatus;
    private String position;

    public Reefer(String port, int maxCapacity, int freeCapacity, String status, String loadingStatus, String position) {
        this.port = port;
        this.reeferId = UUID.randomUUID().toString(); //String.valueOf(idGen.addAndGet(1));
        this.maxCapacity = maxCapacity;
        this.freeCapacity = freeCapacity;
        this.status = status;
        this.loadingStatus = loadingStatus;
        this.position = position;
    }

    public String getReeferId() {
        return reeferId;
    }

    public void setReeferId(String reeferId) {
        this.reeferId = reeferId;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public int getFreeCapacity() {
        return freeCapacity;
    }

    public void setFreeCapacity(int freeCapacity) {
        this.freeCapacity = freeCapacity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLoadingStatus() {
        return loadingStatus;
    }

    public void setLoadingStatus(String loadingStatus) {
        this.loadingStatus = loadingStatus;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }
}