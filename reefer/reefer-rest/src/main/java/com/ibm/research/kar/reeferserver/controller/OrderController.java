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

package com.ibm.research.kar.reeferserver.controller;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.Actors;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import com.ibm.research.kar.reefer.common.json.JsonUtils;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.service.SimulatorService;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RestController
@Controller
@CrossOrigin("*")
public class OrderController {

   @Autowired
   private SimpMessagingTemplate template;
   @Autowired
   private SimulatorService simulatorService;
   @Autowired
   private GuiController gui;

   private ActorRef orderMgrActor = Kar.Actors.ref(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId);

   private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(OrderController.class.getName());

   private int max_period = 10;
   private int period = 1;
   private int counter = 1;
   private OrderStats oldStats = new OrderStats(0, 0, 0);


   /**
    * Convert json order to OrderProperties
    *
    * @param orderMsg - jason encoded message
    * @return OrderProperties instance
    */
   private OrderProperties jsonToOrderProperties(String orderMsg) {
      OrderProperties orderProperties = new OrderProperties();
      String voyageId = "";

      try (JsonReader jsonReader = Json.createReader(new StringReader(orderMsg))) {
         JsonObject req = jsonReader.readObject();
         voyageId = req.getString(Constants.VOYAGE_ID_KEY);
         Voyage voyage = getVoyage(voyageId);
         orderProperties.setProduct(req.getString(Constants.ORDER_PRODUCT_KEY));
         orderProperties.setCorrelationId(req.getString(Constants.CORRELATION_ID_KEY));
         orderProperties.setProductQty(req.getInt(Constants.ORDER_PRODUCT_QTY_KEY));
         String customerId = "N/A";
         if (req.containsKey(Constants.ORDER_CUSTOMER_ID_KEY)) {
            customerId = req.getString(Constants.ORDER_CUSTOMER_ID_KEY);
         }
         orderProperties.setCustomerId(customerId);
         orderProperties.setVoyageId(voyageId);
         orderProperties.setOriginPort(voyage.getRoute().getOriginPort());
         orderProperties.setDestinationPort(voyage.getRoute().getDestinationPort());
         if ( req.containsKey(Constants.REPLY_TO_ENDPOINT_KEY)) {
            orderProperties.setReplyTo(req.getString(Constants.REPLY_TO_ENDPOINT_KEY));
         }
      } catch (Exception e) {
         logger.log(Level.WARNING, e.getMessage()+" error ", e);
      }
      return orderProperties;
   }

   private Voyage getVoyage(String voyageId) {
      ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
      JsonValue reply = Kar.Actors.rootCall(scheduleActor, "voyage", Json.createValue(voyageId));
      return VoyageJsonSerializer.deserialize(reply.asJsonObject());
   }


   /**
    * Called to create an order using properties in the message.
    *
    * @param message - json encoded message
    * @return
    * @throws IOException
    */
   @PostMapping("/orders")
   @ResponseBody
   public JsonValue bookOrder(@RequestBody String message) throws IOException {
      try {
         Actors.Builder.instance().target(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId).
                 method("bookOrder").
                 arg(jsonToOrderProperties(message).getAsJsonObject()).
                 tell();
      } catch (Exception e) {
         logger.log(Level.WARNING, e.getMessage(), e);
         return Json.createValue(Constants.FAILED + " " + e.getMessage());
      }
      return Json.createValue(Constants.OK);
   }

   @MessageMapping("/newOrder")
   public void processNewOrder(@Payload String message, StompHeaderAccessor stompHeaderAccessor) {
      System.out.println("OrderController.processNewOrder() - got new order:"+message);
      Order order = JsonUtils.deserializeOrder(message);
     // System.out.println("OrderController.processNewOrder() - order correlation id:"+order.getCorrelationId());
     // logger.log(Level.INFO,"OrderController.processNewOrder()",stompHeaderAccessor.getMessage());
    //  System.out.println("OrderController.processNewOrder() headers:"+stompHeaderAccessor.getMessageHeaders());
      JsonObjectBuilder bookingStatus = Json.createObjectBuilder();
      bookingStatus.add(Constants.STATUS_KEY,Json.createValue("booked")).
              add(Constants.ORDER_ID_KEY,Json.createValue(order.getId())).
              add(Constants.ORDER_CUSTOMER_ID_KEY,Json.createValue(order.getCustomerId())).
              add(Constants.CORRELATION_ID_KEY, Json.createValue(order.getCorrelationId()));

      template.convertAndSend("/topic/orders", bookingStatus.build().toString(), stompHeaderAccessor.getMessageHeaders());
   }

