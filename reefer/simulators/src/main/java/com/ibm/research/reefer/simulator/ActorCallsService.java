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

package com.ibm.research.reefer.simulator;

import javax.json.JsonValue;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.annotations.Activate;
import com.ibm.research.kar.actor.annotations.Actor;
import com.ibm.research.kar.actor.annotations.Deactivate;
import com.ibm.research.kar.actor.annotations.Remote;


/**
 * A simple test that an actor can call a service
 */
@Actor
public class ActorCallsService extends BaseActor {

	@Activate
	public void initState() {
	}

	@Deactivate
	public void saveState() {
	}

	@Remote
	public JsonValue setUnitPeriod(JsonValue num) {
		return (JsonValue)Kar.Services.call("simservice", "simulator/setunitperiod", num);
	}

//TODO enable this routine when kar-java supports GET
//	@Remote
//	public JsonValue getUnitPeriod() {
//		return (JsonValue)Kar.call("simservice", "simulator/getunitperiod");
//	}

}
