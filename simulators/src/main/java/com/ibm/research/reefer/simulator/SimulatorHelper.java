package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorDeleteState;
import static com.ibm.research.kar.Kar.actorGetAllState;
import static com.ibm.research.kar.Kar.actorSetState;
import static com.ibm.research.kar.Kar.actorGetState;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;


/**
 * An actor that implements a persistent Map
 */
@Actor
public class SimulatorHelper extends BaseActor {

	@Activate
	public void initState() {
	}

	@Deactivate
	public void saveState() {
	}

	@Remote
	public JsonValue get(JsonValue key) {
		JsonValue value = actorGetState(this,((JsonString)key).getString());
		return value;
	}

	@Remote
	public JsonValue set(JsonValue key, JsonValue value) {
		int n = actorSetState(this,((JsonString)key).getString(), value);
		return Json.createValue(n);
	}

	@Remote
	public JsonValue del(JsonValue key) {
		int n = actorDeleteState(this, ((JsonString)key).getString());
		return Json.createValue(n);
	}

	@Remote
	public JsonValue getAll() {
		Map<String,JsonValue> tempMap = new HashMap<String,JsonValue>();
		tempMap.putAll(actorGetAllState(this));
		
		JsonObjectBuilder builder = Json.createObjectBuilder();
		tempMap.forEach(builder::add);
		return builder.build();
	}
}
