/*
 * Copyright IBM Corporation 2020,2021
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.research.kar.reefer.common.time;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class TimeUtils {
    private static Instant startDate;
	private static Instant currentDate;
    private static TimeUtils instance;

    private  TimeUtils() {
        if ( instance != null ) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
        currentDate = startDate = Instant.now();
    }
    public static TimeUtils getInstance(){
        if (instance == null){ //if there is no instance available... create new one
            instance = new TimeUtils();
        }
        return instance;
    }
	public Instant getStartDate() {
		return startDate;
	}

	public Instant getCurrentDate() {
		return currentDate;
	}

	public Instant advanceDate(final int daysIntoTheFuture) {
 	    currentDate = currentDate.plus(daysIntoTheFuture, ChronoUnit.DAYS);
		return currentDate;
    }
    public Instant futureDate(Instant date, long daysIntoTheFuture) {
        return date.plus(daysIntoTheFuture, ChronoUnit.DAYS);
    }
    public boolean isSameDay(Instant date1, Instant date2) {

        return date1.truncatedTo(ChronoUnit.DAYS).equals(date2.truncatedTo(ChronoUnit.DAYS));
    }
    public Instant getDateYearFrom(Instant date) {
        // return date 365 days from now
        return date.plus(365, ChronoUnit.DAYS);
    }
    public Instant getDateNyearsFromNow(Instant now, int years) {
        // return date 365 days from now
        return now.atOffset(ZoneOffset.UTC).plus(years, ChronoUnit.YEARS).toInstant();
    }
    public long getDaysBetween(String startDate, String endDate) {
        Instant sd = Instant.parse(startDate);
        Instant ed = Instant.parse(endDate);
        return getDaysBetween(sd, ed) ;
       
    }
    public long getDaysBetween(Instant startDate, Instant endDate) {
    
        return ChronoUnit.DAYS.between(startDate,endDate);
       
    }
}