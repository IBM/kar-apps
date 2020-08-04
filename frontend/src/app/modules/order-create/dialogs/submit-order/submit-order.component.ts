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
