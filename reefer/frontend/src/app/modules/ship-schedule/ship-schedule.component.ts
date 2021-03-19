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

import { Component, OnInit, ViewChild, SystemJsNgModuleLoader } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { Voyage } from 'src/app/core/models/voyage';
import { Route } from 'src/app/core/models/route';
import { Ship } from 'src/app/core/models/ship';
import { RestService } from 'src/app/core/services/rest.service';
import { RouteConfigLoadEnd } from '@angular/router';
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

    this.restService.currentDate().subscribe((data) => {
      this.updateDate(data);
     });
     this.getActiveVoyages();
  }

  connect() {
    this.stompClient = this.webSocket.connect();
    this.stompClient.connect({}, frame => {
       // Subscribe to notification topic
          this.stompClient.subscribe('/topic/voyages', (event:any) => {

            if ( event.body) {
             let schedule: ActiveSchedule;
               schedule = JSON.parse(event.body);
               this.updateDate(schedule.currentDate);
               this.updateVoyages(schedule.voyages);
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
  updateVoyages( data) {

    this.voyages = data;

    for ( var inx in this.voyages ) {
        let voyage : Voyage = this.voyages[inx];

        let r =  voyage["route"];
        let s = r["vessel"];
        // find index of voyage to update
        const index = this.voyageDataSource.data.findIndex((v: Voyage) => v["route"]["vessel"]['name'] === s['name']);
        if (index !== -1 ) {
           if ( voyage.progress !== this.voyageDataSource.data[index].progress) {
              // tried more efficient this.voyageDataSource.data[index] = voyage, but
              // it produced flicker. So fallback is to change individual properties
              this.voyageDataSource.data[index].progress = voyage.progress;
              this.voyageDataSource.data[index]["route"]['originPort'] = r['originPort'];
              this.voyageDataSource.data[index]["route"]['destinationPort'] = r['destinationPort'];
              this.voyageDataSource.data[index]['sailDate'] = voyage['sailDate'];
              this.voyageDataSource.data[index]['displayArrivalDate'] = voyage['displayArrivalDate'];
              this.voyageDataSource.data[index]['orderCount'] = voyage['orderCount'];
              this.voyageDataSource.data[index]["route"]["vessel"]['freeCapacity'] = s['freeCapacity'];
           }
        } else {
           // new voyage - add a new row in the table
           this.voyageDataSource.data.push(voyage);
        }
    }
     this.voyageDataSource._updateChangeSubscription();
  }
  updateDate(d){
     d = d.replace(/"/g,"");
     this.date = d.substr(0,10);
  }
  getActiveVoyages() {
    this.restService.getActiveVoyages().subscribe((data) => {
       this.updateVoyages(data);
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
