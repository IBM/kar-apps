import { Component, OnInit, ViewChild, AfterViewInit, Input, ContentChild } from '@angular/core';
import { Order } from 'src/app/core/models/order';
import { SelectionModel } from '@angular/cdk/collections';
import { MatPaginator } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { RestService } from 'src/app/core/services/rest.service';
import { MatTableDataSource, MatTable } from '@angular/material/table';
import {MatSort, MatSortable} from '@angular/material/sort';
import { animate, state, style, transition, trigger } from '@angular/animations';
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
  displayedColumns: string[] = ['select',  'orderId', 'status','product', 'productQty', 'voyageId'];//, 'origin', 'destination','sailDate', 'transitTime', 'voyageId', 'reeferIds'];
  orders: Order[] = [];
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

  constructor(private dialog: MatDialog, private restService: RestService) { }

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
    this.dataSource.sort = this.sort;
    this.restService.getAllOrders().subscribe((data) => {
      console.log(data);
      this.dataSource.data = data;
    //this.dataSource =  new MatTableDataSource(data);

     }


    );
 //    this.dataSource.paginator = this.paginator;
 //    this.dataSource.sort = this.sort;

  }

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
}
