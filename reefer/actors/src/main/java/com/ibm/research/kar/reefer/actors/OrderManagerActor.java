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
    // key=correlationId, value=orderId
    private Map<String, String> orderCorrelationIds = new HashMap<>();
    private int bookedTotalCount = 0;
    private int inTransitTotalCount = 0;
    private int spoiltTotalCount = 0;

    private String orderMetrics = "";
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(OrderManagerActor.class.getName());

    int orderCount=0;
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
                    long t = System.currentTimeMillis();
                    activeOrders.putAll(state.get(Constants.ORDERS_KEY).asJsonObject());
                    // map correlationIds to orderIds so that we can efficiently do idempotence check
                    activeOrders.forEach((key,value) -> orderCorrelationIds.put(value.asJsonObject().getString(Constants.CORRELATION_ID_KEY), key));
                }
                logger.info("OrderManagerActor.activate() - Totals - totalInTransit:" + inTransitTotalCount + " totalBooked: " + bookedTotalCount + " totalSpoilt:" + spoiltTotalCount);

            }
        } catch (Throwable e) {
            logger.log(Level.SEVERE,"OrderManagerActor.activate()", e);
            throw new RuntimeException(e);
        }
    }
    @Remote
    public void orderRollback(JsonObject message) {
        logger.info("OrderManagerActor.orderRollback - Called -" + message);
        Order order = new Order(message);
        Kar.Actors.Reminders.cancel(this, order.getId());
        if (orderCorrelationIds.containsKey(order.getCorrelationId())) {
            Actors.Builder.instance().target(ReeferAppConfig.VoyageActorType, order.getVoyageId()).
                    method("rollbackOrder").arg(order.getAsJsonObject()).tell();
            activeOrders.remove(order.getId());
            orderCorrelationIds.remove(order.getCorrelationId());
            Kar.Services.post(Constants.REEFERSERVICE, "/order/booking/failed", message);
        }
    }
    @Remote
    public void bookOrder(JsonObject message) {
        logger.info("OrderManagerActor.bookOrder - Called - orderCount:"+orderCount+" " + message);
        Order order = null;
        try {
            order = new Order(new OrderProperties(message));
            
            // idempotence check
            if (!orderCorrelationIds.containsKey(order.getCorrelationId())) {
                // generate unique order id
                order.generateOrderId();


                if ( orderCount % 10 == 0 ) {
                    orderCount++;
                    order.setMsg("Simulated error in OrderManager");
                    Kar.Services.post(Constants.REEFERSERVICE, "/order/booking/failed", order.getAsJsonObject());
                    return;
                }
                Kar.Services.post(Constants.REEFERSERVICE, "/order/booking/accepted", order.getAsJsonObject());
                Kar.Actors.Reminders.schedule(this, "orderRollback", order.getId(), Instant.now().plus(2, ChronoUnit.MINUTES), Duration.ofMillis(1000), order.getAsJsonObject());
                Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                        method("createOrder").arg(order.getAsJsonObject()).tell();
                Map<String, JsonValue> updateMap = new HashMap<>();
                updateMap.put(order.getId(), order.getAsJsonObject());
                updateStore(Collections.emptyMap(), updateMap);
                activeOrders.put(order.getId(), order.getAsJsonObject());
                orderCorrelationIds.put(order.getCorrelationId(), order.getId());
                logger.info("OrderManagerActor.bookOrder - order saved -" + order.getAsJsonObject());
            } else {
                // the process must have died while handling request and kar just retried the call
                logger.log(Level.WARNING, "OrderManagerActor.bookOrder() - duplicate order - correlationId:"+order.getCorrelationId());
                // fetch previously generated order id
                String savedOrderId = orderCorrelationIds.get(order.getCorrelationId());
                // process as a late booked order.
                orderBooked(activeOrders.get(savedOrderId).asJsonObject());
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, e.getMessage(), e);
            String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
            logger.log(Level.SEVERE, stacktrace);
            if ( order == null ) {
                logger.log(Level.SEVERE, "OrderManagerActor.bookOrder() - Unable to create order instance from message:"+message);
            } else {
                order.setMsg(e.getMessage());
                Kar.Services.post(Constants.REEFERSERVICE, "/order/booking/failed", order.getAsJsonObject());
            }

        }
    }
    @Remote
    public void orderBooked(JsonObject message) {
        try {
            logger.info("OrderManagerActor.orderBooked - Called -" + message);
            JsonObject activeOrder;
            Order order = new Order(message);
            Kar.Actors.Reminders.cancel(this, order.getId());
            if (activeOrders.containsKey(order.getId()) ) {
                activeOrder = activeOrders.get(order.getId()).asJsonObject();
                if ( activeOrder.getString(Constants.ORDER_STATUS_KEY).equals(Order.OrderStatus.PENDING.name())) {
                    order.setStatus(Order.OrderStatus.BOOKED.name());
                    activeOrders.put(order.getId(), order.getAsJsonObject());
                    bookedOrderList.add(order);
                    bookedTotalCount++;
                    Map<String, JsonValue> updateMap = new HashMap<>();
                    updateMap.put(order.getId(), order.getAsJsonObject());
                    updateStore(Collections.emptyMap(), updateMap);
                    Kar.Services.post(Constants.REEFERSERVICE, "/order/booking/success", order.getAsJsonObject());
                } else if ( activeOrder.getString(Constants.ORDER_STATUS_KEY).equals(Order.OrderStatus.BOOKED.name())){
                    // idempotence check - returned previously saved booking
                    Kar.Services.post(Constants.REEFERSERVICE, "/order/booking/success", activeOrder);
                } else {
                    logger.log(Level.SEVERE,"OrderManagerActor.orderBooked() - Unexpected Order State:"+activeOrder);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,"OrderManagerActor.orderBooked() ", e);
            throw e;
        }
    }
    @Remote
    public void orderFailed(JsonObject message) {
        try {
            Order order = new Order(message);
            Kar.Actors.Reminders.cancel(this, order.getId());
            activeOrders.remove(order.getId());
            orderCorrelationIds.remove(order.getCorrelationId());
            Kar.Services.post(Constants.REEFERSERVICE, "/order/booking/failed", message);
        } catch (Exception e) {
            logger.log(Level.SEVERE,"OrderManagerActor.orderFailed() ", e);
            throw e;
        }
    }
    @Remote
    public void orderDeparted(JsonValue message) {
        try {
            Order order = new Order(message);
            if (activeOrders.containsKey(order.getId())) {
                Order activeOrder = new Order(activeOrders.get(order.getId()));
                // idempotence check to prevent double counting
                if (!Order.OrderStatus.INTRANSIT.name().equals(activeOrder.getStatus())) {
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
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,"OrderManagerActor.orderDeparted()", e);
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
                    orderCorrelationIds.remove(orderId);
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
            logger.log(Level.SEVERE,"OrderManagerActor.orderArrived()", e);
            throw e;
        }
    }

    @Remote
    public void orderSpoilt(JsonObject message) {
        try {
            Order order = new Order(message);
            if (activeOrders.containsKey( order.getId())) {
                Order activeOrder = new Order(activeOrders.get( order.getId()));
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
            logger.log(Level.SEVERE,"OrderManagerActor.orderSpoilt()", e);
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
