/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.kar.reefer.model;

import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.util.*;

public class Order {
    public enum OrderStatus { 
        PENDING("Pending"),
        BOOKED("Booked"), 
        INTRANSIT("InTransit"), 
        DELIVERED("Delivered"),
        SPOILT("Spoilt");
    
        private final String label;
        OrderStatus(String label) {
            this.label = label;
        }
        public String getLabel() {
            return label;
        }
    
    };

    String id;
    String correlationId;
    String customerId;
    String product;
    int productQty;
    String voyageId;
    String status;
    String date;
    boolean spoilt;
    String depot;
    String msg;

    public Order(OrderProperties orderProperties) {
        this(orderProperties.getCorrelationId(),orderProperties.getCustomerId(),orderProperties.getProduct(),
            orderProperties.getProductQty(),orderProperties.getVoyageId(),
            OrderStatus.PENDING.getLabel(),new ArrayList<>());
    }

    public Order(JsonValue jo ) {
        this(jo.asJsonObject());
    }
    public Order(JsonObject jo ) {
        this.id = jo.getString(Constants.ORDER_ID_KEY);
        this.correlationId = jo.getString(Constants.CORRELATION_ID_KEY);
        this.customerId = jo.getString(Constants.ORDER_CUSTOMER_ID_KEY);
        this.product = jo.getString(Constants.ORDER_PRODUCT_KEY);
        this.productQty = jo.getInt(Constants.ORDER_PRODUCT_QTY_KEY);
        this.voyageId = jo.getString(Constants.VOYAGE_ID_KEY);
        this.status = jo.getString(Constants.ORDER_STATUS_KEY);
        this.date = jo.getString(Constants.ORDER_DATE_KEY);
        this.spoilt = jo.getBoolean(Constants.ORDER_SPOILT_KEY);
        if ( jo.containsKey(Constants.DEPOT_KEY)) {
            this.depot = jo.getString(Constants.DEPOT_KEY);
        }
        if ( jo.containsKey(Constants.ORDER_MESSAGE_KEY)) {
            this.msg = jo.getString(Constants.ORDER_MESSAGE_KEY);
        }
    }

    public Order( String correlationId, String customerId, String product, int productQty, String voyageId, String status, List<String> reeferIds) {
        this.correlationId = correlationId;
        this.customerId = customerId;
        this.product = product;
        this.productQty = productQty;
        this.voyageId = voyageId;
        this.status = status;
        // the date is used for order sorting. The TimeUtils.getCurrentDate() not applicable as it advances
        // date one day at a time and we need millis resolution
        this.date = Instant.now().toString();
        this.spoilt = false;
    }

    public String generateOrderId() {
        synchronized(Order.class) {
            this.id = UUID.randomUUID().toString();
            return this.id;
        }
    }
    public void setMsg(String message) {
        this.msg = message;
    }
    public String getMsg() {
        return this.msg;
    }
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
    public String getCorrelationId() {
        return this.correlationId;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return id.equals(order.id) && voyageId.equals(order.voyageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, voyageId);
    }

    public String getDepot() {
        return  depot;
    }
    public void setDepot(String depot) {
        this.depot = depot;
    }
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public boolean isSpoilt() {
        return spoilt;
    }

    public void setSpoilt(boolean spoilt) {
        this.spoilt = spoilt;
    }

    public String getId() {
        return id;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public int getProductQty() {
        return productQty;
    }

    public void setProductQty(int productQty) {
        this.productQty = productQty;
    }

    public String getVoyageId() {
        return voyageId;
    }

    public void setVoyageId(String voyageId) {
        this.voyageId = voyageId;
    }

    public String getStatus() {
        return status.toUpperCase();
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public boolean isOriginSimulator() {
        return customerId != null && customerId.equals("simulator");
    }
    public JsonObject getAsJsonObject() {
        JsonObjectBuilder orderBuilder = Json.createObjectBuilder();
        orderBuilder.add(Constants.ORDER_ID_KEY, getId()).
                add(Constants.VOYAGE_ID_KEY, getVoyageId()).
                add(Constants.ORDER_PRODUCT_KEY, getProduct()).
                add(Constants.ORDER_PRODUCT_QTY_KEY, getProductQty()).
                add(Constants.ORDER_CUSTOMER_ID_KEY, getCustomerId()).
                add(Constants.ORDER_STATUS_KEY, getStatus()).
                add(Constants.ORDER_DATE_KEY, getDate()).
                add(Constants.ORDER_SPOILT_KEY, isSpoilt());
        if ( getDepot() != null) {
            orderBuilder.add(Constants.DEPOT_KEY, getDepot());
        }
        if ( correlationId != null) {
            orderBuilder.add(Constants.CORRELATION_ID_KEY, correlationId);
        }
        if ( msg != null) {
            orderBuilder.add(Constants.ORDER_MESSAGE_KEY, msg);
        }
        return orderBuilder.build();
    }
    public JsonObject getOrderParams() {
        JsonObject orderParams = this.getAsJsonObject();
        JsonObjectBuilder jsonOrderBuilder = Json.createObjectBuilder();
        jsonOrderBuilder.add("order", orderParams);
        return jsonOrderBuilder.build();
    }
    public static boolean booked(JsonObject jo) {
        return jo.getString(Constants.ORDER_STATUS_KEY).equals(OrderStatus.BOOKED.name());
    }
    public static boolean pending(JsonObject jo) {
        return jo.getString(Constants.ORDER_STATUS_KEY).equals(OrderStatus.PENDING.name());
    }
}