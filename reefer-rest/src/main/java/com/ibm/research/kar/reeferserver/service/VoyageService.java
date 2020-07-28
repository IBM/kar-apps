package com.ibm.research.kar.reeferserver.service;

import org.springframework.stereotype.Component;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import static com.ibm.research.kar.Kar.*;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

@Component
public class VoyageService {
    
    public void nextDay() {
        System.out.println("VoyageService.nextDay()");
        try {
            Response response = Kar.restPost("simservice", "simulator/advancetime", JsonValue.NULL);
            JsonValue respValue = response.readEntity(JsonValue.class);
            System.out.println("Response = "+respValue);
        /*
        JsonObjectBuilder props = Json.createObjectBuilder();
		
        props.add("daysAtSea", 10).add("currentDate","2020-07-15");
     //   JsonObjectBuilder orderObject = Json.createObjectBuilder();
     //   orderObject.add("order", props.build());
        JsonObject params = props.build();

        ActorRef voyageActor = actorRef("voyage", "1111");

        JsonValue reply = actorCall(voyageActor, "changePosition", params);
        System.out.println("Voyage Actor reply:"+reply);
        */
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
            System.out.println("Response = "+respValue);
 
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        //    return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(Order.IdKey, order.getId()).build();
  
        } catch( Exception ee) {
			ee.printStackTrace();
		} 
    }
}