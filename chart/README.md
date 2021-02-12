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

This chart deploys the Reefer application on a local Kubernetes cluster.
By default helm will deploy images from a local registry listening on localhost:5000.
To deploy official release images, override kar.imagePrefix as indicated below.

To deploy on `k3s` or `Docker Desktop` execute:
```shell
helm install reefer chart --set kar.imagePrefix=quay.io/ibm
```

To deploy on `kind` execute:
```shell
helm install reefer chart --set ingress.pathBased=true --set kar.imagePrefix=quay.io/ibm
```

After deploying, wait about a minute to allow the application to
finish initializing and use the URL displayed by the `helm install`
to access the Reefer Web Application.

To undeploy the application from any type of cluster:
```shell
helm uninstall reefer
```
