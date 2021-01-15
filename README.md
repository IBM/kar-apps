# Reefer Application

The application models simplified business process involved in shipping perishable goods on a ship from manufacturer to a client. The manufacturer places an order for a shipment of goods which requires one or more refrigerated (reefer) containers and a ship. The land transportation of reefer containers as well as Customs clearance are not considered to simplify the design. To facilitate transit of goods, the application uses a fleet of ships covering Atlantic and Pacific oceans. Each ship in a fleet has a different tonnage (reefer cargo capacity) and is assigned to a shipping schedule serving a route between two ports. The itinerary includes departure date from origin port and arrival date at the destination port. All reefers have identical physical dimensions and have a maximum holding capacity in terms of product units.

The Reefer application runtime consists of four separately deployable components.  

1. UI server running in OpenLiberty
Its main responsibility is to serve Angular based Reefer application packaged in multiple javascript bundles.

2. SpringBoot REST server running in OpenLiberty
Its main responsibility is to process HTTP requests and return results to clients. Its clients are browsers, KAR actors, and Reefer simulators. This server also maintains the state for the application (in memory for now) and also generates and manages a shipping schedule.

3. Actor server running in OpenLiberty
Its main responsibility is to implement business logic for Reefer App use cases.

4. Simulator server running in OpenLiberty
Its main responsibility is to provide drivers for Reefer App use cases. Currently supported use cases are:
   * Advance time and notify active voyages of their new locations
   * Gradually create orders filling upcoming voyages to a specified target capacity
   * Randomly generate reefer anomalies at specified failure rate

## Application Architecture
![Alt text](docs/images/reefer-arch.png?raw=true "Reefer Architecture")

### GUI

The Reefer Application GUI is implemented with Angular 9 using SPA (Single Page Application) design where all 
HTML, Javascript, and CSS code is fetched by the browser in a single page load to
improve user experience. The application offers five distinct views 

1. Order Create View 
- Manually create and submit an order

2. Orders View
- Shows number of future (booked), in-transit, and spoiled orders. Configures and starts/stops the Order Simulator 

3. Active Voyage View
- Shows active voyages with progress updated in real time. Configures and starts/stops the Ship Simulator

4. Reefers View 
- Shows number of booked, in-transit, spoiled, and on-maintenance reefers. Configures and starts/stops the Reefer Anomaly Simulator

5. Ships View 
- Shows the fleet of ships
### REST Service
This Spring Framework service provides HTTP based, RESTful API to application clients which include the GUI, KAR actors, 
and simulators. The simulators call it to advance time, fetch current reefer inventory, fetch ship schedule and to get a list of active 
voyages. The REST receives updates from KAR actors and pushes these changes in real time to the GUI using Websockets to
move ships and change reefer and order counts and totals. It is also responsible for initially creating a ship schedule 
for N days ahead and extending the schedule dynamically to make sure the Reefer Application doesn't run out of voyages.
The schedule is computed from a static configuration defined in resources/routes.json which currently includes
routes for seven ships. Each ship in the schedule moves from origin port to destination port in a fixed number of days. 
After reaching its destination, the ship remains at port for one day to unload reefers and then goes back to origin port
with new reefers aboard.

### KAR Actors
KAR actors implement business logic of the Reefer Application. 

#### Order Actor
Represents an order entity in the application. It is created by REST service in response to the GUI or Order Simulator 
request. On new order creation, the actor calls *Voyage Actor* to book reefer containers for its products and a voyage. With
reefers secured, the order state becomes *booked* until the ship departs an origin port in which case the state changes
to *in-transit*. While in-transit, the order may become spoilt when one of its reefers experiences anomaly. If a reefer
anomaly is reported while the order is in the *booked* state, the bad reefer is swapped for a good one. On arrival 
at destination port, this actor sends a message to the *Reefer Provisioner Actor* to release order reefers back
to inventory, calls REST to notify it of order delivery, and finally removes itself from active actors.

#### Voyage Actor
Represents a voyage entity in the application. A voyage includes a ship, and a route from origin to destination ports. 
Its state also includes one or more orders booked by *Order Actors*. The *Ship Simulator* calls this actor as time
advances, to move its ship towards the destination port. The first day of the voyage triggers a message to all *Order Actors* 
which book their orders to notify them of departure. On the last day of the voyage this actor first sends messages to 
*Order Actors* notifying them of arrival, then messages *Reefer Provisioner Actor* to release order reefers back to inventory, and finally
the actor removes itself from active actors.
 
#### Reefer Provisioner Actor
Represents a *singleton* reefer manager entity in the application. It manages reefer inventory, allocating reefers to new orders 
and releasing reefers back to inventory when an order is delivered. If products in an order exceed capacity of a single
reefer container, multiple containers are allocated to the order. This actor also maintains reefer statistics which it 
sends to the REST service at regular intervals. The *Anomaly Simulator*
notifies the *Reefer Provisioner* of reefer anomalies at configured rate after choosing a reefer to spoil at random. If the
reefer has not yet been booked, it is tagged as *On Maintenance* where it remains unavailable for booking for 
two days after which it becomes available again. If the simulator chooses a booked reefer for an anomaly, this actor 
notifies the *Order Actor* which booked the reefer. The *Order Actor* then decides to spoil its order if its already 
in-transit or request a replacement reefer if it's waiting for departure.


