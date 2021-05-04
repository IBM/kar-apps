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

package com.ibm.research.kar.reeferserver.service;

import java.util.*;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.model.Fleet;
import com.ibm.research.kar.reefer.model.Ship;

import org.springframework.stereotype.Component;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonValue;

@Component
public class FleetService {
    private List<Fleet> fleets = new ArrayList<>();
    private int fleetSize=10;  //default

    public List<Fleet> getFleets() {
        return fleets;
    }
	private ActorRef aRef = Kar.Actors.ref(ReeferAppConfig.RestActorName, ReeferAppConfig.RestActorId);

    public void save(int fleetSize) {
		Kar.Actors.State.set(aRef, Constants.REEFER_FLEET_SIZE_KEY, Json.createValue(fleetSize));
		System.out.println("FleetService.save() ++++++++++++ saved fleet size:"+fleetSize);
	}
	public Optional<Integer> fleetSize() {

    	JsonValue jv = Kar.Actors.State.get(aRef, Constants.REEFER_FLEET_SIZE_KEY);
    	if ( jv != null && jv != JsonValue.NULL) {
    		return Optional.of(Integer.valueOf(((JsonNumber)jv).intValue()));
		} else {
    		return Optional.empty();
		}

	}
}