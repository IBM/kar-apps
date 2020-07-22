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

const httpOptions = {
  headers: new HttpHeaders({ 'Content-Type': 'application/json' })
};

@Injectable({
  providedIn: 'root'
})
export class RestService {
  schedule:ShipSchedule[] = [];

  private REST_API_SERVER = GlobalConstants.restServerUrl; 
  constructor(private httpClient: HttpClient) { }
/*
  public sendGetRequest(){
    return this.httpClient.get(this.REST_API_SERVER);
  }
  */
 /*
  public sendGetRequest(){
    console.log('sendGetRequest Called');
    let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
    //let options = new RequestOptions({ headers: headers, method: 'get' });
    //Access-Control-Allow-Origin: *
    const options = { header: headers, params: new HttpParams({fromString: "name=Danny"}) };
    this.httpClient.get(this.REST_API_SERVER, options).pipe(retry(3), catchError(this.handleError))
    .map((response : Response) => response.json())
    .subscribe((data) => {
      this.schedule.push(data);
    });

  console.log('sendGetRequest Completed');

       // Add safe, URL encoded_page parameter
   //    const options = { params: new HttpParams({fromString: "name=Danny"}) };
   //    return this.httpClient.get(this.REST_API_SERVER, options).pipe(retry(3), catchError(this.handleError));
     }
*/
/*
     public getShippingSchedule : Observable<ShipSchedule> {
      console.log('sendGetRequest Called');
      let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
      //let options = new RequestOptions({ headers: headers, method: 'get' });
      //Access-Control-Allow-Origin: *
      const options = { header: headers, params: new HttpParams({fromString: "name=Danny"}) };

      return this.httpClient.get(this.REST_API_SERVER, options).pipe(retry(3), catchError(this.handleError))
      .subscribe(
      );
     // .map((response : Response) => response.json()));
     }
*/
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
  return this.httpClient.post<OrderProperties>(this.REST_API_SERVER+'/orders',order,options).pipe(retry(3), catchError(this.handleError));
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
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams({}) };

  return this.httpClient.get<Voyage[]>(this.REST_API_SERVER+'/voyage/active', options).pipe(retry(3), catchError(this.handleError));

}
getAllOrders()  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams() };

  return this.httpClient.get<Order[]>(this.REST_API_SERVER+'/orders', options).pipe(retry(3), catchError(this.handleError));

}
getReefers( inPort: string)  {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams().set('port',inPort) };

  return this.httpClient.get<Reefer[]>(this.REST_API_SERVER+'/reefers/'+inPort, options).pipe(retry(3), catchError(this.handleError));

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
setAutoMode(delayTime: number) {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  const body : Delay = {
    delay: delayTime

  }
  return this.httpClient.post<string>(this.REST_API_SERVER+'/simulator/automode',body, options).pipe(retry(3), catchError(this.handleError));

}
setManualMode() {
  let headers = new Headers({ 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  const options = { header: headers, params: new HttpParams()};
  return this.httpClient.post<string>(this.REST_API_SERVER+'/simulator/manualmode', options).pipe(retry(3), catchError(this.handleError));

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
