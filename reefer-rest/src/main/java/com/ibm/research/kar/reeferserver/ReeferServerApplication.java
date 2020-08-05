package com.ibm.research.kar.reeferserver;

import com.ibm.research.kar.reefer.common.time.TimeUtils;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
@EnableAutoConfiguration
@ComponentScan("com.ibm.research.kar")
@EnableJpaRepositories("com.ibm.research.kar.reeferserver.repository.*")
@EntityScan("com.ibm.research.kar.reefer.model.*")  
@SpringBootApplication
public class ReeferServerApplication {
	public static void main(final String[] args) {
		SpringApplication.run(ReeferServerApplication.class, args);
		System.out.println("UTC:     " + TimeUtils.getInstance().getStartDate());
	}

}
