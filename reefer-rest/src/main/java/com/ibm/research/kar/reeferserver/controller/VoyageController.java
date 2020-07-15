package com.ibm.research.kar.reeferserver.controller;
import java.io.StringReader;
import java.text.SimpleDateFormat;
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
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;

import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reefer.model.*;
import com.ibm.research.kar.reeferserver.service.ScheduleService;

@RestController
@CrossOrigin("*")public class VoyageController {
    @Autowired
    private ScheduleService shipScheduleService;

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
    @PostMapping("/voyage/update")
      public void updateVoyageState(  @RequestBody String state) throws VoyageNotFoundException{
          System.out.println("VoyageController.updateVoyageState() "+state);
          try (JsonReader jsonReader = Json.createReader(new StringReader(state))) {
             
            JsonObject req = jsonReader.readObject();
            
            req.forEach( (key, value) -> {
            
              if ( key.equals("daysAtSea") ) {

              } else if ( key.equals("freeCapacity") ) {

              } else if ( key.equals("reefers") ) {

              }

            System.out.println("VoyageController.updateVoyageState Key:"+key+" value:"+value );
            });
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
}