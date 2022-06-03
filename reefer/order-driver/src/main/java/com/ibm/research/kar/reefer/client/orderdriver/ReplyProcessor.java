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
import com.ibm.research.kar.reefer.client.orderdriver.model.OrderStats;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

public class ReplyProcessor implements TimeoutHandler, OrderReplyMessageHandler {
   private static final Logger logger = Logger.getLogger(ReplyProcessor.class.getName());
   Map<String, Booking> bookingMap = new ConcurrentHashMap<>();
   Map<String, Function<Booking, String>> replyFunctorMap = new HashMap<>();
   OrderDispatcher orderDispatcher;
   OrderStats orderStats;
   public ReplyProcessor(OrderStats orderStats) {
      this.orderStats = orderStats;
      // register handler for each status sent by reefer in a reply
      // Handler for 'accepted'
      replyFunctorMap.put(Booking.BookingStatus.ACCEPTED.name(), new Function<Booking, String>() {
         @Override
         public String apply(Booking booking) {
            return handleBookingAcceptance(booking);
         }
      });
      // Handler for 'booked'
      replyFunctorMap.put(Booking.BookingStatus.BOOKED.name(), new Function<Booking, String>() {
         @Override
         public String apply(Booking booking) {
            return handleBookingSuccess(booking);
         }
      });
      // Handler for 'failed'
      replyFunctorMap.put(Booking.BookingStatus.FAILED.name(), new Function<Booking, String>() {
         @Override
         public String apply(Booking booking) {
            return handleBookingFailure(booking);
         }
      });
   }
   public void setOrderDispatcher(OrderDispatcher orderDispatcher ) {
      this.orderDispatcher = orderDispatcher;
   }
   private String handleBookingSuccess(Booking booking) {
      // NOTE: THIS METHOD IS CALLED ON A SEPARATE THREAD WHICH HANDLES REPLIES FROM REEFER (Websockets)
      // See: WebSocketController subscription
      if ( !bookingMap.containsKey(booking.getCorrelationId()) ) {
         logger.severe(">>>>>>>>>>>>>>>>>> Order Booked - But not in the outstanding map????");
      } else {
         orderStats.incrementBooked();
         bookingMap.get(booking.getCorrelationId()).cancelTimer();
         Booking cachedBooking = bookingMap.get(booking.getCorrelationId());
         if ( cachedBooking != null ) {
            if ( !cachedBooking.getStatus().equals(Booking.BookingStatus.ACCEPTED) ) {
               logger.warning("Invalid Booking State - Expected 'accepted', instead current state: "+cachedBooking.getStatus().name().toLowerCase());
            }
         }
         bookingMap.remove(booking.getCorrelationId());
       //  logger.info(">>>>>>>>>>>>>>>>>> Order Booked - correlationId: " + booking.getCorrelationId() + " orderId: " + booking.getOrderId() + " latency: " + booking.getLatency()+" bookingMap.size(): "+bookingMap.size());
      }
      return "OK";
   }

   private String handleBookingAcceptance(Booking booking) {
      // NOTE: THIS METHOD IS CALLED ON A SEPARATE THREAD WHICH HANDLES REPLIES FROM REEFER (Websockets)
      // See: WebSocketController subscription
      if ( !bookingMap.containsKey(booking.getCorrelationId()) ) {
         logger.severe(">>>>>>>>>>>>>>>>>> Order Accepted - But not in the outstanding map - what da????");
      } else {
         orderStats.incrementAccepted();
         Booking cachedBooking = bookingMap.get(booking.getCorrelationId());
         if ( cachedBooking != null ) {
            if ( !cachedBooking.getStatus().equals(Booking.BookingStatus.PENDING) ) {
               logger.warning("Invalid Booking State - Expected 'pending', instead current state: "+cachedBooking.getStatus().name().toLowerCase());
            } else {
               cachedBooking.setState(Booking.BookingStatus.ACCEPTED);
            }
         }
      }
      return "OK";
   }

   private String handleBookingFailure(Booking booking) {
      // NOTE: THIS METHOD IS CALLED ON A SEPARATE THREAD WHICH HANDLES REPLIES FROM REEFER (Websockets)
      // See: WebSocketController subscription
      if ( !bookingMap.containsKey(booking.getCorrelationId()) ) {
         logger.severe(">>>>>>>>>>>>>>>>>> Order Failed - But not in the outstanding map - what da???? ");
      } else {
         orderStats.incrementFailed();
         logger.warning(">>>>>>>>>>>>>>>>>> Order Failed - correlationId: " + booking.getCorrelationId() + " orderId: " + booking.getOrderId()
                 + " latency: " + booking.getLatency()+" cause: "+booking.getErrorMsg());
         bookingMap.get(booking.getCorrelationId()).cancelTimer();
         bookingMap.remove(booking.getCorrelationId());
      }
      return "OK";
   }

   public void addPendingOrder(Booking booking) {
      // add booking to pending reply map. Each entry has (already started) timer task with expiration time.
      // When timer task pops, the timeout handler will be invoked
      bookingMap.put(booking.getCorrelationId(), booking);
   }
   public void process(Booking booking) {
      booking.setLatency(System.currentTimeMillis() - Long.valueOf(booking.getOrderTime()));
      if (replyFunctorMap.containsKey(booking.getStatus().name())) {
         replyFunctorMap.get(booking.getStatus().name()).apply(booking);
      }
   }
   /**
    * Replies arrive asynchronously here pushed by subscription in WebSocketController
    * @param message
    */
   @Override
   public void onMessage(Object message) {
      Booking booking = Booking.of((String) message);
      long latency = System.currentTimeMillis() - Long.valueOf(booking.getOrderTime());
      booking.setLatency(latency);
      System.out.println("................. latency:"+latency);
      orderDispatcher.incrementTotalLatency(latency);
      // using order status (accepted, booked, failed) call a registered method to handle
      // the message
      if (replyFunctorMap.containsKey(booking.getStatus().name())) {
         replyFunctorMap.get(booking.getStatus().name()).apply(booking);
      }
   }
   @Override
   public void handleTimeout(Booking booking) {
      logger.severe("Order Timed out - corrId:"+booking.getCorrelationId());
      bookingMap.remove(booking.getCorrelationId());
      orderStats.incrementFailed();
   }
}
