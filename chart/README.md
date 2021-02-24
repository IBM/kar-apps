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

This chart deploys the Reefer application on various Kubernetes clusters.
By default helm will deploy images from a local registry listening on localhost:5000.
To deploy official release images, override kar.imagePrefix as indicated below.

### **Prerequisites to deployment**:  
 * Deploy the KAR Runtime System to the kar-system namespace: [kar-install-dir]/scripts/kar-k8s-deploy.sh
 * If intending to deploy to a local kubernetes (kind, k3s or Docker Desktop)
   * first run [kar-install-dir]/scripts/docker-compose-stop.sh
   * if deploying locally built reefer images, push them to a repo running at localhost:5000 and leave out the override of kar.imagePrefix below

### **To deploy on `k3s` or `Docker Desktop`**
 * From [kar-apps-install-dir] execute:
```shell
helm install reefer chart --set kar.imagePrefix=quay.io/ibm
```

### **To deploy on `kind`**
 * From [kar-apps-install-dir] execute:
```shell
helm install reefer chart --set ingress.pathBased=true --set kar.imagePrefix=quay.io/ibm
```

### **To deploy on `IBM Cloud Kubernetes Service`**
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
from [kar-apps-install-dir], substituting in
the actual values for `your-ingress-subdomain` and `your-ingress-secret`
```shell
helm install reefer chart --set ingress.hostBased=true --set kar.imagePrefix=quay.io/ibm --set ingress.subdomain=your-ingress-subdomain --set ingress.secret=your-ingress-secret
```

### **Wait for application to complete initialization**
 * After deploying, wait about a minute to allow the application to
finish initializing and use the URL printed by the `helm install` command
to access the Reefer Web Application.

### **To undeploy the application from any type of cluster:**
```shell
helm uninstall reefer
```
