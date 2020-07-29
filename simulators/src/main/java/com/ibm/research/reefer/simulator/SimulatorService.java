package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;

public class SimulatorService {

	private Map<String,JsonValue> persistentData;
	private ActorRef aref = actorRef("simhelper","simservice");
	public final static AtomicInteger unitdelay = new AtomicInteger(0);
	public final static AtomicInteger shipthreadcount = new AtomicInteger(0);
	public final static AtomicBoolean reeferRestRunning = new AtomicBoolean(false);
	public final static AtomicInteger ordertarget = new AtomicInteger(0);
	public final static AtomicInteger orderthreadcount = new AtomicInteger(0);
	public final static AtomicReference<JsonValue> currentDate = new AtomicReference<JsonValue>();
	public final static Map<String,FutureVoyage> voyageFreeCap = new HashMap<String,FutureVoyage>();
	private Thread shipthread;
	private Thread orderthread;

	// constructor
	public SimulatorService () {
		System.out.println("SimulatorService constructor!");
		persistentData = new HashMap<String,JsonValue>();
		persistentData.putAll((JsonObject)actorCall(aref, "getAll"));
	}

	public JsonValue toggleReeferRest() {
		reeferRestRunning.set(! reeferRestRunning.get());
		return Json.createValue(reeferRestRunning.toString());
	}

	// local utility to retrieve cached value
	private JsonValue get(JsonValue key) {
		return persistentData.get(((JsonString)key).getString());
	}

	// local utility to update local cache and persistent state
	private JsonValue set(JsonValue key, JsonValue value) {
		persistentData.put(((JsonString)key).getString(), value);
		return actorCall(aref, "set", key, value);
	}

	// -------------------------------- Ship Thread Controller --------------------------------

	public JsonNumber getUnitDelay() {
		try {
			JsonValue jv = actorCall(aref, "get", (JsonValue)Json.createValue("UnitDelay"));
			if (jv.toString().equals("null") ) {
				jv = actorCall(aref, "set", (JsonValue)Json.createValue("UnitDelay"), (JsonValue)Json.createValue(0));
				return Json.createValue(0);
			}
			else {
				return (JsonNumber) jv;
			}
		} catch (ActorMethodNotFoundException e) {
			System.err.println("SimulatorService: actor "+aref.toString()+" not found");
			e.printStackTrace();
			return 	Json.createValue(-1);
		}
	}

	public JsonValue setUnitDelay(JsonValue value) {
		JsonNumber newval;
		if (JsonValue.ValueType.OBJECT == value.getValueType()) {
			newval = ((JsonObject)value).getJsonNumber("value");
		}
		else {
			newval = Json.createValue(((JsonNumber)value).intValue());
		}
		newval = newval.intValue() > 0 ? newval : (JsonNumber)Json.createValue(0);
		synchronized (unitdelay) {
			// if unitdelay > 0 then Ship thread is running.
			// if running and newval == 0 then interrupt thread
			if (0 < unitdelay.intValue() || 0 == newval.intValue()) {
				if (0 < unitdelay.intValue() && 0 == newval.intValue()) {
					if (null != shipthread) {
						shipthread.interrupt();
						shipthread = null;
					}
				}
				unitdelay.set(newval.intValue());
				return Json.createValue("accepted");
			}

			// this is a request to start auto mode
			// is a thread already running?
			if (0 < shipthreadcount.get()) {
				return Json.createValue("rejected");
			}
			//TODO set any null but required config values to their defaults");

			// save new delay
			unitdelay.set(newval.intValue());
			// start the Ship thread
			(shipthread = new ShipThread()).start();

			return Json.createValue("accepted");
		}
	}

	// Manual oneshot. Runs only when unitdelay == 0
	public JsonValue advanceTime() {
    	synchronized (unitdelay) {
    		if (0 == unitdelay.intValue() && 0 == shipthreadcount.get()) {
    			(new ShipThread()).start();
    			return Json.createValue("accepted");
    		}
    		System.out.println("advanceTime rejected: unitdelay="+unitdelay.get()+" shipthreadcount="+shipthreadcount.get());
    		return Json.createValue("rejected");
    	}
	}


	// -------------------------------- Order Thread Controller --------------------------------

	public JsonNumber getOrderTarget() {
		try {
			JsonValue jv = actorCall(aref, "get", (JsonValue)Json.createValue("OrderTarget"));
			if (jv.toString().equals("null") ) {
				jv = actorCall(aref, "set", (JsonValue)Json.createValue("OrderTarget"), (JsonValue)Json.createValue(0));
				return Json.createValue(0);
			}
			else {
				return (JsonNumber) jv;
			}
		} catch (ActorMethodNotFoundException e) {
			System.err.println("SimulatorService: actor "+aref.toString()+" not found");
			e.printStackTrace();
			return 	Json.createValue(-1);
		}
	}

	public JsonValue setOrderTarget(JsonValue value) {
		JsonNumber newval;
		if (JsonValue.ValueType.OBJECT == value.getValueType()) {
			newval = ((JsonObject)value).getJsonNumber("value");
		}
		else {
			newval = Json.createValue(((JsonNumber)value).intValue());
		}
		newval = newval.intValue() > 0 ? newval : (JsonNumber)Json.createValue(0);
		newval = newval.intValue() < 85 ? newval : (JsonNumber)Json.createValue(85);
		synchronized (ordertarget) {
			// if ordertarget > 0 then Order thread is running.
			// if running and newval == 0 then interrupt thread
			if (0 < ordertarget.intValue() || 0 == newval.intValue()) {
				if (0 < ordertarget.intValue() && 0 == newval.intValue()) {
					if (null != orderthread) {
						orderthread.interrupt();
						orderthread = null;
					}
				}
				ordertarget.set(newval.intValue());
				return Json.createValue("accepted");
			}

			// this is a request to start auto mode
			// is a thread already running?
			if (0 < orderthreadcount.get()) {
				return Json.createValue("rejected");
			}
			//TODO set any null but required config values to their defaults");

			// save new Target
			ordertarget.set(newval.intValue());
			// start the Order thread
			(orderthread = new OrderThread()).start();

			return Json.createValue("accepted");
		}
	}

	// Manual oneshot. Runs only when ordertarget == 0
	public JsonValue createOrder() {
    	synchronized (ordertarget) {
    		if (0 == ordertarget.intValue() && 0 == orderthreadcount.get()) {
    			(new OrderThread()).start();
    			return Json.createValue("accepted");
    		}
    		System.out.println("advanceTime rejected: ordertarget="+ordertarget.get()+" orderthreadcount="+orderthreadcount.get());
    		return Json.createValue("rejected");
    	}
	}

	// Update voyage capacity
	public void updateVoyageCapacity(JsonValue capacity) {
		String vid = capacity.asJsonObject().getString("voyageId");
		int freecap = capacity.asJsonObject().getInt("freeCapacity");
	}

}
