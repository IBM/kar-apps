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

import javax.json.Json;
import javax.json.JsonValue;
import java.util.HashMap;
import java.util.Map;

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
}