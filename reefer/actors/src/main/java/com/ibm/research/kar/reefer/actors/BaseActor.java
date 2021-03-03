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

import com.ibm.research.kar.actor.ActorInstance;

/**
 * Kar requires all Actor Classes to implement the ActorInstance interface.
 *
 * When writing an application component that contains multiple actor types,
 * a convenient, but not required, pattern is to share boilerplate code by
 * defining a common superclass for your Actor classes to extend.
 */
public abstract class BaseActor implements ActorInstance {
  protected String type;
  protected String id;
  protected String session;

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getSession() {
    return session;
  }

  @Override
  public void setType(String type) {
    this.type = type;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public void setSession(String session) {
    this.session = session;
  }
}
