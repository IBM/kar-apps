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
