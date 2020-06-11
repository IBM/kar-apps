package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.actor.ActorRef;
import static com.ibm.research.kar.Kar.*;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
@Actor
public class OrderActor extends ActorBoilerplate {
    
    @Activate
    public void init() {

    }

    @Remote
    public void createOrder(JsonObject order) {
        ActorRef voyageActor =  actorRef("voyage","444");
        try {
            JsonValue reply = actorCall( voyageActor, "reserve", order);

        } catch( ActorMethodNotFoundException ee) {
            ee.printStackTrace();
        }
    }
}