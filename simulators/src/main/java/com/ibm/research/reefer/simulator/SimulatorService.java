package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorRef;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;

/**
 * At simulator startup time advance set to manual mode, UnitDelay = 0
 * Transitioning time advance to auto will also start Order and/or Reefer threads if enabled
 * Transitioning time advance to manual will stop running Order or Reefer threads 
 * At thread start any required but unset parameters are to their default values.
 */

public class SimulatorService {

  private Map<String, JsonValue> persistentData;
  private ActorRef aref = actorRef("simhelper", "simservice");
  public final static AtomicInteger unitdelay = new AtomicInteger(0);
  public final static AtomicInteger shipthreadcount = new AtomicInteger(0);
  public final static AtomicBoolean reeferRestRunning = new AtomicBoolean(true);
  public final static AtomicInteger ordertarget = new AtomicInteger(0);
  public final static AtomicInteger orderthreadcount = new AtomicInteger(0);
  public final static AtomicInteger orderwindow = new AtomicInteger(0);
  public final static AtomicInteger orderupdates = new AtomicInteger(0);
  public final static AtomicReference<JsonValue> currentDate = new AtomicReference<JsonValue>();
  public final static AtomicInteger failuretarget = new AtomicInteger(0);
  public final static AtomicInteger reeferthreadcount = new AtomicInteger(0);
  public final static AtomicInteger reeferupdates = new AtomicInteger(0);
  public final static Map<String, FutureVoyage> voyageFreeCap = new HashMap<String, FutureVoyage>();
  private Thread shipthread;
  private Thread orderthread;
  private Thread reeferthread;

  // constructor
  public SimulatorService() {
//		System.out.println("SimulatorService constructor!");
  }

  public JsonValue toggleReeferRest() {
    reeferRestRunning.set(!reeferRestRunning.get());
    return Json.createValue(reeferRestRunning.toString());
  }

  // local utility to retrieve cached value
  // create and fill cache if it is null
  private JsonValue get(JsonValue key) {
    if (null == persistentData) {
      persistentData = new HashMap<String, JsonValue>();
      persistentData.putAll(Kar.actorGetAllState(aref));
    }
    return persistentData.get(((JsonString) key).getString());
  }

  // local utility to update local cache and persistent state
  private JsonValue set(JsonValue key, JsonValue value) {
    if (null == persistentData) {
      persistentData = new HashMap<String, JsonValue>();
      persistentData.putAll(Kar.actorGetAllState(aref));
    }
    persistentData.put(((JsonString) key).getString(), value);
    return Json.createValue(Kar.actorSetState(aref, ((JsonString)key).getString(), value));
  }

  // local utility to get or init persistent values
  private JsonValue getOrInit(JsonValue key) {
    JsonNumber av = (JsonNumber) this.get(key);
    if (av == null) {
      switch (((JsonString)key).getString()) {
        case "ordertarget": av = (JsonNumber) Json.createValue(75); break;
        case "orderupdates": av = (JsonNumber) Json.createValue(3); break;
        case "orderwindow": av = (JsonNumber) Json.createValue(7); break;
        case "failuretarget": av = (JsonNumber) Json.createValue(4); break;
        case "reeferupdates": av = (JsonNumber) Json.createValue(10); break;
        default: av = (JsonNumber) Json.createValue(0);
      }
      this.set(key, av);
    }
    return av;
  }

  // -------------------------------- Ship Thread Controller --------------------------------

  private void startShipThread() {
    (shipthread = new ShipThread()).start();
  }

  public JsonNumber getUnitDelay() {
    return Json.createValue(unitdelay.get());
  }

