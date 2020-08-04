package com.ibm.research.kar.reefer.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
//import java.util.concurrent.atomic.AtomicLong;

public class Order {
    public enum OrderStatus { 
        PENDING("Pending"),
        BOOKED("Booked"), 
        INTRANSIT("InTransit"), 
        DELIVERED("Delivered");
    
        private final String label;
        OrderStatus(String label) {
            this.label = label;
        }
        public String getLabel() {
            return label;
        }
    
    };

    String id;
    String customerId;
    String product;
    int productQty;
    String voyageId;
    String status;
    List<String> reeferIds;

    public Order(OrderProperties orderProperties) {
        this(orderProperties.getCustomerId(),orderProperties.getProduct(),
            orderProperties.getProductQty(),orderProperties.getVoyageId(),
            OrderStatus.PENDING.getLabel(),new ArrayList<>());
    }

    public Order( String customerId, String product, int productQty, String voyageId, String status, List<String> reeferIds) {
        this.id = generateId();
        this.customerId = customerId;
        this.product = product;
        this.productQty = productQty;
        this.voyageId = voyageId;
        this.status = status;
        this.reeferIds = reeferIds;
    }
    private String generateId() {
        return String.valueOf(Instant.now().toEpochMilli());
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
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getReeferIds() {
        return reeferIds;
    }

    public void setReeferIds(List<String> reeferIds) {
        this.reeferIds.addAll(reeferIds);
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}