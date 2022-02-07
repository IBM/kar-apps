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
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
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
import org.apache.commons.lang.exception.ExceptionUtils;

@Actor
public class VoyageActor extends BaseActor {
   private JsonObject voyageInfo;
   private Voyage voyage = null;
   private JsonValue voyageStatus;
   private Map<String, JsonValue> orders = new HashMap<>();
   private Map<String, JsonValue> spoiltReefersMap = new HashMap<>();
   private Map<String, String> reefer2OrderMap = new HashMap<>();
   private Map<String, JsonValue> spoiltOrders = new HashMap<>();
   private Map<String, String> emptyReefersMap = new HashMap<>();
   private JsonValue emptyReefers = null;
   private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(VoyageActor.class.getName());

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
            logger.fine("VoyageActor.init() voyageId:" + this.getId() + " all state" + state);
         }
         // initial actor invocation should handle no state
         if (state.isEmpty()) {
            JsonObject params = Json.createObjectBuilder().
                    add(Constants.VOYAGE_ID_KEY, getId()).add(Constants.VOYAGE_ID_KEY, getId()).
                    build();
            // fetch voyage details
            JsonValue jv = Actors.Builder.instance().target(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId).
                    method("voyage").arg(Json.createValue(getId())).call();
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
            if (state.containsKey(Constants.VOYAGE_INFO_KEY)) {
               voyageInfo = state.get(Constants.VOYAGE_INFO_KEY).asJsonObject();
            }
            if (state.containsKey(Constants.VOYAGE_STATUS_KEY)) {
               voyageStatus = state.get(Constants.VOYAGE_STATUS_KEY);
            }
            if (state.containsKey(Constants.VOYAGE_EMPTY_REEFERS_KEY)) {
               emptyReefers = state.get(Constants.VOYAGE_EMPTY_REEFERS_KEY);
               String[] emptyReeferIds = ((JsonString)emptyReefers).getString().split(",");
               for( String emptyReeferId : emptyReeferIds ) {
                  emptyReefersMap.put(emptyReeferId, emptyReeferId);
               }
               logger.info("VoyageActor.activate() - voyageId:" + getId() + " restored empties - size:" + emptyReefersMap.size());
            }
            if (state.containsKey(Constants.VOYAGE_ORDERS_KEY)) {
               orders.putAll(state.get(Constants.VOYAGE_ORDERS_KEY).asJsonObject());
               int reeferCount=0;
               // build reefer to order map so that we can efficiently retrieve order id
               // when processing refer anomaly
               for(Map.Entry<String, JsonValue> entry: orders.entrySet()) {
                  DepotReply reeferBookingRecord = new DepotReply(entry.getValue());
                  for( String reeferId : reeferBookingRecord.getReefers()) {
                     reefer2OrderMap.put(reeferId, reeferBookingRecord.getOrderId());
                     reeferCount++;
                  }
                  logger.info("VoyageActor.activate() - voyageId:" + getId() + " restored order: "+reeferBookingRecord.getOrderId());
               }
               logger.info("VoyageActor.activate() - voyageId:" + getId() + " restored orders - size:" + orders.size()+" total reefers:"+reeferCount);
            }
            if (state.containsKey(Constants.SPOILT_REEFERS_KEY)) {
               spoiltReefersMap.putAll(state.get(Constants.SPOILT_REEFERS_KEY).asJsonObject());
               logger.info("VoyageActor.activate() - voyageId:" + getId() + " restored spoilt reefers - size:" + spoiltReefersMap.size());
            }
            if (state.containsKey(Constants.SPOILT_ORDERS_KEY)) {
               spoiltOrders.putAll(state.get(Constants.SPOILT_ORDERS_KEY).asJsonObject());
               logger.info("VoyageActor.activate() - voyageId:" + getId() + " restored spoilt orders - size:" + spoiltOrders.size());
            }
         }
         voyage = VoyageJsonSerializer.deserialize(voyageInfo);
      } catch (Exception e) {
         logSevereError("activate()", e);
      }

   }
   @Remote
   public void rollbackOrder(JsonObject message) {
      Order order = new Order(message);
      // ship already arrived?
      if ( voyage.getProgress() >= 100 ) {
         logger.warning("VoyageActor.rollbackOrder() voyageId:" + getId() + " - already arrived - unable to rollback order:" + order.getId() );
         return;
      }
      if ( !orders.containsKey(order.getId() ) ) {
         logger.warning("VoyageActor.rollbackOrder() voyageId:" + getId() + " - unknown order:" + order.getId() );
         return;
      }
      DepotReply reply = new DepotReply(orders.get(order.getId()).asJsonObject());
      // ship already departed?
      if ( voyage.getProgress() > 0 ) {
            // the order is on a ship, so we can't remove it. Since OrderManager
            // does not know about this order (rollback call), let it know that
            // this order has been booked.
            JsonObject booking = buildResponse(reply.getOrder(), voyage.getRoute().getVessel().getFreeCapacity());
            Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, reply.getOrderId()).
                    method("orderBooked").arg(booking).tell();
      } else {
         // The ship is still at port. Tell Depot to undo reefer allocation for this order.
         Actors.Builder.instance().target(ReeferAppConfig.DepotActorType, reply.getDepot()).
                 method("rollbackOrder").arg(order.getAsJsonObject()).tell();
         Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                 method("cancel").arg().tell();
         orders.remove(order.getId());
         voyage.setReeferCount(voyage.getReeferCount() - reply.getReeferCount());
         voyage.updateFreeCapacity(voyage.getRoute().getVessel().getFreeCapacity() + reply.getReeferCount());
         save(reply, orders.get(order.getId()));
         logger.warning("VoyageActor.rollbackOrder() voyageId:" + getId() + " - order:" + order.getId()+" rolled back" );

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
      if (logger.isLoggable(Level.FINE)) {
         logger.fine("VoyageActor.changePosition() voyageId:" + getId() + " " + message.toString() + " state:"
                 + getVoyageStatus());
      }
      if (Objects.isNull(voyage) ) {
         Kar.Actors.remove(this);
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                 .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
      }
      if ( voyage.shipArrived() ) {
         Kar.Actors.remove(this);
         if ( logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "VoyageActor.changePosition() - voyageId:" + getId() + " voyage already arrived - ignoring ");
         }
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
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
                  logger.log(Level.WARNING, "VoyageActor.changePosition() - voyageId:" + voyage.getId() + " arrived BUT its expected state is not DEPARTED. Instead it is " + getVoyageStatus());
               }

               voyageStatus = Json.createValue(VoyageStatus.ARRIVED.name());
               // notify voyage orders of arrival
               processArrivedVoyage(voyage);
               // voyage arrived, no longer need the state
               Kar.Actors.remove(this);
               if (logger.isLoggable(Level.INFO)) {
                  logger.info(
                          "VoyageActor.changePosition() voyageId:" + voyage.getId() + " - ARRIVED - Actor state removed");
               }
            } else {

               JsonObjectBuilder jb = Json.createObjectBuilder();
               if (voyage.shipDeparted(shipCurrentDate, getVoyageStatus())) {
                  // notify voyage orders of departure
                  processDepartingVoyage(voyage);
                  voyageStatus = Json.createValue(VoyageStatus.DEPARTED.name());
                  jb.add(Constants.VOYAGE_STATUS_KEY, voyageStatus);
                  if ( emptyReefers != null ) {
                     jb.add(Constants.VOYAGE_EMPTY_REEFERS_KEY, emptyReefers);
                  }
               } else {  // voyage in transit
                  Actors.Builder.instance().target(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId).
                          method("positionChanged").arg(VoyageJsonSerializer.serialize(voyage)).tell();
               }

               jb.add(Constants.VOYAGE_INFO_KEY, VoyageJsonSerializer.serialize(voyage));
               Kar.Actors.State.set(this, jb.build());
            }
         }
      } catch (Exception e) {
         logSevereError("changePosition()", e);
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "VoyageActor.changePosition() Failed - "+e.getMessage())
                 .add(Constants.VOYAGE_ID_KEY, this.getId()).build();
      }
      return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
   }

   @Remote
   public void reeferAnomaly(JsonObject message) {
      try {

         String spoiltReeferId = String.valueOf(message.getInt(Constants.REEFER_ID_KEY));
         // for debug
         if ( voyage == null ) {
            logger.warning("VoyageActor.reeferAnomaly - voyageId:"+getId()+
                    " voyage already arrived - spoilt reefer:"+spoiltReeferId+" should be in the depot by now");
            return;
         }
         // ignore anomaly if target is an empty reefer
         if ( emptyReefersMap.containsKey(spoiltReeferId)) {
            return;
         }
         if ( Objects.isNull(voyageInfo)) {   // voyage arrived?
            Kar.Actors.remove(this);
            JsonObjectBuilder job = Json.createObjectBuilder();
            // switch anomaly mgr target from voyage to depot
            job.add(Constants.REEFER_ID_KEY, message.getJsonNumber(Constants.REEFER_ID_KEY)).
                    add(Constants.TARGET_KEY, Constants.DEPOT_TARGET_TYPE);
            Actors.Builder.instance().target(ReeferAppConfig.AnomalyManagerActorType, ReeferAppConfig.AnomalyManagerId).
                    method("reeferAnomaly").arg(job.build()).tell();
            return;
         }
         if ( !reefer2OrderMap.containsKey(spoiltReeferId)) {
            return;
         }
         logger.info("VoyageActor.reeferAnomaly - voyageId:"+getId()+" spoilt reefer: "+
                 spoiltReeferId);
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
                  logger.warning("VoyageActor.reeferAnomaly - voyageId:"+getId()+" spoilt reefer:"+
                          spoiltReeferId+" !!!!!!!!!!!!!!!!! order:"+orderId+" not found in the orders Map - orders map size:"+
                          orders.size()+"\n"+sb.toString());
               }
               newSpoiltOrder = true;
               spoiltOrders.put(orderId, Json.createValue(orderId));
               JsonValue jv = orders.get(orderId).asJsonObject();
               Order order = new Order(orders.get(orderId).asJsonObject().getJsonObject(JsonOrder.OrderKey));
               order.setSpoilt(true);
               Actors.Builder.instance().target(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId).
                       method("orderSpoilt").arg(order.getAsJsonObject()).call();
            }

            Map<String, JsonValue> actorStateMap = new HashMap<>();
            actorStateMap.put(Constants.TOTAL_SPOILT_KEY, Json.createValue(spoiltReefersMap.size()));
            Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
            subMapUpdates.put(Constants.SPOILT_REEFERS_KEY, spoiltReefersMap);
            if ( newSpoiltOrder ) subMapUpdates.put(Constants.SPOILT_ORDERS_KEY, spoiltOrders);
            Kar.Actors.State.update(this, Collections.emptyList(), Collections.emptyMap(), actorStateMap, subMapUpdates);
         }
      } catch( Exception e) {
         logSevereError("reeferAnomaly()", e);
      }
   }
   @Remote
   public void reefersBooked(JsonObject message) {
      try {
          // convenience wrapper for DepotActor json reply
         DepotReply reply = new DepotReply(message);
            logger.info("VoyageActor.reefersBooked() - voyageId:" + getId() + " orderId:" + reply.getOrderId() + " booked - reefers:"
                    + reply.getReefers().size());

            Set<String> orderReefers = reply.getReefers();
            for (String rid : orderReefers) {
               reefer2OrderMap.put(rid, reply.getOrderId());
            }
            if (orderReefers.isEmpty()) {
               logger.warning("VoyageActor.reefersBooked() - voyageId:" + getId() + " orderId:" + reply.getOrderId() + " !!!!!!!!!!!!!!!! booking has no reefers:" + message);
            }
            logger.fine("VoyageActor.reefersBooked() - voyageId:" + getId() + " orderId:" + reply.getOrderId() + " booked - reefers:"
                    + orderReefers.size());
            voyage.setReeferCount(voyage.getReeferCount() + reply.getReeferCount());
            voyage.updateFreeCapacity(reply.getReeferCount());
            orders.put(reply.getOrderId(), message);
            voyage.setOrderCount(orders.size());
            voyageStatus = Json.createValue(VoyageStatus.PENDING.name());
            save(reply, message);
            Actors.Builder.instance().target(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId).
                    method("updateVoyage").arg(VoyageJsonSerializer.serialize(voyage)).tell();
             //// SUCCESS
            JsonObject booking = buildResponse(reply.getOrder(), voyage.getRoute().getVessel().getFreeCapacity());
            Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, reply.getOrderId()).
                    method("orderBooked").arg(booking).tell();
      } catch( Exception e) {
         logSevereError("VoyageActor.reefersBooked()", e);
      }

   }
   @Remote
   public void reeferBookingFailed(JsonObject message) {
      try {
         Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, message.getString(Constants.ORDER_ID_KEY)).
                 method("orderFailed").arg(message).tell();
       } catch( Exception e) {
         logSevereError("reeferBookingFailed()", e);
      }
   }
   private boolean handledAlreadyArrived(Order order) {
      if ( Objects.isNull(voyageInfo)) {   // voyage arrived
         Kar.Actors.remove(this);
         JsonObjectBuilder job = Json.createObjectBuilder();
         job.add(Constants.STATUS_KEY, "FAILED").
                 add("ERROR", " voyage " + getId() + " already arrived").
                 add(Constants.ORDER_ID_KEY, order.getId());
         Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                 method("orderFailed").arg(job.build()).tell();
         return true;
      }
      return false;
   }
   private boolean handledAlreadyDeparted(Order order) {
      // booking may come after voyage departure
      if (VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
         logger.log(Level.WARNING, "VoyageActor.reserve() - voyageId:" + getId() + " - already departed - rejecting order booking - " + order.getId());
         JsonObjectBuilder job = Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", " voyage " + getId() + " already departed")
                 .add(Constants.ORDER_ID_KEY, this.getId());
         Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                 method("orderFailed").arg(job.build()).tell();
         return true;
      }
      return false;
   }
   private boolean handledIdempotance(Order order) {
      // Idempotence check. If a given order is in this voyage order list it must have already been processed.
      if (orders.containsKey(order.getId())) {
         JsonValue booking = orders.get(order.getId());
         // this order has already been processed so return result
         if (booking.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
            JsonObject status = buildResponse(order.getAsJsonObject(), voyage.getRoute().getVessel().getFreeCapacity());
           Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                    method("orderBooked").arg(status).tell();
         } else {
            Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                    method("orderFailed").arg(booking).tell();
          }
         return true;
      }
      return false;
   }
   private boolean handledShipFull(Order order) {
      // Check if ship has capacity for the order.
      int howManyReefersNeeded = ReeferAllocator.howManyReefersNeeded(order.getProductQty());
      if (!voyage.capacityAvailable(howManyReefersNeeded)) {
         String msg = "Error - ship capacity exceeded - current available capacity:" + voyage.getRoute().getVessel().getFreeCapacity() * 1000 +
                 " - reduce product quantity or choose a different voyage";
         JsonObjectBuilder job =  Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.FAILED)
                 .add("ERROR", msg);
         Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                 method("orderFailed").arg(job.build()).tell();
         return true;
      }
      return false;
   }
   private boolean validateAndContinue(Order order, JsonObject message) {
      if ( handledAlreadyArrived(order)) {
         return false; // dont continue with the booking
      }
      if ( handledAlreadyDeparted(order)) {
         return false; // dont continue with the booking
      }
      if ( handledIdempotance(order)) {
         return false;  // dont continue with the booking
      }
      if ( handledShipFull(order)) {
         return false;  // dont continue with the booking
      }
      // Continue with booking
      return true;
   }
   /**
    * Called to book a voyage for a given order. Calls DepotActorto book reefers and
    * stores orderId in the Kar persistent storage.
    *
    * @param message Json encoded order properties
    * @return - result of reefer booking
    */
   @Remote
   public void reserve(JsonObject message) {
      // wrapper around Json
      Order order = new Order(message);
      logger.info("VoyageActor.reserve() - voyageId:"+getId()+" - message:"+message);

      try {
         if ( !validateAndContinue(order, message)) {
            return;
         }
          Actors.Builder.instance().target(ReeferAppConfig.DepotActorType, DepotManagerActor.Depot.makeId(voyage.getRoute().getOriginPort())).
                 method("bookReefers").arg(message).tell();
      } catch (Exception e) {
         logSevereError("reserve()", e);
         JsonObjectBuilder job = Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                 .add(Constants.ORDER_ID_KEY, this.getId());
        Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, order.getId()).
                 method("orderFailed").arg(job.build()).tell();
      }
   }

   private void save(DepotReply booking, JsonValue bookingStatus) {
      Map<String, JsonValue> actorStateMap = new HashMap<>();
      actorStateMap.put(Constants.VOYAGE_STATUS_KEY, voyageStatus);
      actorStateMap.put(Constants.VOYAGE_INFO_KEY, VoyageJsonSerializer.serialize(voyage));
      if ( emptyReefers != null ) {
         actorStateMap.put(Constants.VOYAGE_EMPTY_REEFERS_KEY, emptyReefers);
      }

      Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
      Map<String, JsonValue> orderSubMapUpdates = new HashMap<>();
      orderSubMapUpdates.put(booking.getOrderId(), bookingStatus);
      subMapUpdates.put(Constants.VOYAGE_ORDERS_KEY, orderSubMapUpdates);

      Kar.Actors.State.update(this, Collections.emptyList(), Collections.emptyMap(), actorStateMap, subMapUpdates);
   }

   private JsonObject buildResponse(final JsonObject order, final int freeCapacity) {
      return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
              .add(JsonOrder.OrderKey, order)
              .add(Constants.VOYAGE_FREE_CAPACITY_KEY, freeCapacity)
              .build();
   }

   @Remote
   public void replaceReefer(JsonObject message) {
      if ( Objects.isNull(voyageInfo)) {   // voyage arrived?
         Kar.Actors.remove(this);
         return;
      }
      try {
         String spoiltReeferId = String.valueOf(message.getInt(Constants.REEFER_ID_KEY));
         // ignore anomaly if target is an empty reefer
         if ( emptyReefersMap.containsKey(spoiltReeferId)) {
            return;
         }
         if (VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
            boolean reeferExists = reefer2OrderMap.containsKey(spoiltReeferId);
            logger.info("VoyageActor.replaceReefer() - voyageId:"+getId()+" - too late to replace reefer "+
                    spoiltReeferId+", voyage already departed - processing as reefer anomaly - reefer exists:"+reeferExists);
            reeferAnomaly(message);
            return;
         }
          JsonValue reply = Actors.Builder.instance().target(ReeferAppConfig.DepotActorType, DepotManagerActor.Depot.makeId(voyage.getRoute().getOriginPort())).
                 method("reeferReplace").arg(message).call();

         if ( !reply.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
           logger.info("VoyageActor.replaceReefer() - voyageId:"+getId()+" - Error:"+reply.asJsonObject().getString(Constants.ERROR)+" - processing as reefer anomaly");
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

            logger.log(Level.INFO,"VoyageActor.replaceReefer() - voyageId:"+getId()+" replaced: " +spoiltReeferId+ " with: "+newReeferId);
            Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
            Map<String, JsonValue> orderSubMapUpdates = new HashMap<>();
            orderSubMapUpdates.put(orderId, updatedBooking);
            subMapUpdates.put(Constants.VOYAGE_ORDERS_KEY, orderSubMapUpdates);

            Kar.Actors.State.update(this, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), subMapUpdates);
         }
      } catch( Exception e) {
         logSevereError("replaceReefer()", e);
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
            String orderReeferIds = order.asJsonObject().getString(Constants.ORDER_REEFERS_KEY);
            if (orderReeferIds == null) {
               logger.log(Level.WARNING, String.format("VoyageActor.processArrivedVoyage - voyageId: %s  REEFERS MISSING FROM ORDER: %s booking status: %s",getId(), orderId ,order));
            } else if (orderReeferIds.trim().length() == 0) {
               logger.log(Level.WARNING, String.format("VoyageActor.processArrivedVoyage - voyageId: %s  REEFERS NOT BOOKED TO ORDER: %s booking status: %s",getId(), orderId ,order));
            } else {
               reeferIds.append(orderReeferIds).append(",");
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
	         if ( emptyReefers != null ) {
	            job.add(Constants.VOYAGE_EMPTY_REEFERS_KEY, emptyReefers);
	         }
            Actors.Builder.instance().target(ReeferAppConfig.DepotActorType, DepotManagerActor.Depot.makeId(voyage.getRoute().getDestinationPort())).
                    method("voyageReefersArrived").arg(job.build()).call();
         }
         Actors.Builder.instance().target(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId).
                 method("voyageArrived").arg(VoyageJsonSerializer.serialize(voyage)).tell();
          Actors.Builder.instance().target(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId).
                 method("ordersArrived").arg(voyageOrderIds).call();
         orders.keySet().forEach(orderId -> {
            notifyVoyageOrder(orderId, Order.OrderStatus.DELIVERED, "delivered");
         });

      } catch (Exception e) {
         logSevereError("processArrivedVoyage()", e);
      }
   }

   /**
    * Calls REST and Order actors when a ship departs from the origin port
    *
    * @param voyage - Voyage info
    */
   private void processDepartingVoyage(final Voyage voyage) {
      try {
         JsonObject params = Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).
                 add(Constants.VOYAGE_REEFERS_KEY, Json.createValue(voyage.getReeferCount())).
                 add(Constants.VOYAGE_FREE_CAPACITY_KEY, Json.createValue(voyage.getRoute().getVessel().getFreeCapacity())).
                 build();
         JsonValue reply = Actors.Builder.instance().target(ReeferAppConfig.DepotActorType, DepotManagerActor.Depot.makeId(voyage.getRoute().getOriginPort())).
                 method("voyageReefersDeparted").arg(params).call();

         // the depot may return a number of empty reefers to sail on the voyage due to
         // excess reefer inventory
         int emptiesCount = reply.asJsonObject().getInt(Constants.REEFERS_KEY);
         String emptiesRids = reply.asJsonObject().getString(Constants.VOYAGE_EMPTY_REEFERS_KEY);
         if (emptiesRids.trim().length()==0 || emptiesRids.trim().length()==1) {
            emptiesRids ="";
         }
         emptyReefers = Json.createValue(emptiesRids);
         for( String emptyReeferId : ((JsonString)emptyReefers).getString().split(",") ) {
            if	( emptyReeferId.trim().length() > 0 ) {
               emptyReefersMap.put(emptyReeferId, emptyReeferId);
            }
         }
         logger.info("VoyageActor.processDepartingVoyage() - voyageId:"+getId()+" Departure from "+voyage.getRoute().getOriginPort() +" empties count:"+emptiesCount+" emptyReefersMap.size():"+emptyReefersMap.size());
         voyage.updateFreeCapacity(emptiesCount);
         voyage.setReeferCount(voyage.getReeferCount()+emptiesCount);
         Actors.Builder.instance().target(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId).
                 method("voyageDeparted").arg(VoyageJsonSerializer.serialize(voyage)).tell();
         orders.keySet().forEach(orderId -> {
            notifyVoyageOrder(orderId, Order.OrderStatus.INTRANSIT, "departed");
         });
      } catch (Exception e) {
         logSevereError("processDepartedVoyage()", e);
         throw e;
      }

   }

   private void notifyVoyageOrder(String orderId, Order.OrderStatus orderStatus, String methodName) {
      JsonValue value = orders.get(orderId);
      JsonObject booking = value.asJsonObject();
      JsonObject jorder = booking.getJsonObject(Constants.ORDER_KEY);
      Order order = new Order(booking.asJsonObject().getJsonObject(Constants.ORDER_KEY));
      if (!orderStatus.name().equals(order.getStatus())) {
         try {
            Actors.Builder.instance().target(ReeferAppConfig.OrderActorType, orderId).
                    method(methodName).arg().call();
         } catch (Exception orderActorException) {
            // KAR sometimes fails to locate order actor instance even though it exists in REDIS. This can happen
            // after process restart
            if (orderActorException.getMessage().startsWith("Actor instance not found:")) {
               // ignore the error for now, eventually KAR will not throw this error
               logger.log(Level.WARNING, "VoyageActor.notifyVoyageOrder() - voyageId:"+getId()+" KAR failed to locate order actor instance " + orderId);
            } else {
               logger.log(Level.SEVERE,"VoyageActor.notifyVoyageOrder  - voyageId:"+getId() + " Error:", orderActorException);
            }
            throw orderActorException;
         }
         order.setStatus(orderStatus.name());

         JsonObjectBuilder job = Json.createObjectBuilder();
         job.add(Constants.STATUS_KEY, booking.asJsonObject().getString(Constants.STATUS_KEY)).
                 add(Constants.REEFERS_KEY, booking.asJsonObject().getJsonNumber(Constants.REEFERS_KEY).intValue()).
                 add(Constants.ORDER_KEY, order.getAsJsonObject());

         try {
            if (booking.asJsonObject() == null) {
               logger.log(Level.WARNING, String.format("VoyageActor.notifyVoyageOrder - voyageId: %s method name: %s - booking object is not valid:%s",getId(), methodName,booking));
            } else if (!booking.asJsonObject().containsKey(Constants.ORDER_REEFERS_KEY)) {
               logger.log(Level.WARNING, String.format("VoyageActor.notifyVoyageOrder - voyageId: %s method name: %s - booking has no reefers:%s",getId(), methodName,booking));
            } else if (booking.asJsonObject().getString(Constants.ORDER_REEFERS_KEY) != null) {
               job.add(Constants.ORDER_REEFERS_KEY, booking.asJsonObject().get(Constants.ORDER_REEFERS_KEY));
            } else {
               logger.log(Level.WARNING, String.format("VoyageActor.notifyVoyageOrder - voyageId: %s method name: %s - invalid booking :%s",getId(), methodName,booking));
            }
         } catch (Exception e) {
            logger.log(Level.WARNING, String.format("VoyageActor.notifyVoyageOrder - voyageId: %s order: %s booking:%s",getId(),orderId,booking), e);
            throw e;
         }
         JsonObject jo = job.build();
         orders.put(order.getId(), jo);
         Kar.Actors.State.Submap.set(this, Constants.VOYAGE_ORDERS_KEY, order.getId(), jo);
      }
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

   private void logSevereError(String methdodName, Exception e) {
      String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
      logger.log(Level.SEVERE,"VoyageActor." +methdodName+" voyageId:"+getId()+" Error:" +stacktrace);

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
      protected JsonObject getOrder() {
         try {
            if ( jv.asJsonObject() != null ) {
               return jv.asJsonObject().getJsonObject(Constants.ORDER_KEY);
            }
         } catch( Exception e) {
            e.printStackTrace();
            System.out.println("VoyageActor.getOrder()- Missing Order in:"+jv);
         }

         throw new IllegalStateException("Missing Order Object");
      }
      protected String getDepot() {
         try {
            if ( jv.asJsonObject() != null ) {
               return jv.asJsonObject().getJsonObject(Constants.ORDER_KEY).getString(Constants.DEPOT_KEY);
            }
         } catch( Exception e) {
            e.printStackTrace();
            System.out.println("VoyageActor.getDepot()- Missing Order in:"+jv);
         }

         throw new IllegalStateException("VoyageActor.getDepot() - Missing Order Object");
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