package com.ibm.research.kar.reefer.actors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.*;
import com.ibm.research.kar.reefer.common.error.VoyageNotFoundException;
import com.ibm.research.kar.reefer.common.json.JsonUtils;
import com.ibm.research.kar.reefer.common.json.RouteJsonSerializer;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Vessel;
import com.ibm.research.kar.reefer.model.Voyage;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.json.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Actor
public class ScheduleManagerActor extends BaseActor {
    private Routes routes = new Routes();
    private ScheduleService schedule;
    private Instant baseDate;
    private long reeferInventorySize;
    private JsonNumber reefersInTransit = Json.createValue(0);
    private ActiveVoyageManager activeVoyageManager;
    private static Logger logger = ReeferLoggerFormatter.getFormattedLogger(ScheduleManagerActor.class.getName());

    @Activate
    public void activate() {
        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        try {

            int fleetSize = getFleetSize(state);
            schedule = new ScheduleService(new ShippingScheduler(fleetSize));
            reeferInventorySize = FleetCapacity.totalSize(schedule.getRoutes());
            if (state.containsKey(Constants.REEFERS_IN_TRANSIT_COUNT_KEY)) {
                JsonValue metrics = state.get(Constants.REEFERS_IN_TRANSIT_COUNT_KEY);
                if (metrics != null && metrics != JsonValue.NULL) {
                    reefersInTransit = (JsonNumber)metrics;
                }
            }

            JsonValue baseDateValue = state.get(Constants.SCHEDULE_BASE_DATE_KEY);
            Instant lastScheduleDate;
            List<Voyage> restoredActiveList = new LinkedList<>();
            if (Objects.isNull(baseDateValue)) {
                lastScheduleDate = coldStart();
            } else {
                lastScheduleDate = warmStart(state, baseDateValue);
                if (state.containsKey(Constants.ACTIVE_VOYAGES_KEY)) {
                    Map<String, JsonValue> activeVoyages = new HashMap<>();
                    activeVoyages.putAll(state.get(Constants.ACTIVE_VOYAGES_KEY).asJsonObject());

                    for(JsonValue jv: activeVoyages.values()) {
                        Voyage restoredVoyage = VoyageJsonSerializer.deserialize(jv.asJsonObject());
                        restoredActiveList.add( restoredVoyage );
                    }
                    activeVoyageManager = new ActiveVoyageManager(schedule, restoredActiveList);
                    logger.info("ScheduleManagerActor.activate() - restored active voyages - current size:"+restoredActiveList.size());
                }

            }
            if ( activeVoyageManager == null ) {
                activeVoyageManager = new ActiveVoyageManager(schedule, new LinkedList<>());
            } else {
                restoreActiveVoyages(restoredActiveList);
            }
            Kar.Actors.State.set(this, Constants.SCHEDULE_END_DATE_KEY, Json.createValue(lastScheduleDate.toString()));
            logger.info("ScheduleManagerActor.activate() ++++ Saved End Date:" + lastScheduleDate);
            Kar.Actors.State.set(this, Constants.REEFERS_IN_TRANSIT_COUNT_KEY, reefersInTransit);
            logger.info("ScheduleManagerActor.activate() - actor type:" + this.getType() + " generated routes - size:" + schedule.getRoutes().size());
            Kar.Actors.Reminders.schedule(this, "publishSpoiltReeferMetrics", "VoyageManagerReminder",
                    Instant.now().plus(1, ChronoUnit.SECONDS), Duration.ofSeconds(5));


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Remote
    public JsonValue getVessels() throws Exception {
        JsonArrayBuilder jab = Json.createArrayBuilder();
        List<Vessel> vessels = schedule.getVessels();
        for (Vessel vessel : vessels ) {
            jab.add(vessel.getName());
        }
        JsonArray ja = jab.build();
        return ja;
    }
    @Remote
    public void publishSpoiltReeferMetrics() {
        int totalSpoiltReeferCount = 0;
        try {
            List<Voyage> activeVoyages = activeVoyageManager.getActiveVoyages();
            for (Voyage voyage : activeVoyages) {
                try {
                    ActorRef voyageActorRef = Kar.Actors.ref(ReeferAppConfig.VoyageActorType, voyage.getId());
                    JsonValue voyageSpoiltMetrics = Kar.Actors.State.get(voyageActorRef, Constants.TOTAL_SPOILT_KEY);
                    if (voyageSpoiltMetrics != null && voyageSpoiltMetrics != JsonValue.NULL) {
                        totalSpoiltReeferCount += ((JsonNumber) voyageSpoiltMetrics).intValue();
                    }
                } catch (Exception e) {
                    // voyage may have already arrived and was removed
                    logger.log(Level.WARNING, "ScheduleManagerActor.publishSpoiltReeferMetrics()", e);
                }
            }
            Kar.Actors.State.set(this, Constants.TOTAL_SPOILT_KEY, Json.createValue(totalSpoiltReeferCount));
        } catch( Exception e) {
            e.printStackTrace();
        }

    }

    private Instant coldStart() {
        Instant currentDate;
        Instant lastVoyageDate;
        baseDate = currentDate = TimeUtils.getInstance().getCurrentDate();
        lastVoyageDate = TimeUtils.getInstance().getDateYearFrom(TimeUtils.getInstance().getCurrentDate());
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.SCHEDULE_BASE_DATE_KEY, Json.createValue(currentDate.toString()));
        job.add(Constants.CURRENT_DATE_KEY, Json.createValue(currentDate.toString()));

        Kar.Actors.State.set(this, job.build());
        return schedule.generateShipSchedule(baseDate, currentDate, lastVoyageDate);
    }

    private Instant warmStart(Map<String, JsonValue> state, JsonValue baseDateValue) {
        Instant currentDate;
        Instant lastVoyageDate;

        baseDate = Instant.parse(((JsonString) baseDateValue).getString());
        JsonValue date = state.get(Constants.CURRENT_DATE_KEY);
        Instant cd = Instant.parse(((JsonString) date).getString());
        logger.info("ScheduleManagerActor.warmStart() - current date from REDIS:" + date + " cd:" + cd);
        currentDate = TimeUtils.getInstance(cd).getCurrentDate();
        lastVoyageDate = Instant.parse(((JsonString) state.get(Constants.SCHEDULE_END_DATE_KEY)).getString());
        logger.info("ScheduleManagerActor.warmStart() - Restored Current Date:" + currentDate + " baseDate:" + baseDate + " endDate:" + lastVoyageDate);
        return schedule.generateShipSchedule(baseDate, currentDate, lastVoyageDate);

    }

    private int getFleetSize(Map<String, JsonValue> state) {
        Map<String, String> env = System.getenv();
        int fleetSize = 10;

        if (env.containsKey(Constants.REEFER_FLEET_SIZE_KEY) &&
                env.get(Constants.REEFER_FLEET_SIZE_KEY) != null &&
                env.get(Constants.REEFER_FLEET_SIZE_KEY).trim().length() > 0) {
            fleetSize = Integer.parseInt(env.get(Constants.REEFER_FLEET_SIZE_KEY));
        }

        // if its a warm start, restore fleet size to previous value. User may change the fleet size through
        // env variable but the code below ignores it if its different from the previous size.
        JsonValue jv = state.get(Constants.REEFER_FLEET_SIZE_KEY);
        if (jv != null && jv != JsonValue.NULL) {
            if (fleetSize != ((JsonNumber) jv).intValue()) {
                logger.info("ScheduleManagerActor.activate() - Warm start - using previously saved fleet size of " + ((JsonNumber) jv).intValue());
                fleetSize = ((JsonNumber) jv).intValue();
            }
        } else {
            Kar.Actors.State.set(this, Constants.REEFER_FLEET_SIZE_KEY, Json.createValue(fleetSize));
            logger.info("ScheduleManagerActor.activate() ++++++++++++ saved fleet size:" + fleetSize);
        }
        return fleetSize;
    }

    private void restoreActiveVoyages( List<Voyage> activeVoyages ) {
         int inTransit = 0;
        for (Voyage recoveredVoyageState : activeVoyages) {
            try {
                Voyage voyage = schedule.getVoyage(recoveredVoyageState.getId());
                voyage.setOrderCount(recoveredVoyageState.getOrderCount());
                voyage.changePosition(Long.valueOf(recoveredVoyageState.getRoute().getVessel().getPosition()).intValue());
                voyage.setReeferCount(recoveredVoyageState.getReeferCount());
                voyage.setFreeCapacity(recoveredVoyageState.getRoute().getVessel().getFreeCapacity());
                inTransit += recoveredVoyageState.getReeferCount();
            } catch( VoyageNotFoundException r) {
                logger.warning("ScheduleManagerActor.restoreActiveVoyages() - voyage:"+recoveredVoyageState.getId()+" not in master schedule - current date:"+TimeUtils.getInstance().getCurrentDate());
            }
        }
        reefersInTransit = Json.createValue(inTransit);
        Kar.Actors.State.set(this, Constants.REEFERS_IN_TRANSIT_COUNT_KEY, reefersInTransit);
    }

    private Optional<JsonObject> recoverVoyage(String voyageId) {
        ActorRef voyageActorRef = Kar.Actors.ref(ReeferAppConfig.VoyageActorType, voyageId);
        JsonValue jv = Kar.Actors.State.get(voyageActorRef, Constants.VOYAGE_INFO_KEY);
        if (jv == null || jv == JsonValue.NULL) {
            return Optional.empty();
        }
        return Optional.of(jv.asJsonObject());
    }

    @Remote
    public JsonValue startDate() {
        return Json.createValue(TimeUtils.getInstance().getStartDate().toString());
    }

    @Remote
    public JsonValue currentDate() {
        return Json.createValue(TimeUtils.getInstance().getCurrentDate().toString());
    }

    @Remote
    public JsonValue tomorrowsDate() {
        Instant currentDate = TimeUtils.getInstance().getCurrentDate();
        return Json.createValue(currentDate.plus(1, ChronoUnit.DAYS).toString());
    }

    @Remote
    public JsonObject advanceDate() {
        Instant today = TimeUtils.getInstance().advanceDate(1);
        Kar.Actors.State.set(this, Constants.CURRENT_DATE_KEY, Json.createValue(today.toString()));

        try {
            Instant lastDepartureDate = schedule.lastVoyageDepartureDate();
            long daysBetween = TimeUtils.getInstance().getDaysBetween(today, lastDepartureDate);
            if (daysBetween < schedule.THRESHOLD_IN_DAYS) {
                // On a day change generate a future schedule if necessary. The new schedule is generated if
                // we reached a configured threshold of days before the end of current schedule.
                Instant scheduleEndDate = schedule.extendSchedule(baseDate, lastDepartureDate, today);
                Kar.Actors.State.set(this, Constants.SCHEDULE_END_DATE_KEY, Json.createValue(scheduleEndDate.toString()));
            }
            JsonObject message = Json.createObjectBuilder().add(Constants.DATE_KEY, Json.createValue(today.toString()))
                    .build();
            // Reefers on maintenance are freed automatically after a configurable number of days passes.
            ActorRef depotManagerActor = Kar.Actors.ref(ReeferAppConfig.DepotManagerActorType, ReeferAppConfig.DepotManagerId);
            Kar.Actors.tell(depotManagerActor, "newDay", message);

        } catch (Exception e) {
            logger.log(Level.WARNING, "ScheduleManagerActor.advanceDate()", e);
        }
        JsonObjectBuilder reply = Json.createObjectBuilder();
        return reply.add(Constants.STATUS_KEY, Constants.OK).add(Constants.CURRENT_DATE_KEY, today.toString()).build();
    }

    @Remote
    public JsonValue reeferInventorySize() {
        return Json.createValue(reeferInventorySize);
    }

    @Remote
    public void updateVoyage(JsonObject message) {
        try {
            Voyage voyage = VoyageJsonSerializer.deserialize(message);
            Voyage scheduledVoyage = schedule.getVoyage(voyage.getId());
            scheduledVoyage.setFreeCapacity(voyage.getRoute().getVessel().getFreeCapacity());
            scheduledVoyage.setOrderCount(voyage.getOrderCount());
            scheduledVoyage.setReeferCount((voyage.getReeferCount()));
        } catch (Exception e) {
            logger.log(Level.WARNING,"ScheduleManagerActor.updateVoyage()",e);
        }
    }

    @Remote
    public JsonValue activeVoyages() {
        List<Voyage> activeVoyages = activeVoyageManager.getActiveVoyages();
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ScheduleManagerActor.activeVoyages() - active voyages list size:" + activeVoyages.size());
        }
        return voyageListToJsonArray(activeVoyages);
    }