  public JsonValue setUnitDelay(JsonValue value) {
    JsonNumber newval;
    if (JsonValue.ValueType.OBJECT == value.getValueType()) {
      newval = ((JsonObject) value).getJsonNumber("value");
    } else {
      newval = Json.createValue(((JsonNumber) value).intValue());
    }
    newval = newval.intValue() > 0 ? newval : (JsonNumber) Json.createValue(0);
    synchronized (unitdelay) {
      // if unitdelay > 0 then Ship thread is running.
      // if running and newval == 0 then STOP all simulator threads
      if (0 < unitdelay.intValue() || 0 == newval.intValue()) {
        if (0 < unitdelay.intValue() && 0 == newval.intValue()) {
          // set unitdelay=0 now so that order and reefer threads quit on interrupt
          unitdelay.set(0);
          if (null != shipthread) {
            shipthread.interrupt();
            shipthread = null;
          }
          if (null != orderthread) {
            orderthread.interrupt();
            orderthread = null;
          }
          if (null != reeferthread) {
            reeferthread.interrupt();
            reeferthread = null;
          }
        }
        unitdelay.set(newval.intValue());
        return Json.createValue("accepted");
      }

      // this is a request to start auto mode
      // is a thread already running?
      if (0 < shipthreadcount.get()) {
        return Json.createValue("rejected");
      }

      // save new delay
      unitdelay.set(newval.intValue());


      // get persistent value of failure target
      JsonNumber ft = (JsonNumber) getOrInit(Json.createValue("failuretarget"));
      failuretarget.set(ft.intValue());

      startShipThread();

      // if auto order enabled, start that thread too
      if (0 < ordertarget.intValue()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        startOrderThread();
      }

      // if auto reefer enabled, start that thread too
      if (0 < failuretarget.intValue()) {
        startReeferThread();
      }

      return Json.createValue("accepted");
    }
  }

  // Manual oneshot. Runs only when unitdelay == 0
  public JsonValue advanceTime() {
    synchronized (unitdelay) {
      if (0 == unitdelay.intValue() && 0 == shipthreadcount.get() &&
              0 == orderthreadcount.get() && 0 == reeferthreadcount.get()) {
        // get persistent value of ordertarget
        JsonNumber ot = (JsonNumber) this.getOrInit(Json.createValue("ordertarget"));
        ordertarget.set(ot.intValue());
        startShipThread();
        if (0 < ordertarget.intValue()) {
          // call order oneshot as well
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          startOrderThread();
        }
        return Json.createValue("accepted");
      }
      System.out.println("simulator: advanceTime rejected: unitdelay=" + unitdelay.get()
              + " shipthreadcount=" + shipthreadcount.get()
              + " orderthreadcount=" + orderthreadcount.get()
              + " reeferthreadcount=" + reeferthreadcount.get());
      return Json.createValue("rejected");
    }
  }

  // -------------------------------- Order Thread Controller --------------------------------

  private void startOrderThread() {
    // make sure persistent values are set
    JsonNumber ot = (JsonNumber) getOrInit(Json.createValue("ordertarget"));
    ordertarget.set(ot.intValue());
    JsonNumber ou = (JsonNumber) getOrInit(Json.createValue("orderupdates"));
    orderupdates.set(ou.intValue());
    JsonNumber ow = (JsonNumber) getOrInit(Json.createValue("orderwindow"));
    orderwindow.set(ow.intValue());

    (orderthread = new OrderThread()).start();
  }

  public JsonValue getOrderControls() {
    JsonObject oc = Json.createObjectBuilder()
            .add("ordertarget", (JsonNumber) getOrInit(Json.createValue("ordertarget")))
            .add("orderupdates", (JsonNumber) getOrInit(Json.createValue("orderupdates")))
            .add("orderwindow", (JsonNumber) getOrInit(Json.createValue("orderwindow")))
            .build();
    return oc;
  }

