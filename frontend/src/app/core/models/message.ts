import {ShipSchedule} from './ship-schedule';
import {Action} from './action';
import { ShipScheduleComponent } from 'src/app/modules/ship-schedule/ship-schedule.component';


export interface Message {
  schedule?: ShipScheduleComponent;
  action?: Action;
}
