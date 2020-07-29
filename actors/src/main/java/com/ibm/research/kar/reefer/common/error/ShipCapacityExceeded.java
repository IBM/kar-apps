package com.ibm.research.kar.reefer.common.error;

public class ShipCapacityExceeded extends Exception {
    public ShipCapacityExceeded() {
        super();
    }
    public ShipCapacityExceeded(String message) {
        super(message);
    }
}