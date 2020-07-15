package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
	private final static AtomicInteger unitdelay = new AtomicInteger(0);
	private final static AtomicInteger shipthreadcount = new AtomicInteger(0);
	private final static AtomicBoolean reeferRestRunning = new AtomicBoolean(false);
	private Thread shipthread;

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
		JsonNumber newval = (((JsonNumber)value).intValue() > 0) ? (JsonNumber)value : (JsonNumber)Json.createValue(0);
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

//	The Ship Simulator thread
//	  1. tell REST to update world time
//	  2. request from REST information about all active voyages
//	  3. send ship position to all active voyage actors
//    4. tell order simulator the new time
//    5. sleep for UnitDelay seconds
//    6. check auto mode enabled

	public class ShipThread extends Thread {
		boolean running = true;
		boolean interrupted = false;
		boolean oneshot = false;
		int loopcnt = 0;

		public void run() {
			synchronized (unitdelay) {
        		if (0 == unitdelay.intValue()) {
        			oneshot = true;
        		}
			}

			Thread.currentThread().setName("shipthread");
			shipthreadcount.incrementAndGet();
        	System.out.println("started threadid="+Thread.currentThread().getId()+" ... LOUD HORN");
	        while (running) {
	        	if (!oneshot) {
	        		System.out.println("shipthread "+Thread.currentThread().getId()+": running "+ ++loopcnt);
	        	}

	        	if (!reeferRestRunning.get()) {
	        		System.out.println("reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
	        	}
	        	else {
		        	// tell REST to advance time
	        		Response response = Kar.restPost("reeferservice", "time/advance", JsonValue.NULL);
	        		JsonValue currentTime = response.readEntity(JsonValue.class);
	        		System.out.println("New time = "+currentTime.toString());

	        		// fetch all active voyages from REST
	        		response = Kar.restGet("reeferservice", "voyage/active");
	        		JsonValue activeVoyages = response.readEntity(JsonValue.class);

	        		// send ship positions to all active voyages
	        		int nv = 0;
	        		for (JsonValue v : activeVoyages.asJsonArray()) {
	        			String id = v.asJsonObject().getString("id");
	        			int daysAtSea = v.asJsonObject().get("route").asJsonObject().getInt("daysAtSea");
	        			JsonObject message = Json.createObjectBuilder()
	        					.add("daysAtSea", daysAtSea)
//	        					.add("currentDate", currentTime)
	        					.build();
	        			System.out.println("shipthread updates voyageid: "+id+ " with "+message.toString());
	        			Kar.actorCall(actorRef("voyage",id), "changePosition", message);
	        			nv++;
	        		}
	        		System.out.println("shipthread updated "+nv+" active voyages");

	        		//TODO tell order simulator the new time
	        	}

	        	try {
	        		Thread.sleep(1000*unitdelay.intValue());
	        	} catch (InterruptedException e) {
	        		System.out.println("Interrupted Ship Thread "+Thread.currentThread().getId());
	        	    interrupted = true;
	        	}

	        	// check if auto mode turned off
	        	synchronized (unitdelay) {
	        		if (0 == unitdelay.intValue() || interrupted || oneshot) {
	        			unitdelay.set(0);
	        			System.out.println("Stopping Ship Thread "+Thread.currentThread().getId()+" LOUD HORN");
	        			running = false;

	        			if (0 < shipthreadcount.decrementAndGet()) {
	        				System.err.println("we have an extra ship thread running!");
	        			}

	        			// check for threads leftover from a hot method replace
	        			Set<Thread> threadset = Thread.getAllStackTraces().keySet();
	        			for(Thread thread : threadset){
	        				if (thread.getName().equals("shipthread") && thread.getId() != Thread.currentThread().getId()) {
	        					System.out.println("shipthread: killing leftover ship threadid="+thread.getId());
	        					thread.interrupt();
	        				}
	        			}
	        		}
	        	}
	        }
	    }
	}
}
