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
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderStats;

import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.json.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


@Service
public class OrderService { //extends AbstractPersistentService {
    // number of the most recent orders to return to the GUI
    private static int MaxOrdersToReturn = 10;
    private static final Logger logger = Logger.getLogger(OrderService.class.getName());
   /*
    private FixedSizeQueue<Order> activeOrders = new FixedSizeQueue(MaxOrdersToReturn);
    private FixedSizeQueue<Order> bookedOrders = new FixedSizeQueue(MaxOrdersToReturn);
    private FixedSizeQueue<Order> spoiltOrders = new FixedSizeQueue(MaxOrdersToReturn);
*/
    //List<Order> activeOrders = new LinkedList<>();
    //List<Order> bookedOrders = new LinkedList<>();
   // List<Order> spoiltOrders = new LinkedList<>();

    TreeSet<Order> activeOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));
    TreeSet<Order> bookedOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));
    TreeSet<Order> spoiltOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));
    /*
    private AtomicInteger bookedTotalCount = new AtomicInteger();
    private AtomicInteger activeTotalCount = new AtomicInteger();
    private AtomicInteger spoiltTotalCount = new AtomicInteger();

     */
    //Map<String, Map<String, String>>
    private OrderPersistence storage = new OrderPersistence();
    /**
     * Returns N most recent active orders where N = MaxOrdersToReturn
     *
     * @return- Most recent active orders
     */
    public List<Order> getActiveOrderList() {
        /*
        List<JsonValue> activeOrders = getListAJsonArray(Constants.ACTIVE_ORDERS_KEY);
        List<Order> sublist;
        if (activeOrders.size() <= MaxOrdersToReturn) {
            sublist = jsonToOrderList(activeOrders);
        } else {
            sublist = jsonToOrderList(
                    activeOrders.subList(activeOrders.size() - MaxOrdersToReturn, activeOrders.size()));
        }
        return sublist;

         */

        //return new ArrayList<>(activeOrders);
        synchronized (OrderService.class) {
            //return activeOrders.subList(activeOrders.size() - MaxOrdersToReturn, activeOrders.size());
            return activeOrders.descendingSet().stream().limit(MaxOrdersToReturn).collect(Collectors.toList());
        }

    }

    /**
     * Returns N most recent booked orders where N = MaxOrdersToReturn
     *
     * @return Most recent booked orders
     */
    public List<Order> getBookedOrderList() {
        /*
        List<JsonValue> bookedOrders = getListAJsonArray(Constants.BOOKED_ORDERS_KEY);
        List<Order> sublist;
        if (bookedOrders.size() <= MaxOrdersToReturn) {
            sublist = jsonToOrderList(bookedOrders);
        } else {
            sublist = jsonToOrderList(
                    bookedOrders.subList(bookedOrders.size() - MaxOrdersToReturn, bookedOrders.size()));
        }
        return sublist;

         */
      //  return new ArrayList<>(bookedOrders);
        synchronized (OrderService.class) {
            //return bookedOrders.subList(bookedOrders.size() - MaxOrdersToReturn, bookedOrders.size());
            return bookedOrders.descendingSet().stream().limit(MaxOrdersToReturn).collect(Collectors.toList());
        }

    }

    /**
     * Returns N most recent spoilt orders where N = MaxOrdersToReturn
     *
     * @return Most recent spoilt orders
     */
    public List<Order> getSpoiltOrderList() {
        /*
        List<JsonValue> spoiltOrders = getListAJsonArray(Constants.SPOILT_ORDERS_KEY);
        List<Order> sublist;
        if (spoiltOrders.size() <= MaxOrdersToReturn) {
            sublist = jsonToOrderList(spoiltOrders);
        } else {

            sublist = jsonToOrderList(
                    spoiltOrders.subList(spoiltOrders.size() - MaxOrdersToReturn, spoiltOrders.size()));
        }
        return sublist;

         */
        //return new ArrayList<>(spoiltOrders);
        synchronized (OrderService.class) {
            //return spoiltOrders.subList(spoiltOrders.size() - MaxOrdersToReturn, spoiltOrders.size());
            return spoiltOrders.descendingSet().stream().limit(MaxOrdersToReturn).peek(o -> System.out.println(o.getDate())).collect(Collectors.toList());
        }

    }

    /*
    public void restoreOrder(Order order) {
        System.out.println("OrderService.restoreOrder() - Order Status:"+order.getStatus());

        Order.OrderStatus status = Order.OrderStatus.valueOf(order.getStatus());
        boolean restored = true;
        switch(status) {
            case BOOKED:
                //insertAndSort(bookedOrders, order);
                bookedOrders.add(0, order);
                break;

            case INTRANSIT:
                activeOrders.add(0, order);
                break;

            case SPOILT:
                spoiltOrders.add(0, order);
                break;

            default:
                restored = false;
                System.out.println("OrderService.restoreOrder() - unexpected order status:"+status);
        }
        if ( restored ) {
            System.out.println("OrderService.restoreOrder() - orderId:"+order.getId()+" status:"+order.getStatus()+" booked:"+bookedOrders.size()+" active:"+activeOrders.size()+" spoilt:"+spoiltOrders.size());
        }

    }

     */
