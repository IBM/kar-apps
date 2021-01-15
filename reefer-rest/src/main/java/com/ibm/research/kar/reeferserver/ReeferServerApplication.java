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

import com.ibm.research.kar.reefer.common.time.TimeUtils;

import com.ibm.research.kar.reeferserver.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.annotation.PostConstruct;

@EnableAutoConfiguration
@ComponentScan("com.ibm.research.kar")
@EnableJpaRepositories("com.ibm.research.kar.reeferserver.repository.*")
@EntityScan("com.ibm.research.kar.reefer.model.*")  
@SpringBootApplication
public class ReeferServerApplication {
	@Autowired
	private ScheduleService shipScheduleService;

	public static void main(final String[] args) {

		SpringApplication.run(ReeferServerApplication.class, args);
		System.out.println("UTC:     " + TimeUtils.getInstance().getStartDate());
	}
	@PostConstruct
	public void init() {
		System.out.println("ReeferServerApplication.init()............");
		shipScheduleService.generateShipSchedule();
	}
}
