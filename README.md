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
   * Advance time to move ships from origin to destination
   * Create shipping orders and assigned them to ships
   * Generate reefer container events   