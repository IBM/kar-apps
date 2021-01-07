package com.ibm.research.reefer.simulator;

import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;


/**
 * A simple test that an actor can call a service
 */
@Actor
public class ActorCallsService extends BaseActor {

	@Activate
	public void initState() {
	}

	@Deactivate
	public void saveState() {
	}

	@Remote
	public JsonValue setUnitPeriod(JsonValue num) {
		//return (JsonValue)Kar.call("simservice", "simulator/setunitperiod", num);
		return (JsonValue)Kar.Services.call("simservice", "simulator/setunitperiod", num);
	}

//TODO enable this routine when kar-java supports GET
//	@Remote
//	public JsonValue getUnitPeriod() {
//		return (JsonValue)Kar.call("simservice", "simulator/getunitperiod");
//	}

}
