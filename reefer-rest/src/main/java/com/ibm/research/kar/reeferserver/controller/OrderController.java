package com.ibm.research.kar.reeferserver.controller;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.VoyageActor;
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

	private static final Logger logger = Logger.getLogger(OrderController.class.getName());
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
		} catch (Exception e) {
			logger.log(Level.WARNING,"",e);
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
			if ( logger.isLoggable(Level.INFO)) {
				logger.info("OrderController.orderSpoilt() - order id:"+ spoiltOrderId+" has spoilt. Total spoilt orders:"+totalSpoiltOrders);
			}
			gui.updateSpoiltOrderCount(totalSpoiltOrders);
		} catch (Exception e) {
			logger.log(Level.WARNING,"",e);
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
		if ( logger.isLoggable(Level.FINE)) {
			logger.fine("OrderController.bookOrder - Called -" + message);
		}
		// get Java POJO with order properties from json messages
		OrderProperties orderProperties = jsonToOrderProperties(message);
		try {

			Order order = orderService.createOrder(orderProperties);
			JsonObjectBuilder ordersProps = Json.createObjectBuilder();
			ordersProps.add("orderId", order.getId()).add("orderVoyageId", order.getVoyageId()).add("orderProductQty",
					order.getProductQty());

			JsonObjectBuilder orderObject = Json.createObjectBuilder();
			orderObject.add("order", ordersProps.build());
			JsonObject params = orderObject.build();

			ActorRef orderActor = actorRef(ReeferAppConfig.OrderActorName, order.getId());
			// call Order actor to create the order
			JsonValue reply = actorCall(orderActor, "createOrder", params);
			if ( logger.isLoggable(Level.FINE)) {
				logger.fine("OrderController.bookOrder - Order Actor reply:" + reply);
			}
			order.setStatus(OrderStatus.BOOKED.getLabel());
			// extract reefer ids assigned to the order
			JsonArray reefers = reply.asJsonObject().getJsonObject("booking").getJsonArray("reefers");
			// reduce ship free capacity by the number of reefers
			int shipFreeCapacity = scheduleService.updateFreeCapacity(order.getVoyageId(), reefers.size());
			
			simulatorService.updateVoyageCapacity(order.getVoyageId(), shipFreeCapacity);
			voyageService.addOrderToVoyage(order);
			int futureOrderCount = orderService.getOrderCount(Constants.BOOKED_ORDERS_KEY);
			gui.updateFutureOrderCount(futureOrderCount);
		} catch (Exception e) {
			logger.log(Level.WARNING,"",e);
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
		return orderService.getActiveOrderList();
	}

	/**
	 * Returns a list of voyages that have been booked but not yet at sea
	 * @return
	 */
	@GetMapping("/orders/list/booked")
	public List<Order> getBookedOrderList() {
		return orderService.getBookedOrderList();
	}

	/**
	 * Returns a list of spoilt orders
	 * @return
	 */
	@GetMapping("/orders/list/spoilt")
	public List<Order> getSpoiltOrderList() {
		return orderService.getSpoiltOrderList();
	}

	/**
	 * Returns order related counts
	 *
	 * @return
	 */
	@GetMapping("/orders/stats")
	public OrderStats getOrderStats() {
		return orderService.getOrderStats();
	}
}