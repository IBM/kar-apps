import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, retry } from 'rxjs/operators';
import { HttpErrorResponse  } from "@angular/common/http";
import { ShipSchedule } from '../models/ship-schedule';
import { Route } from '../models/route';
import { OrderProperties } from '../models/order-properties';
import { Order } from '../models/order';
import { Port } from '../models/port';
import { Reefer } from '../models/reefer';
import { Addreefers } from '../models/addreefers';
import { Fleet } from '../models/fleet';
import { GlobalConstants } from 'src/app/shared/global-constants';
import { Voyage } from '../models/voyage';
import { Delay } from '../models/delay';
import { VoyagesQuery } from '../models/voyages-query';
import { OrderTarget } from '../models/order-target';
import { DelayTarget } from '../models/delay-target';
import { OrderStats } from '../models/order-stats';
import { OrderSimControls } from '../models/order-sim-controls';
import { ReeferStats } from '../models/reefer-stats';
import {  ReeferSimControls } from '../models/reefer-sim-controls';
import { Process } from 'src/typings';
import { stringToKeyValue } from '@angular/flex-layout/extended/typings/style/style-transforms';

const httpOptions = {
  headers: new HttpHeaders({ 'Content-Type': 'application/json' })
};

@Injectable({
  providedIn: 'root'
})
export class RestService {
  schedule:ShipSchedule[] = [];
  private REST_API_SERVER = GlobalConstants.restServerUrl; 
  constructor(private httpClient: HttpClient) { 
}

  sendGetRequest() : ShipSchedule[] {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams({}) };

   this.httpClient.get<ShipSchedule[]>(this.REST_API_SERVER+'/schedules', options).pipe(retry(3), catchError(this.handleError))
  .subscribe(
    res => console.log(res),
    res => {
      this.schedule.push(res);
    }


    );
    console.log(this.schedule);
    return this.schedule;

}
saveOrder( order: OrderProperties)  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams({}) };
  console.log("rest.service.ts - saveOrder()");
  return this.httpClient.post<OrderProperties>(this.REST_API_SERVER+'/orders',order,options).pipe(retry(3), catchError(this.handleError));
}


createOrder() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};

  return this.httpClient.post<string>(this.REST_API_SERVER+'/simulator/createorder',options).pipe(retry(3), catchError(this.handleError));

}
getSchedule()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams({}) };

  return this.httpClient.get<ShipSchedule[]>(this.REST_API_SERVER+'/schedules', options).pipe(retry(3), catchError(this.handleError));

}
getRoutes()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams({}) };

  return this.httpClient.get<Route[]>(this.REST_API_SERVER+'/routes', options).pipe(retry(3), catchError(this.handleError));

}
getActiveVoyages()  {
  console.log(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>> REEFER_REST_HOST:"+this.REST_API_SERVER);
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams({}) };

  return this.httpClient.get<Voyage[]>(this.REST_API_SERVER+'/voyage/active', options).pipe(retry(3), catchError(this.handleError));

}
getMatchingVoyages(originPort: string, destinationPort: string, date: string) {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  const body : VoyagesQuery = {
    origin : originPort,
    destination : destinationPort,
    departureDate : date
  }
  return this.httpClient.post<Voyage[]>(this.REST_API_SERVER+'/voyage/matching',body, options).pipe(retry(3), catchError(this.handleError));

}

getAllOrders()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<Order[]>(this.REST_API_SERVER+'/orders', options).pipe(retry(3), catchError(this.handleError));

}
getActiveOrders()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<Order[]>(this.REST_API_SERVER+'/orders/list/active', options).pipe(retry(3), catchError(this.handleError));

}
getSpoiltOrders()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<Order[]>(this.REST_API_SERVER+'/orders/list/spoilt', options).pipe(retry(3), catchError(this.handleError));

}
getBookedOrders()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<Order[]>(this.REST_API_SERVER+'/orders/list/booked', options).pipe(retry(3), catchError(this.handleError));

}
getReefers( inPort: string)  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams().set('port',inPort) };

  return this.httpClient.get<Reefer[]>(this.REST_API_SERVER+'/reefers/'+inPort, options).pipe(retry(3), catchError(this.handleError));

}
getReeferStats()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<ReeferStats>(this.REST_API_SERVER+'/reefers/stats', options).pipe(retry(3), catchError(this.handleError));

}
getAllReefers()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<Reefer[]>(this.REST_API_SERVER+'/reefers', options).pipe(retry(3), catchError(this.handleError));

}
addReefers( inPort: string)  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  console.log("addReefers() --- port:"+inPort)

  const options = { header: headers, params: new HttpParams()}; //params : new HttpParams() };
  const body : Addreefers = {
    port: inPort,
    howMany: 2};

  return this.httpClient.post<Port[]>(this.REST_API_SERVER+'/reefers', body, options).pipe(retry(3), catchError(this.handleError));

}

