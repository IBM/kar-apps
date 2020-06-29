package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorGetAllState;
import static com.ibm.research.kar.Kar.actorSetMultipleState;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
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
//			persistentData.put(((JsonValue)Json.createValue("population")).toString(), (JsonValue)Json.createValue(1024));
		}
		persistentData.putAll(actorGetAllState(this));
	}

	@Deactivate
	public void saveState() {
//		for (String key : persistentData.keySet()) {
//			System.out.println("simhelper:saveState");
//			System.out.println(key+" -> "+persistentData.get(key).toString());
//		}
		actorSetMultipleState(this, persistentData);
	}

	@Remote
	public JsonValue get(JsonValue key) {
		return persistentData.get(key.toString());
	}

	@Remote
	public void set(JsonValue key, JsonValue value) {
		persistentData.put(key.toString(), value);
	}
}
