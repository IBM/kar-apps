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

import java.io.StringReader;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.DelayTarget;
import com.ibm.research.kar.reefer.model.OrderSimControls;
import com.ibm.research.kar.reefer.model.ReeferSimControls;
import com.ibm.research.kar.reeferserver.service.SimulatorService;
import com.ibm.research.kar.reeferserver.service.VoyageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class SimulatorController {
  @Autowired
  private VoyageService voyageService;

  @Autowired
  private SimulatorService simulatorService;

  private static final Logger logger = Logger.getLogger(SimulatorController.class.getName());

  @PostMapping("/simulator/delay")
  public Instant shipSimulatorDelay(@RequestBody String delay) {
    int delayTime = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(delay))) {

      JsonObject req = jsonReader.readObject();
      delayTime = req.getInt("delay");
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
    voyageService.changeDelay(delayTime);
    return TimeUtils.getInstance().getCurrentDate();
  }

  @PostMapping("/simulator/createorder")
  public void createOrder() {
     simulatorService.createOrder();
  }

  @PostMapping("/simulator/getdelay")
  public int getShipSimulatorDelay() {
    try {
      return voyageService.getDelay();
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
    return -1;
  }

  @GetMapping("/simulator/getdelayandtarget")
  public DelayTarget getDelayAndTarget() {
    try {
      int delay = voyageService.getDelay();
      int target = simulatorService.getSimOrderTarget();
      return new DelayTarget(delay, target);
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
    return new DelayTarget();
  }

  @PostMapping("/simulator/getsimordertarget")
  public int getSimOrderTarget() {
    return simulatorService.getSimOrderTarget();
  }

  @PostMapping("/simulator/setsimordertarget")
  public void setSimOrderTarget(@RequestBody String body) {
    int orderTarget = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
      JsonObject req = jsonReader.readObject();
      orderTarget = Integer.valueOf(req.getJsonString("target").toString().replace("\"", ""));
      simulatorService.setSimOrderTarget(orderTarget);
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
  }

  @GetMapping("/simulator/controls")
  public ReeferSimControls getReeferSimulatorControls() {
    return simulatorService.getReeferSimControls();
  }

  @PostMapping("/simulator/controls/update")
  public void updateReeferSimulatorControls(@RequestBody String body) {
    int failureRate = 0;
    int updateFrequency = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
      JsonObject req = jsonReader.readObject();
      failureRate = Integer.valueOf(req.getJsonString("failureRate").toString().replace("\"", ""));
      updateFrequency = Integer.valueOf(req.getJsonString("updateFrequency").toString().replace("\"", ""));
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
    simulatorService.updateReeferSimControls(new ReeferSimControls(failureRate, updateFrequency));
  }

  @PostMapping("/simulator/setordersimcontrols")
  public void setOrderSimControls(@RequestBody String body) {
    int orderTarget = 0;
    int orderWindow = 0;
    int updateFrequency = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
      JsonObject req = jsonReader.readObject();
      orderTarget = Integer.valueOf(req.getJsonString("target").toString().replace("\"", ""));
      orderWindow = Integer.valueOf(req.getJsonString("window").toString().replace("\"", ""));
      updateFrequency = Integer.valueOf(req.getJsonString("updateFrequency").toString().replace("\"", ""));
      simulatorService.updateOrderSimControls(orderTarget, orderWindow, updateFrequency);
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
  }

  @GetMapping("/simulator/getordersimcontrols")
  public OrderSimControls getOrderSimControls() {
    OrderSimControls controls = simulatorService.getOrderSimControls();
    return controls;
  }

  @PostMapping("/simulator/reefer/anomaly")
  public void generateAnomaly() {
    simulatorService.generateAnomaly();
  }
}