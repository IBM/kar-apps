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
   * API interface endpoints
   */

//-------------------- Ship thread controls ----------------

  @POST
  @Path("/setunitdelay")
  public JsonValue setunitdelay(JsonValue num) {
    try {
      return simService.setUnitDelay(num);
    } catch (Exception e) {
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
    } catch (Exception e) {
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
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from advancetime");
      return Json.createValue(-1);
    }
  }

// -------------------- Order controls ----------------

  /**
   * gets & sets order simulator control parameters
   */
  @GET
  @Path("/getordercontrols")
  public JsonValue getordercontrols() {
    try {
      return simService.getOrderControls();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from getordercontrols");
      return Json.createValue(-1);
    }
  }

  @POST
  @Path("/setordercontrols")
  public JsonValue setordercontrols(JsonValue controls) {
    try {
      return simService.setOrderControls(controls);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from setordercontrols");
      return Json.createValue(-1);
    }
  }

  /**
   * gets & sets the number of days from now that future voyages can get orders
   */
  @GET
  @Path("/getorderwindow")
  public JsonValue getorderwindow() {
    try {
      return simService.getOrderWindow();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from getorderwindow");
      return Json.createValue(-1);
    }
  }

  @POST
  @Path("/setorderwindow")
  public JsonValue setorderwindow(JsonValue num) {
    try {
      return simService.setOrderWindow(num);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from setorderwindow");
      return Json.createValue(-1);
    }
  }

  /**
   * gets/sets the number of times per day that orders can be generated
   */
  @GET
  @Path("/getorderupdates")
  public JsonValue getorderupdates() {
    try {
      return simService.getOrderUpdates();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from getorderupdates");
      return Json.createValue(-1);
    }
  }

  @POST
  @Path("/setorderupdates")
  public JsonValue setorderupdates(JsonValue num) {
    try {
      return simService.setOrderUpdates(num);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from setorderupdates");
      return Json.createValue(-1);
    }
  }

  /**
   * gets/sets the capacity utilization % target for order generation
   */
  @GET
  @Path("/getordertarget")
  public JsonValue getordertarget() {
    try {
      return simService.getOrderTarget();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from getordertarget");
      return Json.createValue(-1);
    }
  }

  @POST
  @Path("/setordertarget")
  public JsonValue setordertarget(JsonValue num) {
    try {
      return simService.setOrderTarget(num);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from setordertarget");
      return Json.createValue(-1);
    }
  }

  /**
   * One-shot creation of orders for future voyages
   */
  @POST
  @Path("/createorder")
  public JsonValue createorder() {
    try {
      return simService.createOrder();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from createorder");
      return Json.createValue(-1);
    }
  }

  /**
   * Feedback from order actors with updated voyage capacity
   */
  @POST
  @Path("/updatevoyagecapacity")
  public void updatevoyagecapacity(JsonValue capacity) {
    try {
      simService.updateVoyageCapacity(capacity);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from updatevoyagecapacity");
    }
  }

//-------------------- Misc controls ----------------

  /**
   * For development, toggle connection with reefer-rest server
   */
  @POST
  @Path("/togglereeferrest")
  public JsonValue togglereeferrest() {
    return simService.toggleReeferRest();
  }

  /**
   * Called by the shipthread to wake up order and reefer threads
   */
  @POST
  @Path("/newday")
  public void newday() {
    simService.newDay();
  }

  @GET
  @Path("/getorderstats")
  public JsonValue getorderstats() {
    try {
      return simService.getOrderStats();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from getorderstats");
      return Json.createValue(-1);
    }
  }

  @POST
  @Path("/resetorderstats")
  public JsonValue resetorderstats(JsonValue num) {
    try {
      simService.resetOrderStats(num);
      return Json.createValue("accepted");
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from resetorderstats");
      return Json.createValue("rejected");
    }
  }


//-------------------- Reefer controls ----------------

  @GET
  @Path("/getreefercontrols")
  public JsonValue getreefercontrols() {
    try {
      return simService.getReeferControls();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from getreefercontrols");
      return Json.createValue(-1);
    }
  }

  @POST
  @Path("/setreefercontrols")
  public JsonValue setreefercontrols(JsonValue controls) {
    try {
      return simService.setReeferControls(controls);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from setreefercontrols");
      return Json.createValue(-1);
    }
  }

  /**
   * gets/sets the target (x0.01) percent of reefers getting anomalies per day
   */
  @GET
  @Path("/getfailuretarget")
  public JsonValue getfailuretarget() {
    try {
      return simService.getFailureTarget();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from getfailertarget");
      return Json.createValue(-1);
    }
  }

  @POST
  @Path("/setfailuretarget")
  public JsonValue setfailuretarget(JsonValue num) {
    try {
      return simService.setFailureTarget(num);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from setfailertarget");
      return Json.createValue(-1);
    }
  }

  /**
   * gets/sets the max number of times a day anomalies may be generated
   */
  @GET
  @Path("/getreeferupdates")
  public JsonValue getreeferupdates() {
    try {
      return simService.getReeferUpdates();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from getreeferupdates");
      return Json.createValue(-1);
    }
  }

  @POST
  @Path("/setreeferupdates")
  public JsonValue setreeferupdates(JsonValue num) {
    try {
      return simService.setReeferUpdates(num);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from setreeferupdates");
      return Json.createValue(-1);
    }
  }

  /**
   * One-shot creation of a reefer anomaly
   */
  @POST
  @Path("/createanomaly")
  public JsonValue createanomaly() {
    try {
      return simService.createAnomaly();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("help! from createanomaly");
      return Json.createValue(-1);
    }
  }

}