  public JsonValue setOrderControls(JsonValue controls) {
    if (((JsonObject) controls).containsKey("orderwindow")) {
      try {
        JsonNumber ow = ((JsonObject) controls).getJsonNumber("orderwindow");
        setOrderWindow(ow);
      }
      catch (Exception e) {
        JsonObject message = Json.createObjectBuilder()
                .add("invalid orderwindow", ((JsonObject) controls).getString("orderwindow")).build();
        return message;
      }
    }

    if (((JsonObject) controls).containsKey("orderupdates")) {
      try {
        JsonNumber ou = ((JsonObject) controls).getJsonNumber("orderupdates");
        setOrderUpdates(ou);
      }
      catch (Exception e) {
        JsonObject message = Json.createObjectBuilder()
                .add("invalid orderupdates", ((JsonObject) controls).getString("orderupdates")).build();
        return message;
      }
    }

    if (((JsonObject) controls).containsKey("ordertarget")) {
      try {
        JsonNumber ot = ((JsonObject) controls).getJsonNumber("ordertarget");
        setOrderTarget(ot);
      }
      catch (Exception e) {
        JsonObject message = Json.createObjectBuilder()
                .add("invalid ordertarget", ((JsonObject) controls).getString("ordertarget")).build();
        return message;
      }
    }

    JsonObject oc = Json.createObjectBuilder()
            .add("ordertarget", (JsonNumber) getOrInit(Json.createValue("ordertarget")))
            .add("orderupdates", (JsonNumber) getOrInit(Json.createValue("orderupdates")))
            .add("orderwindow", (JsonNumber) getOrInit(Json.createValue("orderwindow")))
            .build();
    return oc;
  }

  public JsonNumber getOrderUpdates() {
    JsonNumber ou = (JsonNumber) this.getOrInit(Json.createValue("orderupdates"));
    orderupdates.set(ou.intValue());
    return ou;
  }

  public JsonNumber setOrderUpdates(JsonValue value) {
    JsonNumber newval;
    if (JsonValue.ValueType.OBJECT == value.getValueType()) {
      newval = ((JsonObject) value).getJsonNumber("value");
    } else {
      newval = Json.createValue(((JsonNumber) value).intValue());
    }
    newval = newval.intValue() > 1 ? newval : (JsonNumber) Json.createValue(1);
    newval = newval.intValue() < 30 ? newval : (JsonNumber) Json.createValue(30);
    orderupdates.set(newval.intValue());
    this.set(Json.createValue("orderupdates"), newval);
    System.out.println("simulator: orderupdates set=" + newval.intValue());
    return newval;
  }

  public JsonNumber getOrderWindow() {
    JsonNumber ow = (JsonNumber) this.getOrInit(Json.createValue("orderwindow"));
    orderwindow.set(ow.intValue());
    return ow;
  }

  public JsonNumber setOrderWindow(JsonValue value) {
    JsonNumber newval;
    if (JsonValue.ValueType.OBJECT == value.getValueType()) {
      newval = ((JsonObject) value).getJsonNumber("value");
    } else {
      newval = Json.createValue(((JsonNumber) value).intValue());
    }
    newval = newval.intValue() > 1 ? newval : (JsonNumber) Json.createValue(1);
    newval = newval.intValue() < 28 ? newval : (JsonNumber) Json.createValue(28);
    orderwindow.set(newval.intValue());
    this.set(Json.createValue("orderwindow"), newval);
    System.out.println("simulator: orderwindow set=" + newval.intValue());
    return newval;
  }

  public JsonNumber getOrderTarget() {
    JsonNumber ot = (JsonNumber) this.getOrInit(Json.createValue("ordertarget"));
    ordertarget.set(ot.intValue());
    return ot;
  }

