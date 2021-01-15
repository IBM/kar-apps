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

public class OrderStats {
    int inTransitOrderCount;
    int futureOrderCount;
    int spoiltOrderCount;
    public OrderStats(int inTransitOrderCount, int futureOrderCount, int spoiltOrderCount) {
        this.inTransitOrderCount = inTransitOrderCount;
        this.futureOrderCount = futureOrderCount;
        this.spoiltOrderCount = spoiltOrderCount;
    }

    public int getInTransitOrderCount() {
        return inTransitOrderCount;
    }

    public void setInTransitOrderCount(int inTransitOrderCount) {
        this.inTransitOrderCount = inTransitOrderCount;
    }

    public int getFutureOrderCount() {
        return futureOrderCount;
    }

    public void setFutureOrderCount(int futureOrderCount) {
        this.futureOrderCount = futureOrderCount;
    }

    public int getSpoiltOrderCount() {
        return spoiltOrderCount;
    }

    public void setSpoiltOrderCount(int spoiltOrderCount) {
        this.spoiltOrderCount = spoiltOrderCount;
    }

    
    
}