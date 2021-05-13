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

import javax.json.Json;
import javax.json.JsonObject;

public class RouteJsonSerializer {
    public static JsonObject serialize(Route route) {
        return Json.createObjectBuilder().add( Route.ORIGIN_PORT, route.getOriginPort()).
            add(Route.DESTINATION_PORT, route.getDestinationPort()).
                add(Route.DAYS_AT_PORT, route.getDaysAtPort()).
                add(Route.DAYS_AT_SEA, route.getDaysAtSea()).
                add( Route.LAST_ARRIVAL_DATE, route.getLastArrival().toString()).
                add(Ship.VESSEL, shipToJson(route.getVessel())).build();
    }
    private static JsonObject shipToJson(Ship s) {
        return Json.createObjectBuilder().
                add(Ship.VESSEL_ID,s.getId()).
                add(Ship.VESSEL_NAME,s.getName()).
                add(Ship.POSITION,s.getPosition()).
                add(Ship.MAX_CAPACITY,s.getMaxCapacity()).
                add(Ship.FREE_CAPACITY,s.getFreeCapacity()).
                add(Ship.LOCATION,s.getLocation()).
                add(Ship.REEFERS,Json.createArrayBuilder(s.getReefers())).build();
    }
    public static Route deserialize(JsonObject jsonRoute) {
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
}
