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

package com.ibm.research.kar.reeferserver.service;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.json.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


@Service
public class OrderService extends AbstractPersistentService {
    @Autowired
    private ScheduleService scheduleService;

    // number of the most recent orders to return to the GUI
    private static int MaxOrdersToReturn = 10;
    private static final Logger logger = Logger.getLogger(OrderService.class.getName());

    /**
     * Returns N most recent active orders where N = MaxOrdersToReturn
     *
     * @return- Most recent active orders
     */
    public List<Order> getActiveOrderList() {
        List<JsonValue> activeOrders = getListAJsonArray(Constants.ACTIVE_ORDERS_KEY);
        List<Order> sublist;
        if (activeOrders.size() <= MaxOrdersToReturn) {
            sublist = jsonToOrderList(activeOrders);
        } else {
            sublist = jsonToOrderList(
                    activeOrders.subList(activeOrders.size() - MaxOrdersToReturn, activeOrders.size()));
        }
        return sublist;
    }

    /**
     * Returns N most recent booked orders where N = MaxOrdersToReturn
     *
     * @return Most recent booked orders
     */
    public List<Order> getBookedOrderList() {
        List<JsonValue> bookedOrders = getListAJsonArray(Constants.BOOKED_ORDERS_KEY);
        List<Order> sublist;
        if (bookedOrders.size() <= MaxOrdersToReturn) {
            sublist = jsonToOrderList(bookedOrders);
        } else {
            sublist = jsonToOrderList(
                    bookedOrders.subList(bookedOrders.size() - MaxOrdersToReturn, bookedOrders.size()));
        }
        return sublist;
    }

    /**
     * Returns N most recent spoilt orders where N = MaxOrdersToReturn
     *
     * @return Most recent spoilt orders
     */
    public List<Order> getSpoiltOrderList() {
        List<JsonValue> spoiltOrders = getListAJsonArray(Constants.SPOILT_ORDERS_KEY);
        List<Order> sublist;
        if (spoiltOrders.size() <= MaxOrdersToReturn) {
            sublist = jsonToOrderList(spoiltOrders);
        } else {

            sublist = jsonToOrderList(
                    spoiltOrders.subList(spoiltOrders.size() - MaxOrdersToReturn, spoiltOrders.size()));
        }
        return sublist;
    }

    private List<Order> jsonToOrderList(List<JsonValue> jsonOrders) {
        List<Order> orders = new ArrayList<>();
        for (JsonValue v : jsonOrders) {
            Order order = new Order(v.asJsonObject().getString(Constants.ORDER_ID_KEY),
                    v.asJsonObject() == JsonValue.NULL ? "" : v.asJsonObject().getString(Constants.ORDER_PRODUCT_KEY),
                    v.asJsonObject() == JsonValue.NULL ? 0 :  v.asJsonObject().getInt(Constants.ORDER_PRODUCT_QTY_KEY),
                    v.asJsonObject() == JsonValue.NULL ? "" : v.asJsonObject().getString(Constants.VOYAGE_ID_KEY),
                    v.asJsonObject() == JsonValue.NULL ? "" : v.asJsonObject().getString(Constants.ORDER_STATUS_KEY), new ArrayList());
            orders.add(order);
        }
        return Collections.unmodifiableList(orders);
    }

    /**
     * Called when an order with given id gets spoiled which means that one or more
     * of its reefers became spoilt while in-transit.
     *
     * @param orderId Order id which became spoilt
     * @return Number of spoilt orders
     */
    public int orderSpoilt(String orderId) {
        JsonArray spoiltList;

        synchronized (OrderService.class) {
            JsonArray activeOrdersArray = getListAJsonArray(Constants.ACTIVE_ORDERS_KEY);

            JsonArrayBuilder spoiltOrderBuilder = Json.createArrayBuilder(getListAJsonArray(Constants.SPOILT_ORDERS_KEY));

            Iterator<JsonValue> activeIterator = activeOrdersArray.iterator();
            // find the matching order which needs to move from active to spoilt list
            while (activeIterator.hasNext()) {
                JsonObject order = activeIterator.next().asJsonObject();
                if (orderId.equals(order.getString(Constants.ORDER_ID_KEY))) {
                    spoiltOrderBuilder.add(0, order);
                    break;
                }
            }
            spoiltList = spoiltOrderBuilder.build();
            if (logger.isLoggable(Level.INFO)) {
                logger.info("OrderService.orderSpoilt() - spoilt order " + orderId + " active count:"
                        + activeOrdersArray.size() + " spoilt count:" + spoiltList.size() + " spoiltList:" + spoiltList);
            }
            // save new spoilt orders list in kar persistent storage
            set(Constants.SPOILT_ORDERS_KEY, spoiltList);

        }

        return spoiltList.size();
    }

