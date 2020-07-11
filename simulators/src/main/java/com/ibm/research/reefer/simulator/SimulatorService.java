package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
	private final static AtomicInteger unitdelay = new AtomicInteger(0);
	private final static AtomicInteger shipthreadcount = new AtomicInteger(0);

	// constructor
	public SimulatorService () {
		System.out.println("SimulatorService constructor!");
		persistentData = new HashMap<String,JsonValue>();
		persistentData.putAll((JsonObject)actorCall(aref, "getAll"));

// attempt to clean up running threads on a hot method replace
// but hot method replace is not working with singleton SimulatorResource
//		Set<Thread> threadset = Thread.getAllStackTraces().keySet();
//		for(Thread thread : threadset){
//		    if (thread.getName().equals("shipthread")) {
//		    	System.out.println("killing leftover ship threadid="+thread.getId());
//		        thread.interrupt();
//		    }
//		}
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
				return Json.createValue(-2);
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
			// if newval == 0 then will not start thread.
			// So, just update value.
			if (0 < unitdelay.intValue() || 0 == newval.intValue()) {
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
			(new ShipThread()).start();

			return Json.createValue("accepted");
		}
	}

	// Only runs when unitdelay == 0
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
        	System.out.println("started thread #"+shipthreadcount.get()+" with threadid="+Thread.currentThread().getId()+" ... LOUD HORN");
	        while (running) {
	        	if (oneshot) {
		        	System.out.println("shipthread: oneshot");
	        	}
	        	else {
	        		System.out.println("shipthread: running "+ ++loopcnt);
	        	}

	        	//TODO tell REST to advance time
	        	//TODO fetch all active voyages from REST
	        	//TODO send ship positions to all active voyages
	        	//TODO tell order simulator the new time

	        	try {
	        		Thread.sleep(1000*unitdelay.intValue());
	        	} catch (InterruptedException e) {
	        		System.out.println("Interrupted Ship Thread "+Thread.currentThread().getId());
	        	    interrupted = true;
	        	}

	        	// check if auto mode turned off
	        	synchronized (unitdelay) {
	        		if (0 == unitdelay.intValue() || interrupted || oneshot) {
	        			if (0 < shipthreadcount.decrementAndGet()) {
	        				System.err.println("we have an extra ship thread running!");
	        			}
	        			unitdelay.set(0);
	        			System.out.println("Stopping Ship Thread "+Thread.currentThread().getId()+" LOUD HORN");
	        			running = false;
	        		}
	        	}
	        }
	    }
	}
}
