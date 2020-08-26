package com.ibm.research.kar.reeferserver.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.Kar.*;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.OrderSimControls;
import com.ibm.research.kar.reefer.model.OrderStats;

import org.springframework.stereotype.Service;

@Service
public class OrderService extends PersistentService{

    /*
    private Map<String, Order> orders = new HashMap<>();
    private Map<String, Order> activeOrders = new HashMap<>();
    private Map<String, Order> futureOrders = new HashMap<>();
    private Map<String, Order> spoiltOrders = new HashMap<>();

*/
 

 

      // local utility to retrieve cached value
  // create and fill cache if it is null

/*
    public List<Order> getOrders() {
        return new ArrayList<Order>(orders.values());
    }
    public void saveOrder(Order order) {
        orders.put(order.getId(), order);
    }
    */
    public Order createOrder(OrderProperties orderProperties) {
        Order order = 
            new Order(orderProperties);

      //  orders.put(order.getId(), order);
      //  futureOrders.put(order.getId(), order);

       
        JsonArrayBuilder bookedOrderArrayBuilder = null;
        JsonValue bookedOrders = get("booked-orders");
        if ( bookedOrders == null ) {
            bookedOrderArrayBuilder = Json.createArrayBuilder();
          //  bookedOrderArray = bookedOrders.asJsonArray();
        } else {
            bookedOrderArrayBuilder = Json.createArrayBuilder();
            JsonArray a = bookedOrders.asJsonArray();
            System.out.println("OrderService.createOrder - cached order list size:"+a.size());
            for( JsonValue o :a ) {
                bookedOrderArrayBuilder.add(o);
            }
        }
        
        JsonValue o = Json.createObjectBuilder().add("orderId",order.getId()).add("voyageId",order.getVoyageId()).build();

        bookedOrderArrayBuilder.add(o);

        JsonArray orderArray = bookedOrderArrayBuilder.build();
        set("booked-orders", orderArray);
        System.out.println("OrderService.createOrder() - added future order id:"+order.getId()+" voyageId:"+order.getVoyageId()+" booked Order:"+orderArray.size());
     //   orderRepository.save(new OrderDTO(order.getId(), order.getCustomerId(), order.getVoyageId(), order.getStatus(), order.getProduct(), order.getProductQty()));
        return order;
    }

