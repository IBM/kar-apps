package com.ibm.research.kar.reeferserver.controller;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.ibm.research.kar.reefer.model.Port;
import com.ibm.research.kar.reefer.model.Reefer;
import com.ibm.research.kar.reefer.model.ReeferSimControls;
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
	private int inventorySize=0;
	@Autowired
    private ReeferService reeferService;
    @Autowired
    private PortService portService;
	@Autowired
	private GuiController gui;
    @Autowired
    private ScheduleService shipScheduleService;


	@PostConstruct
    public void init() {
		List<Route> routes = shipScheduleService.getRoutes();
		Set<String> fleet = new LinkedHashSet<>();

		long fleetMaxCapacity=0;
		for (Route route: routes) {
			fleetMaxCapacity += route.getVessel().getMaxCapacity();
			fleet.add(route.getVessel().getName());
		}
		inventorySize = Double.valueOf(fleet.size()*fleetMaxCapacity*1.2).intValue();
		System.out.println("Inventory Size:::::::"+inventorySize);
    }
    @PostMapping("/reefers")
	public   List<Port>  addReefers(@RequestBody ReeferSupply reeferAdd) throws IOException {
		System.out.println("addReefers() Called - port:"+reeferAdd.getPort()+" howMany:"+reeferAdd.getReeferInventoryCount());
		
        reeferService.addPortReefers(reeferAdd.getPort(), reeferAdd.getReeferInventoryCount());
        portService.incrementReefersAtPort(reeferAdd.getPort(), reeferAdd.getReeferInventoryCount());
		return portService.getPorts();
	}

	@GetMapping("/reefers")
	public List<Reefer>  getAllReefers() {
		System.out.println("getAllReefers() - Got New Request");
		
		return reeferService.getReefers();
	}

	
	@GetMapping("/reefers/stats")
	public ReeferStats getReeferStats() {
		System.out.println("getReeferStats() - Got New Request");
		
		return reeferService.getReeferStats();
	}
	@PostMapping("/reefers/stats/update") 
	public void updateGui(@RequestBody String stats ) {
		System.out.println("ReeferController.updateGui() ");
		try (JsonReader jsonReader = Json.createReader(new StringReader(stats))) {
            
			JsonObject req = jsonReader.readObject();

			int total = req.getInt("total");
			int totalBooked = req.getInt("totalBooked");
			int totalInTransit = req.getInt("totalInTransit");
			int totalSpoilt = req.getInt("totalSpoilt");
			int totalOnMaintenance = req.getInt("totalOnMaintenance");
			System.out.println("ReeferController.updateGui() - ################# total:"+total+" booked:"+totalBooked+" intransit:"+totalInTransit+" spoilt:"+totalSpoilt+"  onmaintenance:"+totalOnMaintenance);
			gui.updateReeferStats(new ReeferStats(total, totalInTransit, totalBooked, totalSpoilt, totalOnMaintenance));
		} catch( Exception e) {
			e.printStackTrace();
		}

	}
	@GetMapping("/reefers/inventory/size")
	public int getReeferInventorySize() {
		return inventorySize;
	}
	@GetMapping("/reefers/{port}")
	public List<Reefer>  getReefers(@RequestParam("port") String port) {
		System.out.println("getReefers() - Got New Request Port:"+port);
		
		return reeferService.getReefers(port);
    }

	private void updateReefers() {
		if ( reeferService != null ) {
			List<Reefer> reefers = reeferService.getReefers();
			for( Reefer reefer : reefers ) {
				if ( reefer.getStatus().equals("Empty")) {
					reefer.setStatus("PartiallyFull");
				} else if ( reefer.getStatus().equals("PartiallyFull")){
					reefer.setStatus("Full");
				} else if ( reefer.getStatus().equals("Full")){
					reefer.setStatus("OnShip");
				} else if ( reefer.getStatus().equals("OnShip")){
					reefer.setStatus("Empty");
				}
				
			}
			//gui.sendReefersUpdate(reefers);
		}
		
	}
}