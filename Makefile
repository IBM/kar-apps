DOCKER_REGISTRY ?= us.icr.io
DOCKER_NAMESPACE ?= research/kar-dev
DOCKER_IMAGE_PREFIX ?= $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/
DOCKER_IMAGE_TAG ?= latest

KAR_JAVA_SDK=$(DOCKER_IMAGE_PREFIX)sdk-java-builder-11:$(DOCKER_IMAGE_TAG)
KAR_JAVA_RUNTIME=$(DOCKER_IMAGE_PREFIX)sdk-java-runtime-11:$(DOCKER_IMAGE_TAG)
KAR_JS_SDK=$(DOCKER_IMAGE_PREFIX)sdk-nodejs-v12:$(DOCKER_IMAGE_TAG)

REEFER_FRONTEND=$(DOCKER_IMAGE_PREFIX)reefer/frontend:$(DOCKER_IMAGE_TAG)
REEFER_BUILDER=$(DOCKER_IMAGE_PREFIX)reefer/builder:$(DOCKER_IMAGE_TAG)
REEFER_ACTORS=$(DOCKER_IMAGE_PREFIX)reefer/actors:$(DOCKER_IMAGE_TAG)
REEFER_SIMULATORS=$(DOCKER_IMAGE_PREFIX)reefer/simulators:$(DOCKER_IMAGE_TAG)
REEFER_REST=$(DOCKER_IMAGE_PREFIX)reefer/reefer-rest:$(DOCKER_IMAGE_TAG)
REEFER_MONITOR=$(DOCKER_IMAGE_PREFIX)reefer/monitor:$(DOCKER_IMAGE_TAG)

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


# dockerPushReeferImages:
# 	docker push $(REEFER_FRONTEND)
# 	docker push $(REEFER_ACTORS)
# 	docker push $(REEFER_BUILDER)
# 	docker push $(REEFER_SIMULATOR)
# 	docker push $(REEFER_REST)

#dockerBuildAndPush:
#	make dockerPushReeferImages

.PHONY: docker
