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

# KAR-APPS: Applications based on the KAR Runtime

# KAR-APPS 1.3.0 - 2022-06-09
+ Reefer
   + Document extension of KAR metrics for reefer application
   + Improve reefer's grafana metrics dashboard
   + Use latest version of KAR and use its updated versions for Kafka and Zookeeper
   + Use docker's compose command instead of docker-compose application
   + Run KAR processes in application containers instead of sidecars

# KAR-APPS 1.2.0 - 2022-05-06
+ Reefer
   + Convert order creation to use async message flow in order to implement a timeout
   + Use TailCalls to eliminate replicated messages during fault testing and reduce idempotence checks required
   + Fixes to incorrect reporting of global reefer counts that occur during fault testing
   + Make progress toward metric and log stacks for kubernetes deployments
   + Add fault driver support for podman play kube deployment

# KAR-APPS 1.1.0 - 2022-02-02
+ Reefer
   + Made Rest Service stateless by moving state to singleton actors
   + Made all actor methods idempotent
   + Incrementally save state in all actor methods to support fault recovery
   + Replaced single reefer depot with a depot for each port
   + Created anomaly manager singleton actor to route each anomaly to its current location (depot or voyage)
   + Increased fleet size and capacity per ship
   + Reduced persistent storage required for reefer state
   + Automatically rebalance depot inventories by moving empty reefers between ports

# KAR-APPS 1.0.1 - 2021-03-29
+ Reefer
   + Improve active voyage view (#14)
   + Enhance reefer-play-start (#22, #25)
   + Fixes to manual order creation (#15, #18)

# KAR-APPS 1.0.0 - 2021-03-10
+ First stable release. It is based off KAR v1.0.1.

