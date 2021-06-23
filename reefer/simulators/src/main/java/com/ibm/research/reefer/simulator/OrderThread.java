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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderThread extends Thread {
  boolean running = true;
  boolean oneshot = false;
  int threadloops = 0;
  int ordertarget;
  JsonValue currentDate;
  JsonValue futureVoyages;
  Instant today;

  int updatesDoneToday = 0;
  int updatesPerDay = 0;
  int orderGroupsPerDay;
  long dayEndTime;
  long totalOrderTime;
  boolean startup = true;
  private static final Logger logger = Logger.getLogger(OrderThread.class.getName());
  private Thread ordersubthread1 = null;
  private Thread ordersubthread2 = null;


  public void run() {
    if (0 == SimulatorService.unitdelay.intValue()
            || 0 == SimulatorService.ordertarget.intValue()) {
      oneshot = true;
    }

    Thread.currentThread().setName("orderthread");
    SimulatorService.orderthreadcount.incrementAndGet();
    if (logger.isLoggable(Level.INFO)) {
      logger.info(
              "orderthread: started threadid=" + Thread.currentThread().getId() + " ... LOUD HORN");
    }

    if (SimulatorService.reeferRestRunning.get()) {
      // Make sure currentDate is set
      if (null == SimulatorService.currentDate.get()) {

        try {
          Response response = Kar.Services.post(Constants.REEFERSERVICE, "time/currentDate",
                  JsonValue.NULL);
          currentDate = response.readEntity(JsonValue.class);
          SimulatorService.currentDate.set(currentDate);
        } catch (Exception e) {
          logger.warning(
                  "orderthread - unable to fetch current date from REST - cause:" + e.getMessage());
        }

        // Response response = Kar.Services.post(Constants.REEFERSERVICE, "time/currentDate",
        // JsonValue.NULL);
        // currentDate = response.readEntity(JsonValue.class);
        // SimulatorService.currentDate.set(currentDate);
      } else {
        currentDate = (JsonValue) SimulatorService.currentDate.get();
      }
    }

    if (!oneshot) {
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("orderthread: waiting for new day");
      }
      try {
        Thread.sleep(1000 * SimulatorService.unitdelay.intValue());
      } catch (InterruptedException e) {
        // nada
      }
    }

    while (running) {
      try {

        if (!SimulatorService.reeferRestRunning.get()) {
          logger.warning(
                  "orderthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
        } else {

          // If new day ...
          // interrupt sub threads if running
          // Count any missed orders from yesterday
          // pull fresh list of voyages within the order window
          // compute the total order capacity, "ordercap" to be made today for each voyage ...
          // ... and the ordersize to use for individual orders
          // set the loop count for max number of order groups to make for each voyage, "updatesPerDay"
          if (ordersubthread1 != null) {
            ordersubthread1.interrupt();
          }
          if (ordersubthread2 != null) {
            ordersubthread2.interrupt();
          }
          JsonValue date = (JsonValue) SimulatorService.currentDate.get();
          if (startup || oneshot || !currentDate.equals(date)) {
            dayEndTime = System.currentTimeMillis() + 1000 * SimulatorService.unitdelay.intValue();
            if (updatesDoneToday < updatesPerDay) {
              SimulatorService.os.addMissed(updatesPerDay - updatesDoneToday);
              logger.warning("orderthread: " + updatesDoneToday + " of " + updatesPerDay
                      + " completed yesterday");
            }
            if (logger.isLoggable(Level.FINE)) {
              logger.fine("orderthread: new day = " + date.toString());
            }
            updatesDoneToday = 0;
            totalOrderTime = 0;
            startup = false;
            currentDate = date;

            synchronized (SimulatorService.voyageFreeCap) {
              // clear so any order update between now and when the map is recreated are not lost
              SimulatorService.voyageFreeCap.clear();
            }

            // pick up any changes
            updatesPerDay = SimulatorService.orderupdates.intValue();
            if (updatesPerDay < 1) {
              updatesPerDay = 1;
              orderGroupsPerDay = 1;
            }
            // use two subthreads to generate orders in parallel?
            if (updatesPerDay > 1) {
              orderGroupsPerDay = 2 * updatesPerDay;
            }

            // ... fetch all future voyages leaving in the next N days
            today = Instant.parse(currentDate.toString().replaceAll("^\"|\"$", ""));

            int windowsize = SimulatorService.orderwindow.intValue();
            // ignore voyages leaving today
            Instant startday = today.plus(1, ChronoUnit.DAYS);
            Instant endday = today.plus(1 + windowsize, ChronoUnit.DAYS);
            endday = endday.minusSeconds(1);
            JsonObject message = Json.createObjectBuilder().add("startDate", startday.toString())
                    .add("endDate", endday.toString()).build();

            try {
              Response response = Kar.Services.post(Constants.REEFERSERVICE, "voyage/inrange",
                      message);
              futureVoyages = response.readEntity(JsonValue.class);
            } catch (Exception e) {
              logger.warning("orderthread: unable to fetch future voyages from REST - cause:"
                      + e.getMessage());
            }

            // Response response = Kar.Services.post(Constants.REEFERSERVICE, "voyage/inrange",
            // message);
            // futureVoyages = response.readEntity(JsonValue.class);
            if (logger.isLoggable(Level.INFO)) {
              logger.info("orderthread: received " + futureVoyages.asJsonArray().size()
                      + " future voyages");
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
            if (logger.isLoggable(Level.FINE)) {
              logger.fine("orderthread: dumping voyageFreeCap MAP ----------");
              SimulatorService.voyageFreeCap.forEach(
                      (key, value) -> logger.fine("orderthread: " + key + " " + value.toString()));
            }
          }

          // Update processing ...
          // Process a single order group in one thread
          // Process multiple order groups in parallel threads
          if (updatesPerDay > updatesDoneToday++) {
            long snapshot = System.currentTimeMillis();
            if (orderGroupsPerDay == 1) {
              (ordersubthread1 = new OrderSubThread()).start();
              ordersubthread1.join();
            }
            else {
              (ordersubthread1 = new OrderSubThread()).start();
              (ordersubthread2 = new OrderSubThread()).start();
              ordersubthread1.join();
              ordersubthread2.join();
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
              long orderTimeRemaining = totalOrderTime / updatesDoneToday
                      * (updatesPerDay - updatesDoneToday);
              if (timeRemaining < 1 && orderTimeRemaining > 0) {
                timeToSleep = 10;
              } else if (orderTimeRemaining > 0 && timeRemaining > 0) {
                timeToSleep = (timeRemaining - orderTimeRemaining)
                        / (1 + updatesPerDay - updatesDoneToday);
                timeToSleep = (timeToSleep < 10) ? 10 : timeToSleep;
              } else {
                timeToSleep = 1000 * SimulatorService.unitdelay.intValue();
              }
              if (logger.isLoggable(Level.FINE)) {
                logger.fine("orderthread: timeRemaining=" + timeRemaining + " orderTimeRemaining="
                        + orderTimeRemaining + " totalOrderTime=" + totalOrderTime
                        + " updatesDoneToday=" + updatesDoneToday + " timeToSleep=" + timeToSleep);
              }
            }
            Thread.sleep(timeToSleep);
          } catch (InterruptedException e) {
            // this is expected
          }
        }

        // check if auto mode should be turned off
        synchronized (SimulatorService.ordertarget) {
          if (0 == SimulatorService.ordertarget.intValue()
                  || 0 == SimulatorService.unitdelay.intValue() || oneshot) {
            if (logger.isLoggable(Level.INFO)) {
              logger.info("orderthread: Stopping Thread " + Thread.currentThread().getId()
                      + " LOUD HORN");
            }
            running = false;

            if (0 < SimulatorService.orderthreadcount.decrementAndGet()) {
              logger.warning("orderthread: we have an extra ship thread running!");
            }

            // check for threads leftover from a hot method replace
            Set<Thread> threadset = Thread.getAllStackTraces().keySet();
            for (Thread thread : threadset) {
              if (thread.getName().equals("orderthread")
                      && thread.getId() != Thread.currentThread().getId()) {
                logger.warning("orderthread: killing leftover order threadid=" + thread.getId());
                thread.interrupt();
              }
            }
          }
        }
      } catch (Throwable e) {
        logger.warning("orderthread:" + e);
      }
    }
  }
}
