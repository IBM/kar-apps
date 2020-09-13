import { Component, OnInit, ViewChild, SystemJsNgModuleLoader } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatHorizontalStepper } from '@angular/material/stepper';
import { Voyage } from 'src/app/core/models/voyage';
import { RestService } from 'src/app/core/services/rest.service';
import { RouteConfigLoadEnd } from '@angular/router';
import { Route } from '@angular/compiler/src/core';
import { SocketService } from 'src/app/core/services/socket.service';
import { Console } from 'console';
import { ActiveSchedule } from 'src/app/core/models/active-schedule';

@Component({
//  animations: [appModuleAnimation()],
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
  @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
  @ViewChild(MatSort, {static: true}) sort: MatSort;
  @ViewChild(MatHorizontalStepper) stepper: MatHorizontalStepper;  
  expandedElement:Voyage | null;
//  shipTableColumns: string[] = [ 'vessel', 'origin', 'destination', 'maxCapacity', 'freeCapacity', 'location'];
  shipTableColumns: string[] = [ 'vessel', 'progress', 'orders','maxCapacity', 'freeCapacity'];

  constructor(private restService: RestService, private webSocketService : SocketService) { 
    let stompClient = this.webSocketService.connect();
    console.log('AppComponent - connected socket');
    stompClient.connect({}, frame => {
      console.log('ActiveVoyageView - connected stompClient');
  // Subscribe to notification topic
        stompClient.subscribe('/topic/voyages', (event:any) => {
          if ( event.body) {
            let schedule: ActiveSchedule;
            schedule = JSON.parse(event.body);

            //this.voyages = JSON.parse(event.body);
            this.voyages = schedule.voyages;
            this.date = schedule.currentDate.substr(1,10);
            console.log('::::::'+this.voyages);
            this.voyageDataSource.data = this.voyages;

          }

        })
    });

  }
  delayChange(event: any) {
 
    let value = event.target.value;
    this.rate = Math.ceil(value/5)*5;
    console.log("Delay Change:"+this.rate);
  }
  ngOnInit(): void {
    this.restService.currentDate().subscribe((data) => {
      console.log("nextDay() - Current Date:" + data.substr(0,10));
      this.date = data.substr(0,10);
    });
    this.restService.getSimulatorDelay().subscribe((data) => {
      console.log(data);
      this.rate = data;
      if ( this.rate > 0 ) {
        this.autoSimButtonLabel = "STOP";
      } else {
        this.autoSimButtonLabel = "START";
      }
    });
    this.getActiveVoyages();
  }
  
  toggleStartStop(event: Event) {
    console.log("Click "+event);
    if ( this.autoSimButtonLabel == "START") {
      this.autoSimButtonLabel = "STOP";
//      this.restService.setAutoMode(10).subscribe((data) => {
//        console.log(data);
//      });
    } else if ( this.autoSimButtonLabel == "STOP") {
      this.autoSimButtonLabel = "START";
      this.rate = 0;
      console.log("............ Stopping Ship Simulator - delay:"+this.rate);
    }
    this.restService.setSimulatorDelay(this.rate).subscribe((data) => {
      console.log(data);
    });
  }

  getActiveVoyages() {

    this.restService.getActiveVoyages().subscribe((data) => {

      let voyages: Voyage[] = data;
      /*
      voyages.forEach(function(voyage) {
        var r : Route = voyage.route;

        console.log(">>>>>>>>"+r.);
      });
*/
     // Object.keys(voyages).forEach(key => {
      //  console.log(">>>>>>>>"+voyages[key]);
      //});
//      for( var v of voyages ) {
//       // var value = `${voyages[v]}`;
//        console.log(">>>>>>>>"+v);
//      }
      //let ships: Ship[] = fleet[0].ships;
      console.log(voyages);
      this.voyageDataSource.data = voyages; 
      });
      //this.shipDataSource.data = this.getFleets()[0].ship;
    //  this.voyageDataSource.sort = this.sort;
    //  this.voyageDataSource.paginator = this.paginator;
  }

  nextDay() {
    console.log('>>>>>>>>>>>>>nextDay called');
    this.restService.advanceDateByOneDay().subscribe((data) => {
      console.log("nextDay() - Current Date:" + data.substr(0,10));
      this.date = data.substr(0,10);
    });
   // this.getActiveVoyages();
  }
  calculateProgress(days: number, voyageDurationInDays: number) {
   // console.log(" Days:"+days + " Total:"+voyageDurationInDays);
    let res =  Math.round( (days/voyageDurationInDays)*100);
   // console.log(" Percent Complete:"+res);
    return res;
  }
}
