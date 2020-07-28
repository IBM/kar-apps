import { Voyage } from './voyage';

export interface ShipSchedule {

  voyageId: string;
  position: string;
  name: string;
  origin: string;
  destination: string;
  sailDate: string;
  transitTime: number;
  freeCapacity: number;
}
