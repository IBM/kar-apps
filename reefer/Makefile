#
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
#

DOCKER_REGISTRY ?= localhost:5000
DOCKER_NAMESPACE ?= kar
LOCALHOST_PREFIX=localhost:5000/kar/
DOCKER_IMAGE_PREFIX ?= $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/
DOCKER_IMAGE_TAG ?= latest
KAR_IMAGE_PREFIX ?= quay.io/ibm/
KAR_IMAGE_TAG ?= 1.3.10

KAR_JAVA_SDK=$(KAR_IMAGE_PREFIX)kar-sdk-java-builder-11:$(KAR_IMAGE_TAG)
KAR_JAVA_RUNTIME=$(KAR_IMAGE_PREFIX)kar-sdk-java-runtime-11:$(KAR_IMAGE_TAG)
KAR_JS_SDK=$(KAR_IMAGE_PREFIX)kar-sdk-nodejs-v18:$(KAR_IMAGE_TAG)

REEFER_FRONTEND=kar-app-reefer-frontend
REEFER_BUILDER=kar-app-reefer-builder
REEFER_ACTORS=kar-app-reefer-actors
REEFER_SIMULATORS=kar-app-reefer-simulators
REEFER_REST=kar-app-reefer-rest
REEFER_MONITOR=kar-app-reefer-monitor

reeferImages: reeferBuilder reeferActors reeferSimulators reeferRest reeferFrontend reeferMonitor


reeferMonitor:
	cd simulators && docker build -f Dockerfile.monitor --build-arg JS_RUNTIME=$(KAR_JS_SDK) -t $(LOCALHOST_PREFIX)$(REEFER_MONITOR) .

reeferFrontend:
	docker build -f frontend/Dockerfile --build-arg JAVA_BUILDER=$(KAR_JAVA_SDK) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(LOCALHOST_PREFIX)$(REEFER_FRONTEND) .

reeferBuilder:
	docker build -f actors/Dockerfile.reefer-builder --build-arg JAVA_BUILDER=$(KAR_JAVA_SDK) -t $(LOCALHOST_PREFIX)$(REEFER_BUILDER) .

reeferActors:
	docker build -f actors/Dockerfile --build-arg JAVA_BUILDER=$(LOCALHOST_PREFIX)$(REEFER_BUILDER) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(LOCALHOST_PREFIX)$(REEFER_ACTORS) .

reeferSimulators:
	docker build -f simulators/Dockerfile --build-arg JAVA_BUILDER=$(LOCALHOST_PREFIX)$(REEFER_BUILDER) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(LOCALHOST_PREFIX)$(REEFER_SIMULATORS) .

reeferRest:
	docker build -f reefer-rest/Dockerfile --build-arg JAVA_BUILDER=$(LOCALHOST_PREFIX)$(REEFER_BUILDER) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(LOCALHOST_PREFIX)$(REEFER_REST) .


pushReeferImages:
	docker push $(DOCKER_IMAGE_PREFIX)$(REEFER_ACTORS):$(DOCKER_IMAGE_TAG)
	docker push $(DOCKER_IMAGE_PREFIX)$(REEFER_SIMULATORS):$(DOCKER_IMAGE_TAG)
	docker push $(DOCKER_IMAGE_PREFIX)$(REEFER_REST):$(DOCKER_IMAGE_TAG)
	docker push $(DOCKER_IMAGE_PREFIX)$(REEFER_FRONTEND):$(DOCKER_IMAGE_TAG)
	docker push $(DOCKER_IMAGE_PREFIX)$(REEFER_MONITOR):$(DOCKER_IMAGE_TAG)

tagReeferImages:
	docker tag $(LOCALHOST_PREFIX)$(REEFER_ACTORS)      $(DOCKER_IMAGE_PREFIX)$(REEFER_ACTORS):$(DOCKER_IMAGE_TAG)
	docker tag $(LOCALHOST_PREFIX)$(REEFER_SIMULATORS)  $(DOCKER_IMAGE_PREFIX)$(REEFER_SIMULATORS):$(DOCKER_IMAGE_TAG)
	docker tag $(LOCALHOST_PREFIX)$(REEFER_REST)        $(DOCKER_IMAGE_PREFIX)$(REEFER_REST):$(DOCKER_IMAGE_TAG)
	docker tag $(LOCALHOST_PREFIX)$(REEFER_FRONTEND)    $(DOCKER_IMAGE_PREFIX)$(REEFER_FRONTEND):$(DOCKER_IMAGE_TAG)
	docker tag $(LOCALHOST_PREFIX)$(REEFER_MONITOR)     $(DOCKER_IMAGE_PREFIX)$(REEFER_MONITOR):$(DOCKER_IMAGE_TAG)

.PHONY: docker
