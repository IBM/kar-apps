package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.*;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;


/**
 * A simple calculator that performs operations on an accumulator.
 */
@Actor
public class SimulatorHelper extends BaseActor {

	private Map<String,JsonValue> persistentData;

	@Activate
	public void initState() {
		if (null == persistentData) {
			persistentData = new HashMap<String,JsonValue>();
		}
		persistentData.putAll(actorGetAllState(this));
	}

	@Deactivate
	public void saveState() {
		//TODO not necessary as each update is persistent, right?
//		actorSetMultipleState(this, persistentData);
	}

	@Remote
	public JsonValue get(JsonValue key) {
		return persistentData.get(((JsonString)key).getString());
	}

	@Remote
	public JsonNumber set(JsonValue key, JsonValue value) {
		int n = actorSetState(this,((JsonString)key).getString(), value);
		persistentData.put(((JsonString)key).getString(), value);
		return Json.createValue(n);
	}

	@Remote
	public JsonNumber del(JsonValue key) {
		int n = actorDeleteState(this, ((JsonString)key).getString());
		persistentData.remove(((JsonString)key).getString());
		return Json.createValue(n);
	}

	@Remote
	public JsonObject showAllState() {
		Map<String,JsonValue> tempMap = new HashMap<String,JsonValue>();
		tempMap.putAll(actorGetAllState(this));
		
		JsonObjectBuilder builder = Json.createObjectBuilder();
		tempMap.forEach(builder::add);
		return builder.build();
	}
}
