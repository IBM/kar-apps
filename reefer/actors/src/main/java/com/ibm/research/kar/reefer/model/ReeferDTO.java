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

import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.ReeferState;
import com.ibm.research.kar.reefer.common.ReeferState.State;
import com.ibm.research.kar.reefer.common.time.TimeUtils;

public class ReeferDTO {

    private int id;
    private State state;
    private String orderId;
    private String voyageId;
    private String maintenanceReleaseDate;

    public ReeferDTO(int id, State state) {
        this(id, state, "","");
    }
    public ReeferDTO(int id, State state, String orderId, String voyageId) {
        this.id = id;
        setState(state);
        this.orderId = orderId;
        this.voyageId = voyageId;
    }
    public int getId() {
        return id;
    }

    public void reset() {
        setState(State.UNALLOCATED);
        this.orderId = "";
        this.voyageId = "";
        this.maintenanceReleaseDate = "";
    }
    public void removeFromVoyage() {
        this.orderId = "";
        this.voyageId = "";
    }
    public State getState() {
        return state;
    }
    public void setState(State state) {
        this.state = state;
    
    }

    public String getOrderId() {
        return orderId;
    }
    public boolean alreadyBad() {
        return getState().equals(ReeferState.State.MAINTENANCE) || getState().equals(ReeferState.State.SPOILT);
    }
    public boolean assignedToOrder() {
        return getOrderId() != null && getOrderId().trim().length() > 0;
    }
    public String getVoyageId() {
        return voyageId;
    }

    public String getMaintenanceReleaseDate() {
        return maintenanceReleaseDate;
    }

    public void setMaintenanceReleaseDate(String today) {
        if ( today == null ) {
            maintenanceReleaseDate = null;
        } else {
	        // on maintenance reefers are not associated with a voyage or order
            this.voyageId = "";
	        this.orderId = "";
            maintenanceReleaseDate = TimeUtils.getInstance().futureDate( Instant.parse(today), Constants.REEFER_DAYS_ON_MAINTENANCE).toString();
        }
        
    }





}
