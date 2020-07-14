package com.ibm.research.reefer.simulator;

import static com.ibm.research.kar.Kar.actorDeleteState;
import static com.ibm.research.kar.Kar.actorGetAllState;
import static com.ibm.research.kar.Kar.actorSetState;
import static com.ibm.research.kar.Kar.actorGetState;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;


/**
 * An actor that implements a persistent Map
 */
@Actor
public class SimulatorHelper extends BaseActor {

	private static Object toValue(Response response) {
		if (response.hasEntity()) {
			MediaType type = response.getMediaType();
			if (type.equals(MediaType.APPLICATION_JSON_TYPE)) {
				return response.readEntity(JsonValue.class);
			} else if (type.equals(MediaType.TEXT_PLAIN_TYPE)) {
				return response.readEntity(String.class);
			} else {
				return JsonValue.NULL;
			}
		} else {
			return JsonValue.NULL;
		}
	}

	@Activate
	public void initState() {
	}

	@Deactivate
	public void saveState() {
	}

	@Remote
	public JsonValue test4get(JsonValue service,JsonValue path) {
		System.out.println("test4get: Kar.restGet "+ ((JsonString)service).getString()+", "+((JsonString)path).getString());
		Response response = Kar.restGet(((JsonString)service).getString(), ((JsonString)path).getString());
		return (JsonValue) toValue(response);
	}

	@Remote
	public JsonValue test4post(JsonValue service, JsonValue path, JsonValue args) {
		System.out.println("test4post: Kar.restPost "+ ((JsonString)service).getString()+", "+((JsonString)path).getString()+", "+ args.toString());
		Response response = Kar.restPost(((JsonString)service).getString(), ((JsonString)path).getString(), args);
		return (JsonValue) toValue(response);
	}

	@Remote
	public JsonValue test4call(JsonValue service,JsonValue path,JsonValue arg) {
		System.out.println("test4call: Kar.call "+ ((JsonString)service).getString()+", "+((JsonString)path).getString());
		return (JsonValue) Kar.call(((JsonString)service).getString(), ((JsonString)path).getString(), arg);
	}

	@Remote
	public JsonValue test4jc(JsonValue service,JsonValue path) {
		System.out.println("test4jc calls "+ ((JsonString)service).getString()+", "+((JsonString)path).getString());
		return (JsonValue) Kar.call( ((JsonString)service).getString(), ((JsonString)path).getString(), Json.createValue(0) );
	}

	@Remote
	public JsonValue get(JsonValue key) {
		JsonValue value = actorGetState(this,((JsonString)key).getString());
		return value;
	}

	@Remote
	public JsonValue set(JsonValue key, JsonValue value) {
		int n = actorSetState(this,((JsonString)key).getString(), value);
		return Json.createValue(n);
	}

	@Remote
	public JsonValue del(JsonValue key) {
		int n = actorDeleteState(this, ((JsonString)key).getString());
		return Json.createValue(n);
	}

	@Remote
	public JsonValue getAll() {
		Map<String,JsonValue> tempMap = new HashMap<String,JsonValue>();
		tempMap.putAll(actorGetAllState(this));
		
		JsonObjectBuilder builder = Json.createObjectBuilder();
		tempMap.forEach(builder::add);
		return builder.build();
	}
}
