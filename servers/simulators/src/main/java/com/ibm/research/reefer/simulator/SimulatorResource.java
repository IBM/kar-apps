package com.ibm.research.reefer.simulator;

import javax.json.Json;
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
	 * Always starts Time, Order and Reefer updates in manual mode.
	 * Has an entry point to resume last operational state.
	 * Transition from manual to auto will start Time, Order or Reefer threads running.
	 * Transition to manual will kill the associated thread.
	 * Transition to auto will initialize unset parameters to their default values.
	 * Period = 0 means manual mode
	 */
	@POST
	@Path("/setunitperiod")
	public JsonValue setunitperiod(JsonValue num) {
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
	 * Gets the current setting for Unit Period
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
