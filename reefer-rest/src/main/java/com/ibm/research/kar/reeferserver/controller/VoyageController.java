package com.ibm.research.kar.reeferserver.controller;

//import static com.ibm.research.kar.Kar.actorCall;
//import static com.ibm.research.kar.Kar.actorRef;
//import static com.ibm.research.kar.Kar.restPost;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private static final Logger logger = Logger.getLogger(VoyageController.class.getName());

  /**
   * Returns voyages matching given originPort, destinationPort and departure date
   * 
   * @param message - json encoded message with query params
   * @return - list of voyages matching the query
   */
  @PostMapping("/voyage/matching")
  public List<Voyage> getMatchingVoyages(@RequestBody String message) {
    String originPort = "";
    String destinationPort = "";
    Instant date = null;

    try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {

      JsonObject req = jsonReader.readObject();
      originPort = req.getString("origin");
      destinationPort = req.getString("destination");
      String departureDate = req.getString("departureDate");
      date = Instant.parse(departureDate);
      if ( logger.isLoggable(Level.INFO)) {
        logger.info("VoyageController.getMatchingVoyages() - origin:" + originPort + " destination:"
                + destinationPort + " date:" + departureDate);
      }
    } catch (Exception e) {
      logger.log(Level.WARNING,e.getMessage(),e);
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
    Instant startDate;
    Instant endDate;

    try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {

      JsonObject req = jsonReader.readObject();
      startDate = Instant.parse(req.getString("startDate"));
      endDate = Instant.parse(req.getString("endDate"));
      if ( logger.isLoggable(Level.INFO)) {
        logger.info("VoyageController.getVoyagesInRange() - startDate:" + startDate.toString() + " endDate:"
                + endDate.toString());
      }
    } catch (Exception e) {
      logger.log(Level.WARNING,e.getMessage(),e);
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
    return activeVoyages();
  }
/*
  @GetMapping("/voyage/upcoming")
  public List<Voyage> getShippingSchedule() {
    return shipScheduleService.get();
  }
*/
  @GetMapping("/voyage/state/{id}")
  public Voyage getVoyageState(@PathVariable("id") String id) throws VoyageNotFoundException {
    return shipScheduleService.getVoyage(id);
  }

  @GetMapping("/voyage/info/{id}")
  public Voyage getVoyage(@PathVariable("id") String id) throws VoyageNotFoundException {
    return shipScheduleService.getVoyage(id);
  }

  /**
   * Update voyage as arrived.
   * 
   * @param message - json encoded params: daysAtSea, voyageId
   * @throws VoyageNotFoundException
   */
  @PostMapping("/voyage/update/arrived")
  public void arrived(@RequestBody String message) {
    try {
      String voyageId = JsonUtils.getVoyageId(message);
      if ( logger.isLoggable(Level.INFO)) {
        logger.info("VoyageController.delivered() - id:" + voyageId + " message:" + message);
      }
      shipScheduleService.updateDaysAtSea(voyageId, JsonUtils.getDaysAtSea(message));
      voyageService.voyageEnded(voyageId);
      orderService.updateOrderStatus(voyageId, OrderStatus.DELIVERED);
    } catch (Exception e) {
      logger.log(Level.WARNING,e.getMessage(),e);
    }

  }

  /**
   * Update voyage as in-transit to the destination.
   * 
   * @param message - json encoded params: daysAtSea and voyageId
   * @throws VoyageNotFoundException
   */
  @PostMapping("/voyage/update/departed")
  public void departed(@RequestBody String message) {
    try {
      String voyageId = JsonUtils.getVoyageId(message);
      if ( logger.isLoggable(Level.INFO)) {
        logger.info("VoyageController.departed() - id:" + voyageId + " message:" + message);
      }
      shipScheduleService.updateDaysAtSea(voyageId, JsonUtils.getDaysAtSea(message));
      voyageService.voyageDeparted(voyageId);
      orderService.updateOrderStatus(voyageId, OrderStatus.INTRANSIT);
    }  catch (Exception e) {
      logger.log(Level.WARNING,e.getMessage(),e);
    }

  }

  /**
   * Updates ship current position
   * 
   * @param message - json encoded params: daysAtSea, voyageId
   * @throws VoyageNotFoundException
   */
  @PostMapping("/voyage/update/position")
  public void updateShipPosition(@RequestBody String message) {
    try {
      String voyageId = JsonUtils.getVoyageId(message);
      int daysAtSea = JsonUtils.getDaysAtSea(message);
      if ( logger.isLoggable(Level.INFO)) {
        logger.info("VoyageController.updateShipPosition() voyageId=" + voyageId + " Voyage Status:"
                + voyageService.getVoyageStatus(voyageId) + " daysAtSea: " + daysAtSea);
      }
      if (daysAtSea > 0) {
        shipScheduleService.updateDaysAtSea(voyageId, daysAtSea);
      }
    } catch (Exception e) {
      logger.log(Level.WARNING,e.getMessage(),e);
    }

  }

  /**
   * Updates reefer counts
   * 
   * @param message
   * @throws VoyageNotFoundException
   */
  @PostMapping("/voyage/update")
  public void updateReeferInventory(@RequestBody String message)  {
     try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
      JsonObject req = jsonReader.readObject();
      String voyageId = req.getString("voyageId");
       if ( logger.isLoggable(Level.FINE)) {
         logger.fine("VoyageController.updateReeferInventory() voyageId=" + voyageId + " Voyage Status:"
                 + voyageService.getVoyageStatus(voyageId));
       }
      if (req.containsKey("reeferCount")) {
        reeferInventoryChange(req);
      }

    } catch (Exception e) {
       logger.log(Level.WARNING,e.getMessage(),e);
    }
  }

  /**
   * Returns all routes
   * 
   * @return - list of routes
   */
  @GetMapping("/voyage/routes")
  public List<Route> getRoutes() {
    return shipScheduleService.getRoutes();
  }

  private void updateSimulator(String voyageId, int freeCapacity) {
    JsonObject params = Json.createObjectBuilder().add("voyageId", voyageId).add("freeCapacity", freeCapacity).build();
    try {
      //restPost("simservice", "/simulator/updatevoyagecapacity", params);
      Kar.Services.post("simservice", "/simulator/updatevoyagecapacity", params);
    } catch (Exception e) {
      logger.log(Level.WARNING,"",e);
    }

  }

  private void reeferInventoryChange(JsonObject req) throws VoyageNotFoundException, ShipCapacityExceeded {
    int reeferCount = req.getInt("reeferCount");
    String voyageId = req.getString("voyageId");
    int shipFreeCapacity = shipScheduleService.updateFreeCapacity(voyageId, reeferCount);
    updateSimulator(voyageId, shipFreeCapacity);
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
    try {
      for (Voyage voyage : activeVoyages) {
        voyage.setOrderCount(voyageOrders(voyage.getId()));
      }
    } catch (Exception e) {
      logger.log(Level.WARNING,e.getMessage(),e);
    }

    return activeVoyages;
  }

  /**
   * Update GUI order counts
   */

  @PostMapping("/voyage/updateGui")
  public void updateGui(@RequestBody String currentDate) {
    if ( logger.isLoggable(Level.FINE)) {
      logger.fine("VoyageController.updateGui() - updating GUI with active schedule - currentDate:" + currentDate);
    }
    try {
      List<Voyage> activeVoyages = activeVoyages();
      gui.sendActiveVoyageUpdate(activeVoyages, currentDate);
      int totalActiveOrders = 0;
      for (Voyage voyage : activeVoyages) {
        totalActiveOrders += voyage.getOrderCount();
      }
      gui.updateInTransitOrderCount(totalActiveOrders);
      gui.updateFutureOrderCount(orderService.getOrderCount(Constants.BOOKED_ORDERS_KEY));
      gui.updateSpoiltOrderCount(orderService.getOrderCount(Constants.SPOILT_ORDERS_KEY));

    } catch( Exception e) {
      logger.log(Level.WARNING,e.getMessage(),e);
    }
  }

}