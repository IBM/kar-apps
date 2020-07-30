package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorRef;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonString;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;

public class OrderThread extends Thread {
	boolean running = true;
	boolean interrupted = false;
	boolean oneshot = false;
	int loopcnt = 0;
	int ordertarget;
	JsonValue currentDate = Json.createValue("");
	JsonValue futureVoyages;
	Instant today;
	//TODO make orderPerDay configurable
	int ordersPerDay = 1;

	public void run() {
		synchronized (SimulatorService.ordertarget) {
    		if (0 == SimulatorService.ordertarget.intValue()) {
    			oneshot = true;
    		}
		}

		Thread.currentThread().setName("orderthread");
		SimulatorService.orderthreadcount.incrementAndGet();
    	System.out.println("started Order threadid="+Thread.currentThread().getId()+" ... LOUD HORN");

    	// grab upcoming voyages

    	while (running) {
        	if (!oneshot) {
        		System.out.println("orderthread "+Thread.currentThread().getId()+": running "+ ++loopcnt);
        	}

        	if (! SimulatorService.reeferRestRunning.get()) {
        		System.out.println("reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
        	}
        	else {
        		// Make sure currentDate is set
        		if (null == SimulatorService.currentDate.get()) {
            		Response response = Kar.restPost("reeferservice", "time/currentDate", JsonValue.NULL);
            		JsonValue currentDate = response.readEntity(JsonValue.class);
            		SimulatorService.currentDate.set(currentDate);
        		}

        		// If new date ...
        		if ( ! currentDate.equals((JsonValue) SimulatorService.currentDate.get())) {
        			synchronized (SimulatorService.voyageFreeCap) {
        				// clear so any order update between now and when the map is recreated are not lost 
        				SimulatorService.voyageFreeCap.clear();
					}
            		// ... fetch all future voyages leaving in the next N days
        	    	JsonValue currentDate = (JsonValue) SimulatorService.currentDate.get();
        	    	today = Instant.parse(currentDate.toString().replaceAll("^\"|\"$", ""));
        	    	Instant tomorrow = today.plus(1, ChronoUnit.DAYS);
        	    	//TODO make 21 a control variable
        	    	Instant endday = tomorrow.plus(21, ChronoUnit.DAYS);
        	    	JsonObject message = Json.createObjectBuilder()
        					.add("startDate", tomorrow.toString())
        					.add("endDate", endday.toString())
        					.build();

        			Response response = Kar.restPost("reeferservice", "voyage/inrange", message);
            		futureVoyages = response.readEntity(JsonValue.class);
            		System.out.println("orderthread received "+futureVoyages.asJsonArray().size()+" future voyages");

            		// ... create MAP of target voyages
        			synchronized (SimulatorService.voyageFreeCap) {
                		for (JsonValue v : futureVoyages.asJsonArray()) {
                			String id = v.asJsonObject().getString("id");
        	                Instant sd = Instant.parse(v.asJsonObject().getString("sailDateObject")
        	                		.replaceAll("^\"|\"$", ""));
        	                int daysbefore = (int) ChronoUnit.DAYS.between(today,sd);
        	                int maxcap = v.asJsonObject().get("route").asJsonObject().get("vessel")
        	                		.asJsonObject().getInt("maxCapacity");
        	                int freecap = v.asJsonObject().get("route").asJsonObject().get("vessel")
        	                		.asJsonObject().getInt("freeCapacity");
        	                int utilization = (maxcap-freecap)*100/maxcap;
        	                int ordertarget = 85;
        	                if (!oneshot) {
        	                	ordertarget = SimulatorService.ordertarget.intValue(); 
        	                }
    	                	double d_ordercap = (ordertarget*maxcap/100.0-(maxcap-freecap))/daysbefore;
        	                int ordercap = (int) Math.ceil(d_ordercap);
//                			System.out.println("orderthread "+id+ " departs in "+daysbefore+" days, freeCap="+freecap+","
//                					+" %full="+utilization+", ordercap="+ordercap);
                			if (SimulatorService.voyageFreeCap.containsKey(id)) {
                				SimulatorService.voyageFreeCap.get(id).setDaysBefore(daysbefore);
                				SimulatorService.voyageFreeCap.get(id).setMaxCapacity(maxcap);
                				SimulatorService.voyageFreeCap.get(id).setOrderCapacity(ordercap);
                			}
                			else {
                				SimulatorService.voyageFreeCap.put(id, new FutureVoyage(daysbefore, maxcap, freecap, ordercap));
                			}
                		}
        			}
        			System.out.println("orderthread dumping voyageFreeCap MAP ----------");
        			SimulatorService.voyageFreeCap.forEach((key, value) -> System.out.println("orderthread "+key + " "
        			+ value.toString()));
        		}

        		// create one order for every voyage below threshold
        		for (Entry<String, FutureVoyage> entry : SimulatorService.voyageFreeCap.entrySet()) {
        		    //System.out.println(entry.getKey() + "/" + entry.getValue());
        		    int freeTarget = entry.getValue().maxCapacity*(100-SimulatorService.ordertarget.intValue())/100;
        		    if (entry.getValue().freeCapacity <= freeTarget) {
                		// divide orderCap into specified number of orders per day
        		    	int ordersize = (entry.getValue().orderCapacity*1000)/ordersPerDay;
        		    	System.out.println("orderthread create order size="+ordersize+" for "+entry.getKey());
        		    	JsonObject order = Json.createObjectBuilder()
            					.add("voyageId", entry.getKey())
            					.add("product", "Bananas")
            					.add("productQty", ordersize)
            					.build();
        		    	Response response = Kar.restPost("reeferservice", "orders", order);
//        		    	System.out.println("orderthread order reponse: " + response.toString());
        		    }
        		}
        	}

    		//TODO interrupt this thread when UnitDelay changes or on a new day
        	try {
        		// finish orders in 1/2 day
        		Thread.sleep(1000*SimulatorService.unitdelay.intValue()/(2*ordersPerDay));
        	} catch (InterruptedException e) {
        		System.out.println("Interrupted Order Thread "+Thread.currentThread().getId());
        	    interrupted = true;
        	}

        	// check if auto mode turned off
        	synchronized (SimulatorService.ordertarget) {
        		if (0 == SimulatorService.ordertarget.intValue() || oneshot) {
        			System.out.println("Stopping Order Thread "+Thread.currentThread().getId()+" LOUD HORN");
        			running = false;

        			if (0 < SimulatorService.orderthreadcount.decrementAndGet()) {
        				System.err.println("we have an extra ship thread running!");
        			}

        			// check for threads leftover from a hot method replace
        			Set<Thread> threadset = Thread.getAllStackTraces().keySet();
        			for(Thread thread : threadset){
        				if (thread.getName().equals("orderthread") && thread.getId() != Thread.currentThread().getId()) {
        					System.out.println("orderthread: killing leftover order threadid="+thread.getId());
        					thread.interrupt();
        				}
        			}
        		}
        	}
        }
    }
}

