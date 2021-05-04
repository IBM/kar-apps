/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.kar.reeferserver.controller;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.VoyageActor;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.*;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
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
			Voyage voyage;
			try {
				voyage = scheduleService.getVoyage(voyageId);
				orderProperties.setProduct(req.getString(Constants.ORDER_PRODUCT_KEY));
				orderProperties.setProductQty(req.getInt(Constants.ORDER_PRODUCT_QTY_KEY));
				String customerId = "N/A";
				if (req.containsKey(Constants.ORDER_CUSTOMER_ID_KEY)) {
					customerId = req.getString(Constants.ORDER_CUSTOMER_ID_KEY);
				}
				orderProperties.setCustomerId(customerId);
				orderProperties.setVoyageId(voyageId);
				orderProperties.setOriginPort(voyage.getRoute().getOriginPort());
				orderProperties.setDestinationPort(voyage.getRoute().getDestinationPort());
			} catch(VoyageNotFoundException vnfe) {
				orderProperties.setBookingStatus(Constants.FAILED).setMsg(" - voyage not found - possibly already arrived");
			}

		} catch (Exception e) {
			logger.log(Level.WARNING,e.getMessage(),e);
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
			if ( !orderService.orderAlreadySpoilt(spoiltOrderId)) {
				// add order to the spoilt list
				int totalSpoiltOrders = orderService.orderSpoilt(spoiltOrderId);
				if ( logger.isLoggable(Level.INFO)) {
					logger.info("OrderController.orderSpoilt() - order id:"+ spoiltOrderId+" has spoilt. Total spoilt orders:"+totalSpoiltOrders);
				}
				gui.updateSpoiltOrderCount(totalSpoiltOrders);
			}
		} catch (Exception e) {
			logger.log(Level.WARNING,e.getMessage(),e);
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
		OrderProperties orderProperties=null;
		try {
			// get Java POJO with order properties from json messages
			orderProperties = jsonToOrderProperties(message);
			Voyage voyage = scheduleService.getVoyage(orderProperties.getVoyageId());
			// check if the provided voyage has already sailed. This is likely when creating manual orders
			// through a GUI when days are short. By the time order details are provided by a user, the
			// ship may have departed.
			if (TimeUtils.getInstance().getCurrentDate().isAfter(voyage.getSailDateObject())) {
				orderProperties.setBookingStatus(Constants.FAILED).setOrderId("N/A").setMsg(" - selected voyage has already sailed - pick another voyage");
				return orderProperties;
			}
			Order order = new Order(orderProperties);
			ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderActorName, order.getId());
			// call Order actor to create the order. This may time out
			JsonValue reply = Kar.Actors.call(orderActor, "createOrder", order.getOrderParams());
			if ( logger.isLoggable(Level.FINE)) {
				logger.fine("OrderController.bookOrder - Order Actor reply:" + reply);
			}
			if ( reply != null && reply.asJsonObject() != null && reply.asJsonObject().containsKey(Constants.STATUS_KEY)) {
				// check if the order was booked
				if ( reply.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
					order.setStatus(OrderStatus.BOOKED.getLabel());
					orderService.saveOrder(order);

					// voyage actor computes freeCapacity
					int freeCapacity = reply.asJsonObject().getInt(Constants.VOYAGE_FREE_CAPACITY_KEY);
					// set ship free capacity, this value will be sent to the GUI
					voyage.getRoute().getVessel()
							.setFreeCapacity(freeCapacity);
					// notify simulator of changed free capacity
					simulatorService.updateVoyageCapacity(order.getVoyageId(), freeCapacity);
					voyage.incrementOrderCount();
					gui.updateFutureOrderCount(orderService.getOrderCount(Constants.BOOKED_ORDERS_KEY));
					orderProperties.setBookingStatus(Constants.OK).setMsg("");;
				} else {
					orderProperties.setBookingStatus(Constants.FAILED).setOrderId("N/A").setMsg(reply.asJsonObject().getString("ERROR"));
				}
			} else {
				logger.log(Level.WARNING,"OrderController.bookOrder() - unexpected reply format - reply:"+reply);
			}


		} catch (VoyageNotFoundException e) {
			logger.log(Level.WARNING,e.getMessage(),e);
		} catch (Exception e) {
			logger.log(Level.WARNING,e.getMessage(),e);
			orderProperties.setBookingStatus(Constants.FAILED).setMsg(e.getMessage());
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
