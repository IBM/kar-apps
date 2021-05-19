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

import java.util.List;
import java.util.stream.Collectors;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.json.RouteJsonSerializer;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reeferserver.service.ScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ibm.research.kar.reefer.model.*;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

@RestController
@CrossOrigin("*")
public class ShipScheduleController {

  @GetMapping("/routes")
  public List<Route> getRoutes() {
    try {
      ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorName, ReeferAppConfig.ScheduleManagerId);
      JsonValue reply = Kar.Actors.call(scheduleActor, "routes");
      JsonArray ja = reply.asJsonArray();
      List<Route> routes =
              ja.stream().map(jv -> jv.asJsonObject()).map(RouteJsonSerializer::deserialize).collect(Collectors.toList());
      return routes;
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }


}