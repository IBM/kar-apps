/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.kar.reefer.common.json;

import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Ship;

import javax.json.*;

import java.io.StringReader;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    public static List<String> getVoyageOrders(String message ) throws Exception {
        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            JsonObject req = jsonReader.readObject();
            JsonArray ja = req.getJsonArray(Constants.VOYAGE_ORDERS_KEY);
            return ja.stream().map(jv -> ((JsonString)jv).getString()).collect(Collectors.toList());
        } catch (Exception e) {
            throw e;
        }

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
    public static Order deserializeOrder(String message) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(message))) {
            return new Order(jsonReader.readObject());
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException("Unable to deserialize Order from a given json");
    }

    public static String getString(JsonValue value, String key) {
        return value.asJsonObject().getString(key);

    }
}
