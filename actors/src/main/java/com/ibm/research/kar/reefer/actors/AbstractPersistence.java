package com.ibm.research.kar.reefer.actors;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonNumber;
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
  protected int incrementAndSave(ActorRef ref, String key, int incrementBy) {
    return calculateAndSave(ref, key, true, incrementBy);
  }
  protected int decrementAndSave(ActorRef ref, String key, int decrementBy) {
    return calculateAndSave(ref, key, false, decrementBy);
  }
  protected int calculateAndSave(ActorRef ref, String key, boolean increment, int adjustment) {
    JsonValue counter = get(ref,key);
    int total=0;
    if ( counter != null ) {
      total = ((JsonNumber)counter).intValue();
    } 
    if ( increment ) {
      total += adjustment;
    } else if ( (total - adjustment) > 0 ) {
      total -= adjustment;
    } else {
      total = 0;
    }
    
    set(ref, key, Json.createValue( total));
    return total;
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
  protected int getSubMapSize(ActorRef ref,String subMapName) {
    return Kar.actorSubMapSize(ref, subMapName);
  }
  protected void removeFromSubMap(ActorRef ref,String subMapName, String subMapKey) {
    Kar.actorDeleteState(ref,subMapName,subMapKey);
  }
  protected void addToSubMap(ActorRef ref,String subMapName, String subMapKey, JsonValue value) {
    Kar.actorSetState(ref,subMapName,subMapKey, value);
  }
  protected void addSubMap(ActorRef ref,String subMapName, Map<String, JsonValue> subMap) {
    Kar.actorSetMultipleState(ref, subMapName, subMap);
  }
  protected void clearState(ActorRef ref) {
    Kar.actorDeleteAllState(ref);
  }
}