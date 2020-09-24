package com.ibm.research.kar.reeferserver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import static com.ibm.research.kar.Kar.*;
import com.ibm.research.kar.reefer.ReeferAppConfig;
import com.ibm.research.kar.reefer.model.Reefer;
import com.ibm.research.kar.reefer.model.ReeferSimControls;
import com.ibm.research.kar.reefer.model.ReeferStats;

import org.springframework.stereotype.Component;
@Component
public class ReeferService {
    private Map<String, List<Reefer>> portReeferMap = new HashMap<>();

    {
		/*
        addPortReefers("Elizabeth, NJ", 10);
		addPortReefers("Oakland, CA", 10);
		addPortReefers("Miami, FL", 10);
		addPortReefers("Boston, MA", 10);
		addPortReefers("London, GB", 10);
		addPortReefers("Shanghai, CN", 10);
		addPortReefers("Antwerp, BE", 10);
		addPortReefers("Bremen, DE", 100);
		addPortReefers("Hamburg, DE", 10);
		addPortReefers("New Orleans, LA", 10);
		addPortReefers("New York, NY", 10);
		*/
	}
	
	public void addPortReefers(String port, int howMany) {
		for( int i=0; i < howMany; i++ ) {
			Reefer reefer = new Reefer(port, 1000,1000,"Empty","OnDock","");
			List<Reefer> reefers;
			if ( (reefers = portReeferMap.get(port) ) == null ) {
				reefers = new ArrayList<>();
				portReeferMap.put(port, reefers);
			}
			reefers.add(reefer);
			
			System.out.println("Added new reefer ID:"+reefer.getReeferId());
		}
		
	}	
	/*
	public void setReeferInventorySize(int inventorySize) {
		JsonObject params = Json.createObjectBuilder()
		.add("inventorySize",  inventorySize)
		.build();
		try {
			// Book reefers for this order thru the ReeferProvisioner
			JsonValue reply = actorCall(  actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId),"inventorySize", params);
			if ( reply.asJsonObject().getString("status").equals("OK") ) {

			}
		} catch( Exception e) {
			e.printStackTrace();
		}

	}	
	*/
    public List<Reefer> getReefers() {
        List<Reefer> reefers = new ArrayList<>();
		for( Entry<String, List<Reefer>> r : portReeferMap.entrySet()) {
			reefers.addAll(r.getValue());
		}
        return reefers;
	}
	public ReeferStats getReeferStats() {
		JsonObject message = Json.createObjectBuilder().build();
		JsonValue reply = actorCall(  actorRef(ReeferAppConfig.ReeferProvisionerActorName,ReeferAppConfig.ReeferProvisionerId),"getStats", message); 
		JsonObject stats = reply.asJsonObject();
		
		return new ReeferStats(stats.getInt("total"),stats.getInt("totalInTransit"),stats.getInt("totalBooked"),stats.getInt("totalSpoilt"),stats.getInt("totalOnMaintenance"));
	}
	
    public List<Reefer> getReefers(String port) {
        List<Reefer> reefers;
        if ( portReeferMap.containsKey(port)) {
            reefers = portReeferMap.get(port);
        } else {
            reefers = new ArrayList<>();
        }
		
        return reefers;
    }
}