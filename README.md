<!--
# Copyright IBM Corporation 2020,2021
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
-->

# KAR Applications  

The [Reefer App](reefer/README.md) was developed to test the KAR runtime against the needs of an enterprise-like application.
Modeled after the 
 [IBM Garage: Reefer Container Shipment reference implementation](https://ibm-cloud-architecture.github.io/refarch-kc/),
this version was almost entirely rewritten to fully take advantage of KAR.

Notes:
 - A goal of the rewrite was to compare the code and configuration complexity of a KAR actor based implementation with a more traditional k8s design.
 - Rather than implement expected customer usage scenarios, the reefer GUI is designed primarily to demonstrate performance and scale.
 - The business logic for anomaly processing is new, but it is designed to reuse anomaly detection logic from the Garage implementation.


