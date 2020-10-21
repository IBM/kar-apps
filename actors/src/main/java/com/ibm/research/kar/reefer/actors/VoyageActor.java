package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.Kar;

import static com.ibm.research.kar.Kar.actorRef;
import static com.ibm.research.kar.Kar.restPost;
import static com.ibm.research.kar.Kar.restGet;
import static com.ibm.research.kar.Kar.actorCall;
import com.ibm.research.kar.actor.ActorRef;

import java.util.Map;
import java.time.Instant;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonString;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.json.JsonUtils;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Ship;
import com.ibm.research.kar.reefer.model.VoyageStatus;
import com.ibm.research.kar.reefer.common.time.TimeUtils;

@Actor
public class VoyageActor extends BaseActor {
    /**
     * If Voyage info is not available in Kar persistent storage call REST
     * to get it. The Voyage info includes details like daysAtSea, departure
     * date, arrival date, etc
     */
    @Activate
    public void init() {
        System.out.println("VoyageActor.init() actorID:" + this.getId());
        try {
            // Check if Voyage info is not available in persistent store
            if (super.get(this, Constants.VOYAGE_INFO_KEY) == null) {
                // call REST to fetch Voyage info
                JsonObject voyageInfo = getVoyageInfo();
                System.out.println("VoyageActor.init() id:" + getId() + " voyage info:" + voyageInfo);
                // store Voyage info in persistent store
                super.set(this, Constants.VOYAGE_INFO_KEY, voyageInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Call REST to get voyage information.
     *
     * @return - json encoded voyage info
     */
    private JsonObject getVoyageInfo() {
        /// Ask REST voyage info
        Response response = restGet("reeferservice", "/voyage/info/" + getId());
        JsonValue jsonVoyage = response.readEntity(JsonValue.class);
        return jsonVoyage.asJsonObject();
    }

    private boolean shipDeparted(int daysAtSea) {
        return daysAtSea == 1;
    }

    private VoyageStatus getVoyageStatus() {
        JsonValue jsonStatus = get(this, Constants.VOYAGE_STATUS_KEY);
        if (jsonStatus == null) {
            return VoyageStatus.UNKNOWN;
        }
        return VoyageStatus.valueOf(((JsonString) jsonStatus).getString());
    }

    /**
     * Returns this voyage orders
     * 
     * @return Order Map
     */

    private Map<String, JsonValue> loadOrders() {
        return super.getSubMap(this, Constants.VOYAGE_ORDERS_KEY);
    }

    /**
     * Determines if ship arrived at the destination port or not. Ship arrives when
     * current date = scheduled shipArrivalDate
     * @param shipCurrentDate - current date
     * @param voyage - voyage info
     * @return - true if ship arrived, false otherwise
     */
    private boolean shipArrived(Instant shipCurrentDate, Voyage voyage) { // String shipArrivalDate, String voyageId) {
        Instant scheduledArrivalDate = Instant.parse(voyage.getArrivalDate());
        return ((shipCurrentDate.equals(scheduledArrivalDate)
                || shipCurrentDate.isAfter(scheduledArrivalDate) && !VoyageStatus.ARRIVED.equals(getVoyageStatus())));
    }

    /**
     * Called when ship position changes while in-transit
     * 
     * @param message - Json encoded message containing daysAtSea value
     * @return
     */
    @Remote
    public JsonValue changePosition(JsonObject message) {
        System.out.println("VoyageActor.changePosition() called Id:" + getId() + " " + message.toString() + " state:"
                + getVoyageStatus());
        try {
            // fetch json encoded voyage info
            JsonValue jsonVoyage = super.get(this, Constants.VOYAGE_INFO_KEY);
            Voyage voyage = JsonUtils.jsonToVoyage(jsonVoyage.asJsonObject());

            int daysAtSea = message.getInt( Constants.VOYAGE_DAYSATSEA_KEY );
            Instant shipCurrentDate = TimeUtils.getInstance().futureDate(voyage.getSailDateObject(), daysAtSea);
            System.out.println(
                    "VoyageActor.changePosition() voyage info:" + jsonVoyage + " ship current date:" + shipCurrentDate);
            String restMethodToCall = "";
            if (shipArrived(shipCurrentDate, voyage)) {
                set(this, Constants.VOYAGE_STATUS_KEY, Json.createValue(VoyageStatus.ARRIVED.name()));
                long snapshot = System.nanoTime();
                processArrivedVoyage(voyage, daysAtSea);
                System.out.println("VoyageActor.changePosition() voyageId=" + voyage.getId() + " order count: " +
                        loadOrders().size() + " arrival processing: " + (System.nanoTime()-snapshot)/1000000);
            } else if (shipDeparted(daysAtSea) && !VoyageStatus.DEPARTED.equals(getVoyageStatus())) {
                set(this, Constants.VOYAGE_STATUS_KEY, Json.createValue(VoyageStatus.DEPARTED.name()));
                long snapshot = System.nanoTime();
                processDepartedVoyage(voyage, daysAtSea);
                System.out.println("VoyageActor.changePosition() voyageId=" + voyage.getId() + " order count: " +
                        loadOrders().size() + " departure processing: " + (System.nanoTime()-snapshot)/1000000);
            } else {  // voyage in transit
                System.out.println("VoyageActor.changePosition() Updating REST - daysAtSea:" + daysAtSea);
                // update REST voyage days at sea
                messageRest("/voyage/update/position", daysAtSea);
            }
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();
        }

    }

    /**
     * Calls REST and Order actors when a ship arrives at the destination port
     * @param voyage - Voyage info
     * @param daysAtSea - ship days at sea
     */
    private void processArrivedVoyage(Voyage voyage, int daysAtSea) {
        System.out.println("VoyageActor.changePosition() voyageId=" + voyage.getId()
                + " has ARRIVED ------------------------------------------------------");
        messageRest("/voyage/update/delivered", daysAtSea);
        // notify each order actor that the ship arrived
        loadOrders().values().forEach(order -> {
            System.out.println("VoyageActor.changePosition() voyageId=" + voyage.getId()
                    + " Notifying Order Actor of arrival - OrderID:" + ((JsonString) order).getString());
            messageOrderActor(((JsonString) order).getString(), "delivered");
        });

    }
    /**
     * Calls REST and Order actors when a ship departs from the origin port
     * @param voyage - Voyage info
     * @param daysAtSea - ship days at sea
     */
    private void processDepartedVoyage(Voyage voyage, int daysAtSea) {
        System.out.println("VoyageActor.changePosition() voyageId=" + voyage.getId()
                + " has DEPARTED ------------------------------------------------------");
        messageRest("/voyage/update/departed", daysAtSea);
        loadOrders().values().forEach(order -> {
            System.out.println("VoyageActor.changePosition() voyageId=" + voyage.getId()
                    + " Notifying Order Actor of departure - OrderID:" + ((JsonString) order).getString());
            messageOrderActor(((JsonString) order).getString(), "departed");
        });

    }

    /**
     * Update REST with ship position
     * @param methodToCall -  REST API to call
     * @param daysAtSea - ship days at sea
     */
    private void messageRest(String methodToCall, int daysAtSea) {
        JsonObject params = Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).add("daysAtSea", daysAtSea)
                .build();
        try {
            /// Notify REST of the position change
            restPost("reeferservice", methodToCall, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Call OrderActor when a ship carrying the order either departs or arrives
     *
     * @param orderId - order id
     * @param methodToCall - actor method to call
     */
    private void messageOrderActor(String orderId, String methodToCall) {
        ActorRef orderActor = Kar.actorRef(ReeferAppConfig.OrderActorName, orderId);
        JsonObject params = Json.createObjectBuilder().build();
        System.out.println("VoyageActor.messageOrderActor() voyageId=" + getId()
                + " Notifying Order Actor of departure - OrderID:" + orderId);
        actorCall(orderActor, methodToCall, params);
    }

    /**
     * Returns all orders associated with this voyage
     * 
     * @return The list of Orders
     */
    @Remote
    public JsonObject getVoyageOrders() {
        System.out.println("VoyageActor.getVoyageOrders() called " + getId());
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                .add("orders", Json.createArrayBuilder(loadOrders().values()).build()).build();
    }

    /**
     * Returns number of orders associated with this voyage
     * 
     * @param message
     * @return
     */
    @Remote
    public JsonObject getVoyageOrderCount(JsonObject message) {
        System.out.println("VoyageActor.getVoyageOrderCount() called " + getId());
        int orderCount = super.getSubMapSize(this, Constants.VOYAGE_ORDERS_KEY);
        System.out.println(" VoyageActor.getVoyageOrderCount() called " + getId() + " Orders:" + orderCount);
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).add("orders", orderCount).build();
    }

    /**
     * Called to change voyage state
     * 
     * @param message Json encoded message containing new status
     * @return
     */
    @Remote
    public JsonObject changeState(JsonObject message) {
        System.out.println("VoyageActor.changeState() called " + getId() + " message:" + message);
        JsonValue value = Json.createValue(message.getString(Constants.STATUS_KEY));
        super.set(this, Constants.VOYAGE_STATUS_KEY, value);
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
    }

    /**
     * Called when ship arrives at the destination
     * 
     * @param message Json encoded message containing new status
     * @return
     */
    @Remote
    public JsonObject voyageEnded(JsonObject message) {
        System.out.println("VoyageActor.voyageEnded() called " + getId());
        super.clearState(this);
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
    }

    /**
     * Called to book a voyage for a given order.
     * 
     * @param message Json encoded order properties
     * @return
     */
    @Remote
    public JsonObject reserve(JsonObject message) {
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
        // Current order count in this voyage
        int orderCount = super.getSubMapSize(this, Constants.VOYAGE_ORDERS_KEY);

        System.out.println("VoyageActor.reserve() called Id:" + getId() + " " + message.toString() + " OrderID:"
                + order.getId() + " Orders size=" + orderCount);

        try {
            // Book reefers for this order through the ReeferProvisioner
            JsonValue bookingStatus = actorCall(
                    actorRef(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "bookReefers", message);
            if (reefersBooked(bookingStatus)) {
                // add new order to this voyage order list
                super.addToSubMap(this, Constants.VOYAGE_ORDERS_KEY, String.valueOf(order.getId()),
                        Json.createValue(order.getId()));
                set(this, Constants.VOYAGE_STATUS_KEY, Json.createValue(VoyageStatus.PENDING.name()));
               return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                        .add(Constants.REEFERS_KEY, bookingStatus.asJsonObject().getJsonArray(Constants.REEFERS_KEY))
                        .add(JsonOrder.OrderKey, order.getAsObject()).build();

            } else {
                return bookingStatus.asJsonObject();
            }

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "INVALID_CALL")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();

        } catch (Exception ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "FAILED").add("ERROR", "Exception")
                    .add(Constants.ORDER_ID_KEY, String.valueOf(this.getId())).build();

        }

    }

    private boolean reefersBooked(JsonValue bookingStatus) {
        return bookingStatus.asJsonObject().getString(Constants.STATUS_KEY).equals(Constants.OK);
    }
}