   private JsonObject messageToJson(String message) {
      try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
         return jsonReader.readObject();
      } catch (Exception e) {
         logger.log(Level.WARNING, e.getMessage()+" error ", e);
         throw e;
      }
   }
   @PostMapping("/order/booking/result")
   public void orderBookingResult(@RequestBody String bookingMessage) {
      if (logger.isLoggable(Level.FINE)) {
         logger.fine("OrderController.orderBookingResult - Order Actor booking status:" + bookingMessage);
      }
      try {
         JsonObject reply = messageToJson(bookingMessage);
         Order order = new Order(reply);
         // HACK: the simulator currently does not support websockets and all communication
         // with it is via REST calls. The Angular GUI on the other hand expects
         // messages via websockets. At some point the sim needs to be updated to
         // use websockets for uniform communication of clients.
         if (order.isOriginSimulator() || order.getReplyTo() != null) {
            JsonObjectBuilder bookingStatus = Json.createObjectBuilder();
            if ( order.isBookingFailed()) {
               bookingStatus.add(Constants.STATUS_KEY,Json.createValue("failed")).
                       add(Constants.ORDER_ID_KEY,Json.createValue(order.getId())).
                       add(Constants.ORDER_CUSTOMER_ID_KEY,Json.createValue(order.getCustomerId())).
                       add(Constants.CORRELATION_ID_KEY, Json.createValue(order.getCorrelationId()));
               if ( order.getMsg() != null ) {
                  bookingStatus.add(Constants.REASON_KEY,order.getMsg());
               }
            } else {
               bookingStatus.add(Constants.STATUS_KEY,Json.createValue("booked")).
                       add(Constants.ORDER_ID_KEY,Json.createValue(order.getId())).
                       add(Constants.ORDER_CUSTOMER_ID_KEY,Json.createValue(order.getCustomerId())).
                       add(Constants.CORRELATION_ID_KEY, Json.createValue(order.getCorrelationId()));
            }
            if ( order.isOriginSimulator()) {
               Kar.Services.tell(Constants.SIMSERVICE, "simulator/orderstatus", bookingStatus.build());
            } else {
               template.convertAndSend("/topic/"+order.getReplyTo(),bookingStatus.build().toString());
            }
         }
      } catch (Exception e) {
         logger.severe("OrderController.orderBookingResult - failed to process booking - received message: "+bookingMessage);
         throw e;
      }
   }

   @PostMapping("/order/booking/accepted")
   public void orderBookingAccepted(@RequestBody String bookingMessage) {
      try {
         Order order = new Order(messageToJson(bookingMessage));
         if (order.isOriginSimulator() || order.getReplyTo() != null ) {
            JsonObjectBuilder bookingStatus = Json.createObjectBuilder();
            bookingStatus.add(Constants.STATUS_KEY, Json.createValue("accepted")).
                    add(Constants.CORRELATION_ID_KEY, Json.createValue(order.getCorrelationId())).add(Constants.ORDER_ID_KEY, order.getId());
            if ( order.isOriginSimulator()) {
               Kar.Services.tell(Constants.SIMSERVICE, "simulator/orderstatus", bookingStatus.build());
            } else {
               template.convertAndSend("/topic/"+order.getReplyTo(),bookingStatus.build().toString());
            }
         }
      } catch (Exception e) {
         logger.severe("OrderController.orderBookingAccepted - failed to process booking - received message: "+bookingMessage);
         throw e;
      }
   }

   /**
    * Returns a list of voyages that are currently at sea
    *
    * @return
    */
   @GetMapping("/orders/list/active")
   public List<Order> getActiveOrderList() {
      JsonValue reply = Kar.Actors.rootCall(orderMgrActor, "ordersInTransit");
      return reply.asJsonArray().stream().map(Order::new).collect(Collectors.toList());
   }

   /**
    * Returns a list of voyages that have been booked but not yet at sea
    *
    * @return
    */
   @GetMapping("/orders/list/booked")
   public List<Order> getBookedOrderList() {
      JsonValue reply = Kar.Actors.rootCall(orderMgrActor, "ordersBooked");
      return reply.asJsonArray().stream().map(Order::new).collect(Collectors.toList());
   }

   /**
    * Returns a list of spoilt orders
    *
    * @return
    */
   @GetMapping("/orders/list/spoilt")
   public List<Order> getSpoiltOrderList() {
      JsonValue reply = Kar.Actors.rootCall(orderMgrActor, "ordersSpoilt");
      return reply.asJsonArray().stream().map(Order::new).collect(Collectors.toList());
   }

   /**
    * Returns order related counts
    *
    * @return
    */
   @GetMapping("/orders/stats")
   public OrderStats getOrderStats() {
      int bookedTotalCount = 0;
      int inTransitTotalCount = 0;
      int spoiltTotalCount = 0;
      if ( orderMgrActor == null ) {
         logger.log(Level.SEVERE,"OrderController.getOrderStats() - error - orderMgrActor instance is null");
         return new OrderStats(0,0,0);
      }
      try {
         JsonValue orderMgrMetrics = Kar.Actors.State.get(orderMgrActor, Constants.ORDER_METRICS_KEY);
         if (orderMgrMetrics != null && orderMgrMetrics != JsonValue.NULL) {
            String orderMetrics = ((JsonString) orderMgrMetrics).getString();
            String[] values = orderMetrics.split(":");
            bookedTotalCount = Integer.valueOf(values[0].trim());
            inTransitTotalCount = Integer.valueOf(values[1].trim());
            spoiltTotalCount = Integer.valueOf(values[2].trim());
         }
      } catch( Exception e) {
         String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
         logger.log(Level.SEVERE,"OrderController.getOrderStats() - error "+stacktrace);
         throw e;
      }
      return new OrderStats(inTransitTotalCount, bookedTotalCount, spoiltTotalCount);
   }

   @Scheduled(fixedDelay = 100)
   public void scheduleGuiUpdate() {
      if (0 >= --counter) {
         OrderStats newStats = getOrderStats();
         if (newStats.getFutureOrderCount() != oldStats.getFutureOrderCount() ||
                 newStats.getSpoiltOrderCount() != oldStats.getSpoiltOrderCount() ||
                 newStats.getInTransitOrderCount() != oldStats.getInTransitOrderCount()) {
            gui.updateOrderCounts(newStats);
            oldStats = newStats;
            period = period / 2 < 1 ? 1 : period / 2;
         } else {
            period = 2 * period > max_period ? max_period : 2 * period;
         }
         counter = period;
      }
   }
}
