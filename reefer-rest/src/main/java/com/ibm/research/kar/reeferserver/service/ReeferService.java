package com.ibm.research.kar.reeferserver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.research.kar.reefer.model.Reefer;

import org.springframework.stereotype.Component;
@Component
public class ReeferService {
    private Map<String, List<Reefer>> portReeferMap = new HashMap<>();

    {
        addPortReefers("Elizabeth, NJ", 20);
		addPortReefers("Oakland, CA", 10);
		addPortReefers("Miami, FL", 15);
		addPortReefers("Boston, MA", 10);
		addPortReefers("London, GB", 11);
		addPortReefers("Shanghai, CN", 13);
		addPortReefers("Antwerp, BE", 13);
		addPortReefers("Bremen, DE", 20);
    }

    public void addPortReefers(String port, int howMany) {
		for( int i=0; i < howMany; i++ ) {
			Reefer reefer = new Reefer(port, 1000,1000,"Empty","OnDock","3.140:5.8944");
			List<Reefer> reefers;
			if ( (reefers = portReeferMap.get(port) ) == null ) {
				reefers = new ArrayList<Reefer>();
				portReeferMap.put(port, reefers);
			}
			reefers.add(reefer);
			
			System.out.println("Added new reefer ID:"+reefer.getReeferId());
		}
		
    }
    public List<Reefer> getReefers() {
        List<Reefer> reefers = new ArrayList<Reefer>();
		for( Entry<String, List<Reefer>> r : portReeferMap.entrySet()) {
			reefers.addAll(r.getValue());
		}
        return reefers;
    }
    public List<Reefer> getReefers(String port) {
        List<Reefer> reefers;
        if ( portReeferMap.containsKey(port)) {
            reefers = portReeferMap.get(port);
        } else {
            reefers = new ArrayList<Reefer>();
        }
		
        return reefers;
    }
}