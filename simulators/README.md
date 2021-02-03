<!--
# Copyright IBM Corporation 2020,2021
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
-->

# Reefer-app  simulator-server  

To launch simular-server:  
kar run -app_port 7080 -app reefer -v info -service simservice -actors simhelper mvn liberty:run  


The simulator state on server start is:  
 - time AND order updates in manual modes (unitdelay=0)  
 - connection to reefer-rest enabled (true)  

Useful simulator commands below.  

Given access to the simulator service port on ${host}, curl can be used for access. For example,
to disable/enable simulator access to reefer-rest server from the command line:  
curl -s -H "Content-Type: application/json" -X POST http://${host}:7080/simulator/togglereeferrest  

A simpler approach is to use the Kar CLI:  
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
The monitor offers three configuration settings:  
  delay - the delay in seconds between reports  
  reset - the number of reports to show before automatically resetting stats
  threshold - the latency in ms above which orders are counted as outliers  

The stats can be reset and threshold changed at any time with:  
kar rest -app reefer post simservice simulator/resetorderstats threshold  
  
2. If the reefer app is deployed using reefer-compose-start.sh, Kar's environment is not exposed outside the pod.
For this scenario the monitor is automatically run inside the pod; dump the monitor's container log to see output.
Here the stats can be reset by exec'ing into the simulator container and issuing the command:  
/kar/bin/kar rest -app reefer post simservice simulator/resetorderstats   
  
3. As of now a running simulator server supports hot method replace.  
**Best to stop auto mode before activating by updating simulator class files with service running**  
Simulator threads left running in auto from before replace are killed by advancetime or start/stop auto mode.
