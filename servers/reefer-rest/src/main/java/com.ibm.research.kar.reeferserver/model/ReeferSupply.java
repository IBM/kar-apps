package com.ibm.research.kar.reeferserver.model;

public class ReeferSupply {
    
    String port;
    int reeferInventoryCount;

    public ReeferSupply(String port, int howMany) {
        this.port = port;
        this.reeferInventoryCount = howMany;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public int getReeferInventoryCount() {
        return reeferInventoryCount;
    }

    public void setReeferInventoryCount(int howMany) {
        this.reeferInventoryCount = howMany;
    }
}