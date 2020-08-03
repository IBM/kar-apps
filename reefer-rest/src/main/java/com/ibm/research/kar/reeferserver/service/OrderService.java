package com.ibm.research.kar.reeferserver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.model.Order;


import org.springframework.stereotype.Component;
@Component
public class OrderService {
    private Map<String, Order> orders = new HashMap<>();

    public List<Order> getOrders() {
        return new ArrayList<Order>(orders.values());
    }

    public void saveOrder(Order order) {
        orders.put(order.getId(), order);
    }
    public Order creatOrder(OrderProperties orderProperties) {
        Order order = 
            new Order(orderProperties);

        orders.put(order.getId(), order);
        return order;
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

}