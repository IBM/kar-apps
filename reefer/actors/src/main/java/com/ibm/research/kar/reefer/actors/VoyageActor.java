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
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.VoyageStatus;

import javax.json.*;
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
    private Map<String, JsonValue> orders = new HashMap<>();
    private static final Logger logger = Logger.getLogger(VoyageActor.class.getName());

    /**
     * Fetch actor's state from Kar persistent storage. On the first invocation call REST
     * to get Voyage info which includes details like daysAtSea, departure
     * date, arrival date, etc. Store it in Kar persistent storage for reuse on subsequent invocations.
     */
    @Activate
    public void activate() {
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
        if (Objects.isNull(voyage)) {
            logger.log(Level.WARNING, "VoyageActor.changePosition() - Error - voyageId " + getId() + " metadata is not defined - looks like the REST service is down");
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Rest Service Unavailable - voyage metadata unknown")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
        try {
            // the simulator advances ship position
            int daysOutAtSea = message.getInt(Constants.VOYAGE_DAYSATSEA_KEY);
            // process only if the position has changed
            if ( voyage.positionChanged(daysOutAtSea) ) {
                // given ship sail date and current days at sea get ship's current date
                Instant shipCurrentDate = TimeUtils.getInstance().futureDate(voyage.getSailDateObject(), daysOutAtSea);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(
                            "VoyageActor.changePosition() voyage info:" + voyageInfo + " ship current date:" + shipCurrentDate);
                }
                // if ship's current date matches arrival date, the ship arrived
                if (voyage.shipArrived(shipCurrentDate, getVoyageStatus())) {
                    // Arriving voyage must be in DEPARTED state
                    if (!VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
                        logger.log(Level.WARNING, "VoyageActor.changePosition() - voyage:" + voyage.getId() + " arrived BUT its expected state is not DEPARTED. Instead it is " + getVoyageStatus());
                    }
                    voyageStatus = Json.createValue(VoyageStatus.ARRIVED.name());
                    // notify voyage orders of arrival
                    processArrivedVoyage(voyage, daysOutAtSea);
                    // voyage arrived, no longer need the state
                    Kar.Actors.remove(this);
                } else {
                    // check if ship departed its origin port
                    if ((daysOutAtSea == 1) && !VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
                        voyageStatus = Json.createValue(VoyageStatus.DEPARTED.name());
                        Kar.Actors.State.set(this, Constants.VOYAGE_STATUS_KEY, voyageStatus);
                        // notify voyage orders of departure
                        processDepartedVoyage(voyage, daysOutAtSea);
                    } else {  // voyage in transit
                        // update REST voyage days at sea
                        messageRest("/voyage/update/position", daysOutAtSea);
                    }
                    voyage.changePosition(daysOutAtSea);
                    Kar.Actors.State.set(this, Constants.VOYAGE_INFO_KEY, VoyageJsonSerializer.serialize(voyage));
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
        JsonValue bookingStatus = null;
        if (orders.containsKey(order.getId())) {
            // this order has already been processed so return result
            if (bookingStatus.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
                return buildResponse(bookingStatus, order);
            }
            return orders.get(order.getId()).asJsonObject();
        }

        try {
            // Book reefers for this order through the ReeferProvisioner
            bookingStatus = Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "bookReefers", message);

            // Check if ReeferProvisioner booked reefers for this order.
            if (bookingStatus.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK)) {
                // add new order to this voyage order list
                Kar.Actors.State.Submap.set(this, Constants.VOYAGE_ORDERS_KEY, String.valueOf(order.getId()),
                        Json.createValue(order.getId()));
                voyage.setOrderCount(orders.size());
                JsonArray reefers = bookingStatus.asJsonObject().getJsonArray(Constants.REEFERS_KEY);
                if (reefers != null && reefers.size() > 0) {
                    voyage.setReeferCount(voyage.getReeferCount()+reefers.size());
                }
                voyageStatus = Json.createValue(VoyageStatus.PENDING.name());
                JsonObjectBuilder jb = Json.createObjectBuilder();
                jb.add(Constants.VOYAGE_STATUS_KEY, voyageStatus).
                        add(Constants.VOYAGE_INFO_KEY, VoyageJsonSerializer.serialize(voyage));
                Kar.Actors.State.set(this, jb.build());
                orders.put(String.valueOf(order.getId()), bookingStatus);
                return buildResponse(bookingStatus, order);
            }
            // return failure
            return bookingStatus.asJsonObject();
        } catch (Exception e) {
            logger.log(Level.WARNING, "VoyageActor.reserve() - Error - voyageId " + getId() + " ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
    }
    private JsonObject buildResponse(final JsonValue bookingStatus, final JsonOrder order) {
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                .add(Constants.REEFERS_KEY, bookingStatus.asJsonObject().getJsonArray(Constants.REEFERS_KEY))
                .add(JsonOrder.OrderKey, order.getAsObject()).build();
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
        orders.keySet().forEach(orderId -> {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("VoyageActor.changePosition() voyageId=" + voyage.getId()
                        + " Notifying Order Actor of arrival - OrderID:" + orderId);
            }
            messageOrderActor("delivered", orderId);
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
        orders.keySet().forEach(orderId -> {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("VoyageActor.processDepartedVoyage() voyageId=" + voyage.getId()
                        + " Notifying Order Actor of departure - OrderID:" + orderId);
            }
            messageOrderActor("departed", orderId);
        });
        messageRest("/voyage/update/departed", daysAtSea);

        ActorRef reeferProvisionerActor = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName,
                ReeferAppConfig.ReeferProvisionerId);
        JsonObject params = Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).
                add(Constants.VOYAGE_REEFERS_KEY, Json.createValue(voyage.getReeferCount())).
                build();
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
    private void messageRest(String methodToCall, int daysAtSea) {
        JsonObject params = Json.createObjectBuilder().
                add(Constants.VOYAGE_ID_KEY, getId()).add("daysAtSea", daysAtSea).
                build();
        Kar.Services.post(Constants.REEFERSERVICE, methodToCall, params);
    }

    /**
     * Call OrderActor when a ship carrying the order either departs or arrives
     *
     * @param orderId      - order id
     * @param methodToCall - actor method to call
     */
    private void messageOrderActor(String methodToCall, String orderId) {
        ActorRef orderActor = Kar.Actors.ref(ReeferAppConfig.OrderActorName, orderId);
        Kar.Actors.call(orderActor, methodToCall);
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
}