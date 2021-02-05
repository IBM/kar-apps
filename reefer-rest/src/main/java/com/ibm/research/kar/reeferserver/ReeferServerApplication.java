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

package com.ibm.research.kar.reeferserver;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.reefer.common.Constants;
import com.ibm.research.kar.reefer.common.time.TimeUtils;

import com.ibm.research.kar.reefer.model.Route;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.service.ScheduleService;
import com.ibm.research.kar.reeferserver.service.VoyageService;
import com.ibm.research.kar.reeferserver.service.TimeService;
import com.ibm.research.kar.reeferserver.scheduler.ShippingScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.annotation.PostConstruct;
import javax.json.Json;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@EnableAutoConfiguration
@ComponentScan("com.ibm.research.kar")
@EnableJpaRepositories("com.ibm.research.kar.reeferserver.repository.*")
@EntityScan("com.ibm.research.kar.reefer.model.*")  
@SpringBootApplication
public class ReeferServerApplication {
	@Autowired
	private ScheduleService shipScheduleService;
	@Autowired
	private VoyageService voyageService;
	@Autowired
	private TimeService timeService;
	public static void main(final String[] args) {

		SpringApplication.run(ReeferServerApplication.class, args);
		System.out.println("UTC:     " + TimeUtils.getInstance().getStartDate());
	}
	@PostConstruct
	public void init() {
		// load routes
		shipScheduleService.getRoutes();
		// The schedule base date is a date when the schedule was generated. It is saved persistently and
		// only exists if the REST service process stops.
		Optional<Instant> scheduleBaseDate = timeService.recoverDate(Constants.SCHEDULE_BASE_DATE_KEY);
		if ( scheduleBaseDate.isPresent()) {
			// recover current date which is persisted on every date change
			Optional<Instant> date = timeService.recoverDate(Constants.CURRENT_DATE_KEY);
			// initialize Singleton TimeUtils
			Instant currentDate = TimeUtils.getInstance(date.get()).getCurrentDate();
			System.out.println("ReeferServerApplication.init() - Restored Current Date:"+currentDate);

			shipScheduleService.generateShipSchedule(scheduleBaseDate.get());
			voyageService.restoreActiveVoyageOrders(shipScheduleService.getActiveSchedule());
		} else {
			Instant currentDate = TimeUtils.getInstance().getCurrentDate();
			System.out.println("ReeferServerApplication.init() - Current Date:"+currentDate);
			timeService.saveDate(currentDate, Constants.SCHEDULE_BASE_DATE_KEY);
			timeService.saveDate(currentDate, Constants.CURRENT_DATE_KEY);
			shipScheduleService.generateShipSchedule();
		}
	}
}
