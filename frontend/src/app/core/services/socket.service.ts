import { Injectable } from '@angular/core';
import { GlobalConstants } from 'src/app/shared/global-constants';

//import { GlobalConstants} from './shared/global-constants';

/*import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { Socket } from 'ngx-socket-io';
import { of, Observable } from 'rxjs';
import { Observer } from 'rxjs/Observer';
import { Message } from '../models/message';
import { Event } from '../models/event';
import * as socketIo from 'socket.io-client';
import { map, filter, scan, tap, catchError , switchAll } from 'rxjs/operators';
import * as Rx from 'rxjs';
import { EMPTY, Subject } from "rxjs";
*/
//import * as Stomp from 'stompjs';
//import * as SockJS from 'sockjs-client';
//import { SocketIoModule, SocketIoConfig, Socket } from 'ngx-socket-io';
//const WS_ENDPOINT = 'http://localhost:8080';
//const WS_ENDPOINT = 'ws://localhost:8080/hello';

//declare var require: any

var SockJs = require("sockjs-client");
var Stomp = require("stompjs");

//const config: SocketIoConfig = { url: 'http://localhost:8080/hello', options: {} };
@Injectable({
  providedIn: 'root'
})
export class SocketService {
  private restUrl : string; //=`http://${process.env.REEFER_REST_HOST}:9080/`;
  //private serverUrl = 'http://localhost:8080/socket'
  //private title = 'WebSockets chat';
  //private stompClient;

  constructor(){
    //let rest_url:string;
    //console.log("+++++++++++++++++++++++++++++REST_API_SERVER:"+GlobalConstants.REST_API_SERVER);
    if ( GlobalConstants.REST_API_SERVER.includes("undefined")) {
      this.restUrl = "http://localhost:9080";
    } else {
      this.restUrl = GlobalConstants.REST_API_SERVER;
    }
    //this.REST_API_SERVER = rest_url; //"http://"+rest_host+":"+GlobalConstants.REST_PORT+"/";
    console.log("+++++++++++++++++++++++++++++ REST URL:"+this.restUrl);
   // this.initializeWebSocketConnection();
  }

  connect() {
   
//    let socket = new SockJs(`http://localhost:8080/websocket-backend/socket`);
//    let socket = new SockJs(`http://localhost:8080/socket`);
    //let socket = new SockJs(`http://localhost:9000/socket`);
  //  let socket = new SockJs( GlobalConstants.restServerUrl+`/socket`);
    let socket = new SockJs( this.restUrl+`/socket`);
    console.log('REST HOST:'+this.restUrl+'/socket Connecting ...');
    let stompClient = Stomp.over(socket);
    console.log('Stomp Connected');
    return stompClient;
}

  /*
  public sendMessage(message : string)  {
    this.socket.emit('new-message', message);
}
public getMessages = () => {
  return Observable.create((observer : any) => {
          this.socket.on('new-message', (message : any) => {
              observer.next(message);
          });
  });
}
*/
/*
  initializeWebSocketConnection(){
    let ws = new SockJS(this.serverUrl);
    this.stompClient = Stomp.over(ws);
    let that = this;
    this.stompClient.connect({}, function(frame) {
      that.stompClient.subscribe("/chat", (message) => {
        if(message.body) {
          //$(".chat").append("<div class='message'>"+message.body+"</div>")
          console.log(message.body);
        }
      });
    });
  }

  sendMessage(message){
    this.stompClient.send("/app/send/message" , {}, message);
    //$('#input').val('');
  }
*/

  //private socket;

  /*
  constructor(private socket: Socket) {
  }
  public sendMessage(message) {
    this.socket.emit('new-message', message);
}
public getMessages = () => {
  return Observable.create((observer) => {
          this.socket.on('new-message', (message) => {
              observer.next(message);
          });
  });
}
*/
/*
  myWebSocket: WebSocketSubject<any> = webSocket('ws://localhost:8080/hello');

  constructor() {
    this.myWebSocket.subscribe(
      msg => console.log('message received: ' + msg),
      // Called whenever there is a message from the server
      err => console.log(err),
      // Called if WebSocket API signals some kind of error
      () => console.log('complete')
      // Called when connection is closed (for whatever reason)
   );
  }

  sendMessageToServer() {
    this.myWebSocket.next({message: 'some message'});
  }
  */
  /*
  private socket$: WebSocketSubject<any>;
  private messagesSubject$ = new Subject();
  public messages$ = this.messagesSubject$.pipe(switchAll(), catchError(e => { throw e }));

  constructor() {
  }
  public connect(): void {

    if (!this.socket$ || this.socket$.closed) {
      this.socket$ = this.getNewWebSocket();
      const messages = this.socket$.pipe(
        tap({
          error: error => console.log(error),
        }), catchError(_ => EMPTY));
      this.messagesSubject$.next(messages);
    }
  }

  private getNewWebSocket() {
    return webSocket(WS_ENDPOINT);
  }
  sendMessage(msg: any) {
    this.socket$.next(msg);
  }
  close() {
    this.socket$.complete();
  }


*/

/*

  private subject: Rx.Subject<MessageEvent>;
  private connected$ = new Subject<any>();
  constructor() {
    // this.initSocket();
   }
  public connect(): Rx.Subject<MessageEvent> {
    console.log("Trying connection");
    if (!this.subject) {

      this.subject = this.create(WS_ENDPOINT);
      console.log("Successfully connected: " + WS_ENDPOINT);
      this.connected$.next(true);
    }
    return this.subject;
  }

  public connected(): Observable<any> {
    return this.connected$.asObservable();
  }

  private create(url): Rx.Subject<MessageEvent> {
    let ws = new WebSocket(url);

    let observable = Rx.Observable.create(
      (obs: Rx.Observer<MessageEvent>) => {
        ws.onmessage = obs.next.bind(obs);
        ws.onerror = obs.error.bind(obs);
        ws.onclose = obs.complete.bind(obs);
        return ws.close.bind(ws);
      })
    let observer = {
      next: (data: Object) => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify(data));
        }
      }
    }
    return Rx.Subject.create(observer, observable);
  }
  */
  /*
  //private socket;
  private socket$: WebSocketSubject<any>;

  constructor() {
   // this.initSocket();
  }



  public connect(): WebSocketSubject<any> {
    if (!this.socket$ || this.socket$.closed) {
      console.log('Connecting to remote service');
      this.socket$ = webSocket(WS_ENDPOINT);
      console.log('Connected to remote service');
    }
    return this.socket$;
  }

  public dataUpdates$() {
    return this.connect().asObservable();
  }

  closeConnection() {
    this.connect().complete();
  }

  sendMessage(msg: any) {
    console.log('Sending message ...');
     this.socket$.next(msg);
     //this.socket$.next(msg);
  }
*/
//private socket$: WebSocketSubject<any>;


/*
    public initSocket(): void {

      console.log('Connecting ....');
//        this.socket = socketIo(SERVER_URL, ['websocket'] );
        this.socket = socketIo(WS_ENDPOINT);

        console.log('Connected ....');
    }

 //   public send(message: Message): void {
      public send(message: string): void {
        this.socket.emit( message);
    }

    public onMessage(): Observable<Message> {
        return new Observable<Message>(observer => {
            this.socket.on('message', (data: Message) => observer.next(data));
        });
    }

    public onEvent(event: Event): Observable<any> {
        return new Observable<Event>(observer => {
            this.socket.on(event, () => observer.next());
        });
    }
*/
}
