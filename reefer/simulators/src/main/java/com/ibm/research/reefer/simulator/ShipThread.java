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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

// Ship Simulator thread functions
// 1. tell REST to update world time
// 2. request from REST list of all active voyages
// 3. send ship position to all active voyage actors, spread out thru day
// 4. tell order&reefer simulators the new time
// 5. total sleep of UnitDelay seconds spread out thru day
// 6. quit if one-shot request or thread interrupted


public class ShipThread extends Thread {
    boolean running = true;
    boolean interrupted = false;
    boolean oneshot = false;
    int loopcnt = 0;
    int event_scale[] = {6,10,25,50,100,200};
    int events_per_day;
    int voyages_per_event;
    int extra_per_event;
    int voyages_updated;
    LinkedHashMap<String, JsonObject> activemap = new LinkedHashMap<String, JsonObject>();
    LinkedHashMap<String, V2update> updatemap = new LinkedHashMap<String, V2update>();
    String[] activekeys;
    Integer[] numevents;
    int sleeptime;
    JsonValue currentDate;
    long last_snapshot;
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(ShipThread.class.getName());
    private class V2update{
      String id;
      int event;
      V2update(String id, int event) {
        this.id = id;
        this.event = event;
      }
    }

    private int events_from_fsize(int fsize) {
      return (fsize < 20) ? fsize : 20;
    }
    
