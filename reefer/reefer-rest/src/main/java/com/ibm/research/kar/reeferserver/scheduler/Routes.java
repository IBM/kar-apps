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
package com.ibm.research.kar.reeferserver.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Ship;
import com.ibm.research.kar.reeferserver.model.Vessel;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonValue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Component
public class Routes {
    @Value("classpath:routes.txt")
    private Resource portsResource;
    @Value("classpath:ships.json")
    private Resource vesselJsonResource;
    private static final Logger logger = Logger.getLogger(Routes.class.getName());
    private ActorRef aRef = Kar.Actors.ref(ReeferAppConfig.RestActorName, ReeferAppConfig.RestActorId);
    public List<Route> generate() throws Exception {
        Map<String, String> env = System.getenv();

        List<Vessel> vessels = loadVessels();
        List<Route> routes = loadRoutes();
        int shipIndex = 0;
        int fleetSize = 10;  // default

        try {
            if ( env.containsKey(Constants.REEFER_FLEET_SIZE_KEY) &&
                    env.get(Constants.REEFER_FLEET_SIZE_KEY) != null &&
                    env.get(Constants.REEFER_FLEET_SIZE_KEY).trim().length() > 0 ) {
                fleetSize = Integer.parseInt(env.get(Constants.REEFER_FLEET_SIZE_KEY));
            }
            // if its a warm start, restore fleet size to previous value. User may change the fleet size through
            // env variable but the code below ignores it if its different from the previous size.
            JsonValue jv = Kar.Actors.State.get( aRef, Constants.REEFER_FLEET_SIZE_KEY);
            if ( jv != null && jv != JsonValue.NULL) {
                if (fleetSize != ((JsonNumber)jv).intValue()) {
                    System.out.println("Routes.generate() - Warm start - using previously saved fleet size of "+((JsonNumber)jv).intValue());
                    fleetSize = ((JsonNumber)jv).intValue();
                }
            } else {
                Kar.Actors.State.set(aRef, Constants.REEFER_FLEET_SIZE_KEY, Json.createValue(fleetSize));
                System.out.println("Routes.generate() ++++++++++++ saved fleet size:"+fleetSize);
            }
            System.out.println("Routes.generate() - starting with fleet size of:"+fleetSize);
            for( Route r : routes ) {
                Vessel vessel = vessels.get(shipIndex++);
                int capacity = Integer.parseInt(vessel.getCapacity().replace(",","").trim());
                r.setVessel(new Ship(vessel.getName(), 0, capacity, capacity, "AtPort"));
                // if ( shipIndex > maxFleetSize) {
                if ( shipIndex > fleetSize) {
                    break;
                }
            }
        } catch( Exception e) {
            e.printStackTrace();
        }

      //  return routes.subList(0,maxFleetSize);
        return routes.subList(0,fleetSize);
    }

    public List<Vessel> loadVessels() throws IOException {
        List<Vessel> ships;
        try {

        } catch( Exception e) {
            e.printStackTrace();
        }
        final ObjectMapper mapper = new ObjectMapper();
        final TypeReference<List<Vessel>> typeReference = new TypeReference<List<Vessel>>() {
        };
        ships = mapper.readValue(vesselJsonResource.getInputStream(), typeReference);
        //System.out.println("FleetService.loadShips() - Found " + ships.size() + " Ships");
       // ships.forEach(ship -> System.out.println("FleetService.loadShips() - Ship: " + ship.getName()
        //        + " Ship Capacity:" + ship.getCapacity())
        //);

        return ships;
    }

    public List<Route> loadRoutes() throws IOException {
        List<Route> routes = new ArrayList<>();
        Set<String> cities = new HashSet<>();
        Pattern pattern = Pattern.compile(":");
        ClassLoader classLoader = getClass().getClassLoader();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                portsResource.getInputStream(), StandardCharsets.UTF_8));) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] arr = pattern.split(line);
                Route r = new Route();
                r.setOriginPort(arr[0].trim());
                r.setDestinationPort(arr[1].trim());
             //   cities.add(arr[0].trim());
             //   cities.add(arr[1].trim());
               r.setDaysAtPort(2);
                r.setDaysAtSea(Integer.parseInt(arr[2].trim()));
                routes.add(r);
            }
        }
        /*
        // given a list of ports create unique routes for all of them
        List<PortPair> portRoutes = getAllPairs(new ArrayList<>(cities));
        System.out.println("Routes.loadRoutes() - number of generated routes:"+portRoutes.size());
        // randomize the routes since getAllPairs() groups by origin port
        Collections.shuffle(portRoutes);
        int maxDuration=26;
        int minDuration=12;
        StringBuilder sb = new StringBuilder("\n");
        // choose route duration at random
        Random rand = new Random();
        portRoutes.forEach(portPair -> {
            int duration = rand.nextInt((maxDuration - minDuration) + 1) + minDuration;
            Route r = new Route();
            r.setOriginPort(portPair.origin);
            r.setDestinationPort(portPair.destination);
            r.setDaysAtSea(duration);
            routes.add(r);
            sb.append(portPair.origin).append(":").append(portPair.destination).append(":").append(duration).append("\n");
        });
        logger.warning(sb.toString());

         */
        return routes;
    }


    public class PortPair {
        public String origin, destination;
        public PortPair(String origin, String destination) {
            this.origin = origin;
            this.destination = destination;
        }
    }

    public List<PortPair> getAllPairs(List<String> ports) {
        List<PortPair> pairs = new ArrayList<>();
        int total = ports.size();
        for(int i=0; i < total; i++) {
            String port1 = ports.get(i);
            for(int j=i+1; j < total; j++) {
               String port2 = ports.get(j);
                pairs.add(new PortPair(port1,port2));
            }
        }
        return pairs;
    }

}
