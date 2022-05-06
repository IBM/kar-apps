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

# Helm Chart

This Helm chart support deploying the Reefer application to various Kubernetes clusters.
In all cases below kafka and redis are deployed in the target K8s cluster.  

Except on k3d only a single instance of reefer components are started.
k3d deployment has an option to deploy replicas of reefer components using global.affinity=true.
When affinity is specified, the replicated reefer components are run on different nodes from
kar system components and the reefer simulator.
Reefer includes an automated fault driver that randomly kills and restarts k3d application nodes.

## Prerequisites to deployment 
 * If intending to deploy to a local kubernetes (kind, k3d or Docker Desktop)
   * first run `[kar-install-dir]/scripts/docker-compose-stop.sh` as the kar system components run in kubernetes.
   * if deploying locally built reefer images, push them to a repo running at localhost:5000 and leave out the override of kar.imagePrefix 
 * Deploy the KAR Runtime System to the kar-system namespace: `[kar-install-dir]/scripts/kar-k8s-deploy.sh`  
   * If using k3d: ```[kar-install-dir]/scripts/kar-k8s-deploy.sh --set global.affinity=true```
 * Sources for Kar and Reefer images each have defaults:
   * Kar image source depends on the results of ```kar version```. A specific version will be pulled from quay.io. Version="unofficial" will pull from localhost:5000
   * Reefer image source defaults to settings in reefer/chart/values.yaml. Reefer distribution will point to a specific version in quay.io. To change these to locally built images pushed into localhost:5000, add the following args to the helm install command: ```--set reefer.imagePrefix=registry:5000/kar``` and ```--set reefer.version=latest```

## Deploy reefer using helm from directory: ```[kar-apps-install-dir]/reefer```

### To deploy on `Docker Desktop`
```shell
helm install reefer chart
```

### To deploy on `kind`
```shell
helm install reefer chart --set ingress.pathBased=true
```

### To deploy on `k3d`
```shell
helm install reefer chart  --set ingress.pathBased=true --set global.affinity=true
```

## To deploy on `IBM Cloud Kubernetes Service`
 * First use `ibmcloud` to determine the `Ingress Subdomain` and `Ingress Secret` for your cluster:
```shell
ibmcloud cs cluster get --cluster your-cluster-name
OK

Name:                           your-cluster-name
...
Ingress Subdomain:              your-ingress-subdomain
Ingress Secret:                 your-ingress-secret
...
```
 * Next deploy the chart by executing the command below
from `[kar-apps-install-dir]/reefer`, substituting in
the actual values for `your-ingress-subdomain` and `your-ingress-secret`
```shell
helm install reefer chart --set ingress.hostBased=true --set ingress.subdomain=your-ingress-subdomain --set ingress.secret=your-ingress-secret
```
## To deploy on `Red Hat OpenShift on IBM Cloud`
 * First use `ibmcloud` to determine the `Ingress Subdomain` for your cluster:
```shell
ibmcloud cs cluster get --cluster your-cluster-name
OK

Name:                           your-cluster-name
...
Ingress Subdomain:              your-ingress-subdomain
...
```
 * Next deploy the chart by executing the command below
from `[kar-apps-install-dir]/reefer`, substituting in
the actual value for `your-ingress-subdomain`
```shell
helm install reefer chart --set ingress.hostBased=true --set ingress.subdomain=your-ingress-subdomain
```

This will expose the Reefer application using http.  If you want to
use https, you will have to manually copy the ingress TLS secret into
your namespace as described in the IBM Cloud documentation
[About Ingress on OpenShift 4](https://cloud.ibm.com/docs/openshift?topic=openshift-ingress-about-roks4).
Then you can add `--set ingress.secret=your-ingress-secret` to your
`helm install` command and deploy with an https enabled ingress.

## Wait for application to complete initialization
 * After deploying, wait about a minute to allow the application to
finish initializing and use the URL printed by the `helm install` command
to access the Reefer Web Application.

## To undeploy the application from any type of cluster
```shell
helm uninstall reefer
[kar-install-dir]/scripts/kar-k8s-undeploy.sh
```
