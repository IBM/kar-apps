export class GlobalConstants {

    public static  REST_API_SERVER =`http://${process.env.REEFER_REST_HOST}:9080/`;

    constructor() {
        // if REEFER_REST_HOST is not defined default to localhost
          
          if ( GlobalConstants.REST_API_SERVER.includes("undefined")) {
            GlobalConstants.REST_API_SERVER = "http://localhost:9080";
        } 
        console.log("+++++++++++++++++++++++++++++ GlobalConstants REST URL:"+GlobalConstants.REST_API_SERVER);
      }
    
}
