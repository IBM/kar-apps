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

import com.ibm.research.kar.reefer.client.orderdriver.json.RouteJsonSerializer;
import com.ibm.research.kar.reefer.client.orderdriver.model.FromToRoute;
import com.ibm.research.kar.reefer.client.orderdriver.model.FutureVoyage;
import com.ibm.research.kar.reefer.client.orderdriver.model.OrderStats;
import com.ibm.research.kar.reefer.client.orderdriver.model.Route;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class OrderDriver implements DayChangeHandler{
   private static final Logger logger = Logger.getLogger(OrderDriver.class.getName());
   WebSocketController webSocketDispatcher = new WebSocketController();
   ReeferWebApi reefer;
   VoyageController voyageController;
   OrderDispatcher orderDispatcher;
   ReplyProcessor replyProcessor;
   Instant newDay;
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

   public OrderDriver addRoutes(String routesArg) throws IOException, InterruptedException, URISyntaxException, IllegalArgumentException{
      String[] range;
      List<Route> allRoutes = getRoutes();
      if ( routesArg.indexOf("-") > 0 ) {
         // got range of routes
         range = routesArg.split("-");
         if ( invalidNumber(range[0].trim(), allRoutes.size()-1 ) ) {
            throw new IllegalArgumentException("Invalid lower route range provided  - must be a number between 0 and "+(allRoutes.size()-1));
         }
         if ( invalidNumber(range[1].trim(), allRoutes.size()-1 ) ) {
            throw new IllegalArgumentException("Invalid upper route range provided - must be a number between 0 and "+(allRoutes.size()-1));
         }
         int lowerRange = Integer.parseInt(range[0].trim());
         int upperRange = Integer.parseInt(range[1].trim());

         for( ; lowerRange <= upperRange; lowerRange++) {
            addRoute(allRoutes, lowerRange);
         }
      } else {
         // no range
         if ( invalidNumber(routesArg.trim(), allRoutes.size()-1 )) {
            throw new IllegalArgumentException("Invalid route index provided - must be a number between 0 and "+(allRoutes.size()-1));
         }
         addRoute(allRoutes, Integer.parseInt(routesArg.trim()));
      }
      return this;
   }
   private void addRoute( List<Route> allRoutes, int inx) {
      Route r = allRoutes.get(inx);
      FromToRoute route = new FromToRoute(r.getOriginPort(), r.getDestinationPort());
      routes.add(route);
      System.out.println("adding route:"+route.toString());
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
   public List<Route> getRoutes() throws IOException, InterruptedException, URISyntaxException {
         // Get all routes from reefer WebAPI
         HttpResponse<String> response = reefer.get("/routes");
         List<Route> routes = new LinkedList<>();
         try (JsonReader jsonReader = Json.createReader(new StringReader(response.body()))) {
            for (JsonValue routeAsJson : jsonReader.readArray()) {
               routes.add(RouteJsonSerializer.deserialize(routeAsJson.asJsonObject()));
            }
         } catch( Exception e) {
            throw new RuntimeException(e);
         }
      return routes;
   }
   public void start() {
      int unitDelay = 0;
      OrderStats orderStats = new OrderStats();
      voyageController = new VoyageController(reefer);
      replyProcessor = new ReplyProcessor(orderStats);
      // connect to Reefer WebAPI server
      webSocketDispatcher.connect(this.url, replyProcessor, this);
      orderDispatcher = new OrderDispatcher().
              withApiServer(reefer).
              withReplyProcessor(replyProcessor).
              withDriverReplyEndpoint(webSocketDispatcher.getEndpoint()).
              withHostIp( webSocketDispatcher.getHostAndIp()).
              withUpdatesPerDay(updatesPerDay).
              withOrderStats(orderStats).
              withOrderTimeout(timeoutMillis);
      replyProcessor.setOrderDispatcher(orderDispatcher);
      try {
         // Get unit delay from reefer WebAPI
         HttpResponse<String> response = reefer.post("/simulator/getdelay");
         if (isNumeric(response.body())) {
            unitDelay = Integer.parseInt(response.body());
            logger.info("OrderDriver.start() - unit delay:" + unitDelay);
         } else {
            throw new IllegalArgumentException("Reefer WebAPI returned invalid unitDelay:"+response.body());
         }
         HttpResponse<String> date = reefer.post("/time/currentDate");
         newDay = Instant.parse(date.body().replaceAll("\"", ""));
         while (running) {
            // get all voyages for a given route
            List<FutureVoyage> voyages = voyageController.getFutureVoyages(routes, newDay, orderTarget,updatesPerDay);
            orderDispatcher.dispatchTodayOrders(voyages, unitDelay);
            logger.info(String.format("Generated all orders for today[%s] - dispatched:%-7d accepted:%-7d booked:%-7d failed:%-7d latency[ mean:%-5.2f std:%-5.2f ] - - waiting for a new day",
                    newDay.toString().substring(0,10),orderStats.getDispatched(),
                    orderStats.getAccepted(),orderStats.getBooked(), orderStats.getFailed(),
                    orderStats.getMeanLatency(), orderStats.getStdLatency()));
            // reefer will push (via websocket) new day notification. We wait on a barrier until this
            // notification comes. See onDayAdvance() below
            barrier.await();
         }
      } catch (URISyntaxException | InterruptedException | IOException | BrokenBarrierException e) {
         logger.severe( "Unexpected error:"+e.getMessage() +"\n Terminating order driver");
         System.exit(-1);
      }
   }
   private boolean invalidNumber(String val, int maxAllowedSize) {
      if ( !isNumeric(val) || Integer.parseInt(val) < 0 || Integer.parseInt(val) > maxAllowedSize) {
         return true;
      }
      return false;
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