  public JsonValue setOrderTarget(JsonValue value) {
    JsonNumber newval;
    if (JsonValue.ValueType.OBJECT == value.getValueType()) {
      newval = ((JsonObject) value).getJsonNumber("value");
    } else {
      newval = Json.createValue(((JsonNumber) value).intValue());
    }
    newval = newval.intValue() > 0 ? newval : (JsonNumber) Json.createValue(0);
    newval = newval.intValue() < 85 ? newval : (JsonNumber) Json.createValue(85);
    synchronized (ordertarget) {
      // if ordertarget > 0 then Order thread is enabled
      // if running and newval == 0 then interrupt thread
      if (0 < ordertarget.intValue() || 0 == newval.intValue()) {
        if (0 < ordertarget.intValue() && 0 == newval.intValue()) {
          if (null != orderthread) {
            orderthread.interrupt();
            orderthread = null;
          }
        }
        ordertarget.set(newval.intValue());
        this.set(Json.createValue("ordertarget"), newval);
        System.out.println("simulator: ordertarget set=" + newval.intValue());
        return Json.createValue("accepted");
      }

      // this is a request to start auto order mode
      // save new Target
      ordertarget.set(newval.intValue());
      this.set(Json.createValue("ordertarget"), newval);

      // start the Order thread if no thread already running and unitdelay>0
      if (0 == orderthreadcount.get() && 0 < unitdelay.intValue()) {
        startOrderThread();
      } else {
        System.out.println("simulator: ordertarget set=" + ordertarget.get()
                + " but thread not started. orderthreadcount=" + orderthreadcount.get()
                + " unitdelay=" + unitdelay.intValue());
      }

      return Json.createValue("accepted");
    }
  }

  // Manual oneshot. Runs only when orderthreadcount == 0
  public JsonValue createOrder() {
    if (0 == orderthreadcount.get()) {
      startOrderThread();
      return Json.createValue("accepted");
    }
    System.out
            .println("simulator: createOrder rejected: orderthreadcount=" + orderthreadcount.get());
    return Json.createValue("rejected");
  }

  // wake up order and reefer threads if sleeping
  public void newDay() {
    if (null != orderthread) {
      orderthread.interrupt();
    }
    if (null != reeferthread) {
      reeferthread.interrupt();
    }
  }

  // Update voyage capacity
  public void updateVoyageCapacity(JsonValue capacity) {
    String vid = capacity.asJsonObject().getString("voyageId");
    int freecap = capacity.asJsonObject().getInt("freeCapacity");
    synchronized (SimulatorService.voyageFreeCap) {
      if (voyageFreeCap.containsKey(vid)) {
        voyageFreeCap.get(vid).setFreeCapacity(freecap);
      } else {
        // The other values will be set later in the order thread,
        // if this voyage is within the simulator's time window
        voyageFreeCap.put(vid, new FutureVoyage(0, 0, freecap, 0, 0));
      }
    }
    System.out.println("simulator: updated freeCapacity to " + freecap + " for voyage " + vid);
  }


  // -------------------------------- Reefer Thread Controller --------------------------------

  private void startReeferThread() {
    // make sure persistent values are set
    JsonNumber ft = (JsonNumber) getOrInit(Json.createValue("failuretarget"));
    failuretarget.set(ft.intValue());
    JsonNumber ru = (JsonNumber) getOrInit(Json.createValue("reeferupdates"));
    reeferupdates.set(ru.intValue());

    (reeferthread = new ReeferThread()).start();
  }

  public JsonValue getReeferControls() {
    JsonObject oc = Json.createObjectBuilder()
            .add("failuretarget", (JsonNumber) getOrInit(Json.createValue("failuretarget")))
            .add("reeferupdates", (JsonNumber) getOrInit(Json.createValue("reeferupdates")))
            .build();
    return oc;
  }

