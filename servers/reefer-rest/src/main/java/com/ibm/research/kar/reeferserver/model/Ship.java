package com.ibm.research.kar.reeferserver.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Ship {
    private String id;
    private String name;
    private String position;
    private int maxCapacity;
    private int freeCapacity;
    private String location;
    private List<Reefer> reefers = new ArrayList<>();
    public Ship() {
        super();
    }
    public Ship(String name, String position, int maxCapacity, int freeCapacity, String location) {
        this.id = this.name = name;
        this.position = position;
        this.maxCapacity = maxCapacity;
        this.freeCapacity = freeCapacity;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.id = this.name = name;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
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
}