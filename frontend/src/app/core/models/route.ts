import { Ship } from './ship';

export interface Route {
   vessel: Ship;
   originPort: string;
   destinationPort: string;
   daysAtSea: number;
   daysAtPort: number;
}
