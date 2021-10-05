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

package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferAllocator;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.VoyageStatus;

import javax.json.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Actor
public class VoyageActor extends BaseActor {
   private JsonObject voyageInfo;
   private Voyage voyage = null;
   private JsonValue voyageStatus;
   private Map<String, JsonValue> orders = new HashMap<>();
   private Map<String, JsonValue> spoiltReefersMap = new HashMap<>();
   private Map<String, String> reefer2OrderMap = new HashMap<>();
   private Map<String, JsonValue> spoiltOrders = new HashMap<>();

   private static final Logger logger = Logger.getLogger(VoyageActor.class.getName());

   /**
    * Fetch actor's state from Kar persistent storage. On the first invocation call REST
    * to get Voyage info which includes details like daysAtSea, departure
    * date, arrival date, etc. Store it in Kar persistent storage for reuse on subsequent invocations.
    */
   @Activate
   public void activate() {
      try {
         // fetch actor state from Kar storage
         Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
         if (logger.isLoggable(Level.FINE)) {
            logger.fine("VoyageActor.init() actorID:" + this.getId() + " all state" + state);
         }
         // initial actor invocation should handle no state
         if (state.isEmpty()) {
            JsonObject params = Json.createObjectBuilder().
                    add(Constants.VOYAGE_ID_KEY, getId()).add(Constants.VOYAGE_ID_KEY, getId()).
                    build();
            // fetch voyage details
            ActorRef scheduleManager = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
            JsonValue jv = Kar.Actors.call(scheduleManager, "voyage", Json.createValue(getId()));
            voyageInfo = jv.asJsonObject();
            // check if voyage already arrived in which case the progress attribute would be 100. It's possible that
            // the arrived voyage would be called with an anomaly
            if ( voyageInfo != null && voyageInfo.getJsonNumber("progress").intValue() == 100) {
               voyageInfo = null;
               return;
            }
            Kar.Actors.State.set(this, Constants.TOTAL_SPOILT_KEY, Json.createValue(0));
            // store voyage information in Kar storage for reuse
            Kar.Actors.State.set(this, Constants.VOYAGE_INFO_KEY, voyageInfo);
         } else {
            System.out.println("VoyageActor.activate() - voyage:" + getId());
            if (state.containsKey(Constants.VOYAGE_INFO_KEY)) {
               voyageInfo = state.get(Constants.VOYAGE_INFO_KEY).asJsonObject();
            }
            if (state.containsKey(Constants.VOYAGE_STATUS_KEY)) {
               voyageStatus = state.get(Constants.VOYAGE_STATUS_KEY);
            }
            if (state.containsKey(Constants.VOYAGE_ORDERS_KEY)) {
               orders.putAll(state.get(Constants.VOYAGE_ORDERS_KEY).asJsonObject());
               // build reefer to order map so that we can efficiently retrieve order id
               // when processing refer anomaly
               for(Map.Entry<String, JsonValue> entry: orders.entrySet()) {
                  DepotReply reeferBookingRecord = new DepotReply(entry.getValue());
                  for( String reeferId : reeferBookingRecord.getReefers()) {
                     reefer2OrderMap.put(reeferId, reeferBookingRecord.getOrderId());
                  }
               }
               System.out.println("VoyageActor.activate() - voyage:" + getId() + " restored orders - size:" + orders.size());
            }
            if (state.containsKey(Constants.SPOILT_REEFERS_KEY)) {
               spoiltReefersMap.putAll(state.get(Constants.SPOILT_REEFERS_KEY).asJsonObject());
               System.out.println("VoyageActor.activate() - voyage:" + getId() + " restored spoilt reefers - size:" + spoiltReefersMap.size());
            }
            if (state.containsKey(Constants.SPOILT_ORDERS_KEY)) {
               spoiltOrders.putAll(state.get(Constants.SPOILT_ORDERS_KEY).asJsonObject());
               System.out.println("VoyageActor.activate() - voyage:" + getId() + " restored spoilt orders - size:" + spoiltOrders.size());
            }

         }
         voyage = VoyageJsonSerializer.deserialize(voyageInfo);
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   /**
    * Called on ship position change. Determines if the ship departed from
    * its origin port or arrived at the destination. Updates REST ship
    * position.
    *
    * @param message - Json encoded message containing daysAtSea value
    * @return
    */
   @Remote
   public JsonValue changePosition(JsonObject message) {
      if (logger.isLoggable(Level.INFO)) {
         logger.info("VoyageActor.changePosition() called Id:" + getId() + " " + message.toString() + " state:"
                 + getVoyageStatus());
      }
      if (Objects.isNull(voyage)) {
         logger.log(Level.WARNING, "VoyageActor.changePosition() - Error - voyageId " + getId() + " metadata is not defined - looks like the REST service is down");
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Rest Service Unavailable - voyage metadata unknown")
                 .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
      }
      try {
         // the simulator advances ship position
         int daysOutAtSea = message.getInt(Constants.VOYAGE_DAYSATSEA_KEY);
         // process only if the position has changed
         if (voyage.positionChanged(daysOutAtSea)) {
            // given ship sail date and current days at sea get ship's current date
            Instant shipCurrentDate = voyage.getSailDateObject().plus(daysOutAtSea, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
            if (logger.isLoggable(Level.FINE)) {
               logger.fine(
                       "VoyageActor.changePosition() voyage info:" + voyageInfo + " ship current date:" + shipCurrentDate);
            }
            voyage.changePosition(daysOutAtSea);
            // if ship's current date matches (or exceeds) its arrival date, the ship arrived
            if (voyage.shipArrived(shipCurrentDate, getVoyageStatus())) {
               if (!VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
                  logger.log(Level.WARNING, "VoyageActor.changePosition() - voyage:" + voyage.getId() + " arrived BUT its expected state is not DEPARTED. Instead it is " + getVoyageStatus());
               }

               voyageStatus = Json.createValue(VoyageStatus.ARRIVED.name());
               // notify voyage orders of arrival
               processArrivedVoyage(voyage);
               // voyage arrived, no longer need the state
               Kar.Actors.remove(this);
            } else {

               JsonObjectBuilder jb = Json.createObjectBuilder();
               if (voyage.shipDeparted(shipCurrentDate, getVoyageStatus())) {
                  // notify voyage orders of departure
                  processDepartedVoyage(voyage);
                  voyageStatus = Json.createValue(VoyageStatus.DEPARTED.name());
                  jb.add(Constants.VOYAGE_STATUS_KEY, voyageStatus);
               } else {  // voyage in transit
                  messageSchedulerActor("positionChanged", voyage);
               }

               jb.add(Constants.VOYAGE_INFO_KEY, VoyageJsonSerializer.serialize(voyage));
               Kar.Actors.State.set(this, jb.build());
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
      return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
   }

   @Remote
   public void reeferAnomaly(JsonObject message) {
      String spoiltReeferId = String.valueOf(message.getInt(Constants.REEFER_ID_KEY));
      if ( voyageInfo == null ) {   // voyage arrived?
         Kar.Actors.remove(this);
         JsonObjectBuilder job = Json.createObjectBuilder();
         // switch anomaly mgr target from voyage to depot
         job.add(Constants.REEFER_ID_KEY, message.getJsonNumber(Constants.REEFER_ID_KEY)).
                add(Constants.TARGET_KEY, Constants.DEPOT_TARGET_TYPE);
         //System.out.println("VoyageActor.reeferAnomaly :::::::::::::::::::::: Voyage "+getId()+" already arrived - forwarding reefer "+spoiltReeferId+" anomaly to AnomalyManager");
         ActorRef anomalyManagerActor = Kar.Actors.ref(ReeferAppConfig.AnomalyManagerActorType, ReeferAppConfig.AnomalyManagerId);
         Kar.Actors.tell(anomalyManagerActor, "reeferAnomaly", job.build());
         return;
      }
      if ( !reefer2OrderMap.containsKey(spoiltReeferId)) {
         System.out.println("VoyageActor.reeferAnomaly - voyage:"+getId()+" !!!!!!!!!!!!!!!!! order not found in reefer2OrderMap for spoilt reefer:"+spoiltReeferId);
      }
      boolean newSpoiltOrder = false;
      if ( !spoiltReefersMap.containsKey(spoiltReeferId)) {
         spoiltReefersMap.put(spoiltReeferId, Json.createValue(spoiltReeferId));
         String orderId = reefer2OrderMap.get(spoiltReeferId);
         if ( !spoiltOrders.containsKey(orderId) ) {
            if ( !orders.containsKey(orderId)) {
               StringBuilder sb = new StringBuilder();
               for(Map.Entry<String, JsonValue> order: orders.entrySet()) {
                  sb.append(order.getKey()).append(",");
               }
               System.out.println("VoyageActor.reeferAnomaly - voyage:"+getId()+" spoilt reefer:"+spoiltReeferId+" !!!!!!!!!!!!!!!!! order:"+orderId+" not found in the orders Map - orders map size:"+orders.size()+"\n"+sb.toString());
            }
            newSpoiltOrder = true;
            spoiltOrders.put(orderId, Json.createValue(orderId));
            JsonValue jv = orders.get(orderId).asJsonObject();
            Order order = new Order(orders.get(orderId).asJsonObject().getJsonObject(JsonOrder.OrderKey));
            order.setSpoilt(true);
            ActorRef orderManagerActor = Kar.Actors.ref(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId);
            Kar.Actors.call(orderManagerActor, "orderSpoilt", order.getAsJsonObject());
         }

         Map<String, JsonValue> actorStateMap = new HashMap<>();
         actorStateMap.put(Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltReefersMap.size()));
         Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
         subMapUpdates.put(Constants.SPOILT_REEFERS_KEY, spoiltReefersMap);
         if ( newSpoiltOrder ) subMapUpdates.put(Constants.SPOILT_ORDERS_KEY, spoiltOrders);
         Kar.Actors.State.update(this, Collections.emptyList(), Collections.emptyMap(), actorStateMap, subMapUpdates);
      }

   }

   /**
    * Called to book a voyage for a given order. Calls DepotActorto book reefers and
    * stores orderId in the Kar persistent storage.
    *
    * @param message Json encoded order properties
    * @return - result of reefer booking
    */
   @Remote
   public JsonObject reserve(JsonObject message) {
      long t = System.currentTimeMillis();
      // wrapper around Json
      Order order = new Order(message);
      if (logger.isLoggable(Level.FINE)) {
         logger.fine("VoyageActor.reserve() called Id:" + getId() + " " + message.toString() + " OrderID:"
                 + order.getId() + " Orders size=" + orders.size());
      }
      // booking may come after voyage departure
      if (VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
         logger.log(Level.WARNING, "VoyageActor.reserve() - voyage Id " + getId() + " - already departed - rejecting order booking - " + order.getId());
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", " voyage " + getId() + " already departed")
                 .add(Constants.ORDER_ID_KEY, this.getId()).build();
      }
      // Idempotence check. If a given order is in this voyage order list it must have already been processed.
      if (orders.containsKey(order.getId())) {
         JsonValue booking = orders.get(order.getId());
         // this order has already been processed so return result
         if (booking.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
            return buildResponse(order, voyage.getRoute().getVessel().getFreeCapacity());
         }
         return orders.get(order.getId()).asJsonObject();
      }

      try {
         // Check if ship has capacity for the order.
         int howManyReefersNeeded = ReeferAllocator.howManyReefersNeeded(order.getProductQty());
         if (!voyage.capacityAvailable(howManyReefersNeeded)) {
            String msg = "Error - ship capacity exceeded - current available capacity:" + voyage.getRoute().getVessel().getFreeCapacity() * 1000 +
                    " - reduce product quantity or choose a different voyage";
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.FAILED)
                    .add("ERROR", msg).build();
         }
         // Book reefers for this order through the DepotActor
         JsonValue booking = Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.DepotActorType, DepotManagerActor.Depot.makeId(voyage.getRoute().getOriginPort())),
                 "bookReefers", message);

         // convenience wrapper for DepotActor json reply
         DepotReply reply = new DepotReply(booking);
         if (reply.success()) {
            Set<String> orderReefers = reply.getReefers();
            for( String rid : orderReefers ) {
               reefer2OrderMap.put(rid, order.getId());
            }
            if (!booking.asJsonObject().containsKey(Constants.ORDER_REEFERS_KEY)) {
               System.out.println("VoyageActor.reserve() - ID:" + getId() + " orderId:" + order.getId() + " !!!!!!!!!!!!!!!! booking has no reefers:" + booking);
            }

            save(reply, order, booking);
            ActorRef scheduleManager = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
            Kar.Actors.tell(scheduleManager, "updateVoyage", VoyageJsonSerializer.serialize(voyage));
            //// Return SUCCESS
            return buildResponse(order, voyage.getRoute().getVessel().getFreeCapacity());
         }
         // Return FAILURE
         return booking.asJsonObject();
      } catch (Exception e) {
         logger.log(Level.WARNING, "VoyageActor.reserve() - Error - voyageId " + getId() + " ", e);
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                 .add(Constants.ORDER_ID_KEY, this.getId()).build();
      }
   }

   private void save(DepotReply booking, Order order, JsonValue bookingStatus) {
      voyage.setReeferCount(voyage.getReeferCount() + booking.getReeferCount());
      voyage.updateFreeCapacity(booking.getReeferCount());
      if (logger.isLoggable(Level.INFO)) {
         logger.info("VoyageActor.save() - Vessel " + voyage.getRoute().getVessel().getName() + " Updated Free Capacity "
                 + voyage.getRoute().getVessel().getFreeCapacity());
      }
      orders.put(order.getId(), bookingStatus);
      voyage.setOrderCount(orders.size());
      voyageStatus = Json.createValue(VoyageStatus.PENDING.name());

      Map<String, JsonValue> actorStateMap = new HashMap<>();
      actorStateMap.put(Constants.VOYAGE_STATUS_KEY, voyageStatus);
      actorStateMap.put(Constants.VOYAGE_INFO_KEY, VoyageJsonSerializer.serialize(voyage));

      Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
      Map<String, JsonValue> orderSubMapUpdates = new HashMap<>();
      orderSubMapUpdates.put(order.getId(), bookingStatus);
      subMapUpdates.put(Constants.VOYAGE_ORDERS_KEY, orderSubMapUpdates);

      Kar.Actors.State.update(this, Collections.emptyList(), Collections.emptyMap(), actorStateMap, subMapUpdates);
      // System.out.println("VoyageActor.save() - order:"+order.getId()+" - bookingStatus :"+bookingStatus);
   }


   private JsonObject buildResponse(final Order order, final int freeCapacity) {
      return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
              .add(JsonOrder.OrderKey, order.getAsJsonObject())
              .add(Constants.DEPOT_KEY, DepotManagerActor.Depot.makeId(voyage.getRoute().getOriginPort()))
              .add(Constants.VOYAGE_FREE_CAPACITY_KEY, freeCapacity)
              .build();
   }

   @Remote
   public void replaceReefer(JsonObject message) {

      try {
         String spoiltReeferId = String.valueOf(message.getInt(Constants.REEFER_ID_KEY));
         if (VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
            boolean reeferExists = reefer2OrderMap.containsKey(spoiltReeferId);
            System.out.println("VoyageActor.replaceReefer() - voyage:"+getId()+" - too late to replace reefer "+
                    spoiltReeferId+", voyage already departed - processing as reefer anomaly - reefer exists:"+reeferExists);
            reeferAnomaly(message);
            return;
         }

         ActorRef depotActor = Kar.Actors.ref(ReeferAppConfig.DepotActorType, DepotManagerActor.Depot.makeId(voyage.getRoute().getOriginPort()));
         JsonValue reply = Kar.Actors.call(depotActor, "reeferReplace",message);

         if ( !reply.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
            System.out.println("VoyageActor.replaceReefer() - voyage:"+getId()+" - Error:"+reply.asJsonObject().getString(Constants.ERROR)+" - processing as reefer anomaly");
            reeferAnomaly(message);
            return;
         }
         int newReeferId = reply.asJsonObject().getInt(Constants.REEFER_REPLACEMENT_ID_KEY);

         String orderId = reefer2OrderMap.get(spoiltReeferId);
         if (orders.containsKey(orderId)) {
            JsonValue order = orders.get(orderId);
            reefer2OrderMap.remove(spoiltReeferId);
            reefer2OrderMap.put(String.valueOf(newReeferId), orderId);


            String reefers[] = order.asJsonObject().getString(Constants.ORDER_REEFERS_KEY).split(",");
            Set<String> reeferIds = new LinkedHashSet<>();

            for( String reeferId : reefers) {
               if (reeferId.equals(spoiltReeferId)) {
                  // replace spoilt reefer
                  reeferIds.add(String.valueOf(newReeferId));
               } else {
                  reeferIds.add(reeferId);
               }
            }
            JsonObject updatedBooking = Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).
                    add(Constants.DEPOT_KEY,  DepotManagerActor.Depot.makeId(voyage.getRoute().getOriginPort())).
                    add(Constants.REEFERS_KEY, Json.createValue(reeferIds.size())).
                    add(Constants.ORDER_REEFERS_KEY, Json.createValue(String.join(",", reeferIds))).
                    add(JsonOrder.OrderKey, order.asJsonObject().getJsonObject(Constants.ORDER_KEY)).build();
            orders.put(orderId, updatedBooking);


            Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
            Map<String, JsonValue> orderSubMapUpdates = new HashMap<>();
            orderSubMapUpdates.put(orderId, updatedBooking);
            subMapUpdates.put(Constants.VOYAGE_ORDERS_KEY, orderSubMapUpdates);

            Kar.Actors.State.update(this, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), subMapUpdates);
         }
      } catch( Exception e) {
         e.printStackTrace();
      }

   }

