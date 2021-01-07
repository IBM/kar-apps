package com.ibm.research.kar.reeferserver.service;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;

public abstract class AbstractPersistentService {
    //private ActorRef aref = Kar.actorRef("resthelper", "reeferservice");
    private ActorRef aref = Kar.Actors.ref("resthelper", "reeferservice");
    private Map<String, JsonValue> persistentData=null;
    protected JsonValue get(String key) {
        if (null == persistentData) {
            persistentData = new HashMap<>();
           // persistentData.putAll(Kar.actorGetAllState(aref));
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
        //retValue = Json.createValue(Kar.actorSetState(aref, key, value));
        return Json.createValue(Kar.Actors.State.set(aref, key, value));

        //return retValue;
      }
      /*
      protected Map<String, JsonValue> getSubMap(String subMapName) {

        //return Kar.actorSubMapGet(aref, subMapName);
        return Kar.Actors.State.get(aref, subMapName);
      }
      protected void removeFromSubMap(String subMapName, String subMapKey) {
       // Kar.actorDeleteState(aref,subMapName,subMapKey);
        Kar.Actors.State.Submap.remove(aref,subMapName,subMapKey);
      }
      protected void addToSubMap(String subMapName, String subMapKey, JsonValue value) {
        //Kar.actorSetState(aref,subMapName,subMapKey, value);
        Kar.Actors.State.Submap.set(aref,subMapName,subMapKey, value);
      }
      protected void addSubMap(String subMapName, Map<String, JsonValue> subMap) {
        //Kar.actorSetMultipleState(aref, subMapName, subMap);
        Kar.Actors.State.Submap.set(aref, subMapName, subMap);
      }
      */
}