package com.ibm.research.kar.reefer.common.json;

import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Ship;

import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.Json;

import java.io.StringReader;
import java.time.Instant;
import com.ibm.research.kar.reefer.common.Constants;

public class JsonUtils {
    /**
     * Extract voyageId from json
     * 
     * @param message - json encoded params
     * 
     * @return voyageId
     */
    public static String getVoyageId(String message) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            JsonObject req = jsonReader.readObject();
            return req.getString(Constants.VOYAGE_ID_KEY);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static int getDaysAtSea(String message) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            JsonObject req = jsonReader.readObject();
            return req.getInt(Constants.VOYAGE_DAYSATSEA_KEY);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String getString(JsonValue value, String key) {
        return value.asJsonObject().getString(key);
    }

    public static Voyage jsonToVoyage(JsonObject jsonVoyage) {
        Instant sailDate = Instant.parse(jsonVoyage.getString("sailDateObject"));
        String arrivalDate = jsonVoyage.getString("arrivalDate");
        Route route = jsonToRoute(jsonVoyage.getJsonObject("route"));
        Voyage voyage = new Voyage(route, sailDate, arrivalDate);
        return voyage;
    }

    public static Route jsonToRoute(JsonObject jsonRoute) {
        Ship ship = jsonToShip(jsonRoute.getJsonObject("vessel"));
        String originPort = jsonRoute.getString("originPort");
        String destinationPort = jsonRoute.getString("destinationPort");
        int daysAtSea = jsonRoute.getInt("daysAtSea");
        int daysAtPort = jsonRoute.getInt("daysAtPort");
        return new Route(ship, originPort, destinationPort, daysAtSea, daysAtPort);
    }

    public static Ship jsonToShip(JsonObject jsonShip) {
        String shipId = jsonShip.getString("id");
        String name = jsonShip.getString("name");
        long position = jsonShip.getInt("position");
        int progress = jsonShip.getInt("progress");
        int maxCapacity = jsonShip.getInt("maxCapacity");
        int freeCapacity = jsonShip.getInt("freeCapacity");
        String location = jsonShip.getString("location");
        Ship ship = new Ship(name, position, maxCapacity, freeCapacity, location);
        ship.setId(shipId);
        return ship;
    }
}
