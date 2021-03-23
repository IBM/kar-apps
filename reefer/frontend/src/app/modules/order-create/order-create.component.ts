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

import { Component, OnInit, OnDestroy, ViewChild, ChangeDetectionStrategy } from '@angular/core';

import {FormControl, Validators} from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { MatTableDataSource }  from '@angular/material/table';
import {ShipScheduleService} from '../../core/services/ship-schedule.service';
import {RestService} from '../../core/services/rest.service';
import {ShipSchedule} from '../../core/models/ship-schedule';
import {SocketService} from '../../core/services/socket.service';
import { Observable, Subject } from 'rxjs/Rx';
import { Subscription } from "rxjs/Subscription";
import { map, tap, catchError, retry } from 'rxjs/operators';
import {SelectionModel} from '@angular/cdk/collections';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {MatDialog, MatDialogRef, MAT_DIALOG_DATA, MatDialogConfig} from '@angular/material/dialog';
import { SubmitOrderComponent } from './dialogs/submit-order/submit-order.component';
import { OrderProperties } from 'src/app/core/models/order-properties';
import { OrderBookedDialogComponent } from './dialogs/order-booked-dialog/order-booked-dialog.component';
import { Voyage } from 'src/app/core/models/voyage';
import { clear } from 'console';

@Component({
  selector: 'app-order-create',
  templateUrl: './order-create.component.html',
  styleUrls: ['./order-create.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})


export class OrderCreateComponent implements OnInit, OnDestroy {

  products: any[] = [
    { name: 'Carrots'},
    { name: 'Beef'},
    { name: 'Butter'},
    { name: 'Apples'},
    { name: 'Coca-Cola'},
  ];
  departureDate: Date;
  allRoutes: any[] = [];
  originPorts: string[] = [];
  destinationPorts: string[] = [];
  allPorts: string[] = [];

  customerId: string;
  selectedOriginPort : string;
  selectedDestinationPort : string;
  selectedProduct: string;
  productQty: number = 1000;
  voyages: Voyage[];
  dataSource = new MatTableDataSource(this.voyages);
  selection = new SelectionModel<Voyage>(false, []);

  connected: Subscription;
  isConnected = false;
  date = new Date(new Date().setDate(new Date().getDate()+1));

  displayedColumns: string[] = ['select', 'voyageId', 'vessel', 'origin', 'destination','sailDate', 'transitTime', 'freeCapacity'];

  messages: Subject<any>;
  @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;


  constructor(private dialog: MatDialog, private shipSchedule: ShipScheduleService,private restService: RestService) {
console.log("Date:"+this.date);
    this.restService.getRoutes().subscribe((data) => {
     // console.log(data);
     this.allRoutes = data;

      var origins = [];
      data.map( routeData => {
       // combine origin and destination port (to and from routes)
       this.originPorts.push(routeData.originPort);
       this.originPorts.push(routeData.destinationPort);
       this.destinationPorts.push(routeData.destinationPort);
       this.destinationPorts.push(routeData.originPort);

      });
      // remove duplicates
      this.originPorts = Array.from(new Set(this.originPorts));
      this.allPorts = this.originPorts;
    });
  }
  saveOrder(product: string, qty: number, origin: string, destination: string, row: Voyage) {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    console.log(">>>>>"+product+" "+ qty+" "+  origin+" "+  destination);

    dialogConfig.data = {
      customerId:this.customerId,
        product:product,
        productQty:qty,
        originPort: origin,
        destinationPort:destination,
        voyageId: row.id,
        shipName: "",
        departure: row.sailDate
    };

    const dialogRef = this.dialog.open(SubmitOrderComponent,
        dialogConfig);


    dialogRef.afterClosed().subscribe(
        val => this.bookOrder(val)

    );

  }
  bookOrder(val: any) {
   const order:  OrderProperties = {
        orderId:'',
        customerId:val.customerId,
        product: val.product,
        productQty: val.productQty,
        originPort: val.originPort,
        destinationPort: val.destinationPort,
        voyageId: val.voyageId,
        bookingStatus: '',
        msg:''
    };
    this.restService.saveOrder(order).subscribe((data : OrderProperties) => {
      console.log("order-create - saveOrder - rest reply:"+data.orderId+" status:"+data.bookingStatus+" msg:"+data.msg);
      this.resetVoyages();
      //this.showOrderIdDialog(data.orderId );
       this.showOrderIdDialog(data );
      })
  }
  resetVoyages() {
    this.selectedOriginPort="";
    this.selectedDestinationPort="";

    this.departureDate = new Date();

    this.dataSource.data = [];
  }
  //showOrderIdDialog(orderId: string) {
  showOrderIdDialog(orderProperties: OrderProperties) {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    dialogConfig.width="600px";
    dialogConfig.data = {
     // orderId: orderId,
      orderId: orderProperties.orderId,
      status: orderProperties.bookingStatus,
      msg: orderProperties.msg,
    };

    const dialogRef = this.dialog.open(OrderBookedDialogComponent,
        dialogConfig);


    dialogRef.afterClosed().subscribe(

    );
  }
  connect() {
   }

  send() {

    console.log('Sending Message');
    this.restService.getSchedule();//sendGetRequest();
  }
  rowClicked(row: any): void {
    console.log(row);

  }

  adjustDestinationPorts(origin: any) {
    console.log(origin.value);
    console.log(this.allRoutes);
    var destinations = [];
    this.allRoutes.map( routeData => {
      this.destinationPorts.length = 0;

      if ( routeData.originPort == origin.value ) {
        console.log("Adding destination:"+routeData.destinationPort);
        destinations.push(routeData.destinationPort);
      } else if ( routeData.destinationPort == origin.value ) {
        console.log("Adding destination:"+routeData.originPort);
        destinations.push(routeData.originPort);

      }

     });
     this.destinationPorts = destinations;
     console.log(this.destinationPorts);
  };
  adjustOriginPorts(destination: any) {
    console.log(destination.value);
    console.log(this.allRoutes);
    var origins = [];
    this.allRoutes.map( routeData => {
      this.originPorts.length = 0;

      if ( routeData.originPort == destination.value ) {
        console.log("Adding destination:"+routeData.destinationPort);
        origins.push(routeData.destinationPort);
      } else if ( routeData.destinationPort == destination.value ) {
        console.log("Adding destination:"+routeData.originPort);
        origins.push(routeData.originPort);

      }

     });
     this.originPorts = origins;
     console.log(this.originPorts);
  };

  selectedVoyage($event, row?: Voyage) {
    const numSelected = this.selection.selected.length;
    if ($event.checked) {
      console.log(row);
      this.saveOrder(this.selectedProduct, this.productQty, this.selectedOriginPort, this.selectedDestinationPort,row);
    }

  }

    /** Whether the number of selected elements matches the total number of rows. */
    isAllSelected() {
      const numSelected = this.selection.selected.length;
      const numRows = this.dataSource.data.length;
      return numSelected === numRows;
    }

    /** Selects all rows if they are not all selected; otherwise clear selection. */
    masterToggle() {

      this.isAllSelected() ?
          this.selection.clear() :
          this.dataSource.data.forEach(row => this.selection.select(row));
    }

    /** The label for the checkbox on the passed row */
    checkboxLabel(row?: Voyage): string {

      if (!row) {
        return `${this.isAllSelected() ? 'select' : 'deselect'} all`;
      }

      return `${this.selection.isSelected(row) ? 'deselect' : 'select'} all`; //row ${row.position + 1}`;
    }

    departureDateChangeEvent(event) {
      this.departureDate = event.value;
      console.log("Departure Date Changed:"+this.departureDate+" UTC:"+this.departureDate.toISOString());
    }

  search() {
    console.log('search called - Origin:'+this.selectedOriginPort+' Destination:'+this.selectedDestinationPort);
    this.restService.getMatchingVoyages(this.selectedOriginPort, this.selectedDestinationPort,this.departureDate.toISOString()).subscribe((data) => {
      console.log(data);
      this.dataSource.data = data;
    }

    );
  }

  ngOnInit(): void {
    this.restService.currentDate().subscribe((data) => {
      console.log("nextDay() - Current Date:" + data.substr(0,10));
      // start the Date picker with tommorrow as the earliest date to order
      this.date = new Date(new Date().setDate(new Date(data.toString()).getDate()+1));
    });
    this.dataSource.paginator = this.paginator;
   this.send();
  }
  ngOnDestroy() {
    console.log('Closing WebSocket Connection');
  }
}

