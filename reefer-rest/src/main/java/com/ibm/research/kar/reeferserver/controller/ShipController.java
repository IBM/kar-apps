package com.ibm.research.kar.reeferserver.controller;

import java.util.List;

import com.ibm.research.kar.reefer.model.Fleet;
import com.ibm.research.kar.reeferserver.service.FleetService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class ShipController {
	@Autowired
	private FleetService fleetService;

	@GetMapping("/fleets")
	public List<Fleet> getAllFleets() {
		return fleetService.getFleets();
	}
}