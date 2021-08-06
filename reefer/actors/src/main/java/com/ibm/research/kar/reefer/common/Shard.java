package com.ibm.research.kar.reefer.common;

import com.ibm.research.kar.reefer.actors.AnomalyManagerActor;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class Shard {
    private long lowerBound;
    private long upperBound;


    public Shard(long lowerBound, long upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public long getLowerBound() {
        return lowerBound;
    }

    public long getUpperBound() {
        return upperBound;
    }

    public long getSize() {
        return upperBound-lowerBound;
    }
    public JsonObject serialize() {
        JsonObjectBuilder shardBuilder = Json.createObjectBuilder();
        shardBuilder.
                add("reefer-id-lower-bound", getLowerBound()).
                add("reefer-id-upper-bound", getUpperBound());
        return shardBuilder.build();
    }
    @Override
    public String toString() {
        return "{" +
                ", lowerBound=" + lowerBound +
                ", upperBound=" + upperBound +
                '}';
    }
}
