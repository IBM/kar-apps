package com.ibm.research.reefer.simulator;

import java.util.Arrays;
import java.util.List;

// Class for accumulating order statistics
// Method for computing running mean and standard deviation from
// "Note on a Method for Calculating Corrected Sums of Squares and Products"
// B. P. Welford, Pages 419-420 | Published online: 30 Apr 2012

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

  public OrderStats() {
    this.successful_orders = 0;
    this.failed_orders = 0;
    this.missed_orders = 0;
    this.oldM = 0.0;
    this.newM = 0.0;
    this.oldS = 0.0;
    this.newS = 0.0;
    this.max_latency = 0;
    this.outliers = 0;
  }

  public OrderStats(int s, int f, int m, double om, double nm, double os, double ns, int ml, int or) {
    this.successful_orders = s;
    this.failed_orders = f;
    this.missed_orders = m;
    this.oldM = om;
    this.newM = nm;
    this.oldS = os;
    this.newS = ns;
    this.max_latency = ml;
    this.outliers = or;
  }

  public boolean addSuccessful(int latency) {
    synchronized (this) {
      if (latency > this.max_latency) {
        this.max_latency = latency;
      }
      double thresh = ( 3*getMean() > getMean()+5*getStddev() ) ? 3*getMean() : getMean()+5*getStddev(); 
      if (latency > thresh && successful_orders > 10) {
        outliers++;
        return true;
      }
      successful_orders++;
      if (successful_orders == 1) {
        newM = oldM = latency;
        oldS = 0.0;
      }
      else {
        newM = oldM + (latency - oldM)/successful_orders;
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
    return (successful_orders > 0) ? newM : 0.0;
  }

  public double getVar() {
    return (successful_orders > 1) ? newS/(successful_orders - 1) : 0.0;
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

  @Override
  public Object clone() {
    synchronized (this) {
      try {
        return (OrderStats) super.clone();
      } catch (CloneNotSupportedException e) {
        return new OrderStats(this.successful_orders, this.failed_orders, this.missed_orders,
                this.oldM, this.newM, this.oldS, this.newS, this.max_latency, this.outliers);
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
    
    OrderStats os = new OrderStats();
    for (int i=1; i<=10; i++) {
      switch (args[0]) {
        case "successful" :  os.addSuccessful(i);
        continue;
        case "failed" :  os.addFailed();
        continue;
        case "missed" :  os.addMissed(i);
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
