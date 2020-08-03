# Reefer-app  simulator-server

To launch simular-server:  
kar -app_port 7080 -app reefer -service simservice -actors simhelper mvn liberty:run


The simulator state on server start is:
 - time AND order updates in manual modes (unitdelay=0)
 - order updates in manual-order mode if ordertarget==0 (ordertarget value is persistent)
 - connection to reefer-rest enabled (true)


Useful simulator commands below. Monitor service console for status.


To enable/disable simulator connection to reefer-rest server from command line, done anytime: 
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/togglereeferrest


To advance time in manual-time mode...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/advancetime  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/advancetime", JsonValue.NULL);


To put shipthread in auto-time mode with N second delay between updates, or back into manual-time mode with N=0, ...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/setunitdelay -d '{"value":N}'  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/setunitdelay", Json.createObjectBuilder().add("value", N).build());


To create one order for needy voyages when in manual-time or manual-order modes ...
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/createorder  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/createorder", JsonValue.NULL);


To put orderthread in auto-order mode with ordertarget percent = T, or back into manual-order mode with T=0, ...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/setordertarget -d '{"value":T}'  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/setordertarget", Json.createObjectBuilder().add("value", T).build());


Note for simulator developers:
As of now a running simulator server supports hot method replace.  
**Best to stop auto mode before activating by updating simulator class files with service running**  
Simulator threads left running in auto from before replace are killed by advancetime or start/stop auto mode.
