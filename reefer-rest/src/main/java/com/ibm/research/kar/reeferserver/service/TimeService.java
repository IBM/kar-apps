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

package com.ibm.research.kar.reeferserver.service;

import com.ibm.research.kar.reefer.common.Constants;
import org.springframework.stereotype.Component;

import javax.json.Json;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
public class TimeService extends AbstractPersistentService {

    public void saveDate(Instant date, String key) {
        super.set(key, Json.createValue(date.toString()));
    }

    public Optional<Instant> recoverDate(String key) {
      // JsonValue jv = super.get(Constants.CURRENT_DATE_KEY);
        JsonValue jv = super.get(key);
        System.out.println("TimeService.recoverDate() - key:"+key+" value:"+jv);
       if (Objects.isNull(jv)) {
           return Optional.empty();
       }

       return Optional.of(Instant.parse( ((JsonString)jv).getString() ));

      //  return Instant.parse( ((JsonString)jv).getString());
    }
}
