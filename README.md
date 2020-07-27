# Reefer Application

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