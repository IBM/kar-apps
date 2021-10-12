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

import com.ibm.research.kar.reefer.actors.OrderActor;
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.ReeferStats;
import com.ibm.research.kar.reeferserver.model.ShippingSchedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Updates the GUI using websockets. The order and reefer related counts are
 * updated at regular intervals via a TimerTask.
 */
@RestController
@CrossOrigin("*")
public class GuiController {
    @Autowired
    private SimpMessagingTemplate template;

    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(GuiController.class.getName());

    public void sendActiveVoyageUpdate(ShippingSchedule schedule) { //List<Voyage> voyages, String currentDate) {
        long start = System.currentTimeMillis();
        template.convertAndSend("/topic/voyages", schedule);
        long end = System.currentTimeMillis();
        if (logger.isLoggable(Level.INFO)) {
            logger.info("GuiController.sendActiveVoyageUpdate() - voyage update took " + (end - start) + " ms");
        }
    }

    public void updateOrderCounts(OrderStats stats) {
        if (stats != null) {
            template.convertAndSend("/topic/orders/stats", stats);
        }

    }

    public void updateReeferStats(ReeferStats stats) {
        if (stats != null) {
            template.convertAndSend("/topic/reefers/stats", stats);
        }

    }

}