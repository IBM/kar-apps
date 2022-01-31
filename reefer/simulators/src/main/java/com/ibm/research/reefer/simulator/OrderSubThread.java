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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import com.ibm.research.reefer.simulator.SimulatorService.OutstandingOrder;

// This thread is started for each oneshot, or for each new day if free running
// For oneshots, the goal is to generate one order for all targeted voyages, as fast as possible
// When free running, the goal is to generate updatesPerDay orders for all targeted orders, spaced thru day
//  
public class OrderSubThread extends Thread {
  boolean running = true;
  int threadloops = 0;
  int ordertarget;
  JsonValue currentDate;
  JsonValue futureVoyages;
  Instant today;

  int tnum;
  OutstandingOrder OO;
  int updatesPerDay;
  AtomicInteger ordersDoneToday;
  boolean oneshot;

  // threadOrdersDone == # orders submitted since thread started
  int threadOrdersDone = 0;
  long totalOrderTime = 0;
  long dayEndTime;
  int expectedOrders;
  boolean startup = true;
  private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(OrderSubThread.class.getName());

  //ordersDoneToday are accumulated across subthreads
    OrderSubThread(int tnum, int updatesPerDay, AtomicInteger ordersDoneToday, boolean oneshot) {
    this.tnum = tnum;
    this.updatesPerDay = updatesPerDay;
    this.ordersDoneToday = ordersDoneToday;
  }

  public void run() {
    // order group processing:
    //  attempt to create <updatesPerDay> orders for every voyage below order target
    //  process voyages in a random order
    //  spread orders evenly across the simulated day
    //  randomize sleep times between orders to try to be out-of-sync with other OrderSubthreads
    try {
      if (tnum == 1) {
        OO = SimulatorService.OO_1;
      }
      else {
        OO = SimulatorService.OO_2;
      }
      // day end time is 98% of unit delay
      dayEndTime = System.currentTimeMillis() + 980 * SimulatorService.unitdelay.intValue();
      List<String> keyList;
      synchronized (SimulatorService.voyageFreeCap) {
        keyList = new ArrayList<String>(SimulatorService.voyageFreeCap.keySet());
      }
      Integer[] indirects = new Integer[keyList.size()];
      for (int i=0; i<keyList.size(); i++) {
        indirects[i] = i;
      }
      List<Integer> intlist = Arrays.asList(indirects);
      Collections.shuffle(intlist);
      intlist.toArray(indirects);

    expectedOrders = keyList.size() * updatesPerDay;
    for (int u = 0; u < updatesPerDay; u++) {
      for (int i = 0; i < keyList.size(); i++) {
        String voyage = keyList.get(indirects[i]);
        FutureVoyage entry = (FutureVoyage) SimulatorService.voyageFreeCap.get(voyage);
        if (entry == null) {
          // new day, voyageFreeCap cleared, stop generating orders for this voyage
          break;
        }
        if (entry.getOrderSize() > 0) {
          if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("ordersubthread%d: create order size=%d for %s", tnum ,entry.getOrderSize(), voyage));
          }
          JsonObject order = Json.createObjectBuilder().add("voyageId", voyage)
                  .add("customerId", "simulator").add("product", "pseudoBanana")
                  .add("productQty", entry.getOrderSize()).build();
          long ordersnap = System.nanoTime();
          String simSequenceID = String.format("%1d%s",tnum,SimulatorService.incrAndGet(Json.createValue(OO.persistKey)));
          OO.setOO(simSequenceID, ordersnap, OO.pending);
          try {
            Kar.Services.post(Constants.REEFERSERVICE, "orders", order);
            // increment counts of orders submitted
            ordersDoneToday.incrementAndGet();
            threadOrdersDone++;
            // wait for order async completion message
            //TODO what happens when thread gets an interrupt? Maybe should stop sending interrupts?
            synchronized (OO) {
              try {
                OO.wait();
              } catch( InterruptedException e) {}
            }
            int otime = (int) ((System.nanoTime() - ordersnap) / 1000000);
            totalOrderTime += otime;
          } catch (Exception e) {
            logger.severe(String.format("ordersubthread%d: error posting order %s", tnum, e.toString()));
            ordersDoneToday.incrementAndGet();
            threadOrdersDone++;
          }
        }
        // compute next sleep time
        // No sleep between orders for oneshots
        if (!oneshot && threadOrdersDone > 0 && expectedOrders > threadOrdersDone) {
          long timeToSleep = 10;
          long timeRemaining = dayEndTime - System.currentTimeMillis();
          long orderTimeRemaining = (totalOrderTime / threadOrdersDone)
                  * (expectedOrders - threadOrdersDone);
          long triggerRandom = 20;  // 20 millis
          if (orderTimeRemaining > 0 && timeRemaining > 0) {
            timeToSleep = (timeRemaining - orderTimeRemaining)
                    / (expectedOrders - threadOrdersDone);
            if (timeToSleep > triggerRandom) {
              timeToSleep = ThreadLocalRandom.current().nextLong(timeToSleep/2, (timeToSleep*3)/2);
            }
            timeToSleep = (timeToSleep < 1) ? 1 : timeToSleep;
          }
          if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("ordersubthread%d: timeRemaining=%d orderTimeRemaining=%d totalOrderTime=%d threadOrdersDone=%d timeToSleep=%d",
                                      tnum, timeRemaining, orderTimeRemaining, totalOrderTime, threadOrdersDone, timeToSleep));
          }
          Thread.sleep(timeToSleep);
        }
      }
     }
    } catch (Throwable e) {
      // expected ... terminate subthread when it is interrupted
    }
  }
}
