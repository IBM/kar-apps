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

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractPersistentService {
    private ActorRef aref = Kar.Actors.ref("resthelper", "reeferservice");
    private Map<String, JsonValue> persistentData = null;

    protected JsonValue get(String key) {
        if (null == persistentData) {
            persistentData = new HashMap<>();
            persistentData.putAll(Kar.Actors.State.getAll(aref));
        }
        return persistentData.get(key);
    }

    // local utility to update local cache and persistent state
    protected JsonValue set(String key, JsonValue value) {
        JsonValue retValue;
        if (null == persistentData) {
            persistentData = new HashMap<>();
        }
        persistentData.put(key, value);
        return Json.createValue(Kar.Actors.State.set(aref, key, value));
    }
    protected void remove(List<String> keys) {
        Kar.Actors.State.removeAll(aref, keys);
    }

    /**
     * Returns a list of unique voyage ids generated from ReeferProvisioner's reefer inventory.
     *
     * @return
     */
    protected List<String> getVoyageIds() {
        ActorRef reeferProvisionerRef = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId);
        Map<String, JsonValue> reeferInventory = Kar.Actors.State.Submap.getAll(reeferProvisionerRef, Constants.REEFER_MAP_KEY);
        return reeferInventory.
                values().
                stream().
                filter(Objects::nonNull).
                map(JsonValue::asJsonObject).
                map(jo -> jo.getString(Constants.VOYAGE_ID_KEY)).
                distinct().
                sorted().
                collect(Collectors.toList());
    }
    protected Optional<JsonObject> getVoyageMetadata(String voyageId) {
        ActorRef voyageActorRef = Kar.Actors.ref(ReeferAppConfig.VoyageActorName, voyageId);
        JsonValue jv  = Kar.Actors.State.get(voyageActorRef, Constants.VOYAGE_INFO_KEY);
        if ( jv == null || jv == JsonValue.NULL) {
           return  Optional.empty();
        }
        return Optional.of(jv.asJsonObject());
    }
}