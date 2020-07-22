import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ShipViewComponent } from './ship-view.component';

describe('ShipViewComponent', () => {
  let component: ShipViewComponent;
  let fixture: ComponentFixture<ShipViewComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ShipViewComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ShipViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
