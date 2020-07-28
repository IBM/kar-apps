package com.ibm.research.kar.reeferserver.controller;

import java.io.StringReader;
import java.time.Instant;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.Delay;
import com.ibm.research.kar.reeferserver.service.VoyageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class SimulatorController {
    @Autowired
    private VoyageService voyageService;
    
  @PostMapping("/simulator/delay")
//    @PostMapping("/simulator/automode")
	public Instant  shipSimulatorDelay(@RequestBody String delay) {
        System.out.println("TimeConSimulatorControllertroller.shipSimulatorDelay() - delay "+delay);
        int delayTime=0;
        try (JsonReader jsonReader = Json.createReader(new StringReader(delay))) {
             
            JsonObject req = jsonReader.readObject();
            delayTime = req.getInt("delay");
            System.out.println("TimeConSimulatorControllertroller.shipSimulatorDelay() - delayTime "+delayTime);

          } catch( Exception e) {
            e.printStackTrace();
          }
        voyageService.changeDelay(delayTime);
        return TimeUtils.getInstance().getCurrentDate();
    }
/*
    @PostMapping("/simulator/manualmode")
	public Instant  manualMode() {
        System.out.println("TimeConSimulatorControllertroller.manualMode()");
        try {
          voyageService.changeDelay(0);
        } catch( Exception e) {
          e.printStackTrace();
        }
       
        return TimeUtils.getInstance().getCurrentDate();
    }
    */
}