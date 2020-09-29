import { Injectable } from '@angular/core';
import { GlobalConstants } from 'src/app/shared/global-constants';

var SockJs = require("sockjs-client");
var Stomp = require("stompjs");


@Injectable({
  providedIn: 'root'
})
export class SocketService {

  constructor(){
   
  }

  connect() {
   

    let socket = new SockJs( GlobalConstants.restServerUrl+`/socket`);
    console.log('Connecting to GlobalConstants.restServerUrl+`/socket...');
    let stompClient = Stomp.over(socket);
    console.log('Stomp Connected');
    return stompClient;
}

 
}
