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

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
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
                map(Order::getAsJsonObject).
                peek(o -> jab.add(o)).
                collect(Collectors.toList());

        return jab.build();
    }
}