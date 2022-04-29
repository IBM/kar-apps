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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class OrderProperties {
    String orderId;
    String sessionId;
    String customerId;
    String product;
    String originPort;
    String destinationPort;
    String voyageId;
    String date;
    String bookingStatus;
    String msg;
    int productQty;
    String replyTo;
   public OrderProperties() {
   }

   public OrderProperties(JsonObject jo ) {
      try {
         if ( jo.containsKey(Constants.ORDER_ID_KEY)) {
            this.orderId = jo.getString(Constants.ORDER_ID_KEY);
         }
         this.sessionId = jo.getString(Constants.CORRELATION_ID_KEY);
         this.customerId = jo.getString(Constants.ORDER_CUSTOMER_ID_KEY);
         this.product = jo.getString(Constants.ORDER_PRODUCT_KEY);
         this.productQty = jo.getInt(Constants.ORDER_PRODUCT_QTY_KEY);
         this.voyageId = jo.getString(Constants.VOYAGE_ID_KEY);
         //this.date = jo.getString(Constants.ORDER_DATE_KEY);
         this.originPort = jo.getString(Constants.ORDER_ORIGIN_KEY);
         this.destinationPort = jo.getString(Constants.ORDER_DESTINATION_KEY);
         if ( jo.containsKey(Constants.REPLY_TO_ENDPOINT_KEY) ) {
            this.replyTo = jo.getString(Constants.REPLY_TO_ENDPOINT_KEY);
         }
      } catch( Exception e) {

         System.out.println("OrderProperties CTOR Failed");
         e.printStackTrace();
      }

   }

    public JsonObject getAsJsonObject() {
        JsonObjectBuilder orderPropertiesBuilder = Json.createObjectBuilder();
        orderPropertiesBuilder.add(Constants.CORRELATION_ID_KEY, getCorrelationId()).
                add(Constants.VOYAGE_ID_KEY, getVoyageId()).
                add(Constants.ORDER_PRODUCT_KEY, getProduct()).
                add(Constants.ORDER_PRODUCT_QTY_KEY, getProductQty()).
                add(Constants.ORDER_CUSTOMER_ID_KEY, getCustomerId()).
                add(Constants.ORDER_ORIGIN_KEY, getOriginPort()).
                add(Constants.ORDER_DESTINATION_KEY, getDestinationPort());
        if ( replyTo != null ) {
           orderPropertiesBuilder.add(Constants.REPLY_TO_ENDPOINT_KEY, replyTo);
        }
        return orderPropertiesBuilder.build();
    }
    public String getReplyTo() {
      return replyTo;
    }
    public void setReplyTo(String replyTo) {
      this.replyTo = replyTo;
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

    public void setDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort;
    }

   public String getDestinationPort() {
      return this.destinationPort ;
   }

    public String getOrderId() {
        return orderId;
    }

    public OrderProperties setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }
    public void setCorrelationId(String sessionId) {
        this.sessionId = sessionId;
    }
    public String getCorrelationId() {
        return this.sessionId;
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