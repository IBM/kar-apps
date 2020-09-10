package com.ibm.research.kar.reeferserver.controller;

import java.util.List;

import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Reefer;
import com.ibm.research.kar.reefer.model.ReeferStats;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.model.ShippingSchedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class GuiController {
    @Autowired
    private SimpMessagingTemplate template;


    @GetMapping("/notify")
    public String getNotification() {

         // Push notifications to front-end
        template.convertAndSend("/topic/notification", "Hello");

        return "Notifications successfully sent to Angular !";
    }
    public void send(String msg) {
        template.convertAndSend("/topic/notification", "Hello");
    }

    public void sendReefersUpdate(List<Reefer> reefers) {
        template.convertAndSend("/topic/reefers", reefers);
    }
    public void sendActiveVoyageUpdate(List<Voyage> voyages, String currentDate) {
        ShippingSchedule schedule = new ShippingSchedule(voyages, currentDate);
        template.convertAndSend("/topic/voyages", schedule); 
     //   updateInTransitOrderCount(voyages.size());
    }
    public void sendOrderUpdate(Order order) {
        
        template.convertAndSend("/topic/orders", order); 
    }
    public void updateInTransitOrderCount(int orderCount) {
        
        template.convertAndSend("/topic/orders/intransit", orderCount); 
    }
    public void updateFutureOrderCount(int orderCount) {
        
        template.convertAndSend("/topic/orders/future", orderCount); 
    }
    public void updateSpoiltOrderCount(int orderCount) {
        
        template.convertAndSend("/topic/orders/spoilt", orderCount); 
    }
    

    public void updateReeferStats(ReeferStats stats) {
        
        template.convertAndSend("/topic/reefers/stats", stats); 
    }
}