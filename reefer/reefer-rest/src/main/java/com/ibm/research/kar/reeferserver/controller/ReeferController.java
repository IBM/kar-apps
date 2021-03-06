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

package com.ibm.research.kar.reeferserver.controller;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.ibm.research.kar.reefer.model.Port;
import com.ibm.research.kar.reefer.model.Reefer;
import com.ibm.research.kar.reefer.model.ReeferStats;
import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reeferserver.model.ReeferSupply;
import com.ibm.research.kar.reeferserver.service.PortService;
import com.ibm.research.kar.reeferserver.service.ReeferService;
import com.ibm.research.kar.reeferserver.service.ScheduleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class ReeferController {
	private int reeferInventorySize = 0;
	@Autowired
	private ReeferService reeferService;
	@Autowired
	private PortService portService;
	@Autowired
	private GuiController gui;
	@Autowired
	private ScheduleService shipScheduleService;
	private static final Logger logger = Logger.getLogger(ReeferController.class.getName());
	@PostConstruct
	public void init() {
		List<Route> routes = shipScheduleService.getRoutes();
		Set<String> fleet = new LinkedHashSet<>();
		// using each ship capacity compute the total fleet reefer inventory size
		long fleetMaxCapacity = 0;
		for (Route route : routes) {
			fleetMaxCapacity += route.getVessel().getMaxCapacity();
			fleet.add(route.getVessel().getName());
		}
		if ( logger.isLoggable(Level.INFO)) {
			logger.info("RestController.init() - Fleet Size:"+fleet.size()+" Max Fleet Capacity:"+fleetMaxCapacity);
		}
		// increase total by additional 40% to ensure we always have reefers available
		reeferInventorySize = Double.valueOf(fleet.size() * fleetMaxCapacity * 0.4).intValue();
	}

	@PostMapping("/reefers")
	public List<Port> addReefers(@RequestBody ReeferSupply reeferAdd) throws IOException {
		reeferService.addPortReefers(reeferAdd.getPort(), reeferAdd.getReeferInventoryCount());
		portService.incrementReefersAtPort(reeferAdd.getPort(), reeferAdd.getReeferInventoryCount());
		return portService.getPorts();
	}

	@GetMapping("/reefers")
	public List<Reefer> getAllReefers() {
		return reeferService.getReefers();
	}

	@GetMapping("/reefers/stats")
	public ReeferStats getReeferStats() {
		return reeferService.getReeferStats();
	}

	@PostMapping("/reefers/stats/update")
	public void updateGui(@RequestBody String stats) {
		try (JsonReader jsonReader = Json.createReader(new StringReader(stats))) {
			JsonObject req = jsonReader.readObject();
			int total = req.getInt("total");
			int totalBooked = req.getInt("totalBooked");
			int totalInTransit = req.getInt("totalInTransit");
			int totalSpoilt = req.getInt("totalSpoilt");
			int totalOnMaintenance = req.getInt("totalOnMaintenance");
			gui.updateReeferStats(new ReeferStats(total, totalInTransit, totalBooked, totalSpoilt, totalOnMaintenance));
		} catch (Exception e) {
			logger.log(Level.WARNING,"",e);
		}
	}

	@GetMapping("/reefers/inventory/size")
	public int getReeferInventorySize() {
		return reeferInventorySize;
	}

	@GetMapping("/reefers/{port}")
	public List<Reefer> getReefers(@RequestParam("port") String port) {
		return reeferService.getReefers(port);
	}

}