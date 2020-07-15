package com.ibm.research.kar.reefer.model;

public class Port {
    String name;
    String position;
    int availableReefers;
    int reefersOnMaintenance;

    

    public String getName() {
        return name;
    }

  
    public String getPosition() {
        return position;
    }

    public Port(String name, String position, int availableReefers, int reefersOnMaintenance) {
        this.name = name;
        this.position = position;
        this.availableReefers = availableReefers;
        this.reefersOnMaintenance = reefersOnMaintenance;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public int getAvailableReefers() {
        return availableReefers;
    }

    public void setAvailableReefers(int availableReefers) {
        this.availableReefers = availableReefers;
    }
    public void adjustReeferCount(int howMany) {
        // when subtracting from reefer inventory make sure we dont go negative
        if ( howMany < 0 && this.availableReefers < howMany ) {
            this.availableReefers = 0;
        }
        this.availableReefers += howMany;
    }
    
    public int getReefersOnMaintenance() {
        return reefersOnMaintenance;
    }

    public void setReefersOnMaintenance(int reefersOnMaintenance) {
        this.reefersOnMaintenance = reefersOnMaintenance;
    }

}