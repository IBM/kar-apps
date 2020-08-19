package com.ibm.research.kar.reefer.model;

public class OrderProperties {
    String orderId;  
    String customerId;
  String product;
  int productQty;
  String originPort;
  String destinationPort;
  String voyageId;
  String date;

  public OrderProperties() {}

  public OrderProperties(String product, int productQty, String voyageId) {
      this.product = product;
      this.productQty = productQty;
      this.voyageId = voyageId;
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

  public String getOriginPort() {
      return originPort;
  }

  public void setOriginPort(String originPort) {
      this.originPort = originPort;
  }

  public String getDestinationPort() {
      return destinationPort;
  }

  public void setDestinationPort(String destinationPort) {
      this.destinationPort = destinationPort;
  }

  public String getOrderId() {
      return orderId;
  }

  public void setOrderId(String orderId) {
      this.orderId = orderId;
  }

  public String getDate() {
      return date;
  }

  public void setDate(String date) {
      this.date = date;
  }

  public String getCustomerId() {
      return customerId;
  }

  public void setCustomerId(String customerId) {
      this.customerId = customerId;
  }
}