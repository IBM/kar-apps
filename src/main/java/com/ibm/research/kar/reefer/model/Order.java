package com.ibm.research.kar.reefer.model;

import javax.json.JsonObject;

public class Order {
    public static final String OrderKey = "order";
    public static final String OrderBookingKey = "booking";
    public static final String IdKey = "orderId";
    public static final String VoyageIdKey = "orderVoyageId";
    public static final String ProductQtyKey = "orderProductQty";
    private JsonObject order;

    public Order(JsonObject order) {
        this.order = order; 
    }

    public String getId() {
        return order.getString(IdKey);
    }

    public boolean containsKey(String key) {
        return order.containsKey(key);
    }
    public String getVoyageId() {
        return order.getString(VoyageIdKey);
    }
    public int getProductQty() {
        return order.getInt(ProductQtyKey);
    }
    public JsonObject getAsObject() {
        return order;
    }
}