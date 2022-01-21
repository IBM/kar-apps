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

The application models simplified business processes involved in shipping perishable goods on a ship from origin port to destination port. A broker places an order for a shipment of goods which requires one or more refrigerated (reefer) containers to be on a specific voyage. Land transportation, container loading/unloading, as well as Customs clearance are not considered. To facilitate the transit of goods, the application uses a fleet of ships covering Atlantic and Pacific oceans. Each ship has a different tonnage (reefer cargo capacity) and is assigned to a shipping schedule serving a route between two ports. The itinerary includes departure dates from origin port and arrival dates at the destination port. All reefers have identical physical dimensions and have a maximum holding capacity in terms of product units.

The Reefer application runtime consists of four separately deployable components.  

1. UI server running in OpenLiberty.
This server hosts an HTTP service to deliver the Angular based Reefer application packaged in multiple javascript bundles.

2. SpringBoot REST server running in OpenLiberty.
This server hosts a stateless WebAPI service that processes HTTP requests from clients and pushes GUI state changes to any connected angular front end. Its clients are browsers, KAR actors, and Reefer simulators. The REST server can be replicated for fault tolerance and throughput.

3. Actor server running in OpenLiberty.
This server hosts actor services that implement the Reefer application business logic. For development all actor instances can run in a single actor service. For production actor types can be segregated into different actor services and replicated.

4. Simulator server running in OpenLiberty.
This server hosts a single service that implements all simulator drivers.

## Application Architecture
![Alt text](docs/images/reefer-arch.png?raw=true "Reefer Architecture")

### GUI
The Reefer Application GUI is implemented with Angular 9 using SPA (Single Page Application) design where all 
HTML, Javascript, and CSS code is fetched by the browser in a single page load to
improve user experience. The application offers four distinct views 

1. Create Order View 
- Manually create and submit an order
2. Orders View
- Shows number of future (booked), in-transit, and spoiled orders. Configures and starts/stops the Order Simulator 
3. Reefers View 
- Shows number of booked, in-transit, spoiled, and on-maintenance reefers. Configures and starts/stops the Reefer Anomaly Simulator
4. Active Voyage View
- Shows active voyages with progress updated in real time. Configures and starts/stops the Ship Simulator

### REST Service
This Spring Framework service provides an HTTP based, RESTful API to external application clients, internal actor components and simulators.
Simulators call REST to advance time, fetch current reefer inventory, fetch ship schedule and to get a list of active voyages. 
REST receives updates from KAR actors and pushes these changes in real time to the GUI using Websockets to
move ships and change reefer and order counts and totals.
Each voyage travels between origin and destination ports in a fixed number of days, remaining at each port for two days to unload/reload reefers.
The voyage schedules are dynamically extended when necessary to allow the simulator to run for unlimited time.  

### KAR Actors
KAR actors implement the business logic of the Reefer Application. 

#### Order Actor
Represents an order entity in the application. In response to a new order request, REST creates a new orderId and calls the createOrder method of the order actor with that Id. This action implicitly creates the new actor instance.
createOrder calls the specified *Voyage Actor* to reserve space for its products and reserve the required containers.
If successful, the order state becomes *booked* until the ship departs a port at which time the state changes
to *in-transit*.
Order actors are notified if an anomaly is detected for one of its reserved reefers. If before departure the reefer is replaced, else it and the order are marked spoilt.
On arrival at destination the actor removes itself from active actors.

#### Voyage Actor
Represents a voyage entity in the application. A voyage includes a ship, a departure date, and a route between origin and destination ports. 
Its state also includes Ids of *Order Actors* that have booked space and their reefers.
The *Ship Simulator* calls this actor at some point each "day" with the new date.
If the date matches departure date, the voyage actor notifies the departure depot of all containers that are no longer there, and notifies all on-board *Order Actors* of their departure.
If the date matches arrival date, the voyage actor first notifies the arrival depot of all containers that have been added, notifies all its *Order Actors* of arrival, and finally removes itself from active actors.

#### Depot Actor
Manages reefer inventory for a specific port, allocating reefers to new orders, removing reefers from inventory on voyage departures and adding reefers to inventory when a voyage arrives. 
If products in an order exceed capacity of a single reefer container, multiple containers are allocated to the order. 
This actor also maintains statistics on the number of reefers booked, in-transit, spoilt and on maintenance which it 
sends to the Depot Manager when there are changes.
When notified that a reefer has gone bad, the provisioner simply tags empty reefers as *On Maintenance* where it remains unavailable for booking for two days, or if booked it calls the associated Order actor with a replacement reefer.
When arrives, any of its reefers marked spoilt are tagged as on maintenance.

#### Anomaly Router Actor
Maintains current location for all reefer containers: in a specific depot or in-transit on a specific voyage. Routes new anomaly events to the actor that represents the current location.

#### Order Manager Actor
Maintains global state for all active orders: booked or in-transit. Makes state change available to REST to be pushed to connected GUIs.

#### Voyage Manager Actor
Maintains global state for all active voyages: those with booked orders or in-transit. Makes state change available to REST to be pushed to connected GUIs.

#### Depot Manager Actor
Maintains global state for all reefer containers. Makes state change available to REST to be pushed to connected GUIs.


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
- podman version 3.4.2+ is required
- when building docker images using podman, make sure BUILDAH_FORMAT=docker has been previously exported to the environment

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

## Deploying Reefer using kind, k3d or docker desktop from latest reefer release
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
Start the application using docker-compose, podman, k3d, docker desktop or kind following the directions above but
**without overriding the IMAGE_PREFIX**

### Native development

Additional prereqs:  
- maven version 3.6.2+  
- JDK 11+
- nodejs
- ulimit for nofiles at least 16384 for the Rest, Actors and Simulators windows below

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
kar run -app reefer -v info -actors order,voyage,reefer-depot,depot-manager,anomaly-manager,order-manager,schedule-manager -actor_collector_interval 10m mvn liberty:run
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
