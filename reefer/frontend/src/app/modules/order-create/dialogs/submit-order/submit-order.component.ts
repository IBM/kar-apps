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
import {MatDialog, MatDialogConfig, MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {FormBuilder, Validators, FormGroup} from "@angular/forms";
import { OrderProperties } from 'src/app/core/models/order-properties';
//import { OrderProperties }


@Component({
  selector: 'app-submit-order',
  templateUrl: './submit-order.component.html',
  styleUrls: ['./submit-order.component.scss']
})
export class SubmitOrderComponent implements OnInit {
  form: FormGroup;
  description: string;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<SubmitOrderComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any ) {
       this.description = 'Review Your Order And Submit';


    this.form = fb.group({
      customerId: [data.customerId, Validators.required],
      product: [data.product, Validators.required],
     productQty: [data.productQty, Validators.required],
     originPort: [data.originPort, Validators.required],
     destinationPort: [data.destinationPort,Validators.required],
     voyageId: [data.voyageId, Validators.required],
     shipName: [data.shipName, Validators.required],
      departure: [data.departure, Validators.required]
    });

}

  ngOnInit() {

  }

  save() {

    this.dialogRef.close(this.form.value);
  }

  close() {
    this.dialogRef.close();
  }
}
