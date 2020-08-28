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
//import {CdkDetailRowDirective } from 'src/app/shared/components/cdk-detail-row.directive';


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
  displayedColumns: string[] = ['select',  'id', 'customerId','status','product', 'productQty', 'voyageId'];//, 'origin', 'destination','sailDate', 'transitTime', 'voyageId', 'reeferIds'];
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

  autoSimButtonLabel: string = "Update";
  dataSource = new MatTableDataSource(this.orders);
 // isExpansionDetailRow = (i: number, row: Object) => row.hasOwnProperty('detailRow');
  public currentExpandedRow: any;
  //public expandRow: boolean = false;
  public expandedElement: boolean = true;
  @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
  //@ViewChild(MatSort) sort: MatSort;
  @ViewChild(MatSort, {static: true}) sort: MatSort;
  
  //@ViewChild(MatTable) table: MatTable<any>;

  //@Input() sorting: MatSortable;

  constructor(private dialog: MatDialog, private restService: RestService, private webSocketService : SocketService) { 
    this.restService.getOrderStats().subscribe((data) => {
      // console.log(data);
      this.inTransitOrders = data.inTransitOrderCount;
      this.futureOrders = data.futureOrderCount;
      this.spoiltOrders = data.spoiltOrderCount;
    });
    let stompClient = this.webSocketService.connect();
    console.log('OrderView - connected socket');
    stompClient.connect({}, frame => {
      console.log('OrderView - connected stompClient');

      stompClient.subscribe('/topic/orders/intransit', (event:any) => {
        if ( event.body) {
          let orderCount : number;
          console.log("-------- IntransitOrders Update:"+event.body);
          orderCount = JSON.parse(event.body);
          this.inTransitOrders = orderCount;
        }
      });
      stompClient.subscribe('/topic/orders/future', (event:any) => {
        if ( event.body) {
          let orderCount : number;
          console.log("-------- FutureOrders Update:"+event.body);
          orderCount = JSON.parse(event.body);
          this.futureOrders = orderCount;
        }
      });

  // Subscribe to notification topic
        stompClient.subscribe('/topic/orders', (event:any) => {
          if ( event.body) {
            let order: Order;
            console.log("-------- Paginator Index:"+this.paginator.pageIndex);

            this.dataSource.data.forEach(row => console.log(row.id));
            // Add the order to the HEAD only when we are on the 
            // first page of orders else just ignore this update
            if ( this.paginator.pageIndex == 0) {

              order = JSON.parse(event.body);

              // The following two lines of code are required
              // to add new order to the HEAD of the list
              const currentData = this.dataSource.data;
              currentData.unshift(order);

              //console.log('::::::'+order);
              this.dataSource.data = currentData;
              this.dataSource.sort = this.sort;
            }

          }

        });

    });
    

    
  }

  isExpansionDetailRow = (_, row: any) => row.hasOwnProperty('detailRow');
  explansionDetialRowCollection = new Array<any>();
/*
  public toggleDetailsRow(row: any): void {
    this.expandRow = this.explansionDetialRowCollection.includes(row);
    if(this.expandRow !== true) {
      this.explansionDetialRowCollection.push(row);
    } else {
      // let index = this.explansionDetialRowCollection.findIndex(idRow => idRow.name === row.element.name);
      let test = this.explansionDetialRowCollection[0].name;
      this.explansionDetialRowCollection.forEach( (item, index) => {
        if(item.position === row.position) this.explansionDetialRowCollection.splice(index, 1);
      });
      // this.explansionDetialRowCollection.splice(0, 1);
    }
  }
  */
  ngOnInit(): void {
   
     this.restService.getOrderTargetAndSimDelay().subscribe((data) => {
      console.log(">>>> Order Target from the Simulator delay:"+data.delay+" target:"+data.target);
     
      if ( data.target > 0 || data.delay > 0 ) {
        this.createOrderManually = true;
        console.log("++++++++++++++ Enable CreateOrder Button");
      }else {
        this.createOrderManually = false;
        console.log("++++++++++++++ Disable CreateOrder Button");
      }
      this.orderTarget = data.target;

      
    });
    this.restService.getOrderSimControls().subscribe((data) => {
      this.orderTarget = data.target;
      this.windowSize = data.window;
      this.orderUpdates = data.updateTarget;
    });
/*
    this.restService.getAllOrders().subscribe((data) => {
      console.log(data);
      this.dataSource.data = data;
    //this.dataSource =  new MatTableDataSource(data);
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;

     }


    );
    */
 //    this.dataSource.paginator = this.paginator;
 //    this.dataSource.sort = this.sort;

  }
  showInTransitOrders() {
    console.log("Click >>>>> showInTransitOrders()");
  }
  showFutureOrders() {
    console.log("Click >>>>> showFutureOrders()");
  }
  showSpoiltOrders() {
    console.log("Click >>>>> showSpoiltOrders()");
  }

  updateOrderSimulatorControlls(event: Event) {
    console.log("Click >>>>>"+event +" Order Target:"+this.orderTarget);
    const request = {};
    request['target'] = this.orderTarget.toString();
    request['window'] = this.windowSize.toString();
    request['updateFrequency'] = this.orderUpdates.toString();
  
    this.restService.setOrderSimControls(request).subscribe((data) => {
      console.log(data);
      if ( this.orderTarget == 0 ) {
        this.createOrderManually = true;
      } else {
        this.createOrderManually = false;
      }
    });
    
  }
  nextOrder() {
    console.log('>>>>>>>>>>>>>nextOrder called');
    
    this.restService.createOrder().subscribe((data) => {
      console.log(data);
      //this.date = data.substr(0,10);
    });
    
   // this.getActiveVoyages();
  }
  /*
  orderTargetChange(event: any ) {
    this.orderTarget = event.target.value;
    if (this.orderTarget == 0 ) {
      this.restService.setOrderTarget(0).subscribe((data) => {
        console.log(data);
      });
    }
  }
  */
  selectedOrder($event, row?: Order) {
  //  const numSelected = this.selection.selected.length;
  //  if ($event.checked) {
  //    console.log(row);
  //    this.saveOrder(this.selectedProduct, 1000, this.selectedOriginPort, this.selectedDestinationPort,row);
  //  }

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

  console.log(searchTerms);

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
    /*
    this.loading = true;
    this.todoService.listTodos(request)
      .subscribe(data => {
        this.todos = data['content'];
        this.totalElements = data['totalElements'];
        this.loading = false;
      }, error => {
        this.loading = false;
      });
      */
  }
 
  nextPage(event: PageEvent) {
    const request = {};
    console.log("PageEvent pageIndex:"+event.pageIndex.toString()+" Size:"+event.pageSize.toString()+" Paginator Index:"+      this.paginator.pageIndex);
    request['page'] = event.pageIndex.toString();
    request['size'] = event.pageSize.toString();
    this.restService.nextPage(request).subscribe((data) => {
      console.log(data);
      //this.date = data.substr(0,10);
    });
  }
}
