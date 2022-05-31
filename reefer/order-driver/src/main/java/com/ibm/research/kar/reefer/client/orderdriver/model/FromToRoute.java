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
package com.ibm.research.kar.reefer.client.orderdriver.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class FromToRoute {
   String origin;
   String destination;

   public FromToRoute(String origin, String destination) {
      this.origin = origin;
      this.destination = destination;
   }
   public static FromToRoute parse(String routeAsString) {
      if ( routeAsString.indexOf(":") == -1 ) {
         throw new IllegalArgumentException("Provided route:"+routeAsString+" is missing a ':' delimiter between origin and destination");
      }
      String[] ports = routeAsString.split(":");
      if ( ports[0] == null || ports[0].length() == 0 ) {
         throw new IllegalArgumentException("Provided route:"+routeAsString+" is missing an origin port");
      }
      if ( ports[1] == null || ports[1].length() == 0 ) {
         throw new IllegalArgumentException("Provided route:"+routeAsString+" is missing a destination port");
      }
      System.out.println("FromToRoute: origin: "+ports[0]+" destination: "+ports[1]);
      return new FromToRoute(ports[0], ports[1]);
   }

   @Override
   public String toString() {
      return "FromToRoute{" +
              "origin='" + origin + '\'' +
              ", destination='" + destination + '\'' +
              '}';
   }

   public JsonObject asJson() {
      if ( origin == null || destination == null ) {
         throw new IllegalStateException("FromToRoute instance not initialized yet, call parse() first");
      }
      JsonObjectBuilder job = Json.createObjectBuilder();
      job.add("route-origin", origin);
      job.add("route-destination", destination);
      return job.build();
   }
}
