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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
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
        orderProperties.setOrderId(getId());
    }
    public Order(OrderDTO dto) {
        this(dto.getId(), dto.getCustomerId(), dto.getProduct(), 
        dto.getProductQty(), dto.getVoyageId(), dto.getStatus(), new ArrayList<String>());
    }
    public Order( String customerId, String product, int productQty, String voyageId, String status, List<String> reeferIds) {

        this(String.valueOf(Instant.now().toEpochMilli()), customerId, product, productQty, voyageId, status,reeferIds);
    }
    public Order( String id, String customerId, String product, int productQty, String voyageId, String status, List<String> reeferIds) {
        this.id = id;
        this.customerId = customerId;
        this.product = product;
        this.productQty = productQty;
        this.voyageId = voyageId;
        this.status = status;
        this.reeferIds = reeferIds;
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

    public JsonObject getOrderParams() {
        JsonObjectBuilder orderParamsBuilder = Json.createObjectBuilder();
        orderParamsBuilder.add("orderId", getId()).add("orderVoyageId", getVoyageId()).add("orderProductQty",
                getProductQty());
        JsonObjectBuilder jsonOrderBuilder = Json.createObjectBuilder();
        jsonOrderBuilder.add("order", orderParamsBuilder.build());
        return jsonOrderBuilder.build();
    }
}