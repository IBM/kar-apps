package com.ibm.research.kar.reeferserver.controller;

import java.util.List;

import com.ibm.research.kar.reeferserver.model.Reefer;

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

    // Initialize Notifications
    //private Notifications notifications = new Notifications(0);

    @GetMapping("/notify")
    public String getNotification() {

        // Increment Notification by one
        //notifications.increment();
        System.out.println("........ getNotification() called");
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
}