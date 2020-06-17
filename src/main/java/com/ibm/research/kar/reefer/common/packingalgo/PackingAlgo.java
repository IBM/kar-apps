package com.ibm.research.kar.reefer.common.packingalgo;

import com.ibm.research.kar.reefer.common.ReeferState;

public interface PackingAlgo {
    public int pack(ReeferState reeferState, int productQuantity);
}