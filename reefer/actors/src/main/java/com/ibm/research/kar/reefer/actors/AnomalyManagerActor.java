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
import com.ibm.research.kar.reefer.common.ReeferLoggerFormatter;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.json.*;
import javax.validation.constraints.Null;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.*;

@Actor
public class AnomalyManagerActor extends BaseActor {
   private int ARRIVAL=1;
   private int DEPARTURE=2;
   private int REPLACE=3;
   private Map<String, ReeferLocation> reefersMap = null;
   // Map supporting lookup of depot enum value given its name
   private Map<String, JsonValue> depotEnumMap = new LinkedHashMap<>();
   // Map supporting lookup of depot name given its enum value
   private Map<Integer, String> reverseDepotEnumMap = new LinkedHashMap<>();
   // Map supporting lookup of ship's enum value given its name
   private Map<String, JsonValue> vesselEnumMap = new LinkedHashMap<>();
   // Map supporting lookup of ship's name given its enum value
   private Map<Integer, String> reverseVesselEnumMap = new LinkedHashMap<>();
   private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(AnomalyManagerActor.class.getName());

   @Activate
   public void activate() {
      long t1 = System.currentTimeMillis();
      try {
         ActorRef scheduleActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
         JsonValue reply = Kar.Actors.call(scheduleActor, "getVessels");
         if (reply != null && reply != JsonValue.NULL) {
            // assign enum value to each ship
            int enumValue = 1;
            for (JsonValue vessel : reply.asJsonArray()) {
               vesselEnumMap.put(((JsonString) vessel).getString(), Json.createValue(enumValue));
               reverseVesselEnumMap.put(enumValue, ((JsonString) vessel).getString());
               enumValue++;
            }
            logger.info("AnomalyManagerActor.activate() - vesselEnumMap.size() = "+vesselEnumMap.size());
         } else {
            throw new IllegalStateException("AnomalyManagerActor.activate() - failed to fetch a list of vessels from the Schedule Manager");
         }
         Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
         if (logger.isLoggable(Level.FINEST)) {
            logger.finest("AnomalyManagerActor.activate() -  fetched all state in " + (System.currentTimeMillis() - t1));
         }
         if (!state.isEmpty()) {
            if (state.containsKey(Constants.TARGET_MAP_KEY)) {
               depotEnumMap = state.get(Constants.TARGET_MAP_KEY).asJsonObject();
               for (Map.Entry<String, JsonValue> entry : depotEnumMap.entrySet()) {
                  reverseDepotEnumMap.put(((JsonNumber) entry.getValue()).intValue(), entry.getKey());
               }
            }
            if (state.containsKey(Constants.REEFERS_KEY)) {
               String reeferTargets = ((JsonString) state.get(Constants.REEFERS_KEY)).getString();
               instantiateReeferTargetMap(reeferTargets);
            }
         }
      } catch (Throwable t) {
         String stacktrace = ExceptionUtils.getStackTrace(t).replaceAll("\n","");
         logger.log(Level.SEVERE,"AnomalyManagerActor.activate() "+stacktrace);
      }
      if (logger.isLoggable(Level.FINEST)) {
         logger.finest("AnomalyManagerActor.activate() -  total time to recover state: " + (System.currentTimeMillis() - t1));
      }
   }

