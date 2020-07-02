package com.ibm.research.kar.reeferserver.model;

import java.util.concurrent.atomic.AtomicLong;

public class Order {
    private static final AtomicLong idGenerator = new AtomicLong();
    String orderId;
    String product;
    int productQty;
    String voyageId;
    String status;
    String reeferIds;
    public Order(OrderProperties orderProperties) {
        this.orderId = String.valueOf(idGenerator.addAndGet(1));
        this.product = orderProperties.getProduct();
        this.productQty = orderProperties.getProductQty();
        this.voyageId = orderProperties.getVoyageId();
        this.status = "Pending";
        
    }

    public Order(String orderId, String product, int productQty, String voyageId, String status, String reeferIds) {
        this.orderId = orderId;
        this.product = product;
        this.productQty = productQty;
        this.voyageId = voyageId;
        this.status = status;
        this.reeferIds = reeferIds;
    }

    public String getOrderId() {
        return orderId;
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

    public String getReeferIds() {
        return reeferIds;
    }

    public void setReeferIds(String reeferIds) {
        this.reeferIds = reeferIds;
    }
}