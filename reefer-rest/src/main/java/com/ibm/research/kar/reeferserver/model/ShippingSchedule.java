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

package com.ibm.research.kar.reeferserver.model;
import java.io.Serializable;
import java.util.List;

//import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;

public class ShippingSchedule implements Serializable {
    private static final long serialVersionUID = 1366354424038297589L;
    private List<Voyage> voyages;
    private String currentDate;

    public ShippingSchedule(List<Voyage> voyages, String currentDate) {
        super();
        this.voyages = voyages;
        this.currentDate = currentDate;
    }

    public List<Voyage> getVoyages() {
        return voyages;
    }

    public void setVoyages(List<Voyage> voyages) {
        this.voyages = voyages;
    }

    public String getCurrentDate() {
        return currentDate;
    }

    public void setCurrentDate(String currentDate) {
        this.currentDate = currentDate;
    }

 }