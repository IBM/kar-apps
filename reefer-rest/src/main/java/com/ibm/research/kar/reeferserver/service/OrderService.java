package com.ibm.research.kar.reeferserver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.Order;


import org.springframework.stereotype.Component;
@Component
public class OrderService {
    private Map<String, Order> orders = new HashMap<>();

    public List<Order> getOrders() {
        return new ArrayList<Order>(orders.values());
    }

    public void saveOrder(Order order) {
        orders.put(order.getId(), order);
    }
    public Order creatOrder(OrderProperties orderProperties) {
        Order order = 
            new Order(orderProperties);

        orders.put(order.getId(), order);
        return order;
    }


}