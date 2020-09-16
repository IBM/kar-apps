package com.ibm.research.kar.reefer.actors;

import java.util.HashMap;
import java.util.Map;

import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;

public abstract class AbstractPersistence {
  private Map<String, JsonValue> persistentData = null;

  protected JsonValue get(ActorRef ref, String key) {
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
    Kar.actorSetState(ref, key, value);
    return value;
  }
  protected Map<String, JsonValue> getSubMap(ActorRef ref,String subMapName) {
    return Kar.actorSubMapGet(ref, subMapName);
  }
  protected void removeFromSubMap(ActorRef ref,String subMapName, String subMapKey) {
    Kar.actorDeleteState(ref,"reefers-map",subMapKey);
  }
  protected void addToSubMap(ActorRef ref,String subMapName, String subMapKey, JsonValue value) {
    Kar.actorSetState(ref,"reefers-map",subMapKey, value);
  }
  protected void addSubMap(ActorRef ref,String subMapName, Map<String, JsonValue> subMap) {
    Kar.actorSetMultipleState(ref, subMapName, subMap);
  }
}