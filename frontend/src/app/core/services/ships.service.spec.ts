import { TestBed } from '@angular/core/testing';

import { ShipsService } from './ships.service';

describe('ShipsService', () => {
  let service: ShipsService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ShipsService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
