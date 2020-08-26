package com.ibm.research.kar.reeferserver.service;

import java.util.Iterator;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonValue;

import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.OrderStats;

import org.springframework.stereotype.Service;

@Service
public class OrderService extends AbstractPersistentService {

    public Order createOrder(OrderProperties orderProperties) {
        Order order = 
            new Order(orderProperties);

        JsonArrayBuilder bookedOrderArrayBuilder = null;
        // fetch booked orders from Kar
        JsonValue bookedOrders = get("booked-orders");
        if ( bookedOrders == null ) {
            bookedOrderArrayBuilder = Json.createArrayBuilder();
        } else {
            bookedOrderArrayBuilder = Json.createArrayBuilder();
            JsonArray orderList = bookedOrders.asJsonArray();
            System.out.println("OrderService.createOrder - cached order list size:"+orderList.size());
            // since orderList is immutable we need copy current booked orders into a new
            // list and then add our new order to it. Could not find a way around this. 
            // Attempt to modify the list from Kar causes UnsuportedOpertion exception.
            for( JsonValue savedOrder : orderList) {
                bookedOrderArrayBuilder.add(savedOrder);
            }
        }
        
        JsonValue newOrder = Json.createObjectBuilder().add("orderId",order.getId()).add("voyageId",order.getVoyageId()).build();

        bookedOrderArrayBuilder.add(newOrder);
        // once the build() is called the list is immutable
        JsonArray orderArray = bookedOrderArrayBuilder.build();
        // save the new booked orders list in Kar
        set("booked-orders", orderArray);
        System.out.println("OrderService.createOrder() - added future order id:"+order.getId()+" voyageId:"+order.getVoyageId()+" booked Order:"+orderArray.size());
        return order;
    }

    public OrderStats getOrderStats() {
        return new OrderStats(getOrders("active-orders"), getOrders("booked-orders"), 0);
    }
    public int getOrders(String orderKindKey) {
        try {
            JsonValue o = get(orderKindKey);
            System.out.println(".....................OrderService.getOrders() -"+orderKindKey+" orders: o="+o);
            if ( o == null ) {
                o = Json.createArrayBuilder().build();
                System.out.println(".....................OrderService.getOrders() - NEW LIST FOR BOOKED ORDERS ");
                set(orderKindKey,o);
            }
            JsonArray orderArray = o.asJsonArray();
            System.out.println(".....................OrderService.getOrders() - "+orderKindKey+" orders:"+orderArray.size());
            return orderArray.size();
        } catch( Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getActiveOrders(String voyageId) {
        
        int voyageOrders = 0;
        JsonValue activeOrders = get("active-orders");
        JsonArray activeOrderArray = activeOrders.asJsonArray();
        Iterator<JsonValue> it = activeOrderArray.iterator();
        // move booked voyage orders to active 
        while( it.hasNext() ) {
            JsonValue v = it.next();
            if ( voyageId.equals(v.asJsonObject().getString("voyageId") ) ) {
                voyageOrders++;
            }
        }
        return voyageOrders;
    }
 
    private void voyageDeparted(String voyageId) {
        System.out.println("OrderService.voyageDeparted()");
        JsonValue bookedOrders = get("booked-orders");
        JsonArray bookedOrderArray = bookedOrders.asJsonArray();

        JsonArrayBuilder newbookedOrderArray = Json.createArrayBuilder();
         JsonArrayBuilder activeOrderbuilder = Json.createArrayBuilder(get("active-orders").asJsonArray());
        System.out.println("--------------- OrderService.voyageDeparted() number of booked orders "+bookedOrderArray.size());
        Iterator<JsonValue> it = bookedOrderArray.iterator();
        // move booked voyage orders to active 
        while( it.hasNext() ) {
            JsonValue v = it.next();
            if ( voyageId.equals(v.asJsonObject().getString("voyageId") ) ) {
                activeOrderbuilder.add(v);
            } else {
                newbookedOrderArray.add(v);
            }
        }
        set("active-orders",activeOrderbuilder.build());
        set("booked-orders",newbookedOrderArray.build());
    }
    private void voyageArrived(String voyageId) {
        JsonValue activeOrders2 = get("active-orders");
        JsonArray activeOrderArray = activeOrders2.asJsonArray();
        JsonArrayBuilder activeOrderArrayBuilder = Json.createArrayBuilder();

        Iterator<JsonValue> it = activeOrderArray.iterator();
        // move booked voyage orders to active 
        while( it.hasNext() ) {
            JsonValue v = it.next();
            if ( voyageId.equals(v.asJsonObject().getString("voyageId") ) ) {
                activeOrderArrayBuilder.add(v);
            }
        }
        set("active-orders",activeOrderArrayBuilder.build());
    }

    public void updateOrderStatus(String voyageId, OrderStatus status, int daysAtSea) {
        System.out.println("OrderService.updateOrderStatus() - voyageId:"+voyageId+ " Status:"+status);
        if ( voyageId == null) {
            System.out.println("OrderService.updateOrderStatus() - voyageId is null, rejecting update request");
            return;
        }

        if ( status.equals(OrderStatus.DELIVERED)) {
            voyageArrived(voyageId);
        } else if ( status.equals(OrderStatus.INTRANSIT)) {
            voyageDeparted(voyageId);
        }
    }

 
}