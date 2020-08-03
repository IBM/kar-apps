package com.ibm.research.kar.reefer.common.error;

public class RouteNotFoundException extends Exception {
    public RouteNotFoundException() {
        super();
    }
    public RouteNotFoundException(String message) {
        super(message);
    }
}