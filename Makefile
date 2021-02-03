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
DOCKER_IMAGE_PREFIX ?= $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/
DOCKER_IMAGE_TAG ?= latest

KAR_JAVA_SDK=$(DOCKER_IMAGE_PREFIX)kar-sdk-java-builder-11:$(DOCKER_IMAGE_TAG)
KAR_JAVA_RUNTIME=$(DOCKER_IMAGE_PREFIX)kar-sdk-java-runtime-11:$(DOCKER_IMAGE_TAG)
KAR_JS_SDK=$(DOCKER_IMAGE_PREFIX)kar-sdk-nodejs-v12:$(DOCKER_IMAGE_TAG)

REEFER_FRONTEND=$(DOCKER_IMAGE_PREFIX)kar-app-reefer-frontend:$(DOCKER_IMAGE_TAG)
REEFER_BUILDER=$(DOCKER_IMAGE_PREFIX)kar-app-reefer-builder:$(DOCKER_IMAGE_TAG)
REEFER_ACTORS=$(DOCKER_IMAGE_PREFIX)kar-app-reefer-actors:$(DOCKER_IMAGE_TAG)
REEFER_SIMULATORS=$(DOCKER_IMAGE_PREFIX)kar-app-reefer-simulators:$(DOCKER_IMAGE_TAG)
REEFER_REST=$(DOCKER_IMAGE_PREFIX)kar-app-reefer-rest:$(DOCKER_IMAGE_TAG)
REEFER_MONITOR=$(DOCKER_IMAGE_PREFIX)kar-app-reefer-monitor:$(DOCKER_IMAGE_TAG)

reeferImages: reeferBuilder reeferActors reeferSimulators reeferRest reeferFrontend reeferMonitor


reeferMonitor:
	cd simulators && docker build -f Dockerfile.monitor -t $(REEFER_MONITOR) .

reeferFrontend:
	cd frontend && docker build --build-arg JAVA_BUILDER=$(KAR_JAVA_SDK) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(REEFER_FRONTEND) .

reeferBuilder:
	cd actors && docker build -f Dockerfile.reefer-builder -t $(REEFER_BUILDER) .

reeferActors:
	cd actors && docker build --build-arg JAVA_BUILDER=$(REEFER_BUILDER) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(REEFER_ACTORS) .

reeferSimulators:
	cd simulators && docker build --build-arg JAVA_BUILDER=$(REEFER_BUILDER) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(REEFER_SIMULATORS) .

reeferRest:
	cd reefer-rest && docker build --build-arg JAVA_BUILDER=$(REEFER_BUILDER) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(REEFER_REST) .


pushReeferImages:
	docker push $(REEFER_FRONTEND)
	docker push $(REEFER_ACTORS)
	docker push $(REEFER_MONITOR)
	docker push $(REEFER_SIMULATORS)
	docker push $(REEFER_REST)

.PHONY: docker
