<!--
# Copyright IBM Corporation 2020,2022
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

# Overview

This document describes how the KAR metrics configuration
for deployment on K3D is extended to include reefer performance data.
See [Deploying KAR Metrics](https://github.com/IBM/kar/blob/main/docs/kar-metrics.md)

Extensions consist of Prometheus configuration needed to capture
reefer performance data from application sidecars, and 
Grafana configuration to create a custom reefer dashboard for
visualization.

After KAR metrics are deployed, Reefer metrics extensions are deployed with:
```shell
[kar-apps-install-dir]/reefer/scripts/start-reefer-metrics.sh
```


## Prometheus configuration extension

This extension is defined in reefer-metrics.yaml.
It specifies in which namespace to look for KAR application pods,
which kar application to target, what URL to use to access
the raw KAR metrics, and how frequently to scrape raw data.


## Grafana configuration extension

This is defined in reefer-dashboard.yaml.
It defines separate CPU and Memory graphs for each reefer application
component to be monitored, and for the method latency graph that tracks
latency for all actor and service methods called in the target components.


## Viewing Reefer Dashboard in Grafana

The custom "Reefer Dashboard" for Grafana deployed above are viewed by first exposing
the Grafana web server on the cluster, attaching a browser to the web server, 
logging into Grafana, and finally selecting the Reefer Dashboard.

1. kubectl port-forward -n prometheus svc/prometheus-grafana  3000:80"
2. Point your browser to localhost:3000"
3. Login to Grafana with default user/password: admin/prom-operator"
4. Click on "Dashboards" in the Grafana menu at top left, search for reefer and select Reefer Dashboard
