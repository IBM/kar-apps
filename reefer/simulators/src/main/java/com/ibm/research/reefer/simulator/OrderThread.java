/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.reefer.simulator;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderThread extends Thread {
  boolean running = true;
  boolean oneshot = false;
  int threadloops = 0;
  int ordertarget;
  int daylength;
  JsonValue currentDate;
  JsonValue futureVoyages;
  Instant today;

  int updatesDoneToday = 0;
  int updatesPerDay = 0;
  int orderGroupsPerDay;
  int ordersExpectedToday;
  long dayEndTime;
  long totalOrderTime;
  boolean startup = true;
  private static final AtomicInteger ordersDoneToday = new AtomicInteger();
  private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(OrderThread.class.getName());
  private Thread ordersubthread1 = null;
  private Thread ordersubthread2 = null;
  private int threadswanted = 0;


  public void run() {
    if (0 == SimulatorService.unitdelay.intValue()
            || 0 == SimulatorService.ordertarget.intValue()) {
      oneshot = true;
    }

    // check for threads leftover from a hot method replace
    Set<Thread> threadset = Thread.getAllStackTraces().keySet();
    for (Thread thread : threadset) {
      if (thread.getName().equals("orderthread")) {
        logger.warning("orderthread: killing leftover order threadid=" + thread.getId());
        thread.interrupt();
      }
    }

    Thread.currentThread().setName("orderthread");
    SimulatorService.orderthreadcount.incrementAndGet();
    if (logger.isLoggable(Level.INFO)) {
      logger.info(
              "orderthread: started threadid=" + Thread.currentThread().getId() + " ... LOUD HORN");
    }

    // check for subthreads still running
    if ( SimulatorService.suborderthreadcounts.get(1-1) != 0 || SimulatorService.suborderthreadcounts.get(2-1) != 0) {
      logger.warning(String.format("orderthread: subthread counts = %d and %d",
              SimulatorService.suborderthreadcounts.get(1-1), SimulatorService.suborderthreadcounts.get(2-1) ));
      SimulatorService.orderthreadcount.decrementAndGet();
      return;
    }

    if (SimulatorService.reeferRestRunning.get()) {
      // Make sure currentDate is set
      if (null == SimulatorService.currentDate.get()) {
        logger.warning("orderthread: - at startup, SimulatorService.currentDate == null");

        try {
          Response response = Kar.Services.post(Constants.REEFERSERVICE, "time/currentDate",
                  JsonValue.NULL);
          currentDate = response.readEntity(JsonValue.class);
          SimulatorService.currentDate.set(currentDate);
        } catch (Exception e) {
          logger.warning(
                  "orderthread: unable to fetch current date from REST - cause:" + e.getMessage());
        }
      } else {
        currentDate = (JsonValue) SimulatorService.currentDate.get();
      }
    }

    // Order thread can be called as oneshot or stay free-running
    // if free-running it is interrupted on each new day
    // one or two sub-threads do the ordering. They terminate when orders are completed or are interrupted on new day if not
    while (running) {
      try {

        if (!SimulatorService.reeferRestRunning.get()) {
          logger.warning(
                  "orderthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
        } else {

          // If new day ...
          // Count any missed orders from yesterday
          // pull fresh list of voyages within the order window
          // compute the total order capacity, "ordercap" to be made today for each voyage ...
          // ... and the ordersize to use for individual orders
          // set the loop count for max number of order groups to make for each voyage, "updatesPerDay"
          // spawn sub-threads
          JsonValue date = (JsonValue) SimulatorService.currentDate.get();
          daylength = SimulatorService.unitdelay.intValue();
          if (startup || oneshot || !currentDate.equals(date)) {
            dayEndTime = System.currentTimeMillis() + 1000 * daylength;
            logger.fine("OrderThread: newday! ordersExpectedToday:"+ordersExpectedToday+" ordersDoneToday:"+ordersDoneToday.get());
            if (ordersDoneToday.get() < ordersExpectedToday) {
              SimulatorService.os.addMissed(ordersExpectedToday - ordersDoneToday.get());
              logger.warning("orderthread: " + ordersDoneToday.get() + " of " + ordersExpectedToday
                      + " submitted yesterday");
            }
            if (logger.isLoggable(Level.FINE)) {
              logger.fine("orderthread: new day = " + date.toString());
            }
            updatesDoneToday = 0;
            totalOrderTime = 0;
            ordersDoneToday.set(0);
            startup = false;
            currentDate = date;

            synchronized (SimulatorService.voyageFreeCap) {
              // clear so any order update between now and when the map is recreated are not lost
              SimulatorService.voyageFreeCap.clear();
            }

            // pick up any changes
            updatesPerDay = SimulatorService.orderupdates.intValue();
            if (updatesPerDay <= 1 || oneshot) {
              updatesPerDay = 1;
              orderGroupsPerDay = 1;
            }
            if (updatesPerDay > 1) {
              // generate orders in parallel threads
              orderGroupsPerDay = 2 * updatesPerDay;
            }

            // ... fetch all future voyages leaving in the next N days
            today = Instant.parse(currentDate.toString().replaceAll("^\"|\"$", ""));

            int windowsize = SimulatorService.orderwindow.intValue();
            // ignore voyages leaving today or tomorrow
            Instant startday = today.plus(2, ChronoUnit.DAYS);
            Instant endday = today.plus(2 + windowsize, ChronoUnit.DAYS);
            endday = endday.minusSeconds(1);
            JsonObject message = Json.createObjectBuilder().add("startDate", startday.toString())
                    .add("endDate", endday.toString()).build();

            try {
              Response response = Kar.Services.post(Constants.REEFERSERVICE, "voyage/inrange",
                      message);
              futureVoyages = response.readEntity(JsonValue.class);
              if (logger.isLoggable(Level.INFO)) {
                logger.info("orderthread: received " + futureVoyages.asJsonArray().size()
                            + " future voyages");
              }
            } catch (Exception e) {
              logger.warning("orderthread: unable to fetch future voyages from REST - cause:"
                      + e.getMessage());
            }

            // ... create MAP of target voyages with computed freecap
            synchronized (SimulatorService.voyageFreeCap) {
              for (JsonValue v : futureVoyages.asJsonArray()) {
                String id = v.asJsonObject().getString("id");
                Instant sd = Instant.parse(
                        v.asJsonObject().getString("sailDateObject").replaceAll("^\"|\"$", ""));
                int daysbefore = (int) ChronoUnit.DAYS.between(today, sd);
                int maxcap = v.asJsonObject().get("route").asJsonObject().get("vessel")
                        .asJsonObject().getInt("maxCapacity");
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
                int ordersize = (ordercap * 1000) / orderGroupsPerDay;

                // fill map, picking up new freecap values since map was cleared
                if (SimulatorService.voyageFreeCap.containsKey(id)) {
                  SimulatorService.voyageFreeCap.get(id).setDaysBefore(daysbefore);
                  SimulatorService.voyageFreeCap.get(id).setMaxCapacity(maxcap);
                  SimulatorService.voyageFreeCap.get(id).setOrderSize(ordersize);
                  SimulatorService.voyageFreeCap.get(id).setUtilization(utilization);
                } else {
                  SimulatorService.voyageFreeCap.put(id,
                          new FutureVoyage(daysbefore, maxcap, freecap, ordersize, utilization));
                }
              }
            }
          }
//          if (logger.isLoggable(Level.FINE)) {
//            logger.fine("orderthread: dumping voyageFreeCap MAP ----------");
//            SimulatorService.voyageFreeCap.forEach(
//                    (key, value) -> logger.fine("orderthread: " + key + " " + value.toString()));
//          }
          ordersExpectedToday = orderGroupsPerDay * SimulatorService.voyageFreeCap.keySet().size();

          // Use only one thread when doing one order for each target voyage
          if (orderGroupsPerDay == 1) {
            threadswanted = 1;
          } else {
            threadswanted = 3;  // binary 11
          }
        } // End of "new day" processing

        // Create sub-threads if needed and if available for creation
        if ( ((1 & threadswanted) != 0) && SimulatorService.suborderthreadcounts.get(1-1) == 0) {
          logger.fine("orderthread: create subthread1");
          (ordersubthread1 = new OrderSubThread(1, updatesPerDay, ordersDoneToday, oneshot)).start();
          threadswanted ^= 1;
        }
        if ( ((2 & threadswanted) != 0) && SimulatorService.suborderthreadcounts.get(2-1) == 0) {
          logger.fine("orderthread: create subthread2");
          (ordersubthread2 = new OrderSubThread(2, updatesPerDay, ordersDoneToday, oneshot)).start();
          threadswanted ^= 2;
        }

        // if oneshot, return if subthread created
        if (oneshot && threadswanted == 0) {
          SimulatorService.orderthreadcount.decrementAndGet();
          return;
        }

        // short sleeps if waiting to start sub-threads, else a full day waiting for ship-thread signal
        boolean interrupted = false;
        long timeToSleep = 1000 * daylength;
        if ( threadswanted != 0) {
          // short sleep
          timeToSleep = 10;
        }
        try {
          Thread.sleep(timeToSleep);
        } catch (InterruptedException e) {
          interrupted = true;
        }

        // if interrupted, check if auto mode should be turned off
        if (interrupted) {
          synchronized (SimulatorService.ordertarget) {
            if (0 == SimulatorService.ordertarget.intValue()
                    || 0 == SimulatorService.unitdelay.intValue()) {
              if (logger.isLoggable(Level.INFO)) {
                logger.info("orderthread: Stopping Thread " + Thread.currentThread().getId()
                        + " LOUD HORN");
              }
              running = false;
//              if (0 < SimulatorService.orderthreadcount.decrementAndGet()) {
//                logger.warning("orderthread: we have an extra order thread running!");
//              }
            }
          }
        }
      } catch (Throwable e) {
        logger.severe("orderthread: " + e);
        e.printStackTrace();
      }
    }
    SimulatorService.orderthreadcount.decrementAndGet();
  }
}
