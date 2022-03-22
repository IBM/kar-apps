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
package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.FleetCapacity;
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import com.ibm.research.kar.reefer.common.Shard;
import com.ibm.research.kar.reefer.common.json.RouteJsonSerializer;
import com.ibm.research.kar.reefer.model.Route;


import javax.json.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Actor
public class DepotManagerActor extends BaseActor {
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(DepotManagerActor.class.getName());
    private List<Depot> depots = new LinkedList<>();
    private long totalInventorySize = 0;

    @Activate
    public void activate() {
        // fetch actor state from Kar storage
        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);

        if (state.isEmpty()) {
            ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
            JsonValue routes = Kar.Actors.call(this, scheduleActor, "routes");
            int inx = 0;
            // Using a route, calculate each depot total size and sum up total inventory.
            StringBuilder sb = new StringBuilder();
            for( JsonValue jsonRoute : routes.asJsonArray() ) {
                Route route = RouteJsonSerializer.deserialize(jsonRoute.asJsonObject());
                sb.append("Ship:").append(route.getVessel().getId()).append(" origin:").
                        append(route.getOriginPort()).append(" destination:").
                        append(route.getDestinationPort()).append(" capacity:").append(route.getVessel().getMaxCapacity()).append("\n");
                // a Shard is a continuous range of reefer ids. Each depot may have 1 or more
                // shards. If a depot "serves" say two voyages there will be two distinct shards
                // each with a unique range of reefer ids.
                inx += assignShardToDepot(route.getOriginPort(),route.getVessel().getId(),route.getVessel().getMaxCapacity(), inx );
                inx += assignShardToDepot(route.getDestinationPort(),route.getVessel().getId(),route.getVessel().getMaxCapacity(), inx );
            }
            totalInventorySize = inx;
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            Map<String, JsonValue> depotMap = new HashMap<>();
            JsonObjectBuilder mapJob = Json.createObjectBuilder();

            // Create a map of serialized depots which we can save as actor state.
            for (Depot depot : depots) {
                // serialize depot to Json using given JsonObjectBuilder. The
                // build() clears the state of the builder so we can reuse it.
                depot.serialize(mapJob);
                depotMap.put(depot.getId(), mapJob.build());

                JsonObjectBuilder job = Json.createObjectBuilder();
                depot.serialize( job);
                arrayBuilder.add(job);
            }

            Kar.Actors.State.Submap.set(this, Constants.DEPOTS_KEY, depotMap);
            Kar.Actors.State.set(this, Constants.TOTAL_REEFER_COUNT_KEY, Json.createValue(totalInventorySize));

            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_REEFER_COUNT_KEY, totalInventorySize).add(Constants.DEPOTS_KEY, arrayBuilder);
            ActorRef anomalyManagerActor = Kar.Actors.ref(ReeferAppConfig.AnomalyManagerActorType, ReeferAppConfig.AnomalyManagerId);
            Kar.Actors.tell(anomalyManagerActor, "depotReefers", job.build());

        } else {

            try {
                totalInventorySize = ((JsonNumber) state.get(Constants.TOTAL_REEFER_COUNT_KEY)).intValue();
                Map<String, JsonValue> depotMap = state.get(Constants.DEPOTS_KEY).asJsonObject();
                for (JsonValue jv : depotMap.values()) {
                    depots.add(deserializeDepot(jv.asJsonObject()));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE,"DepotManager.activate() - Error ", e);
            }
        }
        Kar.Actors.Reminders.schedule(this, "publishReeferMetrics", "AAA", Instant.now().plus(1, ChronoUnit.SECONDS), Duration.ofMillis(1000));
    }

    private int assignShardToDepot(String depotName, String shipName, int shipMaxCapacity, int beginRange) {
        // a depot can be in multiple voyages so either create new or fetch existing
        Depot depot = getDepot(depotName);
        // multiply ship capacity by a factor to pad inventory
        long paddedSize = FleetCapacity.totalSize(shipMaxCapacity);
        // a Shard is a continuous range of reefer ids. Each depot may have 1 or more
        // shards. If a depot "serves" say two voyages there will be two distinct shards
        // each with a unique range of reefer ids.
        Shard shard = new Shard(beginRange, beginRange + (paddedSize-1));
        depot.addShard(shard);
        if (logger.isLoggable(Level.INFO)) {
            logger.info("DepotManager.assignShardToDepot() - Depot:"+depot.getId()+" size:"+depot.getSize()+" ship:"+shipName+
                    " shard low range:"+shard.getLowerBound()+" shard high range:"+shard.getUpperBound());
        }
        return Long.valueOf(paddedSize).intValue();
    }

    private Depot deserializeDepot(JsonObject jsonDepot) {
        Depot depot = new Depot(jsonDepot.getString(Constants.ANOMALY_TARGET_KEY));

        JsonArray ja = jsonDepot.getJsonArray(Constants.SHARDS_KEY);
        for ( JsonValue jsonShard : ja ) {
            Shard shard = new Shard(jsonShard.asJsonObject().getInt("reefer-id-lower-bound"),
                    jsonShard.asJsonObject().getInt("reefer-id-upper-bound") );
            depot.addShard(shard);
        }
        return depot;
    }

    @Remote
    public JsonObject getDepotList() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        String depotsCommaSeparated = depots.stream()
                .map(Depot::getId)
                .collect(Collectors.joining(","));
        job.add(Constants.DEPOTS_KEY, depotsCommaSeparated);
        return job.build();
    }
    @Remote
    public void publishReeferMetrics() {
        int booked = 0, inTransitCount = 0, onMaintenance = 0, spoiltReefers = 0;
        try {

            Map<String, JsonValue> depotMetrics = Kar.Actors.State.Submap.getAll(this, Constants.REEFER_METRICS_MAP_KEY);
            for(Map.Entry<String, JsonValue> metrics : depotMetrics.entrySet()) {
                if ( metrics.getValue() != null) {
                    String[] values = ((JsonString)metrics.getValue()).getString().split(":");
                    if (values != null && values.length > 0) {
                        booked += Integer.parseInt(values[0]);
                        onMaintenance += Integer.valueOf(values[3]).intValue();
                    }
                }
            }
            ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
            JsonValue reefersInTransit = Kar.Actors.call(this, scheduleActor,"reefersInTransit");

            JsonValue spoiltReefersMetrics = Kar.Actors.State.get(scheduleActor, Constants.TOTAL_SPOILT_KEY);
            if (reefersInTransit != null && reefersInTransit != JsonValue.NULL) {
                inTransitCount += ((JsonNumber) reefersInTransit).intValue();
            }
            if (spoiltReefersMetrics != null && spoiltReefersMetrics != JsonValue.NULL) {
                spoiltReefers += ((JsonNumber) spoiltReefersMetrics).intValue();
            }
            saveMetrics(booked, inTransitCount, spoiltReefers, onMaintenance);
        } catch (Exception e) {
            logger.log(Level.SEVERE,"DepotManager.publishReeferMetrics()", e);
        }
    }

    private void saveMetrics(int reefersBooked, int reefersInTransit, int spoilt, int onMaintenance) {
        String metrics = String.format("%d:%d:%d:%d:%d", reefersBooked, reefersInTransit, spoilt, onMaintenance, totalInventorySize);
        Kar.Actors.State.set(this, Constants.REEFER_METRICS_KEY, Json.createValue(metrics));
    }

    private Depot getDepot(String port) {
        Depot depot = new Depot(port);
        if (!depots.contains(depot)) {
            depots.add(depot);
            return depot;
        } else {
            for (Depot existingDepot : depots) {
                if (existingDepot.equals(depot)) {
                    return existingDepot;
                }
            }
        }
        return depot;
    }

    @Remote
    public JsonObject depotInventory(JsonValue depotName) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        try {
            String depotId = ((JsonString) depotName).getString();
            boolean found = false;
            StringBuilder sb = new StringBuilder();
            for (Depot depot : depots) {
                sb.append(depot).append("\n");
                if (depot.getId().equals(depotId)) {
                    found = true;
                    job.add(Constants.TOTAL_REEFER_COUNT_KEY, totalInventorySize).
                            add(Constants.DEPOT_SIZE_KEY, depot.size);
                    JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                    for( Shard shard : depot.getShards()) {
                        arrayBuilder.add(shard.serialize());
                    }
                    job.add(Constants.SHARDS_KEY, arrayBuilder.build());
                    break;
                }
            }
            if (!found ) {
                logger.warning("DepotManager.depotInventory() - depot:" + depotId + " NOT found in depots - known depots:" + sb.toString());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,"DepotManager.depotInventory() Error ", e);
        }

        return job.build();
    }

    @Remote
    public void newDay(JsonObject jsonDate) {
        // "broadcast" to all depots a new date so that on-maintenance reefers are released
        for (Depot depot : depots) {
            // Reefers on maintenance are freed automatically after a configurable number of days passes.
            Kar.Actors.tell(depot.depotActor, "releaseReefersFromMaintenance", jsonDate);
        }

    }

    @Remote
    public void aggregateDepotMetrics() {
        AggregateDepotMetrics metrics = new AggregateDepotMetrics();
        depots.forEach(depot -> {
            Map<String, JsonValue> state = Kar.Actors.State.getAll(depot.getActorRef());
            if (!state.isEmpty()) {
                if (state.containsKey(Constants.REEFER_METRICS_KEY)) {
                    metrics.update(((JsonString) state.get(Constants.REEFER_METRICS_KEY)).getString());
                }
            }
        });
        Kar.Actors.State.set(Kar.Actors.ref(ReeferAppConfig.DepotManagerActorType, ReeferAppConfig.DepotManagerId),
                Constants.REEFER_METRICS_KEY, Json.createValue(metrics.toString()));
    }

    public static class Depot {
        private final String id;
        private long size;
        private final ActorRef depotActor;
        private List<Shard> shards = new LinkedList<>();

        public Depot(String port) {
            id = makeId(port);
            //this.size = size;
            depotActor = Kar.Actors.ref(ReeferAppConfig.DepotActorType, id);
        }

        public void setSize(long size) {
            this.size = size;
        }
        public long getSize() {
            return this.size;
        }
        public void incrementSize(long size) {
            this.size += size;
        }
        public List<Shard> getShards() {
            return shards;
        }

        public void addShard(Shard shard) {
            this.shards.add(shard);
            incrementSize(shard.getSize() );
        }
        public String getId() {
            return id;
        }

        public ActorRef getActorRef() {
            return depotActor;
        }

        public static String makeId(String port) {
            if (port.indexOf(",") > -1) {
                return port.substring(0, port.indexOf(",")) + Constants.REEFER_DEPOT_SUFFIX;
            }
            return port;
        }
        private void serialize(JsonObjectBuilder job) {
            job.add(Constants.ANOMALY_TARGET_KEY, this.getId()).
                    add(Constants.ANOMALY_TARGET_TYPE_KEY, Json.createValue(AnomalyManagerActor.ReeferLocation.LocationType.DEPOT.getType()));
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for( Shard shard : this.getShards()) {
                jab.add(shard.serialize());
            }
            job.add(Constants.SHARDS_KEY, jab.build());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Depot depot = (Depot) o;
            return Objects.equals(id, depot.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Depot{" +
                    "id='" + id + '\'' +
                    ", size=" + size +
                    ", depotActor=" + depotActor +
                    ", shards=" + shards+
                    '}';
        }
    }

    private class AggregateDepotMetrics {
        private int total;
        private int booked;
        private int maintenance;
        private int inTransit;
        private int spoilt;

        public void update(String metrics) {
            String[] values = metrics.split(":");
            booked += Integer.parseInt(values[0].trim());
            inTransit += Integer.parseInt(values[1].trim());
            spoilt += Integer.parseInt(values[2].trim());
            maintenance += Integer.parseInt(values[3].trim());
            total += Integer.parseInt(values[4].trim());
        }

        public int getTotal() {
            return this.total;
        }

        public int getBooked() {
            return this.booked;
        }

        public int getSpoilt() {
            return this.spoilt;
        }

        public int getOnMaintenance() {
            return this.maintenance;
        }

        public int getInTransit() {
            return this.inTransit;
        }

        public String toString() {
            return String.format("%d:%d:%d:%d:%d", getBooked(), getInTransit(),
                    getSpoilt(), getOnMaintenance(),
                    getTotal());
        }
    }
}
