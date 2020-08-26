package com.ibm.research.kar.reefer.actors;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;

public abstract class AbstractPersistence {
    private Map<String, JsonValue> persistentData=null;
    protected JsonValue get(ActorRef ref,String key) {
        if (null == persistentData) {
            persistentData = new HashMap<>();
            persistentData.putAll(Kar.actorGetAllState(ref));
        }
        return persistentData.get(key);
      }
    
      // local utility to update local cache and persistent state
      protected JsonValue set(ActorRef ref, String key, JsonValue value) {
        if (null == persistentData) {
          persistentData = new HashMap<>();
        }
        persistentData.put(key, value);
        return Json.createValue(Kar.actorSetState(ref, key, value));
      }
}