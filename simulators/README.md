# Reefer-app  simulator-server  

To launch simular-server:  
kar run -app_port 7080 -app reefer -service simservice -actors simhelper mvn liberty:run  


The simulator state on server start is:  
 - time AND order updates in manual modes (unitdelay=0)  
 - connection to reefer-rest enabled (true)  

Useful simulator commands below.  

To enable/disable simulator connection to reefer-rest server from command line, done anytime:  
kar rest -app reefer post simservice simulator/togglereeferrest  

To advance time in manual-time mode...  
... from command line:  
kar rest -app reefer post simservice simulator/advancetime  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/advancetime", JsonValue.NULL);  

To put shipthread in auto-time mode with N second delay between updates, or back into manual-time mode with N=0, ...  
... from command line:  
kar rest -app reefer post simservice simulator/setunitdelay '{"value":N}'  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/setunitdelay", Json.createObjectBuilder().add("value", N).build());  

To create orders for needy voyages when in manual-time or manual-order modes ...  
... from command line:  
kar rest -app reefer post simservice simulator/createorder  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/createorder", JsonValue.NULL);  

To put orderthread in auto-order mode with ordertarget percent = T, or back into manual-order mode with T=0, ...  
... from command line:  
kar rest -app reefer post simservice simulator/setordercontrols '{"ordertarget":T}'  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/setordercontrols", Json.createObjectBuilder().add("ordertarget", T).build());  
The setordercontrols API can also be used to set orderwindow and orderupdates values. 
More than one order control can be set on one call.  

To create one (1) reefer anomaly ...  
... from command line:  
kar rest -app reefer post simservice simulator/createanomaly  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/createanomaly", JsonValue.NULL);  

To put reeferthread in auto mode with failuretarget percent = 0.0T, or back into manual-order mode with T=0, ...  
... from command line:  
kar rest -app reefer post simservice simulator/setreefercontrols -d '{"failuretarget":T}'  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/setreefercontrols", Json.createObjectBuilder().add("failuretarget", T).build());  
The setreefercontrols API can also be used to set reeferupdates. 
More than one reefer control can be set on one call.  

Notes for simulator developers:
1. Depending on order control settings, more or less orders need to be created each day for a given fleet schedule.
Depending on reefer performance, and the simulator "advance delay", it may not be possible to generate all required orders
within a simulated day.
Monitoring of missed orders, failed orders, and statistics on latency for successful orders are captured in the simulator.
A Kar client is provided that periodically dumps order stats: monitor-stats.sh.
The stats can be reset at any time with:  
kar rest -app reefer post simservice simulator/resetorderstats   
2. As of now a running simulator server supports hot method replace.  
**Best to stop auto mode before activating by updating simulator class files with service running**  
Simulator threads left running in auto from before replace are killed by advancetime or start/stop auto mode.
