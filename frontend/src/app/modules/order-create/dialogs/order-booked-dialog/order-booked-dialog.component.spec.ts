import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { OrderBookedDialogComponent } from './order-booked-dialog.component';

describe('OrderBookedDialogComponent', () => {
  let component: OrderBookedDialogComponent;
  let fixture: ComponentFixture<OrderBookedDialogComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ OrderBookedDialogComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(OrderBookedDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
