package com.ibm.research.kar.reefer.model;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractOrderList {
    List<Order> orderList = new ArrayList<>();

    public AbstractOrderList(List<Order> orders) {
        this.orderList = orders;
    }

    public List<Order> getActiveOrders() {
        return orderList;
    }

    public void setActiveOrders(List<Order> orders) {
        this.orderList = orders;
    }
    
}
