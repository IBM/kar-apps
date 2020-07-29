# Reefer-app  simulator-server

To launch simular-server:  
kar -app_port 7080 -app reefer -service simservice -actors simhelper mvn liberty:run

As of now a running simulator server supports hot method replace.  
**Best to stop auto mode before activating**  
Simulator threads left running in auto from before replace are killed by advancetime or start/stop auto mode.

Useful simulator commands below. Monitor service console for status.

To advance time in manual mode...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/advancetime  
... from another reeferapp service:  
Kar.restPost("simservice", "simulator/advancetime", JsonValue.NULL);

To enable/disable simulator connection to reefer-rest server from command line,  
can be done anytime, with the thread in auto or manual mode:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/togglereeferrest

To put shipthread in auto mode with N second delay between updates...  
... from command line:  
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/setunitdelay -d N  
Note:
   The above command only works for values of N greater than one digit due to a liberty bug.
   To work around this for all values of N, use:
curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/setunitdelay -d '{"value":N}'  

... to change the unit delay from another reeferapp service:  
Kar.restPost("simservice", "simulator/setunitdelay", Json.createObjectBuilder().add("value", N).build());

To put shipthread into manual mode, POST N=0 to simulator/setunitdelay.

To see all persistent simulator configuration data from command line:  
kar -app reeferapp -invoke simhelper simservice getAll


