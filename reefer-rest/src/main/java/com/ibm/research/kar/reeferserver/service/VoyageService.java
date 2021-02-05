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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.*;
import javax.swing.tree.VariableHeightLayoutCache;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.json.JsonUtils;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.VoyageStatus;

import com.ibm.research.kar.reeferserver.controller.VoyageController;
import org.springframework.stereotype.Component;

@Component
public class VoyageService extends AbstractPersistentService {
    private Map<String, VoyageStatus > voyageStatus = new ConcurrentHashMap<>();
    private static final Logger logger = Logger.getLogger(VoyageService.class.getName());

    public void voyageDeparted(String voyageId) {
        voyageStatus.put(voyageId, VoyageStatus.DEPARTED);
    }
    public VoyageStatus getVoyageStatus(String voyageId) {
        return voyageStatus.get(voyageId);
    }
    public void voyageEnded(String voyageId) {
        voyageStatus.put(voyageId, VoyageStatus.ARRIVED);
    }
    public void nextDay() {
        try {
            Response response = Kar.Services.post(Constants.SIMSERVICE, "simulator/advancetime", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
            if ( logger.isLoggable(Level.INFO)) {
                logger.info("VoyageService.nextDay() - simulator reply:"+respValue);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING,"",e);
        }
    }
    public void changeDelay(int delay) {
        try {
            JsonObject delayArg = Json.createObjectBuilder().add("value", delay).build();
            Response response = Kar.Services.post(Constants.SIMSERVICE,"simulator/setunitdelay", delayArg);
            JsonValue respValue = response.readEntity(JsonValue.class);
        } catch (Exception e) {
            logger.log(Level.WARNING,"",e);
        }
    }
    public int getDelay() throws Exception {
        Response response = Kar.Services.get(Constants.SIMSERVICE,"simulator/getunitdelay");
        JsonValue respValue = response.readEntity(JsonValue.class);
        return Integer.parseInt(respValue.toString());
    }
    public void restoreActiveVoyageOrders(List<Voyage> activeVoyages) {
        for( Voyage voyage: activeVoyages ) {
            Optional<JsonObject> state = super.getVoyageMetadata(voyage.getId());
            if ( state.isPresent()) {
                Voyage recoveredVoyageState = VoyageJsonSerializer.deserialize(state.get());
                voyage.setOrderCount(recoveredVoyageState.getOrderCount());
                voyage.getRoute().getVessel().setProgress(recoveredVoyageState.getRoute().getVessel().getProgress());
                voyage.getRoute().getVessel().setPosition(recoveredVoyageState.getRoute().getVessel().getPosition());
            }
        }
    }
}