package com.ibm.research.kar.reeferserver.model;

public class ReeferSupply {
    
    String port;
    int howMany;

    public ReeferSupply(String port, int howMany) {
        this.port = port;
        this.howMany = howMany;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public int getHowMany() {
        return howMany;
    }

    public void setHowMany(int howMany) {
        this.howMany = howMany;
    }
}