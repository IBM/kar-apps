package com.ibm.research.kar.reefer.common.time;

import java.time.Instant;
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
}