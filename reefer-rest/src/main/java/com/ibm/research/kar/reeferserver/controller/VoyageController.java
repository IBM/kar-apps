package com.ibm.research.kar.reeferserver.controller;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;
import static com.ibm.research.kar.Kar.restPost;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.common.error.ShipCapacityExceeded;
import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reeferserver.service.OrderService;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.reeferserver.service.VoyageService;
import com.ibm.research.kar.reefer.model.VoyageStatus;
import com.ibm.research.kar.reefer.common.json.JsonUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class VoyageController {
  @Autowired
  private ScheduleService shipScheduleService;
  @Autowired
  private OrderService orderService;
  @Autowired
  private VoyageService voyageService;
  @Autowired
  private GuiController gui;

  /**
   * Returns voyages matching given originPort, destinationPort and departure date
   * 
   * @param message - json encoded message with query params
   * @return - list of voyages matching the query
   */
  @PostMapping("/voyage/matching")
  public List<Voyage> getMatchingVoyages(@RequestBody String message) {
    System.out.println("VoyageController.getMatchingVoyages()");
    String originPort = "";
    String destinationPort = "";
    Instant date = null;

    try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {

      JsonObject req = jsonReader.readObject();
      originPort = req.getString("origin");
      destinationPort = req.getString("destination");
      String departureDate = req.getString("departureDate");
      date = Instant.parse(departureDate);
      System.out.println("VoyageController.getMatchingVoyages() - origin:" + originPort + " destination:"
          + destinationPort + " date:" + departureDate);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return shipScheduleService.getMatchingSchedule(originPort, destinationPort, date);
  }

  /**
   * Returns voyages which are in-transit within a given date range
   * 
   * @param message - json encoded query params
   * @return - list of voyages
   */
  @PostMapping("/voyage/inrange")
  public List<Voyage> getVoyagesInRange(@RequestBody String message) {
    System.out.println("VoyageController.getVoyagesInRange()");

    Instant startDate;
    Instant endDate;

    try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {

      JsonObject req = jsonReader.readObject();
      startDate = Instant.parse(req.getString("startDate"));
      endDate = Instant.parse(req.getString("endDate"));
      System.out.println("VoyageController.getVoyagesInRange() - startDate:" + startDate.toString() + " endDate:"
          + endDate.toString());
    } catch (Exception e) {
      e.printStackTrace();
      return new ArrayList<Voyage>();
    }
    return shipScheduleService.getMatchingSchedule(startDate, endDate);
  }

  /**
   * Returns a list of active voyages which are currently at sea
   * 
   * @return - list of voyages
   */
  @GetMapping("/voyage/active")
  public List<Voyage> getActiveVoyages() {
    System.out.println("VoyageController.getActiveVoyages()");

    return activeVoyages();
  }

  @GetMapping("/voyage/upcoming")
  public List<Voyage> getShippingSchedule() {
    System.out.println("VoyageController.getShippingSchedule()");
    return shipScheduleService.get();
  }

  @GetMapping("/voyage/state/{id}")
  public Voyage getVoyageState(@PathVariable("id") String id) throws VoyageNotFoundException {
    System.out.println("VoyageController.getVoyageState()");
    return shipScheduleService.getVoyage(id);
  }

  @GetMapping("/voyage/info/{id}")
  public Voyage getVoyage(@PathVariable("id") String id) throws VoyageNotFoundException {
    System.out.println("VoyageController.getVoyage()");

    return shipScheduleService.getVoyage(id);

  }

  /**
   * Update voyage as arrived.
   * 
   * @param message - json encoded params: daysAtSea, voyageId
   * @throws VoyageNotFoundException
   */
  @PostMapping("/voyage/update/delivered")
  public void delivered(@RequestBody String message) throws VoyageNotFoundException {
    String voyageId = JsonUtils.getVoyageId(message);
    System.out.println("VoyageController.delivered() - id:" + voyageId + " message:" + message);
    shipScheduleService.updateDaysAtSea(voyageId, JsonUtils.getDaysAtSea(message));
    //updateShipPositions(TimeUtils.getInstance().getCurrentDate().toString());
    voyageService.voyageEnded(voyageId);
    orderService.updateOrderStatus(voyageId, OrderStatus.DELIVERED);
    //updateGui(TimeUtils.getInstance().getCurrentDate().toString());
  }

  /**
   * Update voyage as in-transit to the destination.
   * 
   * @param message - json encoded params: daysAtSea and voyageId
   * @throws VoyageNotFoundException
   */
  @PostMapping("/voyage/update/departed")
  public void departed(@RequestBody String message) throws VoyageNotFoundException {
    String voyageId = JsonUtils.getVoyageId(message);
    System.out.println("VoyageController.departed() - id:" + voyageId + " message:" + message);
    shipScheduleService.updateDaysAtSea(voyageId, JsonUtils.getDaysAtSea(message));
    //updateShipPositions(TimeUtils.getInstance().getCurrentDate().toString());
    voyageService.voyageDeparted(voyageId);
    orderService.updateOrderStatus(voyageId, OrderStatus.INTRANSIT);
  }

  /**
   * Updates ship current position
   * 
   * @param message - json encoded params: daysAtSea, voyageId
   * @throws VoyageNotFoundException
   */
  @PostMapping("/voyage/update/position")
  public void updateShipPosition(@RequestBody String message) throws VoyageNotFoundException {
    System.out.println("VoyageController.updateShipPosition() " + message);
    String voyageId = JsonUtils.getVoyageId(message);
    int daysAtSea = JsonUtils.getDaysAtSea(message);
    System.out.println("VoyageController.updateShipPosition() voyageId=" + voyageId + " Voyage Status:"
        + voyageService.getVoyageStatus(voyageId) + " daysAtSea: " + daysAtSea);
    if (daysAtSea > 0) {
      shipScheduleService.updateDaysAtSea(voyageId, daysAtSea);
     // updateShipPositions(TimeUtils.getInstance().getCurrentDate().toString());
    }
  }

  /**
   * Updates reefer counts
   * 
   * @param message
   * @throws VoyageNotFoundException
   */
  @PostMapping("/voyage/update")
  public void updateReeferInventory(@RequestBody String message) throws VoyageNotFoundException {
    System.out.println("VoyageController.updateReeferInventory() " + message);

    // update can either be related to ship movement or reefer inventory change
    try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
      JsonObject req = jsonReader.readObject();
      String voyageId = req.getString("voyageId");

      System.out.println("VoyageController.updateReeferInventory() voyageId=" + voyageId + " Voyage Status:"
          + voyageService.getVoyageStatus(voyageId));

      if (req.containsKey("reeferCount")) {
        reeferInventoryChange(req);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Returns all routes
   * 
   * @return - list of routes
   */
  @GetMapping("/voyage/routes")
  public List<Route> getRoutes() {
    System.out.println("VoyageController.getRoutes()");
    return shipScheduleService.getRoutes();
  }

  private void updateSimulator(String voyageId, int freeCapacity) {
    JsonObject params = Json.createObjectBuilder().add("voyageId", voyageId).add("freeCapacity", freeCapacity).build();
    try {
      restPost("simservice", "/simulator/updatevoyagecapacity", params);
    } catch (Exception e) {
      e.printStackTrace();

    }

  }

  private void reeferInventoryChange(JsonObject req) throws VoyageNotFoundException, ShipCapacityExceeded {
    int reeferCount = req.getInt("reeferCount");
    String voyageId = req.getString("voyageId");

    System.out.println("VoyageController.updateVoyageState() reeferCount=" + reeferCount);

    int shipFreeCapacity = shipScheduleService.updateFreeCapacity(voyageId, reeferCount);
    updateSimulator(voyageId, shipFreeCapacity);

    System.out.println("VoyageController.updateVoyageState() - Ship booked - Ship free capacity:" + shipFreeCapacity);
  }

  /**
   * Returns number of orders on this voyage
   * 
   * @param voyageId
   * @return
   */
  private int voyageOrders(String voyageId) {
    return voyageService.getVoyageOrderCount(voyageId);
  }

  /**
   * Returns a list of voyages currently at sea
   * 
   * @return
   */
  private List<Voyage> activeVoyages() {
    List<Voyage> activeVoyages = shipScheduleService.getActiveSchedule();
    System.out.println("VoyageController.activeVoyages() - active voyages:" + activeVoyages.size());
    try {

      for (Voyage voyage : activeVoyages) {
        voyage.setOrderCount(voyageOrders(voyage.getId()));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return activeVoyages;
  }
/*
  private void updateShipPositions(String currentDate) {
    long t1 = System.currentTimeMillis();
    List<Voyage> activeVoyages = activeVoyages();
    long e1 = System.currentTimeMillis();
    System.out.println("VoyageController.updateGui() - ,,.. time to fetch active voyages " + (e1 - t1) + " ms");
    gui.sendActiveVoyageUpdate(activeVoyages, currentDate);
  }

 */
  /**
   * Update the GUI
   */

  @PostMapping("/voyage/updateGui")
  public void updateGui(@RequestBody String currentDate) {

    long start = System.currentTimeMillis();
    System.out.println("VoyageController.updateGui() - updating GUI with active schedule - currentDate:" + currentDate);

    long t1 = System.currentTimeMillis();
    List<Voyage> activeVoyages = activeVoyages();
    long e1 = System.currentTimeMillis();
    System.out.println("VoyageController.updateGui() - ,,.. time to fetch active voyages " + (e1 - t1) + " ms");
    gui.sendActiveVoyageUpdate(activeVoyages, currentDate);

    long t2 = System.currentTimeMillis();
    int totalActiveOrders = 0;
    for (Voyage voyage : activeVoyages) {
      totalActiveOrders += voyage.getOrderCount();
    }
    long e2 = System.currentTimeMillis();
    System.out.println("VoyageController.updateGui() - ,,.. time to total voyage orders " + (e2 - t2) + " ms");
    long t3 = System.currentTimeMillis();
    gui.updateInTransitOrderCount(totalActiveOrders);
    long e3 = System.currentTimeMillis();
    System.out
        .println("VoyageController.updateGui() - ,,.. time to total update in-transit orders " + (e3 - t3) + " ms");

    long t4 = System.currentTimeMillis();
    gui.updateFutureOrderCount(orderService.getOrderCount(Constants.BOOKED_ORDERS_KEY)); // "booked-orders"));
    long e4 = System.currentTimeMillis();
    System.out.println("VoyageController.updateGui() - ,,.. time to total update future orders " + (e4 - t4) + " ms");

    long t5 = System.currentTimeMillis();
    gui.updateSpoiltOrderCount(orderService.getOrderCount(Constants.SPOILT_ORDERS_KEY));
    long e5 = System.currentTimeMillis();
    System.out.println("VoyageController.updateGui() - ,,.. time to total update spoilt orders " + (e5 - t5) + " ms");
    long end = System.currentTimeMillis();
    System.out.println("VoyageController.activeVoyages() - ,,.. Done - Total Active Orders:" + totalActiveOrders
        + " voyage updateGui took " + (end - start) + " ms");

  }

}