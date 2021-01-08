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