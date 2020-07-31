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

	private static SimulatorService simService = new SimulatorService();

    /**
	 * Simulator cold start goes into manual mode.
	 *           warm start resumes last operational state.
	 * Transition from manual to auto will start associated Time, Order or Reefer thread running.
	 * Transition to manual will kill the associated thread when it is sleeping.
	 * Transition to auto will initialize any required but unset parameters to their default values.
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
	 * One-shot advance of time
	 */
	@POST
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
	@Path("/setordertarget")
	public JsonValue setordertarget(JsonValue num) {
		try {
			return simService.setOrderTarget(num);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from setordertarget");
			return Json.createValue(-1);
		}
	}

	/**
	 * Gets the current setting for Unit Period
	 */
	@GET
	@Path("/getordertarget")
	public JsonValue getordertarget() {
		try {
			return simService.getOrderTarget();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from getordertarget");
			return Json.createValue(-1);
		}
	}

	/**
	 * One-shot advance of time
	 */
	@POST
	@Path("/createorder")
	public JsonValue createorder() {
		try {
			return simService.createOrder();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from advancetime");
			return Json.createValue(-1);
		}
	}

	/**
	 * Update voyage capacity
	 */
	@POST
	@Path("/updatevoyagecapacity")
	public void updatevoyagecapacity(JsonValue capacity) {
		try {
			simService.updateVoyageCapacity(capacity);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("help! from updatevoyagecapacity");
		}
	}

	/**
	 * Toggle connection with reefer-rest server
	 */
	@POST
	@Path("/togglereeferrest")
	public JsonValue togglereeferrest() {
		return simService.toggleReeferRest(); 
	}

	/**
	 * Toggle connection with reefer-rest server
	 */
	@POST
	@Path("/newdayfororders")
	public void newdayfororders() {
		simService.newDayForOrders(); 
	}


// Temporary for testing
	@POST
	@Path("/gimmepost")
	public JsonValue gimmepost(JsonValue val) {
		System.out.println("gimmepost: "+val.toString());
		return val;
	}

	@GET
	@Path("/gimmeget")
	public JsonValue gimmeget() {
		System.out.println("gimmeget called");
		return Json.createValue("GOT");
	}

}
