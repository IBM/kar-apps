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
import com.ibm.research.kar.reefer.model.ReeferStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.json.JsonNumber;
import javax.json.JsonValue;
import java.util.Map;
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
    ActorRef provisioner = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId);

    private static final Logger logger = Logger.getLogger(ReeferController.class.getName());

    @PostConstruct
    public void init() {
        getReeferStats();
    }

    @GetMapping("/reefers/stats")
    public ReeferStats getReeferStats() {

        Map<String, JsonValue> reeferStatsMap = Kar.Actors.State.Submap.getAll(provisioner, Constants.REEFER_STATS_MAP_KEY);
        if (reeferStatsMap.containsKey(Constants.TOTAL_BOOKED_KEY)) {
            totalBooked = ((JsonNumber) reeferStatsMap.get(Constants.TOTAL_BOOKED_KEY)).intValue();
        }
        if (reeferStatsMap.containsKey(Constants.TOTAL_INTRANSIT_KEY)) {
            totalInTransit = ((JsonNumber) reeferStatsMap.get(Constants.TOTAL_INTRANSIT_KEY)).intValue();
        }
        if (reeferStatsMap.containsKey(Constants.TOTAL_SPOILT_KEY)) {
            totalSpoilt = ((JsonNumber) reeferStatsMap.get(Constants.TOTAL_SPOILT_KEY)).intValue();
        }
        if (reeferStatsMap.containsKey(Constants.TOTAL_ONMAINTENANCE_KEY)) {
            totalOnMaintenance = ((JsonNumber) reeferStatsMap.get(Constants.TOTAL_ONMAINTENANCE_KEY)).intValue();
        }
        if (reeferStatsMap.containsKey(Constants.TOTAL_REEFER_COUNT_KEY)) {
            reeferInventorySize = ((JsonNumber) reeferStatsMap.get(Constants.TOTAL_REEFER_COUNT_KEY)).intValue();
        }
     //   System.out.println("ReeferController.getReeferStats()  ********** Booked:" + totalBooked +
    //            " -- InTransit:" + totalInTransit + " -- Spoilt:" + totalSpoilt + " -- onMaintenance:" + totalOnMaintenance);

        return new ReeferStats(reeferInventorySize, totalInTransit, totalBooked, totalSpoilt, totalOnMaintenance);
    }

    @GetMapping("/reefers/inventory/size")
    public int getReeferInventorySize() {
        return reeferInventorySize;
    }

    @Scheduled(fixedRate = 100)
    public void scheduleGuiUpdate() {
        try {
            gui.updateReeferStats(getReeferStats());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}