package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;

public class SimulatorService {

	public JsonValue setUnitPeriod(JsonValue value) {
		ActorRef aref = actorRef("simhelper","simservice");
		try {
			return actorCall(aref, "set", (JsonValue)Json.createValue("UnitPeriod"), value);
		} catch (ActorMethodNotFoundException e) {
			System.err.println("setUnitPeriod: actor "+aref.toString()+" not found");
			e.printStackTrace();
			return 	Json.createValue(-1);
		}
	}

	public JsonNumber getUnitPeriod() {
		ActorRef aref = actorRef("simhelper","simservice");
		try {
			return (JsonNumber) actorCall(aref, "get", (JsonValue)Json.createValue("UnitPeriod"));
		} catch (ActorMethodNotFoundException e) {
			System.err.println("getUnitPeriod: actor "+aref.toString()+" not found");
			e.printStackTrace();
			return 	Json.createValue(-1);
		}
	}

}
