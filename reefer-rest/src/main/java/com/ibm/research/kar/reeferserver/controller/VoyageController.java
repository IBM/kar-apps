package com.ibm.research.kar.reeferserver.controller;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.ibm.research.kar.reeferserver.ReeferServerApplication;
import com.ibm.research.kar.reeferserver.model.Voyage;
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
}