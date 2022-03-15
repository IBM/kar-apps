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

package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.Reminder;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.FixedSizeQueue;
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.OrderProperties;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.json.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Actor
public class OrderManagerActor extends BaseActor {

   private int maxOrderCount = 10;
   // need separate queues for each state type. Can't use single list as the
   // booked orders are more frequent and would push all the other types out of
   // the bounded queue
   private FixedSizeQueue inTransitOrderList = new FixedSizeQueue(maxOrderCount);
   private FixedSizeQueue bookedOrderList = new FixedSizeQueue(maxOrderCount);
   private FixedSizeQueue spoiltOrderList = new FixedSizeQueue(maxOrderCount);

   private Map<String, JsonValue> activeOrders = new HashMap<>();

   private int bookedTotalCount = 0;
   private int inTransitTotalCount = 0;
   private int spoiltTotalCount = 0;

   private String orderMetrics = "";
   private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(OrderManagerActor.class.getName());

   @Activate
   public void activate() {

      Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
      try {
         // initial actor invocation should handle no state
         if (!state.isEmpty()) {

            if (state.containsKey(Constants.ORDER_METRICS_KEY)) {
               orderMetrics = (((JsonString) state.get(Constants.ORDER_METRICS_KEY)).getString());
               String[] values = orderMetrics.split(":");

               bookedTotalCount = Integer.valueOf(values[0].trim());
               inTransitTotalCount = Integer.valueOf(values[1].trim());
               spoiltTotalCount = Integer.valueOf(values[2].trim());
            }
            if (state.containsKey(Constants.ORDERS_KEY)) {
               activeOrders.putAll(state.get(Constants.ORDERS_KEY).asJsonObject());
            }
            logger.info("OrderManagerActor.activate() - Totals - totalInTransit:" + inTransitTotalCount + " totalBooked: " + bookedTotalCount + " totalSpoilt:" + spoiltTotalCount);

         }
      } catch (Throwable e) {
         logger.log(Level.SEVERE, "OrderManagerActor.activate() - error ", e);
         throw new RuntimeException(e);
      }
   }

   @Remote
   public void orderRollback(JsonObject message) {
      Order order = null;
      try {
         order = new Order(message);
         if (!activeOrders.containsKey(order.getId())) {
            logger.warning("OrderManagerActor.orderRollback - Order: " + order.getId() + " not in activeMap - probably duplicate order - ignoring rollback");
         } else {
            Actors.Builder.instance().target(ReeferAppConfig.VoyageActorType, order.getVoyageId()).
                    method("rollbackOrder").arg(order.getAsJsonObject()).tell();
            activeOrders.remove(order.getId());
            order.setStatus(Constants.FAILED);
            order.setMsg("OrderManager - Order booking request timed out");
            Kar.Services.tell(Constants.REEFERSERVICE, "/order/booking/failed", order.getAsJsonObject());
         }
      } catch( Exception e) {
         logger.log(Level.SEVERE, ExceptionUtils.getStackTrace(e).replaceAll("\n", ""));
      } finally {
         if ( order == null ) {
            logger.log(Level.SEVERE, "OrderManagerActor.orderRollback() - Invalid state - order instance invalid (null)");
            return;
         }
         Kar.Actors.Reminders.cancel(this, order.getCorrelationId());
      }

   }

   @Remote
   public void bookOrder(JsonObject message) {
      Order order = null;
      try {
         order = new Order(new OrderProperties(message));
         Reminder[] reminders = Kar.Actors.Reminders.get(this, order.getCorrelationId());
         if (reminders != null && reminders.length > 0) {
            order = new Order((JsonObject) (reminders[0].getArguments()[0]));
         } else {
            // generate unique order id
            order.generateOrderId();
            Kar.Services.tell(Constants.REEFERSERVICE, "/order/booking/accepted", order.getAsJsonObject());
            Kar.Actors.Reminders.schedule(this, "orderRollback", order.getCorrelationId(), Instant.now().plus(Constants.ORDER_TIMEOUT_SECS, ChronoUnit.SECONDS), Duration.ofMillis(1000), order.getAsJsonObject());
         }
         Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                 method("createOrder").arg(order.getAsJsonObject()).tell();
         Map<String, JsonValue> updateMap = new HashMap<>();
         updateMap.put(order.getId(), order.getAsJsonObject());
         updateStore(Collections.emptyMap(), updateMap);
         activeOrders.put(order.getId(), order.getAsJsonObject());
      } catch (Exception e) {
         logger.log(Level.SEVERE, ExceptionUtils.getStackTrace(e).replaceAll("\n", ""));
         if (order == null) {
            logger.log(Level.SEVERE, "OrderManagerActor.bookOrder() - error - Unable to create order instance from message:" + message);
         } else {
            order.setMsg(e.getMessage());
            order.setStatus(Constants.FAILED);
            Kar.Services.tell(Constants.REEFERSERVICE, "/order/booking/failed", order.getAsJsonObject());
         }
      }
   }

