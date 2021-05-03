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
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderStats;
import org.springframework.stereotype.Service;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


@Service
public class OrderService { //extends AbstractPersistentService {
    // number of the most recent orders to return to the GUI
    private static int MaxOrdersToReturn = 10;
    private static final Logger logger = Logger.getLogger(OrderService.class.getName());

    TreeSet<Order> activeOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));
    TreeSet<Order> bookedOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));
    TreeSet<Order> spoiltOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));
    private OrderPersistence storage = new OrderPersistence();

    /**
     * Returns N most recent active orders where N = MaxOrdersToReturn
     *
     * @return- Most recent active orders
     */
    public List<Order> getActiveOrderList() {
        synchronized (OrderService.class) {
            return activeOrders.descendingSet().stream().limit(MaxOrdersToReturn).collect(Collectors.toList());
        }

    }

    /**
     * Returns N most recent booked orders where N = MaxOrdersToReturn
     *
     * @return Most recent booked orders
     */
    public List<Order> getBookedOrderList() {
        synchronized (OrderService.class) {
            return bookedOrders.descendingSet().stream().limit(MaxOrdersToReturn).collect(Collectors.toList());
        }

    }

    /**
     * Returns N most recent spoilt orders where N = MaxOrdersToReturn
     *
     * @return Most recent spoilt orders
     */
    public List<Order> getSpoiltOrderList() {
        synchronized (OrderService.class) {
            return spoiltOrders.descendingSet().stream().limit(MaxOrdersToReturn).peek(o -> System.out.println(o.getDate())).collect(Collectors.toList());
        }

    }


    /**
     * Called when an order with given id gets spoiled which means that one or more
     * of its reefers became spoilt while in-transit.
     *
     * @param orderId Order id which became spoilt
     * @return Number of spoilt orders
     */
    public int orderSpoilt(String orderId) {
        synchronized (OrderService.class) {
            for (Order order : activeOrders) {
                if (orderId.equals(order.getId())) {
                    // need to persist the flag to restore orders on restart
                    order.setSpoilt(true);
                    spoiltOrders.add(order);
                    storage.saveOrder(order.getId(), order.getAsJsonObject());
                    break;
                }
            }
            return spoiltOrders.size();
        }
    }

    /**
     * Checks is a given order is already in the spoilt list
     *
     * @param orderId - Order
     * @return true if order already spoilt
     */
    public boolean orderAlreadySpoilt(String orderId) {
        synchronized (OrderService.class) {
            for (Order order : spoiltOrders) {
                if (order.equals(orderId)) {
                    return true;
                }
            }
            return false;
        }
    }
    public void saveOrder(Order order) {
        synchronized (OrderService.class) {
            bookedOrders.add(order);
            storage.saveOrder(order.getId(), order.getAsJsonObject());
        }

    }

    /**
     * Returns aggregate counts for booked, active, and spoilt orders
     *
     * @return order counts
     */
    public OrderStats getOrderStats() {
        synchronized (OrderService.class) {
            return new OrderStats(activeOrders.size(), bookedOrders.size(), spoiltOrders.size());
        }
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
            int orderCount = 0;
            switch (orderListKindKey) {
                case Constants.ACTIVE_ORDERS_KEY:
                    orderCount = activeOrders.size();
                    break;
                case Constants.BOOKED_ORDERS_KEY:
                    orderCount = bookedOrders.size();
                    break;

                case Constants.SPOILT_ORDERS_KEY:
                    orderCount = spoiltOrders.size();
                    break;
            }
            return orderCount;
        }


    }

    /**
     * Called when a ship (in a voyage) departs from an origin port. Moves orders
     * associated with a given voyage from booked to active list.
     *
     * @param voyageId Voyage id
     */
    private void voyageDeparted(String voyageId, List<String> voyageOrderList) {
        synchronized (OrderService.class) {
            if (!voyageOrderList.isEmpty()) {
                List<Order> orders = departedOrderList(voyageOrderList);
                Map<String, JsonValue> newActiveOrders =
                        orders.stream().filter(Objects::nonNull).
                                peek(o -> o.setStatus(Order.OrderStatus.INTRANSIT.toString())).
                                collect(Collectors.toMap(Order::getId, order -> order.getAsJsonObject()));
                activeOrders.addAll(orders);
                storage.saveOrders(newActiveOrders);
                bookedOrders.removeAll(orders);
            }

        }
    }

    private List<Order> departedOrderList(List<String> voyageOrderList) {
        return voyageOrderList.stream().map(orderId -> {
            for (Order o : bookedOrders) {
                if (o.getId() != null && o.getId().equals(orderId)) {
                    return o;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Called when a voyage ends at destination port. When a ship arrives it may
     * have spoilt reefers aboard which went bad while in-transit. If even one
     * reefer becomes spoilt in an order, for simplicity, we spoil an entire order.
     * Removes voyage orders from both active and spoilt lists.
     *
     * @param voyageId
     */
    public void voyageArrived(String voyageId, List<String> voyageOrderList) {
        synchronized (OrderService.class) {
            if (!voyageOrderList.isEmpty()) {
                removeArrivedOrders(voyageOrderList);
                removeSpoiltOrders(voyageOrderList);
            }
        }
    }

    private void removeArrivedOrders(List<String> voyageOrderList) {
        List<Order> arrived = getOrders(voyageOrderList, activeOrders);

        activeOrders.removeAll(arrived);
        storage.remove(arrived.stream().map(Order::getId).collect(Collectors.toList()));
    }

    private List<Order> getOrders(List<String> voyageOrderList, TreeSet<Order> orders) {
        return voyageOrderList.stream().map(orderId -> {
            for (Order o : orders) {
                if (o.getId() != null && o.getId().equals(orderId)) {
                    return o;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private void removeSpoiltOrders(List<String> voyageOrderList) {
        List<Order> spoilt = getOrders(voyageOrderList, spoiltOrders);
        spoiltOrders.removeAll(spoilt);
    }

    /**
     * Called when a voyage either departs its origin port or arrives at destination
     * port.
     *
     * @param voyageId Voyage id
     * @param status   - Voyage status
     */
    public void updateOrderStatus(String voyageId, Order.OrderStatus status, List<String> voyageOrderList) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("OrderService.updateOrderStatus() - voyageId:" + voyageId + " Status:" + status);
        }
        if (voyageId == null) {
            logger.warning("OrderService.updateOrderStatus() - voyageId is null, rejecting update request");
            return;
        }
        if (Order.OrderStatus.DELIVERED.equals(status)) {
            voyageArrived(voyageId, voyageOrderList);
         } else if (Order.OrderStatus.INTRANSIT.equals(status)) {
            voyageDeparted(voyageId, voyageOrderList);
        }
    }
    private class OrderPersistence {
        ActorRef aRef = Kar.Actors.ref(ReeferAppConfig.RestActorName, ReeferAppConfig.RestActorId);

        OrderPersistence() {
            System.out.println("OrderService.OrderPersistence.ctor - Restoring orders");
            restoreOrders();
        }

        void remove(List<String> keys2Remove) {
            Kar.Actors.State.Submap.removeAll(aRef, Constants.REST_ORDERS_KEY, keys2Remove);
        }

        void saveOrders(Map<String, JsonValue> orders) {
            Kar.Actors.State.Submap.set(aRef, Constants.REST_ORDERS_KEY, orders);
        }

        void saveOrder(String key, JsonObject order) {
            Kar.Actors.State.Submap.set(aRef, Constants.REST_ORDERS_KEY, key, order);
        }

        void restoreOrders() {
            Map<String, JsonValue> orders = Kar.Actors.State.Submap.getAll(aRef, Constants.REST_ORDERS_KEY);

            for (JsonValue jv : orders.values()) {
                if (jv == null) {
                    continue;
                }
                Order order = new Order(jv);
                switch (OrderStatus.valueOf(order.getStatus().toUpperCase())) {
                    case INTRANSIT:
                        if (order.isSpoilt()) {
                            spoiltOrders.add(order);
                        }
                        activeOrders.add(order);
                        break;
                    case BOOKED:
                        bookedOrders.add(order);
                        break;
                }
            }

            System.out.println("OrderService.OrderPersistence.ctor - Restored orders - active:" + activeOrders.size() + " booked:" + bookedOrders.size() + " spoilt:" + spoiltOrders.size());
        }
    }

    public class FixedSizeQueue<Order> extends ArrayBlockingQueue<Order> {
        private int size;

        public FixedSizeQueue(int capacity) {
            super(capacity);
            this.size = capacity;
        }

        // Drops the oldest element when full
        @Override
        synchronized public boolean add(Order e) {
            if (super.size() == this.size) {
                // removes the oldest element from the queue
                this.remove();
            }
            return super.add(e);
        }

    }

}
