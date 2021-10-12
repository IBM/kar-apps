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

package com.ibm.research.kar.reeferserver.controller;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import com.ibm.research.kar.reefer.model.ReeferStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestController
@CrossOrigin("*")
public class ReeferController {

    private int reeferInventorySize = 0;
    private int totalBooked = 0;
    private int totalInTransit = 0;
    private int totalSpoilt = 0;
    private int totalOnMaintenance = 0;


    @Autowired
    private GuiController gui;
    ActorRef depotManager = Kar.Actors.ref(ReeferAppConfig.DepotManagerActorType, ReeferAppConfig.DepotManagerId);

    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(ReeferController.class.getName());

    private int max_period = 10;
    private int period = 1;
    private int counter = 1;
    private ReeferStats oldStats = new ReeferStats(0, 0, 0, 0, 0);

    @PostConstruct
    public void init() {
        getReeferStats();
    }

    @GetMapping("/reefers/stats")
    public ReeferStats getReeferStats() {

        JsonValue metrics = Kar.Actors.State.get(depotManager, Constants.REEFER_METRICS_KEY);
        if (metrics != null && metrics != JsonValue.NULL) {
            String[] values = ((JsonString) metrics).getString().split(":");

            totalBooked = Integer.valueOf(values[0].trim());
            totalInTransit = Integer.valueOf(values[1].trim());
            totalSpoilt = Integer.valueOf(values[2].trim());
            totalOnMaintenance = Integer.valueOf(values[3].trim());
            reeferInventorySize = Integer.valueOf(values[4].trim());

        }
        return new ReeferStats(reeferInventorySize, totalInTransit, totalBooked, totalSpoilt, totalOnMaintenance);
    }

    @GetMapping("/reefers/inventory/size")
    public int getReeferInventorySize() {
        return reeferInventorySize;
    }

    @Scheduled(fixedDelay = 1000)
    public void scheduleGuiUpdate() {
        try {
            if (0 >= --counter) {
                ReeferStats newStats = getReeferStats();
                if (newStats.getTotalBooked() != oldStats.getTotalBooked() ||
                        newStats.getTotalInTransit() != oldStats.getTotalInTransit() ||
                        newStats.getTotalOnMaintenance() != oldStats.getTotalOnMaintenance() ||
                        newStats.getTotalSpoilt() != oldStats.getTotalSpoilt()) {
                    gui.updateReeferStats(newStats);
                    oldStats = newStats;
                    period = period / 2 < 1 ? 1 : period / 2;
                } else {
                    period = 2 * period > max_period ? max_period : 2 * period;
                }
                counter = period;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "ReeferController.scheduleGuiUpdate()",e);
        }

    }
}
