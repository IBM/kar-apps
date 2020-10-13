package com.ibm.research.kar.reeferserver.controller;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.OrderProperties;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reeferserver.service.OrderService;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.reeferserver.service.SimulatorService;
import com.ibm.research.kar.reeferserver.service.VoyageService;

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
	private SimulatorService simulatorService;

	@Autowired
	private VoyageService voyageService;
	@Autowired
	private GuiController gui;

	/**
	 * Convert json order to OrderProperties
	 * 
	 * @param orderMsg - jason encoded message
	 * @return OrderProperties instance
	 */
	private OrderProperties jsonToOrderProperties(String orderMsg) {
		OrderProperties orderProperties = new OrderProperties();
		String voyageId = "";

		try (JsonReader jsonReader = Json.createReader(new StringReader(orderMsg))) {

			JsonObject req = jsonReader.readObject();

			voyageId = req.getString(Constants.VOYAGE_ID_KEY);
			Voyage voyage = scheduleService.getVoyage(voyageId);
			
			orderProperties.setProduct(req.getString("product"));
			orderProperties.setProductQty(req.getInt("productQty"));
			String customerId = "N/A";
			if (req.containsKey("customerId")) {
				customerId = req.getString("customerId");
			}
			orderProperties.setCustomerId(customerId);
			orderProperties.setVoyageId(voyageId);
			orderProperties.setOriginPort(voyage.getRoute().getOriginPort());
			orderProperties.setDestinationPort(voyage.getRoute().getDestinationPort());
		} catch (VoyageNotFoundException e) {
			System.out.println(
					"OrderController.orderDetails() - voyage " + voyageId + " not found in the shipping schedule");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return orderProperties;
	}

    /**
	 * Called to update the GUI spoil count when and an order is spoilt
	 * 
	 * message - json encoded message
	 * @param 
	 * @throws IOException
	 */
	@PostMapping("/orders/spoilt")
	public void orderSpoilt(@RequestBody String message) throws IOException {
		try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {

			JsonObject req = jsonReader.readObject();
			String spoiltOrderId = req.getString(Constants.ORDER_ID_KEY);
			int totalSpoiltOrders = orderService.orderSpoilt(spoiltOrderId);
			System.out.println("OrderController.orderSpoilt()................................. Orders Spoilt:"+totalSpoiltOrders);
			gui.updateSpoiltOrderCount(totalSpoiltOrders);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
    /**
	 * Called to create an order using properties in the message. 
	 * 
	 * @param message - json encoded message
	 * @return
	 * @throws IOException
	 */
	@PostMapping("/orders")
	public OrderProperties bookOrder(@RequestBody String message) throws IOException {
		System.out.println("OrderController.bookOrder - Called -" + message);
		// get Java POJO with order properties from json messages
		OrderProperties orderProperties = jsonToOrderProperties(message);
		try {

			Order order = orderService.createOrder(orderProperties);
			orderProperties.setOrderId(order.getId());
			JsonObjectBuilder ordersProps = Json.createObjectBuilder();
			ordersProps.add("orderId", order.getId()).add("orderVoyageId", order.getVoyageId()).add("orderProductQty",
					order.getProductQty());

			JsonObjectBuilder orderObject = Json.createObjectBuilder();
			orderObject.add("order", ordersProps.build());
			JsonObject params = orderObject.build();

			ActorRef orderActor = actorRef(ReeferAppConfig.OrderActorName, order.getId());
			// call Order actor to create the order
			JsonValue reply = actorCall(orderActor, "createOrder", params);
			System.out.println("Order Actor reply:" + reply);
			
			order.setStatus(OrderStatus.BOOKED.getLabel());

			JsonArray reefers = reply.asJsonObject().getJsonObject("booking").getJsonArray("reefers");
			int shipFreeCapacity = scheduleService.updateFreeCapacity(order.getVoyageId(), reefers.size());
			
			simulatorService.updateVoyageCapacity(order.getVoyageId(), shipFreeCapacity);
			voyageService.addOrderToVoyage(order);
			int futureOrderCount = orderService.getOrders("booked-orders");
			gui.updateFutureOrderCount(futureOrderCount);
		} catch (Exception ee) {
			ee.printStackTrace();
		}
		return orderProperties;
	}
    /**
	 * Returns a list of voyages that are currently at sea
	 * 
	 * @return
	 */
	@GetMapping("/orders/list/active")
	public List<Order> getActiveOrderList() {
		System.out.println("OrderController.getActiveOrderList() - Got New Request");
		return orderService.getActiveOrderList();
	}

	/**
	 * Returns a list of voyages that have been booked but not yet at sea
	 * @return
	 */
	@GetMapping("/orders/list/booked")
	public List<Order> getBookedOrderList() {
		System.out.println("OrderController.getBookedOrderList() - Got New Request");

		return orderService.getBookedOrderList();
	}

	/**
	 * Returns a list of spoilt orders
	 * @return
	 */
	@GetMapping("/orders/list/spoilt")
	public List<Order> getSpoiltOrderList() {
		System.out.println("OrderController.getSpoiltOrderList() - Got New Request");

		return orderService.getSpoiltOrderList();
	}

	@GetMapping("/orders/stats")
	public OrderStats getOrderStats() {
		System.out.println("OrderController.getOrderStats() - Got New Request");

		return orderService.getOrderStats();
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
		System.out.println("OrderController.nextPage() - Page:" + page + " Size:" + size);

	}

}