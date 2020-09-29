import { Injectable } from '@angular/core';
import { GlobalConstants } from 'src/app/shared/global-constants';

var SockJs = require("sockjs-client");
var Stomp = require("stompjs");

@Injectable({
  providedIn: 'root'
})
export class SocketService {
  private restUrl : string; 

  constructor(){
    // if REEFER_REST_HOST is not defined default to localhost
    if ( GlobalConstants.REST_API_SERVER.includes("undefined")) {
      this.restUrl = "http://localhost:9080";
    } else {
      this.restUrl = GlobalConstants.REST_API_SERVER;
    }
    console.log("+++++++++++++++++++++++++++++ REST URL:"+this.restUrl);
  }

  connect() {
    let socket = new SockJs( this.restUrl+`/socket`);
    console.log('REST HOST:'+this.restUrl+'/socket Connecting ...');
    let stompClient = Stomp.over(socket);
    console.log('Stomp Connected');
    return stompClient;
}


}