   /**
    * Calls REST and Order actors when a ship arrives at the destination port
    *
    * @param voyage - Voyage info
    */
   private void processArrivedVoyage(final Voyage voyage) {
      try {
         JsonArrayBuilder voyageOrderIdsBuilder = Json.createArrayBuilder();
         StringBuilder reeferIds = new StringBuilder();
         // notify each order actor that the ship arrived

         orders.keySet().forEach(orderId -> {
            voyageOrderIdsBuilder.add(orderId);
            JsonValue order = orders.get(orderId);
            String stringifiedReeferIds = order.asJsonObject().getString(Constants.ORDER_REEFERS_KEY);
            if (stringifiedReeferIds == null) {
               System.out.println("VoyageActor.processArrivedVoyage - " + getId() + " !!!!!!!!!!!!!!!!!!!!!!  REEFERS MISSING FROM ORDER:" + orderId + " booking status:" + order);
            } else if (stringifiedReeferIds.trim().length() == 0) {
               System.out.println("VoyageActor.processArrivedVoyage - " + getId() + " !!!!!!!!!!!!!!!!!!!!!!  REEFERS NOT BOOKED TO ORDER:" + orderId + " booking status:" + order);
            } else {
               reeferIds.append(stringifiedReeferIds).append(",");
            }
         });

         StringBuilder spoiltReeferIds = new StringBuilder();
         spoiltReefersMap.keySet().forEach(spoiltReefer -> {
            spoiltReeferIds.append(spoiltReefer).append(",");
         });
         JsonArray voyageOrderIds = voyageOrderIdsBuilder.build();

         if (!voyageOrderIds.isEmpty()) {
            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.VOYAGE_ID_KEY, getId()).
                    add(Constants.VOYAGE_ARRIVAL_DATE_KEY, voyage.getArrivalDate()).
                    add(Constants.REEFERS_KEY, reeferIds.toString()).
                    add(Constants.SPOILT_REEFERS_KEY, spoiltReeferIds.toString());
            Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.DepotActorType, DepotManagerActor.Depot.makeId(voyage.getRoute().getDestinationPort())),
                    "voyageReefersArrived", job.build());
         }

         messageSchedulerActor("voyageArrived", voyage);
         ActorRef orderManagerActor = Kar.Actors.ref(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId);
         Kar.Actors.call(orderManagerActor, "ordersArrived", voyageOrderIds);

         orders.keySet().forEach(orderId -> {
            notifyVoyageOrder(orderId, Order.OrderStatus.DELIVERED, "delivered");
         });

      } catch (Exception e) {
         e.printStackTrace();
         logger.log(Level.WARNING, "VoyageActor.processArrivedVoyage() - Error while notifying order of arrival- voyageId " + getId() + " ", e);
      }
   }

   /**
    * Calls REST and Order actors when a ship departs from the origin port
    *
    * @param voyage - Voyage info
    */
   private void processDepartedVoyage(final Voyage voyage) {
      try {
         ActorRef depotActor = Kar.Actors.ref(ReeferAppConfig.DepotActorType,
                 DepotManagerActor.Depot.makeId(voyage.getRoute().getOriginPort()));
         JsonObject params = Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).
                 add(Constants.VOYAGE_REEFERS_KEY, Json.createValue(voyage.getReeferCount())).
                 build();
         Kar.Actors.tell(depotActor, "voyageReefersDeparted", params);
         messageSchedulerActor("voyageDeparted", voyage);
         orders.keySet().forEach(orderId -> {
            notifyVoyageOrder(orderId, Order.OrderStatus.INTRANSIT, "departed");
         });
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   private void notifyVoyageOrder(String orderId, Order.OrderStatus orderStatus, String methodName) {
      JsonValue value = orders.get(orderId);
      JsonObject booking = value.asJsonObject();
      JsonObject jorder = booking.getJsonObject(Constants.ORDER_KEY);
      Order order = new Order(booking.asJsonObject().getJsonObject(Constants.ORDER_KEY));
      if (!orderStatus.name().equals(order.getStatus())) {
         try {
            messageOrderActor(methodName, orderId);
         } catch (Exception orderActorException) {
            // KAR sometimes fails to locate order actor instance even though it exists in REDIS. This can happen
            // after process restart
            if (orderActorException.getMessage().startsWith("Actor instance not found:")) {
               // ignore the error for now, eventually KAR will not throw this error
               logger.log(Level.WARNING, "VoyageActor.notifyVoyageOrder() - KAR failed to locate order actor instance " + orderId);
            } else {
               orderActorException.printStackTrace();
            }
         }
         order.setStatus(orderStatus.name());

         JsonObjectBuilder job = Json.createObjectBuilder();
         job.add(Constants.STATUS_KEY, booking.asJsonObject().getString(Constants.STATUS_KEY)).
                 add(Constants.REEFERS_KEY, booking.asJsonObject().getJsonNumber(Constants.REEFERS_KEY).intValue()).
                 add(Constants.ORDER_KEY, order.getAsJsonObject());

         try {
            if (booking.asJsonObject() == null) {
               System.out.println("VoyageActor.notifyVoyageOrder() - ID:" + getId() + " ** " + methodName + " ** " + " booking object is not valid:" + booking);
            } else if (!booking.asJsonObject().containsKey(Constants.ORDER_REEFERS_KEY)) {
               System.out.println("VoyageActor.notifyVoyageOrder() - ID:" + getId() + " ** " + methodName + " ** " + " booking has no reefers:" + booking);
            } else if (booking.asJsonObject().getString(Constants.ORDER_REEFERS_KEY) != null) {
               job.add(Constants.ORDER_REEFERS_KEY, booking.asJsonObject().get(Constants.ORDER_REEFERS_KEY));
            } else {
               System.out.println("VoyageActor.notifyVoyageOrder() - ID:" + getId() + " order:" + orderId + " ** " + methodName + " ** " + " does not have any reefers - booking result:" + booking);
            }
         } catch (Exception e) {
            e.printStackTrace();
            System.out.println("VoyageActor.notifyVoyageOrder() - ID:" + getId() + " order:" + orderId + " ** " + methodName + " ** " + " booking:" + booking);
         }

         JsonObject jo = job.build();
         //System.out.println("VoyageActor.notifyVoyageOrder() - ID:"+getId()+ " order:"+orderId+" json:"+jo.toString().substring(0,1000));
         orders.put(order.getId(), jo);
         Kar.Actors.State.Submap.set(this, Constants.VOYAGE_ORDERS_KEY, order.getId(), jo);
      }
   }

   /**
    * Call OrderActor when a ship carrying the order either departs or arrives
    *
    * @param orderId      - order id
    * @param methodToCall - actor method to call
    */
   private void messageOrderActor(String methodToCall, String orderId) {
      ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderActorType, orderId);
      Kar.Actors.call(orderActor, methodToCall);
   }

   /**
    * Call ScheduleManagerActor when a ship changes position
    *
    * @param voyage       - voyage details
    * @param methodToCall - actor method to call
    */
   private void messageSchedulerActor(String methodToCall, Voyage voyage) {
      ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
      JsonObject jo = VoyageJsonSerializer.serialize(voyage);
      Kar.Actors.tell(scheduleActor, methodToCall, jo);
   }

   /**
    * Converts voyage status from JsonValue to VoyageStatus
    *
    * @return VoyageStatus instance
    */
   private VoyageStatus getVoyageStatus() {
      if (Objects.isNull(voyageStatus)) {
         return VoyageStatus.UNKNOWN;
      }
      return VoyageStatus.valueOf(((JsonString) voyageStatus).getString());
   }

   private class DepotReply {

      private JsonValue jv;
      private JsonValue reeferCount;

      protected DepotReply(JsonValue jsonReply) {
         jv = jsonReply;
      }

      protected boolean success() {
         return jv != null &&
                 jv.asJsonObject() != null  &&
                 jv.asJsonObject().containsKey(Constants.STATUS_KEY) &&
                 jv.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK);
      }

      protected int getReeferCount() {
         if (reeferCount == null) {
            reeferCount = jv.asJsonObject().get(Constants.REEFERS_KEY);
         }
         return ((JsonNumber) reeferCount).intValue();
      }
      protected String getOrderId() {
         try {
            if ( jv.asJsonObject() != null ) {
               return jv.asJsonObject().getJsonObject(Constants.ORDER_KEY).getString(Constants.ORDER_ID_KEY);
            }
         } catch( Exception e) {
            e.printStackTrace();
            System.out.println("VoyageActor.getOrderId()- Missing Order ID in:"+jv);
         }

         throw new IllegalStateException("Missing Order ID");
      }
      protected Set<String> getReefers() {
         Set<String> orderReefers = new HashSet<>();
         if ( jv.asJsonObject() != null && jv.asJsonObject().containsKey(Constants.ORDER_REEFERS_KEY)) {
            String[] reefers = jv.asJsonObject().getString(Constants.ORDER_REEFERS_KEY).split(",");
            for( String rid : reefers ) {
               orderReefers.add(rid);
            }
         }
         return orderReefers;
      }
   }
}