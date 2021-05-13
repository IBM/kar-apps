package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.Routes;
import com.ibm.research.kar.reefer.common.ScheduleService;
import com.ibm.research.kar.reefer.common.ShippingScheduler;
import com.ibm.research.kar.reefer.common.error.VoyageNotFoundException;
import com.ibm.research.kar.reefer.common.json.JsonUtils;
import com.ibm.research.kar.reefer.common.json.VoyageJsonSerializer;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Voyage;

import javax.json.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Actor
public class ScheduleManagerActor extends BaseActor {
    private Routes routes = new Routes();
    // private List<Route> shipRoutes;
    private ScheduleService schedule;
    private Instant baseDate;
    private TreeSet<Order> activeOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));
    private TreeSet<Order> bookedOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));
    private TreeSet<Order> spoiltOrders = new TreeSet<>(Comparator.comparing(o -> Instant.parse(o.getDate())));

    private static final Logger logger = Logger.getLogger(OrderManagerActor.class.getName());

    @Activate
    public void activate() {
        Map<String, JsonValue> state = Kar.Actors.State.getAll(this);
        Map<String, String> env = System.getenv();
        int fleetSize = 10;
        try {
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
            schedule = new ScheduleService(new ShippingScheduler(fleetSize));
            JsonValue baseDateValue = state.get(Constants.SCHEDULE_BASE_DATE_KEY);

            Instant currentDate;
            Instant lastVoyageDate;
            if (Objects.isNull(baseDateValue)) {
                baseDate = currentDate = TimeUtils.getInstance().getCurrentDate();
                lastVoyageDate = TimeUtils.getInstance().getDateYearFrom(TimeUtils.getInstance().getCurrentDate());
                Kar.Actors.State.set(this, Constants.SCHEDULE_BASE_DATE_KEY, Json.createValue(currentDate.toString()));
                Kar.Actors.State.set(this, Constants.CURRENT_DATE_KEY, Json.createValue(currentDate.toString()));
            } else {
                //currentDate = Instant.parse(((JsonString) baseDateValue).getString());
                baseDate = Instant.parse(((JsonString) baseDateValue).getString());
                JsonValue date = state.get(Constants.CURRENT_DATE_KEY);
                currentDate = TimeUtils.getInstance(Instant.parse(((JsonString)date).getString())).getCurrentDate();
                lastVoyageDate = Instant.parse(((JsonString) state.get(Constants.SCHEDULE_END_DATE_KEY)).getString());
                System.out.println("ScheduleManagerActor.activate() - Restored Current Date:" + currentDate+" baseDate:"+baseDate+" endDate:"+lastVoyageDate);
            }
            Instant lastScheduleDate = schedule.generateShipSchedule(currentDate, lastVoyageDate);
            //timeService.saveDate(((TreeSet<Voyage>) masterSchedule).last().getSailDateObject(), Constants.SCHEDULE_END_DATE_KEY);
            Kar.Actors.State.set(this, Constants.SCHEDULE_END_DATE_KEY, Json.createValue(lastScheduleDate.toString()));
            System.out.println("ScheduleManagerActor.activate() ++++ Saved End Date:" + lastScheduleDate);
            System.out.println("ScheduleManagerActor.activate() - actor type:" + this.getType() + " generated routes - size:" + schedule.getRoutes().size());
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        //Instant t = currentDate.plus(1, ChronoUnit.DAYS);
        return Json.createValue(currentDate.plus(1, ChronoUnit.DAYS).toString());
    }

    @Remote
    public JsonObject advanceDate() {
        System.out.println("ScheduleManagerActor.advanceDate() ------------------");
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
                // System.out.println("TimeController.advance() >>>>>>>>>>>>>>> extended schedule");
            } else {
                //  System.out.println("TimeController.advance() >>>>>>>>>>>>>>> extending schedule in "+daysBetween+" days");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
        }
        System.out.println("ScheduleManagerActor.advanceDate() ------------------returning current date:" + today);
        JsonObjectBuilder reply = Json.createObjectBuilder();
        return reply.add(Constants.STATUS_KEY, Constants.OK).add(Constants.CURRENT_DATE_KEY, today.toString()).build();
    }

    @Remote
    public void positionChanged(JsonObject message) {
        System.out.println("ScheduleManagerActor.positionChanged() ------------------");
        try {
            //String voyageId = JsonUtils.getVoyageId(message);
            // int daysAtSea = JsonUtils.getDaysAtSea(message);
            String voyageId = JsonUtils.getVoyageId(message.toString());
            int daysAtSea = JsonUtils.getDaysAtSea(message.toString());
            /*
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ScheduleManager.positionChanged() - voyageId:" + voyageId + " Voyage Status:"
                        + schedule.getVoyageStatus(voyageId) + " daysAtSea: " + daysAtSea);
            }

             */
            if (daysAtSea > 0) {
                schedule.updateDaysAtSea(voyageId, daysAtSea);
            }
            // updateGuiSchedule(TimeUtils.getInstance().getCurrentDate().toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }

    }

    @Remote
    public void updateVoyage(JsonObject message) {
        System.out.println("ScheduleManagerActor.updateVoyage() ------------------");
        try {
            Voyage voyage = schedule.getVoyage(message.getString(Constants.VOYAGE_ID_KEY));
            voyage.setFreeCapacity(message.getInt(Constants.VOYAGE_FREE_CAPACITY_KEY));
            voyage.setOrderCount(message.getInt(Constants.VOYAGE_ORDERS_KEY));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Remote
    public JsonValue activeVoyages() {
        System.out.println("ScheduleManagerActor.activeVoyages() ------------------");

        return voyageListToJsonArray(schedule.getActiveSchedule());
    }

    @Remote
    public JsonObject activeSchedule() {
        System.out.println("ScheduleManagerActor.activeSchedule() ------------------");
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add(Constants.CURRENT_DATE_KEY, TimeUtils.getInstance().getCurrentDate().toString()).
                add(Constants.ACTIVE_VOYAGES_KEY, voyageListToJsonArray(schedule.getActiveSchedule()));
        return job.build();
    }

    private JsonArray voyageListToJsonArray(List<Voyage> voyages) {
        JsonArrayBuilder jab = Json.createArrayBuilder();
        voyages.forEach(voyage -> {
            jab.add(VoyageJsonSerializer.serialize(voyage));
        });
        JsonArray ja = jab.build();
        System.out.println("ScheduleManagerActor.activeVoyages() ------------------ active voyages:" + ja);
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


        // originPort = message.getString("origin");
        //   destinationPort = message.getString("destination");
        String departureDate = message.getString("departureDate");
        date = Instant.parse(departureDate);
        if (logger.isLoggable(Level.INFO)) {
            logger.info("VoyageController.getMatchingVoyages() - origin:" + originPort + " destination:"
                    + destinationPort + " date:" + departureDate);
        }

        //return schedule.getMatchingSchedule(message.getString("origin"), message.getString("destination"), date);
        return voyageListToJsonArray(schedule.getMatchingSchedule(message.getString("origin"), message.getString("destination"), date));
    }

    @Remote
    public void voyageDeparted(JsonObject message) {
        try {
            String voyageId = JsonUtils.getVoyageId(message.toString());
            int daysAtSea = JsonUtils.getDaysAtSea(message.toString());
            //   List<String> voyageOrderList = JsonUtils.getVoyageOrders(message.toString());
            if (logger.isLoggable(Level.INFO)) {
                logger.info("ScheduleManagerActor.departed() - id:" + voyageId + " message:" + message);
            }
            schedule.updateDaysAtSea(voyageId, daysAtSea);
            // voyageService.voyageDeparted(voyageId);
            // orderService.updateOrderStatus(voyageId, Order.OrderStatus.INTRANSIT, voyageOrderList);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
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
    public void voyageArrived(JsonObject message) {
        try {
            String voyageId = JsonUtils.getVoyageId(message.toString());
            int daysAtSea = JsonUtils.getDaysAtSea(message.toString());
            //List<String> voyageOrderList = JsonUtils.getVoyageOrders(message.toString());
            if (logger.isLoggable(Level.INFO)) {
                logger.info("VoyageController.delivered() - id:" + voyageId + " message:" + message);
            }
            schedule.updateDaysAtSea(voyageId, daysAtSea);
            //voyageService.voyageEnded(voyageId);
            // orderService.updateOrderStatus(voyageId, Order.OrderStatus.DELIVERED, voyageOrderList);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            e.printStackTrace();
        }
    }
/*
    @Remote
    public JsonValue routes() {
        schedule.getRoutes();
    }

 */
}
