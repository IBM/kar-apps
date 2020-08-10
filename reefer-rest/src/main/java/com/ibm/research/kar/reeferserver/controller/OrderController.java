package com.ibm.research.kar.reeferserver.controller;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;

import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.service.OrderService;
import com.ibm.research.kar.reeferserver.service.ScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@CrossOrigin("*")
public class OrderController {

	@Autowired
	private OrderService orderService;

	@Autowired
	private ScheduleService scheduleService;
	
	@Autowired
	private NotificationController webSocket;

	private OrderProperties orderDetails(String orderMsg) {
		OrderProperties orderProperties = new OrderProperties();
			
			
		try (JsonReader jsonReader = Json.createReader(new StringReader(orderMsg))) {
		 
			JsonObject req = jsonReader.readObject();
			orderProperties.setProduct(req.getString("product"));
			orderProperties.setProductQty(req.getInt("productQty"));
			String customerId = "N/A";
			if ( req.containsKey("customerId")) {
				customerId = req.getString("customerId");
			}
			orderProperties.setCustomerId(customerId);
			Voyage voyage = scheduleService.getVoyage(req.getString("voyageId"));
			
			orderProperties.setVoyageId(req.getString("voyageId"));
			orderProperties.setOriginPort(voyage.getRoute().getOriginPort());
			orderProperties.setDestinationPort(voyage.getRoute().getDestinationPort());

		  } catch( Exception e) {
			e.printStackTrace();
		  }
		  return orderProperties;
	}
    @PostMapping("/orders")
	public OrderProperties bookOrder(@RequestBody String op) throws IOException {
		System.out.println("bookOrder() Called -"+op);
		OrderProperties orderProperties = orderDetails(op);

		Order order = orderService.createOrder(orderProperties); 
        try {
			JsonObjectBuilder ordersProps = Json.createObjectBuilder();
			ordersProps.add("orderId",order.getId()).
				add("orderVoyageId", order.getVoyageId()).
				add("orderProductQty",order.getProductQty());

			JsonObjectBuilder orderObject = Json.createObjectBuilder();
			orderObject.add("order", ordersProps.build());
			JsonObject params = orderObject.build();
            ActorRef orderActor = actorRef("order", order.getId());
            JsonValue reply = actorCall(orderActor, "createOrder", params);
			System.out.println("Order Actor reply:"+reply);
			order.setStatus(OrderStatus.BOOKED.getLabel());
			webSocket.sendOrderUpdate(order);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		}

		orderProperties.setOrderId(order.getId());
		
		return orderProperties;


	}
	@GetMapping("/orders")
	public List<Order>  getAllOrders() {
		System.out.println("getAllOrders() - Got New Request");
		
		return orderService.getOrders();
	}
	/**
	 * Implements server side pagination for the front end. Currently just a stub
	 * 
	 * @param page - page index
	 * @param size - items per page
	 */
	@PostMapping("/orders/nextpage")
	public void nextPage(@RequestParam(name = "page", defaultValue = "0") int page,
								@RequestParam(name = "size", defaultValue = "10") int size) {
		System.out.println("OrderController.nextPage() - Page:"+page+" Size:"+size);
   
	}

}