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
package com.ibm.research.kar.reefer.client.orderdriver;

import com.ibm.research.kar.reefer.client.orderdriver.model.Booking;
import com.ibm.research.kar.reefer.client.orderdriver.model.FutureVoyage;

import javax.json.Json;
import javax.json.JsonObject;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class OrderDispatcher {
   private static final Logger logger = Logger.getLogger(OrderDispatcher.class.getName());
   private ReeferWebApi reeferApiServer;
   private String orderDriverEndpoint;
   private String hostIP;
   private long totalLatency;
   private long totalOrdersDispatched;
   private long totalOrdersDone;
   private int updatesPerDay = 3;  // default
   private long orderCounter = 1;
   ReplyProcessor replyProcessor;
   long timeoutMillis=0;

   public OrderDispatcher withApiServer(ReeferWebApi server ) {
      this.reeferApiServer = server;
      return this;
   }
   public OrderDispatcher withReplyProcessor(ReplyProcessor replyProcessor ) {
      this.replyProcessor = replyProcessor;
      return this;
   }
   public OrderDispatcher withDriverReplyEndpoint(String endpoint ) {
      this.orderDriverEndpoint = endpoint;
      return this;
   }
   public OrderDispatcher withHostIp(String hostIP ) {
      this.hostIP = hostIP;
      return this;
   }
   public OrderDispatcher withUpdatesPerDay( int updatesPerDay ) {
      this.updatesPerDay = updatesPerDay;
      return this;
   }
   public OrderDispatcher withOrderTimeout( long timeoutMillis ) {
      this.timeoutMillis = timeoutMillis;
      return this;
   }
   public void dispatchTodayOrders(FutureVoyage futureVoyage, int unitDelay) {
      // day end time is 98% of unit delay
      long dayEndTime = System.currentTimeMillis() + 980 * unitDelay;
      long expectedOrders = updatesPerDay;
      for (int u = 0; u < updatesPerDay; u++) {
         if (futureVoyage.getOrderSize() > 0) {
            JsonObject order = createOrder(futureVoyage);
            Booking booking = Booking.of(order);
            // start a timer task for each booking to support timeouts
            booking.startTimer(timeoutMillis, replyProcessor);
            // add booking to outstanding map so that we can match replies
            replyProcessor.addPendingOrder(booking);
            try {
               reeferApiServer.post("/orders", order);
               logger.info("OrderDriver - dispatched order: " + booking.getCorrelationId());
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
            totalOrdersDispatched++;
            long timeRemaining = dayEndTime - System.currentTimeMillis();
            // stop processing if less than 10ms left in day
            if (timeRemaining < 10) {
               return;
            }
/*
            try {
               sleepBetweenOrders(timeRemaining, expectedOrders);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }

 */

         }

      }

   }

   private void sleepBetweenOrders(long timeRemaining, long expectedOrders) throws InterruptedException {
      if (expectedOrders > totalOrdersDispatched) {
         long timeToSleep = 10;
         long orderTimeRemaining = (totalLatency / totalOrdersDone)
                 * (expectedOrders - totalOrdersDispatched);
         long triggerRandom = 20;  // 20 millis
         if (orderTimeRemaining > 0 && timeRemaining > 0) {
            timeToSleep = (timeRemaining - orderTimeRemaining)
                    / (expectedOrders - totalOrdersDispatched);
            if (timeToSleep > triggerRandom) {
               timeToSleep = ThreadLocalRandom.current().nextLong(timeToSleep / 2, (timeToSleep * 3) / 2);
            }
            timeToSleep = (timeToSleep < 1) ? 1 : timeToSleep;
         }
         System.out.println(String.format("++++++++++++++++++++++++++ timeRemaining=%d orderTimeRemaining=%d totalOrderTime=%d threadOrdersDone=%d timeToSleep=%d",
                 timeRemaining, orderTimeRemaining, totalLatency, totalOrdersDone, timeToSleep));

         Thread.sleep(timeToSleep);
      }
   }

   private JsonObject createOrder(FutureVoyage futureVoyage) {
      return Json.createObjectBuilder().add("voyageId", futureVoyage.getId())
              .add("customerId", "order-driver")
              .add("product", "pseudoBeef")
              .add("productQty", futureVoyage.getOrderSize())
              .add("correlationId", hostIP + ":" + orderCounter++)
              .add("client-reply-endpoint", orderDriverEndpoint)
              .add("order-time", String.valueOf(System.currentTimeMillis())).
              build();
   }
}
