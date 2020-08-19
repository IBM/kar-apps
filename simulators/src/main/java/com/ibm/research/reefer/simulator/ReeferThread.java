package com.ibm.research.reefer.simulator;

import java.time.Instant;
import java.util.Random;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;

public class ReeferThread extends Thread {
  boolean running = true;
  boolean interrupted = false;
  boolean oneshot = false;
  int threadloops = 0;
  int reefersToBreak;
  int r2b[];
  int inventorySize;
  Instant today;
  JsonValue currentDate = Json.createValue("");

  //TODO make updatesPerDay configurable
  int updatesPerDay = 10;
  int anomaliesPerUpdate;
  int anomaliesDoneToday;

  public void run() {

    if (0 == SimulatorService.unitdelay.intValue()
            || 0 == SimulatorService.failuretarget.intValue()) {
      oneshot = true;
    }

    Thread.currentThread().setName("reeferthread");
    SimulatorService.reeferthreadcount.incrementAndGet();
    System.out.println(
            "reeferthread: started threadid=" + Thread.currentThread().getId() + " ... LOUD HORN");

    // If new day, get reefer inventory, anomaly target %,  and select reefers to break
    // Set number of anomalies per loop based on requested updates per day
    // Tell reefer provisioner the bad news

    while (running) {
      if (!oneshot) {
//        		System.out.println("reeferthread: "+Thread.currentThread().getId()+": running "+ ++threadloops);
      }

      if (!SimulatorService.reeferRestRunning.get()) {
//        		System.out.println("reeferthread: reefer-rest service ignored. POST to simulator/togglereeferrest to enable");
      } else {
        // Make sure currentDate is set
        if (null == SimulatorService.currentDate.get()) {
          Response response = Kar.restPost("reeferservice", "time/currentDate", JsonValue.NULL);
          currentDate = response.readEntity(JsonValue.class);
          SimulatorService.currentDate.set(currentDate);
        }

        // If new day ...
        if (oneshot || !currentDate.equals((JsonValue) SimulatorService.currentDate.get())) {

          currentDate = (JsonValue) SimulatorService.currentDate.get();
          // Get reefer inventory size from reefer-rest
          Response response = Kar.restGet("reeferservice", "reefers/inventory/size");
          JsonValue is = (JsonValue) response.readEntity(JsonValue.class);
          inventorySize = ((JsonNumber) is).intValue();

          // Get anomaly target for today
          if (oneshot) {
            reefersToBreak = 1;
            System.out.println("reeferthread: oneshot generating 1 anomaly");
          }
          else {
            reefersToBreak = (inventorySize * SimulatorService.failuretarget.get())/10000;
            System.out.println("reeferthread: generating "+reefersToBreak+" anomalies for new day");
          }
          r2b = new int[reefersToBreak];
          Random rand = new Random();
          for (int i=0; i<reefersToBreak; i++) {
            // ignore possibility that same reefer receives multiple anomalies
            r2b[i] = rand.nextInt(inventorySize) + 1;
          }

          anomaliesPerUpdate = reefersToBreak / updatesPerDay;
          if (0 == anomaliesPerUpdate && 0 < reefersToBreak) {
            anomaliesPerUpdate = 1;
          }
          anomaliesDoneToday = 0;
        }

        // if not done for the day, generate anomaliesPerUpdate more failures
        if (anomaliesDoneToday < reefersToBreak) {
          for (int i=0; i<anomaliesPerUpdate; i++) {
            String reefer = "R" + r2b[anomaliesDoneToday++];
            System.out.println("reeferthread: alerting provisioner about anomaly in "+reefer);
          }
        }
      }

      // sleep if not a oneshot reefer command
      if (!oneshot) {
        try {
          Thread.sleep(1000 * SimulatorService.unitdelay.intValue() / updatesPerDay);
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }

      // check if auto mode should be turned off
      if (0 == SimulatorService.failuretarget.intValue()
              || 0 == SimulatorService.unitdelay.intValue() || oneshot) {
        System.out.println(
                "reeferthread: Stopping Thread " + Thread.currentThread().getId() + " LOUD HORN");
        running = false;

        if (0 < SimulatorService.reeferthreadcount.decrementAndGet()) {
          System.err.println("reeferthread: we have an extra reefer thread running!");
        }

        // check for threads leftover from a hot method replace
        Set<Thread> threadset = Thread.getAllStackTraces().keySet();
        for (Thread thread : threadset) {
          if (thread.getName().equals("reeferthread")
                  && thread.getId() != Thread.currentThread().getId()) {
            System.out.println("reeferthread: killing leftover reefer threadid=" + thread.getId());
            thread.interrupt();
          }
        }
      }
    }
  }
}
