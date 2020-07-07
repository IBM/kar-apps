package com.ibm.research.kar.reeferserver.controller;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.ibm.research.kar.reeferserver.model.Order;
import com.ibm.research.kar.reeferserver.model.OrderProperties;
import com.ibm.research.kar.reeferserver.service.OrderService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.actor.ActorRef;
import static com.ibm.research.kar.Kar.*;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
//import javax.json.JsonString;
import javax.json.JsonValue;
@RestController
@CrossOrigin("*")
public class OrderController {
    
	@Autowired
	private OrderService orderService;

	@Autowired
	private NotificationController webSocket;

    @PostMapping("/orders")
	public OrderProperties bookOrder(@RequestBody OrderProperties orderProperties) throws IOException {
		System.out.println("bookOrder() Called - Saving Order=> Product:"+orderProperties.getProduct());
		Order order = new Order(orderProperties);
		orderService.saveOrder(order);

        try {

			JsonObjectBuilder ordersProps = Json.createObjectBuilder();
		
			ordersProps.add("number", 10).add("orderId","3333").add("orderVoyageId", "5555").add("orderProductQty",1000);
			JsonObjectBuilder orderObject = Json.createObjectBuilder();
			orderObject.add("order", ordersProps.build());
			JsonObject params = orderObject.build();

            ActorRef orderActor = actorRef("order", "1111");

            JsonValue reply = actorCall(orderActor, "createOrder", params);
			System.out.println("Order Actor reply:"+reply);
            

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		}

		orderProperties.setOrderId(order.getOrderId());
		return orderProperties;
	}
	@GetMapping("/orders")
	public List<Order>  getAllOrders() {
		System.out.println("getAllOrders() - Got New Request");

		//webSocket.send("DUDE HELLO");





		
		return orderService.getOrders();
	}



}