   @Remote
   public void orderBooked(JsonObject message) {
      Order order = null;
      try {
         JsonObject activeOrder;
         order = new Order(message);
         if (!activeOrders.containsKey(order.getId())) {
            logger.log(Level.WARNING, "OrderManagerActor.orderBooked() - order:" + order.getId() + " not found in activeOrders Map");
            return;
         }
         activeOrder = activeOrders.get(order.getId()).asJsonObject();
         if ( Order.pending(activeOrder) ) {
            order.setStatus(Order.OrderStatus.BOOKED.name());
            activeOrders.put(order.getId(), order.getAsJsonObject());
            bookedOrderList.add(order);
            bookedTotalCount++;
            Map<String, JsonValue> updateMap = new HashMap<>();
            updateMap.put(order.getId(), order.getAsJsonObject());
            updateStore(Collections.emptyMap(), updateMap);
            Kar.Services.tell(Constants.REEFERSERVICE, "/order/booking/success", order.getAsJsonObject());
         } else if ( Order.booked( activeOrder) ) {
            logger.log(Level.INFO, "OrderManagerActor.orderBooked() - sending reply to REST - idempotance path");
            // idempotence check - returned previously saved booking
            Kar.Services.tell(Constants.REEFERSERVICE, "/order/booking/success", activeOrder);
         } else {
            logger.log(Level.SEVERE, "OrderManagerActor.orderBooked() - error Unexpected Order State:" + activeOrder);
         }

      } catch (Exception e) {
         logger.log(Level.SEVERE, "OrderManagerActor.orderBooked() - error ", ExceptionUtils.getStackTrace(e).replaceAll("\n", ""));
         throw e;
      } finally {
         if (order == null) {
            logger.log(Level.SEVERE, "OrderManagerActor.orderBooked() - Invalid state - order instance invalid (null)");
            return;
         }
         Kar.Actors.Reminders.cancel(this, order.getCorrelationId());
      }
   }

   @Remote
   public void orderFailed(JsonObject message) {
      Order order = null;
      try {
         order = new Order(message);
         activeOrders.remove(order.getId());
         order.setStatus(Constants.FAILED);
         Kar.Services.tell(Constants.REEFERSERVICE, "/order/booking/failed", order.getAsJsonObject());
      } catch (Exception e) {
         logger.log(Level.SEVERE, "OrderManagerActor.orderFailed() - error ", ExceptionUtils.getStackTrace(e).replaceAll("\n", ""));
         throw e;
      } finally {
         if (order == null) {
            logger.log(Level.SEVERE, "OrderManagerActor.orderFailed() - Invalid state - order instance invalid (null)");
            return;
         }
         Kar.Actors.Reminders.cancel(this, order.getCorrelationId());
      }
   }

   @Remote
   public void orderDeparted(JsonValue message) {
      try {

         Order order = new Order(message);

         if (activeOrders.containsKey(order.getId())) {
            Order activeOrder = new Order(activeOrders.get(order.getId()));
            // idempotence check to prevent double counting
            if (!Order.OrderStatus.INTRANSIT.name().equals(activeOrder.getStatus()) ) {
               inTransitOrderList.add(order);
               bookedOrderList.remove(order);
               inTransitTotalCount++;
               bookedTotalCount--;
               order.setStatus(Order.OrderStatus.INTRANSIT.name());
               activeOrders.put(order.getId(), order.getAsJsonObject());
               Map<String, JsonValue> updateMap = new HashMap<>();
               updateMap.put(order.getId(), order.getAsJsonObject());
               updateStore(Collections.emptyMap(), updateMap);
            }
         } else {
            logger.log(Level.SEVERE, "OrderManagerActor.orderDeparted() "+" order: " +order.getId()+" not in active orders map - message:"+message);
         }
      } catch (Exception e) {
         logger.log(Level.SEVERE, "OrderManagerActor.orderDeparted() - error ", ExceptionUtils.getStackTrace(e).replaceAll("\n", ""));
         throw e;
      }
   }

