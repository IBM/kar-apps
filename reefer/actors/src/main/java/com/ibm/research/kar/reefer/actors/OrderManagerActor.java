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
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.FixedSizeQueue;
import com.ibm.research.kar.reefer.model.Order;

import javax.json.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Actor
public class OrderManagerActor extends BaseActor {

    private int maxOrderCount = 10;
    // need separate queues for each state type. Can't use single list as the
    // booked orders are more frequent and would push all the other types out of
    // the bounded queue
    private FixedSizeQueue activeOrderList = new FixedSizeQueue(maxOrderCount);
    private FixedSizeQueue bookedOrderList = new FixedSizeQueue(maxOrderCount);
    private FixedSizeQueue spoiltOrderList = new FixedSizeQueue(maxOrderCount);

    private Map<String, JsonValue> activeOrders = new HashMap<>();
    private int bookedTotalCount = 0;
    private int inTransitTotalCount = 0;
    private int spoiltTotalCount = 0;

    private String orderMetrics = "";
    private static final Logger logger = Logger.getLogger(OrderManagerActor.class.getName());

    @Activate
    public void activate() {

        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        try {
            // initial actor invocation should handle no state
            if (!state.isEmpty()) {
                /*
                if (state.containsKey(Constants.TOTAL_BOOKED_KEY)) {
                    bookedTotalCount = (((JsonNumber) state.get(Constants.TOTAL_BOOKED_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_INTRANSIT_KEY)) {
                    inTransitTotalCount = (((JsonNumber) state.get(Constants.TOTAL_INTRANSIT_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_SPOILT_KEY)) {
                    spoiltTotalCount = (((JsonNumber) state.get(Constants.TOTAL_SPOILT_KEY)).intValue());
                }

                 */
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
                    System.out.println("OrderManagerActor.activate() - time to restore active orders:"+ (System.currentTimeMillis() - t) + " millis");
                }
                System.out.println("OrderManagerActor.activate() - Totals - totalInTransit:" + inTransitTotalCount + " totalBooked: " + bookedTotalCount + " totalSpoilt:" + spoiltTotalCount);

            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Remote
    public void orderBooked(JsonObject message) {
        long t = System.currentTimeMillis();
        try {
            Order order = new Order(message);
            if ( !activeOrders.containsKey(order.getId())) {
                JsonObjectBuilder jo = Json.createObjectBuilder();
                activeOrders.put(order.getId(), order.getAsJsonObject());
                Kar.Actors.State.Submap.set(this, Constants.ORDERS_KEY, order.getId(), order.getAsJsonObject());

                bookedOrderList.add(order);
                bookedTotalCount++;
                saveMetrics();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //System.out.println("OrderManager.orderBooked - time spent here - " + (System.currentTimeMillis()-t)+" ms");
        }

    }

    @Remote
    public void orderDeparted(JsonValue message) {
        try {
            Order order = new Order(message);
            if ( activeOrders.containsKey(order.getId())) {
                Order activeOrder = new Order(activeOrders.get(order.getId()));
                // idempotence check to prevent double counting
                if ( !Order.OrderStatus.INTRANSIT.name().equals(activeOrder.getStatus())) {
                    activeOrderList.add(order);
                    bookedOrderList.remove(order);
                    inTransitTotalCount++;
                    bookedTotalCount--;
                    order.setStatus(Order.OrderStatus.INTRANSIT.name());
                    Kar.Actors.State.Submap.set(this, Constants.ORDERS_KEY, order.getId(), order.getAsJsonObject());

                    saveMetrics();
                }

            } else {

            }

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Remote
    public void orderArrived(JsonValue message) {
        try {
            Order order = new Order(message);
            if ( activeOrders.containsKey(order.getId())) {
                Order activeOrder = new Order(activeOrders.get(order.getId()));
                activeOrderList.remove(order);
                inTransitTotalCount--;
                activeOrders.remove(order);
                Kar.Actors.State.Submap.remove(this, Constants.ORDERS_KEY, order.getId() );
                if (activeOrder.isSpoilt()) {
                    spoiltTotalCount--;
                    spoiltOrderList.remove(activeOrder);
                }
                saveMetrics();
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Remote
    public void orderSpoilt(JsonObject message) {
        try {
            Order order = new Order(message);
            if ( activeOrders.containsKey(order.getId())) {
                Order activeOrder = new Order(activeOrders.get(order.getId()));
                // idempotence check to prevent double counting
                if (!activeOrder.isSpoilt()) {

                    spoiltOrderList.add(order);
                    spoiltTotalCount++;
                    //System.out.println("OrderManager.orderSpoilt() - order spoilt - count:"+spoiltTotalCount);
                    order.setSpoilt(true);
                    Kar.Actors.State.Submap.set(this, Constants.ORDERS_KEY, order.getId(), order.getAsJsonObject());
                    saveMetrics();
                }
            } else {
                System.out.println("OrderManager.orderSpoilt() - order spoilt - but not found in orders map - id:"+order.getId());
            }

        } catch (Exception e) {
            e.printStackTrace();
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
        return getOrderList(activeOrderList);
    }
    private JsonValue getOrderList(FixedSizeQueue orders) {
        JsonArrayBuilder jab = Json.createArrayBuilder();
        orders.forEach(order -> jab.add(order.getAsJsonObject()));
        return jab.build();
    }
    private void saveMetrics() {
        String metrics = String.format("%d:%d:%d", bookedTotalCount, inTransitTotalCount, spoiltTotalCount);
        //System.out.println("OrderManager.saveMetrics() - "+metrics);
        Kar.Actors.State.set(this, Constants.ORDER_METRICS_KEY, Json.createValue(metrics));
    }
}
