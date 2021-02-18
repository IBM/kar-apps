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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodTimeoutException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.VoyageStatus;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Actor
public class VoyageActor extends BaseActor {
    private JsonObject voyageInfo;
    private Voyage voyage = null;
    private JsonValue voyageStatus;
    private Map<String, String> orders = new HashMap<>();
    private static final Logger logger = Logger.getLogger(VoyageActor.class.getName());

    /**
     * Fetch actor's state from Kar persistent storage. On the first invocation call REST
     * to get Voyage info which includes details like daysAtSea, departure
     * date, arrival date, etc. Store it in Kar persistent storage for reuse on subsequent invocations.
     */
    @Activate
    public void init() {
        // fetch actor state from Kar storage
        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("VoyageActor.init() actorID:" + this.getId() + " all state" + state);
        }

        try {
            // initial actor invocation should handle no state
            if (state.isEmpty()) {
                // call REST just once to get static voyage information like departure and arrival dates, etc
                Response response = Kar.Services.get("reeferservice", "/voyage/info/" + getId());
                voyageInfo = response.readEntity(JsonValue.class).asJsonObject();
                // store voyage information in Kar storage for reuse
                Kar.Actors.State.set(this, Constants.VOYAGE_INFO_KEY, voyageInfo);
            } else {

                if (state.containsKey(Constants.VOYAGE_INFO_KEY)) {
                    voyageInfo = state.get(Constants.VOYAGE_INFO_KEY).asJsonObject();
                }
                if (state.containsKey(Constants.VOYAGE_STATUS_KEY)) {
                    voyageStatus = state.get(Constants.VOYAGE_STATUS_KEY);
                }
                if (state.containsKey(Constants.VOYAGE_ORDERS_KEY)) {
                    JsonValue jv = state.get(Constants.VOYAGE_ORDERS_KEY);
                    // since we already have all orders by calling actorGetAllState() above we can
                    // deserialize them using Jackson's ObjectMapper. Alternatively, one can
                    // use Kar.actorSubMapGet() which is an extra call.
                    ObjectMapper mapper = new ObjectMapper();
                    // deserialize json orders into a HashMap
                    orders = mapper.readValue(jv.toString(), HashMap.class);
                }

            }
            voyage = VoyageJsonSerializer.deserialize(voyageInfo);
        } catch (Exception e) {
            logger.log(Level.WARNING, "VoyageActor.init() - Error - voyageId " + getId() + " ", e);
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
        // If voyage is null it means that this actor was not able to fetch voyage info from the Rest
        // service when this actor was activated (see init() above). Most likely the service is down.
        if (Objects.isNull(voyage)) {
            logger.log(Level.WARNING, "VoyageActor.changePosition() - Error - voyageId " + getId() + " metadata is not defined - looks like the REST service is down");
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Rest Service Unavailable - voyage metadata unknown")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
        try {
            // the simulator advances ship position
            int daysOutAtSea = message.getInt(Constants.VOYAGE_DAYSATSEA_KEY);
            voyage.getRoute().getVessel().setPosition(daysOutAtSea);
            int progress = Math.round((daysOutAtSea / (float) voyage.getRoute().getDaysAtSea()) * 100);
            voyage.getRoute().getVessel().setProgress(progress);
            JsonObject jo = VoyageJsonSerializer.serialize(voyage);
            Kar.Actors.State.set(this, Constants.VOYAGE_INFO_KEY, jo);
            // given ship sail date and current days at sea get ship's current date
            Instant shipCurrentDate = TimeUtils.getInstance().futureDate(voyage.getSailDateObject(), daysOutAtSea);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "VoyageActor.changePosition() voyage info:" + voyageInfo + " ship current date:" + shipCurrentDate);
            }
            String restMethodToCall = "";
            // if ship's current date matches arrival date, the ship arrived
            if (shipArrived(shipCurrentDate, voyage)) {
                // Arriving voyage must be in DEPARTED state
                if (!VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
                    logger.log(Level.WARNING, "VoyageActor.changePosition() - voyage:" + voyage.getId() + " arrived BUT its expected state is not DEPARTED. Instead it is " + getVoyageStatus());
                }
                voyageStatus = Json.createValue(VoyageStatus.ARRIVED.name());
                // notify voyage orders of arrival
                processArrivedVoyage(voyage, daysOutAtSea);
                // voyage arrived, no longer need the state
                Kar.Actors.remove(this);
            } // check if ship departed its origin port
            else if ((daysOutAtSea == 1) && !VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
                voyageStatus = Json.createValue(VoyageStatus.DEPARTED.name());
                Kar.Actors.State.set(this, Constants.VOYAGE_STATUS_KEY, voyageStatus);
                // notify voyage orders of departure
                processDepartedVoyage(voyage, daysOutAtSea);
            } else {  // voyage in transit
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("VoyageActor.changePosition() Updating REST - daysAtSea:" + daysOutAtSea);
                }
                // update REST voyage days at sea
                if (!messageRest("/voyage/update/position", daysOutAtSea)) {
                    logger.log(Level.WARNING, "VoyageActor.changePosition() - Error - voyageId " + getId() + " - unable to message REST service - is the service down?");
                    return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", " REST Service down")
                            .add(Constants.VOYAGE_ID_KEY, String.valueOf(this.getId())).build();
                }

            }
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "VoyageActor.changePosition() - Error - voyageId " + getId() + " ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.VOYAGE_ID_KEY, String.valueOf(this.getId())).build();
        }
    }

    /**
     * Called to book a voyage for a given order. Calls ReeferProvisioner to book reefers and
     * stores orderId in the Kar persistent storage.
     *
     * @param message Json encoded order properties
     * @return - result of reefer booking
     */
    @Remote
    public JsonObject reserve(JsonObject message) {
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("VoyageActor.reserve() called Id:" + getId() + " " + message.toString() + " OrderID:"
                    + order.getId() + " Orders size=" + orders.size());
        }
        try {
            // Book reefers for this order through the ReeferProvisioner
            JsonValue bookingStatus = Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "bookReefers", message);
            // Check if ReeferProvisioner booked the reefers for this order.
            if (bookingStatus.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
                // add new order to this voyage order list
                Kar.Actors.State.Submap.set(this, Constants.VOYAGE_ORDERS_KEY, String.valueOf(order.getId()),
                        Json.createValue(order.getId()));
                orders.put(String.valueOf(order.getId()), String.valueOf((order.getId())));
                voyage.setOrderCount(orders.size());
                JsonObject jo = VoyageJsonSerializer.serialize(voyage);  //JsonUtils.voyageToJson(voyage);
                Kar.Actors.State.set(this, Constants.VOYAGE_INFO_KEY, jo);
                // reload order map since there is a change. Local orders map is not mutable
                voyageStatus = Json.createValue(VoyageStatus.PENDING.name());
                Kar.Actors.State.set(this, Constants.VOYAGE_STATUS_KEY, voyageStatus);
                return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                        .add(Constants.REEFERS_KEY, bookingStatus.asJsonObject().getJsonArray(Constants.REEFERS_KEY))
                        .add(JsonOrder.OrderKey, order.getAsObject()).build();
            } else {
                return bookingStatus.asJsonObject();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "VoyageActor.reserve() - Error - voyageId " + getId() + " ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
    }

    /**
     * Calls REST and Order actors when a ship arrives at the destination port
     *
     * @param voyage    - Voyage info
     * @param daysAtSea - ship days at sea
     */
    private void processArrivedVoyage(Voyage voyage, int daysAtSea) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("VoyageActor.changePosition() voyageId=" + voyage.getId()
                    + " has ARRIVED ------------------------------------------------------");
        }
        // notify each order actor that the ship arrived
        orders.values().forEach(orderId -> {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("VoyageActor.changePosition() voyageId=" + voyage.getId()
                        + " Notifying Order Actor of arrival - OrderID:" + orderId);
            }
            try {
                messageOrderActor("delivered", orderId);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "VoyageActor.processArrivedVoyage() - Error - voyageId " + getId() + " ", e);
            }
        });
        messageRest("/voyage/update/arrived", daysAtSea);
        Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                "releaseVoyageReefers",
                Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).build());
    }

    /**
     * Calls REST and Order actors when a ship departs from the origin port
     *
     * @param voyage    - Voyage info
     * @param daysAtSea - ship days at sea
     */
    private void processDepartedVoyage(Voyage voyage, int daysAtSea) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("VoyageActor.processDepartedVoyage() voyageId=" + voyage.getId()
                    + " has DEPARTED ------------------------------------------------------");
        }
        orders.values().forEach(orderId -> {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("VoyageActor.processDepartedVoyage() voyageId=" + voyage.getId()
                        + " Notifying Order Actor of departure - OrderID:" + orderId);
            }
            messageOrderActor("departed", orderId);
        });
        messageRest("/voyage/update/departed", daysAtSea);

        ActorRef reeferProvisionerActor = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName,
                ReeferAppConfig.ReeferProvisionerId);
        JsonObject params = Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).build();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "VoyageActor.processDepartedVoyage() - calling ReeferProvisionerActor voyageReefersDeparted - voyageId:" + getId());
        }
        Kar.Actors.call(reeferProvisionerActor, "voyageReefersDeparted", params);
    }

    /**
     * Update REST with ship position
     *
     * @param methodToCall -  REST API to call
     * @param daysAtSea    - ship days at sea
     */
    private boolean messageRest(String methodToCall, int daysAtSea) {
        JsonObject params = Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).add("daysAtSea", daysAtSea)
                .build();
            try {
                Kar.Services.post(Constants.REEFERSERVICE, methodToCall, params);
                return true;
            } catch (Exception e) {
                logger.log(Level.WARNING, "", e);
                return false;
            }
    }

    /**
     * Call OrderActor when a ship carrying the order either departs or arrives
     *
     * @param orderId      - order id
     * @param methodToCall - actor method to call
     */
    private void messageOrderActor(String methodToCall, String orderId) {
        ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderActorName, orderId);
        try {
            Kar.Actors.call(orderActor, methodToCall);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
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

    /**
     * Determines if ship arrived at the destination port or not. Ship arrives when
     * current date = scheduled shipArrivalDate
     *
     * @param shipCurrentDate - current date
     * @param voyage          - voyage info
     * @return - true if ship arrived, false otherwise
     */
    private boolean shipArrived(Instant shipCurrentDate, Voyage voyage) {
        Instant scheduledArrivalDate = Instant.parse(voyage.getArrivalDate());
        return ((shipCurrentDate.equals(scheduledArrivalDate)
                || shipCurrentDate.isAfter(scheduledArrivalDate) && !VoyageStatus.ARRIVED.equals(getVoyageStatus())));
    }

}