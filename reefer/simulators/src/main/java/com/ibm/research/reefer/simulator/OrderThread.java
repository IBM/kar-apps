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
  boolean woke_from_join = false;
  private static final AtomicInteger ordersDoneToday = new AtomicInteger();
  private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(OrderThread.class.getName());
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
    // if free-running it is interrupted on each new day ...
    //   ... interrupt subthreads if still around (this should not happpen)
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
          JsonValue date = (JsonValue) SimulatorService.currentDate.get();
          if (startup || oneshot || !currentDate.equals(date)) {
            dayEndTime = System.currentTimeMillis() + 1000 * SimulatorService.unitdelay.intValue();
            logger.fine("OrderThread: newday! ordersExpectedToday:"+ordersExpectedToday+
                    " ordersDoneToday:"+ordersDoneToday.get());
            if (ordersDoneToday.get() < ordersExpectedToday) {
              SimulatorService.os.addMissed(ordersExpectedToday - ordersDoneToday.get());
              logger.warning("orderthread: " + ordersDoneToday.get() + " of " + ordersExpectedToday
                      + " completed yesterday");
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
            if (logger.isLoggable(Level.FINE)) {
              logger.fine("orderthread: dumping voyageFreeCap MAP ----------");
              SimulatorService.voyageFreeCap.forEach(
                      (key, value) -> logger.fine("orderthread: " + key + " " + value.toString()));
            }
            ordersExpectedToday = orderGroupsPerDay * SimulatorService.voyageFreeCap.keySet().size();
          }

          // Create orders in one or two sub threads
          // Use only one thread when doing one order for each target voyage
          // When doing oneshots, order creation may complete before end of day and this code called again
          if (ordersExpectedToday > ordersDoneToday.get()) {
            try {
              if (orderGroupsPerDay == 1) {
                  (ordersubthread1 = new OrderSubThread(1, updatesPerDay, ordersDoneToday, oneshot)).start();
                ordersubthread1.join();
              }
              else {
                  (ordersubthread1 = new OrderSubThread(1, updatesPerDay, ordersDoneToday, oneshot)).start();
                  (ordersubthread2 = new OrderSubThread(2, updatesPerDay, ordersDoneToday, oneshot)).start();
                ordersubthread1.join();
                ordersubthread2.join();
              }
            } catch (InterruptedException e) {
              woke_from_join = true;
            }

            // join may have been interrupted by a new day
            // if so, interrupt unterminated sub threads
            if ( woke_from_join ) {
              int threadWaiting = 0;
              if (! "TERMINATED".equals(ordersubthread1.getState().toString())) {
                ordersubthread1.interrupt();
                threadWaiting = 1;
              }
              if (ordersubthread2 != null &&
                  ! "TERMINATED".equals(ordersubthread2.getState().toString())) {
                ordersubthread2.interrupt();
                threadWaiting += 2;
              }
              try {
                if (0 < (threadWaiting & 1)) {
                  ordersubthread1.join();
                }
                if (0 < (threadWaiting & 2)) {
                  ordersubthread2.join();
                }
              } catch (InterruptedException e) {
                // hmmm, not sure if this should happen
                woke_from_join = true;
              }
            }
            ordersubthread1 = null;
            ordersubthread2 = null;
          }
        }

        // sleep to get in synch with new day interrupt
        // don't sleep if this was a oneshot order command or the thread was interrupted in join
        // if running standalone, without rest service, sleep for a full day
        if (!oneshot || !woke_from_join) {
          try {
            long timeToSleep = 10;
            if (!SimulatorService.reeferRestRunning.get()) {
              timeToSleep = 1000 * SimulatorService.unitdelay.intValue();
            }
            Thread.sleep(timeToSleep);
          } catch (InterruptedException e) {
            // this is expected
          }
        }

        woke_from_join = false;
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
        logger.warning("orderthread: " + e);
        e.printStackTrace();
      }
    }
  }
}
