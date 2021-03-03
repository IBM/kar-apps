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

package com.ibm.research.kar.reefer.model;

import java.time.Instant;

import javax.json.JsonArray;
import javax.json.JsonObject;

public class JsonOrder {
    public static final String OrderKey = "order";
    public static final String OrderBookingKey = "booking";
    public static final String IdKey = "orderId";
    public static final String VoyageIdKey = "orderVoyageId";
    public static final String ProductQtyKey = "orderProductQty";
    public static final String OriginPortKey = "originPort";
    public static final String DestinationPortKey = "destinationPort";
    private JsonObject order;

    public JsonOrder(JsonObject order) {
        this.order = order;
    }

    public String getId() {
        return order.getString(IdKey);
    }

    public boolean containsKey(String key) {
        return order.containsKey(key);
    }

    public String getVoyageId() {
        return order.getString(VoyageIdKey);
    }

    public String getOriginPort() {
        return order.getString(OriginPortKey);
    }

    public String getDestinationPort() {
        return order.getString(DestinationPortKey);
    }

    public int getProductQty() {
        return order.getInt(ProductQtyKey);
    }

    public JsonObject getAsObject() {
        return order;
    }
}

