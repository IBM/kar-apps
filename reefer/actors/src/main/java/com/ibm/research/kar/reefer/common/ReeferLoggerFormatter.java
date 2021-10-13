package com.ibm.research.kar.reefer.common;/*
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

import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import java.util.logging.*;

public class ReeferLoggerFormatter {

   private static Map<String, String> env = System.getenv();
   public static Logger getFormattedLogger(String clzName) {
      Logger logger = Logger.getLogger(clzName);

      if (env.containsKey("LOG_TIMESTAMP") && env.get("LOG_TIMESTAMP") != null ) {
         Object o = env.get("LOG_TIMESTAMP");
         if ( Boolean.parseBoolean(env.get("LOG_TIMESTAMP")) ) {
            logger.setUseParentHandlers(false);

            SimpleFormatter sf = new SimpleFormatter() {
               private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

               @Override
               public synchronized String format(LogRecord lr) {
                  return String.format(format,
                          new Date(lr.getMillis()),
                          lr.getLevel().getLocalizedName(),
                          lr.getMessage()
                  );
               }
            };
            StdOutConsoleHandler sh = new StdOutConsoleHandler();
            sh.setFormatter(sf);
            logger.addHandler(sh);
         }
      }
      return logger;
   }
   public static class StdOutConsoleHandler extends ConsoleHandler {
      @Override
      protected void setOutputStream(OutputStream out) throws SecurityException {
         super.setOutputStream(System.out);
      }
   }
}