   private void instantiateReeferTargetMap(String reeferTargets) {
      reefersMap = new LinkedHashMap<>();
      // ReeferLocation instances are restored from a stringified list where
      // each entry is encoded as follows: <REEFERID:int>|<DEPOTID:int>|<TYPE:int>
      // where TYPE[1,2] 1: Depot, 2: VOYAGE. Example:
      // 0|1|1
     // String reeferTargets = ((JsonString) state.get(Constants.REEFERS_KEY)).getString();
      if (reeferTargets != null) {
         long t1 = System.currentTimeMillis();
         String[] targets = reeferTargets.split(",");

         for (String target : targets) {
            String[] props = target.split("\\|");
            ReeferLocation rl = null;

            if (Constants.DEPOT_TARGET_TYPE == Integer.parseInt(props[2])) {
               // use reverseDepotEnumMap to decode depot name using depot enum value
               rl = new ReeferLocation(Integer.parseInt(props[0]), reverseDepotEnumMap.get(Integer.parseInt(props[1])), Integer.parseInt(props[2]));
            } else if (Constants.VOYAGE_TARGET_TYPE == Integer.parseInt(props[2])) {
               // voyage is stored persistently as [enum value(int):date(string)]
               String[] voyageParts = props[1].split(":");
               String voyage = reverseVesselEnumMap.get(Integer.parseInt(voyageParts[0].trim())) + ":" + voyageParts[1];
               rl = new ReeferLocation(Integer.parseInt(props[0]), voyage, Integer.parseInt(props[2]));
            } else {
               throw new IllegalStateException("AnomalyManagerActor.instantiateReeferTargetMap() - unexpected reefer target type:" + Integer.parseInt(props[2]) +
                       " - should be either " + Constants.DEPOT_TARGET_TYPE + " or " + Constants.VOYAGE_TARGET_TYPE);
            }
            reefersMap.put(props[0], rl);
         }
         logger.info("AnomalyManagerActor.instantiateReeferTargetMap() + restored reefers map - size: "+reefersMap.size());
         if (logger.isLoggable(Level.FINEST)) {
            logger.finest("AnomalyManagerActor.instantiateReeferTargetMap() - time to instantiate reeferMap from state took:" + (System.currentTimeMillis() - t1) + " size:" + reefersMap.size());
         }
      } else {
         if (logger.isLoggable(Level.WARNING)) {
            logger.warning("AnomalyManagerActor.instantiateReeferTargetMap() - reeferTargets not defined ");
         }
      }

   }

   @Remote
   public void depotReefers(JsonObject depotReefers) {
      long t = System.currentTimeMillis();
      try {
         JsonArray ja = depotReefers.getJsonArray(Constants.DEPOTS_KEY);
         int totalCount = depotReefers.getInt(Constants.TOTAL_REEFER_COUNT_KEY);
         StringBuilder sb = new StringBuilder();
         reefersMap = new LinkedHashMap<>(totalCount);
         int depotEnumValue = 1;
         for (JsonValue depot : ja) {
            long t1 = System.currentTimeMillis();
            String target = depot.asJsonObject().getString(Constants.ANOMALY_TARGET_KEY);
            depotEnumMap.put(target, Json.createValue(depotEnumValue));
            reverseDepotEnumMap.put(depotEnumValue, target);
            for (JsonValue jsonShard : depot.asJsonObject().getJsonArray(Constants.SHARDS_KEY)) {
               int lower = jsonShard.asJsonObject().getInt("reefer-id-lower-bound");
               int upper = jsonShard.asJsonObject().getInt("reefer-id-upper-bound");

               sb.append(createReefers(target, depotEnumValue, lower, upper));
            }
            depotEnumValue++;
         }
         long t2 = System.currentTimeMillis();
         Kar.Actors.State.set(this, Constants.REEFERS_KEY, Json.createValue(sb.toString()));
         Kar.Actors.State.Submap.set(this, Constants.TARGET_MAP_KEY, depotEnumMap);
         System.out.println("AnomalyManagerActor.depotReefers() - saved depot reefers - total time:" + (System.currentTimeMillis() - t2) + " state size (KB):" + (sb.length() / 1024));
      } catch (Exception e) {
         String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
         logger.log(Level.SEVERE,"AnomalyManagerActor.depotReefers() "+stacktrace);
      }
   }

   private String createReefers(String depot, int depotId, int lowerRange, int upperRange) {
      StringBuilder sb = new StringBuilder();
      try {
         for (int reeferId = lowerRange; reeferId <= upperRange; reeferId++) {
            sb.append(reeferId).append("|").append(depotId).append("|").append(Constants.DEPOT_TARGET_TYPE).append(",");
            reefersMap.put(String.valueOf(reeferId), new ReeferLocation(reeferId, depot, ReeferLocation.LocationType.DEPOT.getType()));
         }
      } catch (Exception e) {
         String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
         logger.log(Level.SEVERE,"AnomalyManagerActor.createReefers() "+stacktrace );
      }
      return sb.toString();
   }

