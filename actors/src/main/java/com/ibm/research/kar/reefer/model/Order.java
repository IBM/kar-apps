package com.ibm.research.kar.reefer.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
//import java.util.concurrent.atomic.AtomicLong;

public class Order {
   // private static final AtomicLong idGenerator = new AtomicLong();
    String id;
    String product;
    int productQty;
    String voyageId;
    String status;
    List<String> reeferIds;

    public Order(OrderProperties orderProperties) {
        this.id = generateId(); //String.valueOf(idGenerator.addAndGet(1));
        this.product = orderProperties.getProduct();
        this.productQty = orderProperties.getProductQty();
        this.voyageId = orderProperties.getVoyageId();
        this.status = "Pending";
        reeferIds = new ArrayList<>();
    }

//    public Order(String orderId, String product, int productQty, String voyageId, String status, List<String> reeferIds) {
    public Order( String product, int productQty, String voyageId, String status, List<String> reeferIds) {
        this.id = generateId();
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
}