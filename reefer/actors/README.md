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

# Reefer Actor Service

To launch Reefer Actor service run:

kar run -app reefer -v info -actors order,voyage,reefer-depot,depot-manager,anomaly-manager,order-manager,schedule-manager -actor_collector_interval 10m mvn liberty:run

To test placing an order run the following in a separate shell:

kar run -runtime_port 32123 -app reefer curl -s -H "Content-Type: application/kar+json" -X POST http://localhost:32123/kar/v1/actor/order/dummyid/call/createOrder -d '[{"order":{"number":10,"orderId":"4519","orderVoyageId":"144312","orderProductQty":2400}}]'