advanceDateByOneDay() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  return this.httpClient.post<String>(this.REST_API_SERVER+'/time/nextDay', options).pipe(retry(3), catchError(this.handleError));
}
currentDate() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
 
  return this.httpClient.post<String>(this.REST_API_SERVER+'/time/currentDate', options).pipe(retry(3), catchError(this.handleError));
}
getAllPorts()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<Port[]>(this.REST_API_SERVER+'/ports', options).pipe(retry(3), catchError(this.handleError));

}
getFleets()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<Fleet[]>(this.REST_API_SERVER+'/fleets', options).pipe(retry(3), catchError(this.handleError));

}
setOrderTarget(request) {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });

  const params = request;
  return this.httpClient.post<String>(this.REST_API_SERVER+'/simulator/setsimordertarget',params).pipe(retry(3), catchError(this.handleError));

}
setOrderSimControls(request) {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const params = request;
  return this.httpClient.post<String>(this.REST_API_SERVER+'/simulator/setordersimcontrols',params).pipe(retry(3), catchError(this.handleError));
}
getOrderSimControls() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  return this.httpClient.get<OrderSimControls>(this.REST_API_SERVER+'/simulator/getordersimcontrols').pipe(retry(3), catchError(this.handleError));
}
getOrderTargetAndSimDelay() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  
  return this.httpClient.get<DelayTarget>(this.REST_API_SERVER+'/simulator/getdelayandtarget', options).pipe(retry(3), catchError(this.handleError));

}
getOrderStats() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  
  return this.httpClient.get<OrderStats>(this.REST_API_SERVER+'/orders/stats', options).pipe(retry(3), catchError(this.handleError));

}
getReeferControls() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  
  return this.httpClient.get<ReeferSimControls>(this.REST_API_SERVER+'/simulator/controls', options).pipe(retry(3), catchError(this.handleError));

}
updateReeferControls(request) {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const params = request;
  return this.httpClient.post<String>(this.REST_API_SERVER+'/simulator/controls/update', params).pipe(retry(1), catchError(this.handleError));

}
getOrderTarget() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  
  return this.httpClient.post<number>(this.REST_API_SERVER+'/simulator/getsimordertarget', options).pipe(retry(3), catchError(this.handleError));

}
getSimulatorDelay() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};

  return this.httpClient.post<number>(this.REST_API_SERVER+'/simulator/getdelay', options).pipe(retry(3), catchError(this.handleError));

}
setSimulatorDelay(delayTime: number) {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  const body : Delay = {
    delay: delayTime

  }
  return this.httpClient.post<string>(this.REST_API_SERVER+'/simulator/delay',body, options).pipe(retry(3), catchError(this.handleError));

}

nextPage(request) {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  //const options = { header: headers, params: new HttpParams() };
  const params = request;
  return this.httpClient.post<String>(this.REST_API_SERVER+'/orders/nextpage', params).pipe(retry(3), catchError(this.handleError));

}
generateAnomaly() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  
  const options = { header: headers, params: new HttpParams()};
  return this.httpClient.post<String>(this.REST_API_SERVER+'/simulator/reefer/anomaly', options).pipe(retry(3), catchError(this.handleError));

}
    handleError(error: HttpErrorResponse) {
    let errorMessage = 'Unknown error!';
    if (error.error instanceof ErrorEvent) {
      // Client-side errors
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Server-side errors
      errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
    }
    console.log('Error');
    window.alert(errorMessage);
    return throwError(errorMessage);
  }

}
