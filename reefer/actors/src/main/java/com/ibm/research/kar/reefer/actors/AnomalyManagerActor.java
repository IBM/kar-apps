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
import java.util.TreeMap;

@Actor
public class AnomalyManagerActor extends BaseActor {
    private Map<String, JsonValue> reefersMap = null;

    @Activate
    public void activate() {
        try {
            //System.out.println("AnomalyManagerActor.activate() -------------------- ");
            long t1 = System.currentTimeMillis();
            Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
            System.out.println("AnomalyManagerActor.activate() -  fetched all state in" + (System.currentTimeMillis() - t1));
            if (!state.isEmpty()) {
                if (state.containsKey(Constants.REEFER_MAP_KEY)) {
                    reefersMap = new LinkedHashMap<>();
                    long t2 = System.currentTimeMillis();
                    reefersMap.putAll(state.get(Constants.REEFER_MAP_KEY).asJsonObject());
                    System.out.println("AnomalyManagerActor.activate() -  restored reefers map - size:" + reefersMap.size() + " in " + (System.currentTimeMillis() - t2));
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

    }

    @Remote
    public void depotReefers(JsonObject depotReefers) {
        TreeMap<Integer, String> shardMap = new TreeMap<>();
        // System.out.println("AnomalyManagerActor.depotReefers() - " + depotReefers);
        long t = System.currentTimeMillis();
        try {
            JsonArray ja = depotReefers.getJsonArray(Constants.DEPOTS_KEY);
            int totalCount = depotReefers.getInt(Constants.TOTAL_REEFER_COUNT_KEY);
            reefersMap = new LinkedHashMap<>(totalCount + 1);
            for (JsonValue depot : ja) {
                long t1 = System.currentTimeMillis();
                int lower = depot.asJsonObject().getInt("reefer-id-lower-bound");
                int upper = depot.asJsonObject().getInt("reefer-id-upper-bound");
                Map<String, JsonValue> updateMap =
                        addDepotReefers(depot.asJsonObject().getString(Constants.ANOMALY_TARGET_KEY), lower, upper);
                long t2 = System.currentTimeMillis();
                shardMap.put(lower, depot.asJsonObject().getString(Constants.ANOMALY_TARGET_KEY));
                Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, updateMap);
                long t3 = System.currentTimeMillis();
                reefersMap.putAll(updateMap);
                System.out.println("AnomalyManagerActor.depotReefers() - depot:" + depot.asJsonObject().getString(Constants.ANOMALY_TARGET_KEY) +
                        " gen time:" + (t2 - t1) + " save time:" + (t3 - t2) + " map put time:" + (System.currentTimeMillis() - t3) +
                        " reefersMapSize size:" + reefersMap.size() + " update map size:" + updateMap.size() + " total time:" + (System.currentTimeMillis() - t1));
                updateMap.clear();
            }
            // in unlikely case reeferId is out of range, return null for target
            shardMap.put(totalCount, null);
            System.out.println("AnomalyManagerActor.depotReefers() - saved depot reefers - total time:" + (System.currentTimeMillis() - t));
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private Map<String, JsonValue> addDepotReefers(String depot, int lowerRange, int upperRange) {
        //    System.out.println("AnomalyManagerActor.addDepotReefers()");
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
                JsonObject target = reefersMap.get(reeferId).asJsonObject();
                //    System.out.println("AnomalyManagerActor.reeferAnomaly() - ANOMALY :::::::: ID:" + reeferId +
                //            " TARGET >>>>>>>> " + target.getString(Constants.TARGET_KEY) + " SHARD:" +shardMap.floorEntry(Integer.valueOf(reeferId))+" TARGET TYPE:" + target.getInt(Constants.TARGET_TYPE_KEY));
                ActorRef targetActor;
                switch (target.getInt(Constants.TARGET_TYPE_KEY)) {
                    case 1:
                        targetActor = Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, target.getString(Constants.TARGET_KEY));
                        Kar.Actors.tell(targetActor, "reeferAnomaly", message);
                        break;
                    case 2:
                        targetActor = Kar.Actors.ref(ReeferAppConfig.OrderActorName, target.getString(Constants.TARGET_KEY));
                        Kar.Actors.tell(targetActor, "reeferAnomaly", message);
                        break;
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
        int count = update(message);
        //  System.out.println("AnomalyManagerActor.voyageDeparted() - updated targets for " + count + " reefers");
    }

    @Remote
    public void voyageArrived(JsonObject message) {
        int count = update(message);
        // System.out.println("AnomalyManagerActor.voyageArrived() - updated targets for " + count + " reefers");
    }

    private int update(JsonObject message) {
        Map<String, JsonValue> updateMap = new HashMap<>();
        try {
            JsonArray ja = message.getJsonArray(Constants.TARGETS_KEY);
            ja.forEach(target -> {
                String anomalyTarget = target.asJsonObject().getJsonString(Constants.ANOMALY_TARGET_KEY).getString();
                int targetType = target.asJsonObject().getInt(Constants.ANOMALY_TARGET_TYPE_KEY);
                String reeferIds = target.asJsonObject().getJsonString(Constants.REEFERS_KEY).getString();
                String[] rids = reeferIds.split(",");
                //      System.out.println("AnomalyManagerActor.update() - "+rids.length+" reefers to update - target:"+anomalyTarget);
                JsonObjectBuilder job = Json.createObjectBuilder();
                for (String reeferId : rids) {
                    JsonObject targetObject = reeferTarget(String.valueOf(reeferId), anomalyTarget, targetType, job);
                    updateMap.put(reeferId, targetObject);
                    if (reefersMap.containsKey(reeferId)) {
                        reefersMap.put(reeferId, targetObject);
                    }
                }
                //Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, updateMap);
                // updateMap.clear();
            });
            Kar.Actors.State.Submap.set(this, Constants.REEFER_MAP_KEY, updateMap);
        } catch (Exception e) {
            System.out.println("AnomalyManagerActor.update() - Error:" + e.getMessage());
            e.printStackTrace();
        }
        return updateMap.size();
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
