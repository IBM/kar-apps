package com.ibm.research.kar.reeferserver.controller;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reeferserver.service.VoyageService;
@RestController
@CrossOrigin("*")
public class TimeController {
    @Autowired
    private VoyageService voyageService;

    @PostMapping("/time/startDate")
	public Instant  getStartDate() {
        System.out.println("TimeController.getStartDate()");
        Instant date =  TimeUtils.getInstance().getStartDate();
        System.out.println("TimeController.getStartDate() - Date:"+date.toString());
        return date;
    }
    @PostMapping("/time/currentDate")
	public Instant  getCurrentDate() {
        System.out.println("TimeController.getCurrentDate()");
        Instant date =  TimeUtils.getInstance().getCurrentDate();
       System.out.println("TimeController.getCurrentDate() - Date:"+date.toString());

        return date;
    }
    @PostMapping("/time/nextDay")
	public Instant  nextDay() {
        System.out.println("TimeController.nextDay()");
    
        voyageService.nextDay();
        String date = "";
        try {
            date = TimeUtils.getInstance().getCurrentDate().toString().substring(0,10);
            System.out.println("nextDay() - Returning Date >>>>>>>"+date);
        } catch( Exception e) {
            e.printStackTrace();
        }
            
       return TimeUtils.getInstance().getCurrentDate();
    }
    @PostMapping("/time/advance")
	public Instant  advance() {
        System.out.println("TimeController.advance()");
        Instant time = 
            TimeUtils.getInstance().advanceDate(1);
    //    voyageService.advanceTime();
        return time;
    }
   
}