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
package com.ibm.research.kar.reefer.client.orderdriver.model;

import com.ibm.research.kar.reefer.client.orderdriver.ReplyProcessor;
import com.ibm.research.kar.reefer.client.orderdriver.TimeoutHandler;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.util.Date;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class Booking {
   public enum BookingStatus {PENDING, BOOKED, FAILED, ACCEPTED };

   private BookingStatus status = BookingStatus.PENDING;
   private String orderId;
   private String customerId;
   private String correlationId;
   private String errorMsg;
   // store order latency
   private long latency=0;
   private String orderTime;
   private  TimerTask task;

   private Booking(String jsonBooking) {
      try (JsonReader jsonReader = Json.createReader(new StringReader(jsonBooking))) {
         JsonObject jsonStatus = jsonReader.readObject();
         if( jsonStatus.containsKey("status")) {
            this.status = BookingStatus.valueOf(jsonStatus.getString("status").toUpperCase());
         }
         if( jsonStatus.containsKey("orderId")) {
            this.orderId = jsonStatus.getString("orderId");
         }
         if( jsonStatus.containsKey("customerId")) {
            this.customerId = jsonStatus.getString("customerId");
         }
         this.correlationId = jsonStatus.getString("correlationId");
         if ( status.equals(BookingStatus.FAILED)) {
            this.errorMsg = jsonStatus.getString("reason");
         }
         if ( jsonStatus.containsKey("order-time")) {
            orderTime = jsonStatus.getString("order-time");
         }
      } catch( Exception e) {
         throw new RuntimeException(e);
      }
   }
   public static Booking of(String jsonBooking) {
      return new Booking(jsonBooking);
   }
   public static Booking of(JsonObject jsonBooking) {
      return new Booking(jsonBooking.toString());
   }
   public BookingStatus getStatus() {
      return status;
   }
   public void setLatency(long latencyMillis) {
      latency = latencyMillis;
   }
   public void setState(BookingStatus state) {
      status = state;
   }
   public long getLatency() {
      return latency;
   }
   public String getOrderId() {
      return orderId;
   }
   public String getOrderTime() { return orderTime; }
   public String getCustomerId() {
      return customerId;
   }

   public String getCorrelationId() {
      return correlationId;
   }

   public String getErrorMsg() {
      return errorMsg;
   }

   private Booking getBookingInstance() {
      return this;
   }
   public void startTimer(long timeout, TimeoutHandler timeoutHandler) {
      task = new TimerTask() {
         public void run() {
            task.cancel();
            timeoutHandler.handleTimeout(getBookingInstance());
         }
      };
      Timer timer = new Timer(getCorrelationId());
      timer.schedule(task, timeout);
   }
   public void cancelTimer() {
      if ( task != null ) {
         task.cancel();
      }
   }
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Booking booking = (Booking) o;
      return status == booking.status && Objects.equals(orderId, booking.orderId) && Objects.equals(customerId, booking.customerId) && Objects.equals(correlationId, booking.correlationId) && Objects.equals(errorMsg, booking.errorMsg);
   }

   @Override
   public int hashCode() {
      return Objects.hash(status, orderId, customerId, correlationId, errorMsg);
   }
}
