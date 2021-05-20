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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Actor
public class OrderManagerActor extends BaseActor {

    private int maxOrderCount = 10;
    // need separate queues for each state type. Can't use single list as the
    // booked orders are more frequent and would push all the other types out of
    // the bounded queue
    private FixedSizeQueue activeOrderList = new FixedSizeQueue(maxOrderCount);
    private FixedSizeQueue bookedOrderList = new FixedSizeQueue(maxOrderCount);
    private FixedSizeQueue spoiltOrderList = new FixedSizeQueue(maxOrderCount);

    private AtomicBoolean valuesChanged = new AtomicBoolean();
    private AtomicInteger bookedTotalCount = new AtomicInteger();
    private AtomicInteger inTransitTotalCount = new AtomicInteger();
    private AtomicInteger spoiltTotalCount = new AtomicInteger();

    private static final Logger logger = Logger.getLogger(OrderManagerActor.class.getName());

    @Activate
    public void activate() {

        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        try {
            // initial actor invocation should handle no state
            if (!state.isEmpty()) {
                if (state.containsKey(Constants.TOTAL_BOOKED_KEY)) {
                    bookedTotalCount.set(((JsonNumber) state.get(Constants.TOTAL_BOOKED_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_INTRANSIT_KEY)) {
                    inTransitTotalCount.set(((JsonNumber) state.get(Constants.TOTAL_INTRANSIT_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_SPOILT_KEY)) {
                    spoiltTotalCount.set(((JsonNumber) state.get(Constants.TOTAL_SPOILT_KEY)).intValue());
                }
                if (state.containsKey(Constants.BOOKED_ORDERS_KEY)) {
                    bookedOrderList.addAll(restoreRecentOrders(state.get(Constants.BOOKED_ORDERS_KEY)));
                }
                if (state.containsKey(Constants.ACTIVE_ORDERS_KEY)) {
                    activeOrderList.addAll(restoreRecentOrders(state.get(Constants.ACTIVE_ORDERS_KEY)));
                }
                if (state.containsKey(Constants.SPOILT_ORDERS_KEY)) {
                    spoiltOrderList.addAll(restoreRecentOrders(state.get(Constants.SPOILT_ORDERS_KEY)));
                }
                System.out.println("OrderManagerActor.activate() - Totals - totalInTransit:"+inTransitTotalCount+" totalBooked: "+bookedTotalCount+" totalSpoilt:"+spoiltTotalCount+" activeOrdersList:" + activeOrderList.size() +
                        " bookedOrdersList:" + bookedOrderList.size() + " spoiltOrdersList:" + spoiltOrderList.size());
            }
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private List<Order> restoreRecentOrders(JsonValue jv) throws Exception {
        try {
            if (jv != null && jv != JsonValue.NULL) {
                JsonArray ja = jv.asJsonArray();
                return ja.stream().map(Order::new).collect(Collectors.toList());
            }
        } catch ( Exception e) {
            e.printStackTrace();
        }

        throw new IllegalArgumentException("Not able to restore order list - the list is null");
    }

    @Remote
    public void orderBooked(JsonObject message) {

        try {
            JsonObjectBuilder jo = Json.createObjectBuilder();
            Order order = new Order(message);
            bookedOrderList.add(order);
            valuesChanged.set(true);
            bookedTotalCount.incrementAndGet();
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount.intValue())).
                    add(Constants.BOOKED_ORDERS_KEY, bookedOrderList.getAll());
            Kar.Actors.State.set(this, job.build());
        } catch( Exception e) {
            e.printStackTrace();
        }

    }

    @Remote
    public void ordersDeparted(JsonValue message) {
        try {
            JsonArray ja = message.asJsonObject().getJsonArray(Constants.VOYAGE_ORDERS_KEY);
            ja.forEach(jo -> {
                Order order = new Order(jo.asJsonObject());
                activeOrderList.add(order);
                bookedOrderList.remove(order);
                inTransitTotalCount.incrementAndGet();
                long b = bookedTotalCount.get();
                bookedTotalCount.addAndGet(-1);
            });
            valuesChanged.set(true);
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount.intValue())).
                    add(Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount.intValue())).
                    add(Constants.BOOKED_ORDERS_KEY, bookedOrderList.getAll()).
                    add(Constants.ACTIVE_ORDERS_KEY, activeOrderList.getAll());

            Kar.Actors.State.set(this, job.build());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Remote
    public void ordersArrived(JsonValue message) {
        try {
            JsonArray ja = message.asJsonObject().getJsonArray(Constants.VOYAGE_ORDERS_KEY);
            ja.forEach(jo -> {
                Order order = new Order(jo.asJsonObject());
                activeOrderList.remove(order);
                spoiltOrderList.remove(order);
                long t = inTransitTotalCount.get();
                inTransitTotalCount.addAndGet(-1);
                if (order.isSpoilt()) {
                    spoiltTotalCount.addAndGet(-1);
                }
            });
            valuesChanged.set(true);
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount.intValue())).
                    add(Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount.intValue())).
                    add(Constants.ACTIVE_ORDERS_KEY, activeOrderList.getAll()).
                    add(Constants.SPOILT_ORDERS_KEY, spoiltOrderList.getAll());
            Kar.Actors.State.set(this, job.build());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Remote
    public void orderSpoilt(JsonObject message) {
        try {
            Order order = new Order(message);
            spoiltOrderList.add(order);
            spoiltTotalCount.incrementAndGet();
            valuesChanged.set(true);
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount.intValue())).add(Constants.SPOILT_ORDERS_KEY, spoiltOrderList.getAll());
            Kar.Actors.State.set(this, job.build());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }
}
