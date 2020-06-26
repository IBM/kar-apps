package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;

public class SimulatorService {

	public void setUnitPeriod(JsonValue value) {
		ActorRef aref = actorRef("simhelper","simservice");
		try {
			actorCall(aref, "set", (JsonValue)Json.createValue("UnitPeriod"), value);
		} catch (ActorMethodNotFoundException e) {
			System.err.println("setUnitPeriod: actor "+aref.toString()+" not found");
			e.printStackTrace();
		}
	}

	public JsonNumber getUnitPeriod() {
		JsonNumber unitPeriod = Json.createValue(-1);
		ActorRef aref = actorRef("simhelper","simservice");
		try {
			unitPeriod = (JsonNumber) actorCall(aref, "get", (JsonValue)Json.createValue("UnitPeriod"));
		} catch (ActorMethodNotFoundException e) {
			System.err.println("getUnitPeriod: actor "+aref.toString()+" not found");
			e.printStackTrace();
		}
		return unitPeriod;
	}

}
