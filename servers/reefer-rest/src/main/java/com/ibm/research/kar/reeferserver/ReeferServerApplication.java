package com.ibm.research.kar.reeferserver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReeferServerApplication {
    private static SimpleDateFormat simpleDateFormat = 
		  new SimpleDateFormat("MM/dd/yyyy");
		  
	private static Date startDate;
	private static Date currentDate;

	private static TimeZone timeZone = TimeZone.getTimeZone("UTC");
	private static Calendar calendar = Calendar.getInstance(timeZone);

	public static void main(String[] args) {
		SpringApplication.run(ReeferServerApplication.class, args);
		setTime();
	}
	private static void setTime() {
//		TimeZone timeZone = TimeZone.getTimeZone("UTC");
//        Calendar calendar = Calendar.getInstance(timeZone);
    
		simpleDateFormat.setTimeZone(timeZone);
		currentDate = startDate = calendar.getTime();
		System.out.println("UTC:     " + simpleDateFormat.format(startDate));

	}

	public static Date getStartDate() {
		return startDate;
	}
	public static Date getCurrentDate() {
		return currentDate;
	}
	public static void advanceDate(int days) {
        calendar.setTime(currentDate ); 
		calendar.add(Calendar.DATE, days);
		currentDate = calendar.getTime();
	}
}
