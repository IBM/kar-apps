package com.ibm.research.kar.reeferserver.service;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;
import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.model.OrderSimControls;
import com.ibm.research.kar.reefer.model.ReeferSimControls;

import org.springframework.stereotype.Service;

@Service
public class SimulatorService {


    public void updateVoyageCapacity(String voyageId, int freeCapacity) {
        JsonObject params = Json.createObjectBuilder()
        .add("voyageId",voyageId)
        .add("freeCapacity",freeCapacity)
        .build();
        try {
  
            Response response = Kar.restPost("simservice","/simulator/updatevoyagecapacity", params);
  
        } catch( Exception e) {
            e.printStackTrace();
            
        }
    }
    public void setSimOrderTarget(int orderTarget) {
        System.out.println("SimulatorService.setSimOrderTarget()");
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
        System.out.println("SimulatorService.setSimOrderWindow()");
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
        System.out.println("SimulatorService.setSimOrderUpdateFrequency()");
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
        System.out.println("SimulatorService.updateOrderSimControls()");
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
        System.out.println("SimulatorService.getOrderSimControls()");
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
        System.out.println("SimulatorService.getOrderSimControls() - target:"+target+" window:"+window+" updateFrequency:"+updateFrequency);
        return new OrderSimControls(target, window, updateFrequency);
    }
    public int getSimOrderTarget() {
        System.out.println("SimulatorService.getSimOrderTarget()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getordertarget");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("SimulatorService.getSimOrderTarget() Sim Response = "+respValue);
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
        System.out.println("SimulatorService.getOrderSimWindow()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getorderwindow");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("SimulatorService.getSimOrderTarget() Sim Response = "+respValue);
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
        System.out.println("SimulatorService.getOrderSimUpdateFrequency()");
        int orderTarget=0;
        try {
            Response response = Kar.restGet("simservice", "simulator/getorderupdates");
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("SimulatorService.getSimOrderTarget() Sim Response = "+respValue);
            orderTarget = Integer.parseInt(respValue.toString());
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
        } 
        return orderTarget;
    }
    public ReeferSimControls getReeferSimControls() {
        System.out.println("SimulatorService.getReeferSimControls ");
        Response response = Kar.restGet("simservice", "simulator/getreefercontrols");
        JsonValue respValue = response.readEntity(JsonValue.class);
        System.out.println("Response = "+respValue);
        int failureRate = respValue.asJsonObject().getInt("failuretarget");
        int updateFrequency = respValue.asJsonObject().getInt("reeferupdates");
		return new ReeferSimControls(failureRate,updateFrequency);
	}
	public void updateReeferSimControls(ReeferSimControls simControls) {
        System.out.println("SimulatorService.updateReeferSimControls()");
        try {
            JsonObject body = Json.createObjectBuilder().
                add("reeferupdates", simControls.getUpdateFrequency()).
                add("failuretarget", simControls.getFailureRate()).
                build();

            Response response = Kar.restPost("simservice", "simulator/setreefercontrols", body);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
		/*
			JsonObject message = Json.createObjectBuilder().build();
			JsonValue reply = actorCall(  actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId),"getStats", message); 
			JsonObject controls = reply.asJsonObject();
			*/
			System.out.println("SimulatorService.updateReeferSimControls - failureRate:"+simControls.getFailureRate()+" frequencyUpdate:"+simControls.getUpdateFrequency());
        }
        public void generateAnomaly() {
            System.out.println("SimulatorService.generateAnomaly()");
            try {
                Response response = Kar.restPost("simservice", "simulator/createanomaly", JsonValue.NULL);
                JsonValue respValue = response.readEntity(JsonValue.class);
                System.out.println("Response = "+respValue);
            } catch (ActorMethodNotFoundException ee) {
                ee.printStackTrace();
            //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
      
            } catch( Exception ee) {
                ee.printStackTrace();
            } 
        }

        public void createOrder() {
            System.out.println("SimulatorService.createOrder()");
            try {
 
    
                Response response = Kar.restPost("simservice", "simulator/createorder", JsonValue.NULL);
                JsonValue respValue = response.readEntity(JsonValue.class);
                System.out.println("Response = "+respValue);
            } catch (ActorMethodNotFoundException ee) {
                ee.printStackTrace();
            //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
      
            } catch( Exception ee) {
                ee.printStackTrace();
            } 
            }
}