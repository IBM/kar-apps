package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.Kar;

import com.ibm.research.kar.actor.ActorRef;

import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.*;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.json.JsonUtils;
import com.ibm.research.kar.reefer.model.*;
import com.ibm.research.kar.reefer.common.time.TimeUtils;

@Actor
public class VoyageActor extends BaseActor {
    private JsonObject voyageInfo;
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
                // store static voyage information in Kar storage for reuse
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
        } catch (Exception e) {
            logger.log(Level.WARNING, "VoyageActor.init() - Error - voyageId "+getId()+" ", e);
        }
    }

    /**
     * Save actor's state when the instance is passivated. Currently just saves the
     * actor's status.
     */
    @Deactivate
    public void deactivate() {
        if (voyageStatus != null && !((JsonString)voyageStatus).getString().equals( VoyageStatus.ARRIVED.name())) {
            Kar.Actors.State.set(this, Constants.VOYAGE_STATUS_KEY, voyageStatus);
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

        try {
            Voyage voyage = JsonUtils.jsonToVoyage(voyageInfo);
            // the simulator advances ship position
            int daysAtSea = message.getInt(Constants.VOYAGE_DAYSATSEA_KEY);
            // given ship sail date and current days at sea get ship's current date
            Instant shipCurrentDate = TimeUtils.getInstance().futureDate(voyage.getSailDateObject(), daysAtSea);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(
                        "VoyageActor.changePosition() voyage info:" + voyageInfo + " ship current date:" + shipCurrentDate);
            }
            String restMethodToCall = "";
            // if ship's current date matches arrival date, the ship arrived
            if (shipArrived(shipCurrentDate, voyage)) {
                // Arriving voyage must be in DEPARTED state
                if ( !VoyageStatus.DEPARTED.equals( getVoyageStatus()) ) {
                    logger.log(Level.WARNING,"VoyageActor.changePosition() - voyage:"+voyage.getId()+" arrived BUT its expected state is not DEPARTED. Instead it is "+getVoyageStatus());
                }
                voyageStatus = Json.createValue(VoyageStatus.ARRIVED.name());
                long snapshot = System.nanoTime();
                processArrivedVoyage(voyage, daysAtSea);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("VoyageActor.changePosition() voyageId=" + voyage.getId() + " ARRIVED - order count: " +
                            orders.size() + " arrival processing: " + (System.nanoTime() - snapshot) / 1000000);
                }
                // voyage arrived, no longer need the state
                Kar.Actors.remove(this);
            } // check if ship departed its origin port
            else if ((daysAtSea == 1) && !VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
                voyageStatus = Json.createValue(VoyageStatus.DEPARTED.name());
                long snapshot = System.nanoTime();
                processDepartedVoyage(voyage, daysAtSea);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("VoyageActor.changePosition() voyageId=" + voyage.getId() + " DEPARTED -  order count: " +
                            orders.size() + " departure processing: " + (System.nanoTime() - snapshot) / 1000000);
                }
            } else {  // voyage in transit
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("VoyageActor.changePosition() Updating REST - daysAtSea:" + daysAtSea);
                }
                // update REST voyage days at sea
                messageRest("/voyage/update/position", daysAtSea);
            }
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "VoyageActor.changePosition() - Error - voyageId "+getId()+" ", e);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
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
            if ( bookingStatus.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK) ) {
                // add new order to this voyage order list
                Kar.Actors.State.Submap.set(this, Constants.VOYAGE_ORDERS_KEY, String.valueOf(order.getId()),
                        Json.createValue(order.getId()));
                orders.put(String.valueOf(order.getId()), String.valueOf((order.getId())));
                // reload order map since there is a change. Local orders map is not mutable
                voyageStatus = Json.createValue(VoyageStatus.PENDING.name());
                return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                        .add(Constants.REEFERS_KEY, bookingStatus.asJsonObject().getJsonArray(Constants.REEFERS_KEY))
                        .add(JsonOrder.OrderKey, order.getAsObject()).build();
            } else {
                return bookingStatus.asJsonObject();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "VoyageActor.reserve() - Error - voyageId "+getId()+" ", e);
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
                    "VoyageActor.processDepartedVoyage() - calling ReeferProvisionerActor voyageReefersDeparted - voyageId:"+getId());
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
        JsonObject params = Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).add("daysAtSea", daysAtSea)
                .build();
        try {
            /// Notify REST of the position change
             Kar.Services.post("reeferservice", methodToCall, params);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
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
        Kar.Actors.call(orderActor, methodToCall);
    }

    /**
     * Converts voyage status from JsonValue to VoyageStatus
     *
     * @return VoyageStatus instance
     */
    private VoyageStatus getVoyageStatus() {
        if (voyageStatus == null) {
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
    private boolean shipArrived(Instant shipCurrentDate, Voyage voyage) { // String shipArrivalDate, String voyageId) {
        Instant scheduledArrivalDate = Instant.parse(voyage.getArrivalDate());
        return ((shipCurrentDate.equals(scheduledArrivalDate)
                || shipCurrentDate.isAfter(scheduledArrivalDate) && !VoyageStatus.ARRIVED.equals(getVoyageStatus())));
    }

}