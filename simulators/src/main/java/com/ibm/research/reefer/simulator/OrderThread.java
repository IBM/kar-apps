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
  boolean oneshot = false;
  int threadloops = 0;
  int ordertarget;
  JsonValue currentDate;
  JsonValue futureVoyages;
  Instant today;

  int ordersDoneToday = 0;
  int ordersPerDay = 0;
  long dayEndTime;
  long totalOrderTime;
  boolean startup = true;
  

  public void run() {
    if (0 == SimulatorService.unitdelay.intValue()
            || 0 == SimulatorService.ordertarget.intValue()) {
      oneshot = true;
    }

    Thread.currentThread().setName("orderthread");
    SimulatorService.orderthreadcount.incrementAndGet();
    System.out.println(
            "orderthread: started threadid=" + Thread.currentThread().getId() + " ... LOUD HORN");

    if (SimulatorService.reeferRestRunning.get()) {
      // Make sure currentDate is set
      if (null == SimulatorService.currentDate.get()) {
        Response response = Kar.restPost("reeferservice", "time/currentDate", JsonValue.NULL);
        currentDate = response.readEntity(JsonValue.class);
        SimulatorService.currentDate.set(currentDate);
      }
      else {
        currentDate = (JsonValue) SimulatorService.currentDate.get();
      }
    }


    while (running) {
      if (!oneshot) {
//        		System.out.println("orderthread: "+Thread.currentThread().getId()+": running "+ ++threadloops);
      }

      if (!SimulatorService.reeferRestRunning.get()) {
//        		System.out.println("orderthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
      } else {

        // If new date ...
        // pull fresh list of voyages within the order window
        // compute the total order capacity, "ordercap" to be made today for each voyage
        // set the loop count for max number of orders to make for each voyage, "ordersPerDay"
        JsonValue date = (JsonValue) SimulatorService.currentDate.get();
        if (startup || oneshot || !currentDate.equals(date)) {
          dayEndTime = System.currentTimeMillis() + 1000* SimulatorService.unitdelay.intValue();
          if (ordersDoneToday < ordersPerDay) {
            System.out.println("orderthread: " + ordersDoneToday + " of " + ordersPerDay + " completed yesterday");
          }
          System.out.println("orderthread: new day = " + date.toString());
          ordersDoneToday = 0;
          totalOrderTime = 0;
          startup = false;
          currentDate = date;

          synchronized (SimulatorService.voyageFreeCap) {
            // clear so any order update between now and when the map is recreated are not lost
            SimulatorService.voyageFreeCap.clear();
          }

          // pick up any changes
          ordersPerDay = SimulatorService.orderupdates.intValue();
          if (ordersPerDay < 1) {
            ordersPerDay = 1; 
          }

          // ... fetch all future voyages leaving in the next N days
          today = Instant.parse(currentDate.toString().replaceAll("^\"|\"$", ""));

          int windowsize = SimulatorService.orderwindow.intValue();
          Instant endday = today.plus(windowsize, ChronoUnit.DAYS);
          JsonObject message = Json.createObjectBuilder().add("startDate", today.toString())
                  .add("endDate", endday.toString()).build();

          Response response = Kar.restPost("reeferservice", "voyage/inrange", message);
          futureVoyages = response.readEntity(JsonValue.class);
          System.out.println("orderthread: received " + futureVoyages.asJsonArray().size()
                  + " future voyages");

          // ... create MAP of target voyages with computed freecap
          synchronized (SimulatorService.voyageFreeCap) {
            for (JsonValue v : futureVoyages.asJsonArray()) {
              String id = v.asJsonObject().getString("id");
              Instant sd = Instant.parse(
                      v.asJsonObject().getString("sailDateObject").replaceAll("^\"|\"$", ""));
              int daysbefore = (int) ChronoUnit.DAYS.between(today, sd);
              int maxcap = v.asJsonObject().get("route").asJsonObject().get("vessel").asJsonObject()
                      .getInt("maxCapacity");
              int freecap = v.asJsonObject().get("route").asJsonObject().get("vessel")
                      .asJsonObject().getInt("freeCapacity");
              int utilization = (maxcap - freecap) * 100 / maxcap;

              // set target utilization threshold
              int ordertarget = SimulatorService.ordertarget.intValue() > 0
                      ? SimulatorService.ordertarget.intValue()
                      : 85;

              // compute ordercap for the day
              double d_ordercap = 0;
              if (daysbefore > 0) {
                d_ordercap = (ordertarget * maxcap / 100.0 - (maxcap - freecap)) / daysbefore;
              }
              int ordercap = (int) Math.ceil(d_ordercap);

              // fill map, picking up new freecap values since map was cleared
              if (SimulatorService.voyageFreeCap.containsKey(id)) {
                SimulatorService.voyageFreeCap.get(id).setDaysBefore(daysbefore);
                SimulatorService.voyageFreeCap.get(id).setMaxCapacity(maxcap);
                SimulatorService.voyageFreeCap.get(id).setOrderCapacity(ordercap);
                SimulatorService.voyageFreeCap.get(id).setUtilization(utilization);
              } else {
                SimulatorService.voyageFreeCap.put(id,
                        new FutureVoyage(daysbefore, maxcap, freecap, ordercap, utilization));
              }
            }
          }
          System.out.println("orderthread: dumping voyageFreeCap MAP ----------");
          SimulatorService.voyageFreeCap.forEach((key, value) -> System.out
                  .println("orderthread " + key + " " + value.toString()));
        }

        if (ordersPerDay > ordersDoneToday++) {
          long snapshot = System.currentTimeMillis();
          // create one order for every voyage below threshold
          for (Entry<String, FutureVoyage> entry : SimulatorService.voyageFreeCap.entrySet()) {
            // System.out.println(entry.getKey() + "/" + entry.getValue());
            if (entry.getValue().orderCapacity > 0) {
              // divide orderCap into specified number of orders per day
              int ordersize = (entry.getValue().orderCapacity * 1000) / ordersPerDay;
              System.out.println(
                      "orderthread: create order size=" + ordersize + " for " + entry.getKey());
              JsonObject order = Json.createObjectBuilder().add("voyageId", entry.getKey())
                      .add("customerId", "simulator").add("product", "pseudoBanana")
                      .add("productQty", ordersize).build();
              Response response = Kar.restPost("reeferservice", "orders", order);
              JsonValue rsp = response.readEntity(JsonValue.class);
              if (null == rsp.asJsonObject().getString("voyageId")) {
                System.err.println("orderthread: error submitting order: "+ order.toString());
              }
            }
//            		    else {
//            		    	System.out.println("orderthread: no order for "+entry.getKey());
//            		    }
          }
          totalOrderTime += System.currentTimeMillis() - snapshot;
        }
      }

      // sleep if not a oneshot order command
      if (!oneshot) {
        try {
          long timeToSleep = 990 * SimulatorService.unitdelay.intValue();
          // compute next sleep time
          if (SimulatorService.reeferRestRunning.get()) {
            long timeRemaining = dayEndTime - System.currentTimeMillis();
            long orderTimeRemaining = totalOrderTime/ordersDoneToday * (ordersPerDay-ordersDoneToday);
            if (timeRemaining < 1 && orderTimeRemaining > 0) {
              timeToSleep = 10; 
            }
            else if (orderTimeRemaining > 0 && timeRemaining > 0) {
              timeToSleep = (timeRemaining - orderTimeRemaining) / (1 + ordersPerDay - ordersDoneToday);
              timeToSleep = (timeToSleep < 10) ? 10 : timeToSleep;
            }
            else {
              timeToSleep = 1000 * SimulatorService.unitdelay.intValue();
            }
            System.out.println("orderthread: timeRemaining="+timeRemaining+ " orderTimeRemaining="+orderTimeRemaining+" totalOrderTime="+totalOrderTime+" ordersDoneToday="+ordersDoneToday+" timeToSleep="+timeToSleep);
          }
          Thread.sleep(timeToSleep);
        } catch (InterruptedException e) {
//            		System.out.println("orderthread: Interrupted Thread "+Thread.currentThread().getId());
        }
      }

      // check if auto mode should be turned off
      synchronized (SimulatorService.ordertarget) {
        if (0 == SimulatorService.ordertarget.intValue()
                || 0 == SimulatorService.unitdelay.intValue() || oneshot) {
          System.out.println(
                  "orderthread: Stopping Thread " + Thread.currentThread().getId() + " LOUD HORN");
          running = false;

          if (0 < SimulatorService.orderthreadcount.decrementAndGet()) {
            System.err.println("orderthread: we have an extra ship thread running!");
          }

          // check for threads leftover from a hot method replace
          Set<Thread> threadset = Thread.getAllStackTraces().keySet();
          for (Thread thread : threadset) {
            if (thread.getName().equals("orderthread")
                    && thread.getId() != Thread.currentThread().getId()) {
              System.out.println("orderthread: killing leftover order threadid=" + thread.getId());
              thread.interrupt();
            }
          }
        }
      }
    }
  }
}
