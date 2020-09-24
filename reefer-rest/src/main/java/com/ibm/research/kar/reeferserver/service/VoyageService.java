package com.ibm.research.kar.reeferserver.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.model.Order;

import org.springframework.stereotype.Component;

@Component
public class VoyageService extends AbstractPersistentService {

    Map<String, Set<Order>> voyageOrders = new HashMap<>();

    public void addOrderToVoyage(Order order) {

        Set<Order> orders;
        if (voyageOrders.containsKey(order.getVoyageId())) {
            orders = voyageOrders.get(order.getVoyageId());
        } else {
            orders = new HashSet<>();
            voyageOrders.put(order.getVoyageId(), orders);
        }
        orders.add(order);
        System.out.println("VoyageService.addOrderToVoyage()-Added Order to Voyage:" + order.getVoyageId()
                + " Order Count:" + orders.size());

    }

    public Set<Order> getOrders(String voyageId) {
        /*
         * the following calls fail with Timeout ActorRef voyageActor =
         * Kar.actorRef("voyage", voyageId); JsonObject params =
         * Json.createObjectBuilder().build(); JsonValue reply =
         * Kar.actorCall(voyageActor, "getVoyageOrderCount", params);
         * System.out.println("*************** VoyageService.getOrders() - voyage id:"
         * +voyageId+" reply:"+reply);
         */
        if (voyageOrders.containsKey(voyageId)) {
            return voyageOrders.get(voyageId);
        } else {
            return Collections.emptySet();
        }
    }

    public void voyageEnded(String voyageId) {

        if (voyageOrders.containsKey(voyageId)) {
            Set<Order> orders = voyageOrders.get(voyageId);
            System.out.println("VoyageService.voyageEnded() - voyage orders:" + orders.size());
            JsonObjectBuilder orderObject = Json.createObjectBuilder();
            JsonObject params = orderObject.build();
            orders.forEach(order -> {
                System.out.println("VoyageService.voyageEnded() - calling OrderActor.delivered() Id:"
                        + order.getId());
                ActorRef orderActor = Kar.actorRef("order", order.getId());
                JsonValue reply = Kar.actorCall(orderActor, "delivered", params);
                System.out.println("Order Actor reply:" + reply);
            });

            for (Iterator<String> iterator = voyageOrders.keySet().iterator(); iterator.hasNext();) {
                String key = iterator.next();
                if (key.equals(voyageId)) {
                    iterator.remove();
                    break;
                }
            }

        }
    }

    public int getVoyageOrderCount(String voyageId) {
        if (voyageOrders.containsKey(voyageId)) {
            return voyageOrders.get(voyageId).size();
        }
        return getOrders(voyageId).size();
        // return 0;
    }

    public void nextDay() {
        System.out.println("VoyageService.nextDay()");
        try {
            Response response = Kar.restPost("simservice", "simulator/advancetime", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = " + respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            // return Json.createObjectBuilder().add("status",
            // OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey,
            // order.getId()).build();

        } catch (Exception ee) {
            ee.printStackTrace();
        }
    }

    public void changeDelay(int delay) {
        System.out.println("VoyageService.changeDelay() - delay:" + delay);
        try {
            JsonObject delayArg = Json.createObjectBuilder().add("value", delay).build();
            Response response = Kar.restPost("simservice", "simulator/setunitdelay", delayArg); // Json.createValue(delay));
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("VoyageService.getDelay() Sim Response = " + respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            // return Json.createObjectBuilder().add("status",
            // OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey,
            // order.getId()).build();

        } catch (Exception ee) {
            ee.printStackTrace();
        }
    }

    public int getDelay() throws Exception {
        System.out.println("VoyageService.getDelay()");
        Response response = Kar.restGet("simservice", "simulator/getunitdelay"); // Json.createValue(delay));
        JsonValue respValue = response.readEntity(JsonValue.class);
        System.out.println("VoyageService.getDelay() Sim Response = " + respValue);

        return Integer.parseInt(respValue.toString());

    }
}