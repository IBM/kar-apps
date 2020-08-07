package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.actor.ActorRef;

public class SimulatorService {

	private Map<String,JsonValue> persistentData;
	private ActorRef aref = actorRef("simhelper","simservice");
	public final static AtomicInteger unitdelay = new AtomicInteger(0);
	public final static AtomicInteger shipthreadcount = new AtomicInteger(0);
	public final static AtomicBoolean reeferRestRunning = new AtomicBoolean(true);
	public final static AtomicInteger ordertarget = new AtomicInteger(0);
	public final static AtomicInteger orderthreadcount = new AtomicInteger(0);
	public final static AtomicReference<JsonValue> currentDate = new AtomicReference<JsonValue>();
	public final static Map<String,FutureVoyage> voyageFreeCap = new HashMap<String,FutureVoyage>();
	private Thread shipthread;
	private Thread orderthread;

	// constructor
	public SimulatorService () {
//		System.out.println("SimulatorService constructor!");
	}

	public JsonValue toggleReeferRest() {
		reeferRestRunning.set(! reeferRestRunning.get());
		return Json.createValue(reeferRestRunning.toString());
	}

	// local utility to retrieve cached value
	private JsonValue get(JsonValue key) {
		if (null == persistentData) {
			persistentData = new HashMap<String,JsonValue>();
			persistentData.putAll((JsonObject)actorCall(aref, "getAll"));
		}
		return persistentData.get(((JsonString)key).getString());
	}

	// local utility to update local cache and persistent state
	private JsonValue set(JsonValue key, JsonValue value) {
		if (null == persistentData) {
			persistentData = new HashMap<String,JsonValue>();
			persistentData.putAll((JsonObject)actorCall(aref, "getAll"));
		}
		persistentData.put(((JsonString)key).getString(), value);
		return actorCall(aref, "set", key, value);
	}

	// -------------------------------- Ship Thread Controller --------------------------------

	public JsonNumber getUnitDelay() {
		return Json.createValue(unitdelay.get());
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
					if (null != orderthread) {
						orderthread.interrupt();
						orderthread = null;
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

			// save new delay
			unitdelay.set(newval.intValue());
			// get persistent value of ordertarget
			JsonNumber ot = (JsonNumber)this.get(Json.createValue("ordertarget"));
			ordertarget.set(ot.intValue()); 
			// start the Ship thread
			(shipthread = new ShipThread()).start();

			// if auto order enabled, start that thread too
			if (0 < ordertarget.intValue()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				(orderthread = new OrderThread()).start();
			}

			return Json.createValue("accepted");
		}
	}

	// Manual oneshot. Runs only when unitdelay == 0
	public JsonValue advanceTime() {
    	synchronized (unitdelay) {
    		if (0 == unitdelay.intValue() && 0 == shipthreadcount.get()) {
    			// get persistent value of ordertarget
    			JsonNumber ot = (JsonNumber)this.get(Json.createValue("ordertarget"));
    			ordertarget.set(ot.intValue()); 
    			(new ShipThread()).start();
    			if (0 < ordertarget.intValue()) {
    				// call order oneshot as well
    				try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
    				(new OrderThread()).start();
    			}
    			return Json.createValue("accepted");
    		}
    		System.out.println("simulator: advanceTime rejected: unitdelay="+unitdelay.get()+" shipthreadcount="+shipthreadcount.get());
    		return Json.createValue("rejected");
    	}
	}


	// -------------------------------- Order Thread Controller --------------------------------

	public JsonNumber getOrderTarget() {
		JsonNumber ot = (JsonNumber)this.get(Json.createValue("ordertarget"));
		if (ot == null) {
			this.set(Json.createValue("ordertarget"), (JsonValue)Json.createValue(0));
			ordertarget.set(0); 
			return Json.createValue(0);
		}
		ordertarget.set(ot.intValue()); 
		return Json.createValue(ordertarget.get());
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
			// if ordertarget > 0 then Order thread is enabled
			// if running and newval == 0 then interrupt thread
			if (0 < ordertarget.intValue() || 0 == newval.intValue()) {
				if (0 < ordertarget.intValue() && 0 == newval.intValue()) {
					if (null != orderthread) {
						orderthread.interrupt();
						orderthread = null;
					}
				}
				ordertarget.set(newval.intValue());
				this.set(Json.createValue("ordertarget"), newval);
				System.out.println("simulator: ordertarget set="+newval.intValue());
				return Json.createValue("accepted");
			}

			// this is a request to start auto order mode
			// save new Target
			ordertarget.set(newval.intValue());
			this.set(Json.createValue("ordertarget"), newval);

			// start the Order thread if no thread already running and unitdelay>0
			if (0 == orderthreadcount.get() && 0 < unitdelay.intValue()) {
				(orderthread = new OrderThread()).start();
			}
			else {
				System.out.println("simulator: ordertarget set="+ordertarget.get()+" but thread not started. orderthreadcount="+
						orderthreadcount.get()+" unitdelay="+unitdelay.intValue());
			}

			return Json.createValue("accepted");
		}
	}

	// Manual oneshot. Runs only when orderthreadcount == 0
	public JsonValue createOrder() {
//    	synchronized (ordertarget) {
    		if (0 == orderthreadcount.get()) {
    			(orderthread = new OrderThread()).start();
    			return Json.createValue("accepted");
    		}
    		System.out.println("simulator: createOrder rejected: orderthreadcount="+orderthreadcount.get());
    		return Json.createValue("rejected");
    	}
//	}

	// wake up order thread if sleeping
	public void newDayForOrders() {
		if (null != orderthread) {
			orderthread.interrupt();
		}
	}

	// Update voyage capacity
	public void updateVoyageCapacity(JsonValue capacity) {
		String vid = capacity.asJsonObject().getString("voyageId");
		int freecap = capacity.asJsonObject().getInt("freeCapacity");
		synchronized (SimulatorService.voyageFreeCap) {
			if (voyageFreeCap.containsKey(vid)) {
				voyageFreeCap.get(vid).setFreeCapacity(freecap);
			}
			else {
				// The other values will be set later in the order thread,
				// if this voyage is within the simulator's time window
				voyageFreeCap.put(vid, new FutureVoyage(0, 0, freecap, 0, 0));
			}
		}
		System.out.println("simulator: updated freeCapacity to "+freecap+" for voyage "+vid);
	}

}
