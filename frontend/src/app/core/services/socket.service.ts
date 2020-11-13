import { Injectable } from '@angular/core';
import { GlobalConstants } from 'src/app/shared/global-constants';
import * as Rx from "rxjs/Rx";

var SockJs = require("sockjs-client");
var Stomp = require("stompjs");

@Injectable({
  providedIn: 'root'
})
export class SocketService {
  constructor(){

  }

  connect() {
    console.log('Connecting to '+GlobalConstants.restServerUrl+'/socket...');
    let socket = new SockJs( GlobalConstants.restServerUrl+`/socket`);
    let stompClient = Stomp.over(socket);
    console.log('+++++++ StompClient connected to the server');
    return stompClient;
  }

}