   @Remote
   public void ordersDeparted(JsonValue message) {
      try {
         String voyageId = message.asJsonObject().getString(Constants.VOYAGE_ID_KEY);
         JsonArray orders = message.asJsonObject().getJsonArray(Constants.ORDERS_KEY);
         orders.forEach(oId -> {
            String orderId = ((JsonString) oId).getString();
            if (activeOrders.containsKey(orderId)) {
               Order activeOrder = new Order(activeOrders.get(orderId));
               Map<String, JsonValue> updateMap = new HashMap<>();
               // idempotence check
               if (!Order.OrderStatus.INTRANSIT.name().equals(activeOrder.getStatus())) {
                  activeOrder.setStatus(Order.OrderStatus.INTRANSIT.name());
                  inTransitOrderList.add(activeOrder);
                  bookedOrderList.remove(activeOrder);
                  inTransitTotalCount++;
                  bookedTotalCount--;
                  activeOrders.put(activeOrder.getId(), activeOrder.getAsJsonObject());
                  updateMap.put(activeOrder.getId(), activeOrder.getAsJsonObject());
               }
               if (!updateMap.isEmpty()) {
                  updateStore(Collections.emptyMap(), updateMap);
               }
            }  else {
               logger.log(Level.SEVERE, "OrderManagerActor.ordersDeparted() "+" order: " +orderId+" not in active orders map - message:"+message);
            }
         });
      } catch (Exception e) {
         logger.log(Level.SEVERE, "OrderManagerActor.ordersDeparted() - error ", ExceptionUtils.getStackTrace(e).replaceAll("\n", ""));
         throw e;
      }
   }

   @Remote
   public void ordersArrived(JsonValue message) {
      List<String> orders2Remove = new ArrayList<>();
      JsonArray orders = message.asJsonArray();
      orders.forEach(oId -> {
         String orderId = ((JsonString) oId).getString();
         if (activeOrders.containsKey(orderId)) {
            if (orderArrived(new Order(activeOrders.get(orderId)))) {
               orders2Remove.add(orderId);
               activeOrders.remove(orderId);
            }
         }
      });
      HashMap<String, List<String>> deleteMap = new HashMap<>();
      deleteMap.put(Constants.ORDERS_KEY, orders2Remove);
      updateStore(deleteMap, Collections.emptyMap());
   }

   private boolean orderArrived(Order activeOrder) {
      try {
         if (!Order.OrderStatus.DELIVERED.name().equals(activeOrder.getStatus())) {
            inTransitOrderList.remove(activeOrder);
            inTransitTotalCount--;
            if (activeOrder.isSpoilt()) {
               spoiltTotalCount--;
               spoiltOrderList.remove(activeOrder);
            }
            return true;
         }
         return false;
      } catch (Exception e) {
         logger.log(Level.SEVERE, "OrderManagerActor.orderArrived() - error ", ExceptionUtils.getStackTrace(e).replaceAll("\n", ""));
         throw e;
      }
   }

   @Remote
   public void orderSpoilt(JsonObject message) {
      try {
         Order order = new Order(message);
         if (activeOrders.containsKey(order.getId())) {
            Order activeOrder = new Order(activeOrders.get(order.getId()));
            // idempotence check to prevent double counting
            if (!activeOrder.isSpoilt()) {
               spoiltOrderList.add(order);
               spoiltTotalCount++;
               order.setSpoilt(true);
               activeOrders.put(order.getId(), order.getAsJsonObject());
               Map<String, JsonValue> updateMap = new HashMap<>();
               updateMap.put(order.getId(), order.getAsJsonObject());
               updateStore(Collections.emptyMap(), updateMap);
            }
         }
      } catch (Exception e) {
         logger.log(Level.SEVERE, "OrderManagerActor.orderSpoilt() - error ", ExceptionUtils.getStackTrace(e).replaceAll("\n", ""));
         throw e;
      }
   }

   @Remote
   public JsonValue ordersBooked() {
      return getOrderList(bookedOrderList);
   }

   @Remote
   public JsonValue ordersSpoilt() {
      return getOrderList(spoiltOrderList);
   }

   @Remote
   public JsonValue ordersInTransit() {
      return getOrderList(inTransitOrderList);
   }

   private JsonValue getOrderList(FixedSizeQueue orders) {
      JsonArrayBuilder jab = Json.createArrayBuilder();
      orders.forEach(order -> jab.add(order.getAsJsonObject()));
      return jab.build();
   }

   private void updateStore(Map<String, List<String>> deleteMap, Map<String, JsonValue> updateMap) {
      String metrics = String.format("%d:%d:%d", bookedTotalCount, inTransitTotalCount, spoiltTotalCount);
      Map<String, JsonValue> actorStateMap = new HashMap<>();
      actorStateMap.put(Constants.ORDER_METRICS_KEY, Json.createValue(metrics));
      Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
      subMapUpdates.put(Constants.ORDERS_KEY, updateMap);
      Kar.Actors.State.update(this, Collections.emptyList(), deleteMap, actorStateMap, subMapUpdates);
   }

}
