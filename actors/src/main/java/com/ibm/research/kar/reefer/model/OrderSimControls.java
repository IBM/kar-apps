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

package com.ibm.research.kar.reefer.model;

public class OrderSimControls {
    int target;
    int window;
    int updateTarget;

    public OrderSimControls(int target, int window, int updateTarget) {
        this.target = target;
        this.window = window;
        this.updateTarget = updateTarget;
    }
    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public int getWindow() {
        return window;
    }

    public void setWindow(int window) {
        this.window = window;
    }

    public int getUpdateTarget() {
        return updateTarget;
    }

    public void setUpdateTarget(int updateTarget) {
        this.updateTarget = updateTarget;
    }



}