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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class OrderStats {
   private AtomicLong dispatched = new AtomicLong();
   private AtomicLong accepted = new AtomicLong();
   private AtomicLong failed = new AtomicLong();
   private AtomicLong booked = new AtomicLong();
   private AtomicLong latency = new AtomicLong();
   private AtomicLong statCount = new AtomicLong();
   private StandardDeviation std = new StandardDeviation();
   public long getAccepted() {
      return accepted.get();
   }

   public void incrementAccepted() {
      this.accepted.incrementAndGet();
   }

   public long getDispatched() {
      return dispatched.get();
   }

   public void incrementDispatched() {
      this.dispatched.incrementAndGet();
   }

   public long getFailed() {
      return failed.get();
   }

   public void incrementFailed() {
      this.failed.incrementAndGet();
   }

   public long getBooked() {
      return booked.get();
   }

   public void incrementBooked() {
      this.booked.incrementAndGet();
   }

   public double getMeanLatency() {
      return std.mean();
   }
   public double getStdLatency() {
      return std.get();
   }

   public void incrementLatency(long latency) {
      this.latency.addAndGet(latency);
      std.addLatencyDataPoint((double)latency);
   }
   private class StandardDeviation {
      //List<Double> latencyList = new LinkedList<>();
      double standardDev = 0.0;
      double latencySum;
      long statCount = 0;
      double newMean = 0.0;
      double oldMean = 0.0;
      double newStd = 0.0;
      double oldStd = 0.0;
      protected void addLatencyDataPoint(double latency) {
         synchronized(this) {
            statCount++;
            if (statCount == 1) {
               newMean = oldMean = latency;
            } else {
               newMean = oldMean + (latency - oldMean) / statCount;
               newStd = oldStd + (latency - oldMean)*(latency - newMean);

               oldStd = newStd;
               oldMean = newMean;
            }
         }
      }
      protected double mean() {
         synchronized (this) {
            return statCount > 0 ? newMean : 0.0;
         }
      }

      protected synchronized double get() {
         return Math.sqrt(newStd/(statCount-1));


/*
         standardDev = standardDev + Math.pow((latency.get() - mean()), 2);
         double sq = standardDev /statCount;
         return Math.sqrt(sq);

 */
      }

   }
}
