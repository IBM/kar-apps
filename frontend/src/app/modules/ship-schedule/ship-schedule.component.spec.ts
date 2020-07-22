import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ShipScheduleComponent } from './ship-schedule.component';

describe('ShipScheduleComponent', () => {
  let component: ShipScheduleComponent;
  let fixture: ComponentFixture<ShipScheduleComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ShipScheduleComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ShipScheduleComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
