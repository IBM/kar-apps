# Reefer Front End

This project was generated with [Angular CLI](https://github.com/angular/angular-cli) version 9.1.6.

To build the project, run mvn clean install

By default, the Reefer front end uses port 9088. If this port is taken, edit src/main/liberty/config/serve/xml
and change the value of http port in the following xml element:

<httpEndpoint httpPort="9088" httpsPort="8443" id="defaultHttpEndpoint" host="*" />

The Reefer front end communicates with the REST service using a URL defined in dist/reefer/index.html
  <script type="text/javascript">
     var reeferRestApiBaseUrl="http://localhost:9080";

     window.reeferRestApiBaseUrl = reeferRestApiBaseUrl;
  </script>

Change the value of reeferRestApiBaseUrl to match the host and port of your REST service.

To launch the Reefer front end using Liberty maven plugin, run
  mvn liberty:run

Point your browser to: http://localhost:9088 to launch the Reefer user interface.

