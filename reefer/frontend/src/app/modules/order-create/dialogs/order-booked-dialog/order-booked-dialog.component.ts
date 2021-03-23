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

import { Component, OnInit, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { OrderProperties } from 'src/app/core/models/order-properties';

@Component({
  selector: 'app-order-booked-dialog',
  templateUrl: './order-booked-dialog.component.html',
  styleUrls: ['./order-booked-dialog.component.scss']
})
export class OrderBookedDialogComponent implements OnInit {
  description: string;

  constructor( private dialogRef: MatDialogRef<OrderBookedDialogComponent>,
    @Inject(MAT_DIALOG_DATA) data) {
    console.log("order-booked-dialog - "+ data.orderId);
    if ( data.status === 'OK') {
       this.description = 'Your Order Has Been Booked - Order ID:'+data.orderId;
    } else {
       this.description =  'Order:'+data.orderId+' Booking Status:'+data.status+' '+data.msg;
    }


  }
  ngOnInit(): void {

  }
  close() {
    this.dialogRef.close();
  }
}

