package com.ibm.research.kar.reeferserver.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Ship {
    private static transient AtomicLong idGen = new AtomicLong(1000);
    private String id;
    private String name;
    private String position;
    private int maxCapacity;
    private int freeCapacity;
    private String status;
    private List<Reefer> reefers = new ArrayList<>();
    
    public Ship(String name, String position, int maxCapacity, int freeCapacity, String status) {
        this.id = String.valueOf(idGen.addAndGet(1));
        this.name = name;
        this.position = position;
        this.maxCapacity = maxCapacity;
        this.freeCapacity = freeCapacity;
        this.status = status;
    }

    public static AtomicLong getIdGen() {
        return idGen;
    }

    public static void setIdGen(AtomicLong idGen) {
        Ship.idGen = idGen;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}