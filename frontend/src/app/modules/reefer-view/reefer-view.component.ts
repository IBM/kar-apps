import { Component, OnInit, ViewChild, ViewChildren, QueryList } from '@angular/core';
import { Port } from 'src/app/core/models/port';
import { MatDialog } from '@angular/material/dialog';
import { RestService } from 'src/app/core/services/rest.service';
import { MatTableDataSource } from '@angular/material/table';
import { SelectionModel } from '@angular/cdk/collections';
import { MatPaginator } from '@angular/material/paginator';
import { Reefer } from 'src/app/core/models/reefer';
import { MatSort } from '@angular/material/sort';
import { SocketService } from 'src/app/core/services/socket.service';

@Component({
  selector: 'app-reefer-view',
  templateUrl: './reefer-view.component.html',
  styleUrls: ['./reefer-view.component.scss']
})
export class ReeferViewComponent implements OnInit {
  selectedPort : string;
  ports: Port[] = [];
  reefers: Reefer[] = [];
  portsDataSource = new MatTableDataSource(this.ports);
  reeferDataSource = new MatTableDataSource(this.reefers);
  portTableColumns: string[] = [ 'port', 'position', 'reeferCnt', 'onMaintenance', 'add'];
  reeferTableColumns: string[] = [ 'reeferId','port',  'position', 'maxCapacity', 'freeCapacity', 'status', 'loadingStatus'];
  filterValues = {};
  filterSelectObj = [];
  messageList:  string[] = [];
  portSelection = new SelectionModel<Port>(false, []);
  reeferSelection = new SelectionModel<Reefer>(false, []);
  //@ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
  @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
  @ViewChild(MatSort, {static: true}) sort: MatSort;
  //@ViewChildren(MatPaginator) paginator = new QueryList<MatPaginator>();


  constructor(private dialog: MatDialog, private restService: RestService, private webSocketService : SocketService ) {
// Object to create Filter for
		// Open connection with server socket
    let stompClient = this.webSocketService.connect();
    //console.log('AppComponent - connected socket');
    stompClient.connect({}, frame => {
      console.log('ReeferView - connected socket');
  // Subscribe to notification topic
        stompClient.subscribe('/topic/reefers', (event:any) => {
          if ( event.body) {
            this.reefers = JSON.parse(event.body);
            console.log('::::::'+this.reefers);
            this.reeferDataSource.data = this.reefers;

          }

        })
    });

  }


  ngOnInit(): void {
   /// this.portsDataSource.paginator = this.paginator.toArray()[0];
    this.reeferDataSource.paginator = this.paginator;
    this.reeferDataSource.sort = this.sort;
/*
    this.socketService.getMessages()
    .subscribe((message: string) => {
      console.log(message);
      this.messageList.push(message);
    });
    */
    /*
    this.restService.getAllPorts().subscribe((data) => {
      console.log(data);

      this.portsDataSource.data = data;
      });
*/
this.restService.getAllReefers().subscribe((data) => {
  console.log(">>>>>>>>>"+data);

  this.reeferDataSource.data = data;
  });

  }

  public doFilter = (value: string) => {
    this.reeferDataSource.filter = value.trim().toLocaleLowerCase();
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
    this.reeferDataSource.filter = JSON.stringify(this.filterValues)
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
  this.reeferDataSource.filter = "";
}

  addPortReefers(port : any) {
    console.log("Add Port Reefers - port:"+port.name);
    this.restService.addReefers(port.name).subscribe((data) => {
      console.log(data);

      this.portsDataSource.data = data;
      this.onRowClicked(port);
      });
  }
  onRowClicked(reefer: any): void {
    console.log(reefer);
    this.selectedPort = reefer.port;
    this.restService.getReefers(reefer.port).subscribe((data) => {
      console.log(data);

      this.reeferDataSource.data = data;
      });

  }


}
