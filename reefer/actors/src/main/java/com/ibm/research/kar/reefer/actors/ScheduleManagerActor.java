package com.ibm.research.kar.reefer.actors;

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

import javax.json.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Actor
public class ScheduleManagerActor extends BaseActor {
    private Routes routes = new Routes();
    private ScheduleService schedule;
    private Instant baseDate;
    private long reeferInventorySize;
    private JsonNumber reefersInTransit = Json.createValue(0);
    private static final Logger logger = Logger.getLogger(OrderManagerActor.class.getName());

    @Activate
    public void activate() {
        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        try {

            int fleetSize = getFleetSize(state);
            schedule = new ScheduleService(new ShippingScheduler(fleetSize));
            reeferInventorySize = FleetCapacity.totalSize(schedule.getRoutes());
            if (state.containsKey(Constants.REEFERS_IN_TRANSIT_COUNT_KEY)) {
                JsonValue metrics = state.get(Constants.REEFERS_IN_TRANSIT_COUNT_KEY);
                //System.out.println("ScheduleManagerActor.activate() - metrics:"+metrics);
                if (metrics != null && metrics != JsonValue.NULL) {
                    reefersInTransit = (JsonNumber)metrics;//Json.createValue(Integer.valueOf(((JsonString) metrics).getString()).intValue());
                }
            }

            JsonValue baseDateValue = state.get(Constants.SCHEDULE_BASE_DATE_KEY);
            Instant lastScheduleDate;
            if (Objects.isNull(baseDateValue)) {
                lastScheduleDate = coldStart();
            } else {
                lastScheduleDate = warmStart(state, baseDateValue);
                restoreActiveVoyages();
            }
            Kar.Actors.State.set(this, Constants.SCHEDULE_END_DATE_KEY, Json.createValue(lastScheduleDate.toString()));
            System.out.println("ScheduleManagerActor.activate() ++++ Saved End Date:" + lastScheduleDate);
            Kar.Actors.State.set(this, Constants.REEFERS_IN_TRANSIT_COUNT_KEY, reefersInTransit);
            System.out.println("ScheduleManagerActor.activate() - actor type:" + this.getType() + " generated routes - size:" + schedule.getRoutes().size());
            Kar.Actors.Reminders.schedule(this, "publishSpoiltReeferMetrics", "VoyageManagerReminder", Instant.now().plus(1, ChronoUnit.SECONDS), Duration.ofSeconds(5));

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
        List<Voyage> activeVoyages = schedule.getActiveSchedule();
        for (Voyage voyage : activeVoyages) {
            try {
                ActorRef voyageActorRef = Kar.Actors.ref(ReeferAppConfig.VoyageActorType, voyage.getId());
                JsonValue voyageSpoiltMetrics = Kar.Actors.State.get(voyageActorRef, Constants.TOTAL_SPOILT_KEY);
                if (voyageSpoiltMetrics != null && voyageSpoiltMetrics != JsonValue.NULL) {
                    totalSpoiltReeferCount += ((JsonNumber) voyageSpoiltMetrics).intValue();
                }
            } catch (Exception e) {
                // voyage may have already arrived and was removed
                e.printStackTrace();
            }
        }
        Kar.Actors.State.set(this, Constants.TOTAL_SPOILT_KEY, Json.createValue(totalSpoiltReeferCount));
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
        System.out.println("ScheduleManagerActor.warmStart() - current date from REDIS:" + date + " cd:" + cd);
        currentDate = TimeUtils.getInstance(cd).getCurrentDate();
        lastVoyageDate = Instant.parse(((JsonString) state.get(Constants.SCHEDULE_END_DATE_KEY)).getString());
        System.out.println("ScheduleManagerActor.warmStart() - Restored Current Date:" + currentDate + " baseDate:" + baseDate + " endDate:" + lastVoyageDate);
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
                System.out.println("ScheduleManagerActor.activate() - Warm start - using previously saved fleet size of " + ((JsonNumber) jv).intValue());
                fleetSize = ((JsonNumber) jv).intValue();
            }
        } else {
            Kar.Actors.State.set(this, Constants.REEFER_FLEET_SIZE_KEY, Json.createValue(fleetSize));
            System.out.println("ScheduleManagerActor.activate() ++++++++++++ saved fleet size:" + fleetSize);
        }
        return fleetSize;
    }

    private void restoreActiveVoyages() {
        List<Voyage> activeVoyages = schedule.getActiveSchedule();
        int inTransit = 0;
        for (Voyage voyage : activeVoyages) {
            Optional<JsonObject> state = recoverVoyage(voyage.getId());
            if (state.isPresent()) {
                Voyage recoveredVoyageState = VoyageJsonSerializer.deserialize(state.get());
                voyage.setOrderCount(recoveredVoyageState.getOrderCount());
                voyage.changePosition(Long.valueOf(recoveredVoyageState.getRoute().getVessel().getPosition()).intValue());
                voyage.setReeferCount(recoveredVoyageState.getReeferCount());
                voyage.setFreeCapacity(recoveredVoyageState.getRoute().getVessel().getFreeCapacity());
                inTransit += recoveredVoyageState.getReeferCount();
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
            logger.log(Level.WARNING, "", e);
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
            e.printStackTrace();
        }
    }

    @Remote
    public JsonValue activeVoyages() {
        return voyageListToJsonArray(schedule.getActiveSchedule());
    }

    @Remote
    public JsonObject activeSchedule() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.CURRENT_DATE_KEY, TimeUtils.getInstance().getCurrentDate().toString()).
                add(Constants.ACTIVE_VOYAGES_KEY, voyageListToJsonArray(schedule.getActiveSchedule()));
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ScheduleManagerActor.getVoyagesInRange() - startDate:" + startDate.toString() + " endDate:"
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
        if (logger.isLoggable(Level.INFO)) {
            logger.info("ScheduleManagerActor.getMatchingVoyages() - origin:" + originPort + " destination:"
                    + destinationPort + " date:" + departureDate);
        }
        return voyageListToJsonArray(schedule.getMatchingSchedule(message.getString("origin"), message.getString("destination"), date));
    }

    @Remote
    public void voyageDeparted(JsonObject message) {
        try {
            Voyage voyage = VoyageJsonSerializer.deserialize(message);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ScheduleManagerActor.departed() - id:" + voyage.getId() + " message:" + message);
            }
            Voyage activeVoyage = schedule.updateDaysAtSea(voyage.getId(), Long.valueOf(voyage.getRoute().getVessel().getPosition()).intValue());
            activeVoyage.setOrderCount(voyage.getOrderCount());
            activeVoyage.setFreeCapacity(voyage.getRoute().getVessel().getFreeCapacity());
            activeVoyage.setReeferCount(voyage.getReeferCount());

            reefersInTransit = Json.createValue(reefersInTransit.intValue() + voyage.getReeferCount());
         //   System.out.println("ScheduleManagerActor.voyageDeparted() >>>>>>>>>>>>>>>> reefersInTransit:::: " + reefersInTransit);
            saveMetrics();
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
        }
    }
    @Remote
    public JsonValue reefersInTransit() {
        List<Voyage> activeVoyages = schedule.getActiveSchedule();
        int inTransit=0;
        for (Voyage voyage : activeVoyages) {
           inTransit += voyage.getReeferCount();
        }
        return Json.createValue(inTransit);
    }
    @Remote
    public void voyageArrived(JsonObject message) {
        try {
            Voyage voyage = VoyageJsonSerializer.deserialize(message);
            if (logger.isLoggable(Level.INFO)) {
                logger.info("VoyageController.delivered() - id:" + voyage.getId() + " message:" + message);
            }
            schedule.updateDaysAtSea(voyage.getId(), Long.valueOf(voyage.getRoute().getVessel().getPosition()).intValue());
            voyage.changePosition(Long.valueOf(voyage.getRoute().getVessel().getPosition()).intValue());
            if ( (reefersInTransit.intValue() - voyage.getReeferCount()) >= 0) {
                reefersInTransit = Json.createValue(reefersInTransit.intValue() - voyage.getReeferCount());
            } else {
                reefersInTransit = Json.createValue(0);
            }

         //   System.out.println("ScheduleManagerActor.voyageArrived() >>>>>>>>>>>>>>>> reefersInTransit:::: " + reefersInTransit);
            saveMetrics();
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
        }
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
}
