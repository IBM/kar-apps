package com.ibm.research.kar.reeferserver.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.ibm.research.kar.reefer.common.time.TimeUtils;
@RestController
@CrossOrigin("*")
public class TimeController {

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
    @PostMapping("/time/advance")
	public Instant  nextDay() {
        System.out.println("TimeController.nextDay()");
        return TimeUtils.getInstance().advanceDate(1);
	}
}