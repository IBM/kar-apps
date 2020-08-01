package com.ibm.research.reefer.simulator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map.Entry;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;

public class OrderThread extends Thread {
	boolean running = true;
	boolean interrupted = false;
	boolean oneshot = false;
	int threadloops = 0;
	int ordertarget;
	JsonValue currentDate = Json.createValue("");
	JsonValue futureVoyages;
	Instant today;
	//TODO make orderPerDay configurable
	int ordersPerDay = 1;
	int ordersDoneToday = 0;

	public void run() {
//		synchronized (SimulatorService.ordertarget) {
    		if (0 == SimulatorService.ordertarget.intValue()) {
    			oneshot = true;
    		}
//		}

		Thread.currentThread().setName("orderthread");
		SimulatorService.orderthreadcount.incrementAndGet();
    	System.out.println("orderthread: started threadid="+Thread.currentThread().getId()+" ... LOUD HORN");

    	// grab upcoming voyages

    	while (running) {
        	if (!oneshot) {
        		System.out.println("orderthread: "+Thread.currentThread().getId()+": running "+ ++threadloops);
        	}

        	if (! SimulatorService.reeferRestRunning.get()) {
        		System.out.println("orderthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
        	}
        	else {
        		// Make sure currentDate is set
        		if (null == SimulatorService.currentDate.get()) {
            		Response response = Kar.restPost("reeferservice", "time/currentDate", JsonValue.NULL);
            		currentDate = response.readEntity(JsonValue.class);
            		SimulatorService.currentDate.set(currentDate);
        		}

        		// If new date ...
        		// pull fresh list of voyages within the order window
        		// compute the total order capacity, "ordercap" to be made today for each voyage
        		// set the loop count for max number of orders to make for each voyage, "ordersPerDay"
        		if ( oneshot || ! currentDate.equals((JsonValue) SimulatorService.currentDate.get())) {
        			synchronized (SimulatorService.voyageFreeCap) {
        				// clear so any order update between now and when the map is recreated are not lost 
        				SimulatorService.voyageFreeCap.clear();
					}
            		// ... fetch all future voyages leaving in the next N days
        	    	currentDate = (JsonValue) SimulatorService.currentDate.get();
        	    	today = Instant.parse(currentDate.toString().replaceAll("^\"|\"$", ""));
        	    	Instant tomorrow = today.plus(1, ChronoUnit.DAYS);
        	    	//TODO make window size a control variable
        	    	int windowsize = 7;
        	    	Instant endday = tomorrow.plus(windowsize, ChronoUnit.DAYS);
        	    	JsonObject message = Json.createObjectBuilder()
        					.add("startDate", tomorrow.toString())
        					.add("endDate", endday.toString())
        					.build();

        			Response response = Kar.restPost("reeferservice", "voyage/inrange", message);
            		futureVoyages = response.readEntity(JsonValue.class);
            		System.out.println("orderthread: received "+futureVoyages.asJsonArray().size()+" future voyages");

            		// ... create MAP of target voyages with computed freecap
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

        	                // set target utilization threshold
        	                int ordertarget = 85;
        	                if (!oneshot) {
        	                	ordertarget = SimulatorService.ordertarget.intValue(); 
        	                }
        	                // compute ordercap for the day
    	                	double d_ordercap = (ordertarget*maxcap/100.0-(maxcap-freecap))/daysbefore;
        	                int ordercap = (int) Math.ceil(d_ordercap);
        	                ordersDoneToday = 0;

        	                // fill map, picking up new freecap values since map was cleared
                			if (SimulatorService.voyageFreeCap.containsKey(id)) {
                				SimulatorService.voyageFreeCap.get(id).setDaysBefore(daysbefore);
                				SimulatorService.voyageFreeCap.get(id).setMaxCapacity(maxcap);
                				SimulatorService.voyageFreeCap.get(id).setOrderCapacity(ordercap);
                				SimulatorService.voyageFreeCap.get(id).setUtilization(utilization);
                			}
                			else {
                				SimulatorService.voyageFreeCap.put(id, new FutureVoyage(daysbefore, maxcap, freecap, ordercap, utilization));
                			}
                		}
        			}
        			System.out.println("orderthread: dumping voyageFreeCap MAP ----------");
        			SimulatorService.voyageFreeCap.forEach((key, value) -> System.out.println("orderthread "+key + " "
        			+ value.toString()));
        		}

        		if (ordersPerDay > ordersDoneToday++) {
            		// create one order for every voyage below threshold
            		for (Entry<String, FutureVoyage> entry : SimulatorService.voyageFreeCap.entrySet()) {
            		    //System.out.println(entry.getKey() + "/" + entry.getValue());
            		    int freeTarget = entry.getValue().maxCapacity*(100-ordertarget)/100;
            		    if (entry.getValue().freeCapacity <= freeTarget) {
                    		// divide orderCap into specified number of orders per day
            		    	int ordersize = (entry.getValue().orderCapacity*1000)/ordersPerDay;
            		    	System.out.println("orderthread: create order size="+ordersize+" for "+entry.getKey()+" freeTarget="+freeTarget);
            		    	JsonObject order = Json.createObjectBuilder()
                					.add("voyageId", entry.getKey())
                					.add("product", "Bananas")
                					.add("productQty", ordersize)
                					.build();
            		    	Response response = Kar.restPost("reeferservice", "orders", order);
            		    	//TODO check response for errors?
            		    }
            		    else {
            		    	System.out.println("orderthread: no order for "+entry.getKey()+" freeTarget="+freeTarget);
            		    }
            		}
        		}
        	}

        	// sleep if not a oneshot order command
    		if (! oneshot) {
            	try {
            		// finish orders in 1/2 day
            		Thread.sleep(1000*SimulatorService.unitdelay.intValue()/(2*ordersPerDay));
            	} catch (InterruptedException e) {
            		System.out.println("orderthread: Interrupted Thread "+Thread.currentThread().getId());
            	    interrupted = true;
            	}
    		}

        	// check if auto mode should be turned off
        	synchronized (SimulatorService.ordertarget) {
        		if (0 == SimulatorService.ordertarget.intValue() || 0 == SimulatorService.unitdelay.intValue() || oneshot) {
        			System.out.println("orderthread: Stopping Thread "+Thread.currentThread().getId()+" LOUD HORN");
        			running = false;

        			if (0 < SimulatorService.orderthreadcount.decrementAndGet()) {
        				System.err.println("orderthread: we have an extra ship thread running!");
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

