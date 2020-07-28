import { Voyage } from './voyage';

export interface ActiveSchedule {
    voyages: Voyage[];
    currentDate: string;
  
  }