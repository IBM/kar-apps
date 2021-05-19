/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.kar.reeferserver.service;

import javax.json.Json;
import javax.json.JsonNumber;
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

    public int getDelay()  {
        Response response = Kar.Services.get(Constants.SIMSERVICE,"simulator/getunitdelay");
        JsonValue respValue = response.readEntity(JsonValue.class);
        return Integer.parseInt(respValue.toString());
    }
    public void updateVoyageCapacity(String voyageId, int freeCapacity) {
        JsonObject params = Json.createObjectBuilder().add("voyageId", voyageId).add("freeCapacity", freeCapacity)
                .build();
        try {
            Kar.Services.post(Constants.SIMSERVICE, "/simulator/updatevoyagecapacity", params);
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
    }

    public int setSimOrderTarget(int orderTarget) {
         try {
            JsonObject body = Json.createObjectBuilder().add("value", orderTarget).build();
             Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/setordertarget", body);
             JsonValue respValue = response.readEntity(JsonValue.class);
             System.out.println("SimultatorService.setSimOrderTarget() ******************** target:"+respValue);
             return ((JsonNumber)respValue).intValue();
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
         return 0;
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

    public int updateOrderSimControls(int orderTarget, int window, int updateFrequency) {
        try {
            JsonObject body = Json.createObjectBuilder().
                    add("ordertarget", orderTarget).
                    add("orderupdates", updateFrequency).
                    add("orderwindow", window).
                    build();
            Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/setordercontrols", body);
            JsonValue v = response.readEntity(JsonValue.class);

            return v.asJsonObject().getInt("ordertarget");
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
        return 0;
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