    /**
     * Checks is a given order is already in the spoilt list
     *
     * @param orderId - Order
     * @return true if order already spoilt
     */
    public boolean orderAlreadySpoilt(String orderId) {
        JsonArray spoiltOrdersArray = getListAJsonArray(Constants.SPOILT_ORDERS_KEY);
        Iterator<JsonValue> spoiltIterator = spoiltOrdersArray.iterator();
        while (spoiltIterator.hasNext()) {
            JsonObject order = spoiltIterator.next().asJsonObject();
            if (orderId.equals(order.getString(Constants.ORDER_ID_KEY))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called when a new order is received.
     *
     * @param orderProperties Order properties
     * @return new Order instance
     */
    public Order createOrder(OrderProperties orderProperties) {
        Order order = new Order(orderProperties);
        synchronized (OrderService.class) {
            JsonValue newOrder = Json.createObjectBuilder().
                    add("orderId", order.getId()).
                    add("voyageId", order.getVoyageId()).
                    add("product", order.getProduct()).
                    add("productQty", order.getProductQty()).
                    add("order-status", order.getStatus()).
                    build();

            JsonArrayBuilder bookedOrderArrayBuilder = Json
                    .createArrayBuilder(getListAJsonArray(Constants.BOOKED_ORDERS_KEY));
            bookedOrderArrayBuilder.add(newOrder);
            JsonArray bookedOrdersArray = bookedOrderArrayBuilder.build();
            set(Constants.BOOKED_ORDERS_KEY, bookedOrdersArray);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("OrderService.createOrder() - added future order id:" + order.getId() + " voyageId:"
                        + order.getVoyageId() + " booked Order:" + bookedOrdersArray.size());
            }
            orderProperties.setOrderId(order.getId());
        }

        return order;
    }

    private Set<Voyage> findVoyagesBeyondDepartureDate(JsonArray bookedOrders) {
        Instant today = TimeUtils.getInstance().getCurrentDate();
        return bookedOrders.
                stream().
                map(jv -> {
                    try {
                        return scheduleService.getVoyage(jv.asJsonObject().getString(Constants.VOYAGE_ID_KEY));
                    } catch (VoyageNotFoundException e) {
                        return null;
                    }
                }).
                filter(Objects::nonNull).
                filter(v -> TimeUtils.getInstance().getDaysBetween(Instant.parse(v.getArrivalDate()), today) > 5).
                collect(Collectors.toSet());
    }

    private Set<Voyage> findVoyagesBeyondArrivalDate(JsonArray activeOrders) {
        Instant today = TimeUtils.getInstance().getCurrentDate();
        return activeOrders.
                stream().
                map(jv -> {
                    try {
                        return scheduleService.getVoyage(jv.asJsonObject().getString(Constants.VOYAGE_ID_KEY));
                    } catch (VoyageNotFoundException e) {
                        return null;
                    }
                }).
                filter(Objects::nonNull).
                filter(v -> TimeUtils.getInstance().getDaysBetween(Instant.parse(v.getArrivalDate()), today) > 5).
                collect(Collectors.toSet());
    }

    /**
     * Returns aggregate counts for booked, active, spoilt and on-maintenance orders
     *
     * @return order counts
     */
    public OrderStats getOrderStats() {
        return new OrderStats(getOrderCount(Constants.ACTIVE_ORDERS_KEY), getOrderCount(Constants.BOOKED_ORDERS_KEY),
                getOrderCount(Constants.SPOILT_ORDERS_KEY));
    }

    /**
     * Given the order list type return a count. For example if
     * orderKindKey=Contstants.BOOKED_ORDERS_KEY the method returns total number of
     * booked orders
     *
     * @param orderListKindKey - type of order list (active,booked,spoilt,
     *                         on-maintenance)
     * @return number of orders
     */
    public int getOrderCount(String orderListKindKey) {
        synchronized (OrderService.class) {
            try {
                JsonValue o = get(orderListKindKey);
                if (o == null) {
                    o = Json.createArrayBuilder().build();
                    set(orderListKindKey, o);
                }
                JsonArray orderArray = o.asJsonArray();
                return orderArray.size();
            } catch (Exception e) {
                logger.log(Level.WARNING, "OrderService.getOrderCount() - Error - ", e);
            }
        }

        return 0;
    }

    private JsonArray toJsonArray(List<JsonValue> list) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (JsonValue v : list) {
            arrayBuilder.add(v);
        }
        return arrayBuilder.build();
    }

    /**
     * Called when a ship (in a voyage) departs from an origin port. Moves orders
     * associated with a given voyage from booked to active list.
     *
     * @param voyageId Voyage id
     */
    private void voyageDeparted(String voyageId) {
        synchronized (OrderService.class) {
            List<JsonValue> newBookedOrderList = getMutableOrderList(Constants.BOOKED_ORDERS_KEY);
            JsonArray newActiveOrderList = getListAJsonArray(Constants.ACTIVE_ORDERS_KEY);

            JsonArrayBuilder activeOrderBuilder = Json.createArrayBuilder(newActiveOrderList);
            // Move booked to active list
            Iterator<JsonValue> it = newBookedOrderList.iterator();
            while (it.hasNext()) {
                JsonValue v = it.next();
                if (voyageId.equals(v.asJsonObject().getString(Constants.VOYAGE_ID_KEY))) {
                    activeOrderBuilder.add(v);
                    // remove from booked
                    it.remove();
                }
            }
            JsonArray activeArray = activeOrderBuilder.build();

            set(Constants.ACTIVE_ORDERS_KEY, activeArray);
            set(Constants.BOOKED_ORDERS_KEY, toJsonArray(newBookedOrderList));
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("OrderService.voyageDeparted() - voyage:" + voyageId + " departed today:" +
                        TimeUtils.getInstance().getCurrentDate() + " booked voyages:"
                        + newBookedOrderList.size() + " active voyages:" + activeArray.size());
            }
        }
    }

