package com.ibm.research.kar.reeferserver.controller;

import java.io.StringReader;
import java.time.Instant;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.ibm.research.kar.reefer.common.time.TimeUtils;
import com.ibm.research.kar.reefer.model.DelayTarget;
import com.ibm.research.kar.reefer.model.OrderSimControls;
import com.ibm.research.kar.reefer.model.ReeferSimControls;
import com.ibm.research.kar.reeferserver.service.OrderService;
import com.ibm.research.kar.reeferserver.service.SimulatorService;
import com.ibm.research.kar.reeferserver.service.VoyageService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class SimulatorController {
    @Autowired
    private VoyageService voyageService;
 
    @Autowired
    private SimulatorService simulatorService;

  @PostMapping("/simulator/delay")
	public Instant  shipSimulatorDelay(@RequestBody String delay) {
        System.out.println("SimulatorController.shipSimulatorDelay() - delay "+delay);
        int delayTime=0;
        try (JsonReader jsonReader = Json.createReader(new StringReader(delay))) {
             
            JsonObject req = jsonReader.readObject();
            delayTime = req.getInt("delay");
            System.out.println("SimulatorController.shipSimulatorDelay() - delayTime "+delayTime);

          } catch( Exception e) {
            e.printStackTrace();
          }
        voyageService.changeDelay(delayTime);
        return TimeUtils.getInstance().getCurrentDate();
    }
    @PostMapping("/simulator/getdelay")
    public int  getShipSimulatorDelay() {
        System.out.println("SimulatorController.getShipSimulatorDelay() ");
        try {
          return voyageService.getDelay();
        }  catch( Exception e) {
          e.printStackTrace();
        }
       return -1;
    }
    @GetMapping("/simulator/getdelayandtarget")
    public DelayTarget getDelayAndTarget() {
        System.out.println("SimulatorController.getDelayAndTarget() ");
        try {
          int delay = voyageService.getDelay();
          int target = simulatorService.getSimOrderTarget();
          return new DelayTarget(delay, target);
        }  catch( Exception e) {
          e.printStackTrace();
        }
       return new DelayTarget();
    }
    @PostMapping("/simulator/getsimordertarget")
    public int  getSimOrderTarget() {
      System.out.println("SimulatorControllertroller.getSimOrderTarget() ");

      return simulatorService.getSimOrderTarget();
  }


    @PostMapping("/simulator/setsimordertarget")
    public void  setSimOrderTarget(@RequestBody String body) {
      System.out.println("SimulatorController.setSimOrderTarget() - target "+body);
      int orderTarget=0;
      try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
            
          JsonObject req = jsonReader.readObject();
      
          orderTarget = Integer.valueOf(req.getJsonString("target").toString().replace("\"",""));
          System.out.println("TimeConSimulatorControllertroller.setSimOrderTarget() - orderTarget "+orderTarget);
          simulatorService.setSimOrderTarget(orderTarget);
        } catch( Exception e) {
          e.printStackTrace();
        }
    }
    @GetMapping("/simulator/controls")
	public ReeferSimControls getReeferSimulatorControls() {
		System.out.println("SimulatorController.getReeferSimulatorControls() - Got New Request");
		
		return simulatorService.getReeferSimControls();
	}
	@PostMapping("/simulator/controls/update")
	public void updateReeferSimulatorControls(@RequestBody String body) {
		System.out.println("SimulatorController.updateReeferSimulatorControls() - Got New Request");
    int failureRate=0;
    int updateFrequency = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
          
      JsonObject req = jsonReader.readObject();
      failureRate = Integer.valueOf(req.getJsonString("failureRate").toString().replace("\"",""));
      updateFrequency = Integer.valueOf(req.getJsonString("updateFrequency").toString().replace("\"",""));
    } catch( Exception e ) {
      e.printStackTrace();
    }
      
		simulatorService.updateReeferSimControls(new ReeferSimControls(failureRate, updateFrequency));

	//	return "OK";
	}
  @PostMapping("/simulator/setordersimcontrols")
  public void  setOrderSimControls(@RequestBody String body) {
    System.out.println("SimulatorController.setOrderSimControls() - target "+body);
    int orderTarget=0;
    int orderWindow = 0;
    int updateFrequency = 0;
    try (JsonReader jsonReader = Json.createReader(new StringReader(body))) {
          
        JsonObject req = jsonReader.readObject();
    
        orderTarget = Integer.valueOf(req.getJsonString("target").toString().replace("\"",""));
        orderWindow = Integer.valueOf(req.getJsonString("window").toString().replace("\"",""));
        updateFrequency = Integer.valueOf(req.getJsonString("updateFrequency").toString().replace("\"",""));
        System.out.println("TimeConSimulatorControllertroller.setOrderSimControls() - target "+orderTarget+ " window:"+orderWindow+ " updateFrequency:"+updateFrequency);
        simulatorService.updateOrderSimControls(orderTarget, orderWindow, updateFrequency);
      } catch( Exception e) {
        e.printStackTrace();
      }
    }
    @GetMapping("/simulator/getordersimcontrols")
    public OrderSimControls  getOrderSimControls() {
      System.out.println("SimulatorController.getOrderSimControls() ");
      OrderSimControls controls = simulatorService.getOrderSimControls();
      System.out.println("TimeConSimulatorControllertroller.getordersimcontrols() - target "+controls.getTarget()+ " window:"+controls.getWindow()+ " updateFrequency:"+controls.getUpdateTarget());
      return controls;
    }
    @PostMapping("/simulator/reefer/anomaly")
    public void generateAnomaly() {
      simulatorService.generateAnomaly();
    }
}