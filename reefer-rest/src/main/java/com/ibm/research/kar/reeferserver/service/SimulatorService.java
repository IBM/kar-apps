package com.ibm.research.kar.reeferserver.service;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.actors.VoyageActor;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.OrderSimControls;
import com.ibm.research.kar.reefer.model.ReeferSimControls;

import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class SimulatorService {
    private static final Logger logger = Logger.getLogger(SimulatorService.class.getName());
    public void updateVoyageCapacity(String voyageId, int freeCapacity) {
        JsonObject params = Json.createObjectBuilder().add("voyageId", voyageId).add("freeCapacity", freeCapacity)
                .build();
        try {
            Kar.Services.post(Constants.SIMSERVICE, "/simulator/updatevoyagecapacity", params);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public void setSimOrderTarget(int orderTarget) {
         try {
            JsonObject body = Json.createObjectBuilder().add("value", orderTarget).build();
             Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/setordertarget", body);
             JsonValue respValue = response.readEntity(JsonValue.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public void setSimOrderWindow(int window) {
        try {
            JsonObject body = Json.createObjectBuilder().add("value", window).build();
            Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/setorderwindow", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public void setSimOrderUpdateFrequency(int updateFrequency) {
        try {
            JsonObject body = Json.createObjectBuilder().add("value", updateFrequency).build();
            Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/setorderupdates", body);
            response.readEntity(JsonValue.class);
         } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public void updateOrderSimControls(int orderTarget, int window, int updateFrequency) {
        try {
            setSimOrderTarget(orderTarget);
            setSimOrderWindow(window);
            setSimOrderUpdateFrequency(updateFrequency);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public OrderSimControls getOrderSimControls() {
        int target = 0;
        int window = 1;
        int updateFrequency = 2;
        try {
            target = getSimOrderTarget();
            window = getOrderSimWindow();
            updateFrequency = getOrderSimUpdateFrequency();

        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
        return new OrderSimControls(target, window, updateFrequency);
    }

    public int getSimOrderTarget() {
        int orderTarget = 0;
        try {
            Response response = Kar.Services.get(Constants.SIMSERVICE, "simulator/getordertarget");
            JsonValue respValue = response.readEntity(JsonValue.class);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
        return orderTarget;
    }

    public int getOrderSimWindow() {
        int orderTarget = 0;
        try {
            Response response = Kar.Services.get(Constants.SIMSERVICE, "simulator/getorderwindow");
            JsonValue respValue = response.readEntity(JsonValue.class);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
        return orderTarget;
    }

    public int getOrderSimUpdateFrequency() {
        int orderTarget = 0;
        try {
            Response response = Kar.Services.get(Constants.SIMSERVICE, "simulator/getorderupdates");
            JsonValue respValue = response.readEntity(JsonValue.class);
           orderTarget = Integer.parseInt(respValue.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
        return orderTarget;
    }

    public ReeferSimControls getReeferSimControls() {
        Response response = Kar.Services.get(Constants.SIMSERVICE, "simulator/getreefercontrols");
        JsonValue respValue = response.readEntity(JsonValue.class);
        int failureRate = respValue.asJsonObject().getInt("failuretarget");
        int updateFrequency = respValue.asJsonObject().getInt("reeferupdates");
        return new ReeferSimControls(failureRate, updateFrequency);
    }

    public void updateReeferSimControls(ReeferSimControls simControls) {
        try {
            JsonObject body = Json.createObjectBuilder().add("reeferupdates", simControls.getUpdateFrequency())
                    .add("failuretarget", simControls.getFailureRate()).build();
            Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/setreefercontrols", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public void generateAnomaly() {
        try {
            Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/createanomaly", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("SimulatorService.generateAnomaly() - sim response:"+respValue);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public void createOrder() {
        try {
            Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/createorder", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }
}