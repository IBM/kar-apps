package com.ibm.research.kar.reefer.actors;

import static com.ibm.research.kar.Kar.actorCall;
import static com.ibm.research.kar.Kar.actorRef;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Remote;
import com.ibm.research.kar.actor.exceptions.ActorMethodNotFoundException;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferState.State;
import com.ibm.research.kar.reefer.model.JsonOrder;
import com.ibm.research.kar.reefer.model.OrderStatus;
@Actor
public class OrderActor extends BaseActor { //} extends BaseActor {
    private final String REEFERS_KEY = "reefers";
    private final String ORDER_MAP_KEY="order-map-key";
    private final String STATE_KEY = "order-state";
    JsonArray reeferList;
    JsonValue state;
     @Activate
    public void init() {
        System.out.println("OrderActor.init() called id:"+getId()+" type:"+this.getType());
        try {
            
            JsonValue reefers =  Kar.actorGetState(this, REEFERS_KEY);//get(this, REEFERS_KEY);
            if ( reefers != null && reefers != JsonValue.NULL) {
                System.out.println("OrderActor.init() reefers not null - id:"+getId()+" type:"+this.getType()+" reefers: "+reefers);
                reeferList = reefers.asJsonArray();
                System.out.println("OrderActor.init() - Order Id:"+getId()+" cached reefer list size:"+reeferList.size());
            } else {
                System.out.println("OrderActor.init() reefers  null id:"+getId()+" type:"+this.getType());
            }
            state = Kar.actorGetState(this, STATE_KEY);
//            state = Kar.actorGetState(this, ORDER_MAP_KEY, STATE_KEY);
            System.out.println("OrderActor.init() - Order Id:"+getId()+" cached state:"+state+" type:"+this.getType());
        } catch( Exception e ) {
            e.printStackTrace();
        }
 
    }
    private void unreserveReefer(int reeferId) {
        ActorRef reeferActor =  Kar.actorRef(ReeferAppConfig.ReeferActorName,String.valueOf(reeferId));
        JsonObject params = Json.createObjectBuilder().build();
        actorCall( reeferActor, "unreserve", params);
    }
    @Remote
    public JsonObject delivered(JsonObject message) {
        JsonValue voyageId = Kar.actorGetState(this, "voyageId");  //get(this, "voyageId");
        System.out.println(voyageId+" >>>>>>>>>>>>>>>>>>>>>>>>>>> orderActor.delivered() called- Actor ID:" +getId()+" type:"+this.getType());
        try {
            state = Json.createValue(OrderStatus.DELIVERED.name());
            Kar.actorSetState(this,STATE_KEY, state);
            if ( reeferList == null ) {
                JsonValue reefers = Kar.actorGetState(this,  REEFERS_KEY); //get(this, REEFERS_KEY);
                reeferList = reefers.asJsonArray();
            }
            System.out.println(voyageId+" >>>>>>>>>>>>>>>>>>>>>>>>>>> OrderActor.delivered() - unreserving reefers:"+reeferList);
            reeferList.forEach(reefer -> {
                unreserveReefer( ((JsonNumber)reefer).intValue());
             });
            System.out.println(voyageId+"OrderActor.delivered() - Order Id:"+getId()+" cached reefer list size:"+reeferList.size());
            
            return Json.createObjectBuilder().add("status", "OK").add("orderId", String.valueOf(this.getId())).build();
        } catch( Exception e ) {
            e.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","VOYAGE_ID_MISSING").add("orderId", String.valueOf(this.getId())).build();
        }
    }
    @Remote
    public JsonObject departed(JsonObject message) {
        JsonValue voyage = get(this,"voyageId");
        System.out.println("OrderActor.departed() called- Actor ID:" +getId()+" type:"+this.getType()+" voyage:"+voyage);
        try {
            state = Json.createValue(OrderStatus.INTRANSIT.name());
            //set(this,STATE_KEY, state);
            Kar.actorSetState(this, STATE_KEY, state);
            //JsonValue orderState = Json.createValue(OrderStatus..ordinal());
            //set(this,STATE_KEY, orderState);
            if ( reeferList != null ) {

                ActorRef reeferProvisionerActor =  Kar.actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId);
                JsonObject params = Json.createObjectBuilder().add("in-transit",reeferList.size()).build();
                actorCall( reeferProvisionerActor, "updateInTransit", params);
 
                System.out.println(voyage+" OrderActor.departed() - Order Id:"+getId()+" in-transit reefer list size:"+reeferList.size());
            }
            return Json.createObjectBuilder().add("status", "OK").add("orderId", String.valueOf(this.getId())).build();
        } catch( Exception e ) {
            e.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR",e.getMessage()).add("orderId", String.valueOf(this.getId())).build();
        }
    }
    @Remote
    public JsonObject anomaly(JsonObject message) {
    //    JsonValue voyage = get(this,"voyageId");
        state = Kar.actorGetState(this, STATE_KEY);
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!OrderActor.anomaly() called- Actor ID:" +getId()+" type:"+this.getType()+" state:"+((JsonString)state).getString());
        return Json.createObjectBuilder().add(Constants.ORDER_STATUS_KEY, ((JsonString)state).getString()).add("orderId", String.valueOf(this.getId())).build();

    }
    @Remote
    /*
       Replace spoilt reefer if the order has not departed yet. 
    */
    public JsonObject replaceReefer(JsonObject message) {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!OrderActor.replaceReefer() called- Actor ID:" +getId()+" state:"+((JsonString)state).getString());
        if ( reeferList == null ) {
            JsonValue reefers = Kar.actorGetState(this,  REEFERS_KEY); 
            reeferList = reefers.asJsonArray();
        }
        int spoiltReeferId = message.getJsonNumber(Constants.REEFER_ID_KEY).intValue();
        int replacementReeferId = message.getJsonNumber(Constants.REEFER_REPLACEMENT_ID_KEY).intValue();
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!OrderActor.replaceReefer() called- Actor ID:" +getId()+" replacing spoilt reefer:"+spoiltReeferId+" with new one:"+replacementReeferId);
        JsonArrayBuilder newReeferList = Json.createArrayBuilder();
        reeferList.forEach(reefer -> {
            if ( ((JsonNumber)reefer).intValue() == spoiltReeferId ) {
                newReeferList.add(replacementReeferId);
            } else {
                newReeferList.add(reefer);
            }
           
         });
         JsonArray orderReefers = newReeferList.build();
         Kar.actorSetState(this,REEFERS_KEY, orderReefers);
         System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!OrderActor.replaceReefer() called- Actor ID:" +getId()+" saved new Reefer List:"+orderReefers.toString());
        return Json.createObjectBuilder().build();

    }
    @Remote
    public JsonObject createOrder(JsonObject message) {
        System.out.println(
            "OrderActor.createOrder() called- Actor ID:" + this.getId()+" type:"+this.getType()+" message:"+message);//.getJsonObject(JsonOrder.OrderKey));
        JsonOrder order = new JsonOrder(message.getJsonObject(JsonOrder.OrderKey));
        state = Json.createValue(OrderStatus.PENDING.name());
       // set(this,STATE_KEY, state);

       
        Kar.actorSetState(this, STATE_KEY, state);
        try {
            // voyageId is mandatory
            if ( order.containsKey(JsonOrder.VoyageIdKey) ) {
                String voyageId = order.getVoyageId();
                //set(this,"voyageId", Json.createValue(voyageId));
                Kar.actorSetState(this,"voyageId", Json.createValue(voyageId));
                JsonObject reply = bookVoyage(voyageId, order);
                System.out.println("OrderActor.createOrder() - Order Booked -Reply:"+reply);
                if ( reply.getString("status").equals("OK")) {

                    JsonArray reefers = reply.getJsonArray("reefers");
                    System.out.println("OrderActor.createOrder() - Order Booked - Reefers:"+reefers.size());
                    if ( reefers != null && reefers != JsonValue.NULL ) {
                      // set(this,"reefers", reefers);
                       Kar.actorSetState(this,"reefers", reefers);
                       System.out.println("OrderActor.createOrder() saved order "+getId()+" reefer list - size "+reefers.size());
                    }
                    state = Json.createValue(OrderStatus.BOOKED.name());
                    //set(this,STATE_KEY, state);
                    Kar.actorSetState(this, STATE_KEY, state);
                    
                    System.out.println("OrderActor.createOrder() saved order "+getId()+" state:"+ ((JsonString)state).getString()); //get(this,STATE_KEY));
                    return Json.createObjectBuilder().add(JsonOrder.OrderBookingKey, reply).build();
                } else {
                    return reply;
                }
             } else {
                System.out.println(
                    "OrderActor.createOrder() Failed - Missing voyageId");
                return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","VOYAGE_ID_MISSING").add("orderId", String.valueOf(this.getId())).build();

            }
         
        } catch( Exception e) {
            e.printStackTrace();
            return Json.createObjectBuilder().add("status", "FAILED").add("ERROR","Exception").add("orderId", String.valueOf(this.getId())).build();

        }

 
    }

    private JsonObject bookVoyage(String voyageId, JsonOrder order) {
        try {
            JsonObject params = 
                Json.createObjectBuilder().add(JsonOrder.OrderKey, order.getAsObject()).build();
            ActorRef voyageActor = actorRef(ReeferAppConfig.VoyageActorName, voyageId);
            JsonValue reply = actorCall(voyageActor, "reserve", params);
            return reply.asJsonObject();
        } catch (ActorMethodNotFoundException ee) {
            ee.printStackTrace();
            return Json.createObjectBuilder().add("status", OrderStatus.FAILED.name()).add("ERROR","INVALID_CALL").add(JsonOrder.IdKey, order.getId()).build();
        }
    }
}