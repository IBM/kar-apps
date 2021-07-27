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

# Reefer Application Overview

The application models simplified business processes involved in shipping perishable goods on a ship from a manufacturer to a client. A broker places an order for a shipment of goods which requires one or more refrigerated (reefer) containers and a ship. The land transportation of reefer containers as well as Customs clearance are not considered to simplify the design. To facilitate the transit of goods, the application uses a fleet of ships covering Atlantic and Pacific oceans. Each ship has a different tonnage (reefer cargo capacity) and is assigned to a shipping schedule serving a route between two ports. The itinerary includes departures date from origin port and arrival dates at the destination port. All reefers have identical physical dimensions and have a maximum holding capacity in terms of product units.

The Reefer application runtime consists of four separately deployable components.  

1. UI server running in OpenLiberty.
Its main responsibility is to serve the Angular based Reefer application packaged in multiple javascript bundles.

2. SpringBoot REST server running in OpenLiberty.
Its main responsibility is to process HTTP requests and return results to clients. Its clients are browsers, KAR actors, and Reefer simulators. This server also maintains the state for the application (in memory for now) and also generates and manages a shipping schedule.

3. Actor server running in OpenLiberty.
Its main responsibility is to implement business logic for Reefer App use cases.

4. Simulator server running in OpenLiberty.
Its main responsibility is to provide drivers for Reefer App use cases. Currently supported use cases are:
   * Advance time and notify active voyages of their new locations
   * Gradually create orders filling upcoming voyages to a specified target capacity
   * Randomly generate reefer anomalies at specified failure rate

## Application Architecture
![Alt text](docs/images/reefer-arch.png?raw=true "Reefer Architecture")

### GUI

The Reefer Application GUI is implemented with Angular 9 using SPA (Single Page Application) design where all 
HTML, Javascript, and CSS code is fetched by the browser in a single page load to
improve user experience. The application offers four distinct views 

1. Order Create View 
- Manually create and submit an order

2. Orders View
- Shows number of future (booked), in-transit, and spoiled orders. Configures and starts/stops the Order Simulator 

3. Active Voyage View
- Shows active voyages with progress updated in real time. Configures and starts/stops the Ship Simulator

4. Reefers View 
- Shows number of booked, in-transit, spoiled, and on-maintenance reefers. Configures and starts/stops the Reefer Anomaly Simulator

### REST Service
This Spring Framework service provides an HTTP based, RESTful API to external application clients, internal actor components and simulators.
Simulators call to advance time, fetch current reefer inventory, fetch ship schedule and to get a list of active voyages. 
REST receives updates from KAR actors and pushes these changes in real time to the GUI using Websockets to
move ships and change reefer and order counts and totals.
REST is also responsible for creating the voyage schedules based on REST start time and an initial fleet definition of ships and their routes defined statically in routes.json.
Each voyage travels between origin and destination ports in a fixed number of days, remaining at each port for two days to unload/reload reefers.
The voyage schedules are dynamically extended when necessary to allow the simulator to run for unlimited time.  

### KAR Actors
KAR actors implement the business logic of the Reefer Application. 

#### Order Actor
Represents an order entity in the application. In response to a new order request, REST creates a new orderId and calls the createOrder method of the order actor with that Id. This action implicitly creates the new actor instance.
createOrder calls the specified *Voyage Actor* to reserve space for its products and reserve the required containers.
If successful, the order state becomes *booked* until the ship departs a port at which time the state changes
to *in-transit*.
Order actors are notified if an anomaly is detected for one of its reserved reefers.
If the anomaly is received before departure the order actor requests a replacement reefer from the provisioner.
If after departure the order actor changes state to *spoilt* and informs the provisioner.
On arrival at port the actor removes itself from active actors.

#### Voyage Actor
Represents a voyage entity in the application. A voyage includes a ship, a departure date, and a route between origin and destination ports. 
Its state also includes Ids of *Order Actors* that have booked space and their reefers.
The *Ship Simulator* calls this actor at some point each "day" with the new date.
If the date matches departure date, the voyage actor notifies all on-board *Order Actors* of their departure.
If the date matches arrival date, the voyage actor first notifies all its *Order Actors* of arrival, then notifies the *Reefer Provisioner Actor* of the list of reefers to return to inventory, and finally removes itself from active actors.
 
#### Reefer Provisioner Actor
Represents a *singleton* reefer manager entity in the application. It manages reefer inventory, allocating reefers to new orders and releasing reefers back to inventory when an order is delivered. 
If products in an order exceed capacity of a single reefer container, multiple containers are allocated to the order. 
This actor also maintains statistics on the number of reefers booked, in-transit, spoilt and on maintenance which it 
sends to the REST service at regular intervals when there are outstanding changes.
When notified that a reefer has gone bad, the provisioner either passes the anomaly to the order that owns it or simply tags the reefer as *On Maintenance* where it remains unavailable for booking for 
two days after which it becomes available again. 
When notified that a voyage has arrived, any of its reefers marked spoilt are tagged as on maintenance.

### Simulators

Three simulators are included to exercise the Reefer Application:  