   private String serializeReefers() {
      StringBuilder sb = new StringBuilder();

      for (String reeferId : reefersMap.keySet()) {
         ReeferLocation reeferLocation = reefersMap.get(reeferId);
         sb.append(reeferId).append("|");
         if (Constants.DEPOT_TARGET_TYPE == reeferLocation.getTargetType()) {
            // to reduce space in persistent storage use enum value for each depot name.
            JsonValue depotId = depotEnumMap.get(reeferLocation.getTarget());
            sb.append(((JsonNumber) depotId).intValue());
         } else if (Constants.VOYAGE_TARGET_TYPE == reeferLocation.getTargetType()) {
            try {
               String[] voyageParts = reeferLocation.getTarget().split(":");
               JsonValue jv = vesselEnumMap.get(voyageParts[0]);
               sb.append(((JsonNumber) jv).intValue()).append(":").append(voyageParts[1]);
            } catch (Exception e) {
               logger.log(Level.WARNING,
                          String.format("AnomalyManagerActor.serializeReefers() - failed serializing voyage - target: %s reeferId:",reeferLocation.getTarget(), reeferId));
               throw e;
            }
         } else {
            logger.log(Level.SEVERE,
                    String.format("AnomalyManagerActor.serializeReefers() - unable to determine enumeration value for target:" + reeferLocation.getTarget()));
            throw new IllegalStateException("AnomalyManager.serializeReefers() - unable to determine enumeration value for target:" + reeferLocation.getTarget());
         }
         sb.append("|").append(reeferLocation.getTargetType()).append(",");
      }
      return sb.toString();
   }

   @Remote
   public void reeferAnomaly(JsonObject message) {
      try {
         String reeferId = String.valueOf(message.getInt(Constants.REEFER_ID_KEY));
         if (reefersMap.containsKey(reeferId)) {
            ReeferLocation target = reefersMap.get(reeferId);
            int targetType = target.getTargetType();;
            // if a message contains value with key Constants.TARGET_KEY it means that the target (depot or voyage) received anomaly
            // but it no longer has the reefer and anomaly was sent back. To avoid sending the anomaly there again just
            // drop it.
            if ( message.containsKey(Constants.TARGET_KEY) &&  message.getInt(Constants.TARGET_KEY) != target.targetType ) {
              return;
            }
            ActorRef targetActor;
            switch (targetType) {
               case Constants.DEPOT_TARGET_TYPE:
                  targetActor = Kar.Actors.ref(ReeferAppConfig.DepotActorType, target.getTarget());
                  Kar.Actors.tell(targetActor, "reeferAnomaly", message);
                  break;
               case Constants.VOYAGE_TARGET_TYPE:
                  targetActor = Kar.Actors.ref(ReeferAppConfig.VoyageActorType, target.getTarget());
                  Kar.Actors.tell(targetActor, "reeferAnomaly", message);
                  break;
               default:
                  logger.log(Level.WARNING,
                             String.format("AnomalyManagerActor.reeferAnomaly() --------------------------- reeferId: %s unknown target type:",reeferId,target.getTargetType()));

            }
         } else {
            logger.log(Level.WARNING,
                    "AnomalyManagerActor.reeferAnomaly() - !!!!!!!!!!! reeferId:" + reeferId + " Not Found in inventory");
         }
      } catch (Exception e) {
         String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
            logger.log(Level.SEVERE, "AnomalyManagerActor.reeferAnomaly() "+stacktrace);
      }
   }

   @Remote
   public void voyageDeparted(JsonObject message) {
      update(message, DEPARTURE);
   }

   @Remote
   public void voyageArrived(JsonObject message) {
      update(message, ARRIVAL);
   }

   @Remote
   public void updateLocation(JsonObject message) {
      if ( logger.isLoggable(Level.FINE)) {
         logger.log(Level.FINE, "AnomalyManagerActor.updateLocation() - message: " + message);
      }
      update(message, REPLACE);
   }

