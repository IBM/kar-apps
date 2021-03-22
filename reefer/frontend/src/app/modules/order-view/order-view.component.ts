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

import { Component, OnInit, ViewChild, AfterViewInit, Input, ContentChild } from '@angular/core';
import { Order } from 'src/app/core/models/order';
import { SelectionModel } from '@angular/cdk/collections';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { RestService } from 'src/app/core/services/rest.service';
import { MatTableDataSource, MatTable } from '@angular/material/table';
import {MatSort, MatSortable} from '@angular/material/sort';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { SocketService } from 'src/app/core/services/socket.service';
import { OrderStats } from 'src/app/core/models/order-stats';


@Component({
  selector: 'app-order-view',
  templateUrl: './order-view.component.html',
  styleUrls: ['./order-view.component.scss'],
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({height: '0px', minHeight: '0', visibility: 'hidden'})),
      state('expanded', style({height: '*', visibility: 'visible'})),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
],
})
export class OrderViewComponent implements OnInit {
  selection = new SelectionModel<Order>(false, []);
  displayedColumns: string[] = ['select',  'id', 'customerId','product', 'productQty', 'voyageId'];
  orders: Order[] = [];
  orderTarget : number ;
  windowSize : number;
  orderUpdates : number;
  filterValues = {};
  filterSelectObj = [];
  totalElements: number = 0;
  loading: boolean;
  createOrderManually: boolean;
  inTransitOrders:number ;
  futureOrders:number;
  spoiltOrders:number ;
  orderLabel: string = " ----------- Orders ----------- ";

  autoSimButtonLabel: string = "Update";
  dataSource = new MatTableDataSource(this.orders);
  public currentExpandedRow: any;
  //public expandRow: boolean = false;
  public expandedElement: boolean = true;
  @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
  @ViewChild(MatSort, {static: true}) sort: MatSort;

  isExpansionDetailRow = (_, row: any) => row.hasOwnProperty('detailRow');
  explansionDetialRowCollection = new Array<any>();

  webSocket: SocketService;

  constructor(private dialog: MatDialog, private restService: RestService, private webSocketService : SocketService) {
   this.webSocket = webSocketService;

    this.restService.getOrderStats().subscribe((data) => {
      // console.log(data);
      this.inTransitOrders = data.inTransitOrderCount;
      this.futureOrders = data.futureOrderCount;
      this.spoiltOrders = data.spoiltOrderCount;
    });

  }



  connect() {
      let stompClient = this.webSocketService.connect();
     // console.log('OrderView - connected socket');
      stompClient.connect({}, frame => {
        console.log('OrderViewComponent - waiting for events on /topic/orders/stats');
        stompClient.subscribe('/topic/orders/stats', (event:any) => {

          if ( event.body) {
            let orderStats : OrderStats;
            orderStats = JSON.parse(event.body);
            this.spoiltOrders = orderStats.spoiltOrderCount;
            this.futureOrders = orderStats.futureOrderCount;
            this.inTransitOrders = orderStats.inTransitOrderCount;
          }
        });

    // Subscribe to notification topic
        stompClient.subscribe('/topic/orders', (event:any) => {
          if ( event.body) {
              let order: Order;
              this.dataSource.data.forEach(row => console.log(row.id));
              // Add the order to the HEAD only when we are on the
              // first page of orders else just ignore this update
              if ( this.paginator.pageIndex == 0) {
                order = JSON.parse(event.body);
                // The following two lines of code are required
                // to add new order to the HEAD of the list
                const currentData = this.dataSource.data;
                currentData.unshift(order);

                this.dataSource.data = currentData;
                this.dataSource.sort = this.sort;
              }

            }

          });

      }, this.errorCallBack.bind(this));
  }

  errorCallBack(error) {
     console.log("OrderViewComponent.errorCallBack() - Websocket connection closed -> " + error)
     // retries connection every 2s
     setTimeout(() => {
        console.log("OrderViewComponent.errorCallBack() - retrying connection every 2s .... ");
        this.connect();
     }, 2000);
  }

