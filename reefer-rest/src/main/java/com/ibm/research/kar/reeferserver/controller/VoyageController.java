package com.ibm.research.kar.reeferserver.controller;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;
import static com.ibm.research.kar.Kar.restPost;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
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
//import com.ibm.research.kar.Kar.*;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Order.OrderStatus;
import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reeferserver.service.OrderService;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.reeferserver.service.VoyageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")public class VoyageController {
    @Autowired
    private ScheduleService shipScheduleService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private VoyageService voyageService;
    @Autowired
    private GuiController gui;

    @PostMapping("/voyage/matching")
    public List<Voyage> getMatchingVoyages(@RequestBody String body) {
      System.out.println("VoyageController.getMatchingVoyages()");
      String originPort="";
      String destinationPort="";
      Instant date=null;

        try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
             
          JsonObject req = jsonReader.readObject();
          originPort = req.getString("origin");
          destinationPort = req.getString("destination");
          String departureDate = req.getString("departureDate");
          date = Instant.parse(departureDate);
          System.out.println("VoyageController.getMatchingVoyages() - origin:"+originPort+" destination:"+destinationPort+" date:"+departureDate);
        } catch( Exception e) {
          e.printStackTrace();
        }
        return shipScheduleService.getMatchingSchedule(originPort, destinationPort, date);
    } 

    @PostMapping("/voyage/inrange")
    public List<Voyage> getVoyagesInRange(@RequestBody String body) {
      System.out.println("VoyageController.getVoyagesInRange()");

      Instant startDate;
      Instant endDate;

      try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
            
        JsonObject req = jsonReader.readObject();
        startDate = Instant.parse(req.getString("startDate"));
        endDate = Instant.parse(req.getString("endDate"));
        System.out.println("VoyageController.getVoyagesInRange() - startDate:"+startDate.toString()+" endDate:"+endDate.toString());
      } catch( Exception e) {
        e.printStackTrace();
        return new ArrayList<Voyage>();
      }
      return shipScheduleService.getMatchingSchedule(startDate, endDate);
    } 

    @GetMapping("/voyage/active")
    public List<Voyage> getActiveVoyages() {
      System.out.println("VoyageController.getActiveVoyages()");
     // return shipScheduleService.getActiveSchedule();
    
     return activeVoyages();
    } 
    @GetMapping("/voyage/upcoming")
    public List<Voyage> getShippingSchedule() {
      System.out.println("VoyageController.getShippingSchedule()");
      return shipScheduleService.get();
    }
    @GetMapping("/voyage/state/{id}")
    public Voyage getVoyageState(@PathVariable("id") String id) throws VoyageNotFoundException{
      System.out.println("VoyageController.getVoyageState()");
      return shipScheduleService.getVoyage(id);
    } 

    private void updateSimulator(String voyageId, int freeCapacity) {
      JsonObject params = Json.createObjectBuilder()
      .add("voyageId",voyageId)
      .add("freeCapacity",freeCapacity)
      .build();
      try {

          Response response = restPost("simservice","/simulator/updatevoyagecapacity", params);

      } catch( Exception e) {
          e.printStackTrace();
          
      }

    }
    private boolean shipDeparted(int daysAtSea) {
      return daysAtSea == 1;
    }
    @PostMapping("/voyage/update")
      public void updateVoyageState(  @RequestBody String state) throws VoyageNotFoundException{
        System.out.println("VoyageController.updateVoyageState() "+state);
        String voyageId=null;
        int daysAtSea=0;

        try (JsonReader jsonReader = Json.createReader(new StringReader(state))) {
            
          JsonObject req = jsonReader.readObject();
          voyageId = req.getString("voyageId");
          if ( req.containsKey("daysAtSea")) {
            daysAtSea = req.getInt("daysAtSea");

           // System.out.println("VoyageController.updateVoyageState() daysAtSea="+req.getInt("daysAtSea"));
          
            Voyage voyage = shipScheduleService.updateDaysAtSea(voyageId, daysAtSea);
            Instant shipCurrentDate = TimeUtils.getInstance().futureDate(voyage.getSailDateObject(), daysAtSea);
            if ( shipCurrentDate.equals(Instant.parse(voyage.getArrivalDate()) )  ||
                  shipCurrentDate.isAfter(Instant.parse(voyage.getArrivalDate()))) {
              orderService.updateOrderStatus(voyageId, OrderStatus.DELIVERED, daysAtSea);
              System.out.println("VoyageController.updateVoyageState() voyageId="+voyageId+" has ARRIVED ------------------------------------------------------");
              voyageService.voyageEnded(voyageId);
            } else {

              if ( shipDeparted(daysAtSea) ) {
                System.out.println("VoyageController.updateVoyageState() voyageId="+voyageId+" has DEPARTED ------------------------------------------------------");
                Set<Order> orders = voyageService.getOrders(voyageId);
                orders.forEach(order -> {
                  ActorRef orderActor =  Kar.actorRef(ReeferAppConfig.OrderActorName, order.getId());
                  JsonObject params = Json.createObjectBuilder().build();
                  System.out.println("VoyageController.updateVoyageState() voyageId="+order.getVoyageId()+" Notifying Order Actor of departure - OrderID:"+order.getId());
                  actorCall( orderActor, "departed", params);
                });


              }
              orderService.updateOrderStatus(voyageId, OrderStatus.INTRANSIT, daysAtSea);
             // OrderStats stats = orderService.getOrderStats();
              
//              gui.updateInTransitOrderCount(stats.getInTransitOrderCount());
 //             gui.updateFutureOrderCount(stats.getFutureOrderCount());	
            }
            
          } else if ( req.containsKey("reeferCount") ) {
            int reeferCount = req.getInt("reeferCount");
            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@  VoyageController.updateVoyageState() reeferCount="+reeferCount);

            int shipFreeCapacity =
              shipScheduleService.updateFreeCapacity(voyageId, reeferCount);
            updateSimulator(voyageId, shipFreeCapacity);

            System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@  VoyageController.updateVoyageState() - Ship booked - Ship free capacity:"+shipFreeCapacity);

          } else if ( req.containsKey("reefers") ) {
            
          }

        } catch( Exception e) {
          e.printStackTrace();
        }
    } 
    @GetMapping("/voyage/routes")
    public List<Route> getRoutes() {
      System.out.println("VoyageController.getRoutes()");
      return shipScheduleService.getRoutes();
    }
    private int voyageOrders(String voyageId) {
      int voyages = 0;

      try {

        Set<Order> orders = voyageService.getOrders(voyageId);
        int totalOrderReefers = 0;
        for( Order order : orders) {
          totalOrderReefers += orderReefers(order.getId());
        }
        
        ActorRef voyageActor = actorRef("voyage", voyageId);
        JsonObject params = Json.createObjectBuilder().build();
        JsonValue reply = actorCall(voyageActor, "getVoyageOrderCount", params);
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>----------------VoyageController.voyageOrders() - Voyage "+voyageId+" orders:"+reply); //+" total Order Reefers:"+totalOrderReefers);
        voyages = reply.asJsonObject().getInt("orders");
      } catch( Exception e) {
        e.printStackTrace();
      }
      return voyages;

    }
    private int orderReefers(String orderId) {
      int reeferCount = 0;

      try {
        ActorRef orderActor = actorRef("order", orderId);
        JsonObject params = Json.createObjectBuilder().build();
        JsonValue reply = actorCall(orderActor, "reeferCount", params);
        //System.out.println("VoyageController.orderReefers()----------------Order Actor reply"+orderId+" orders:"+reply);
        reeferCount = reply.asJsonObject().getInt(Constants.TOTAL_REEFER_COUNT_KEY);
      } catch( Exception e) {
        e.printStackTrace();
      }
      return reeferCount;

    }
    private List<Voyage> activeVoyages() {
      List<Voyage> activeVoyages = shipScheduleService.getActiveSchedule();
      System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% VoyageController.activeVoyages() - active voyages:"+activeVoyages.size());
      try {
        
        for( Voyage voyage: activeVoyages) {
          voyage.setOrderCount(voyageOrders(voyage.getId()));
        }
       } catch( Exception e) {
        e.printStackTrace();
      }

      return activeVoyages;
    }
    @PostMapping("/voyage/updateGui")
    public void updateGui(@RequestBody String currentDate ) {
      System.out.println("VoyageController.updateGui() - updating GUI with active schedule - currentDate:"+currentDate);
      List<Voyage> activeVoyages = activeVoyages();
      gui.sendActiveVoyageUpdate(activeVoyages, currentDate);

      int totalActiveOrders = 0;
      for( Voyage voyage: activeVoyages) {
        totalActiveOrders += voyage.getOrderCount();
      }
      gui.updateInTransitOrderCount(totalActiveOrders);

      int  futureOrderCount =  orderService.getOrders("booked-orders");

			gui.updateFutureOrderCount(futureOrderCount);	
      System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% VoyageController.activeVoyages() - Done - Total Active Orders:"+totalActiveOrders);
 
    }

}