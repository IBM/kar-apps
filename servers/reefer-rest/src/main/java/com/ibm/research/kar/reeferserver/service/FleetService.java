package com.ibm.research.kar.reeferserver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.research.kar.reeferserver.model.Fleet;
import com.ibm.research.kar.reeferserver.model.Ship;

import org.springframework.stereotype.Component;
@Component
public class FleetService {
    private List<Fleet> fleets = new ArrayList<>();
    private Map<String, Ship> atlanticFleet = new HashMap<>();
    private Map<String, Ship> pacificFleet = new HashMap<>();
 	
	{
		atlanticFleet.put("Abyss", new Ship("Abyss",":",5660, 2000, "AtPort"));
		pacificFleet.put("Atlantis", new Ship("Atlantis",":",2000, 500, "AtPort"));
		atlanticFleet.put("Black Pearl", new Ship("Black Pearl",":",1000, 233, "AtPort"));
		atlanticFleet.put("Santa Maria", new Ship("Santa Maria",":",1444, 430, "AtPort"));
		atlanticFleet.put("Andrea Gail", new Ship("Andrea Gail",":",1000, 300, "AtPort"));
		atlanticFleet.put("Victoria", new Ship("Victoria",":",1566, 1000, "AtPort"));
		atlanticFleet.put("Trinidad", new Ship("Trinidad",":",989, 120, "AtPort"));
		 
		fleets.add(new Fleet("Atlantic",  new ArrayList<Ship>(atlanticFleet.values())));
		fleets.add(new Fleet("Pacific",  new ArrayList<Ship>(pacificFleet.values())));
    }
    
    public List<Fleet> getFleets() {
        return fleets;
    }
}