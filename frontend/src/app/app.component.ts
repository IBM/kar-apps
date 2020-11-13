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
