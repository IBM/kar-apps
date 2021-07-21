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

//import static com.ibm.research.kar.Kar.actorCall;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReeferThread extends Thread {
    boolean running = true;
    boolean interrupted = false;
    boolean oneshot = false;
    int threadloops = 0;
    int reefersToBreak;
    int r2b[];
    int inventorySize;
    JsonValue currentDate = Json.createValue("");

    int updatesPerDay = 1;
    int anomaliesPerUpdate;
    int anomaliesDoneToday;
    private static final Logger logger = Logger.getLogger(ReeferThread.class.getName());

    public void run() {

            if (0 == SimulatorService.unitdelay.intValue()
                    || 0 == SimulatorService.failuretarget.intValue()) {
                oneshot = true;
            }

            Thread.currentThread().setName("reeferthread");
            SimulatorService.reeferthreadcount.incrementAndGet();
            if (logger.isLoggable(Level.INFO)) {
                logger.info("reeferthread: started threadid=" + Thread.currentThread().getId() + " ... LOUD HORN");
            }

            // If new day, get reefer inventory, anomaly target %,  and select reefers to break
            // Set number of anomalies per loop based on requested updates per day
            // Tell reefer provisioner the bad news

            while (running) {
                if (!oneshot) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("reeferthread: " + Thread.currentThread().getId() + ": running " + ++threadloops);
                    }
                }

                if (!SimulatorService.reeferRestRunning.get()) {
                    logger.warning("reeferthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
                } else {
                    // Make sure currentDate is set
                    if (null == SimulatorService.currentDate.get()) {
                        try {
                            Response response = Kar.Services.post(Constants.REEFERSERVICE, "time/currentDate", JsonValue.NULL);
                            currentDate = response.readEntity(JsonValue.class);
                        } catch (Exception e) {
                            logger.warning("reeferthread: Unable to fetch current date from REST - cause:" + e.getMessage());
                        }
                        SimulatorService.currentDate.set(currentDate);
                    }

                    // If new day ...
                    if (oneshot || !currentDate.equals((JsonValue) SimulatorService.currentDate.get())) {

                        currentDate = (JsonValue) SimulatorService.currentDate.get();
                        try {
                            // Get reefer inventory size from reefer-rest
                            Response response = Kar.Services.get(Constants.REEFERSERVICE, "reefers/inventory/size");

                            JsonValue is = (JsonValue) response.readEntity(JsonValue.class);
                            inventorySize = ((JsonNumber) is).intValue();
                        } catch (Exception e) {
                            logger.warning("reeferthread: Unable to fetch reefer inventory size - cause:" + e.getMessage());
                        }
                        // Get anomaly target for today
                        if (oneshot) {
                            reefersToBreak = 1;
                            if (logger.isLoggable(Level.INFO)) {
                                logger.info("reeferthread: oneshot generating 1 anomaly");
                            }
                        } else {
                            reefersToBreak = (inventorySize * SimulatorService.failuretarget.get()) / 10000;
                            if (logger.isLoggable(Level.INFO)) {
                                logger.info("reeferthread: generating " + reefersToBreak + " anomalies for new day");
                            }
                        }
                        r2b = new int[reefersToBreak];
                        Random rand = new Random();
                        for (int i = 0; i < reefersToBreak; i++) {
                            // ignore possibility that same reefer receives multiple anomalies
                            r2b[i] = rand.nextInt(inventorySize);
                        }

                        updatesPerDay = SimulatorService.reeferupdates.get();
                        anomaliesPerUpdate = reefersToBreak / updatesPerDay;
                        if (0 == anomaliesPerUpdate && 0 < reefersToBreak) {
                            anomaliesPerUpdate = 1;
                        }
                        anomaliesDoneToday = 0;
                    }

                    // if not done for the day, generate anomaliesPerUpdate more failures
                    for (int i = 0; i < anomaliesPerUpdate; i++) {
                        if (anomaliesDoneToday < reefersToBreak) {
                            int reeferid = r2b[anomaliesDoneToday++];
                            // ActorRef reeferProvisionerActor = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName,
                            //       ReeferAppConfig.ReeferProvisionerId);
                            ActorRef anomalyManagerActor = Kar.Actors.ref(ReeferAppConfig.AnomalyManagerActorName,
                                    ReeferAppConfig.AnomalyManagerId);
                            JsonObject params = Json.createObjectBuilder()
                                    .add(Constants.REEFER_ID_KEY, reeferid)
                                    .add(Constants.DATE_KEY, currentDate)
                                    .build();

                            if (logger.isLoggable(Level.FINE)) {
                                logger.fine("reeferthread: alerting provisioner about anomaly in reefer_" + reeferid);
                            }
                            try {
                                //Kar.Actors.call(reeferProvisionerActor, "reeferAnomaly", params);
                                Kar.Actors.call(anomalyManagerActor, "reeferAnomaly", params);
                            } catch (Exception e) {
                                logger.warning("reeferthread: error sending anomaly " + e.toString());
                            }
                        }
                    }
                }

                // sleep if not a oneshot reefer command
                if (!oneshot) {
                    try {
                        Thread.sleep(1000 * SimulatorService.unitdelay.intValue() / updatesPerDay);
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }

                // check if auto mode should be turned off
                if (0 == SimulatorService.failuretarget.intValue()
                        || 0 == SimulatorService.unitdelay.intValue() || oneshot) {
                    if (logger.isLoggable(Level.INFO)) {
                        logger.info("reeferthread: Stopping Thread " + Thread.currentThread().getId() + " LOUD HORN");
                    }
                    running = false;

                    if (0 < SimulatorService.reeferthreadcount.decrementAndGet()) {
                        logger.warning("reeferthread: we have an extra reefer thread running!");
                    }

                    // check for threads leftover from a hot method replace
                    Set<Thread> threadset = Thread.getAllStackTraces().keySet();
                    for (Thread thread : threadset) {
                        if (thread.getName().equals("reeferthread")
                                && thread.getId() != Thread.currentThread().getId()) {
                            logger.warning("reeferthread: killing leftover reefer threadid=" + thread.getId());
                            thread.interrupt();
                        }
                    }
                }
            }
    }
}
