package com.ibm.research.kar.reeferserver.controller;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.ibm.research.kar.reefer.model.*;

import com.ibm.research.kar.reeferserver.model.ReeferSupply;
import com.ibm.research.kar.reeferserver.service.PortService;
import com.ibm.research.kar.reeferserver.service.ReeferService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class ReeferController extends TimerTask {
    @Autowired
    private ReeferService reeferService;
    @Autowired
    private PortService portService;
	@Autowired
	private NotificationController webSocket;
	private int count=1;

	public ReeferController() {
	//	Timer timer = new Timer();
	//	timer.scheduleAtFixedRate(this, new Date(), 5000);
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
		
		return reeferService.getReefers(); //new ArrayList<Reefer>(new ArrayList<Reefer>());
	}
	@GetMapping("/reefers/{port}")
	public List<Reefer>  getReefers(@RequestParam("port") String port) {
		System.out.println("getReefers() - Got New Request Port:"+port);
		
		return reeferService.getReefers(port);
    }
	public void run(){
		//toy implementation
		
		//updateReefers();
		//System.out.println("after change age is "+age);
  
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
			count++;
			webSocket.sendReefersUpdate(reefers);
//			System.out.println("Websocket :: updating reefers");
		}
		
	}
}