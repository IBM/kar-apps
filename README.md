# Reefer Application

The application models simplified business process involved in shipping perishable goods on a ship from manufacturer to a client. The manufacturer places an order for a shipment of goods which requires one or more refrigerated (reefer) containers and a ship. The land transportation of reefer containers as well as Customs clearance are not considered to simplify the design. To facilitate transit of goods, the application uses a fleet of ships covering Atlantic and Pacific oceans. Each ship in a fleet has a different tonnage (reefer cargo capacity) and is assigned to a shipping schedule serving a route between two ports. The itinerary includes departure date from origin port and arrival date at the destination port. All reefers have identical physical dimensions and have a maximum holding capacity in terms of product units.

The Reefer application runtime consists of four seperately deployable components.  

1. UI server running in OpenLiberty
Its main responsibility is to serve Angular based Reefer application packaged in multiple javascript bundles.

2. SpringBoot REST server running in OpenLiberty
Its main responsibility is to process HTTP requests and return results to clients. Its clients are browsers, KAR actors, and Reefer simulators. This server also maintains the state for the application (in memory for now) and also generates and manages a shipping schedule.

3. Actor server running in OpenLiberty
Its main responsibility is to implement business logic for Reefer App use cases.

4. Simulator server running in OpenLiberty
Its main responsibility is to provide drivers for Reefer App use cases. Currently supported use case are:
   * Advance time and notify active voyages of their new locations
   * Gradually create orders to fill upcoming voyages to a specified target capacity

## Reefer Deployment

### Prereqs
- maven - version 3.6.2+
- KAR framework
- Kind - version 0.8.1+. 
 

### Quick Start
```
# build KAR java SDK in [KAR install dir]/sdk/java/kar-java
mvn clean install

# deploy KAR dependencies Kafka and Redis by either ...
# ... running them in kind
[KAR install dir]/scripts/setup-kind-kar-macos.sh
# ... or running them in docker-compose
[KAR install dir]/scripts/docker-compose-start.sh

# export Kafka and Redis specific env vars by running
[KAR install dir]/scripts/kar-kind-env.sh

# clone Reefer application
clone https://github.ibm.com/cwiklik/reefer-app.git

# build Reefer application. This step takes awhile since it involves a download
# of node and npm, and builds Angular GUI runtime artifacts.
mvn clean install
```
Launch Reefer server processes:
```
# Reefer GUI Server in [reefer install dir]/frontend dir run:
mvn liberty:run

# Reefer Actor Server in [reefer install dir]/actors dir run:
kar -app reefer -actors order,reefer,voyage,reefer-provisioner mvn liberty:run

# Reefer REST Server in [reefer install dir]/reefer-rest dir run:
kar -app_port 9080 -app reefer -service reeferservice  mvn liberty:run

# Reefer Simulator Server in [reefer install dir]/simulators dir run:
kar -app_port 7080 -app reefer -service simservice -actors simhelper mvn liberty:run
```

To access the Reefer Web Application point your browser to:
```
http:\\localhost:9088
```
### GUI

The Reefer Application implements SPA (Single Page Application) design where all 
HTML, Javascript, and CSS code is fetched by the browser in a single page load to
improve user experience. The application offers five distinct views 

1. Order Create View 
- Manually create and submit an order

2. Orders View
- Shows existing orders and starts/stops the Order Simulator

3. Active Voyage View
- Shows active voyages and starts/stops the Ship Simulator

4. Reefers View (partially implemented)

5. Ships View (partially implemented)

### Simulators

Current implementation of the Reefer Application provides two simulators. The Ship
Simulator which advances time and causes ships to move from an origin port
to a destination port. To start the Ship Simulator, navigate to the Active Voyage View and 
change the value in a field directly under the **Simulated Delay** label. This value represents
the desired time compression (in secs). For example, the value of 10 means that 
each day is 10 secs long, 20 makes a day 20 secs, etc. The default value is 0, which disables 
the Ship Simulator. The minimum (and recommended) compression factor for the Ship Simulator is 10secs. 
Once the value is greater than zero, the **Start** button is activated. Press it to start the simulator.
This is a modal button which will toggle **Start** and **Stop**. When the Ship Simulator is running
the voyages begin to appear and the progress updated at the chosen rate.

With the Ship Simulator running you can start the Order Simulator by navigating to the Orders View. 
Change the value directly under the **Order Simulator** label to a number greater than zero. This value
represents the max percentage the orders will fill each ship. For example, the value of 80 
means that the ship will be filled with orders up to 80% of the maximum capacity. After choosing the 
desired percentage, press **Update** button to start the Order Simulator. Orders begin to appear, with 
the newest shown on top.

