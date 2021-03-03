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

import { Injectable } from '@angular/core';
import { Observable ,  BehaviorSubject ,  ReplaySubject } from 'rxjs';
//import { SocketService } from './socket.service';
import { ShipSchedule } from '../models/ship-schedule';
import { HttpClientModule, HttpClient } from '@angular/common/http';
import { RestService } from './rest.service';
//import { SSL_OP_ALL } from 'constants';

/*
const SHIPPING_SCHEDULE: ShipSchedule[] = [
  { position: 1, name: 'Abyss', origin: 'Elizabeth, US', destination: 'London, GB', sailDate: '20-May-2020', transitTime: 27, freeCapacity: 250},
  { position: 1, name: 'Atlantis', origin: 'Elizabeth, US', destination: 'London, GB', sailDate: '20-May-2020', transitTime: 27, freeCapacity: 200},
  { position: 1, name: 'Blue Moon', origin: 'Elizabeth, US', destination: 'London, GB', sailDate: '20-May-2020', transitTime: 27, freeCapacity: 25},
  { position: 1, name: 'Black Pearl', origin: 'Elizabeth, US', destination: 'London, GB', sailDate: '20-May-2020', transitTime: 27, freeCapacity: 1000},
  { position: 1, name: 'Santa Maria', origin: 'Elizabeth, US', destination: 'London, GB', sailDate: '20-May-2020', transitTime: 27, freeCapacity: 2530},
  { position: 1, name: 'Andrea Gail', origin: 'Elizabeth, US', destination: 'London, GB', sailDate: '20-May-2020', transitTime: 27, freeCapacity: 111},
  { position: 1, name: 'Victoria', origin: 'Elizabeth, US', destination: 'London, GB', sailDate: '20-May-2020', transitTime: 27, freeCapacity: 890},
  { position: 1, name: 'Trinidad', origin: 'Elizabeth, US', destination: 'London, GB', sailDate: '20-May-2020', transitTime: 27, freeCapacity: 30},

];
*/
@Injectable({
  providedIn: 'root'
})

export class ShipScheduleService {


  ss : ShipSchedule[] = [];

  constructor( private http: HttpClient, private rest: RestService) {}

  getShippingSchedule() {
     this.rest.getSchedule().subscribe( result => {
      this.ss = result;
    });
   console.log(this.ss);
   return this.ss;
  }

}
