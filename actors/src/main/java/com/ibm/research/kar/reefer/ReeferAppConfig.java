package com.ibm.research.kar.reefer;


public class ReeferAppConfig {
    //public static final String VoyageActor = "voyage";
    // size of each reefer in terms of product units. For
    // simplification, each product has the same size
    public static final int ReeferMaxCapacityValue = 1000;
    public static final int ReeferInventorySize=1000000;
    public static final String ReeferProvisionerId = "ReeferManager";

    public static final String ReeferActorName="reefer";
    public static final String ReeferProvisionerActorName="reefer-provisioner";
    public static final String VoyageActorName="voyage";
    public static final String OrderActorName="order";

    // If the reefer capacity is greater or equal 60, it is considered full (ie. ALLOCATED)
    public static final int CapacityThresholdFloor = 60;

    public static final String PackingAlgoStrategy = "simple";

}