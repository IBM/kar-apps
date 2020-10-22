package com.ibm.research.kar.reefer.model;

import java.time.Instant;

import javax.json.JsonArray;
import javax.json.JsonObject;

public class JsonOrder {
    public static final String OrderKey = "order";
    public static final String OrderBookingKey = "booking";
    public static final String IdKey = "orderId";
    public static final String VoyageIdKey = "orderVoyageId";
    public static final String ProductQtyKey = "orderProductQty";
    public static final String OriginPortKey = "originPort";
    public static final String DestinationPortKey = "destinationPort";
    private JsonObject order;

    public JsonOrder(JsonObject order) {
        this.order = order; 
    }

    public String getId() {
        return order.getString(IdKey); //order.getString(IdKey);
    }

    public boolean containsKey(String key) {
        return order.containsKey(key);
    }
    public String getVoyageId() {
        return order.getString(VoyageIdKey);
    }
    public String getOriginPort() {
        return order.getString(OriginPortKey);
    }
    public String getDestinationPort() {
        return order.getString(DestinationPortKey);
    }
    public int getProductQty() {
        return order.getInt(ProductQtyKey);
    }
    public JsonObject getAsObject() {
        return order;
    }

}