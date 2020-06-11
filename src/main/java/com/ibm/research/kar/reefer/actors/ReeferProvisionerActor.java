package com.ibm.research.kar.reefer.actors;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
@Actor
public class ReeferProvisionerActor {
    @Activate
    public void init() {

    }

    @Remote
    public void createbookReefers(JsonObject order) {
        //JsonObject result = Json.createObjectBuilder().
    }
}