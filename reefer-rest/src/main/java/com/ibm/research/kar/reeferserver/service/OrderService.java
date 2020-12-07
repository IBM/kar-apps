package com.ibm.research.kar.reeferserver.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonValue;
import javax.json.JsonObject;

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
        // private JsonArray getListAJsonArray(String orderListKind) {

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
            Order order = new Order(v.asJsonObject().getString(Constants.ORDER_ID_KEY), "", 0,
                    v.asJsonObject().getString(Constants.VOYAGE_ID_KEY), "", new ArrayList());
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

        synchronized(OrderService.class) {
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
                        + activeOrdersArray.size() + " spoilt count:" + spoiltList.size()+" spoiltList:"+spoiltList);
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
        synchronized(OrderService.class) {
            JsonValue newOrder = Json.createObjectBuilder().add("orderId", order.getId())
                    .add("voyageId", order.getVoyageId()).build();

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
    private void findVoyagesBeyondDepartureDate(JsonArray bookedOrders) throws VoyageNotFoundException {
        Instant today = TimeUtils.getInstance().getCurrentDate();
        for( JsonValue v : bookedOrders ) {
            Voyage voyage = scheduleService.getVoyage(v.asJsonObject().getString(Constants.VOYAGE_ID_KEY));
            long daysBetween = TimeUtils.getInstance().getDaysBetween(voyage.getSailDateObject(), today);
            if ( daysBetween > 5) {
            //if ( voyage.getSailDateObject().isBefore(today)) {
                logger.log(Level.WARNING,"OrderService.findVoyagesBeyondDepartureDate() - voyage:"+voyage.getId()+
                        " should have sailed on:"+voyage.getSailDateObject()+" but still in the booked list as of today:"+today);
            }
        }
    }
    private void findVoyagesBeyondArrivalDate(JsonArray activeOrders) throws VoyageNotFoundException {
        Instant today = TimeUtils.getInstance().getCurrentDate();
        for( JsonValue v : activeOrders ) {
            Voyage voyage = scheduleService.getVoyage(v.asJsonObject().getString(Constants.VOYAGE_ID_KEY));
            long daysBetween = TimeUtils.getInstance().getDaysBetween(Instant.parse(voyage.getArrivalDate()), today);
            if ( daysBetween > 5) {
            //if ( Instant.parse(voyage.getArrivalDate()).isBefore(today)) {
                logger.log(Level.WARNING,"OrderService.findVoyagesBeyondArrivalDate() - voyage:"+voyage.getId()+
                        " should have arrived on:"+voyage.getArrivalDate()+" but still in the active list as of today "+today);
            }
        }
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
     * @param orderKindKey - type of order list (active,booked,spoilt,
     *                     on-maintenance)
     * @return number of orders
     */
    public int getOrderCount(String orderListKindKey) {
        synchronized(OrderService.class) {
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
        synchronized(OrderService.class) {
            List<JsonValue> newBookedList = getMutableOrderList(Constants.BOOKED_ORDERS_KEY);
            JsonArray newActiveList = getListAJsonArray(Constants.ACTIVE_ORDERS_KEY);

            JsonArrayBuilder activeOrderBuilder = Json.createArrayBuilder(newActiveList);
            boolean voyageFound=false;
            // Move booked to active list
            Iterator<JsonValue> it = newBookedList.iterator();
            while (it.hasNext()) {
                JsonValue v = it.next();
                if (voyageId.equals(v.asJsonObject().getString(Constants.VOYAGE_ID_KEY))) {
                    activeOrderBuilder.add(v);
                    // remove from booked
                    it.remove();
                    voyageFound = true;
                }
            }
            if ( !voyageFound) {
                System.out.println("OrderService.voyageDeparted() - voyage:"+voyageId+" not in the booked list");
            } else {
                System.out.println("OrderService.voyageDeparted() - voyage:"+voyageId+" departed today:"+TimeUtils.getInstance().getCurrentDate());
            }
            JsonArray activeArray = activeOrderBuilder.build();
            try {
                set(Constants.ACTIVE_ORDERS_KEY, activeArray);
                // findVoyagesBeyondArrivalDate(activeArray);

                set(Constants.BOOKED_ORDERS_KEY, toJsonArray(newBookedList));
                findVoyagesBeyondDepartureDate(toJsonArray(newBookedList));
            } catch( VoyageNotFoundException e) {
                logger.log(Level.WARNING,e.getMessage(),e);
            }


            if (logger.isLoggable(Level.INFO)) {
                logger.info("OrderService.voyageDeparted() - voyage:" + voyageId + " booked voyage:"
                        + newBookedList.size() + " active voyages:" + activeArray.size());
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
        List<JsonValue> newList = new ArrayList<>();

        // fetch immutable list of orders from Kar persistent storage
        JsonArray orders = getListAJsonArray(orderListKind);
        // copy spoilt orders from immutable list to one that we can change
        orders.forEach(value -> {
            newList.add(value);
        });
        return newList;
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
        synchronized(OrderService.class) {
            List<JsonValue> newSpoiltList = getMutableOrderList(Constants.SPOILT_ORDERS_KEY);
            List<JsonValue> newActiveList = getMutableOrderList(Constants.ACTIVE_ORDERS_KEY);

            // remove voyage orders from the spoilt list
            removeVoyageOrdersFromList(voyageId, newSpoiltList);
            // remove voyage orders from the active list
            removeVoyageOrdersFromList(voyageId, newActiveList);
            System.out.println("OrderService.voyageArrived() - removed voyage:"+voyageId+" from in-transit list");
            try {
                // save new spoilt list in the Kar persistent storage
                set(Constants.SPOILT_ORDERS_KEY, toJsonArray(newSpoiltList));

                // save new active list in the Kar persistent storage
                set(Constants.ACTIVE_ORDERS_KEY, toJsonArray(newActiveList));
                findVoyagesBeyondArrivalDate(toJsonArray(newActiveList));

            } catch( VoyageNotFoundException e ) {
                logger.log(Level.WARNING,e.getMessage(),e);
            }

            if (logger.isLoggable(Level.INFO)) {
                logger.info("OrderService.voyageArrived() - voyageId:" + voyageId
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
        } else if (OrderStatus.INTRANSIT.equals(status)) {
            voyageDeparted(voyageId);
        }
    }
}
