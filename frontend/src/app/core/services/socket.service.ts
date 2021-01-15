/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