    private JsonArray getListAJsonArray(String orderListKind) {
        JsonValue orders = get(orderListKind);
        if (orders == null) {
            return Json.createArrayBuilder().build();
        } else {
            return orders.asJsonArray();
        }

    }

    private List<JsonValue> getMutableOrderList(String orderListKind) {
        // fetch immutable list of orders from Kar persistent storage
        JsonArray orders = getListAJsonArray(orderListKind);
        // Can't use Collectors.toList() here since we need mutable list. According
        // to java documentation there is no guarantee for the toList() to return
        // a mutable list. Instead use Supplier (ArrayList)
        return orders.stream().distinct().collect(Collectors.toCollection(ArrayList<JsonValue>::new));
    }

    private void removeVoyageOrdersFromList(String voyageId, List<JsonValue> orderList) {
        Iterator<JsonValue> it = orderList.iterator();
        while (it.hasNext()) {
            JsonObject order = it.next().asJsonObject();
            if (voyageId.equals(order.getString(Constants.VOYAGE_ID_KEY))) {
                it.remove();
            }
        }
    }

    /**
     * Called when a voyage ends at destination port. When a ship arrives it may
     * have spoilt reefers aboard which went bad while in-transit. If even one
     * reefer becomes spoilt in an order, for simplicity, we spoil an entire order.
     * Removes voyage orders from both active and spoilt lists.
     *
     * @param voyageId
     */
    private void voyageArrived(String voyageId) {
        synchronized (OrderService.class) {
            List<JsonValue> newSpoiltList = getMutableOrderList(Constants.SPOILT_ORDERS_KEY);
            List<JsonValue> newActiveList = getMutableOrderList(Constants.ACTIVE_ORDERS_KEY);
            removeVoyageOrdersFromList(voyageId, newSpoiltList);
            removeVoyageOrdersFromList(voyageId, newActiveList);
            set(Constants.SPOILT_ORDERS_KEY, toJsonArray(newSpoiltList));
            set(Constants.ACTIVE_ORDERS_KEY, toJsonArray(newActiveList));
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("OrderService.voyageArrived() - voyageId:" + voyageId
                        + " - Active Orders:" + newActiveList.size() + " Spoilt Orders:" + newSpoiltList.size());
            }
        }
    }

    /**
     * Called when a voyage either departs its origin port or arrives at destination
     * port.
     *
     * @param voyageId Voyage id
     * @param status   - Voyage status
     */
    public void updateOrderStatus(String voyageId, OrderStatus status) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("OrderService.updateOrderStatus() - voyageId:" + voyageId + " Status:" + status);
        }
        if (voyageId == null) {
            logger.warning("OrderService.updateOrderStatus() - voyageId is null, rejecting update request");
            return;
        }
        if (OrderStatus.DELIVERED.equals(status)) {
            voyageArrived(voyageId);
            // check if there are voyages that should have arrived but didn't (due to REST crash)
            Set<Voyage> neverArrivedList =
                    findVoyagesBeyondArrivalDate(toJsonArray(getMutableOrderList(Constants.ACTIVE_ORDERS_KEY)));
            // force arrival to reclaim reefers and clean orders
            neverArrivedList.forEach(v -> forceArrival(v));
        } else if (OrderStatus.INTRANSIT.equals(status)) {
            voyageDeparted(voyageId);
            // check if there are voyages that should have departed but didn't (due to REST crash)
            Set<Voyage> neverDepartedList =
                    findVoyagesBeyondDepartureDate(toJsonArray(getMutableOrderList(Constants.BOOKED_ORDERS_KEY)));
            // force arrival to reclaim reefers and clean orders
            neverDepartedList.forEach(v -> voyageDeparted(v.getId()));
        }
    }

    private void forceArrival(Voyage voyage) {
        voyageArrived(voyage.getId());
        try {
            Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "releaseVoyageReefers",
                    Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, voyage.getId()).build());
            logger.warning("OrderService.forceArrival() - forced reefers release - voyage:" + voyage.getId());

        } catch (Exception e) {
            logger.warning("OrderService.forceArrival() - ReeferProvisioner.releaseVoyageReefers call failed - cause: " + e.getMessage());
        }
    }

}
