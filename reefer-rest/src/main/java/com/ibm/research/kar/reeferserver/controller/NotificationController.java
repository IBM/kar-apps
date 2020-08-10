package com.ibm.research.kar.reeferserver.controller;

import java.util.List;

import com.ibm.research.kar.reefer.model.Order;
import com.ibm.research.kar.reefer.model.Reefer;
import com.ibm.research.kar.reefer.model.Voyage;
import com.ibm.research.kar.reeferserver.model.ShippingSchedule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
public class NotificationController {
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
    }
    public void sendOrderUpdate(Order order) {
        
        template.convertAndSend("/topic/orders", order); 
    }
}