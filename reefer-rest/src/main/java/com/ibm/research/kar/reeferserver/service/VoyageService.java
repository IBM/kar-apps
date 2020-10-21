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
import javax.swing.tree.VariableHeightLayoutCache;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.VoyageStatus;

import org.springframework.stereotype.Component;

@Component
public class VoyageService extends AbstractPersistentService {
 
    Map<String, Set<Order>> voyageOrders = new HashMap<>();
    Map<String, VoyageStatus > voyageStatus = new HashMap<>();

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
        if (voyageOrders.containsKey(voyageId)) {
            return voyageOrders.get(voyageId);
        } else {
            return Collections.emptySet();
        }
    }
    public void voyageDeparted(String voyageId) {
        voyageStatus.put(voyageId, VoyageStatus.DEPARTED);
    }
    public VoyageStatus getVoyageStatus(String voyageId) {
        return voyageStatus.get(voyageId);
    }
    public void voyageEnded(String voyageId) {
        voyageStatus.put(voyageId, VoyageStatus.ARRIVED);
        if (voyageOrders.containsKey(voyageId)) {
            // remove voyage orders
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
        } else {
             return 0;
        }
    }

    public void nextDay() {
        System.out.println("VoyageService.nextDay()");
        try {
            Response response = Kar.restPost("simservice", "simulator/advancetime", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = " + respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        } catch (Exception ee) {
            ee.printStackTrace();
        }
    }

    public void changeDelay(int delay) {
        System.out.println("VoyageService.changeDelay() - delay:" + delay);
        try {
            JsonObject delayArg = Json.createObjectBuilder().add("value", delay).build();
            Response response = Kar.restPost("simservice", "simulator/setunitdelay", delayArg); 
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("VoyageService.getDelay() Sim Response = " + respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        } catch (Exception ee) {
            ee.printStackTrace();
        }
    }

    public int getDelay() throws Exception {
        System.out.println("VoyageService.getDelay()");
        Response response = Kar.restGet("simservice", "simulator/getunitdelay"); 
        JsonValue respValue = response.readEntity(JsonValue.class);
        System.out.println("VoyageService.getDelay() Sim Response = " + respValue);

        return Integer.parseInt(respValue.toString());

    }
}