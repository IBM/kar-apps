package com.ibm.research.kar.reeferserver.controller;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ibm.research.kar.reeferserver.error.VoyageNotFoundException;
import com.ibm.research.kar.reeferserver.model.*;
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
    @ResponseBody
    public Voyage getVoyageState(@PathVariable("id") String id) throws VoyageNotFoundException{
      System.out.println("VoyageController.getVoyageState()");
      return shipScheduleService.getVoyage(id);
    } 
    @GetMapping("/voyage/routes")
    public List<Route> getRoutes() {
      System.out.println("VoyageController.getRoutes()");
      return shipScheduleService.getRoutes();
    }
}