package com.ibm.research.kar.reeferserver.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonValue;

import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.OrderStats;

import org.springframework.stereotype.Service;

@Service
public class OrderService extends AbstractPersistentService {

    private static int MaxOrdersToReturn = 10;
    private List<Order> bookedOrders = new ArrayList<>();
    private List<Order> activeOrders = new ArrayList<>();
    private List<Order> spoiltOrders = new ArrayList<>();
    private List<Order> onMaintenanceOrders = new ArrayList<>();

    public List<Order> getActiveOrderList() {
        if (activeOrders.size() <= MaxOrdersToReturn) {
            return Collections.unmodifiableList(activeOrders);
        } else {
            return activeOrders.subList(activeOrders.size() - 10, activeOrders.size());
        }

    }

    public List<Order> getBookedOrderList() {
        synchronized (bookedOrders) {
            if (bookedOrders.size() <= MaxOrdersToReturn) {
                return bookedOrders;
            } else {
                return bookedOrders.subList(bookedOrders.size() - 10, bookedOrders.size());
            }

        }

    }

    public List<Order> getSpoiltOrderList() {
        synchronized (spoiltOrders) {
            if (spoiltOrders.size() <= MaxOrdersToReturn) {
                return spoiltOrders;
            } else {
                return spoiltOrders.subList(spoiltOrders.size() - 10, spoiltOrders.size());
            }

        }

    }

    public int orderSpoilt(String orderId) {
        JsonValue spoiltOrdersList = get(Constants.SPOILT_ORDERS_KEY);
        JsonArrayBuilder builder = null;
        if (spoiltOrders != null && spoiltOrders != JsonValue.NULL) {
            builder = Json.createArrayBuilder(spoiltOrdersList.asJsonArray());
        } else {
            builder = Json.createArrayBuilder();
        }

        Iterator<Order> activeIterator = activeOrders.iterator();
        while (activeIterator.hasNext()) {
            Order order = activeIterator.next();
            if (orderId.equals(order.getId())) {

                spoiltOrders.add(order);
                JsonValue spoiltOrder = Json.createObjectBuilder().add("orderId", order.getId())
                        .add("voyageId", order.getVoyageId()).build();
                builder.add(spoiltOrder);
                break;
            }
        }
        JsonArray spoiltList = builder.build();
        set(Constants.SPOILT_ORDERS_KEY, spoiltList);
        return spoiltList.size();
    }

    public Order createOrder(OrderProperties orderProperties) {
        Order order = new Order(orderProperties);
        bookedOrders.add(order);
        JsonArrayBuilder bookedOrderArrayBuilder = null;
        // fetch booked orders from Kar
        JsonValue bookedOrders = get(Constants.BOOKED_ORDERS_KEY);
        if (bookedOrders == null) {
            bookedOrderArrayBuilder = Json.createArrayBuilder();
        } else {
            bookedOrderArrayBuilder = Json.createArrayBuilder();
            JsonArray orderList = bookedOrders.asJsonArray();
            System.out.println("OrderService.createOrder - cached order list size:" + orderList.size());
            // since orderList is immutable we need copy current booked orders into a new
            // list and then add our new order to it. Could not find a way around this.
            // Attempt to modify the list from Kar causes UnsuportedOpertion exception.
            for (JsonValue savedOrder : orderList) {
                bookedOrderArrayBuilder.add(savedOrder);
            }
        }

        JsonValue newOrder = Json.createObjectBuilder().add("orderId", order.getId())
                .add("voyageId", order.getVoyageId()).build();

        bookedOrderArrayBuilder.add(newOrder);
        // once the build() is called the list is immutable
        JsonArray orderArray = bookedOrderArrayBuilder.build();
        // save the new booked orders list in Kar
        set(Constants.BOOKED_ORDERS_KEY, orderArray);
        System.out.println("OrderService.createOrder() - added future order id:" + order.getId() + " voyageId:"
                + order.getVoyageId() + " booked Order:" + orderArray.size());
        return order;
    }

    public OrderStats getOrderStats() {
        return new OrderStats(getOrders(Constants.ACTIVE_ORDERS_KEY), getOrders(Constants.BOOKED_ORDERS_KEY),
                getOrders(Constants.SPOILT_ORDERS_KEY));
    }

