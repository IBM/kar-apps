package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;

public class SimulatorService {

	private Map<String,JsonValue> persistentData;
	private ActorRef aref = actorRef("simhelper","simservice");

	// constructor
	public SimulatorService () {
		persistentData = new HashMap<String,JsonValue>();
		try {
			persistentData.putAll((JsonObject)actorCall(aref, "getAll"));
		} catch (ActorMethodNotFoundException e) {
			System.err.println("SimulatorService: actor "+aref.toString()+" not found");
			e.printStackTrace();
		}
	}

	// local utility to update local cache and persistent state
	private JsonValue set(JsonValue key, JsonValue value) {
		persistentData.put(((JsonString)key).getString(), value);
		return actorCall(aref, "set", key, value);
	}

	// local utility to retrieve cached value
	private JsonValue get(JsonValue key) {
		return persistentData.get(((JsonString)key).getString());
	}

	public JsonValue setUnitPeriod(JsonValue value) {
		JsonNumber newval = (((JsonNumber)value).intValue() > 0) ? (JsonNumber)value : (JsonNumber)Json.createValue(0);
		JsonNumber current = (JsonNumber)this.get((JsonValue)Json.createValue("UnitPeriod"));
		if (0 == current.intValue() && 0 < newval.intValue()) {
			//TODO
			//set any null config values to their default
			//start Time thread
		}
		try {
			// save new period value
			return this.set((JsonValue)Json.createValue("UnitPeriod"), (JsonValue)newval);
		} catch (Exception e) {
			e.printStackTrace();
			return 	Json.createValue(-1);
		}
	}

	public JsonNumber getUnitPeriod() {
		try {
			return (JsonNumber) actorCall(aref, "get", (JsonValue)Json.createValue("UnitPeriod"));
		} catch (ActorMethodNotFoundException e) {
			System.err.println("SimulatorService: actor "+aref.toString()+" not found");
			e.printStackTrace();
			return 	Json.createValue(-1);
		}
	}

}
