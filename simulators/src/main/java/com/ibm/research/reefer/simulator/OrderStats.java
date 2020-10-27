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
  private double welford_oldM;
  private double welford_newM;
  private double welford_oldS;
  private double welford_newS;
  private int max_latency;

  public OrderStats() {
    this.successful_orders = 0;
    this.failed_orders = 0;
    this.missed_orders = 0;
    this.welford_oldM = 0.0;
    this.welford_newM = 0.0;
    this.welford_oldS = 0.0;
    this.welford_newS = 0.0;
    this.max_latency = 0;
  }

  public OrderStats(int s, int f, int m, double om, double nm, double os, double ns, int ml) {
    this.successful_orders = s;
    this.failed_orders = f;
    this.missed_orders = m;
    this.welford_oldM = om;
    this.welford_newM = nm;
    this.welford_oldS = os;
    this.welford_newS = ns;
    this.max_latency = ml;
  }

  public void addSuccessful(int latency) {
    synchronized (this) {
      successful_orders++;
      if (latency > this.max_latency) {
        this.max_latency = latency;
      }
      if (successful_orders == 1) {
        welford_newM = welford_oldM = latency;
      }
      else {
        welford_newM = welford_oldM + (latency - welford_oldM)/successful_orders;
        welford_newS = welford_oldS + (latency - welford_oldM)*(latency - welford_newM);
        welford_oldM = welford_newM;
        welford_oldS = welford_newS;
      }
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
    return (successful_orders > 0) ? welford_newM : 0.0;
  }

  public double getStddev() {
    return (successful_orders > 1) ? welford_newS/(successful_orders - 1) : 0.0;
  }

  public int getMax() {
    return this.max_latency;
  }

  @Override
  public Object clone() {
    synchronized (this) {
      try {
        return (OrderStats) super.clone();
      } catch (CloneNotSupportedException e) {
        return new OrderStats(this.successful_orders, this.failed_orders, this.missed_orders,
                this.welford_oldM, this.welford_newM, this.welford_oldS, this.welford_newS, this.max_latency);
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
