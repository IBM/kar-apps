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

import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { DefaultModule } from './layouts/default/default.module';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { SubmitOrderComponent } from './modules/order-create/dialogs/submit-order/submit-order.component';
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import { MatDialogModule } from "@angular/material/dialog";
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule} from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { OrderBookedDialogComponent } from './modules/order-create/dialogs/order-booked-dialog/order-booked-dialog.component';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
//import {  MatHorizontalStepper } from '@angular/material/stepper';
import { SocketIoModule, SocketIoConfig } from 'ngx-socket-io';
import { HashLocationStrategy, LocationStrategy } from '@angular/common';
import { SocketService } from './core/services/socket.service';
//const config: SocketIoConfig = { url: 'http://localhost:3000', options: {} };
import {MatProgressBarModule} from '@angular/material/progress-bar';

//declare var require: any;

const  RestServerURL= 'http://localhost:9000';

@NgModule({
  declarations: [
    AppComponent,
    SubmitOrderComponent,
    OrderBookedDialogComponent,
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    BrowserAnimationsModule,
    DefaultModule,
    MatCheckboxModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatProgressBarModule,

    //SocketIoModule.forRoot(config),
  ],
  providers: [{provide:SocketService, useClass: SocketService}, {provide: LocationStrategy, useClass: HashLocationStrategy}],
  bootstrap: [AppComponent]
})
export class AppModule { }
