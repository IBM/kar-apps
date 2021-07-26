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
import com.ibm.research.kar.reefer.common.json.RouteJsonSerializer;
import com.ibm.research.kar.reefer.model.Route;

import javax.json.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Actor
public class DepotManagerActor extends BaseActor {
    private static final Logger logger = Logger.getLogger(OrderManagerActor.class.getName());
    private List<Depot> depots = new LinkedList<>();
    private long totalInventorySize = 0;

    @Activate
    public void activate() {
        // fetch actor state from Kar storage
        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);

        if (state.isEmpty()) {
            ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorName, ReeferAppConfig.ScheduleManagerId);
            JsonValue reply = Kar.Actors.call(scheduleActor, "routes");
            reply.asJsonArray().stream().forEach(jsonRoute -> {
                Route route = RouteJsonSerializer.deserialize(jsonRoute.asJsonObject());
                // the origin and destination must have same reefer inventory size to hash reefers consistently
                Depot origin = getDepot(route.getOriginPort(), route.getVessel().getMaxCapacity());
                Depot destination = getDepot(route.getDestinationPort(), route.getVessel().getMaxCapacity());
                long maxSize = Math.max(origin.size + route.getVessel().getMaxCapacity(), destination.size + route.getVessel().getMaxCapacity());
                origin.setSize(maxSize);
                destination.setSize(maxSize);
            });
            for (Depot depot : depots) {
                totalInventorySize += depot.setInventorySize();
            }
            int inx = 0;
            JsonArrayBuilder jab = Json.createArrayBuilder();
            Map<String, JsonValue> depotMap = new HashMap<>();
            for (Depot depot : depots) {
                Shard shard = new Shard(inx, inx + depot.size - 1); // zero based index
                inx += depot.size;
                depot.setShard(shard);
                JsonObjectBuilder job = Json.createObjectBuilder();
                JsonObjectBuilder mapJob = Json.createObjectBuilder();
                serializeDepot(depot, job);
                jab.add(job);
                serializeDepot(depot, mapJob);
                depotMap.put(depot.getId(), mapJob.build());
                //System.out.println("DepotManager.activate() - deserializing depot:"+depot.toString());
            }

            Kar.Actors.State.Submap.set(this, Constants.DEPOTS_KEY, depotMap);
            Kar.Actors.State.set(this, Constants.TOTAL_REEFER_COUNT_KEY, Json.createValue(totalInventorySize));

