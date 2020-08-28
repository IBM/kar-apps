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


To create orders for needy voyages when in manual-time or manual-order modes ...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/createorder  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/createorder", JsonValue.NULL);


To put orderthread in auto-order mode with ordertarget percent = T, or back into manual-order mode with T=0, ...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/setordercontrols -d '{"ordertarget":T}'  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/setordercontrols", Json.createObjectBuilder().add("ordertarget", T).build());
The setordercontrols API can also be used to set orderwindow and orderupdates values. More than one order controls can be set at the same time.

To create one (1) reefer anomaly ...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/createanomaly  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/createanomaly", JsonValue.NULL);


To put reeferthread in auto mode with failuretarget percent = 0.0T, or back into manual-order mode with T=0, ...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/setreefercontrols -d '{"failuretarget":T}'  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/setreefercontrols", Json.createObjectBuilder().add("failuretarget", T).build());  
The setreefercontrols API can also be used to set reeferupdates. More than one reefer controls can be set at the same time.  


Note for simulator developers:
As of now a running simulator server supports hot method replace.  
**Best to stop auto mode before activating by updating simulator class files with service running**  
Simulator threads left running in auto from before replace are killed by advancetime or start/stop auto mode.
