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

package com.ibm.research.kar.reeferserver;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.actors.OrderActor;
import com.ibm.research.kar.reefer.actors.VoyageActor;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.FleetCapacity;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.common.time.TimeUtils;

import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.ReeferDTO;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.service.*;
import com.ibm.research.kar.reeferserver.scheduler.ShippingScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.annotation.PostConstruct;
import javax.json.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

@EnableAutoConfiguration
@ComponentScan("com.ibm.research.kar")
@EnableJpaRepositories("com.ibm.research.kar.reeferserver.repository.*")
@EntityScan("com.ibm.research.kar.reefer.model.*")  
@SpringBootApplication
public class ReeferServerApplication {
	@Autowired
	private ScheduleService shipScheduleService;
	@Autowired
	private VoyageService voyageService;
	@Autowired
	private TimeService timeService;
	@Autowired
	private FleetService fleetService;
	@Autowired
	private OrderService orderService;
	@Value("${start}")
	private static String mode;
	private ReeferDTO[] reeferMasterInventory = null;
	public static void main(final String[] args) {

		SpringApplication.run(ReeferServerApplication.class, args);
		System.out.println("UTC:   " + TimeUtils.getInstance().getStartDate());
	}
	@PostConstruct
	public void init() {
		// load routes
		List<Route> routes = shipScheduleService.getRoutes();
		// The schedule base date is a date when the schedule was generated. It is saved persistently and
		// only exists if the REST service process stops.
		Optional<Instant> scheduleBaseDate = timeService.recoverDate(Constants.SCHEDULE_BASE_DATE_KEY);
		if ( scheduleBaseDate.isPresent()) {
			System.out.println("ReeferServerApplication.init() - WARM START");
			// WARM START
			// recover current date which is persisted on every date change
			Optional<Instant> date = timeService.recoverDate(Constants.CURRENT_DATE_KEY);
			// initialize Singleton TimeUtils with restored current date
			Instant currentDate = TimeUtils.getInstance(date.get()).getCurrentDate();
			System.out.println("ReeferServerApplication.init() - Restored Current Date:"+currentDate);

			shipScheduleService.generateShipSchedule(scheduleBaseDate.get());
			// restore state of active voyages (order count, daysOutAtSea, etc) from actors
			voyageService.restoreActiveVoyageOrders(shipScheduleService.getActiveSchedule());
			System.out.println("ReeferServerApplication.init() - >>>>>>>>>>>>>> Restoring Orders ...........");

			restoreReeferInventory(FleetCapacity.totalSize(routes));
			//restoreOrders();
		} else {
			// COLD START
			System.out.println("ReeferServerApplication.init() - COLD START");
			Instant currentDate = TimeUtils.getInstance().getCurrentDate();
			System.out.println("ReeferServerApplication.init() - Current Date:"+currentDate);
			timeService.saveDate(currentDate, Constants.SCHEDULE_BASE_DATE_KEY);
			timeService.saveDate(currentDate, Constants.CURRENT_DATE_KEY);
			shipScheduleService.generateShipSchedule(currentDate);
			System.out.println("ReeferServerApplication.init() - Saved Base Date:"+timeService.recoverDate(Constants.SCHEDULE_BASE_DATE_KEY).get());
		}
	}
	/*
	private void restoreOrders() {


		//Map<String, JsonValue> state = Kar.Actors.State.getAll(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId));
		// Book reefers for this order through the ReeferProvisioner
		//JsonValue reply = Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
		//		"getOrders");
		System.out.println("ReeferServerApplication.restoreOrders() - returned");
		//System.out.println("VoyageActor.reserve() - Id:" + getId()+" ReeferProvisioner.book() reply:"+bookingStatus);
		if (success(reply)) {
			System.out.println("ReeferServerApplication.restoreOrders() - SUCCESS");
			JsonArray orders = reply.asJsonObject().getJsonArray(Constants.ORDERS_KEY);
			System.out.println("ReeferServerApplication.restoreOrders() - restoring "+orders.size());
		} else {
			System.out.println("ReeferServerApplication.restoreOrders() - FAILURE");
			throw new IllegalStateException("ReeferProvisioner.getOrders() - returned failure");
		}
	}
		private boolean success(JsonValue jv) {
			return jv != null &&
					jv.asJsonObject() != null &&
					jv.asJsonObject().containsKey(Constants.STATUS_KEY) &&
					jv.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK);
		}

	 */

	private void restoreReeferInventory( int totalReeferInventorySize) {

		ActorRef reeferProvisionerRef = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId);
		//JsonValue totalReeferInventory = Kar.Actors.State.get(reeferProvisionerRef, Constants.TOTAL_REEFER_COUNT_KEY);

