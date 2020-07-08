package com.ibm.research.kar.reeferserver.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.ibm.research.kar.reeferserver.model.ShippingSchedule;
import com.ibm.research.kar.reeferserver.service.ScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ibm.research.kar.reeferserver.model.*;

@RestController
@CrossOrigin("*")
public class ShipScheduleController {

  @Autowired
  private ScheduleService shipScheduleService;

  @GetMapping("/schedules")
  public List<Voyage> getShippingSchedule() {
    System.out.println("ShipScheduleController.getShippingSchedule()");
    return shipScheduleService.get();
  }
  @GetMapping("/routes")
  public List<Route> getRoutes() {
    System.out.println("ShipScheduleController.getRoutes()");
    return shipScheduleService.getRoutes();
  }
}