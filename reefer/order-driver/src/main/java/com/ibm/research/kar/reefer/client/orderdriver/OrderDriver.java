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

import com.ibm.research.kar.reefer.client.orderdriver.model.FromToRoute;
import com.ibm.research.kar.reefer.client.orderdriver.model.FutureVoyage;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

//@Component
//@CrossOrigin("*")
public class OrderDriver implements DayChangeHandler{
   private static final Logger logger = Logger.getLogger(OrderDriver.class.getName());
   WebSocketController webSocketDispatcher = new WebSocketController();
   ReeferWebApi reefer;
   BlockingQueue<String> replyQueue = new ArrayBlockingQueue<>(1);
   VoyageController voyageController;
   OrderDispatcher orderDispatcher;
   ReplyProcessor replyProcessor;
   Instant currentDate;
   Instant newDay;
   Instant today;
   boolean running = true;
   List<FromToRoute> routes = new LinkedList<>();
   // barrier is used to await new day notification from reefer via a websocket push
   CyclicBarrier barrier = new CyclicBarrier(2);
   int updatesPerDay=3;
   int orderTarget=75;
   long timeoutMillis = 60*1000*3;  // 3 minutes default timeout
   String url;

   public OrderDriver(String url) {
      this.url = url;
      reefer = new ReeferWebApi(url);
   }
   public OrderDriver addRoute(String routesArg) {
      routes.add(FromToRoute.parse(routesArg));
      return this;
   }
   public OrderDriver updatesPerDay( String updatesPerDay ) {
      if ( !isNumeric(updatesPerDay)) {
         throw new RuntimeException("Invalid value for updatesPerDay - must be a number");
      }
      this.updatesPerDay = Integer.parseInt(updatesPerDay);
      return this;
   }
   public OrderDriver orderTarget( String orderTarget ) {
      if ( !isNumeric(orderTarget)) {
         throw new RuntimeException("Invalid value for orderTarget - must be a number");
      }
      this.orderTarget = Integer.parseInt(orderTarget);
      return this;
   }
   public OrderDriver orderTimeout( String orderTimeout ) {
      if ( !isNumeric(orderTimeout)) {
         throw new RuntimeException("Invalid value for orderTarget - must be a number");
      }
      this.timeoutMillis = Long.valueOf(orderTimeout);
      return this;
   }
   public void start() {
      int unitDelay = 0;
      voyageController = new VoyageController(reefer);
      replyProcessor = new ReplyProcessor();
      // connect to Reefer WebAPI server
      webSocketDispatcher.connect(this.url, replyProcessor, this);
      orderDispatcher = new OrderDispatcher().
              withApiServer(reefer).
              withReplyProcessor(replyProcessor).
              withDriverReplyEndpoint(webSocketDispatcher.getEndpoint()).
              withHostIp( webSocketDispatcher.getHostAndIp()).
              withUpdatesPerDay(updatesPerDay).
              withOrderTimeout(timeoutMillis);

      try {

         // Get unit delay from WebAPI
         HttpResponse<String> response = reefer.post("/simulator/getdelay");
         if (isNumeric(response.body())) {
            unitDelay = Integer.parseInt(response.body());
            logger.info("OrderDriver.start() - unit delay:" + unitDelay);
         }
         HttpResponse<String> date = reefer.post("/time/currentDate");
         newDay = today = Instant.parse(date.body().replaceAll("\"", ""));
         while (running) {
            // get all voyages for a given route
            List<FutureVoyage> voyages = voyageController.getFutureVoyages(routes, newDay, orderTarget);
            // select the first voyage from the (sorted by departure date) list. Already departed voyages
            // will not be in the list. As the voyage arrives, this code will automatically start filling
            // the next voyage on a return trip.
            orderDispatcher.dispatchTodayOrders(voyages.get(0), unitDelay);
            logger.info("OrderDriver - generated all orders for today - waiting for a new day ...");
            // reefer will push (via websocket) new day notification. We wait on a barrier until this
            // notification comes. See onDayAdvance() below
            barrier.await();
         }
      } catch (URISyntaxException | InterruptedException | IOException | BrokenBarrierException e) {
         e.printStackTrace();
         System.exit(-1);
      }
   }

   private final Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");

   public boolean isNumeric(String strNum) {
      if (strNum == null) {
         return false;
      }
      return pattern.matcher(strNum).matches();
   }

   /**
    * Reefer WebAPI will asynchronously push notification every time a day is advanced.
    *
    * @param date
    */
   @Override
   public void onDayAdvance(Instant date) {
      // NOTE: THIS METHOD IS CALLED ON A SEPARATE THREAD WHICH HANDLES REPLIES FROM REEFER (Websockets)
      // See: WebSocketController subscription
      newDay = date;
      try {
         // wait until new day notification is pushed
         barrier.await();
      } catch (BrokenBarrierException | InterruptedException e) {
         e.printStackTrace();
      }
   }

}
