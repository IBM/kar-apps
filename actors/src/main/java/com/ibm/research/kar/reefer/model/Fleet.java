package com.ibm.research.kar.reefer.model;

import java.util.List;

public class Fleet {
    String name;
    List<Ship> ships;

    public Fleet(String name, List<Ship> ships) {
        this.name = name;
        this.ships = ships;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Ship> getShips() {
        return ships;
    }

    public void addShips(List<Ship> ships) {
        this.ships.addAll(ships);
    }
    public void addShip(Ship ship) {
        this.ships.add(ship);
    } 
}