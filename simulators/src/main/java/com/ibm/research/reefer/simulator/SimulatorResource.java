package com.ibm.research.reefer.simulator;


import javax.json.Json;
import javax.json.JsonValue;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton; 


@Singleton @Path("/simulator")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SimulatorResource {

	private static SimulatorService simService;
	public SimulatorResource() {
		if (null == simService) {
			simService = new SimulatorService();
		}
	}

@PreDestroy
public void reset() {
	System.out.println("predestroy");
	simService = null;
}


    /**
	 * Simulator cold start goes into manual mode.
	 *           warm start resumes last operational state.
	 * Transition from manual to auto will start associated Time, Order or Reefer thread running.
	 * Transition to manual will kill the associated thread.
	 * Transition to auto will initialize unset parameters to their default values.
	 * Delay = 0 means manual mode
	 */
	@POST
	@Path("/setunitdelay")
	public JsonValue setunitdelay(JsonValue num) {
		try {
			return simService.setUnitDelay(num);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from setunitdelay");
			return Json.createValue(-1);
		}
	}

	/**
	 * Gets the current setting for Unit Period
	 */
	@GET
	@Path("/getunitdelay")
	public JsonValue getunitdelay() {
		try {
			return simService.getUnitDelay();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from getunitdelay");
			return Json.createValue(-1);
		}
	}

	/**
	 * Gets the current setting for Unit Period
	 */
	@GET
	@Path("/advancetime")
	public JsonValue advancetime() {
		try {
			return simService.advanceTime();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from advancetime");
			return Json.createValue(-1);
		}
	}

	@POST
	@Path("/gimme")
	public JsonValue gimme(JsonValue val) {
		System.out.println("val type="+val.getValueType()+"  val.tostring="+val.toString());
		return val;
	}

}
