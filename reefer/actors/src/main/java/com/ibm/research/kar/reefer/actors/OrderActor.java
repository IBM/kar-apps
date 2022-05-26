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
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Actor
public class OrderActor extends BaseActor {
   // wrapper containing order state
   private Order order = null;
   private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(OrderActor.class.getName());

   @Activate
   public void activate() {
      Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
      try {
         // initial actor invocation should handle no state
         if (!state.isEmpty()) {
            order = new Order(state.get(Constants.ORDER_KEY).asJsonObject());
            if (logger.isLoggable(Level.FINE)) {
               logger.fine(String.format("OrderActor.activate() - orderId: %s state: %s voyageId: %s ",
                       getId(), order.getStatus(), order.getVoyageId()));
            }
         }
      } catch (Exception e) {
         logger.log(Level.SEVERE, "OrderActor.activate() Error ", e);
      }
   }

   @Remote
   public Kar.Actors.TailCall processReeferBookingResult(JsonObject orderAsJson) {
      try {
         if (order.getStatus().equals(OrderStatus.BOOKED.name()) || order.getStatus().equals(OrderStatus.INTRANSIT.name())) {
            logger.log(Level.WARNING, "OrderActor.processReeferBookingResult() - duplicate booked message received for corrId=" + order.getCorrelationId() + " orderId:" + order.getId() + " status:" + order.getStatus());
            return null;
         }
         return new Kar.Actors.TailCall(this, "saveOrderStateChangeAndNotify", orderAsJson);
      } catch (Exception e) {
         String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n", "");
         logger.log(Level.SEVERE, "OrderActor.processReeferBookingResult() - Error - orderId " + getId() + " Error: " + stacktrace);
         return null;
      }
   }

   @Remote
   public Kar.Actors.TailCall saveOrderStateChangeAndNotify(JsonObject orderAsJson) {
     Order booking = new Order(orderAsJson);
     if (booking.isBookingFailed()) {
        logger.log(Level.SEVERE, "OrderActor.saveOrderStateChangeAndNotify() - failed - corrId: " + booking.getCorrelationId() + " orderId: " + booking.getId() + " reason: " + booking.getMsg());
        order.setBookingFailed();
        order.setMsg(booking.getMsg());
     } else {
        order.setStatus(OrderStatus.BOOKED.name());
     }
     Kar.Actors.State.set(this, Constants.ORDER_KEY, order.getAsJsonObject());
     logger.log(Level.FINE, "OrderActor.saveOrderStateChangeAndNotify() - notifying OrderManager - order: "+getId() +" corrId: "+order.getCorrelationId() );
     return new Kar.Actors.TailCall(Kar.Actors.ref(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId),
           "processReeferBookingResult", orderAsJson);
   }

   /**
    * Called to book a new order using properties included in the message. Calls the VoyageActor
    * to allocate reefers and a ship to carry them.
    *
    * @return
    */
   @Remote
   public Kar.Actors.TailCall createOrder(JsonObject orderAsJson) {
      try {
         // Java wrapper around Json payload
         order = new Order(orderAsJson);
         order.setStatus(OrderStatus.PENDING.name());
         Kar.Actors.State.set(this, Constants.ORDER_KEY, order.getAsJsonObject());
         return new Kar.Actors.TailCall(Kar.Actors.ref(ReeferAppConfig.VoyageActorType, order.getVoyageId()),
                 "reserve", order.getAsJsonObject());
      } catch (Exception e) {
         logger.log(Level.WARNING, "OrderActor.createOrder() - Error - orderId " + getId() + " ", e);
         order.setMsg(e.getMessage());
         return null;
      }
   }
   @Remote
   public void replaceReefer(JsonObject message) {
      if (order == null) {
         Kar.Actors.remove(this);
         return;
      }
      Actors.Builder.instance().target(ReeferAppConfig.VoyageActorType, order.getVoyageId()).
              method("replaceReefer").arg(message).tell();
   }

   /**
    * Called when an order is delivered (ie.ship arrived at the destination port).
    *
    * @return
    */
   @Remote
   public void delivered() {
      Kar.Actors.remove(this);
   }

   @Remote
   public void cancel() {
      logger.warning(String.format("OrderActor.cancel() - orderId: %s - order cancelled due to rollback", getId()));
      Kar.Actors.remove(this);
   }

   /**
    * Called when ship departs from an origin port.
    *
    * @return
    */
   @Remote
   public JsonObject departed() {
      if (order == null) {
         Kar.Actors.remove(this);
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.FAILED)
                 .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).add("ERROR", "Order Already Arrived").build();
      }
      if (!OrderStatus.DELIVERED.name().equals(order.getStatus()) && !OrderStatus.INTRANSIT.name().equals(order.getStatus())) {
         saveOrderStatusChange(OrderStatus.INTRANSIT);
      }
      return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
              .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
   }

   private JsonObject saveOrderStatusChange(OrderStatus state) {
      order.setStatus(state.name());
      Kar.Actors.State.set(this, Constants.ORDER_KEY, order.getAsJsonObject());
      return order.getAsJsonObject();
   }
}
