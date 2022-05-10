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
package com.ibm.research.kar.reefer.client.orderdriver.model;

import java.util.Objects;

public class FutureVoyage {
   String id;
   int daysBefore;
   int maxCapacity;
   int freeCapacity;
   int orderSize;
   int utilization;

   public FutureVoyage(String id, int daysBefore, int maxCapacity, int freeCapacity, int orderSize, int utilization) {
      this.id = id;
      this.daysBefore = daysBefore;
      this.maxCapacity = maxCapacity;
      this.freeCapacity = freeCapacity;
      this.orderSize = orderSize;
      this.utilization = utilization;
   }
   public String getId() {
      return id;
   }
   public int getDaysBefore() {
      return daysBefore;
   }

   public int getMaxCapacity() {
      return maxCapacity;
   }

   public int getFreeCapacity() {
      return freeCapacity;
   }

   public int getOrderSize() {
      return orderSize;
   }

   public int getUtilization() {
      return utilization;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FutureVoyage that = (FutureVoyage) o;
      return daysBefore == that.daysBefore && maxCapacity == that.maxCapacity && freeCapacity == that.freeCapacity && orderSize == that.orderSize && utilization == that.utilization;
   }

   @Override
   public int hashCode() {
      return Objects.hash(daysBefore, maxCapacity, freeCapacity, orderSize, utilization);
   }

   @Override
   public String toString() {
      return "FutureVoyage{" +
              "daysBefore=" + daysBefore +
              ", maxCapacity=" + maxCapacity +
              ", freeCapacity=" + freeCapacity +
              ", orderSize=" + orderSize +
              ", utilization=" + utilization +
              '}';
   }
}