            JsonObjectBuilder job = Json.createObjectBuilder();
            job.add(Constants.TOTAL_REEFER_COUNT_KEY, totalInventorySize).add(Constants.DEPOTS_KEY, jab);
            ActorRef anomalyManagerActor = Kar.Actors.ref(ReeferAppConfig.AnomalyManagerActorName, ReeferAppConfig.AnomalyManagerId);
            Kar.Actors.tell(anomalyManagerActor, "depotReefers", job.build());

        } else {

            try {
                totalInventorySize = ((JsonNumber) state.get(Constants.TOTAL_REEFER_COUNT_KEY)).intValue();
                Map<String, JsonValue> depotMap = state.get(Constants.DEPOTS_KEY).asJsonObject();
                for (JsonValue jv : depotMap.values()) {
                    depots.add(deserializeDepot(jv.asJsonObject()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Kar.Actors.Reminders.schedule(this, "publishReeferMetrics", "AAA", Instant.now().plus(1, ChronoUnit.SECONDS), Duration.ofSeconds(5));
    }

    private void serializeDepot(Depot depot, JsonObjectBuilder job) {
        job.add(Constants.ANOMALY_TARGET_KEY, depot.getId()).
                add(Constants.ANOMALY_TARGET_TYPE_KEY, Json.createValue(AnomalyManagerActor.ReeferLocation.LocationType.DEPOT.getType())).
                add("reefer-id-lower-bound", depot.getShard().getLowerBound()).
                add("reefer-id-upper-bound", depot.getShard().getUpperBound());
    }

    private Depot deserializeDepot(JsonObject jsonDepot) {
        Depot depot = new Depot(jsonDepot.getString(Constants.ANOMALY_TARGET_KEY));
        depot.setShard(new Shard(jsonDepot.getInt("reefer-id-lower-bound"), jsonDepot.getInt("reefer-id-upper-bound")));
        return depot;
    }

    @Remote
    public JsonObject getDepotList() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        String depotsCommaSeparated = depots.stream()
                .map(depot -> depot.getId())
                .collect(Collectors.joining(","));
        //
        job.add(Constants.DEPOTS_KEY, depotsCommaSeparated);
        return job.build();
    }
    @Remote
    public void publishReeferMetrics() {
        int booked = 0, inTransitCount = 0, onMaintenance = 0, spoiltReefers = 0;
        try {
            for (Depot depot : depots) {
                JsonValue metrics = Kar.Actors.State.get(depot.depotActor, Constants.REEFER_METRICS_KEY);
                if (metrics != null && metrics != JsonValue.NULL) {
                    String[] values = ((JsonString) metrics).getString().split(":");
                    if (values != null && values.length > 0) {
                        booked += Integer.valueOf(values[0]).intValue();
                        onMaintenance += Integer.valueOf(values[3]).intValue();
                    }
                }
            }
            ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorName, ReeferAppConfig.ScheduleManagerId);
            JsonValue inTransitMetrics = Kar.Actors.State.get(scheduleActor, Constants.REEFERS_IN_TRANSIT_COUNT_KEY);
            JsonValue spoiltReefersMetrics = Kar.Actors.State.get(scheduleActor, Constants.TOTAL_SPOILT_KEY);
            if (inTransitMetrics != null && inTransitMetrics != JsonValue.NULL) {
                inTransitCount += ((JsonNumber) inTransitMetrics).intValue();
            }
            if (spoiltReefersMetrics != null && spoiltReefersMetrics != JsonValue.NULL) {
                spoiltReefers += ((JsonNumber) spoiltReefersMetrics).intValue();
            }
            saveMetrics(booked, inTransitCount, spoiltReefers, onMaintenance);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveMetrics(int reefersBooked, int reefersInTransit, int spoilt, int onMaintenance) {
        String metrics = String.format("%d:%d:%d:%d:%d", reefersBooked, reefersInTransit, spoilt, onMaintenance, totalInventorySize);
        Kar.Actors.State.set(this, Constants.REEFER_METRICS_KEY, Json.createValue(metrics));
    }

    private Depot getDepot(String port, int size) {
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
            for (Depot depot : depots) {
                if (depot.getId().equals(depotId)) {
                    job.add(Constants.TOTAL_REEFER_COUNT_KEY, totalInventorySize).
                            add(Constants.DEPOT_SIZE_KEY, depot.size).
                            add("reefer-id-lower-bound", depot.getShard().getLowerBound()).
                            add("reefer-id-upper-bound", depot.getShard().getUpperBound());
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return job.build();
    }

    @Remote
    public void newDay(JsonObject jsonDate) {
        // "broadcast" to all depot new date so that on-maintenance reefers are released
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
        Kar.Actors.State.set(Kar.Actors.ref(ReeferAppConfig.DepotManagerActorName, ReeferAppConfig.DepotManagerId),
                Constants.REEFER_METRICS_KEY, Json.createValue(metrics.toString()));
    }

    public static class Depot {
        private final String id;
        private long size;
        private final ActorRef depotActor;
        private Shard shard;


        public Depot(String port) {
            id = makeId(port);
            //this.size = size;
            depotActor = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, id);
        }

        public void setSize(long size) {
            this.size = size;
        }

        public Shard getShard() {
            return shard;
        }

        public void setShard(Shard shard) {
            this.shard = shard;
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

        public long setInventorySize() {
            this.size = FleetCapacity.totalSize(this.size);
            return this.size;
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
                    ", shard=" + shard +
                    '}';
        }
    }

    private class Shard {
        private long lowerBound;
        private long upperBound;

        public Shard(long lowerBound, long upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        public long getLowerBound() {
            return lowerBound;
        }

        public long getUpperBound() {
            return upperBound;
        }

        @Override
        public String toString() {
            return "Shard{" +
                    "lowerBound=" + lowerBound +
                    ", upperBound=" + upperBound +
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
            booked += Integer.valueOf(values[0].trim());
            inTransit += Integer.valueOf(values[1].trim());
            spoilt += Integer.valueOf(values[2].trim());
            maintenance += Integer.valueOf(values[3].trim());
            total += Integer.valueOf(values[4].trim());
        }

        public int incrementTotal(int delta) {
            this.total += delta;
            return this.total;
        }

        public int decrementTotal(int delta) {
            this.total -= delta;
            return this.total;
        }

        public int getTotal() {
            return this.total;
        }

        public int incrementBooked(int delta) {
            this.booked += delta;
            return this.booked;
        }

        public int decrementBooked(int delta) {
            this.booked -= delta;
            return this.booked;
        }

        public int getBooked() {
            return this.booked;
        }

        public int incrementSpoilt(int delta) {
            this.spoilt += delta;
            return this.spoilt;
        }

        public int decrementSpoilt(int delta) {
            this.spoilt -= delta;
            return this.spoilt;
        }

        public int getSpoilt() {
            return this.spoilt;
        }

        public int incrementMaintenance(int delta) {
            this.maintenance += delta;
            return this.maintenance;
        }

        public int decrementMaintenance(int delta) {
            this.maintenance -= delta;
            return this.maintenance;
        }

        public int getOnMaintenance() {
            return this.maintenance;
        }

        public int incrementInTransit(int delta) {
            this.inTransit += delta;
            return this.inTransit;
        }

        public int decrementInTransit(int delta) {
            this.inTransit -= delta;
            return this.inTransit;
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