			// fetch reefer map
			Map<String, JsonValue> reeferInventory = Kar.Actors.State.Submap.getAll(reeferProvisionerRef, Constants.REEFER_MAP_KEY);
            System.out.println("ReeferServerApplication.restoreReeferInventory() - totalReeferInventory="+ totalReeferInventorySize);
			// allocate reefer array which is used to allocate/deallocate reefers
			reeferMasterInventory = new ReeferDTO[totalReeferInventorySize];
			reeferInventory.
					values().
					stream().
					filter(Objects::nonNull).
					map(jv -> jv.asJsonObject()).
					forEach(reefer -> reeferMasterInventory[reefer.getInt(Constants.REEFER_ID_KEY)] = jsonObjectToReeferDTO(reefer));
			System.out.println("ReeferServerApplication.restoreReeferInventory()-restored "+reeferInventory.size());


	}
	/*
	private void restoreOrders() {
		if ( reeferMasterInventory != null && reeferMasterInventory.length > 0 ) {

			// restore in-memory cache which manages order-reefers association. This cache provides performance
			// optimization which reduces latency of voyage arrival processing and idempotence check when booking
			// reefers to order.
			// (1) on voyage arrival, code does not need to scan reefer inventory for reefers associated with
			//     orders in a voyage. The cache contains all reefer ids for each order which can be used to
			//     remove them from persistent store as a batch
			// (2) with the cache its fast to check for idempotence when processing reefer booking for an order
			//
			Map<String, Set<String>> order2ReeferMap = Arrays.stream(reeferMasterInventory).filter(Objects::nonNull).
					collect(Collectors.groupingBy(ReeferDTO::getOrderId, Collectors.mapping(r -> String.valueOf(r.getId()), Collectors.toSet())));
			Set<String> orders = order2ReeferMap.keySet().stream().filter(Objects::nonNull).collect(Collectors.toSet());
			System.out.println("ReeferServerApplication.restoreOrderToReefersMap()-restored "+orders.size());
			orders.forEach(orderId -> {
				System.out.println("ReeferServerApplication.restoreOrderToReefersMap()-restoring order "+orderId);
				if ( !orderId.trim().isEmpty()) {
					orderState(orderId);
				}

			});
		} else {
			System.out.println("ReeferServerApplication.restoreOrderToReefersMap()- reeferInventory not restored - cant restore orders");
		}

	}
	private void orderState(String orderId) {
		try {
			ActorRef orderActorRef = Kar.Actors.ref(ReeferAppConfig.OrderActorName, orderId);
			Map<String, JsonValue> state = Kar.Actors.State.getAll(orderActorRef);
			if ( state == null || state == JsonValue.NULL) {
				System.out.println("\"ReeferServerApplication.orderState - unable to fetch order state - orderId:"+orderId);
			} else if (!state.isEmpty()) {
				System.out.println("\"ReeferServerApplication.orderState - fetching order state - orderId:"+orderId);
				//String oId = ((JsonString)state.get(Constants.ORDER_ID_KEY)).toString();
				String vId = ((JsonString)state.get(Constants.VOYAGE_ID_KEY)).getString();
				String cId = ((JsonString)state.get(Constants.ORDER_CUSTOMER_ID_KEY)).getString();
				String status = ((JsonString)state.get(Constants.ORDER_STATUS_KEY)).getString();

				String product = ((JsonString)state.get(Constants.ORDER_PRODUCT_KEY)).getString();
				int productQty = ((JsonNumber)state.get(Constants.ORDER_PRODUCT_QTY_KEY)).intValue();
				Order order = new Order(orderId, cId, product, productQty, vId, status, Collections.emptyList() );
				orderService.restoreOrder(order);
			}
		} catch( Exception e) {
			e.printStackTrace();
		}

		//
		//JsonObject jo = null;
		//ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderActorName, orderId);
		//JsonValue reply = Kar.Actors.call(orderActor, "state");
		//if ( reply != null && reply != JsonValue.NULL) {
	//		orderService.restoreOrder(jsonObjectToOrder(reply.asJsonObject()));
	//	}

	//
	}
	*/
	private ReeferDTO jsonObjectToReeferDTO(JsonObject reefer) {
		ReeferDTO reeferDTO = new ReeferDTO(reefer.getInt(Constants.REEFER_ID_KEY),
				ReeferState.State.valueOf(reefer.getString(Constants.REEFER_STATE_KEY)),
				reefer.getString(Constants.ORDER_ID_KEY), reefer.getString(Constants.VOYAGE_ID_KEY));
		if (reefer.containsKey(Constants.REEFERS_MAINTENANCE_DATE)) {
			reeferDTO.setMaintenanceReleaseDate(reefer.getString(Constants.REEFERS_MAINTENANCE_DATE));
		}
		return reeferDTO;
	}
	private Order jsonObjectToOrder(JsonObject order) {
		String orderId = order.getString(Constants.ORDER_ID_KEY);
		String customerId = order.getString(Constants.ORDER_CUSTOMER_ID_KEY);
		String voyageId = order.getString(Constants.VOYAGE_ID_KEY);
		String state = order.getString(Constants.ORDER_STATUS_KEY);
		String product = order.getString(Constants.ORDER_PRODUCT_KEY);
		int productQty = order.getInt(Constants.ORDER_PRODUCT_QTY_KEY);
		return new Order(orderId, customerId, product, productQty, voyageId, state, Collections.emptyList());
	}
}
