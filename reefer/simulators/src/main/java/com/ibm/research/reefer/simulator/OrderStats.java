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

package com.ibm.research.reefer.simulator;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

// Class for accumulating order statistics
// Method for computing running mean and standard deviation from
// "Note on a Method for Calculating Corrected Sums of Squares and Products"
// B. P. Welford, Pages 419-420 | Published online: 30 Apr 2012

// Outlier threshold can be explicitly set (t>0) or dynamically computed (t==0)
// latencies ignored for orders that failed
// missed order count represents 

public class OrderStats {
  private int successful_orders;
  private int failed_orders;
  private int missed_orders;
  private double oldM;
  private double newM;
  private double oldS;
  private double newS;
  private int max_latency;
  private int outliers;
  private double threshold;
  private int stat_count;

  public OrderStats(int t) {
    this.successful_orders = 0;
    this.failed_orders = 0;
    this.missed_orders = 0;
    this.oldM = 0.0;
    this.newM = 0.0;
    this.oldS = 0.0;
    this.newS = 0.0;
    this.max_latency = 0;
    this.outliers = 0;
    this.threshold = t;
    this.stat_count = 0;
  }

  public OrderStats(int s, int f, int m, double om, double nm, double os, double ns, int ml, int or, double t, int sc) {
    this.successful_orders = s;
    this.failed_orders = f;
    this.missed_orders = m;
    this.oldM = om;
    this.newM = nm;
    this.oldS = os;
    this.newS = ns;
    this.max_latency = ml;
    this.outliers = or;
    this.threshold = t;
    this.stat_count = sc;
  }

  // return true if latency is an outlier
  public boolean addSuccessful(int latency) {
    double thresh;
    synchronized (this) {
      successful_orders++;
      if (latency > this.max_latency) {
        this.max_latency = latency;
      }
      // threshold manually set
      if (this.threshold > 0) {
        thresh = this.threshold;
        if (latency > thresh) {
          outliers++;
          return true;
        }
      }
      // threshold is dynamic, but need at least 10 latencies to use the estimate
      else {
        thresh = ( 3*getMean() > getMean()+5*getStddev() ) ? 3*getMean() : getMean()+5*getStddev();
        if (latency > thresh && stat_count > 10) {
          outliers++;
          return true;
        }
      }
      stat_count++;
      if (stat_count == 1) {
        newM = oldM = latency;
        oldS = 0.0;
      }
      else {
        newM = oldM + (latency - oldM)/stat_count;
        newS = oldS + (latency - oldM)*(latency - newM);
        oldM = newM;
        oldS = newS;
      }
      return false;
    }
  }

  public void addFailed() {
    synchronized (this) {
      failed_orders++;
    }
  }

  public void addMissed(int num) {
    synchronized (this) {
      missed_orders += num;
    }
  }

  public int getSuccessful() {
    return this.successful_orders;
  }

  public int getFailed() {
    return this.failed_orders;
  }

  public int getMissed() {
    return this.missed_orders;
  }

  public double getMean() {
    return (stat_count > 0) ? newM : 0.0;
  }

  public double getVar() {
    return (stat_count > 1) ? newS/(stat_count - 1) : 0.0;
  }

  public double getStddev() {
    return Math.sqrt(getVar());
  }

  public int getMax() {
    return this.max_latency;
  }

  public int getOutliers() {
    return this.outliers;
  }

  public int getThreshold() {
    return (int) Math.round(this.threshold);
  }

  @Override
  public Object clone() {
    synchronized (this) {
      try {
        return (OrderStats) super.clone();
      } catch (CloneNotSupportedException e) {
        return new OrderStats(this.successful_orders, this.failed_orders, this.missed_orders,
                this.oldM, this.newM, this.oldS, this.newS, this.max_latency, this.outliers,
                this.threshold, this.stat_count);
      }
    }
  }

  // testit
  public static void main(String[] args) {
    List<String> keymap = Arrays.asList("successful","failed","missed");
    if ( args.length != 1 || ! keymap.contains(args[0])) {
      System.err.println("need arg:  successful|failed|missed");
      System.exit(1);
    }
    
    OrderStats os = new OrderStats(0);
    Random rng = new Random();
    for (int i=1; i<=100; i++) {
      switch (args[0]) {
        case "successful" :  os.addSuccessful((int) Math.round(rng.nextGaussian()*10+50));
        continue;
        case "failed" :  os.addFailed();
        continue;
        case "missed" :  os.addMissed(rng.nextInt(2));
        continue;
      }
    }

    Object oss = os.clone();
    switch (args[0]) {
      case "successful" :  
        System.out.println("n_good=" + ((OrderStats)oss).getSuccessful() + " mean="+ ((OrderStats)oss).getMean() +
                " stddev="+ ((OrderStats)oss).getStddev() + " max=" + ((OrderStats)oss).getMax());
        System.exit(0);
      case "failed" :
        System.out.println("n_failed=" + ((OrderStats)oss).getFailed());
        System.exit(0);
      case "missed" :
        System.out.println("n_missed=" + ((OrderStats)oss).getMissed());
        System.exit(0);
    }
  }
}
