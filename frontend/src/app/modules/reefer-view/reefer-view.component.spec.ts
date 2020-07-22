import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ReeferViewComponent } from './reefer-view.component';

describe('ReeferViewComponent', () => {
  let component: ReeferViewComponent;
  let fixture: ComponentFixture<ReeferViewComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ReeferViewComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ReeferViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