/*
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



    public void orderDeparted(String orderId) {
        activeTotalCount.incrementAndGet();
        bookedTotalCount.decrementAndGet();
    }
 */
    /**
     * Called when an order with given id gets spoiled which means that one or more
     * of its reefers became spoilt while in-transit.
     *
     * @param orderId Order id which became spoilt
     * @return Number of spoilt orders
     */
    public int orderSpoilt(String orderId) {
        /*
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

         */
        synchronized (OrderService.class) {
                for (Order order : activeOrders ) {
                    if ( orderId.equals(order.getId())) {
                        // need to persist the flag to restore orders on restart
                        order.setSpoilt(true);
                        spoiltOrders.add( order);

                     //   ActorRef aRef = Kar.Actors.ref("resthelper", "reeferservice");
                     //   Kar.Actors.State.Submap.set(aRef,Constants.SPOILT_ORDERS_KEY,order.getId(), order.getAsJsonObject());
                       // storage.saveOrder(order.getId(), order.getAsJsonObject(), Constants.SPOILT_ORDERS_KEY);
                       // storage.saveOrder(order.getId(), order.getAsJsonObject(), Constants.REST_ORDERS_KEY);
                        storage.saveOrder(order.getId(), order.getAsJsonObject());
                        break;
                    }
                }
               // StringBuilder sb = new StringBuilder();
              //  for(Order o : spoiltOrders ) {
              //      sb.append(o.getId()).append(",");
              //  }
            //System.out.println("OrderService.orderSpoilt() -------------------------------------- spoilt orders:"+sb.toString());
           // spoiltTotalCount.incrementAndGet();
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
        /*
        JsonArray spoiltOrdersArray = getListAJsonArray(Constants.SPOILT_ORDERS_KEY);
        Iterator<JsonValue> spoiltIterator = spoiltOrdersArray.iterator();
        while (spoiltIterator.hasNext()) {
            JsonObject order = spoiltIterator.next().asJsonObject();
            if (orderId.equals(order.getString(Constants.ORDER_ID_KEY))) {
                return true;
            }
        }

         */
        synchronized (OrderService.class) {
            for (Order order : spoiltOrders ) {
                if ( order.equals(orderId) ) {
                    return true;
                }
            }
            return false;
        }
    }
/*
    private void saveOrders(Map<String, JsonValue> orders, String map ) {
        ActorRef aRef = Kar.Actors.ref("resthelper", "reeferservice");
        Kar.Actors.State.Submap.set(aRef,map, orders);

    }
    private void saveOrder(String key, JsonObject order, String map) {
        ActorRef aRef = Kar.Actors.ref("resthelper", "reeferservice");
        Kar.Actors.State.Submap.set(aRef,map,key,order);
    }
*/
    public void saveOrder(Order order) {
        /*
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
        }

         */
       // bookedTotalCount.incrementAndGet();
        synchronized (OrderService.class) {
            bookedOrders.add( order);
        //    ActorRef aRef = Kar.Actors.ref("resthelper", "reeferservice");
       //     Kar.Actors.State.Submap.set(aRef,Constants.BOOKED_ORDERS_KEY,order.getId(), order.getAsJsonObject());
           // storage.saveOrder(order.getId(), order.getAsJsonObject(), Constants.BOOKED_ORDERS_KEY);
            //storage.saveOrder(order.getId(), order.getAsJsonObject(), Constants.REST_ORDERS_KEY);
            storage.saveOrder(order.getId(), order.getAsJsonObject());
        }

    }
     /*
    public void remove(List<String> keys) {
        super.remove(keys);
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

    public Set<Voyage> findVoyagesBeyondArrivalDate(JsonArray activeOrders) {
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

     */

    /**
     * Returns aggregate counts for booked, active, and spoilt orders
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
        /*
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

         */

        synchronized (OrderService.class) {
            int orderCount = 0;
            switch(orderListKindKey) {
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
/*
    public JsonArray toJsonArray(List<JsonValue> list) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (JsonValue v : list) {
            arrayBuilder.add(v);
        }
        return arrayBuilder.build();
    }


 */
    /**
     * Called when a ship (in a voyage) departs from an origin port. Moves orders
     * associated with a given voyage from booked to active list.
     *
     * @param voyageId Voyage id
     */
    private void voyageDeparted(String voyageId,  List<String> voyageOrderList) {
    //    activeTotalCount.addAndGet(voyageOrderList.size());
     //   bookedTotalCount.addAndGet(-voyageOrderList.size());
        synchronized (OrderService.class) {
            if ( !voyageOrderList.isEmpty()) {
                List<Order> orders = new ArrayList<>();
                try {
                     orders = departedOrderList(voyageOrderList);
                } catch( Exception e) {
                    e.printStackTrace();
                }

                /*
                List<Order> orders = voyageOrderList.stream().map(orderId -> {
                    for( Order o : bookedOrders) {
                        if ( o.getId().equals(orderId)) {
                            return o;
                        }
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());

                 */
                System.out.println("OrderService.voyageDeparted() ++++++++++++++++++++++++++ voyageOrderList.size()="+voyageOrderList.size()+" Adding:"+orders.size()+" bookedOrders.size()="+bookedOrders.size());

                Map<String, JsonValue> newActiveOrders =
                        orders.stream().filter(Objects::nonNull).
                                peek(o -> o.setStatus(Order.OrderStatus.INTRANSIT.toString())).
                                collect(Collectors.toMap(Order::getId, order -> order.getAsJsonObject()));
                activeOrders.addAll(orders);
               /*
                ActorRef aRef = Kar.Actors.ref("resthelper", "reeferservice");
                Kar.Actors.State.Submap.set(aRef,Constants.ACTIVE_ORDERS_KEY, newActiveOrders);

                */
               // storage.saveOrders(newActiveOrders,Constants.ACTIVE_ORDERS_KEY );
                storage.saveOrders(newActiveOrders );
                //storage.saveOrders(newActiveOrders,Constants.REST_ORDERS_KEY );
                bookedOrders.removeAll(orders);
               // Kar.Actors.State.Submap.removeAll(aRef,Constants.BOOKED_ORDERS_KEY, new ArrayList<>(newActiveOrders.keySet());
                //storage.remove(new ArrayList<>(newActiveOrders.keySet()), Constants.BOOKED_ORDERS_KEY);

            }

        }
        /*
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

         */
    }
    private List<Order> departedOrderList(List<String> voyageOrderList) {
        return voyageOrderList.stream().map(orderId -> {
            for( Order o : bookedOrders) {
                if ( o.getId() != null && o.getId().equals(orderId)) {
                    return o;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
/*
    private JsonArray getListAJsonArray(String orderListKind) {
        JsonValue orders = get(orderListKind);
        if (orders == null) {
            return Json.createArrayBuilder().build();
        } else {
            return orders.asJsonArray();
        }

    }

    public List<JsonValue> getMutableOrderList(String orderListKind) {
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
*/
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
            if ( !voyageOrderList.isEmpty()) {
                removeArrivedOrders(voyageOrderList);
                removeSpoiltOrders(voyageOrderList);
            }
        }
        /*
        activeTotalCount.addAndGet(-voyageOrderList.size());
        if (activeTotalCount.get() < 0) {
            activeTotalCount.set(0);
        }

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
        */

    }
    private void removeArrivedOrders(List<String> voyageOrderList) {
        List<Order> arrived = getOrders(voyageOrderList, activeOrders);
                /*
                List<Order> arrived = voyageOrderList.stream().map(orderId -> {
            for( Order o : activeOrders) {
                if ( o.getId().equals(orderId)) {
                    return o;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());

                 */
        activeOrders.removeAll(arrived);
       // ActorRef aRef = Kar.Actors.ref("resthelper", "reeferservice");
      //  Kar.Actors.State.Submap.removeAll(aRef,Constants.ACTIVE_ORDERS_KEY, arrived.stream().map(Order::getId).collect(Collectors.toList()));
        //storage.remove(arrived.stream().map(Order::getId).collect(Collectors.toList()), Constants.ACTIVE_ORDERS_KEY);
        //storage.remove(arrived.stream().map(Order::getId).collect(Collectors.toList()), Constants.REST_ORDERS_KEY);
        storage.remove(arrived.stream().map(Order::getId).collect(Collectors.toList()));
    }
    private List<Order> getOrders(List<String> voyageOrderList, TreeSet<Order> orders) {
        return voyageOrderList.stream().map(orderId -> {
            for( Order o : orders) {
                if ( o.getId() != null && o.getId().equals(orderId)) {
                    return o;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
    private void removeSpoiltOrders(List<String> voyageOrderList) {
        /*
        List<Order> spoilt = voyageOrderList.stream().map(orderId -> {
            for( Order o : spoiltOrders) {
                if ( o.getId().equals(orderId)) {
                    return o;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());

         */
        List<Order> spoilt = getOrders(voyageOrderList, spoiltOrders);
        spoiltOrders.removeAll(spoilt);
 //       ActorRef aRef = Kar.Actors.ref("resthelper", "reeferservice");
 //       Kar.Actors.State.Submap.removeAll(aRef,Constants.SPOILT_ORDERS_KEY, spoilt.stream().map(Order::getId).collect(Collectors.toList()));
        //storage.remove(spoilt.stream().map(Order::getId).collect(Collectors.toList()), Constants.SPOILT_ORDERS_KEY);
    }
    /*
    private void remove(List<String> keys2Remove, String fromMap) {
        ActorRef aRef = Kar.Actors.ref("resthelper", "reeferservice");
        Kar.Actors.State.Submap.removeAll(aRef,fromMap, keys2Remove);
    }
     */
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
            // check if there are voyages that should have arrived but didn't (due to REST crash)
  //          Set<Voyage> neverArrivedList =
  //                  findVoyagesBeyondArrivalDate(toJsonArray(getMutableOrderList(Constants.ACTIVE_ORDERS_KEY)));
            // force arrival to reclaim reefers and clean orders
 //           neverArrivedList.forEach(v -> forceArrival(v));
        } else if (Order.OrderStatus.INTRANSIT.equals(status)) {
            voyageDeparted(voyageId, voyageOrderList);
            // check if there are voyages that should have departed but didn't (due to REST crash)
         //   Set<Voyage> neverDepartedList =
        //            findVoyagesBeyondDepartureDate(toJsonArray(getMutableOrderList(Constants.BOOKED_ORDERS_KEY)));
            // force arrival to reclaim reefers and clean orders
          //  neverDepartedList.forEach(v -> voyageDeparted(v.getId()));
        }
    }
/*
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
*/

    private class OrderPersistence {
        ActorRef aRef = Kar.Actors.ref(ReeferAppConfig.RestActorName, ReeferAppConfig.RestActorId);

        OrderPersistence() {
            System.out.println("OrderService.OrderPersistence.ctor - Restoring orders");
            restoreOrders();
        }
        void remove(List<String> keys2Remove) {
            Kar.Actors.State.Submap.removeAll(aRef,Constants.REST_ORDERS_KEY, keys2Remove);
        }
        void saveOrders(Map<String, JsonValue> orders ) {
            Kar.Actors.State.Submap.set(aRef,Constants.REST_ORDERS_KEY, orders);
        }
        void saveOrder(String key, JsonObject order) {
            Kar.Actors.State.Submap.set(aRef,Constants.REST_ORDERS_KEY,key,order);
        }
/*
       void remove(List<String> keys2Remove, String fromMap) {
            Kar.Actors.State.Submap.removeAll(aRef,fromMap, keys2Remove);
        }
        void saveOrders(Map<String, JsonValue> orders, String map ) {
             Kar.Actors.State.Submap.set(aRef,map, orders);
        }
        void saveOrder(String key, JsonObject order, String map) {
            Kar.Actors.State.Submap.set(aRef,map,key,order);
        }

        void restoreOrders() {
            Map<String, JsonValue> orders = Kar.Actors.State.Submap.getAll(aRef,Constants.REST_ORDERS_KEY);
            activeOrders = restoreOrders(orders, Constants.ACTIVE_ORDERS_KEY);
            bookedOrders = restoreOrders(orders, Constants.BOOKED_ORDERS_KEY);
            spoiltOrders = restoreOrders(orders, Constants.SPOILT_ORDERS_KEY);
            System.out.println("OrderService.OrderPersistence.ctor - Restored orders - active:"+activeOrders.size()+" booked:"+bookedOrders.size()+" spoilt:"+spoiltOrders.size());
        }

         */
        void restoreOrders() {
            Map<String, JsonValue> orders = Kar.Actors.State.Submap.getAll(aRef,Constants.REST_ORDERS_KEY);

            for( JsonValue jv : orders.values()) {
                if ( jv == null ) {
                    continue;
                }
                Order order = new Order(jv);
                switch(OrderStatus.valueOf(order.getStatus().toUpperCase())) {
                    case INTRANSIT:
                        if ( order.isSpoilt()) {
                            spoiltOrders.add(order);
                        }
                        activeOrders.add(order);
                        break;
                    case BOOKED:
                        bookedOrders.add(order);
                        break;
                }
            }

            System.out.println("OrderService.OrderPersistence.ctor - Restored orders - active:"+activeOrders.size()+" booked:"+bookedOrders.size()+" spoilt:"+spoiltOrders.size());
            //return orders.values().stream().map(Order::new).collect(Collectors.toList());
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
