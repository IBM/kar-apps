import { Reefer } from './reefer';

export interface Ship {
  id: string;
  name: string;
  position: number;
  progress: number;
  maxCapacity: number;
  freeCapacity : number;
  location : string;
  reefers: Array<Reefer>;
}
