package com.ibm.research.kar.reeferserver.service;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.model.OrderSimControls;

import org.springframework.stereotype.Service;

@Service
public class SimulatorService {
    public void setSimOrderTarget(int orderTarget) {
        System.out.println("OrderService.setSimOrderTarget()");
        try {
            JsonObject body = Json.createObjectBuilder().add("value", orderTarget).build();

            Response response = Kar.restPost("simservice", "simulator/setordertarget", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public void setSimOrderWindow(int window) {
        System.out.println("OrderService.setSimOrderWindow()");
        try {
            JsonObject body = Json.createObjectBuilder().add("value", window).build();

            Response response = Kar.restPost("simservice", "simulator/setorderwindow", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public void setSimOrderUpdateFrequency(int updateFrequency) {
        System.out.println("OrderService.setSimOrderUpdateFrequency()");
        try {
            JsonObject body = Json.createObjectBuilder().add("value", updateFrequency).build();

            Response response = Kar.restPost("simservice", "simulator/setorderupdates", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public void updateOrderSimControls(int orderTarget, int window, int updateFrequency) {
        System.out.println("OrderService.updateOrderSimControls()");
        try {
            setSimOrderTarget(orderTarget);
            setSimOrderWindow(window);
            setSimOrderUpdateFrequency(updateFrequency);
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public OrderSimControls getOrderSimControls() {
        System.out.println("OrderService.getOrderSimControls()");
        int target = 0;
        int window = 1;
        int updateFrequency = 2;
        try {
            target = getSimOrderTarget();
            window = getOrderSimWindow();
            updateFrequency = getOrderSimUpdateFrequency();

  
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return new OrderSimControls(target, window, updateFrequency);
    }
    public int getSimOrderTarget() {
        System.out.println("OrderService.getSimOrderTarget()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getordertarget");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("OrderService.getSimOrderTarget() Sim Response = "+respValue);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return orderTarget;
    }
    public int getOrderSimWindow() {
        System.out.println("OrderService.getOrderSimWindow()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getorderwindow");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("OrderService.getSimOrderTarget() Sim Response = "+respValue);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return orderTarget;
    }
    public int getOrderSimUpdateFrequency() {
        System.out.println("OrderService.getOrderSimUpdateFrequency()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getorderupdates");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("OrderService.getSimOrderTarget() Sim Response = "+respValue);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return orderTarget;
    }
}