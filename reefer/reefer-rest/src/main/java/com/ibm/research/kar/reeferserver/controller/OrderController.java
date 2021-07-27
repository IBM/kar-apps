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
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.service.SimulatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("*")
public class OrderController {


    @Autowired
    private SimulatorService simulatorService;
    @Autowired
    private GuiController gui;

    private ActorRef orderMgrActor = Kar.Actors.ref(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId);

    private static final Logger logger = Logger.getLogger(OrderController.class.getName());

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
            Voyage voyage;

            voyage = getVoyage(voyageId);
            orderProperties.setProduct(req.getString(Constants.ORDER_PRODUCT_KEY));
            orderProperties.setProductQty(req.getInt(Constants.ORDER_PRODUCT_QTY_KEY));
            String customerId = "N/A";
            if (req.containsKey(Constants.ORDER_CUSTOMER_ID_KEY)) {
                customerId = req.getString(Constants.ORDER_CUSTOMER_ID_KEY);
            }
            orderProperties.setCustomerId(customerId);
            orderProperties.setVoyageId(voyageId);
            orderProperties.setOriginPort(voyage.getRoute().getOriginPort());
            orderProperties.setDestinationPort(voyage.getRoute().getDestinationPort());

        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return orderProperties;
    }

    private Voyage getVoyage(String voyageId) {
        ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
        JsonValue reply = Kar.Actors.call(scheduleActor, "voyage", Json.createValue(voyageId));
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
    public OrderProperties bookOrder(@RequestBody String message) throws IOException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("OrderController.bookOrder - Called -" + message);
        }
        long t = System.currentTimeMillis();
        OrderProperties orderProperties = null;
        try {
            // get Java POJO with order properties from json messages
            orderProperties = jsonToOrderProperties(message);
            Voyage voyage = getVoyage(orderProperties.getVoyageId());

            Order order = new Order(orderProperties);
            long t2 = System.currentTimeMillis();
            ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderActorType, order.getId());
            JsonValue reply = Kar.Actors.call(orderActor, "createOrder", order.getAsJsonObject());
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("OrderController.bookOrder - Order Actor reply:" + reply);
            }
            if (reply != null && reply.asJsonObject() != null && reply.asJsonObject().containsKey(Constants.STATUS_KEY)) {
                // check if the order was booked
                if (reply.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
                    // voyage actor computes freeCapacity
                    int freeCapacity = reply.asJsonObject().getInt(Constants.VOYAGE_FREE_CAPACITY_KEY);
                    // notify simulator of changed free capacity
                    simulatorService.updateVoyageCapacity(order.getVoyageId(), freeCapacity);
                    orderProperties.setBookingStatus(Constants.OK).setMsg("");
                } else {
                    orderProperties.setBookingStatus(Constants.FAILED).setOrderId("N/A").setMsg(reply.asJsonObject().getString("ERROR"));
                }
            } else {
                logger.log(Level.WARNING, "OrderController.bookOrder() - unexpected reply format - reply:" + reply);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            orderProperties.setBookingStatus(Constants.FAILED).setMsg(e.getMessage());
        }
        return orderProperties;
    }

    /**
     * Returns a list of voyages that are currently at sea
     *
     * @return
     */
    @GetMapping("/orders/list/active")
    public List<Order> getActiveOrderList() {
        JsonValue reply = Kar.Actors.call(orderMgrActor, "ordersInTransit");
        return reply.asJsonArray().stream().map(Order::new).collect(Collectors.toList());
    }

    /**
     * Returns a list of voyages that have been booked but not yet at sea
     *
     * @return
     */
    @GetMapping("/orders/list/booked")
    public List<Order> getBookedOrderList() {
        JsonValue reply = Kar.Actors.call(orderMgrActor, "ordersBooked");
        return reply.asJsonArray().stream().map(Order::new).collect(Collectors.toList());
    }

    /**
     * Returns a list of spoilt orders
     *
     * @return
     */
    @GetMapping("/orders/list/spoilt")
    public List<Order> getSpoiltOrderList() {
        JsonValue reply = Kar.Actors.call(orderMgrActor, "ordersSpoilt");
        return reply.asJsonArray().stream().map(Order::new).collect(Collectors.toList());
    }

    /**
     * Returns order related counts
     *
     * @return
     */
    @GetMapping("/orders/stats")
    public OrderStats getOrderStats() {

        Map<String, JsonValue> state = Kar.Actors.State.getAll(orderMgrActor);
        int bookedTotalCount = 0;
        int inTransitTotalCount = 0;
        int spoiltTotalCount = 0;

        if (!state.isEmpty()) {
            if (state.containsKey(Constants.TOTAL_BOOKED_KEY)) {
                bookedTotalCount = ((JsonNumber) state.get(Constants.TOTAL_BOOKED_KEY)).intValue();
            }
            if (state.containsKey(Constants.TOTAL_INTRANSIT_KEY)) {
                inTransitTotalCount = ((JsonNumber) state.get(Constants.TOTAL_INTRANSIT_KEY)).intValue();
            }
            if (state.containsKey(Constants.TOTAL_SPOILT_KEY)) {
                spoiltTotalCount = ((JsonNumber) state.get(Constants.TOTAL_SPOILT_KEY)).intValue();
            }
            if (state.containsKey(Constants.ORDER_METRICS_KEY)) {
                String metrics = ((JsonString) state.get(Constants.ORDER_METRICS_KEY)).getString();
                String[] values = metrics.split(":");

                bookedTotalCount = Integer.valueOf(values[0].trim());
                inTransitTotalCount = Integer.valueOf(values[1].trim());
                spoiltTotalCount = Integer.valueOf(values[2].trim());
            }
        }
        //   System.out.println("OrderController.getOrderStats()  ********** Booked:" + bookedTotalCount + " -- InTransit:" + inTransitTotalCount + " -- Spoilt:" + spoiltTotalCount);
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
