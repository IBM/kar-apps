import { TestBed } from '@angular/core/testing';

import { ReefersService } from './reefers.service';

describe('ReefersService', () => {
  let service: ReefersService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ReefersService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