  ngOnInit(): void {
     // connect to back end server via Websocket
     this.connect();
     this.restService.getOrderTargetAndSimDelay().subscribe((data) => {

      if ( data.target > 0 && data.delay > 0 ) {
        this.createOrderManually = false;
      }else {
        this.createOrderManually = true;
      }
      this.orderTarget = data.target;


    });
    this.restService.getOrderSimControls().subscribe((data) => {
      this.orderTarget = data.target;
      this.windowSize = data.window;
      this.orderUpdates = data.updateTarget;
    });

  }
  showInTransitOrders(event) {
    console.log("Click >>>>> showInTransitOrders() ");
    this.restService.getActiveOrders().subscribe((data) => {
     // console.log(data);
      this.dataSource.data = data;

    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.orderLabel = " ----------- In-Transit Orders ----------- ";

     }


    );
  }
  showFutureOrders() {
    console.log("Click >>>>> showFutureOrders()");
    this.restService.getBookedOrders().subscribe((data) => {
    this.dataSource.data = data;

    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.orderLabel = " ----------- Future Orders ----------- ";
     }


    );
  }
  showSpoiltOrders() {
    console.log("Click >>>>> showSpoiltOrders()");
    this.restService.getSpoiltOrders().subscribe((data) => {

      this.dataSource.data = data;

    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.orderLabel = " ----------- Spoilt Orders ----------- ";
     }


    );
  }

  updateOrderSimulatorControlls(event: Event) {
    console.log("Click >>>>>"+event +" Order Target:"+this.orderTarget);
    const request = {};
    if ( this.orderTarget < 0 ) {
       this.orderTarget = 0;
    } else if ( this.orderTarget > 100 ) {
       this.orderTarget = 100;
    }
    request['target'] = this.orderTarget.toString();
    request['window'] = this.windowSize.toString();
    request['updateFrequency'] = this.orderUpdates.toString();

    this.restService.setOrderSimControls(request).subscribe((data) => {
      console.log(data);
      this.orderTarget = data;
    });
    this.restService.getOrderTargetAndSimDelay().subscribe((data) => {
      if ( this.orderTarget > 0 && data.delay > 0 ) {
        this.createOrderManually = false;
      } else {
        this.createOrderManually = true;
      }
    });

  }

  nextOrder() {
    console.log('>>>>>>>>>>>>>nextOrder called');

    this.restService.createOrder().subscribe((data) => {
   //   console.log(data);
      //this.date = data.substr(0,10);
    });

   // this.getActiveVoyages();
  }

  selectedOrder($event, row?: Order) {

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
checkboxLabel(row?: Order): string {

  if (!row) {
    return `${this.isAllSelected() ? 'select' : 'deselect'} all`;
  }

  return `${this.selection.isSelected(row) ? 'deselect' : 'select'} all`;
}
public doFilter = (value: string) => {
  this.dataSource.filter = value.trim().toLocaleLowerCase();
}

// Get Uniqu values from columns to build filter
getFilterObject(fullObj, key) {
  const uniqChk = [];
  fullObj.filter((obj) => {
    if (!uniqChk.includes(obj[key])) {
      uniqChk.push(obj[key]);
    }
    return obj;
  });
  return uniqChk;
}
// Called on Filter change
filterChange(filter, event) {
  //let filterValues = {}
  this.filterValues[filter.columnProp] = event.target.value.trim().toLowerCase()
  this.dataSource.filter = JSON.stringify(this.filterValues)
}
// Custom filter method fot Angular Material Datatable
createFilter() {
let filterFunction = function (data: any, filter: string): boolean {
  let searchTerms = JSON.parse(filter);
  let isFilterSet = false;
  for (const col in searchTerms) {
    if (searchTerms[col].toString() !== '') {
      isFilterSet = true;
    } else {
      delete searchTerms[col];
    }
  }

//  console.log(searchTerms);

  let nameSearch = () => {
    let found = false;
    if (isFilterSet) {
      for (const col in searchTerms) {
        searchTerms[col].trim().toLowerCase().split(' ').forEach(word => {
          if (data[col].toString().toLowerCase().indexOf(word) != -1 && isFilterSet) {
            found = true
          }
        });
      }
      return found
    } else {
      return true;
    }
  }
  return nameSearch()
}
return filterFunction
}


// Reset table filters
resetFilters() {
this.filterValues = {}
this.filterSelectObj.forEach((value, key) => {
  value.modelValue = undefined;
})
this.dataSource.filter = "";
}
  private getTodos(request) {

  }

  nextPage(event: PageEvent) {
    const request = {};
    console.log("PageEvent pageIndex:"+event.pageIndex.toString()+" Size:"+event.pageSize.toString()+" Paginator Index:"+      this.paginator.pageIndex);
    request['page'] = event.pageIndex.toString();
    request['size'] = event.pageSize.toString();
    this.restService.nextPage(request).subscribe((data) => {
      console.log(data);

    });
  }
}
