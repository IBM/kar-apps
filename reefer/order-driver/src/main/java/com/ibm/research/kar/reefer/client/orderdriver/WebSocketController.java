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
package com.ibm.research.kar.reefer.client.orderdriver;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class WebSocketController extends StompSessionHandlerAdapter {
   private StompSession session;
   OrderReplyMessageHandler messageHandler;
   private String endpoint;
   DayChangeHandler dateChangeHandler;

   public StompSession connect(String url, OrderReplyMessageHandler mh, DayChangeHandler dateChangeHandler) {
      messageHandler = mh;
      this.dateChangeHandler = dateChangeHandler;
      WebSocketClient webSocketClient = new StandardWebSocketClient();
      SockJsClient sockJsClient = new SockJsClient(List.of(new org.springframework.web.socket.sockjs.client.WebSocketTransport(webSocketClient)));
      WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
      // stompClient.setMessageConverter(new MappingJackson2MessageConverter());
      stompClient.setMessageConverter(new StringMessageConverter());

      try {
         InetAddress inetAddress = InetAddress.getLocalHost();
         long pid = ProcessHandle.current().pid();
         endpoint = inetAddress.getHostName() + ":" + pid;
         session = stompClient
                 .connect(url + "/socket", this)
                 .get();

         System.out.println(".... Got connection - endpoint:" + endpoint);

      } catch (InterruptedException | ExecutionException | UnknownHostException e) {
         System.out.println(" cause:" + e.getCause().getClass().getName() + " inner cause:" + e.getCause().getCause().getClass().getName());
         throw new RuntimeException(e);
      }
      return session;
   }

   public String getEndpoint() {
      if (endpoint == null) {
         throw new RuntimeException("WebsocketController - Endpoint is not set");
      }
      return "/topic/" + endpoint;
   }

   public String getHostAndIp() {
      return endpoint;
   }

   @Override
   public void afterConnected(StompSession stompSession, StompHeaders connectedHeaders) {
      // subscription for new day notifications
      stompSession.subscribe("/topic/time", new StompFrameHandler() {
         @Override
         public Type getPayloadType(StompHeaders headers) {
            return String.class;
         }

         @Override
         public void handleFrame(StompHeaders headers, Object payload) {
            dateChangeHandler.onDayAdvance(Instant.parse((String) payload));
         }
      });
      // subscription for all replies
      stompSession.subscribe(getEndpoint(), new StompFrameHandler() {
         @Override
         public Type getPayloadType(StompHeaders headers) {
            return String.class;
         }

         @Override
         public void handleFrame(StompHeaders headers, Object payload) {
            messageHandler.onMessage(payload);
         }
      });
   }

   @Override
   public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
      exception.printStackTrace();
   }
}
