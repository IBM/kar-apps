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

package com.ibm.research.kar.reefer.common;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;

import javax.json.JsonValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActorState {
    private ActorRef actor;

    private Map<String, JsonValue> actorStateMap = new HashMap<>();
    private Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
    private Map<String, List<String>> deleteMap = new HashMap<>();

    public ActorState(ActorRef actor) {
        this.actor = actor;
    }
    public ActorState delete(String key, List<String> toDeleteList) {
        deleteMap.put(key, toDeleteList);
        return this;
    }
    public ActorState update(String key, JsonValue value) {
        actorStateMap.put(key, value);
        return this;
    }
    public ActorState updateSubMap(String subMapKey, String key, JsonValue value) {
        Map<String, JsonValue> subMap;
        if ( subMapUpdates.containsKey(subMapKey) ) {
            subMap = subMapUpdates.get(subMapKey);
        } else {
            subMap = new HashMap<>();
            subMapUpdates.put(subMapKey, subMap);
        }
        subMap.put(key, value);
        return this;
    }
    public void persist() {
        Kar.Actors.State.update(actor, Collections.emptyList(), deleteMap, actorStateMap, subMapUpdates);
    }
}