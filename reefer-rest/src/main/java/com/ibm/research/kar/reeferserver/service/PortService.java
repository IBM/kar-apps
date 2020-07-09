package com.ibm.research.kar.reeferserver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.research.kar.reeferserver.model.Port;

import org.springframework.stereotype.Component;
@Component
public class PortService {
    private Map<String, Port> ports = new HashMap<>();

	{

		ports.put("Elizabeth, NJ",new Port("Elizabeth, NJ", ":", 0, 0));
		ports.put("Oakland, CA",new Port("Oakland, CA", ":", 0,0));
		ports.put("Miami, FL",new Port("Miami, FL", ":", 0, 0));
		ports.put("Boston, MA",new Port("Boston, MA", ":", 0, 0));
		ports.put("London, GB",new Port("London, GB", ":", 0,0));
		ports.put("Shanghai, CN",new Port("Shanghai, CN", ":", 0, 0));
		ports.put("Antwerp, BE",new Port("Antwerp, BE", ":", 0, 0));
		ports.put("Bremen, DE",new Port("Bremen, DE", ":", 0,0));
		

    } 
    
    public List<Port> getPorts() {
        return  new ArrayList<Port>(ports.values());
    }

    public void incrementReefersAtPort(String port, int howMany) {
        ports.get(port).adjustReeferCount( howMany );
    }
}