import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DefaultComponent } from './default.component';
import { OrderCreateComponent } from 'src/app/modules/order-create/order-create.component';
import { RouterModule } from '@angular/router';
import { OrderViewComponent } from 'src/app/modules/order-view/order-view.component'
import { ReeferViewComponent } from 'src/app/modules/reefer-view/reefer-view.component';
import { ShipScheduleComponent } from 'src/app/modules/ship-schedule/ship-schedule.component';
import { ShipViewComponent } from 'src/app/modules/ship-view/ship-view.component';
import { SimulatorComponent } from 'src/app/modules/simulator/simulator.component';
import { SharedModule} from 'src/app/shared/shared.module';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatDividerModule } from '@angular/material/divider';
import { FlexLayoutModule } from '@angular/flex-layout';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule} from '@angular/material/form-field';

import {MatInputModule} from '@angular/material/input';
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatSelectModule} from '@angular/material/select';
import { FormsModule, FormControl } from '@angular/forms';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatNativeDateModule } from '@angular/material/core';

@NgModule({
  declarations: [
    DefaultComponent,
    OrderCreateComponent,
    OrderViewComponent,
    ReeferViewComponent,
    ShipScheduleComponent,
    ShipViewComponent,
    SimulatorComponent
  ],
  imports: [
    CommonModule,
    RouterModule,
    SharedModule,
    MatSidenavModule,
    MatDividerModule,
    FlexLayoutModule,
    MatCardModule,
    MatFormFieldModule,
    MatDividerModule,
    MatInputModule,
    MatDatepickerModule,
    MatSelectModule,
    FormsModule,
    MatPaginatorModule,
    MatTableModule,
    MatNativeDateModule
  ],
  exports: [
    MatDatepickerModule,
    MatNativeDateModule
  ],
  providers: [ MatDatepickerModule ],
})
export class DefaultModule { }
