package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.*;
import javax.json.bind.JsonbBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
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

    // wrapper containing order state
    private Order orderState = null;
    private static final Logger logger = Logger.getLogger(OrderActor.class.getName());

    @Activate
    public void activate() {
        Map<String, JsonValue> state = Kar.actorGetAllState(this);
        try {
            // initial actor invocation should handle no state
            if (!state.isEmpty()) {
                orderState = new Order(state);
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("OrderActor.activate() - orderId: %s state: %s voyageId: %s reefers: %d",
                            getId(), orderState.getState(), orderState.getVoyageId(), orderState.getReeferMap().size()));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "OrderActor.activate() - Error - orderId "+getId()+" ", e);
        }
    }
    /**
     * Save actor's state when the instance is passivated. Currently just saves the
     * actor's status and voyageId.
     */
    @Deactivate
    public void deactivate() {
        try {
            // don't save state if the order has been delivered
            if (orderState != null && !OrderStatus.DELIVERED.name().equals(orderState.getStateAsString()) ){
                JsonObjectBuilder job = Json.createObjectBuilder();
                job.add(Constants.ORDER_STATUS_KEY, orderState.getState()).
                        add(Constants.VOYAGE_ID_KEY, orderState.getVoyageId());
                Kar.actorSetMultipleState(this, job.build());
            }
        } catch( Exception e) {
            logger.log(Level.WARNING, "OrderActor.deactivate() - Error - orderId "+getId()+" ", e);
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                    "OrderActor.delivered() - entry");
        }
        try {

            if (logger.isLoggable(Level.INFO)) {
                logger.info(String.format("OrderActor.delivered() -  orderId: %s voyageId: %s reefers: %d ",
                        getId(), orderState.getVoyageId(), orderState.getReeferMap() == null ? 0 : orderState.getReeferMap().size()));
            }
            if (orderState.getReeferMap() != null) {
                // pass reefer ids to the ProvisionerActor
                JsonArrayBuilder reefersToRelease = Json.createArrayBuilder(orderState.getReeferMap().keySet());
                // message the ReeferProvisionerActor to release reefers in a given list
                actorCall(actorRef(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                        "unreserveReefers",
                        Json.createObjectBuilder().add(Constants.REEFERS_KEY, reefersToRelease).build());
            }
            changeOrderStatus(OrderStatus.DELIVERED);
            // as soon as the order is delivered and reefers are released we clear actor
            // state
            Kar.actorDeleteAllState(this);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "OrderActor.delivered() - Error - orderId "+getId()+" ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED")
                    .add("ERROR", "OrderActor - Failure while handling order delivery")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(
                        "OrderActor.delivered() - exit");
            }
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                    "OrderActor.departed() - entry");
        }
        try {
            changeOrderStatus(OrderStatus.INTRANSIT);
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
            logger.log(Level.WARNING, "OrderActor.departed() - Error - orderId "+getId()+" ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(
                        "OrderActor.departed() - exit");
            }
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                    "OrderActor.anomaly() - entry");
        }
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
                    changeOrderStatus(OrderStatus.SPOILT);
                }
                state = orderState.getStateAsString();
            }

            return Json.createObjectBuilder().add(Constants.ORDER_STATUS_KEY, state)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "OrderActor.anomaly() - Error - orderId "+getId()+" ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(
                        "OrderActor.anomaly() - exit");
            }
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                    "OrderActor.replaceReefer() - entry");
        }
        try {
            int spoiltReeferId = message.getJsonNumber(Constants.REEFER_ID_KEY).intValue();
            int replacementReeferId = message.getJsonNumber(Constants.REEFER_REPLACEMENT_ID_KEY).intValue();
            if (logger.isLoggable(Level.INFO)) {
                logger.info(String.format("OrderActor.replaceReefer() - orderId: %s state: %s spoilt reefer id: %s replacement reefer id: %s",
                        getId(), orderState.getState(), spoiltReeferId, replacementReeferId));
            }

            // reefer replace is a two step process (remove + add)
            Kar.actorDeleteState(this, Constants.REEFER_MAP_KEY, String.valueOf(spoiltReeferId));
            Kar.actorSetState(this, Constants.REEFER_MAP_KEY, String.valueOf(replacementReeferId),
                    Json.createValue(replacementReeferId));
            orderState.replaceReefer(spoiltReeferId, replacementReeferId);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "OrderActor.replaceReefer() - Error - orderId "+getId()+" ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(
                        "OrderActor.replaceReefer() - exit");
            }
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                    "OrderActor.reeferCount() - entry");
        }
        try {
            return Json.createObjectBuilder().add(Constants.TOTAL_REEFER_COUNT_KEY, orderState.getReeferMap().size()).build();
        } catch( Exception e) {
            return Json.createObjectBuilder().add(Constants.TOTAL_REEFER_COUNT_KEY, -1).build();
        } finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(
                        "OrderActor.reeferCount() - exit");
            }
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                    "OrderActor.createOrder() - entry");
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.info(String.format("OrderActor.createOrder() - orderId: %s message: %s", getId(), message));
        }
        try {
            // Java wrapper around Json payload
            JsonOrder jsonOrder = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));

            orderState = new Order(Json.createObjectBuilder().
                    add(Constants.ORDER_STATUS_KEY, OrderStatus.PENDING.name()).
                    add(Constants.VOYAGE_ID_KEY, Json.createValue(jsonOrder.getVoyageId())).build());
            changeOrderStatus(OrderStatus.PENDING);
            // Call Voyage actor to book the voyage for this order. This call also
            // reserves reefers
            JsonObject voyageBookingResult = bookVoyage(jsonOrder);
            if (logger.isLoggable(Level.INFO)) {
                logger.info(String.format("OrderActor.createOrder() - orderId: %s VoyageActor reply: %s", getId(), voyageBookingResult));
            }
            // Check if voyage has been booked
            if (voyageBookingResult.getString(Constants.STATUS_KEY).equals(Constants.OK)) {
                saveOrderReefers(voyageBookingResult);
                changeOrderStatus(OrderStatus.BOOKED);
                if (logger.isLoggable(Level.INFO)) {
                    logger.info(String.format("OrderActor.createOrder() - orderId: %s saved - voyage: %s state: %s reefers: %d",
                            getId(), orderState.getVoyageId(), orderState.getStateAsString(), orderState.getReeferMap().size()));
                }
                return Json.createObjectBuilder().add(JsonOrder.OrderBookingKey, voyageBookingResult).build();
            } else {
                return voyageBookingResult;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "OrderActor.createOrder() - Error - orderId "+getId()+" ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();

        } finally {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(
                        "OrderActor.createOrder() - exit");
            }
        }

    }

    private void changeOrderStatus(OrderStatus state) {
        JsonValue jv = Json.createValue(state.name());
        orderState.newState(jv);
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
                orderState.addReefer(((JsonNumber) reeferId).intValue());
            });
            Kar.actorSetMultipleState(this, Constants.REEFER_MAP_KEY, reeferMap);
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
        Map<String, String> reeferMap = null;

        public Order(Map<String, JsonValue> allState) {
            try {
                this.state = allState.get(Constants.ORDER_STATUS_KEY);
                this.voyageId = allState.get(Constants.VOYAGE_ID_KEY);
                if (allState.containsKey(Constants.REEFER_MAP_KEY)) {
                    JsonValue jv = allState.get(Constants.REEFER_MAP_KEY);
                    // since we already have all reefers by calling actorGetAllState() above we can
                    // deserialize them using Jackson's ObjectMapper. Alternatively, one can
                    // use Kar.actorSubMapGet() which is an extra call.
                    ObjectMapper mapper = new ObjectMapper();
                    // deserialize json reefers into a HashMap
                    Map<String, String> reeferMap = mapper.readValue(jv.toString(), HashMap.class);
                     this.addReefers(reeferMap);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "", e);
            }

        }
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

        public Map<String, String> getReeferMap() {
            return reeferMap;
        }

        public void addReefers(Map<String, String> reeferMap) {
            this.reeferMap = reeferMap;
        }
        public void addReefer(int reeferId) {
            if (this.reeferMap == null) {
                this.reeferMap = new HashMap<>();
            }
            this.reeferMap.put(String.valueOf(reeferId), String.valueOf(reeferId));
        }
        public void replaceReefer(int reeferId, int replacementReeferId) {
            this.reeferMap.remove(String.valueOf(reeferId));
            this.addReefer(replacementReeferId);
        }
        public void newState(JsonValue state) {
            this.state = state;
        }

        public JsonObject toJsonObject() {
            return Json.createObjectBuilder().add(Constants.ORDER_STATUS_KEY, getState()).add(Constants.VOYAGE_ID_KEY, getVoyageId()).build();
        }
    }
}