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
package com.ibm.research.kar.reefer.common;

import com.ibm.research.kar.reefer.model.Order;

import javax.json.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;

public class FixedSizeQueue extends ArrayBlockingQueue<Order> {
    private int size;

    public FixedSizeQueue(int capacity) {
        super(capacity);
        this.size = capacity;
    }

    // Drops the oldest element when full
    @Override
    synchronized public boolean add(Order e) {
        if (super.size() == this.size) {
            // removes the oldest element from the queue
            this.remove();
        }
        return super.add(e);
    }
    public JsonArray getAll() {
        JsonArrayBuilder jab = Json.createArrayBuilder();
        super.stream().
                map(this::convert).
                peek(o -> jab.add(o)).
                collect(Collectors.toList());

        return jab.build();
    }
    public JsonObject convert(Order o) {
        JsonObjectBuilder jab = Json.createObjectBuilder();
        return jab.add(Constants.ORDER_ID_KEY, o.getId()).
                add(Constants.VOYAGE_ID_KEY, o.getVoyageId()).
                add(Constants.ORDER_STATUS_KEY, o.getStatus()).
                add(Constants.ORDER_SPOILT_KEY, o.isSpoilt()).
                add(Constants.ORDER_PRODUCT_KEY, o.getProduct()).
                add(Constants.ORDER_PRODUCT_QTY_KEY, o.getProductQty()).
                add(Constants.ORDER_CUSTOMER_ID_KEY, o.getCustomerId()).
                add(Constants.ORDER_DATE_KEY, o.getDate()).build();
    }
}