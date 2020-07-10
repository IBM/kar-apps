package com.ibm.research.kar.reeferserver;

import com.ibm.research.kar.reefer.common.time.TimeUtils;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReeferServerApplication {
	public static void main(final String[] args) {
		SpringApplication.run(ReeferServerApplication.class, args);
		System.out.println("UTC:     " + TimeUtils.getInstance().getStartDate());
	}

}
