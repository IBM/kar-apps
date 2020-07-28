# Reefer Front End

This project was generated with [Angular CLI](https://github.com/angular/angular-cli) version 9.1.6.

To build the project, run mvn clean install

By default, the Reefer front end uses port 9088. If this port is taken, edit src/main/liberty/config/serve/xml
and change the value of http port in the following xml element:

`<httpEndpoint httpPort="9088" httpsPort="8443" id="defaultHttpEndpoint" host="*" />`

The Reefer front end communicates with the REST service using a URL defined in dist/reefer/index.html

 `var reeferRestApiBaseUrl="http://localhost:9080";`


Change the value of reeferRestApiBaseUrl to match the host and port of your REST service.

__Before launching Reefer front end make sure that Reefer REST, Reefer Actors, and Reefer Simulator processes are running. Also when the Reefer Simulator starts, run curl -s -H "Content-Type: application/json" -X POST http://localhost:7080/simulator/togglereeferrest__

To launch the Reefer front end using Liberty maven plugin, run
  mvn liberty:run

Point your browser to: http://localhost:9088 to launch the Reefer user interface.

