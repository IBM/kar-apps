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

  selectedOriginPort : string;
  selectedDestinationPort : string;
  selectedProduct: string;
  productQty: number = 1000;
  //shippingSchedule: ShipSchedule[] = []; //this.shipSchedule.getShippingSchedule();
  voyages: Voyage[];
  //dataSource = new MatTableDataSource(this.shippingSchedule);
  dataSource = new MatTableDataSource(this.voyages);
  //selection = new SelectionModel<ShipSchedule>(false, []);
  selection = new SelectionModel<Voyage>(false, []);

/*
  transactions$ = this.dataService.messages$.pipe(
    //map(rows => rows.data),

    catchError(error => { throw error; }),
    tap({
      error: error => console.log('[Live Table component] Error:', error),
      complete: () => console.log('[Live Table component] Connection Closed')
    }
    )
  );

*/
  //shippingSchedule: ShipSchedule;
  connected: Subscription;
  isConnected = false;
  date  =  new  FormControl(new  Date());

  displayedColumns: string[] = ['select', 'voyageId', 'vessel', 'origin', 'destination','sailDate', 'transitTime', 'freeCapacity'];
  //dataSource = new MatTableDataSource<ShipSchedule>(SHIPPING_SCHEDULE);

  messages: Subject<any>;
  @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;


  constructor(private dialog: MatDialog, private shipSchedule: ShipScheduleService,private restService: RestService) {

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
      // this.allPorts.push()
      });
      // remove duplicates
      this.originPorts = Array.from(new Set(this.originPorts));
      //this.destinationPorts = this.originPorts;
      this.allPorts = this.originPorts;
      /*
     // var destinations = [];
      data.map( routeData => {
        this.destinationPorts.push(routeData.destinationPort);
       // return route.destinationPort;
      });
*/
    });

    /*
    console.log('Connecting ...');
    this.connected = dataService.connected().subscribe(status => {
      this.isConnected = status;
      console.log('status', status);
      // this._changeDetectorRef.detectChanges()
    });
    */
    //this.dataService.connect();
  }
  saveOrder(product: string, qty: number, origin: string, destination: string, row: Voyage) {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    console.log(">>>>>"+product+" "+ qty+" "+  origin+" "+  destination);

    dialogConfig.data = {

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
       // val => console.log("Dialog output:", val),
        val => this.bookOrder(val)

    );

  }
  bookOrder(val: any) {
    //console.log("bookOrder() - Dialog output:", val);
    const order:  OrderProperties = {
        orderId:'',
        product: val.product,
        productQty: val.productQty,
        originPort: val.originPort,
        destinationPort: val.destinationPort,
        voyageId: val.voyageId
    };
    this.restService.saveOrder(order).subscribe((data) => {

      this.resetVoyages();
      this.showOrderIdDialog(data.orderId );
      })
  }
  resetVoyages() {
    this.selectedOriginPort="";
    this.selectedDestinationPort="";
    this.departureDate = new Date();
    this.dataSource.data = [];
  }
  showOrderIdDialog(orderId: string) {
    const dialogConfig = new MatDialogConfig();

    dialogConfig.disableClose = true;
    dialogConfig.autoFocus = true;
    dialogConfig.width="600px";
    dialogConfig.data = {
      orderId: orderId,

    };

    const dialogRef = this.dialog.open(OrderBookedDialogComponent,
        dialogConfig);


    dialogRef.afterClosed().subscribe(

    );
  }
  connect() {
    //this.dataService.initSocket();
  //  console.log('Connecting ...');
    //this.dataService.connect();
    /*
    this.connected = this.dataService.connected().subscribe(status => {
      this.isConnected = status;
      console.log('status', status);
      // this._changeDetectorRef.detectChanges()
    });
    */
  //  this.messages = <Subject<any>>this.dataService
   //   .connect();
      /*
      .map((response: MessageEvent): any => {
        console.log(response);
        return response.data;
      });
*///
    //  console.log(this.dataService);



    // this.messages.next(`CONNECT: ${this.address}`);
  }

  send() {
   // this.connect();
    console.log('Sending Message');
    // this.messageLog = [...this.messageLog, 'SENT: ' + this.message];
    //this.messages.next(JSON.stringify('some message'));
    //this.dataService.sendMessage('some message');
    this.restService.getSchedule();//sendGetRequest();
  }
  rowClicked(row: any): void {
    console.log(row);

  }
/*
  openDialog(): void {
    const dialogRef = this.dialog.open(DialogOverviewExampleDialog, {
      width: '250px',
      data: {name: this.name, animal: this.animal}
    });

    dialogRef.afterClosed().subscribe(result => {
      console.log('The dialog was closed');
      this.animal = result;
    });
  }
*/
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
 //   this.destinationPorts = this.allPorts.filter(function(destination) {
 //     return destination != origin.value;
 //   });
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
 //   this.destinationPorts = this.allPorts.filter(function(destination) {
 //     return destination != origin.value;
 //   });
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
   // dataSource.set
   // const data = this.dataSource.data;
  // this.shippingSchedule = this.restService.getSchedule();
    //this.dataSource.data = data;
    this.restService.getMatchingVoyages(this.selectedOriginPort, this.selectedDestinationPort,this.departureDate.toISOString()).subscribe((data) => {
      console.log(data);
      this.dataSource.data = data;
     // this.dataSource.filter =this.selectedOriginPort && this.selectedDestinationPort;
     /*
      this.dataSource.data = data.filter( ss => {
        return ss.origin === this.selectedOriginPort && ss.destination === this.selectedDestinationPort;
      })
*/
    }

    );
  }

  ngOnInit(): void {
   // this.dataService.initializeWebSocketConnection();
    this.dataSource.paginator = this.paginator;
   this.send();
   // this.dataService.sendMessage(this.displayedColumns);
  }
  ngOnDestroy() {
    console.log('Closing WebSocket Connection');
    //this.dataService.closeConnection();
  }
}

