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
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.common.Constants;

public class OrderSubThread extends Thread {
  boolean running = true;
  boolean oneshot = false;
  int threadloops = 0;
  int ordertarget;
  JsonValue currentDate;
  JsonValue futureVoyages;
  Instant today;

  int updatesDoneToday = 0;
  int updatesPerDay = 0;
  long dayEndTime;
  long totalOrderTime;
  boolean startup = true;
  private static final Logger logger = Logger.getLogger(OrderSubThread.class.getName());

  public void run() {
    // order group processing:
    //  create one order for every voyage below threshold
    //  process voyages in random order
    try {
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
  
      for (int i = 0; i < keyList.size(); i++) {
        String voyage = keyList.get(indirects[i]);
        FutureVoyage entry = (FutureVoyage) SimulatorService.voyageFreeCap.get(voyage);
        if (entry == null) {
          // new day, voyageFreeCap cleared
          break;
        }
        if (entry.getOrderSize() > 0) {
          if (logger.isLoggable(Level.FINE)) {
            logger.fine("ordersubthread: create order size=" + entry.getOrderSize() + " for " + voyage);
          }
          JsonObject order = Json.createObjectBuilder().add("voyageId", voyage)
                  .add("customerId", "simulator").add("product", "pseudoBanana")
                  .add("productQty", entry.getOrderSize()).build();
          long ordersnap = System.nanoTime();
          JsonValue rsp = null;
  
          try {
            Response response = Kar.Services.post(Constants.REEFERSERVICE, "orders", order);
            rsp = response.readEntity(JsonValue.class);
          } catch (Exception e) {
            logger.warning("ordersubthread: error posting order " + e.toString());
          }
          if (null == rsp || !Constants.OK.equals(rsp.asJsonObject().getString("bookingStatus"))
                  || null == rsp.asJsonObject().getString("voyageId")) {
            SimulatorService.os.addFailed();
            logger.warning("ordersubthread: bad response when submitting order: " + order.toString()
                    + "\nresponse:" + rsp);
          } else {
            int otime = (int) ((System.nanoTime() - ordersnap) / 1000000);
            if (SimulatorService.os.addSuccessful(otime)) {
              // orderstats indicates an outlier
              String orderid = rsp.asJsonObject().getString("orderId");
              logger.warning("ordersubthread: order latency outlier voyage=" + voyage + " order="
                      + orderid + " ===> " + otime);
            }
          }
        }
      }
    } catch (Throwable e) {
      logger.warning("ordersubthread:" + e);
    }
  }
}
