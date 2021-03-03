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

import { Component, OnInit, ViewChild } from '@angular/core';
import { RestService } from 'src/app/core/services/rest.service';
import { Ship } from 'src/app/core/models/ship';
import { MatTableDataSource } from '@angular/material/table';
import { Fleet } from 'src/app/core/models/fleet';
import { MatSort } from '@angular/material/sort';
import { MatPaginator } from '@angular/material/paginator';
import { trigger, state, style, transition, animate } from '@angular/animations';
import {MatHorizontalStepper} from '@angular/material/stepper';
@Component({
  selector: 'app-ship-view',
  templateUrl: './ship-view.component.html',
  styleUrls: ['./ship-view.component.scss'],
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({height: '0px', minHeight: '0'})),
      state('expanded', style({height: '*'})),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
  ],
})
export class ShipViewComponent implements OnInit {
  count : number = 0;
  progressbarValue = 0;
  ships: Ship[] = [];
  shipDataSource = new MatTableDataSource(this.ships);
  @ViewChild(MatPaginator, { static: true }) paginator: MatPaginator;
  @ViewChild(MatSort, {static: true}) sort: MatSort;
  @ViewChild(MatHorizontalStepper) stepper: MatHorizontalStepper;
  expandedElement:Ship | null;

  shipTableColumns: string[] = [ 'shipId', 'name', 'position', 'maxCapacity', 'freeCapacity', 'status'];
  constructor( private restService: RestService) { }

  ngOnInit(): void {
    this.restService.getFleets().subscribe((data) => {

      let fleet: Fleet[] = data;
      let ships: Ship[] = fleet[0].ships;
      console.log(ships);
      this.shipDataSource.data = ships;
      });
      //this.shipDataSource.data = this.getFleets()[0].ship;
      this.shipDataSource.sort = this.sort;
      this.shipDataSource.paginator = this.paginator;
  }
  complete() {
    this.stepper.selected.completed = true;
    this.stepper.selected.editable = false;
    this.stepper.next();
  }

  next() {
    this.stepper.next()
  }
  getFleets() : Fleet[]  {
    let fleets: Fleet[];

    this.restService.getFleets().subscribe((data) => {
      console.log(data);
      //return data;
      fleets = data;
     // this.shipDataSource.data = data[0].ship;
      });
      return fleets;
  }
  switchFleet() {

  }
  onRowClicked(ship: Ship): void {
    console.log(ship.name);
   // this.selectedPort = port.name;
    // this.restService.getReefers(port.name).subscribe((data) => {
    //   console.log(data);

    //   this.reeferDataSource.data = data;
    //   });

  }

  public doFilter = (value: string) => {
    this.shipDataSource.filter = value.trim().toLocaleLowerCase();
  }

  moveShip(event: Event) {
    this.count = this.count +1;;
    console.log("click "+this.count);
    this.progressbarValue += 10;
  }
  resetShipPosition(event: Event) {
    this.progressbarValue = 0;
  }
}
