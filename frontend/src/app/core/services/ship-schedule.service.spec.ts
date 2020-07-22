import { TestBed } from '@angular/core/testing';

import { ShipScheduleService } from './ship-schedule.service';

describe('ShipScheduleService', () => {
  let service: ShipScheduleService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ShipScheduleService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
