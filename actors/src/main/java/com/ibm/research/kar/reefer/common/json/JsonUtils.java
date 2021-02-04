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

package com.ibm.research.kar.reefer.common.json;

import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Ship;

import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.Json;

import java.io.StringReader;
import java.time.Instant;
import com.ibm.research.kar.reefer.common.Constants;

public class JsonUtils {
    /**
     * Extract voyageId from json
     * 
     * @param message - json encoded params
     * 
     * @return voyageId
     */
    public static String getVoyageId(String message) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            JsonObject req = jsonReader.readObject();
            return req.getString(Constants.VOYAGE_ID_KEY);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static int getDaysAtSea(String message) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            JsonObject req = jsonReader.readObject();
            return req.getInt(Constants.VOYAGE_DAYSATSEA_KEY);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String getString(JsonValue value, String key) {
        return value.asJsonObject().getString(key);
    }
/*
    public static Voyage jsonToVoyage(JsonObject jsonVoyage) {
        System.out.println("JsonUtils.jsonToVoyage - "+jsonVoyage);
        Instant sailDate = Instant.parse(jsonVoyage.getString("sailDateObject"));
        String arrivalDate = jsonVoyage.getString("arrivalDate");
        Route route = jsonToRoute(jsonVoyage.getJsonObject("route"));
        Voyage voyage = new Voyage(route, sailDate, arrivalDate);
        voyage.setOrderCount(jsonVoyage.getInt("orderCount"));
        return voyage;
    }

    public static Route jsonToRoute(JsonObject jsonRoute) {
        Ship ship = jsonToShip(jsonRoute.getJsonObject("vessel"));
        String originPort = jsonRoute.getString("originPort");
        String destinationPort = jsonRoute.getString("destinationPort");
        int daysAtSea = jsonRoute.getInt("daysAtSea");
        int daysAtPort = jsonRoute.getInt("daysAtPort");
        return new Route(ship, originPort, destinationPort, daysAtSea, daysAtPort);
    }

    public static Ship jsonToShip(JsonObject jsonShip) {
        String shipId = jsonShip.getString("id");
        String name = jsonShip.getString("name");
        long position = jsonShip.getInt("position");
        int progress = jsonShip.getInt("progress");
        int maxCapacity = jsonShip.getInt("maxCapacity");
        int freeCapacity = jsonShip.getInt("freeCapacity");
        String location = jsonShip.getString("location");
        Ship ship = new Ship(name, position, maxCapacity, freeCapacity, location);
        ship.setId(shipId);
        ship.setProgress(progress);
        return ship;
    }

    public static JsonObject voyageToJson(Voyage v) {
       return Json.createObjectBuilder().
               add("id", Json.createValue(v.getId())).
               add("route", routeToJson(v.getRoute())).
               add("sailDateObject", v.getSailDateObject().toString()).
               add("sailDate",v.getSailDate()).
               add("arrivalDate", v.getArrivalDate()).
               add("displayArrivalDate",v.getDisplayArrivalDate()).
               add("orderCount",v.getOrderCount()).build();
    }
    public static JsonObject routeToJson(Route r) {
       String la = (r.getLastArrival() == null) ? "" : r.getLastArrival().toString();
       return Json.createObjectBuilder().
               add("vessel", shipToJson(r.getVessel())).
               add("originPort",r.getOriginPort()).
               add("destinationPort",r.getDestinationPort()).
               add("daysAtSea",r.getDaysAtSea()).
               add("daysAtPort",r.getDaysAtPort()).
               add("lastArrival",la).build();
    }
    public static JsonObject shipToJson(Ship s) {
       return Json.createObjectBuilder().
               add("id",s.getId()).
               add("name",s.getName()).
               add("position",s.getPosition()).
               add("progress",s.getProgress()).
               add("maxCapacity",s.getMaxCapacity()).
               add("freeCapacity",s.getFreeCapacity()).
               add("location",s.getLocation()).
               add("reefers",Json.createArrayBuilder(s.getReefers())).build();
    }


 */
}
