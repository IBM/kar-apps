package com.ibm.research.kar.reeferserver.controller;

import java.time.Instant;

import javax.json.Json;
import javax.json.JsonObject;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.reeferserver.service.SimulatorService;
import com.ibm.research.kar.reeferserver.service.VoyageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class TimeController {
    @Autowired
    private VoyageService voyageService;
    @Autowired
    private ScheduleService schduleService;

    @PostMapping("/time/startDate")
    public Instant getStartDate() {
        System.out.println("TimeController.getStartDate()");
        Instant date = TimeUtils.getInstance().getStartDate();
        System.out.println("TimeController.getStartDate() - Date:" + date.toString());
        return date;
    }

    @PostMapping("/time/currentDate")
    public Instant getCurrentDate() {
        System.out.println("TimeController.getCurrentDate()");
        Instant date = TimeUtils.getInstance().getCurrentDate();
        System.out.println("TimeController.getCurrentDate() - Date:" + date.toString());

        return date;
    }

    /*
     * Called by the GUI to advance time while in manual mode.
     * 
     */
    @PostMapping("/time/nextDay")
    public Instant nextDay() {
        System.out.println("TimeController.nextDay()");

        voyageService.nextDay();
        String date = "";
        try {
            date = TimeUtils.getInstance().getCurrentDate().toString().substring(0, 10);
            System.out.println("nextDay() - Returning Date >>>>>>>" + date);
            JsonObject message = Json.createObjectBuilder()
                    .add(Constants.DATE_KEY, Json.createValue(TimeUtils.getInstance().getCurrentDate().toString()))
                    .build();

            Kar.actorCall(Kar.actorRef(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "releaseReefersfromMaintenance", message);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return TimeUtils.getInstance().getCurrentDate();
    }

    @PostMapping("/time/advance")
    public Instant advance() {
        Instant time = TimeUtils.getInstance().advanceDate(1);
        System.out.println("TimeController.advance() ***************************************** NEXT DAY "
                + time.toString() + " ***************************************************************");
        try {

            schduleService.generateNextSchedule(time);
            JsonObject message = Json.createObjectBuilder().add(Constants.DATE_KEY, Json.createValue(time.toString()))
                    .build();

            Kar.actorCall(Kar.actorRef(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId),
                    "releaseReefersfromMaintenance", message);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return time;
    }

}