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

import javax.json.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ReeferWebApi {
   private final String webApiUrl;
   private final HttpClient http;

   public ReeferWebApi(String url) {
      this.webApiUrl = url;
      http = HttpClient.newHttpClient();
   }

   public HttpResponse<String> post(String path, JsonObject...args) throws URISyntaxException,InterruptedException,IOException{
      HttpRequest request = HttpRequest.newBuilder()
              .uri(new URI(webApiUrl+path))
              .POST(args.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(args[0].toString()))
              .header("Content-type", "application/json")
              .build();
      return http.send(request, HttpResponse.BodyHandlers.ofString());
   }

   public HttpResponse<String> get(String path, JsonObject...args) throws URISyntaxException,InterruptedException,IOException{
      HttpRequest request = HttpRequest.newBuilder()
              .uri(new URI(webApiUrl+path))
              .GET()
              .header("Content-type", "application/json")
              .build();
      return http.send(request, HttpResponse.BodyHandlers.ofString());
   }

}