    public void run() {
        if (0 == SimulatorService.unitdelay.intValue()) {
            oneshot = true;
        }

        Thread.currentThread().setName("shipthread");
        SimulatorService.shipthreadcount.incrementAndGet();
        if (logger.isLoggable(Level.INFO)) {
            logger.info("shipthread: started threadid=" + Thread.currentThread().getId() + " ... LOUD HORN");
        }
        int nextevent = 0;
        // get number of routes from rest
        JsonValue voyageRoutes = null;
        if (SimulatorService.numberofroutes.get() == 0) {
          try {
			Response response = Kar.Services.get(Constants.REEFERSERVICE, "voyage/routes");
			voyageRoutes = response.readEntity(JsonValue.class);
		  } catch (Exception e) {
			logger.warning("shipthread: Unable to fetch voyage routes from REST - cause:" + e.getMessage());
		  }
		  SimulatorService.numberofroutes.set(voyageRoutes.asJsonArray().size());
		  if (logger.isLoggable(Level.INFO)) {
			logger.info("shipthread: number of voyage routes = " + SimulatorService.numberofroutes.get());
		  }
        }
        events_per_day = events_from_fsize(SimulatorService.numberofroutes.get());
        if (null == numevents || numevents.length != events_per_day) {
          numevents = new Integer[events_per_day];
          for (int i=0; i<numevents.length; i++) {
            numevents[i] = Integer.valueOf(0);
          }
        }
        sleeptime = 1000 * SimulatorService.unitdelay.intValue();
        last_snapshot = System.nanoTime();

        while (running) {
            long snapshot = System.nanoTime();
            long delta = snapshot - last_snapshot;
            last_snapshot = snapshot;
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("shipthread: " + Thread.currentThread().getId() + ": running " + ++loopcnt + " nextevent===> " + nextevent
                        + "  sleeptime= " + sleeptime + " last delta= " + delta / 1000000);
            }

            if (!SimulatorService.reeferRestRunning.get()) {
                logger.warning("shipthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
            } else {
                if (0 == nextevent) {
                    // start of new day, tell REST to advance time
                    activemap.clear();
                    // further fixup needed if user manually kills ship thread
                    try {
                        Response response = Kar.Services.post(Constants.REEFERSERVICE, "time/advance", JsonValue.NULL);
                        currentDate = response.readEntity(JsonValue.class);
                    } catch (Exception e) {
                        logger.warning("shipthread: time advance call failed - cause:" + e.getMessage());
                    }
                    SimulatorService.currentDate.set(currentDate);

                    if (logger.isLoggable(Level.INFO)) {
                        logger.info("shipthread: New time ======> " + currentDate.toString());
                    }
                    // tell other threads to wake up to stay synchronized with ship thread
                    Kar.Services.post(Constants.SIMSERVICE,"simulator/newday", JsonValue.NULL);

                    JsonValue activeVoyages = null;
                    try {
                        // fetch all active voyages from REST
                        Response response = Kar.Services.get(Constants.REEFERSERVICE, "voyage/active");
                        // catch exception and continue
                        activeVoyages = response.readEntity(JsonValue.class);
                        if (logger.isLoggable(Level.INFO)) {
                            logger.info("shipthread: received " + activeVoyages.asJsonArray().size() + " active voyages");
                        }
                    } catch (Exception e) {
                        logger.warning("shipthread: Unable to fetch active voyages from REST - cause:" + e.getMessage());
                        // in case of failure, make sure we dont trigger NPE in the for-loop below
                       activeVoyages = Json.createArrayBuilder().build();
                    }

                    // compute ship positions to send to all active voyages
                    Instant ed = Instant.parse(currentDate.toString().replaceAll("^\"|\"$", ""));
                    for (JsonValue v : activeVoyages.asJsonArray()) {
                        String id = v.asJsonObject().getString("id");
                        Instant sd = Instant
                                .parse(v.asJsonObject().getString("sailDateObject").replaceAll("^\"|\"$", ""));
                        long daysout = ChronoUnit.DAYS.between(sd, ed);
                        JsonObject message = Json.createObjectBuilder().add("daysAtSea", daysout)
                                .add("currentDate", currentDate).build();
                        activemap.put(id, message);
                    }

                    // remove non-active voyages from update map
                    Iterator<Entry<String, V2update>> iter = updatemap.entrySet().iterator();
                    while (iter.hasNext()) {
                        Entry<String,V2update> entry = iter.next();
                        V2update u = entry.getValue();
                        if (!activemap.containsKey(u.id)) {
                          numevents[u.event]--;
                          if (logger.isLoggable(Level.FINE)) {
                            logger.fine("shipthread: deleting " + u.id + " from event " + u.event);
                          }
                          iter.remove();
                        }
                    }

                    // add new voyages into first "emptiest" event
                    for (String id : activemap.keySet()) {
                      if (!updatemap.containsKey(id)) {
                        int min = Collections.min(Arrays.asList(numevents));
                        for (int i=0;i<numevents.length;i++) {
                          if ( numevents[i] == min ) {
                            numevents[i]++;
                            if (logger.isLoggable(Level.FINE)) {
                              logger.fine("shipthread: adding " + id + " with event " + i);
                            }
                            updatemap.put(id, new V2update(id, i));
                            break;
                          }
                        }
                        if (logger.isLoggable(Level.FINE)) {
                          logger.fine("shipthread: updatemap = " + Arrays.toString(numevents));
                        }
                      }
                    }

                    if (SimulatorService.unitdelay.intValue() > 0) {
                        sleeptime = (1000 * SimulatorService.unitdelay.intValue()) / (events_per_day);
                    } else {
                        sleeptime = (1000 / events_per_day);
                    }
                }

                for (V2update u : updatemap.values()) {
                  if (u.event == nextevent) {
                    String id = u.id;
                    JsonObject message = activemap.get(id);
                    try {
                      Kar.Actors.tell(Kar.Actors.ref("voyage", id), "changePosition", message);
                    } catch (Exception ex) {
                      //  Add support to handle tell failures. For now just log the cause
                      logger.warning("shipthread: changePosition failed - cause:" + ex.getMessage());
                    }
                    if (logger.isLoggable(Level.FINE)) {
                      logger.fine("shipthread: updates voyageid: " + id + " with " + message.toString());
                    }
                  }
                }
              }

            nextevent++;
            if (events_per_day == nextevent) {
                nextevent = 0;
/*
                // tell GUI to update active voyages
                snapshot = System.nanoTime();

                try {
                    Kar.Services.post(Constants.REEFERSERVICE, "voyage/updateGui", currentDate);
                } catch (Exception e) {
                    logger.warning("shipthread: updateGUI failed - cause:" + e.getMessage());
                }
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("shipthread: updateGui took " + (System.nanoTime() - snapshot) / 1000000 + " ms");
                }

 */
            }

            try {
                Thread.sleep(sleeptime);
            } catch (InterruptedException e) {
                interrupted = true;
            }

            // check if auto mode turned off
            if ((nextevent == 0 && (0 == SimulatorService.unitdelay.intValue() || oneshot)) || interrupted) {
                SimulatorService.unitdelay.set(0);
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("shipthread: Stopping Thread " + Thread.currentThread().getId() + " LOUD HORN");
                }
                running = false;

                if (0 < SimulatorService.shipthreadcount.decrementAndGet()) {
                    logger.warning("shipthread: we have an extra thread running!");
                }

                // check for threads leftover from a hot method replace
                Set<Thread> threadset = Thread.getAllStackTraces().keySet();
                for (Thread thread : threadset) {
                    if (thread.getName().equals("shipthread")
                            && thread.getId() != Thread.currentThread().getId()) {
                        logger.warning("shipthread: killing leftover threadid=" + thread.getId());
                        thread.interrupt();
                    }
                }
            }
        }
    }
}
