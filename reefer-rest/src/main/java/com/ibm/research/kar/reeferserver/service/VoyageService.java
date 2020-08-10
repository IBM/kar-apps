package com.ibm.research.kar.reeferserver.service;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;

import org.springframework.stereotype.Component;

@Component
public class VoyageService {
    
    public void nextDay() {
        System.out.println("VoyageService.nextDay()");
        try {
            Response response = Kar.restPost("simservice", "simulator/advancetime", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);

        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }

    public void changeDelay(int delay) {
        System.out.println("VoyageService.changeDelay() - delay:"+delay);
        try {
            JsonObject delayArg = 
                Json.createObjectBuilder().add("value", delay).build();
            Response response =  Kar.restPost("simservice", "simulator/setunitdelay",delayArg); //Json.createValue(delay));
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("VoyageService.getDelay() Sim Response = "+respValue);
 
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
    public int getDelay() throws Exception {
        System.out.println("VoyageService.getDelay()");
        Response response =  Kar.restGet("simservice", "simulator/getunitdelay"); //Json.createValue(delay));
        JsonValue respValue = response.readEntity(JsonValue.class);
        System.out.println("VoyageService.getDelay() Sim Response = "+respValue);

        return Integer.parseInt(respValue.toString());


    }
}