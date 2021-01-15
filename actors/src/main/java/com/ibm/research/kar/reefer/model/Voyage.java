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

public class Voyage {
    
    private String id;
    private Route route;

    private Instant sailDateObject;
    private String sailDate;
    private String arrivalDate;
    private String displayArrivalDate;
    private int orderCount=0;
    
    public Voyage(Route route, Instant sailDateObject, String arrivalDate) {
        this.route = route;
        this.sailDateObject = sailDateObject;
        this.arrivalDate = arrivalDate;
        this.displayArrivalDate = arrivalDate.substring(0,10);
        this.sailDate = sailDateObject.toString().substring(0,10);
        this.id = String.format("%s-%s",route.getVessel().getName(),this.sailDateObject.toString()).replaceAll("/","-");
    }
    public String getId() {
        return id;
    }
    public Route getRoute() {
        return route;
    }
    public String getSailDate() {
        return sailDate;
    }
    public String getArrivalDate() {
        return arrivalDate;
    }
    public Instant getSailDateObject() {
        return sailDateObject;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(int orderCount) {
        this.orderCount = orderCount;
    }

    public String getDisplayArrivalDate() {
        return displayArrivalDate;
    }

    
 
    
}