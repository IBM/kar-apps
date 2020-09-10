import { Route } from '@angular/compiler/src/core';

export interface Voyage {
  id: string;
  route: Route;
  sailDateObject: string;
  sailDate: string;
  displayArrivalDate: string;
  orderCount: number;
  
}
