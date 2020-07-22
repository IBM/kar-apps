import { Route } from '@angular/compiler/src/core';

export interface Voyage {
  id: string;
  route: Route;
  sailDateObject: string;
  sailDate: string;
  /*
  shipName: string;
  originPort: string;
  destinationPort: string;
  departureDate: string;
  transitTime: number;
  freeCapacity: number;
  */
}
