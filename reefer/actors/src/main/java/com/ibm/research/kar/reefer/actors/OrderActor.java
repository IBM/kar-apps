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
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;

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
         logger.log(Level.SEVERE,"OrderActor.activate()", e);
      }


   }
/*
   @Remote
   public JsonObject state() {

      try {
         if (order == null) {
            activate();
         }
         return order.getAsJsonObject();
      } catch (Exception e) {
         logger.log(Level.SEVERE,"OrderActor.state()", e);
         throw e;
      }

   }
*/
   /**
    * Called to book a new order using properties included in the message. Calls the VoyageActor
    * to allocate reefers and a ship to carry them.
    *
    * @param message Order properties
    * @return
    */
   @Remote
   public JsonObject createOrder(JsonObject message) {
      // Idempotence test. Check if this order has already been booked.
      if (order != null && OrderStatus.BOOKED.name().equals(order.getStatus())) {
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                 .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
      }
      try {
         // Java wrapper around Json payload
         order = new Order(message);
         // Call Voyage actor to book the voyage for this order. This call also
         // reserves reefers
         JsonObject voyageBookingResult = bookVoyage(order);
         if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("OrderActor.createOrder() - orderId: %s VoyageActor reply: %s", getId(), voyageBookingResult));
         }
         // Check if voyage has been booked
         if (voyageBookingResult.containsKey(Constants.STATUS_KEY) && voyageBookingResult.getString(Constants.STATUS_KEY).equals(Constants.OK)) {
            order.setDepot(voyageBookingResult.getString(Constants.DEPOT_KEY));
            Kar.Actors.State.set(this, Constants.ORDER_KEY, order.getAsJsonObject());
            messageOrderManager("orderBooked");
         } else {
            Kar.Actors.remove(this);
         }
         return voyageBookingResult;
      } catch (Exception e) {
         logger.log(Level.WARNING, "OrderActor.createOrder() - Error - orderId " + getId() + " ", e);
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                 .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
      }
   }

   private void messageOrderManager(String methodToCall) {
      ActorRef orderMgrActor = Kar.Actors.ref(ReeferAppConfig.OrderManagerActorType, ReeferAppConfig.OrderManagerId);
      Kar.Actors.tell(orderMgrActor, methodToCall, order.getAsJsonObject());
   }

   @Remote
   public void replaceReefer(JsonObject message) {
      if ( order == null ) {
         Kar.Actors.remove(this);
         return;
      }
      ActorRef voyageActor = Kar.Actors.ref(ReeferAppConfig.VoyageActorType, order.getVoyageId());
      Kar.Actors.tell(voyageActor, "replaceReefer", message);
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

   /**
    * Called when ship departs from an origin port.
    *
    * @return
    */
   @Remote
   public JsonObject departed() {
      if ( order == null ) {
         Kar.Actors.remove(this);
         return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.FAILED)
                 .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).add("ERROR","Order Already Arrived").build();
      }
      if (order != null && !OrderStatus.DELIVERED.name().equals(order.getStatus()) && !OrderStatus.INTRANSIT.name().equals(order.getStatus())) {
         messageOrderManager("orderDeparted");
         saveOrderStatusChange(OrderStatus.INTRANSIT);
      }
      return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
              .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
   }

   private void saveOrderStatusChange(OrderStatus state) {

      order.setStatus(state.name());
      Kar.Actors.State.set(this, Constants.ORDER_KEY, order.getAsJsonObject());
   }

   /**
    * Called to book voyage for this order by messaging Voyage actor.
    *
    * @param order Json encoded order properties
    * @return The voyage booking result
    */
   private JsonObject bookVoyage(Order order) {
      ActorRef voyageActor = Kar.Actors.ref(ReeferAppConfig.VoyageActorType, order.getVoyageId());
      return Kar.Actors.call(voyageActor, "reserve", order.getAsJsonObject()).asJsonObject();
   }


}