    @Remote
    public JsonObject activeSchedule() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.CURRENT_DATE_KEY, TimeUtils.getInstance().getCurrentDate().toString()).
                add(Constants.ACTIVE_VOYAGES_KEY, activeVoyages());//voyageListToJsonArray(schedule.getActiveSchedule()));
        return job.build();
    }

    private JsonArray voyageListToJsonArray(List<Voyage> voyages) {
        JsonArrayBuilder jab = Json.createArrayBuilder();
        voyages.forEach(voyage -> {
            if (voyage.getProgress() == 0 && voyage.getOrderCount() == 0) {
                Optional<JsonObject> actualVoyageState = recoverVoyage(voyage.getId());
                if (actualVoyageState.isPresent()) {
                    Voyage recoveredVoyageState = VoyageJsonSerializer.deserialize(actualVoyageState.get());
                    voyage.setOrderCount(recoveredVoyageState.getOrderCount());
                    voyage.setReeferCount(recoveredVoyageState.getReeferCount());
                    voyage.setFreeCapacity(recoveredVoyageState.getRoute().getVessel().getFreeCapacity());
                }
            }
            jab.add(VoyageJsonSerializer.serialize(voyage));
        });
        JsonArray ja = jab.build();
        return ja;
    }

    @Remote
    public JsonValue voyagesInRange(JsonObject message) {
        Instant startDate;
        Instant endDate;

        startDate = Instant.parse(message.getString("startDate"));
        endDate = Instant.parse(message.getString("endDate"));
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ScheduleManagerActor.getVoyagesInRange() - startDate:" + startDate.toString() + " endDate:"
                    + endDate.toString());
        }

        return voyageListToJsonArray(schedule.getMatchingSchedule(startDate, endDate));
    }

    @Remote
    public JsonValue matchingVoyages(JsonObject message) {
        String originPort = "";
        String destinationPort = "";
        Instant date = null;
        String departureDate = message.getString("departureDate");
        date = Instant.parse(departureDate);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("ScheduleManagerActor.getMatchingVoyages() - origin:" + originPort + " destination:"
                    + destinationPort + " date:" + departureDate);
        }
        return voyageListToJsonArray(schedule.getMatchingSchedule(message.getString("origin"), message.getString("destination"), date));
    }

    @Remote
    public void voyageDeparted(JsonObject message) {
        try {
            Voyage voyage = VoyageJsonSerializer.deserialize(message);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("ScheduleManagerActor.voyageDeparted() - id:" + voyage.getId() + " message:" + message);
            }
            Voyage activeVoyage = schedule.updateDaysAtSea(voyage.getId(), Long.valueOf(voyage.getRoute().getVessel().getPosition()).intValue());
            activeVoyage.setOrderCount(voyage.getOrderCount());
            activeVoyage.setFreeCapacity(voyage.getRoute().getVessel().getFreeCapacity());
            activeVoyage.setReeferCount(voyage.getReeferCount());

            reefersInTransit = Json.createValue(reefersInTransit.intValue() + voyage.getReeferCount());
            saveMetrics();
            // update voyage state
            Kar.Actors.State.Submap.set(this, Constants.ACTIVE_VOYAGES_KEY, voyage.getId(), VoyageJsonSerializer.serialize(activeVoyage) );
        } catch (Exception e) {
            String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
            logger.log(Level.SEVERE, "ScheduleManagerActor.voyageDeparted() "+stacktrace);
            e.printStackTrace();
        }
    }

    @Remote
    public void voyageArrived(JsonObject message) {
        try {
            Voyage voyage = VoyageJsonSerializer.deserialize(message);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ScheduleManagerActor.voyageArrived() - Voyage:" + voyage.getId() + " message:" + message);
            }
            schedule.updateDaysAtSea(voyage.getId(), Long.valueOf(voyage.getRoute().getVessel().getPosition()).intValue());
            voyage.changePosition(Long.valueOf(voyage.getRoute().getVessel().getPosition()).intValue());
            if ( (reefersInTransit.intValue() - voyage.getReeferCount()) >= 0) {
                reefersInTransit = Json.createValue(reefersInTransit.intValue() - voyage.getReeferCount());
            } else {
                reefersInTransit = Json.createValue(0);
            }
            saveMetrics();
            // update voyage state
            Kar.Actors.State.Submap.set(this, Constants.ACTIVE_VOYAGES_KEY, voyage.getId(), VoyageJsonSerializer.serialize(voyage) );
        } catch (Exception e) {
            String stacktrace = ExceptionUtils.getStackTrace(e).replaceAll("\n","");
            logger.log(Level.SEVERE, "ScheduleManagerActor.voyageArrived() "+stacktrace);
            e.printStackTrace();
        }
    }
    @Remote
    public JsonValue reefersInTransit() {
        List<Voyage> activeVoyages = activeVoyageManager.getActiveVoyages();
        int inTransit=0;
        for (Voyage voyage : activeVoyages) {
            inTransit += voyage.getReeferCount();
        }
        return Json.createValue(inTransit);
    }
    private void saveMetrics() {
        String metrics = String.format("%d", reefersInTransit.intValue());
        Kar.Actors.State.set(this, Constants.REEFERS_IN_TRANSIT_COUNT_KEY, reefersInTransit);
    }

    @Remote
    public void positionChanged(JsonObject message) {
        try {
            Voyage voyage = VoyageJsonSerializer.deserialize(message);
            if (voyage.getProgress() > 0) {
                schedule.updateDaysAtSea(voyage.getId(), Long.valueOf(voyage.getRoute().getVessel().getPosition()).intValue());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }

    }

    @Remote
    public JsonObject voyageState(JsonValue id) throws VoyageNotFoundException {
        return voyage(id);
    }

    @Remote
    public JsonObject voyage(JsonValue id) throws VoyageNotFoundException {
        return VoyageJsonSerializer.serialize(schedule.getVoyage(((JsonString) id).getString()));
    }

    @Remote
    public void orderBooked(JsonObject message) {
        try {
            String voyageId = JsonUtils.getVoyageId(message.toString());
            Voyage voyage = schedule.getVoyage(voyageId);
            voyage.incrementOrderCount();
            voyage.setFreeCapacity(message.getJsonNumber(Constants.VOYAGE_FREE_CAPACITY_KEY).intValue());
        } catch (VoyageNotFoundException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
        }

    }


    @Remote
    public JsonValue routes() {
        List<Route> routes = schedule.getRoutes();
        JsonArrayBuilder jab = Json.createArrayBuilder();
        routes.forEach(route -> jab.add(RouteJsonSerializer.serialize(route)));
        return jab.build();
    }

    private class ActiveVoyageManager {
        private ActorRef scheduleManagerActor = Kar.Actors.ref(ReeferAppConfig.ScheduleManagerActorType, ReeferAppConfig.ScheduleManagerId);
        private final ScheduleService schedule;
        private List<Voyage> activeVoyages;
        ActiveVoyageManager(ScheduleService schedule, List<Voyage> activeVoyages) {
            this.schedule = schedule;
            this.activeVoyages = activeVoyages;
        }
        public List<Voyage> getActiveVoyages() {
            List<Voyage> newActiveVoyages = schedule.getActiveVoyages();
            if ( !newActiveVoyages.isEmpty() ) {
                List<String> arrivedVoyages = getArrivedVoyages(newActiveVoyages);
                activeVoyages.clear();
                activeVoyages.addAll( newActiveVoyages);
                // delete voyages that arrived and update those still en route
            //    Kar.Actors.State.update(scheduleManagerActor, arrivedVoyages,
            //            Collections.emptyMap(), Collections.emptyMap(),  getActiveVoyageUpdateMap(newActiveVoyages));
                Kar.Actors.State.update(scheduleManagerActor, Collections.emptyList(),
                        getArrivedVoyagesRemoveMap(arrivedVoyages), Collections.emptyMap(),  getActiveVoyageUpdateMap(newActiveVoyages));
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("ActiveVoyageManager.getActiveVoyages() - arrived voyage count:" + arrivedVoyages.size());
                }
            }
            return activeVoyages;
        }
        private List<String> getArrivedVoyages(final List<Voyage>newActiveVoyages) {
            // get a list of arrived voyages which need to be removed from active list.
            return activeVoyages.stream()
                    .filter(voyage -> !newActiveVoyages.contains(voyage))
                    .map(Voyage::getId)
                    .collect(Collectors.toList());
        }
        private  Map<String, Map<String, JsonValue>> getActiveVoyageUpdateMap(List<Voyage> newActiveVoyages) {
            Map<String, JsonValue> updateMap = new HashMap<>();
            for( Voyage voyage : newActiveVoyages ) {
                updateMap.put( voyage.getId(), VoyageJsonSerializer.serialize(voyage));
            }
            Map<String, Map<String, JsonValue>> subMapUpdates = new HashMap<>();
            subMapUpdates.put(Constants.ACTIVE_VOYAGES_KEY, updateMap);
            return subMapUpdates;
        }
        private  Map<String, List<String>> getArrivedVoyagesRemoveMap(final List<String> arrivedVoyages) {
            Map<String,List<String>> subMapDeletes = new HashMap<>();
            subMapDeletes.put(Constants.ACTIVE_VOYAGES_KEY, arrivedVoyages);
            return subMapDeletes;
        }
    }
}
