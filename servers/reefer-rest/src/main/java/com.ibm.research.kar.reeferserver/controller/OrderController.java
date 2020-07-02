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
		orderProperties.setOrderId(order.getOrderId());
		return orderProperties;
	}
	@GetMapping("/orders")
	public List<Order>  getAllOrders() {
		System.out.println("getAllOrders() - Got New Request");

		webSocket.send("DUDE HELLO");





		
		return orderService.getOrders();
	}



}