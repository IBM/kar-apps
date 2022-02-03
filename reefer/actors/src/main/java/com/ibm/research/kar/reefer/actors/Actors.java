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

package com.ibm.research.kar.reefer.actors;

import com.ibm.research.kar.Kar;
import com.ibm.research.kar.actor.ActorRef;

import javax.json.JsonValue;

public final class Actors {
   private ActorRef actor;
   private String method;
   private JsonValue value;

   private Actors(ActorRef actor, String method, JsonValue arg) {
      this.actor = actor;
      this.method = method;
      this.value = arg;
   }


   public interface Target {
      Method target(String targetType, String targetId);
   }
   public interface Method {
      Arg method(String name);
   }
   public interface Arg {
      Invoke arg(JsonValue value);
      Invoke arg();
   }
   public interface Invoke {
      void tell();
      JsonValue call();
   }

   public static class Builder implements Target,Method,Arg,Invoke {
      private ActorRef actor;
      private String method;
      private JsonValue value=null;

      private Builder() {}

      public static Target instance() {
         return new Builder();
      }
      @Override
      public Method target(String targetType, String targetId) {
         this.actor = Kar.Actors.ref(targetType, targetId);
         return this;
      }
      @Override
      public Arg method(String name) {
         method = name;
         return this;
      }

      @Override
      public Invoke arg(JsonValue value) {
         this.value = value;
         return this;
      }
      @Override
      public Invoke arg() {
         return this;
      }
      @Override
      public void tell() {
         if ( value != null ) {
            Kar.Actors.tell(actor, method, value);
         } else {
            Kar.Actors.tell(actor, method);
         }
      }
      @Override
      public JsonValue call() {
         if ( value != null ) {
            return Kar.Actors.call(actor, method, value);
         } else {
            return Kar.Actors.call(actor, method);
         }

      }
   }






}

