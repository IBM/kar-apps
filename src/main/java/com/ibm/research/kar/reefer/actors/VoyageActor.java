package com.ibm.research.kar.reefer.actors;
import com.ibm.research.kar.actor.ActorRef;
import static com.ibm.research.kar.Kar.*;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.supervisor.ActorSupervisor;

import javax.inject.Inject;
/*
import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;
import static com.ibm.research.kar.Kar.actorTell;
*/
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

@Actor
public class VoyageActor extends ActorBoilerplate {
    @Inject
    ActorSupervisor supervisor;
    
    @Activate
    public void init() {

    }

    @Remote
    public void reserve(JsonObject order) {

        System.out.println("VoyageActor.reserve() called");
        // ActorRef reeferProvisioner =  actorRef("reefer-provisioner","444");
        // try {
        //     JsonValue reply = actorCall( reeferProvisioner, "bookReefers", order);

        // } catch( ActorMethodNotFoundException ee) {
        //     ee.printStackTrace();
        // }
     }

    @Deactivate
    public void kill() {
        
    }
}