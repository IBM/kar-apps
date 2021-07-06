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

import javax.json.*;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.DelayTarget;
import com.ibm.research.kar.reefer.model.OrderSimControls;
import com.ibm.research.kar.reefer.model.ReeferSimControls;
import com.ibm.research.kar.reeferserver.service.SimulatorService;

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
  private SimulatorService simulatorService;

  private static final Logger logger = Logger.getLogger(SimulatorController.class.getName());

  @PostMapping("/simulator/delay")
  public Instant shipSimulatorDelay(@RequestBody String delay) {
    int delayTime = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(delay))) {

      JsonObject req = jsonReader.readObject();
      delayTime = Integer.valueOf(req.getString("delay"));
      JsonObject delayArg = Json.createObjectBuilder().add("value", delayTime).build();
      Kar.Services.post(Constants.SIMSERVICE,"simulator/setunitdelay", delayArg);

    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }

    return TimeUtils.getInstance().getCurrentDate();
  }

  @PostMapping("/simulator/createorder")
  public void createOrder() {
     simulatorService.createOrder();
  }

  @PostMapping("/simulator/getdelay")
  public int getShipSimulatorDelay() {
    try {
      return simulatorService.getDelay();
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
    return -1;
  }

  @GetMapping("/simulator/getdelayandtarget")
  public DelayTarget getDelayAndTarget() {
    try {
      int delay = simulatorService.getDelay();
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
  public int  setSimOrderTarget(@RequestBody String body) {
    int orderTarget = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
      JsonObject req = jsonReader.readObject();
      orderTarget = Integer.valueOf(req.getJsonString("target").toString().replace("\"", ""));
      return simulatorService.setSimOrderTarget(orderTarget);
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
    return 0;
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
  public int setOrderSimControls(@RequestBody String body) {
    int orderTarget = 0;
    int orderWindow = 0;
    int updateFrequency = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
      JsonObject req = jsonReader.readObject();
      orderTarget = Integer.valueOf(req.getJsonString("target").toString().replace("\"", ""));
      orderWindow = Integer.valueOf(req.getJsonString("window").toString().replace("\"", ""));
      updateFrequency = Integer.valueOf(req.getJsonString("updateFrequency").toString().replace("\"", ""));
      // The sim does range check and returns a value
      orderTarget = simulatorService.updateOrderSimControls(orderTarget, orderWindow, updateFrequency);
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }
    return orderTarget;
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