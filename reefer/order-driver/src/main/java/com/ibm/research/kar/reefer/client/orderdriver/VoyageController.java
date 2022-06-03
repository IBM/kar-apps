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
package com.ibm.research.kar.reefer.client.orderdriver;

import com.ibm.research.kar.reefer.client.orderdriver.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.client.orderdriver.model.FromToRoute;
import com.ibm.research.kar.reefer.client.orderdriver.model.FutureVoyage;
import com.ibm.research.kar.reefer.client.orderdriver.model.Voyage;

import javax.json.*;
import javax.ws.rs.core.Response;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;


public class VoyageController {
   private static final Logger logger = Logger.getLogger(VoyageController.class.getName());
   private ReeferWebApi reeferApiServer;

   public VoyageController(ReeferWebApi reeferApiServer) {
      this.reeferApiServer = reeferApiServer;
   }

   public List<FutureVoyage> getFutureVoyages(List<FromToRoute> routes, Instant today, int orderTarget, int updatesPerDay ) {
      List<Voyage> voyages = new LinkedList<>();
      List<FutureVoyage> futureVoyages = new LinkedList<>();
      HttpResponse<String> jsonVoyages;
      try {
         JsonArrayBuilder jab = Json.createArrayBuilder();
         for (FromToRoute route : routes) {
            jab.add(route.asJson());
         }
         JsonObjectBuilder job = Json.createObjectBuilder();
         job.add("routes", jab.build());
         jsonVoyages = reeferApiServer.post("/voyage/list", job.build());
      } catch (Exception e) {
         throw new RuntimeException(e);
      }

      try (JsonReader jsonReader = Json.createReader(new StringReader(jsonVoyages.body()))) {
         JsonArray ja = jsonReader.readArray();
         for (JsonValue voyageAsJson : ja) { //jsonReader.readArray()) {
            Voyage voyage = VoyageJsonSerializer.deserialize(voyageAsJson.asJsonObject());
            Instant sailDate = voyage.getSailDateObject();
            int daysBeforeDeparture = (int) ChronoUnit.DAYS.between(today, sailDate);

            voyages.add(voyage);
            int maxCapacity = voyage.getRoute().getVessel().getMaxCapacity();
            int freeCapacity = voyage.getRoute().getVessel().getFreeCapacity();
            int utilization = (maxCapacity - freeCapacity) * 100 / maxCapacity;
            double dayOrderCap = 0;
            int orderCapacity = 0;
            int orderSize = 0;
            if (daysBeforeDeparture > 0) {
               dayOrderCap = (orderTarget * maxCapacity / 100.0 - (maxCapacity - freeCapacity)) / daysBeforeDeparture;
               if ( dayOrderCap > 0 ) {
                  orderCapacity = (int) Math.ceil(dayOrderCap);
                  orderSize = (orderCapacity * 1000)/ updatesPerDay;
               }
            }
            FutureVoyage futureVoyage = new FutureVoyage(voyage.getId(), daysBeforeDeparture, maxCapacity, freeCapacity, orderSize, utilization);
            futureVoyages.add(futureVoyage);
            logger.fine("Voyage:" + voyage.getId() + " daysBeforeDeparture:"+daysBeforeDeparture+" maxCapacity: " + maxCapacity + " freeCapacity: " +
                    freeCapacity + " orderSize: " + orderSize + " utilization: " + utilization);
         }
      }
      return futureVoyages;
   }
}
