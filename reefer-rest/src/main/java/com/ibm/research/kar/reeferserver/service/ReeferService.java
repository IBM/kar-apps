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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import static com.ibm.research.kar.Kar.*;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.model.Reefer;
import com.ibm.research.kar.reefer.model.ReeferSimControls;
import com.ibm.research.kar.reefer.model.ReeferStats;

import org.springframework.stereotype.Component;

@Component
public class ReeferService {
	private Map<String, List<Reefer>> portReeferMap = new HashMap<>();

	public void addPortReefers(String port, int howMany) {
		for (int i = 0; i < howMany; i++) {
			Reefer reefer = new Reefer(port, 1000, 1000, "Empty", "OnDock", "");
			List<Reefer> reefers;
			if ((reefers = portReeferMap.get(port)) == null) {
				reefers = new ArrayList<>();
				portReeferMap.put(port, reefers);
			}
			reefers.add(reefer);

			System.out.println("Added new reefer ID:" + reefer.getReeferId());
		}

	}

	public List<Reefer> getReefers() {
		List<Reefer> reefers = new ArrayList<>();
		for (Entry<String, List<Reefer>> r : portReeferMap.entrySet()) {
			reefers.addAll(r.getValue());
		}
		return reefers;
	}

	public ReeferStats getReeferStats() {
		JsonObject message = Json.createObjectBuilder().build();
		JsonValue reply = Kar.Actors.call(Kar.Actors.ref(ReeferAppConfig.ReeferProvisionerActorName, ReeferAppConfig.ReeferProvisionerId), "getStats",
				message);
		JsonObject stats = reply.asJsonObject();

		return new ReeferStats(stats.getInt("total"), stats.getInt("totalInTransit"), stats.getInt("totalBooked"),
				stats.getInt("totalSpoilt"), stats.getInt("totalOnMaintenance"));
	}

	public List<Reefer> getReefers(String port) {
		List<Reefer> reefers;
		if (portReeferMap.containsKey(port)) {
			reefers = portReeferMap.get(port);
		} else {
			reefers = new ArrayList<>();
		}

		return reefers;
	}
}