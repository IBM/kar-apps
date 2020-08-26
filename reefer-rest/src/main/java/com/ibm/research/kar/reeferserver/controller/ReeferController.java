package com.ibm.research.kar.reeferserver.controller;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;

import com.ibm.research.kar.reefer.model.Port;
import com.ibm.research.kar.reefer.model.Reefer;
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
	@GetMapping("/reefers/inventory/size")
	public int getReeferInventorySize() {
		System.out.println("getReeferInventorySize() - Got New Request");
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