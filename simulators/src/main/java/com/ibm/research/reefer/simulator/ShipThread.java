package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorRef;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import java.util.logging.Level;
import java.util.logging.Logger;

// Ship Simulator thread functions
// 1. tell REST to update world time
// 2. request from REST list of all active voyages
// 3. send ship position to all active voyage actors, spread out thru day
// 4. tell order&reefer simulators the new time
// 5. total sleep of UnitDelay seconds spread out thru day
// 6. quit if one-shot request or thread interrupted


public class ShipThread extends Thread {
  boolean running = true;
  boolean interrupted = false;
  boolean oneshot = false;
  int loopcnt = 0;
  int events_per_day = 5;
  int voyages_per_event;
  int extra_per_event;
  int voyages_updated;
  LinkedHashMap<String,JsonObject> activemap;
  String[] activekeys;
  int sleeptime;
  JsonValue currentDate;
  long last_snapshot;
  private static final Logger logger = Logger.getLogger(ShipThread.class.getName());

  public void run() {
    if (0 == SimulatorService.unitdelay.intValue()) {
      oneshot = true;
    }

    Thread.currentThread().setName("shipthread");
    SimulatorService.shipthreadcount.incrementAndGet();
    if (logger.isLoggable(Level.INFO)) {
      logger.info("shipthread: started threadid=" + Thread.currentThread().getId() + " ... LOUD HORN");
    }
    int nextevent = 0;
    activemap = new LinkedHashMap<String,JsonObject>();
    sleeptime=1000 * SimulatorService.unitdelay.intValue();
    last_snapshot = System.nanoTime();

    while (running) {
      long snapshot = System.nanoTime();
      long delta = snapshot - last_snapshot;
      last_snapshot = snapshot;
      if (logger.isLoggable(Level.FINE)) {
        logger.fine("shipthread: " + Thread.currentThread().getId() + ": running " + ++loopcnt + " nextevent===> "+nextevent
                + "  sleeptime= " + sleeptime + " last delta= " + delta/1000000);
      }

      if (!SimulatorService.reeferRestRunning.get()) {
        logger.warning("shipthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
      } else {
        if (0 == nextevent) {
          // start of new day, tell REST to advance time
          activemap.clear();
          Response response = Kar.restPost("reeferservice", "time/advance", JsonValue.NULL);
          currentDate = response.readEntity(JsonValue.class);
          SimulatorService.currentDate.set(currentDate);
          if (logger.isLoggable(Level.INFO)) {
            logger.info("shipthread: New time ======> " + currentDate.toString());
          }

          // tell other threads to wake up
          Kar.restPost("simservice", "simulator/newday", JsonValue.NULL);

          // fetch all active voyages from REST
          response = Kar.restGet("reeferservice", "voyage/active");
          JsonValue activeVoyages = response.readEntity(JsonValue.class);
          if (logger.isLoggable(Level.INFO)) {
            logger.info("shipthread: received " + activeVoyages.asJsonArray().size() + " active voyages");
          }

          // compute ship positions to send to all active voyages
          Instant ed = Instant.parse(currentDate.toString().replaceAll("^\"|\"$", ""));
          for (JsonValue v : activeVoyages.asJsonArray()) {
            String id = v.asJsonObject().getString("id");
            Instant sd = Instant
                    .parse(v.asJsonObject().getString("sailDateObject").replaceAll("^\"|\"$", ""));
            long daysout = ChronoUnit.DAYS.between(sd, ed);
            JsonObject message = Json.createObjectBuilder().add("daysAtSea", daysout)
                    .add("currentDate", currentDate).build();
            activemap.put(id,message);
          }

          // compute number of ships to update on each iteration
          activekeys = activemap.keySet().toArray(new String[activemap.size()]);
          voyages_per_event = activemap.size()/events_per_day;
          // spread out any extras as well
          extra_per_event = activemap.size() % events_per_day;
          if (0 == voyages_per_event) {
            voyages_per_event = 1;
          }
          voyages_updated = 0;
          if (SimulatorService.unitdelay.intValue() > 0) {
            sleeptime = (1000 * SimulatorService.unitdelay.intValue())/(events_per_day);
          } else {
            sleeptime = (1000 / events_per_day);
          }
        }

        int voyages_to_update = (0 < extra_per_event--) ? voyages_per_event + 1 : voyages_per_event;
        for (int e=0; e<voyages_to_update; e++) {
          if ( activemap.size() > voyages_updated) {
            String id = activekeys[voyages_updated++];
            JsonObject message = activemap.get(id);
            Kar.actorTell(actorRef("voyage", id), "changePosition", message);
            if (logger.isLoggable(Level.FINE)) {
              logger.fine("shipthread: updates voyageid: " + id + " with " + message.toString());
            }
          }
        }
      }

      nextevent++;
      if (events_per_day == nextevent) {
        nextevent = 0;

        // tell GUI to update active voyages
        snapshot = System.nanoTime();
        Kar.restPost("reeferservice", "voyage/updateGui", currentDate);
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("shipthread: updateGui took " + (System.nanoTime()-snapshot)/1000000 + " ms");
        }
      }

      try {
        Thread.sleep(sleeptime);
      } catch (InterruptedException e) {
        interrupted = true;
      }

      // check if auto mode turned off
      if ((nextevent==0 && (0 == SimulatorService.unitdelay.intValue() || oneshot)) || interrupted ) {
        SimulatorService.unitdelay.set(0);
        if (logger.isLoggable(Level.INFO)) {
          logger.info("shipthread: Stopping Thread " + Thread.currentThread().getId() + " LOUD HORN");
        }
        running = false;

        if (0 < SimulatorService.shipthreadcount.decrementAndGet()) {
          logger.warning("shipthread: we have an extra thread running!");
        }

        // check for threads leftover from a hot method replace
        Set<Thread> threadset = Thread.getAllStackTraces().keySet();
        for (Thread thread : threadset) {
          if (thread.getName().equals("shipthread")
                  && thread.getId() != Thread.currentThread().getId()) {
            logger.warning("shipthread: killing leftover threadid=" + thread.getId());
            thread.interrupt();
          }
        }
      }
    }
  }
}
