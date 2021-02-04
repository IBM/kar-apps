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

import java.util.ArrayList;
import java.util.List;

public class Ship {
    public static final String VESSEL="vessel";
    public static final String VESSEL_ID="id";
    public static final String VESSEL_NAME="name";
    public static final String POSITION="position";
    public static final String PROGRESS="progress";
    public static final String MAX_CAPACITY="maxCapacity";
    public static final String FREE_CAPACITY="freeCapacity";
    public static final String LOCATION="location";
    public static final String REEFERS="reefers";

    private String id;
    private String name;
    private long position;
    private int progress;
    private int maxCapacity;
    private int freeCapacity;
    private String location;
    private List<Reefer> reefers = new ArrayList<>();
    public Ship() {
        super();
    }
    public Ship(String name, long position, int maxCapacity, int freeCapacity, String location) {
        this.id = this.name = name;
        this.position = position;
        this.maxCapacity = maxCapacity;
        this.freeCapacity = freeCapacity;
        this.location = location;
    }
    public Ship clone() {
        return new Ship(name, position, maxCapacity, freeCapacity, location);
    }
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.id = name;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }
    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<Reefer> getReefers() {
        return reefers;
    }

    public void addReefers(List<Reefer> reefers) {
        this.reefers.addAll(reefers);
    }
    public void addReefer(Reefer reefer) {
        this.reefers.add(reefer);
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setReefers(List<Reefer> reefers) {
        this.reefers = reefers;
    }

    @Override
    public String toString() {
        return "Ship{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", position=" + position +
                ", progress=" + progress +
                ", maxCapacity=" + maxCapacity +
                ", freeCapacity=" + freeCapacity +
                ", location='" + location + '\'' +
                ", reefers=" + reefers +
                '}';
    }
}