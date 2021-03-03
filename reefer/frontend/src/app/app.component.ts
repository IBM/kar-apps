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

import { Component } from '@angular/core';
import { SocketService } from './core/services/socket.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {
  title = 'reefer';

  //public notifications = 0;

    constructor(private webSocketService: SocketService) {
/*
		// Open connection with server socket
        let stompClient = this.webSocketService.connect();
        //console.log('AppComponent - connected socket');
        stompClient.connect({}, frame => {
          console.log('AppComponent - connected socket');
			// Subscribe to notification topic
            stompClient.subscribe('/topic/notification', notifications => {

				// Update notifications attribute with the recent messsage sent from the server
                //this.notifications = JSON.parse(notifications.body).count;
                console.log('Got new notification');
            })
        });
*/
    }
}
