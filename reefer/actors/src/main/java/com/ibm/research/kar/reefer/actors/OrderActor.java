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
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Actor
public class OrderActor extends BaseActor {
    // wrapper containing order state
    private Order order = null;
    private static final Logger logger = Logger.getLogger(OrderActor.class.getName());

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
            e.printStackTrace();
        }


    }

    @Remote
    public JsonObject state() {

        try {
            if (order == null) {
                activate();
            }
            return order.getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

    }

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
        ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderManagerActorName, ReeferAppConfig.OrderManagerId);
        Kar.Actors.tell(orderActor, methodToCall, order.getAsJsonObject());
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
        if (order != null && !OrderStatus.DELIVERED.name().equals(order.getStatus()) && !OrderStatus.INTRANSIT.name().equals(order.getStatus())) {
            messageOrderManager("orderDeparted");
            saveOrderStatusChange(OrderStatus.INTRANSIT);
        }
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
    }

    /**
     * Called when a reefer anomaly is detected.
     * If order in transit, call provisioner to mark reefer spoilt
     * else call provisioner to request replacement reefer
     *
     * @param message - json encoded message
     */
    @Remote
    public void reeferAnomaly(JsonObject message) {
        int spoiltReeferId = message.getInt(Constants.REEFER_ID_KEY);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("OrderActor.reeferAnomaly() - :" + getId() + "received spoilt reefer ID " + spoiltReeferId);
        }
        // handle anomaly after ship arrival.
        if (order == null || order.getStatus() == null ||
                OrderStatus.DELIVERED.name().equals(order.getStatus())) {
            // Race condition
            if (logger.isLoggable(Level.INFO)) {
                logger.info("OrderActor.reeferAnomaly() - anomaly just arrived after order delivered");
            }
            // this actor should not be alive
            Kar.Actors.remove(this);
            return;
        }
        switch (OrderStatus.valueOf(order.getStatus())) {
            case INTRANSIT:
                // change state to Spoilt and inform provisioner
                tagAsSpoilt(spoiltReeferId);
                break;
            case BOOKED:
                // ship hasn't left the origin port, request replacement
                requestReplacementReefer(spoiltReeferId);
                break;
            default:
        }
    }

    /**
     * Calls provisioner to tag a given reefer as spoilt.
     *
     * @param spoiltReeferId
     */
    private void tagAsSpoilt(int spoiltReeferId) {

        if (!order.isSpoilt()) {
            order.setSpoilt(true);
            JsonObject jo = order.getAsJsonObject();
            ActorRef orderManagerActor = Kar.Actors.ref(ReeferAppConfig.OrderManagerActorName, ReeferAppConfig.OrderManagerId);
            Kar.Actors.call(orderManagerActor, "orderSpoilt", jo);
            Kar.Actors.State.set(this, Constants.ORDER_KEY, jo);
        }

        if (logger.isLoggable(Level.INFO)) {
            logger.info(String.format("OrderActor.tagAsSpoilt() - orderId: %s spoilt: %s", getId(),
                    order.isSpoilt()));
        }
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.REEFER_ID_KEY, spoiltReeferId).add(Constants.ORDER_KEY, order.getAsJsonObject());

        ActorRef voyageRef = Kar.Actors.ref(ReeferAppConfig.VoyageActorName, order.getVoyageId());
        Kar.Actors.tell(voyageRef, "reeferSpoilt", job.build());//.asJsonObject();
    }

    /**
     * Calls provisioner to request a replacement reefer. Reefer can be replaced if
     * an order is booked but not yet departed.
     *
     * @param spoiltReeferId
     */
    private void requestReplacementReefer(int spoiltReeferId) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("OrderActor.requestReplacementReefer() - orderId: %s requesting replacement for %s",
                    getId(), spoiltReeferId));
        }
        System.out.println(String.format("OrderActor.requestReplacementReefer() ------------------------------ orderId: %s requesting replacement for %s",
                getId(), spoiltReeferId));
        ActorRef scheduleManager = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorName, ReeferAppConfig.ScheduleManagerId);
        JsonValue currentDate = Kar.Actors.call(scheduleManager, "currentDate");

        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.DATE_KEY, currentDate).
                add(Constants.SPOILT_REEFER_KEY, spoiltReeferId);

        ActorRef depot = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, order.getDepot());
        JsonObject reply = Kar.Actors.call(depot, "reeferReplacement", job.build()).asJsonObject();
        if (reply.getString(Constants.STATUS_KEY).equals(Constants.FAILED)) {
            logger.warning("OrderActor.requestReplacementReefer() - orderId: " + getId()
                    + " request to replace reeferId " + spoiltReeferId + " failed");
            return;
        }

        int replacementReeferId = reply.getInt(Constants.REEFER_REPLACEMENT_ID_KEY);
        if (logger.isLoggable(Level.INFO)) {
            logger.info(String.format(
                    "OrderActor.requestReplacementReefer() - orderId: %s state: %s spoilt reefer id: %s replacement reefer id: %s",
                    getId(), order.getStatus(), spoiltReeferId, replacementReeferId));
        }

        JsonObjectBuilder voyageMessageBuilder = Json.createObjectBuilder();
        job.add(Constants.REEFER_ID_KEY, String.valueOf(replacementReeferId)).
                add(Constants.SPOILT_REEFER_KEY, String.valueOf(spoiltReeferId)).
                add(Constants.ORDER_ID_KEY, order.getId());
        ActorRef voyageActorRef = Kar.Actors.ref(ReeferAppConfig.VoyageActorName, order.getVoyageId());
        Kar.Actors.call(voyageActorRef, "replaceReefer", voyageMessageBuilder.build());
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
        ActorRef voyageActor = Kar.Actors.ref(ReeferAppConfig.VoyageActorName, order.getVoyageId());
        return Kar.Actors.call(voyageActor, "reserve", order.getAsJsonObject()).asJsonObject();
    }


}