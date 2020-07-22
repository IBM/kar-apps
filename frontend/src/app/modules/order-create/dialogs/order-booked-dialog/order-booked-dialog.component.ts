import { Component, OnInit, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';

@Component({
  selector: 'app-order-booked-dialog',
  templateUrl: './order-booked-dialog.component.html',
  styleUrls: ['./order-booked-dialog.component.scss']
})
export class OrderBookedDialogComponent implements OnInit {
  description: string;

  constructor( private dialogRef: MatDialogRef<OrderBookedDialogComponent>,
    @Inject(MAT_DIALOG_DATA) data) {
     this.description = 'Your Order Has Been Booked - Order ID:'+data.orderId;
      }
  ngOnInit(): void {

  }
  close() {
    this.dialogRef.close();
  }
}

