package com.ibm.research.kar.reefer.actors;

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
    //     1. state: PENDING | BOOKED | INTRANSIT | SPOILT | DELIVERED
    //     2. voyageId : voyage id the order is assigned to
    //     3. reefer map: map containing reefer ids assigned to this order

    // wrapper containing order state
    private Order orderState = null;
    private static final Logger logger = Logger.getLogger(OrderActor.class.getName());

    @Activate
    public void activate() {
        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        try {
            // initial actor invocation should handle no state
            if (!state.isEmpty()) {
                orderState = new Order(state);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(String.format("OrderActor.activate() - orderId: %s state: %s voyageId: %s reefers: %d",
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
                Kar.Actors.State.set(this, job.build());
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
    public JsonObject delivered() {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "OrderActor.delivered() - entry - id:"+getId());
        }
        try {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("OrderActor.delivered() -  orderId: %s voyageId: %s reefers: %d ",
                        getId(), orderState.getVoyageId(), orderState.getReeferMap() == null ? 0 : orderState.getReeferMap().size()));
            }
            // as soon as the order is delivered and reefers are released we clear actor
            // state
            Kar.Actors.remove(this);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "OrderActor.delivered() - Error - orderId "+getId()+" ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED")
                    .add("ERROR", "OrderActor - Failure while handling order delivery")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "OrderActor.delivered() - exit id:"+getId());
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
    public JsonObject departed() {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "OrderActor.departed() - entry id:"+getId());
        }
        try {
            changeOrderStatus(OrderStatus.INTRANSIT);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "OrderActor.departed() - Error - orderId "+getId()+" ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", e.getMessage())
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "OrderActor.departed() - exit id:"+getId());
            }
        }
    }

    /**
     * Called when a reefer anomaly is detected.
     * If order in transit, call provisioner to mark reefer spoilt
     * else call provisioner to request replacement reefer
     *
     * @param message - json encoded message
     */
    @Remote
    public void anomaly(JsonObject message) {
      int spoiltReeferId = message.getInt(Constants.REEFER_ID_KEY);
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("OrderActor.anomaly() - entry id:" + getId() + "received spoilt reefer ID " + spoiltReeferId);
      }

      try {
        if (orderState == null || orderState.getState() == null ||
                OrderStatus.DELIVERED.name().equals(orderState.getStateAsString())) {
          // Race condition
          logger.warning("OrderActor.anomaly() - anomaly just arrived after order delivered");
          // this actor should not be alive
            Kar.Actors.remove(this);

        } else {
          // if this order is in transit, change state to Spoilt and inform provisioner
          if (OrderStatus.INTRANSIT.equals(OrderStatus.valueOf(orderState.getStateAsString()))) {
            markSpoilt(message);
          } else {
            // Order is booked. Request replacement
            requestReplacementReefer(message);
          }
        }
      } catch (Exception e) {
        logger.log(Level.WARNING, "OrderActor.anomaly() - Error - orderId " + getId() + " ", e);
      } finally {
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("OrderActor.anomaly() - exit id:" + getId());
        }
      }
    }

    /**
     * Calls provisioner to mark reefer spoilt
     *
     * @param reeferId
     */
    private void markSpoilt(JsonObject message) {
      int spoiltReeferId = message.getInt(Constants.REEFER_ID_KEY);
      ActorRef provisioner = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId);
      changeOrderStatus(OrderStatus.SPOILT);
      if (logger.isLoggable(Level.INFO)) {
        logger.info(String.format("OrderActor.anomaly() - orderId: %s state: %s", getId(),
                orderState.getState()));
      }
      JsonObject reply = Kar.Actors.call(provisioner, "reeferSpoilt", message).asJsonObject();
      if (reply.getString(Constants.STATUS_KEY).equals(Constants.FAILED)) {
        logger.warning("OrderActor.anomaly() - orderId " + getId() + " request to mark reeferId "
                + spoiltReeferId + " spoilt failed");
      }
    }

    /**
     * Calls provisioner to request replacement reefer
     *
     * @param reeferId
     */
    private void requestReplacementReefer(JsonObject message) {
      int spoiltReeferId = message.getInt(Constants.REEFER_ID_KEY);
      ActorRef provisioner = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId);
      if (logger.isLoggable(Level.FINE)) {
        logger.fine(String.format("OrderActor.anomaly() - orderId: %s requesting replacement for %s",
                getId(), message.getInt(Constants.REEFER_ID_KEY)));
      }
      JsonObject reply = Kar.Actors.call(provisioner, "reeferReplacement", message).asJsonObject();
      if (reply.getString(Constants.STATUS_KEY).equals(Constants.FAILED)) {
        logger.warning("OrderActor.anomaly() - orderId: " + getId()
                + " request to replace reeferId " + spoiltReeferId + " failed");
        return;
      }

      int replacementReeferId = reply.getInt(Constants.REEFER_REPLACEMENT_ID_KEY);
      if (logger.isLoggable(Level.INFO)) {
        logger.info(String.format(
                "OrderActor.anomaly() - orderId: %s state: %s spoilt reefer id: %s replacement reefer id: %s",
                getId(), orderState.getState(), spoiltReeferId, replacementReeferId));
      }

      // reefer replace is a two step process (remove + add)
      Kar.Actors.State.Submap.remove(this, Constants.REEFER_MAP_KEY, String.valueOf(spoiltReeferId));
      Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, String.valueOf(replacementReeferId),
              Json.createValue(replacementReeferId));
      orderState.replaceReefer(spoiltReeferId, replacementReeferId);
    }

    /**
     * Return number of reefers in this order
     *
     * @param message
     * @return
     */
    @Remote
    public JsonObject reeferCount(JsonObject message) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "OrderActor.reeferCount() - entry id:"+getId());
        }
        try {
            return Json.createObjectBuilder().add(Constants.TOTAL_REEFER_COUNT_KEY, orderState.getReeferMap().size()).build();
        } catch( Exception e) {
            return Json.createObjectBuilder().add(Constants.TOTAL_REEFER_COUNT_KEY, -1).build();
        } finally {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "OrderActor.reeferCount() - exit id:"+getId());
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
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(
                    "OrderActor.createOrder() - entry id:"+getId());
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
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("OrderActor.createOrder() - orderId: %s VoyageActor reply: %s", getId(), voyageBookingResult));
            }
            // Check if voyage has been booked
            if (voyageBookingResult.getString(Constants.STATUS_KEY).equals(Constants.OK)) {
                saveOrderReefers(voyageBookingResult);
                changeOrderStatus(OrderStatus.BOOKED);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(String.format("OrderActor.createOrder() - orderId: %s saved - voyage: %s state: %s reefers: %d",
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
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "OrderActor.createOrder() - exit id:"+getId());
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
            Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, reeferMap);
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
        ActorRef voyageActor = Kar.Actors.ref(ReeferAppConfig.VoyageActorName, order.getVoyageId());
        JsonValue reply = Kar.Actors.call(voyageActor, "reserve", params);
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