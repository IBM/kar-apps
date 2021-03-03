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

import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Ship;
import com.ibm.research.kar.reefer.model.Voyage;

import javax.json.Json;
import javax.json.JsonObject;
import java.time.Instant;

public class VoyageJsonSerializer {


    public static Voyage deserialize(JsonObject jsonVoyage) {
        Instant sailDate = Instant.parse(jsonVoyage.getString(Voyage.SAIL_DATE));
        String arrivalDate = jsonVoyage.getString(Voyage.ARRIVAL_DATE);
        Route route = jsonToRoute(jsonVoyage.getJsonObject(Route.ROUTE));
        String id = jsonVoyage.getString(Voyage.ID);
        Voyage voyage = new Voyage(id, route, sailDate, arrivalDate);
        voyage.setOrderCount(jsonVoyage.getInt(Voyage.ORDER_COUNT));
        voyage.setProgress(jsonVoyage.getInt(Voyage.ORDER_COUNT));
        return voyage;
    }

    private static Route jsonToRoute(JsonObject jsonRoute) {
        Ship ship = jsonToShip(jsonRoute.getJsonObject(Ship.VESSEL));
        String originPort = jsonRoute.getString(Route.ORIGIN_PORT);
        String destinationPort = jsonRoute.getString(Route.DESTINATION_PORT);
        int daysAtSea = jsonRoute.getInt(Route.DAYS_AT_SEA);
        int daysAtPort = jsonRoute.getInt(Route.DAYS_AT_PORT);
        return new Route(ship, originPort, destinationPort, daysAtSea, daysAtPort);
    }

    private static Ship jsonToShip(JsonObject jsonShip) {
        String shipId = jsonShip.getString(Ship.VESSEL_ID);
        String name = jsonShip.getString(Ship.VESSEL_NAME);
        long position = jsonShip.getInt(Ship.POSITION);
        //int progress = jsonShip.getInt(Ship.PROGRESS);
        int maxCapacity = jsonShip.getInt(Ship.MAX_CAPACITY);
        int freeCapacity = jsonShip.getInt(Ship.FREE_CAPACITY);
        String location = jsonShip.getString(Ship.LOCATION);
        Ship ship = new Ship(name, position, maxCapacity, freeCapacity, location);
        ship.setId(shipId);
       // ship.setProgress(progress);
        return ship;
    }

    public static JsonObject serialize(Voyage v) {
        return Json.createObjectBuilder().
                add(Voyage.ID, Json.createValue(v.getId())).
                add(Route.ROUTE, routeToJson(v.getRoute())).
                add(Voyage.SAIL_DATE, v.getSailDateObject().toString()).
                add(Voyage.SAIL_DATE_STRING,v.getSailDate()).
                add(Voyage.ARRIVAL_DATE, v.getArrivalDate()).
                add(Voyage.DISPLAY_ARRIVAL_DATE,v.getDisplayArrivalDate()).
                add(Voyage.ORDER_COUNT,v.getOrderCount()).build();
    }
    private static JsonObject routeToJson(Route r) {
        String la = (r.getLastArrival() == null) ? "" : r.getLastArrival().toString();
        return Json.createObjectBuilder().
                add(Ship.VESSEL, shipToJson(r.getVessel())).
                add(Route.ORIGIN_PORT,r.getOriginPort()).
                add(Route.DESTINATION_PORT,r.getDestinationPort()).
                add(Route.DAYS_AT_SEA,r.getDaysAtSea()).
                add(Route.DAYS_AT_PORT,r.getDaysAtPort()).
                add(Route.LAST_ARRIVAL_DATE,la).build();
    }
    private static JsonObject shipToJson(Ship s) {
        return Json.createObjectBuilder().
                add(Ship.VESSEL_ID,s.getId()).
                add(Ship.VESSEL_NAME,s.getName()).
                add(Ship.POSITION,s.getPosition()).
             //   add(Ship.PROGRESS,s.getProgress()).
                add(Ship.MAX_CAPACITY,s.getMaxCapacity()).
                add(Ship.FREE_CAPACITY,s.getFreeCapacity()).
                add(Ship.LOCATION,s.getLocation()).
                add(Ship.REEFERS,Json.createArrayBuilder(s.getReefers())).build();
    }
}
