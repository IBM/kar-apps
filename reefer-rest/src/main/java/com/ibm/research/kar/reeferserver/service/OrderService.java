package com.ibm.research.kar.reeferserver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reeferserver.repository.OrderRepository;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.OrderDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
@Service
public class OrderService {
//	@Autowired
//	private OrderRepository orderRepository;


    private Map<String, Order> orders = new HashMap<>();

    public List<Order> getOrders() {
        return new ArrayList<Order>(orders.values());
    }

    public void saveOrder(Order order) {
        orders.put(order.getId(), order);
    }
    public Order createOrder(OrderProperties orderProperties) {
        Order order = 
            new Order(orderProperties);

        orders.put(order.getId(), order);

     //   orderRepository.save(new OrderDTO(order.getId(), order.getCustomerId(), order.getVoyageId(), order.getStatus(), order.getProduct(), order.getProductQty()));
        return order;
    }
    public void updateOrderStatus(String voyageId, OrderStatus status) {
        System.out.println("OrderService.updateOrderStatus() - Order Map size="+orders.size());
        orders.forEach( (key, order) -> {
            if ( order.getVoyageId().equals(voyageId) && !order.getStatus().equals(status.getLabel()) ) {
                System.out.println("OrderService.updateOrderStatus() updating Order status to:"+status.getLabel());
                order.setStatus(status.getLabel());
             }
        });
    }

    public void createSimOrder() {
        System.out.println("OrderService.createSimOrder()");
        try {
            Response response = Kar.restPost("simservice", "simulator/createorder", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);
        /*
        JsonObjectBuilder props = Json.createObjectBuilder();
		
        props.add("daysAtSea", 10).add("currentDate","2020-07-15");
     //   JsonObjectBuilder orderObject = Json.createObjectBuilder();
     //   orderObject.add("order", props.build());
        JsonObject params = props.build();

        ActorRef voyageActor = actorRef("voyage", "1111");

        JsonValue reply = actorCall(voyageActor, "changePosition", params);
        System.out.println("Voyage Actor reply:"+reply);
        */
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public void setSimOrderTarget(int orderTarget) {
        System.out.println("OrderService.setSimOrderTarget()");
        try {
           // JsonNumber target = Json.createValue(orderTarget);
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
}