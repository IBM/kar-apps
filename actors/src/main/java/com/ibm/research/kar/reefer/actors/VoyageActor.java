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
        System.out.println("VoyageActor.changePosition() called Id:"+getId()+" " + message.toString());

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
        System.out.println("VoyageActor.getVoyageOrders() called " + getId() );
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK)
                .add("orders", Json.createArrayBuilder(loadOrders().values()).build()).build();
    }

    @Remote
    public JsonObject getVoyageOrderCount(JsonObject message) {
        System.out.println("VoyageActor.getVoyageOrderCount() called " + getId() );
        Map<String, JsonValue> orders = loadOrders();
        System.out.println(" VoyageActor.getVoyageOrderCount() called " + getId() +" Orders:"+orders.size());
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).add("orders", orders.size()).build();
    }

    @Remote
    public JsonObject changeState(JsonObject message) {
        System.out.println("VoyageActor.changeState() called " + getId() );
        JsonValue value = Json.createValue(message.getString(Constants.STATUS_KEY));
        super.set(this, Constants.STATUS_KEY, value);
        return Json.createObjectBuilder().add(Constants.STATUS_KEY, Constants.OK).build();
    }

    @Remote
    public JsonObject reserve(JsonObject message) {
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));

        System.out.println("VoyageActor.reserve() called Id:"+getId() +" "+ message.toString() + " OrderID:" + order.getId()
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