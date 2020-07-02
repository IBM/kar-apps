package com.ibm.research.reefer.simulator;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonValue;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Path("/simulator")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SimulatorResource {

	SimulatorService simService = new SimulatorService();

	/**
	 * Sets the number of seconds between advancing a unit of time
	 * Setting to 0 stops auto advance
	 */
	@POST
	@Path("/setunitperiod")
	public JsonValue setunitperiod(JsonValue num) {
		if (((JsonNumber)num).intValue() < 0) {
			num = Json.createValue(0);
		}
		try {
			return simService.setUnitPeriod(num);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from setunitperiod");
			return Json.createValue(-1);
		}
	}

	/**
	 *
	 */
	@GET
	@Path("/getunitperiod")
	public JsonValue getunitperiod() {
		try {
			return simService.getUnitPeriod();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from getunitperiod");
			return Json.createValue(-1);
		}
	}

}
