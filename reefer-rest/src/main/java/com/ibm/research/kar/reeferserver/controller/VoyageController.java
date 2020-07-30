package com.ibm.research.kar.reeferserver.controller;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.*;
import com.ibm.research.kar.reeferserver.service.ScheduleService;

@RestController
@CrossOrigin("*")public class VoyageController {
    @Autowired
    private ScheduleService shipScheduleService;
    @Autowired
    private NotificationController webSocket;

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
      return shipScheduleService.getActiveSchedule();
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

          Response response = Kar.restPost("simservice","/simulator/updatevoyagecapacity", params);

      } catch( Exception e) {
          e.printStackTrace();
          
      }

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

              System.out.println("VoyageController.updateVoyageState() daysAtSea="+req.getInt("daysAtSea"));
              shipScheduleService.updateDaysAtSea(voyageId, daysAtSea);
            } else if ( req.containsKey("reeferCount") ) {
              int reeferCount = req.getInt("reeferCount");
              System.out.println("VoyageController.updateVoyageState() reeferCount="+reeferCount);

              int shipFreeCapacity =
                shipScheduleService.updateFreeCapacity(voyageId, reeferCount);
                updateSimulator(voyageId, shipFreeCapacity);

              System.out.println("VoyageController.updateVoyageState() - Ship booked - Ship free capacity:"+shipFreeCapacity);

            } else if ( req.containsKey("reefers") ) {
              
            }

          //  System.out.println("VoyageController.updateVoyageState Key:"+key+" value:"+value );
          //  });
         //   System.out.println("VoyageController.updateVoyageState+"+req.toString()+" ID:"+req.getString("id")+" DaysAtSea:"+req.getInt("daysAtSea"));
            
            //JsonParser parser = new JsonParser();
         //   JsonObject req = new JsonObject(state);
            //System.out.println("VoyageController.updateVoyageState() - ID:"+req.getString("id") +" DaysAtSea:"+req.getInt("daysAtSea"));
      //  Voyage voyage = shipScheduleService.getVoyage(req.getString("id"));
   
          } catch( Exception e) {
            e.printStackTrace();
          }
          /*
          JsonReader jsonReader = Json.createReader(new StringReader(state));
          JsonObject req = jsonReader.readObject();

          //JsonParser parser = new JsonParser();
       //   JsonObject req = new JsonObject(id).
          System.out.println("VoyageController.updateVoyageState() - ID:"+req.getString("id") +" DaysAtSea:"+req.getInt("daysAtSea"));
      Voyage voyage = shipScheduleService.getVoyage(req.getString("id"));
     // voyage.getRoute().setVessel(vessel);
*/
/*
      // update ship position
      if ( !voyage.getRoute().getVessel().getPosition().equals(vessel.getPosition())) {
        voyage.getRoute().getVessel().setPosition(vessel.getPosition());
      }
      if ( !voyage.getRoute().getVessel().getPosition().equals(vessel.getPosition())) {
        voyage.getRoute().getVessel().setPosition(vessel.getPosition());
      }
      if ( voyage.getRoute().getVessel().getFreeCapacity() != vessel.getFreeCapacity()) {
        voyage.getRoute().getVessel().setFreeCapacity(vessel.getFreeCapacity());
      }
*/
    } 
    @GetMapping("/voyage/routes")
    public List<Route> getRoutes() {
      System.out.println("VoyageController.getRoutes()");
      return shipScheduleService.getRoutes();
    }
    @PostMapping("/voyage/updateGui")
    public void updateGui(@RequestBody String currentDate ) {
      System.out.println("VoyageController.updateGui() - updating GUI with active schedule - currentDate:"+currentDate);
      webSocket.sendActiveVoyageUpdate(shipScheduleService.getActiveSchedule(), currentDate);
      System.out.println("VoyageController.updateGui() - Done");
    }

}