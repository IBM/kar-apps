package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorRef;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;

// Ship Simulator thread functions
// 1. tell REST to update world time
// 2. request from REST list of all active voyages
// 3. send ship position to all active voyage actors
// 4. tell order simulator the new time
// 5. sleep for UnitDelay seconds
// 6. quit if one-shot request or thread interrupted

public class ShipThread extends Thread {
  boolean running = true;
  boolean interrupted = false;
  boolean oneshot = false;
  int loopcnt = 0;

  public void run() {
    if (0 == SimulatorService.unitdelay.intValue()) {
      oneshot = true;
    }

    Thread.currentThread().setName("shipthread");
    SimulatorService.shipthreadcount.incrementAndGet();
    System.out.println(
            "shipthread: started threadid=" + Thread.currentThread().getId() + " ... LOUD HORN");
    while (running) {
      if (!oneshot) {
        System.out.println(
                "shipthread: " + Thread.currentThread().getId() + ": running " + ++loopcnt);
      }

      if (!SimulatorService.reeferRestRunning.get()) {
        System.out.println(
                "shipthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
      } else {
        // tell REST to advance time
        Response response = Kar.restPost("reeferservice", "time/advance", JsonValue.NULL);
        JsonValue currentDate = response.readEntity(JsonValue.class);
        SimulatorService.currentDate.set(currentDate);
        System.out.println("shipthread: New time = " + currentDate.toString());

        // fetch all active voyages from REST
        response = Kar.restGet("reeferservice", "voyage/active");
        JsonValue activeVoyages = response.readEntity(JsonValue.class);
        System.out.println(
                "shipthread: received " + activeVoyages.asJsonArray().size() + " active voyages");

        // send ship positions to all active voyages
        int nv = 0;
        Instant ed = Instant.parse(currentDate.toString().replaceAll("^\"|\"$", ""));
        for (JsonValue v : activeVoyages.asJsonArray()) {
          String id = v.asJsonObject().getString("id");
          Instant sd = Instant
                  .parse(v.asJsonObject().getString("sailDateObject").replaceAll("^\"|\"$", ""));
          long daysout = ChronoUnit.DAYS.between(sd, ed);
          JsonObject message = Json.createObjectBuilder().add("daysAtSea", daysout)
                  .add("currentDate", currentDate).build();
          System.out.println("shipthread: updates voyageid: " + id + " with " + message.toString());
          Kar.actorCall(actorRef("voyage", id), "changePosition", message);
          nv++;
        }
        System.out.println("shipthread: updated " + nv + " active voyages");

        // tell GUI to update active voyages
        Kar.restPost("reeferservice", "voyage/updateGui", currentDate);

        // tell other threads to wake up
        Kar.restPost("simservice", "simulator/newday", JsonValue.NULL);
      }

      try {
        Thread.sleep(1000 * SimulatorService.unitdelay.intValue());
      } catch (InterruptedException e) {
//        		System.out.println("shipthread: Interrupted Thread "+Thread.currentThread().getId());
        interrupted = true;
      }

      // check if auto mode turned off
      synchronized (SimulatorService.unitdelay) {
        if (0 == SimulatorService.unitdelay.intValue() || interrupted || oneshot) {
          SimulatorService.unitdelay.set(0);
          System.out.println(
                  "shipthread: Stopping Thread " + Thread.currentThread().getId() + " LOUD HORN");
          running = false;

          if (0 < SimulatorService.shipthreadcount.decrementAndGet()) {
            System.err.println("shipthread: we have an extra thread running!");
          }

          // check for threads leftover from a hot method replace
          Set<Thread> threadset = Thread.getAllStackTraces().keySet();
          for (Thread thread : threadset) {
            if (thread.getName().equals("shipthread")
                    && thread.getId() != Thread.currentThread().getId()) {
              System.out.println("shipthread: killing leftover threadid=" + thread.getId());
              thread.interrupt();
            }
          }
        }
      }
    }
  }
}