   private void update(JsonObject message, int eventType) {
      try {
         long t = System.currentTimeMillis();
         String anomalyTarget = message.getJsonString(Constants.ANOMALY_TARGET_KEY).getString();
         int targetType = message.getInt(Constants.ANOMALY_TARGET_TYPE_KEY);
         String reeferIds = message.getJsonString(Constants.REEFERS_KEY).getString();
         String voyageId = message.getJsonString(Constants.VOYAGE_ID_KEY).getString();

         String[] rids = reeferIds.split(",");
         String event = "NA";
         if ( eventType == DEPARTURE ) {
            event = "DEPARTED";
         } else if ( eventType == ARRIVAL ) {
            event = "ARRIVED";
         } else {
            event = "REPLACED";
         }
         logger.log(Level.WARNING, "AnomalyManagerActor.update() - voyage:"+voyageId+" "+event+" total reefers="+rids.length+" newTargetType:"+targetType+" newTarget:"+anomalyTarget);
         int updateCount=0;
         int voyageReeferCount=0;
         if ( eventType == ARRIVAL ) {
            for (Map.Entry<String, ReeferLocation> entry : reefersMap.entrySet()) {
               if (entry.getValue().getTargetType() == ReeferLocation.LocationType.VOYAGE.getType()
                       && entry.getValue().getTarget().equals(voyageId)) {
                  voyageReeferCount++;
               }
            }
         }
         
         for (String reeferId : rids) {
            if (reefersMap.containsKey(reeferId)) {
               updateCount++;
               ReeferLocation targetLocation = reefersMap.get(reeferId);
               targetLocation.setTarget(anomalyTarget);
               targetLocation.setTargetType(targetType);
            } else {
               logger.log(Level.WARNING, "AnomalyManagerActor.update() - voyage:"+voyageId+" reefer: " + reeferId + " not in the map");
            }
         }
         String serializedReeferTargets = serializeReefers();
         Kar.Actors.State.set(this, Constants.REEFERS_KEY, Json.createValue(serializedReeferTargets));

         int extraVoyageReefers=0;
         logger.log(Level.WARNING, "AnomalyManagerActor.update() - voyage:"+voyageId+" "+event+" total reefers="+rids.length+" updateCount:"+updateCount+" voyageReeferCount:"+voyageReeferCount);
         if ( eventType == ARRIVAL ) {
            for(Map.Entry<String,ReeferLocation> entry: reefersMap.entrySet()) {
               if ( entry.getValue().getTargetType() == ReeferLocation.LocationType.VOYAGE.getType()
                       && entry.getValue().getTarget().equals(voyageId)) {
                  boolean exists = false;
                  extraVoyageReefers++;
                  for (String reeferId : rids) {
                     if (entry.getKey().equals(reeferId)) {
                        exists = true;
                        break;
                     }
                  }
                  logger.log(Level.SEVERE, "AnomalyManagerActor.update() - reefer: " + entry.getKey() + " is still associated with voyage:"+voyageId+" which has arrived - reefer provided: "+exists+" rids.size= "+rids.length);
               }
            }
         }
         if ( extraVoyageReefers > 0) {
            logger.log(Level.WARNING, "AnomalyManagerActor.update() - voyage:"+voyageId+" "+event+" total reefers="+rids.length+" updateCount:"+updateCount+" voyageReeferCount:"+voyageReeferCount+" extraVoyageReefers:"+extraVoyageReefers);
         }
         if ( logger.isLoggable(Level.FINE)) {
            if ( AnomalyManagerActor.ReeferLocation.LocationType.DEPOT.getType() == targetType) {
               logger.fine("AnomalyManagerActor.update() - Depot: "+anomalyTarget+" received "+rids.length+" reefers");
            } else {
               logger.fine("AnomalyManagerActor.update() - Voyage:"+anomalyTarget+" added "+rids.length+" reefers");
            }
         }
      } catch (Exception e) {
         String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
         logger.log(Level.SEVERE, "AnomalyManagerActor.update() - Error: "+stacktrace);
      }

   }

   public static class ReeferLocation {
      public enum LocationType {
         DEPOT(Constants.DEPOT_TARGET_TYPE),
         VOYAGE(Constants.VOYAGE_TARGET_TYPE);
         public final int type;

         LocationType(int type) {
            this.type = type;
         }

         public int getType() {
            return type;
         }
      }

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
