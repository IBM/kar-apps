package com.ibm.research.kar.reeferserver.controller;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.ibm.research.kar.reeferserver.ReeferServerApplication;
@RestController
@CrossOrigin("*")
public class TimeController {
    private transient final SimpleDateFormat dateFormat 
       = new SimpleDateFormat("MM/dd/yyyy");

    @GetMapping("/time/startDate")
	public Date  getStartDate() {
        System.out.println("TimeController.getStartDate()");
        Date date =  ReeferServerApplication.getStartDate();
        System.out.println("TimeController.getStartDate() - Date:"+date.toString());

//        Map env = System.getenv();
//for (Iterator it=env.entrySet().iterator(); it.hasNext(); ) {
//   Map.Entry entry = (Map.Entry)it.next();
//   System.out.println(entry.getKey() + " = " + entry.getValue());
//}
        return date;
    }
    @GetMapping("/time/currentDate")
	public Date  getCurrentDate() {
        System.out.println("TimeController.getCurrentDate()");
        Date date =  ReeferServerApplication.getCurrentDate();
        System.out.println("TimeController.getCurrentDate() - Date:"+date.toString());

        return date;
    }
    @PostMapping("/time/advance")
	public Date  nextDay() {
        System.out.println("TimeController.nextDay()");
        return ReeferServerApplication.advanceDate(1);
	}
}