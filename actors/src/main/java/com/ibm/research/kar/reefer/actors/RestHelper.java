package com.ibm.research.kar.reefer.actors;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;

//import static com.ibm.research.kar.Kar.actorDeleteState;
//import static com.ibm.research.kar.Kar.actorGetAllState;
//import static com.ibm.research.kar.Kar.actorGetState;
//import static com.ibm.research.kar.Kar.actorSetState;

import java.util.HashMap;
import java.util.Map;
@Actor
public class RestHelper extends BaseActor {
    
    @Activate
    public void initState() {
    }
  
    @Deactivate
    public void saveState() {
    }
  
    @Remote
    public JsonValue get(JsonValue key) {
      //JsonValue value = actorGetState(this, ((JsonString) key).getString());
        JsonValue value = Kar.Actors.State.get(this, ((JsonString) key).getString());
      return value;
    }
  
    @Remote
    public JsonValue set(JsonValue key, JsonValue value) {
      //int n = actorSetState(this, ((JsonString) key).getString(), value);
        int n = Kar.Actors.State.set(this, ((JsonString) key).getString(), value);
      return Json.createValue(n);
    }
  
    @Remote
    public JsonValue del(JsonValue key) {
      //int n = actorDeleteState(this, ((JsonString) key).getString());
        int n = Kar.Actors.State.remove(this, ((JsonString) key).getString());
      return Json.createValue(n);
    }
  
    @Remote
    public JsonValue getAll() {
      Map<String, JsonValue> tempMap = new HashMap<>();
     // tempMap.putAll(actorGetAllState(this));
        tempMap.putAll(Kar.Actors.State.getAll(this));
  
      JsonObjectBuilder builder = Json.createObjectBuilder();
      tempMap.forEach(builder::add);
      return builder.build();
    }
}