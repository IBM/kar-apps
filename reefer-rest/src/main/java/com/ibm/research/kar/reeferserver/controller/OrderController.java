package com.ibm.research.kar.reeferserver.controller;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ibm.research.kar.reefer.model.Order;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderDTO;
import com.ibm.research.kar.reeferserver.repository.OrderRepository;
import com.ibm.research.kar.reeferserver.service.OrderService;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.actor.ActorRef;
import static com.ibm.research.kar.Kar.*;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
//import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
@RestController
@CrossOrigin("*")
public class OrderController {
//	@Autowired
//	private OrderRepository orderRepository;

	@Autowired
	private OrderService orderService;

	@Autowired
	private ScheduleService scheduleService;
	
	@Autowired
	private NotificationController webSocket;

    @PostMapping("/orders")
	//public OrderProperties bookOrder(@RequestBody OrderProperties orderProperties) throws IOException {
	public OrderProperties bookOrder(@RequestBody String op) throws IOException {
			//System.out.println("bookOrder() Called - Saving Order=> Product:"+orderProperties.getProduct());
			System.out.println("bookOrder() Called -"+op);
			OrderProperties orderProperties = new OrderProperties();
			
			
			try (JsonReader jsonReader = Json.createReader(new StringReader(op))) {
             
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
		
		Order order = orderService.createOrder(orderProperties); 
		

        try {

			JsonObjectBuilder ordersProps = Json.createObjectBuilder();
		
			ordersProps.add("orderId",order.getId()).
				add("orderVoyageId", orderProperties.getVoyageId()).
				add("orderProductQty",orderProperties.getProductQty());

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

	@PostMapping("/orders/nextpage")
	public void nextPage(@RequestParam(name = "page", defaultValue = "0") int page,
								@RequestParam(name = "size", defaultValue = "10") int size) {
		System.out.println("OrderController.nextPage() - Page:"+page+" Size:"+size);
	 // return "OK";
   
	}
	/*
	public Page<Order> nextPage(@RequestParam(name = "page", defaultValue = "0") int page,
								@RequestParam(name = "size", defaultValue = "10") int size) {
	  PageRequest pageRequest = PageRequest.of(page, size);
	  Page<OrderDTO> pageResult = orderRepository.findAll(pageRequest);
	  List<Order> orders = pageResult.stream().map(Order::new).collect(Collectors.toList());
   
	  return new PageImpl<>(orders, pageRequest, pageResult.getTotalElements());
   
	}
	*/
}