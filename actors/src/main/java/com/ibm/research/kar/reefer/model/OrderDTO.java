package com.ibm.research.kar.reefer.model;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "order")
public class OrderDTO implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = -4976477400101042541L;
    @Id
    private String id;
    private String customerId;
    private String voyageId;
    private String status;
    private String product;
    private int productQty;
    public OrderDTO() {}
    public OrderDTO(String id, String customerId, String voyageId, String status, String product, int productQty) {
        this.id = id;
        this.customerId = customerId;
        this.voyageId = voyageId;
        this.status = status;
        this.product = product;
        this.productQty = productQty;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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
    

}