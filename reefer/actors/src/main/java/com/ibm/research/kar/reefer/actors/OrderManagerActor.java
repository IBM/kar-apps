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

    private int bookedTotalCount = 0;
    private int inTransitTotalCount = 0;
    private int spoiltTotalCount = 0;

    private static final Logger logger = Logger.getLogger(OrderManagerActor.class.getName());

    @Activate
    public void activate() {

        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        try {
            // initial actor invocation should handle no state
            if (!state.isEmpty()) {
                if (state.containsKey(Constants.TOTAL_BOOKED_KEY)) {
                    bookedTotalCount = (((JsonNumber) state.get(Constants.TOTAL_BOOKED_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_INTRANSIT_KEY)) {
                    inTransitTotalCount = (((JsonNumber) state.get(Constants.TOTAL_INTRANSIT_KEY)).intValue());
                }
                if (state.containsKey(Constants.TOTAL_SPOILT_KEY)) {
                    spoiltTotalCount = (((JsonNumber) state.get(Constants.TOTAL_SPOILT_KEY)).intValue());
                }

                System.out.println("OrderManagerActor.activate() - Totals - totalInTransit:"+inTransitTotalCount+" totalBooked: "+bookedTotalCount+" totalSpoilt:"+spoiltTotalCount);

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
            JsonObjectBuilder jo = Json.createObjectBuilder();
            Order order = new Order(message);
            bookedOrderList.add(order);
            bookedTotalCount++;
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount));
            Kar.Actors.State.set(this, job.build());
        } catch( Exception e) {
            e.printStackTrace();
        } finally {
            //System.out.println("OrderManager.orderBooked - time spent here - " + (System.currentTimeMillis()-t)+" ms");
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
                inTransitTotalCount++;
                bookedTotalCount--;
            });
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_BOOKED_KEY, Json.createValue(bookedTotalCount)).
                    add(Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount));

            Kar.Actors.State.set(this, job.build());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
    @Remote
    public void ordersArrived(JsonValue message) {

    }
    @Remote
    public void orderArrived(JsonValue message) {
        try {
            Order order = new Order(message);

            activeOrderList.remove(order);
            inTransitTotalCount--;
            if (order.isSpoilt()) {
                spoiltTotalCount--;
                spoiltOrderList.remove(order);
            }
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount)).
                    add(Constants.TOTAL_INTRANSIT_KEY, Json.createValue(inTransitTotalCount));
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
            spoiltTotalCount++;
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltTotalCount));
            Kar.Actors.State.set(this, job.build());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }
}
