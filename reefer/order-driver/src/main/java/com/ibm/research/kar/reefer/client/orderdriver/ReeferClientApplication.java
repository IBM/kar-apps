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
package com.ibm.research.kar.reefer.client.orderdriver;

import com.ibm.research.kar.reefer.client.orderdriver.json.RouteJsonSerializer;
import com.ibm.research.kar.reefer.client.orderdriver.model.Route;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAutoConfiguration
@EnableScheduling
@ComponentScan("com.ibm.research.kar")
@SpringBootApplication
public class ReeferClientApplication implements ApplicationRunner {
   @Autowired
   private Environment environment;

   public static void main(String[] args) {
      SpringApplication.run(ReeferClientApplication.class, args);
   }

   @Override
   public void run(ApplicationArguments args) throws Exception {
      if ( args.containsOption("help")) {
         System.out.println("java -jar -Dserver.port=<driver port> reefer-kar-order-driver.jar <OPTIONS>");
         System.out.println("OPTIONS:\n --route=<route>| REQUIRED, example: --route=Tanger-Med-MA:Savannah-US\n" +
                 " --orderTimeout=<order timeout>| OPTIONAL, default=3 minutes\n" +
                 " --updatesPerDay=<number of orders per day>| OPTIONAL, default=3\n" +
                 " --orderTarget=<order target percentage>| OPTIONAL, default=75\n\n");
         System.exit(0);
      }

      System.out.println("Target URL:" + environment.getProperty("url"));
      OrderDriver driver = new OrderDriver(environment.getProperty("url"));

      if ( args.containsOption("getRoutes")) {
         int i=0;
         for( Route r : driver.getRoutes() ) {
            System.out.println("["+i+"] "+r.getOriginPort()+"-"+r.getDestinationPort());
            i++;
         }
         System.exit(0);
      }

      if ( !args.containsOption("route")) {
         System.out.println("Missing --route=<String>");
         System.exit(-1);
      }
      driver.addRoute(args.getOptionValues("route").get(0));

      if ( args.containsOption("updatesPerDay")) {
         driver.updatesPerDay(args.getOptionValues("updatesPerDay").get(0));
      }
      if ( args.containsOption("orderTarget")) {
         driver.orderTarget(args.getOptionValues("orderTarget").get(0));
      }
      if ( args.containsOption("orderTimeout")) {
         driver.orderTimeout(args.getOptionValues("orderTimeout").get(0));
      }

      driver.start();
   }

}