### Simulators

The Reefer Application provides three simulators:

#### Ship Simulator
The Ship Simulator which advances time and moves ships from an origin port
to a destination port. To start the Ship Simulator, navigate to the Active Voyages View and 
change the value in a field directly under the **Simulated Delay** label. This value represents
the desired time compression (in secs). For example, the value of 10 means that 
each day is 10 secs long, 20 makes a day 20 secs, etc. The default value is 0, which disables 
the Ship Simulator. The minimum (and recommended) compression factor for the Ship Simulator is 5 secs. 
Once the value is greater than zero, the **Start** button is activated. Press it to start the simulator.
This is a modal button which will toggle **Start** and **Stop**. While the Ship Simulator is running,
the ships begin to appear, and each ship progress is updated in real time at a chosen rate.
#### Order Simulator
With the Ship Simulator running you can start the Order Simulator by navigating to the Orders View. The behavior of
this simulator can be changed using three values which are directly under the **Order Simulator** label. Set the
*Order Target* value to a number greater than zero. This value represents the max percentage the orders will fill each ship. 
For example, the value of 80 means that the ship will be filled with orders up to 80% of its maximum capacity. 
Set the *Future Time Window* value to a number greater than zero. This value represents a moving window of days for which orders
will be generated into the future. Set the *Order Updates per Day* value to a number greater than zero. This value represents
a number of orders the simulator will generate per day.
After choosing the values, press **Update** button to start the Order Simulator. The order counts begin to change as new 
orders are created, ships depart/arrive, and orders becoming spoiled while en-route to destination.
#### Reefer Anomaly Simulator
With the Ship Simulator running you can start the **Reefer Anomaly Simulator** by navigating to the Reefers View.
The behavior of this simulator can be changed using two values which are directly under the **Reefer Anomaly Simulator** 
label. Set the *Failure Rate* value to a number greater than zero. This value represents reefer failure rate as a percentage
of available reefers. Set the *Anomaly Updates per Day* value to a number greater than zero. This value represents number of
reefer failures the simulator will generate per day. Press **Update** button to start the simulator. Watch the *Spoilt* and 
*On Maintenance* counts begin to change. A reefer becomes spoilt only if it is on a ship which has departed (ie. in transit). 
When a spoilt reefer arrives at the destination port, it's state changes to on-maintenance. Spoilt reefers which are 
not in-transit are counted as on-maintenance and remain there for two days after which they become available
for booking again.

## Reefer Deployment

### Prereqs
- maven version 3.6.2+
- access to KAR artifacts built locally
- docker-compose or Kind version 0.8.1+


### Building KAR artifacts
```
# clone KAR repository
clone https://github.ibm.com/solsa/kar.git

# build docker images
cd kar
make dockerCore
```


### Quick Start to run application with docker-compose
```
# clone Reefer application repository
clone https://github.ibm.com/cwiklik/reefer-app.git

# build docker images
cd reefer-app
make reeferImages

# start reefer application
./scripts/reefer-compose-start.sh

# After 30-60 seconds, access the Reefer Web Application from your browser at:
http://localhost:9088/
```


### Quick Start for developers
```
# build KAR java SDK in [KAR install dir]
make installJavaSDK

# deploy KAR dependencies Kafka and Redis by either ...
# ... running them in docker-compose
[KAR install dir]/scripts/docker-compose-start.sh
# ... or running them in kind
[KAR install dir]/scripts/setup-kind-kar-macos.sh

# export Kafka and Redis specific env vars by running
[KAR install dir]/scripts/kar-env-local.sh

# clone Reefer application
clone https://github.ibm.com/cwiklik/reefer-app.git

# build Reefer application. This step takes awhile since it involves a download
# of node and npm, and builds Angular GUI runtime artifacts.
cd reefer-app
mvn clean install
```

# Launch Reefer server processes:
```
# Reefer GUI Server in [reefer install dir]/frontend dir run:
mvn liberty:run

# Reefer Actor Server in [reefer install dir]/actors dir run:
kar run -app reefer -actors order,reefer,voyage,reefer-provisioner mvn liberty:run

# Reefer REST Server in [reefer install dir]/reefer-rest dir run:
kar run -app_port 9080 -app reefer -service reeferservice  mvn liberty:run

# Reefer Simulator Server in [reefer install dir]/simulators dir run:
kar run -app_port 7080 -app reefer -service simservice -actors simhelper mvn liberty:run
```

# Access the Reefer Web Application from your browser at:
```
http://localhost:9088
```
