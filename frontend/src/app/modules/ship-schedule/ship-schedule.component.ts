import { Component, OnInit, ViewChild, SystemJsNgModuleLoader } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Voyage } from 'src/app/core/models/voyage';
import { RestService } from 'src/app/core/services/rest.service';
import { RouteConfigLoadEnd } from '@angular/router';
import { Route } from '@angular/compiler/src/core';
import { SocketService } from 'src/app/core/services/socket.service';
import { Console } from 'console';
import { ActiveSchedule } from 'src/app/core/models/active-schedule';
import { GlobalConstants } from 'src/app/shared/global-constants';
import {
  trigger,
  state,
  style,
  animate,
  transition,
  // ...
} from '@angular/animations';

var SockJs = require("sockjs-client");
var Stomp = require("stompjs");

@Component({
  selector: 'app-ship-schedule',
  templateUrl: './ship-schedule.component.html',
  styleUrls: ['./ship-schedule.component.scss']
})
export class ShipScheduleComponent implements OnInit {
  date: string;
  rate: number = 0;
  count : number = 0;
  autoSimButtonLabel: string = "START";

  progressbarValue = 0;
  voyages: Voyage[] = [];
  voyageDataSource = new MatTableDataSource(this.voyages);
  expandedElement:Voyage | null;
  shipTableColumns: string[] = [ 'vessel', 'progress', 'orders','maxCapacity', 'freeCapacity'];
  stompClient:any;
  webSocket: SocketService;

  constructor(private restService: RestService, private webSocketService : SocketService) {
    this.webSocket = webSocketService;
  }

  connect() {
    this.stompClient = this.webSocket.connect();
    this.stompClient.connect({}, frame => {
       // Subscribe to notification topic
          this.stompClient.subscribe('/topic/voyages', (event:any) => {

            if ( event.body) {
              let schedule: ActiveSchedule;
              schedule = JSON.parse(event.body);
              this.voyages = schedule.voyages;
              let d = schedule.currentDate;
              // strip quotation marks
              d = d.replace(/"/g,"");
              this.date = d.substr(0,10);
             // console.log('::::::'+this.voyages);
            //  this.voyageDataSource.data = new MatTableDataSource(this.voyages);//this.voyages;
               this.voyageDataSource = new MatTableDataSource(this.voyages);
            }

          })
    // call bind to be able to use it in errorCallBack method. If not
    // done, this is undefined in errorCallBack.
    }, this.errorCallBack.bind(this));

  }
  errorCallBack(error) {
     console.log("ShipScheduleComponent.errorCallBack() - Websocket connection closed -> " + error)
     // retries connection every 2s
     setTimeout(() => {
        console.log("ShipScheduleComponent.errorCallBack() - retrying connection every 2s .... ");
        this.connect();
     }, 2000);
  }
  delayChange(event: any) {

    let value = event.target.value;
    this.rate = Math.ceil(value/5)*5;
    console.log("Delay Change:"+this.rate);
  }
  ngOnInit(): void {

   // connect to back end server via Websocket
   this.connect();

    this.restService.getSimulatorDelay().subscribe((data) => {
     // console.log(data);
      this.rate = data;
      if ( this.rate > 0 ) {
        this.autoSimButtonLabel = "STOP";
      } else {
        this.autoSimButtonLabel = "START";
      }
    });
    this.getActiveVoyages();
  }

  update(event: Event) {
    console.log("Click "+event);
    this.restService.setSimulatorDelay(this.rate).subscribe((data) => {
      console.log(data);
    });
  }

  getActiveVoyages() {

    this.restService.getActiveVoyages().subscribe((data) => {

      let voyages: Voyage[] = data;
      this.voyageDataSource.data = voyages;
      });
  }

  nextDay() {
    this.restService.advanceDateByOneDay().subscribe((data) => {
      console.log("nextDay() - Current Date:" + data.substr(0,10));
      this.date = data.substr(0,10);
    });

  }
  calculateProgress(days: number, voyageDurationInDays: number) {
    let res =  Math.round( (days/voyageDurationInDays)*100);
    return res;
  }
}
