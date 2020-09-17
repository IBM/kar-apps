package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;
import static com.ibm.research.kar.Kar.restPost;

import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.JsonOrder;

@Actor
public class VoyageActor extends BaseActor {

    @Activate
    public void init() {
        System.out.println("VoyageActor.init() actorID:" + this.getId());
    }

    private Map<String, JsonValue> loadOrders() {
        return super.getSubMap(this, Constants.VOYAGE_ORDERS_KEY);
    }

    @Remote
    public JsonValue changePosition(JsonObject message) {
        System.out.println(getId() + " VoyageActor.changePosition() called " + message.toString());

        int daysAtSea = message.getInt("daysAtSea");
        JsonObject params = Json.createObjectBuilder().add(Constants.VOYAGE_ID_KEY, getId()).add("daysAtSea", daysAtSea).build();
        try {

            restPost("reeferservice", "/voyage/update", params);
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add(Constants.STATUS_KEY, "Failed").add("error", e.getMessage()).build();
        }

    }

    @Remote
    public JsonObject getVoyageOrders() {
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                .add("orders", Json.createArrayBuilder(loadOrders().values()).build()).build();
    }

    @Remote
    public JsonObject getVoyageOrderCount(JsonObject message) {
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).add("orders", loadOrders().size()).build();
    }

    @Remote
    public JsonObject changeState(JsonObject message) {
        JsonValue value = Json.createValue(message.getString(Constants.STATUS_KEY));
        super.set(this, Constants.STATUS_KEY, value);
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
    }

    @Remote
    public JsonObject reserve(JsonObject message) {
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));

        System.out.println(getId() + " VoyageActor.reserve() called " + message.toString() + " OrderID:" + order.getId()
                + " Orders size=" + loadOrders().size());

        try {
            // Book reefers for this order thru the ReeferProvisioner
            JsonValue bookingStatus = actorCall(
                    actorRef(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "bookReefers", message);
            if (reefersBooked(bookingStatus)) {

                super.addToSubMap(this, Constants.VOYAGE_ORDERS_KEY, String.valueOf(order.getId()),
                        Json.createValue(order.getId()));

                set(this, Constants.STATUS_KEY, Json.createValue("Pending"));

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