  public JsonValue setReeferControls(JsonValue controls) {
    if (((JsonObject) controls).containsKey("reeferupdates")) {
      try {
        JsonNumber ru = ((JsonObject) controls).getJsonNumber("reeferupdates");
        setReeferUpdates(ru);
      }
      catch (Exception e) {
        JsonObject message = Json.createObjectBuilder()
                .add("invalid reeferupdates", ((JsonObject) controls).getString("reeferupdates")).build();
        return message;
      }
    }

    if (((JsonObject) controls).containsKey("failuretarget")) {
      try {
        JsonNumber ft = ((JsonObject) controls).getJsonNumber("failuretarget");
        setFailureTarget(ft);
      }
      catch (Exception e) {
        JsonObject message = Json.createObjectBuilder()
                .add("invalid failuretarget", ((JsonObject) controls).getString("failuretarget")).build();
        return message;
      }
    }

    JsonObject oc = Json.createObjectBuilder()
            .add("failuretarget", (JsonNumber) getOrInit(Json.createValue("failuretarget")))
            .add("reeferupdates", (JsonNumber) getOrInit(Json.createValue("reeferupdates")))
            .build();
    return oc;
  }

  public JsonNumber getFailureTarget() {
    JsonNumber rt = (JsonNumber) this.getOrInit(Json.createValue("failuretarget"));
    failuretarget.set(rt.intValue());
    return rt;
  }

  // failure target in units of 0.01%
  public JsonValue setFailureTarget(JsonValue value) {
    JsonNumber newval;
    if (JsonValue.ValueType.OBJECT == value.getValueType()) {
      newval = ((JsonObject) value).getJsonNumber("value");
    } else {
      newval = Json.createValue(((JsonNumber) value).intValue());
    }
    newval = newval.intValue() > 0 ? newval : (JsonNumber) Json.createValue(0);
    newval = newval.intValue() < 500 ? newval : (JsonNumber) Json.createValue(5);
    synchronized (failuretarget) {
      // if failuretarget > 0 then reefer thread is enabled
      // if running and newval == 0 then interrupt thread
      if (0 < failuretarget.intValue() || 0 == newval.intValue()) {
        if (0 < failuretarget.intValue() && 0 == newval.intValue()) {
          if (null != reeferthread) {
            reeferthread.interrupt();
            reeferthread = null;
          }
        }
        failuretarget.set(newval.intValue());
        this.set(Json.createValue("failuretarget"), newval);
        System.out.println("simulator: failuretarget set=" + newval.intValue());
        return Json.createValue("accepted");
      }

      // this is a request to start auto Reefer mode
      // save new Target
      failuretarget.set(newval.intValue());
      this.set(Json.createValue("failuretarget"), newval);

      // start the reefer thread if no thread already running and unitdelay>0
      if (0 == reeferthreadcount.get() && 0 < unitdelay.intValue()) {
        startReeferThread();
      } else {
        System.out.println("simulator: failuretarget set=" + failuretarget.get()
                + " but thread not started. reeferthreadcount=" + reeferthreadcount.get()
                + " unitdelay=" + unitdelay.intValue());
      }

      return Json.createValue("accepted");
    }
  }

  public JsonNumber getReeferUpdates() {
    JsonNumber ru = (JsonNumber) this.getOrInit(Json.createValue("reeferupdates"));
    reeferupdates.set(ru.intValue());
    return ru;
  }

  public JsonNumber setReeferUpdates(JsonValue value) {
    JsonNumber newval;
    if (JsonValue.ValueType.OBJECT == value.getValueType()) {
      newval = ((JsonObject) value).getJsonNumber("value");
    } else {
      newval = Json.createValue(((JsonNumber) value).intValue());
    }
    newval = newval.intValue() > 1 ? newval : (JsonNumber) Json.createValue(1);
    newval = newval.intValue() < 30 ? newval : (JsonNumber) Json.createValue(30);
    reeferupdates.set(newval.intValue());
    this.set(Json.createValue("reeferupdates"), newval);
    System.out.println("simulator: reeferupdates set=" + newval.intValue());
    return newval;
  }

  // Manual oneshot. Runs only when reeferthreadcount == 0
  public JsonValue createAnomaly() {
    if (0 == reeferthreadcount.get()) {
      startReeferThread();
      return Json.createValue("accepted");
    }
    System.out
            .println("simulator: createAnomaly rejected: reeferthreadcount=" + reeferthreadcount.get());
    return Json.createValue("rejected");
  }

}
