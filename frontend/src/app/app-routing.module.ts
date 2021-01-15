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

import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { DefaultComponent } from './layouts/default/default.component';
import { OrderCreateComponent } from './modules/order-create/order-create.component';
import { OrderViewComponent } from './modules/order-view/order-view.component';
import { ReeferViewComponent } from './modules/reefer-view/reefer-view.component';
import { ShipScheduleComponent } from './modules/ship-schedule/ship-schedule.component';
import { ShipViewComponent } from './modules/ship-view/ship-view.component';
import { SimulatorComponent } from './modules/simulator/simulator.component';


const routes: Routes = [{
  path: '',
  component: DefaultComponent,
  children: [{
    path: '',
    component: OrderCreateComponent
  }, {
    path: 'orders',
    component: OrderViewComponent
  }, {
    path: 'reefers',
    component: ReeferViewComponent
  }, {
    path: 'schedule',
    component: ShipScheduleComponent
  }, {
    path: 'ships',
    component: ShipViewComponent
  }, {
    path: 'simulator',
    component: SimulatorComponent
  }
]
}];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