#### Ship Simulator
The Ship Simulator advances time and moves ships from an origin port
to a destination port. To start the Ship Simulator, navigate to the Active Voyages View and 
change the value in a field directly under the **Simulated Delay** label. This value represents
the desired time compression (in secs). For example, the value of 10 means that 
each day is 10 secs long, 20 makes a day 20 secs, etc. A value of 0 disables automatically advancing time.
When the simulator starts the simulated delay is set to 0. 
The operational value is modified by changing the value and hitting the **Update** button.
If the operational value is 0, the **Click to Advance Time** button can be used to manually request an advance.
Requests done while still processing a previous advance are ignored.  

#### Order Simulator  
The order simulator creates orders for upcoming voyages. Its operational parameters are:  
   * **Order Target** - the percent of a voyage capacity to be filled when it departs.  
   * **Future Time Window** - the number of days before departure that orders are generated for each voyage.  
   * **Order Updates per day** - the requested number of daily orders to generate for each voyage.  

After choosing the values, press the **Update** button to make them operational.
An order target of 0 disabled automatic order generation when the ship simulator is active.
When the order generation is disabled, the manual **Create Orders** button can be used.  

#### Reefer Anomaly Simulator  
The reefer simulator generate anomalies randomly across the entire reefer inventory. Its operational parameters are:  
   * **Failure Target** - 100x the percent of inventory to pick each day. For example, 4 means that anomalies will be generated for approximately 0.04% of the Total Reefer count each day.  
   * **Anomaly Updates per day** - the number of times anomalies will be generated each day.  

After choosing the values, press the **Update** button to make them operational.
An order target of 0 disabled automatic anomaly generation when the ship simulator is active.
When the anomaly generation is disabled, the manual **Create Anomaly** button can be used to generate one anomaly.  

# Reefer Deployment and Development

## Prereqs for running or developing Reefer
- clone `kar` from https://github.com/IBM/kar.git
- browse the README and follow the getting-started guide
- clone `kar-apps` from https://github.com/IBM/kar-apps.git

## Deploying Reefer using docker-compose from the latest reefer release
- start the KAR runtime using 
  `[kar-install-dir]/scripts/docker-compose-start.sh`
- start the reefer application using `IMAGE_PREFIX=quay.io/ibm [kar-apps-install-dir]/reefer/scripts/reefer-compose-start.sh`
- when the application is ready, point browser at the URL listed for the reefer GUI
- stop the reefer application using `[kar-apps-install-dir]/reefer/scripts/reefer-compose-stop.sh`

## Deploying Reefer using rootless podman from the latest reefer release
- if KAR was previously launched using docker-compose, stop KAR by running `[kar-install-dir]/scripts/docker-compose-stop.sh`
- start the kar runtime and reefer application using `IMAGE_PREFIX=quay.io/ibm [kar-apps-install-dir]/reefer/scripts/reefer-play-start.sh`
- when the application is ready, point browser at the URL listed for the reefer GUI
- stop KAR and the reefer application using `[kar-apps-install-dir]/reefer/scripts/reefer-play-stop.sh`

## Deploying Reefer using kind, k3s or docker desktop from latest reefer release
- See [KAR Deployment Options](https://github.com/IBM/kar/blob/main/docs/kar-deployments.md) for instructions on installing these
- See [Reefer launch on Kubernetes](chart/README.md) for instructions

## Reefer Development

Development can be done with native compilation or docker images.

### Docker development

Dev images are served via a local image repository. Start one using:
  `[kar-install-dir]/scripts/start-local-registry.sh`

Build the reefer dev images and push them into a local registry:
```
cd [kar-apps-install-dir]/reefer
make reeferImages
make pushReeferImages
```
Start the application using docker-compose, podman, k3s, docker desktop or kind following the directions above but
**without overriding the IMAGE_PREFIX**

### Native development

Additional prereqs:  
- maven version 3.6.2+  
- JDK 11+
- nodejs

Build java application:  
```
cd [kar-apps-install-dir]/reefer
mvn clean install
```
Build actors singleton Docker image:
```
KAR_IMAGE_PREFIX=localhost:5000/kar/ make reeferImages
```

Start the KAR runtime: `[kar-install-dir]/scripts/docker-compose-start.sh`

Open four terminal windows, one for each reefer service:
- Rest window
```
cd [kar-apps-install-dir]/reefer/reefer-rest
source [kar-install-dir]/scripts/kar-env-local.sh
kar run -app_port 9080 -app reefer -v info -service reeferservice mvn liberty:run
```

- Actors window
```
cd [kar-apps-install-dir]/reefer/actors
source [kar-install-dir]/scripts/kar-env-local.sh
kar run -app reefer -v info -actors order,voyage mvn liberty:run
```
- Singleton Actors window
```
cd [kar-apps-install-dir]/reefer
source [kar-install-dir]/scripts/kar-env-local.sh
docker-compose -f scripts/reefer-actors.yaml -p reefer up
```
- Simulators window
```
cd [kar-apps-install-dir]/reefer/simulators
source [kar-install-dir]/scripts/kar-env-local.sh
kar run -app_port 7080 -app reefer -v info -service simservice -actors simhelper mvn liberty:run
```

- Frontend window
```
cd [kar-apps-install-dir]/reefer/frontend
mvn liberty:run
```

- When all services have completed initialization, access the Reefer Web Application from your browser at:
`http://localhost:9088/`