    public OrderStats getOrderStats() {
//        return new OrderStats(activeOrders.size(), futureOrders.size(), spoiltOrders.size());
        return new OrderStats(getOrders("active-orders"), getOrders("booked-orders"), 0);
    }
    public int getOrders(String orderKindKey) {
        //return futureOrders.size();
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
    /*
    public int getActiveOrders() {
        //return activeOrders.size();
        try {
            JsonValue activeOrders2 = get("active-orders");
            if ( activeOrders2 == null ) {
                activeOrders2 = Json.createArrayBuilder().build();
                set("active-orders",activeOrders2);
            }
            JsonArray activeOrderArray = activeOrders2.asJsonArray();
            System.out.println(".....................OrderService.getFutureOrders() - active orders:"+activeOrderArray.size());
            return activeOrderArray.size();
        } catch( Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
    */
    public int getActiveOrders(String voyageId) {
        
        int voyageOrders = 0;
        JsonValue activeOrders2 = get("active-orders");
        JsonArray activeOrderArray = activeOrders2.asJsonArray();
        Iterator<JsonValue> it = activeOrderArray.iterator();
        // move booked voyage orders to active 
        while( it.hasNext() ) {
            JsonValue v = it.next();
            if ( voyageId.equals(v.asJsonObject().getString("voyageId") ) ) {
                voyageOrders++;
            }
        }
        /*
        Iterator<String> it = activeOrders.keySet().iterator();
        while(it.hasNext() ) {
            Order order = activeOrders.get(it.next());
            if ( voyageId.equals(order.getVoyageId())) {
                voyageOrders++;
            }
        }
        */

        return voyageOrders;
        
    }
    /*
    private void addActiveOrder(String voyageId, Order order) {
        System.out.println("OrderService.addActiveOrder - futureOrder size:"+futureOrders.size());

        if (futureOrders.containsKey(order.getId())) {
            Iterator<String> it = futureOrders.keySet().iterator();
            while(it.hasNext() ) {
                Order o = futureOrders.get(it.next());
                if ( o.getVoyageId() != null && o.getVoyageId().equals(voyageId) ) {
                    System.out.println("OrderService.addActiveOrder - ############# Removing order:"+o.getId()+" from future orders - current future order count:"+futureOrders.size());
                    it.remove();
                    activeOrders.put(o.getId(), o);
                    System.out.println("OrderService.addActiveOrder - ############# Added order:"+o.getId()+" to active orders - current active order count:"+activeOrders.size());
                    break;
                }

            }
        }
       
    }
    private void removeDeliveredOrders(String voyageId, Order order) {

        if (activeOrders.containsKey(order.getId())) {
            Iterator<String> it = activeOrders.keySet().iterator();
            while(it.hasNext() ) {
                Order o = activeOrders.get(it.next());
                if ( o.getVoyageId().equals(voyageId) ) {
                    System.out.println("OrderService.removeDeliveredOrders - ############# Removing order:"+o.getId()+" from future orders - current future order count:"+futureOrders.size());
                    it.remove();
                }

            }
        }
                
    }
    */
    private void voyageDeparted(String voyageId) {
        System.out.println("OrderService.voyageDeparted()");
        JsonValue bookedOrders = get("booked-orders");
        JsonArray bookedOrderArray = bookedOrders.asJsonArray();

        JsonArrayBuilder newbookedOrderArray = Json.createArrayBuilder();
       // JsonArrayBuilder bookedOrderArrayBuilder = Json.createArrayBuilder(bookedOrders.asJsonArray());

     //   JsonValue activeOrders2 = get("active-orders");
     //   JsonArray activeOrderArray = activeOrders2.asJsonArray();
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
            //removeDeliveredOrders(voyageId, order);
        } else if ( status.equals(OrderStatus.INTRANSIT)) {
            voyageDeparted(voyageId);
           // addActiveOrder(voyageId, order);
        }

        /*
        orders.forEach( (key, order) -> {
            System.out.println("OrderService.updateOrderStatus() - Order id:"+order.getId()+" voyageId:"+order.getVoyageId()+"  >>>> Looking for voyageId:"+ voyageId);
            if ( order.getVoyageId() != null && order.getVoyageId().equals(voyageId) ) {
               // System.out.println("OrderService.updateOrderStatus() - Order id:"+order.getId()+" voyageId:"+order.getVoyageId()+" status:"+order.getStatus());
                if ( OrderStatus.valueOf(order.getStatus().toUpperCase()).equals(OrderStatus.PENDING)) {
                    
                } else {
                    if (  !order.getStatus().equalsIgnoreCase(status.getLabel()) ) {
                       // System.out.println("OrderService.updateOrderStatus() updating Order status to:"+status.getLabel()+" daysAtSea:"+daysAtSea +" InFutureOrders="+futureOrders.containsKey(order.getId()));
                        order.setStatus(status.getLabel());
                        if ( status.equals(OrderStatus.DELIVERED)) {
                            removeDeliveredOrders(voyageId, order);
                        } else if ( status.equals(OrderStatus.INTRANSIT)) {
                            addActiveOrder(voyageId, order);
    
                        }
                        
                    }
                }
            }

           
        });
        */
    }
/*
    public void createSimOrder() {
        System.out.println("OrderService.createSimOrder()");
        try {
            Response response = Kar.restPost("simservice", "simulator/createorder", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    */
    public void setSimOrderTarget(int orderTarget) {
        System.out.println("OrderService.setSimOrderTarget()");
        try {
            JsonObject body = Json.createObjectBuilder().add("value", orderTarget).build();

            Response response = Kar.restPost("simservice", "simulator/setordertarget", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public void setSimOrderWindow(int window) {
        System.out.println("OrderService.setSimOrderWindow()");
        try {
            JsonObject body = Json.createObjectBuilder().add("value", window).build();

            Response response = Kar.restPost("simservice", "simulator/setorderwindow", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public void setSimOrderUpdateFrequency(int updateFrequency) {
        System.out.println("OrderService.setSimOrderUpdateFrequency()");
        try {
            JsonObject body = Json.createObjectBuilder().add("value", updateFrequency).build();

            Response response = Kar.restPost("simservice", "simulator/setorderupdates", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public void updateOrderSimControls(int orderTarget, int window, int updateFrequency) {
        System.out.println("OrderService.updateOrderSimControls()");
        try {
            setSimOrderTarget(orderTarget);
            setSimOrderWindow(window);
            setSimOrderUpdateFrequency(updateFrequency);
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public OrderSimControls getOrderSimControls() {
        System.out.println("OrderService.getOrderSimControls()");
        int target = 0;
        int window = 1;
        int updateFrequency = 2;
        try {
            target = getSimOrderTarget();
            window = getOrderSimWindow();
            updateFrequency = getOrderSimUpdateFrequency();

  
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return new OrderSimControls(target, window, updateFrequency);
    }
    public int getSimOrderTarget() {
        System.out.println("OrderService.getSimOrderTarget()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getordertarget");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("OrderService.getSimOrderTarget() Sim Response = "+respValue);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return orderTarget;
    }
    public int getOrderSimWindow() {
        System.out.println("OrderService.getOrderSimWindow()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getorderwindow");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("OrderService.getSimOrderTarget() Sim Response = "+respValue);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return orderTarget;
    }
    public int getOrderSimUpdateFrequency() {
        System.out.println("OrderService.getOrderSimUpdateFrequency()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getorderupdates");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("OrderService.getSimOrderTarget() Sim Response = "+respValue);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return orderTarget;
    }
}