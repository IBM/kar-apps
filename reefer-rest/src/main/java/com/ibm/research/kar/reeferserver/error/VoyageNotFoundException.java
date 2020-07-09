package com.ibm.research.kar.reeferserver.error;

public class VoyageNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public VoyageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    public VoyageNotFoundException(String message) {
        super(message);
    }
}