    public int getOrders(String orderKindKey) {
        try {
            JsonValue o = get(orderKindKey);
            System.out.println("OrderService.getOrders() -" + orderKindKey + " orders: o=" + o);
            if (o == null) {
                o = Json.createArrayBuilder().build();
                System.out.println("OrderService.getOrders() - NEW LIST FOR BOOKED ORDERS ");
                set(orderKindKey, o);
            }
            JsonArray orderArray = o.asJsonArray();
            System.out.println(
                    "OrderService.getOrders() - " + orderKindKey + " orders:" + orderArray.size());
            return orderArray.size();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getActiveOrders(String voyageId) {

        int voyageOrders = 0;
        JsonValue activeOrders = get(Constants.ACTIVE_ORDERS_KEY);
        JsonArray activeOrderArray = activeOrders.asJsonArray();
        Iterator<JsonValue> it = activeOrderArray.iterator();
        // move booked voyage orders to active
        while (it.hasNext()) {
            JsonValue v = it.next();
            if (voyageId.equals(v.asJsonObject().getString(Constants.VOYAGE_ID_KEY))) {
                voyageOrders++;
            }
        }
        return voyageOrders;
    }

    private void moveBookedOrdersToActive(String departedVoyageId) {
        synchronized (bookedOrders) {
            Iterator<Order> bookedIterator = bookedOrders.iterator();
            while (bookedIterator.hasNext()) {
                Order order = bookedIterator.next();

                if (departedVoyageId.equals(order.getVoyageId())) {
                    activeOrders.add(order);
                    order.setStatus(OrderStatus.INTRANSIT.name().toLowerCase());
                    bookedIterator.remove();
                }
            }
        }

    }

    private void removeActiveOrders(String arrivedVoyageId) {
        Iterator<Order> activeIterator = activeOrders.iterator();
        while (activeIterator.hasNext()) {
            Order order = activeIterator.next();
            if (arrivedVoyageId.equals(order.getVoyageId())) {
                activeIterator.remove();
                removeSpoiltOrders(order.getId());
            }
        }
    }

    private void removeSpoiltOrders(String orderId) {
        Iterator<Order> spoiltIterator = spoiltOrders.iterator();
        while (spoiltIterator.hasNext()) {
            Order order = spoiltIterator.next();
            if (orderId.equals(order.getId())) {
                spoiltIterator.remove();
                break;
            }
        }
    }

    private void voyageDeparted(String voyageId) {
        System.out.println("OrderService.voyageDeparted() - voyage:" + voyageId);
        JsonValue bookedOrders = get(Constants.BOOKED_ORDERS_KEY);
        if ( bookedOrders != null ) {
            JsonArray bookedOrderArray = bookedOrders.asJsonArray();
            if ( bookedOrderArray != null) {
                moveBookedOrdersToActive(voyageId);
                JsonValue orders = get(Constants.ACTIVE_ORDERS_KEY);

                if ( orders != null ) {
                    JsonArrayBuilder newbookedOrderArray = Json.createArrayBuilder();
                    JsonArrayBuilder activeOrderbuilder = Json.createArrayBuilder(orders.asJsonArray());
                    System.out.println(voyageId + "--------------- OrderService.voyageDeparted() number of booked orders "
                            + bookedOrderArray.size());
                    Iterator<JsonValue> it = bookedOrderArray.iterator();
                    // move booked voyage orders to active
                    while (it.hasNext()) {
                        JsonValue v = it.next();
                        if (voyageId.equals(v.asJsonObject().getString(Constants.VOYAGE_ID_KEY))) {
                            activeOrderbuilder.add(v);
                        } else {
                            newbookedOrderArray.add(v);
                        }
                    }
            
                    set(Constants.ACTIVE_ORDERS_KEY, activeOrderbuilder.build());
                    set(Constants.BOOKED_ORDERS_KEY, newbookedOrderArray.build());
                }

            }
 
        }
 
    }

    private void voyageArrived(String voyageId) {
        JsonValue activeOrders = get(Constants.ACTIVE_ORDERS_KEY);
        JsonArray activeOrderArray = activeOrders.asJsonArray();
        // JsonArrayBuilder newSpoiltOrderArray =
        // Json.createArrayBuilder(activeOrderArray);
        List<JsonValue> newList = new ArrayList<>();

        JsonValue spoiltOrders = get(Constants.SPOILT_ORDERS_KEY);
        JsonArray spoiltOrderArray = spoiltOrders.asJsonArray();
        spoiltOrderArray.forEach(value -> {
            newList.add(value);
        });

        JsonArrayBuilder activeOrderArrayBuilder = Json.createArrayBuilder();
        removeActiveOrders(voyageId);
        Iterator<JsonValue> it = activeOrderArray.iterator();
        // move booked voyage orders to active
        while (it.hasNext()) {
            JsonValue v = it.next();
            // skip orders which have just been delivered (voyage arrived)
            if (!voyageId.equals(v.asJsonObject().getString(Constants.VOYAGE_ID_KEY))) {
                activeOrderArrayBuilder.add(v);

            } else {
                removeOrderFromSpoiltList(newList, v.asJsonObject().getString(Constants.ORDER_ID_KEY));
            }
        }
        JsonArray orders = activeOrderArrayBuilder.build();
        System.out.println("................................. OrderService.voyageArrived() - voyageId:" + voyageId
                + " - Saving Active Voyages - Count:" + orders.size());
        set(Constants.ACTIVE_ORDERS_KEY, orders);

        JsonArrayBuilder newSpoiltOrderArrayBuilder = Json.createArrayBuilder();
        newList.forEach(value -> {
            newSpoiltOrderArrayBuilder.add(value);
        });
        JsonArray al = newSpoiltOrderArrayBuilder.build();
        System.out.println("................................. OrderService.voyageArrived() - voyageId:" + voyageId
                + " - Saving SpoiltOrders List -" + al.toString());
        set(Constants.SPOILT_ORDERS_KEY, al);
    }

    private void removeOrderFromSpoiltList(List<JsonValue> spoiltOrderArray, String orderId) {
        // JsonArrayBuilder spoiltOrderArrayBuilder = Json.createArrayBuilder();

        Iterator<JsonValue> it = spoiltOrderArray.iterator();
        while (it.hasNext()) {
            JsonValue v = it.next();
            // skip orders which has just been delivered (voyage arrived)
            if (orderId.equals(v.asJsonObject().getString(Constants.ORDER_ID_KEY))) {
                it.remove();
            }
        }
        // return spoiltOrderArrayBuilder.bui
    }

    public void updateOrderStatus(String voyageId, OrderStatus status, int daysAtSea) {
        System.out.println("OrderService.updateOrderStatus() - voyageId:" + voyageId + " Status:" + status);
        if (voyageId == null) {
            System.out.println("OrderService.updateOrderStatus() - voyageId is null, rejecting update request");
            return;
        }

        if (status.equals(OrderStatus.DELIVERED)) {
            voyageArrived(voyageId);
        } else if (status.equals(OrderStatus.INTRANSIT)) {
            voyageDeparted(voyageId);
        }
    }

}