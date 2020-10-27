package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;

@Actor
public class OrderActor extends BaseActor {
    // There are three properties we need to persist for each order:
    //     1. state: PENDING | BOOKED | INTRANSIT | SPOILT
    //     2. voyageId : voyage id the order is assigned to
    //     3. reefer map: map containing reefer ids assigned to this order
    private Order orderState = null;
    private static final Logger logger = Logger.getLogger(OrderActor.class.getName());

    @Activate
    public void init() {
        JsonValue jv = super.get(this, Constants.ORDER_KEY);
        // instanceof is null safe
        if (jv instanceof JsonObject) {
            orderState = new Order(jv.asJsonObject());
            Map<String, JsonValue> reeferMap = super.getSubMap(this, Constants.REEFER_MAP_KEY);
            orderState.addReefers(reeferMap);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("OrderActor.init() - orderId: %s state: %s voyageId: %s reefers: %d",
                        getId(), orderState.getState(), orderState.getVoyageId(), orderState.getReeferMap().size()));

            }
         }
    }

    /**
     * Called when an order is delivered (ie.ship arrived at the destination port).
     * Calls ReeferProvisioner to release all reefers in this order.
     *
     * @param message - json encoded params: voyageId
     * @return
     */
    @Remote
    public JsonObject delivered(JsonObject message) {
        try {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(String.format("OrderActor.delivered() -  orderId: %s voyageId: %s reefers: %d ",
                        getId(), orderState.getVoyageId(), orderState.getReeferMap().size()));
            }

            // pass reefer ids to the ProvisionerActor
            JsonArrayBuilder reefersToRelease = Json.createArrayBuilder(orderState.getReeferMap().keySet());
            // message the ReeferProvisionerActor to release reefers in a given list
            actorCall(actorRef(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "unreserveReefers",
                    Json.createObjectBuilder().add(Constants.REEFERS_KEY, reefersToRelease).build());
            // as soon as the order is delivered and reefers are released we clear actor
            // state
            Kar.actorDeleteAllState(this);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED")
                    .add("ERROR", "OrderActor - Failure while handling order delivery")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
    }

    /**
     * Called when ship departs from an origin port. Message ReeferProvisioner number
     * of reefers in this order so that it can update its counts
     *
     * @param message - json encoded message
     * @return
     */
    @Remote
    public JsonObject departed(JsonObject message) {
        try {
            saveOrderStatus(OrderStatus.INTRANSIT);
            // Notify ReeferProvisioner that the order is in-transit
            if (!orderState.getReeferMap().isEmpty()) {
                ActorRef reeferProvisionerActor = Kar.actorRef(ReeferAppConfig.ReeferProvisionerActorName,
                        ReeferAppConfig.ReeferProvisionerId);
                JsonObject params = Json.createObjectBuilder().add("in-transit", orderState.getReeferMap().size()).build();
                actorCall(reeferProvisionerActor, "updateInTransit", params);
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("OrderActor.departed() - orderId: %s voyageId: %s in-transit reefers: %d",
                            getId(), orderState.getVoyageId(), orderState.getReeferMap().size()));
                }
            }
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
    }

    /**
     * Called when a reefer anomaly is detected.
     *
     * @param message - json encoded message
     * @return The state of the order
     */
    @Remote
    public JsonObject anomaly(JsonObject message) {
        try {
            // ReeferProvisioner notifies the order on anomaly. The order returns its current state
            // and the decision is made to either spoil the reefer or assign it to maintenance.
            String state = "";
            if (orderState == null || orderState.getState() == null) {
                // possible race condition. Order just arrived and state cleared, the sim says reefer in the order is bad
                state = OrderStatus.DELIVERED.name();
            } else {
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("OrderActor.anomaly() - orderId: %s state: %s", getId(), orderState.getState()));
                }
                // if this order is in transit, change state to Spoilt
                if (OrderStatus.INTRANSIT.equals(OrderStatus.valueOf(orderState.getStateAsString()))) {
                    saveOrderStatus(OrderStatus.SPOILT);
                }
                state = orderState.getStateAsString();
            }

            return Json.createObjectBuilder().add(Constants.ORDER_STATUS_KEY, state)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }
    }


    /**
     * Replace spoilt reefer with a good one. This is a use case where an order has
     * not yet departed but one of of its reefers goes bad.
     *
     * @param message - json encoded message
     * @return The state of the order
     */
    @Remote
    public JsonObject replaceReefer(JsonObject message) {
        try {
            int spoiltReeferId = message.getJsonNumber(Constants.REEFER_ID_KEY).intValue();
            int replacementReeferId = message.getJsonNumber(Constants.REEFER_REPLACEMENT_ID_KEY).intValue();
            if (logger.isLoggable(Level.INFO)) {
                logger.info(String.format("OrderActor.replaceReefer() - orderId: %s state: %s spoilt reefer id: %s replacement reefer id: %s",
                        getId(), orderState.getState(), spoiltReeferId, replacementReeferId));
            }

            // reefer replace is a two step process (remove + add)
            super.removeFromSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(spoiltReeferId));
            super.addToSubMap(this, Constants.REEFER_MAP_KEY, String.valueOf(replacementReeferId),
                    Json.createValue(replacementReeferId));
            // replace reefer map with the current one which just changed. Maps from Kar storage
            // are immutable
            orderState.addReefers(super.getSubMap(this, Constants.REEFER_MAP_KEY));
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }

    }

    /**
     * Return number of reefers in this order
     *
     * @param message
     * @return
     */
    @Remote
    public JsonObject reeferCount(JsonObject message) {
        return Json.createObjectBuilder().add(Constants.TOTAL_REEFER_COUNT_KEY, orderState.getReeferMap().size()).build();
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info(String.format("OrderActor.createOrder() - orderId: %s message: %s", getId(), message));
        }
        try {
            // Java wrapper around Json payload
            JsonOrder jsonOrder = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));

            orderState = new Order(Json.createObjectBuilder().
                    add(Constants.ORDER_STATUS_KEY, OrderStatus.PENDING.name()).
                    add(Constants.VOYAGE_ID_KEY, Json.createValue(jsonOrder.getVoyageId())).build());
            saveOrderStatus(OrderStatus.PENDING);
            // Call Voyage actor to book the voyage for this order. This call also
            // reserves reefers
            JsonObject voyageBookingResult = bookVoyage(jsonOrder);
            if (logger.isLoggable(Level.INFO)) {
                logger.info(String.format("OrderActor.createOrder() - orderId: %s VoyageActor reply: %s", getId(), voyageBookingResult));
            }
            if (voyageBooked(voyageBookingResult)) {
                saveOrderReefers(voyageBookingResult);
                saveOrderStatus(OrderStatus.BOOKED);
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("OrderActor.createOrder() - orderId: %s saved - voyage: %s state: %s reefers: %d",
                            getId(), orderState.getVoyageId(), orderState.getStateAsString(), orderState.getReeferMap().size()));
                }
                return Json.createObjectBuilder().add(JsonOrder.OrderBookingKey, voyageBookingResult).build();
            } else {
                return voyageBookingResult;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();

        }

    }

    private void saveOrderStatus(OrderStatus state) {
        JsonValue jv = Json.createValue(state.name());
        orderState.newState(jv);
        super.set(this, Constants.ORDER_KEY, orderState.toJsonObject());
    }

    /**
     * Returns status of voyage booking.
     *
     * @param orderBookingStatus - reply message from Voyage actor
     * @return - true if voyage was booked, false otherwise
     */
    private boolean voyageBooked(JsonObject orderBookingStatus) {
        return orderBookingStatus.getString(Constants.STATUS_KEY).equals(Constants.OK);
    }

    /**
     * Called to persist reefer ids associated with this order
     *
     * @param orderBookingStatus Contains reefer ids
     * @throws Exception
     */
    private void saveOrderReefers(JsonObject orderBookingStatus) throws Exception {
        JsonArray reefers = orderBookingStatus.getJsonArray(Constants.REEFERS_KEY);
        if (reefers != null) {
            // copy assigned reefer id's to a map and save it in kar storage
            Map<String, JsonValue> reeferMap = new HashMap<>();
            reefers.forEach(reeferId -> {
                reeferMap.put(String.valueOf(((JsonNumber) reeferId).intValue()), reeferId);
            });
            addSubMap(this, Constants.REEFER_MAP_KEY, reeferMap);
            orderState.addReefers(reeferMap);
        }
    }

    /**
     * Called to book voyage for this order by messaging Voyage actor.
     *
     * @param voyageId The voyage id
     * @param order    Json encoded order properties
     * @return The voyage booking result
     */
    private JsonObject bookVoyage(JsonOrder order) {
        JsonObject params = Json.createObjectBuilder().add(JsonOrder.OrderKey, order.getAsObject()).build();
        ActorRef voyageActor = actorRef(ReeferAppConfig.VoyageActorName, order.getVoyageId());
        JsonValue reply = actorCall(voyageActor, "reserve", params);
        return reply.asJsonObject();
    }

    /**
     * Convenience class to hold order actor state
     */
    private class Order {
        JsonValue state = null;
        JsonValue voyageId = null;
        Map<String, JsonValue> reeferMap = null;

        public Order(JsonObject jo) {
            this.state = jo.getJsonString(Constants.ORDER_STATUS_KEY);
            this.voyageId = jo.getJsonString(Constants.VOYAGE_ID_KEY);
        }

        public JsonValue getState() {
            return state;
        }

        public String getStateAsString() {
            return ((JsonString) orderState.getState()).getString();
        }

        public JsonValue getVoyageId() {
            return voyageId;
        }

        public Map<String, JsonValue> getReeferMap() {
            return reeferMap;
        }

        public void addReefers(Map<String, JsonValue> reeferMap) {
            this.reeferMap = reeferMap;
        }

        public void newState(JsonValue state) {
            this.state = state;
        }

        public JsonObject toJsonObject() {
            return Json.createObjectBuilder().add(Constants.ORDER_STATUS_KEY, getState()).add(Constants.VOYAGE_ID_KEY, getVoyageId()).build();
        }
    }
}