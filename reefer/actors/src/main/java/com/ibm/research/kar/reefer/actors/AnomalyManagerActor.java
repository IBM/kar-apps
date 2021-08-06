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

import javax.json.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Actor
public class AnomalyManagerActor extends BaseActor {
    private Map<String, ReeferLocation> reefersMap = null;
    private Map<String, JsonValue> state = null;
    @Activate
    public void activate() {
        try {
            long t1 = System.currentTimeMillis();
            state = Kar.Actors.State.getAll(this);
            System.out.println("AnomalyManagerActor.activate() -  fetched all state in " + (System.currentTimeMillis() - t1));
            if (!state.isEmpty()) {
                if (state.containsKey(Constants.REEFERS_KEY)) {
                    reefersMap = new LinkedHashMap<>();
                    instantiateReeferTargetMap();
                } else {
                    System.out.println("AnomalyManagerActor.activate() - reefer-target state is not available");
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

    private void instantiateReeferTargetMap() {
        // ReeferLocation instances are restored from a stringified list where
        // each entry is encoded as follows: <REEFERID:int>|<DEPOT NAME:string>|<TYPE:int>
        // where TYPE[1,2] 1: Depot, 2: Order. Example:
        // 0|NewYorkReeferDepot|1
        String reeferTargets = ((JsonString)state.get(Constants.REEFERS_KEY)).getString();
        if ( reeferTargets != null ) {
            long t1 = System.currentTimeMillis();
            String[] targets = reeferTargets.split(",");
            for ( String target : targets ) {
                String[] props = target.split("\\|");
                reefersMap.put(props[0], new ReeferLocation(Integer.valueOf(props[0]), props[1], Integer.valueOf(props[2])));
            }
            System.out.println("AnomalyManagerActor.instantiateReeferTargetMap() - time to instantiate reeferMap from state took:"+(System.currentTimeMillis() - t1)+" size:"+reefersMap.size());
        } else {
            System.out.println("AnomalyManagerActor.instantiateReeferTargetMap() - reeferTargets not defined !!!!!!!!!!!!!!");
        }

    }
    @Remote
    public void depotReefers(JsonObject depotReefers) {
        System.out.println("AnomalyManagerActor.depotReefers() - " + depotReefers);
        long t = System.currentTimeMillis();
        try {
            JsonArray ja = depotReefers.getJsonArray(Constants.DEPOTS_KEY);
            int totalCount = depotReefers.getInt(Constants.TOTAL_REEFER_COUNT_KEY);
            StringBuilder sb = new StringBuilder();
            reefersMap = new LinkedHashMap<>(totalCount );
            for (JsonValue depot : ja) {
                long t1 = System.currentTimeMillis();
                for( JsonValue jsonShard : depot.asJsonObject().getJsonArray(Constants.SHARDS_KEY)) {
                    int lower = jsonShard.asJsonObject().getInt("reefer-id-lower-bound");
                    int upper = jsonShard.asJsonObject().getInt("reefer-id-upper-bound");
                    sb.append(createReefers(depot.asJsonObject().getString(Constants.ANOMALY_TARGET_KEY), lower, upper));
                }
            }
            long t2 = System.currentTimeMillis();
            Kar.Actors.State.set(this, Constants.REEFERS_KEY, Json.createValue(sb.toString()));
            System.out.println("AnomalyManagerActor.depotReefers() - saved depot reefers - total time:" + (System.currentTimeMillis() - t2) );
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
    private String createReefers(String depot, int lowerRange, int upperRange) {
        StringBuilder sb = new StringBuilder();
        try {
            long duration = 0, total = 0;
            JsonObjectBuilder job = Json.createObjectBuilder();
            for (int reeferId = lowerRange; reeferId <= upperRange; reeferId++) {
                long t2 = System.currentTimeMillis();
                sb.append(String.valueOf(reeferId)).append("|").append(depot).append("|").append(1).append(",");
                total += (System.currentTimeMillis() - t2);
                reefersMap.put(String.valueOf(reeferId), new ReeferLocation(reeferId,depot, ReeferLocation.LocationType.DEPOT.getType()));
            }
          //  System.out.println("AnomalyManagerActor.addDepotReefers() ---------- depot:" + depot + " reefer lower range:" + lowerRange + " upper:" + upperRange + " reeferMap size:" + reefersMap.size() + " duration:" + duration + " map update took:" + total);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
    private String serializeReefers() {
        StringBuilder sb = new StringBuilder();

            for (String key : reefersMap.keySet()) {
                ReeferLocation reeferLocation = reefersMap.get(key);
                sb.append(reeferLocation.getId()).append("|").append(reeferLocation.getTarget()).append("|").append(reeferLocation.getTargetType()).append(",");
            }
            //     System.out.println("AnomalyManagerActor.addDepotReefers() ---------- depot:" + depot + " reefer lower range:" + lowerRange + " upper:" + upperRange + " reeferMap size:" + reefersMap.size() + " duration:" + duration + " map update took:" + total);

        return sb.toString();
    }
    private Map<String, JsonValue> addDepotReefers(String depot, int lowerRange, int upperRange) {
        Map<String, JsonValue> updateMap = new HashMap<>(upperRange - lowerRange + 1);
        try {
            long duration = 0, total = 0;
            JsonObjectBuilder job = Json.createObjectBuilder();
            for (int reeferId = lowerRange; reeferId <= upperRange; reeferId++) {
                long t = System.currentTimeMillis();
                JsonObject target = reeferTarget(String.valueOf(reeferId), depot, 1, job);
                duration += (System.currentTimeMillis() - t);
                long t2 = System.currentTimeMillis();
                updateMap.put(String.valueOf(reeferId), target);
                total += (System.currentTimeMillis() - t2);
            }
            //     System.out.println("AnomalyManagerActor.addDepotReefers() ---------- depot:" + depot + " reefer lower range:" + lowerRange + " upper:" + upperRange + " reeferMap size:" + reefersMap.size() + " duration:" + duration + " map update took:" + total);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return updateMap;
    }

    private JsonObject reeferTarget(String reeferId, String target, int targetType, JsonObjectBuilder job) {
        job.add(Constants.REEFER_ID_KEY, reeferId).
                add(Constants.TARGET_KEY, target).
                add(Constants.TARGET_TYPE_KEY, targetType);
        return job.build();
    }


    @Remote
    public void reeferAnomaly(JsonObject message) {
        try {
            String reeferId = String.valueOf(message.getInt(Constants.REEFER_ID_KEY));
            if (reefersMap.containsKey(reeferId)) {
                ReeferLocation target = reefersMap.get(reeferId);
                ActorRef targetActor;
                boolean departedFromDepot=false;
                if ( message.containsKey(Constants.DEPOT_KEY)) {
                    departedFromDepot = true;
                    System.out.println("AnomalyManagerActor.reeferAnomaly() --------------------------- forwarding anomaly from depot:"+
                                message.getString(Constants.DEPOT_KEY)+" to target:"+target.getTarget());
                }
                switch (target.getTargetType()) {
                    case 1:  // Depot type
                        if ( departedFromDepot && target.getTarget().equals(message.getString(Constants.DEPOT_KEY))) {
                            // the anomaly was kicked back from depot
                            break;
                        }
                        targetActor = Kar.Actors.ref(ReeferAppConfig.DepotActorType, target.getTarget());
                        Kar.Actors.tell(targetActor, "reeferAnomaly", message);
                        break;
                    case 2:  // Order type
                        targetActor = Kar.Actors.ref(ReeferAppConfig.OrderActorType, target.getTarget());
                        Kar.Actors.tell(targetActor, "reeferAnomaly", message);
                        if ( departedFromDepot ) {
                            System.out.println("AnomalyManagerActor.reeferAnomaly() --------------------------- forwarded anomaly from depot:"+
                                    message.getString(Constants.DEPOT_KEY)+" to order:"+target.getTarget());
                        }

                        break;
                    default:
                        System.out.println("AnomalyManagerActor.reeferAnomaly() --------------------------- reeferId:"+ reeferId
                               + " unknown target type:"+target.getTargetType());
                }
            } else {
                System.out.println("AnomalyManagerActor.reeferAnomaly() - !!!!!!!!!!! reeferId:" + reeferId + " Not Found in inventory");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Remote
    public void voyageDeparted(JsonObject message) {
        update(message);
        //  System.out.println("AnomalyManagerActor.voyageDeparted() - updated targets for " + count + " reefers");
    }

    @Remote
    public void voyageArrived(JsonObject message) {
        update(message);
        // System.out.println("AnomalyManagerActor.voyageArrived() - updated targets for " + count + " reefers");
    }

    private void update(JsonObject message) {
        try {
            long t = System.currentTimeMillis();
            JsonArray ja = message.getJsonArray(Constants.TARGETS_KEY);
            for (JsonValue target : ja ) {
                String anomalyTarget = target.asJsonObject().getJsonString(Constants.ANOMALY_TARGET_KEY).getString();
                int targetType = target.asJsonObject().getInt(Constants.ANOMALY_TARGET_TYPE_KEY);
                String reeferIds = target.asJsonObject().getJsonString(Constants.REEFERS_KEY).getString();
                String[] rids = reeferIds.split(",");
                //      System.out.println("AnomalyManagerActor.update() - "+rids.length+" reefers to update - target:"+anomalyTarget);
                JsonObjectBuilder job = Json.createObjectBuilder();
                for (String reeferId : rids) {
                    if (reefersMap.containsKey(reeferId)) {
                        ReeferLocation targetLocation = reefersMap.get(reeferId);
                        targetLocation.setTarget(anomalyTarget);
                        targetLocation.setTargetType(targetType);
                    }
                }
            }
            String serializedReeferTargets = serializeReefers();
            Kar.Actors.State.set(this, Constants.REEFERS_KEY, Json.createValue(serializedReeferTargets));

       //     System.out.println("AnomalyManagerActor.update() - redis save took "+(System.currentTimeMillis() - t) + " "+ reeferUpdatedCount +" reefers changed location" );
        } catch (Exception e) {
            System.out.println("AnomalyManagerActor.update() - Error:" + e.getMessage());
            e.printStackTrace();
        }

    }

    @Remote
    public void anomaly(JsonObject message) {
        System.out.println("AnomalyManager.anomaly()");
    }

    public static class ReeferLocation {
        public enum LocationType {
            DEPOT(1),
            ORDER(2);
            public final int type;

            private LocationType(int type) {
                this.type = type;
            }

            public int getType() {
                return type;
            }
        }

        ;
        private int id;
        private String target;
        private int targetType;

        public ReeferLocation(int id, String target, int targetType) {
            this.id = id;
            this.target = target;
            this.targetType = targetType;
        }

        public int getId() {
            return id;
        }

        public String getTarget() {
            return target;
        }

        public int getTargetType() {
            return targetType;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public void setTargetType(int targetType) {
            this.targetType = targetType;
        }
    }
}
