package com.ibm.research.kar.reeferserver.controller;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.OrderStats;
import com.ibm.research.kar.reefer.model.Reefer;
import com.ibm.research.kar.reefer.model.ReeferStats;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.model.ShippingSchedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class GuiController {
    @Autowired
    private SimpMessagingTemplate template;

    private AtomicInteger inTransitOrderCount = new AtomicInteger();
    private AtomicInteger futureOrderCount = new AtomicInteger();
    private AtomicInteger spoiltOrderCount = new AtomicInteger();
    private ReeferStats reeferStats;

    private AtomicBoolean valuesChanged = new AtomicBoolean();

    public GuiController() {
        TimerTask timerTask = new GuiUpdateTask();
        // running timer task as daemon thread. It updates
        // the GUI counts at regular intervals
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(timerTask, 0, 100);
    }

    public void sendReefersUpdate(List<Reefer> reefers) {
        template.convertAndSend("/topic/reefers", reefers);
    }

    public void sendActiveVoyageUpdate(List<Voyage> voyages, String currentDate) {
        ShippingSchedule schedule = new ShippingSchedule(voyages, currentDate);
        template.convertAndSend("/topic/voyages", schedule);
    }

    public void sendOrderUpdate(Order order) {

        template.convertAndSend("/topic/orders", order);
    }

    public void updateInTransitOrderCount(int orderCount) {
        inTransitOrderCount.set(orderCount);
        valuesChanged.set(true);
    }

    public void updateFutureOrderCount(int orderCount) {
        futureOrderCount.set(orderCount);
        valuesChanged.set(true);
    }

    public void updateSpoiltOrderCount(int orderCount) {
        spoiltOrderCount.set(orderCount);
        valuesChanged.set(true);
    }

    public void updateReeferStats(ReeferStats stats) {
        reeferStats = stats;
        valuesChanged.set(true);
       
    }

    private class GuiUpdateTask extends TimerTask {
        @Override
        public void run() {

            if (valuesChanged.get()) {
                OrderStats orderStats = new OrderStats(inTransitOrderCount.get(), futureOrderCount.get(),
                        spoiltOrderCount.get());
                System.out.println(
                        "GuiController.run()................................. Orders Spoilt:" + spoiltOrderCount.get());
                if ( orderStats != null ) {
                    template.convertAndSend("/topic/orders/stats", orderStats);
                }
                
                if ( reeferStats != null ) {
                    template.convertAndSend("/topic/reefers/stats", reeferStats);
                }
                valuesChanged.set(false);
            }

        }
    }
}