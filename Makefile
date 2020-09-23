DOCKER_REGISTRY ?= us.icr.io
DOCKER_NAMESPACE ?= research/kar-dev
DOCKER_IMAGE_PREFIX ?= $(DOCKER_REGISTRY)/$(DOCKER_NAMESPACE)/
DOCKER_IMAGE_TAG ?= latest
MAVEN_REPOSITORY ?= $(HOME)/.m2

KAR_JAVA_SDK=$(DOCKER_IMAGE_PREFIX)sdk-java-builder-11:$(DOCKER_IMAGE_TAG)
KAR_JAVA_RUNTIME=$(DOCKER_IMAGE_PREFIX)sdk-java-runtime-11:$(DOCKER_IMAGE_TAG)

REEFER_FRONTEND=$(DOCKER_IMAGE_PREFIX)reefer/frontend:$(DOCKER_IMAGE_TAG)
REEFER_ACTORS=$(DOCKER_IMAGE_PREFIX)reefer/actors:$(DOCKER_IMAGE_TAG)
REEFER_SIMULATORS=$(DOCKER_IMAGE_PREFIX)reefer/simulators:$(DOCKER_IMAGE_TAG)
REEFER_REST=$(DOCKER_IMAGE_PREFIX)reefer/reefer-rest:$(DOCKER_IMAGE_TAG)

all: reeferActors reeferSimulators reeferRest reeferFrontend

reeferFrontend:
	cd frontend && docker build --rm -v $(MAVEN_REPOSITORY):/root/.m2:Z --build-arg JAVA_BUILDER=$(KAR_JAVA_SDK) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(REEFER_FRONTEND) .

reeferActors:
	cd actors && docker build --rm -v $(MAVEN_REPOSITORY):/root/.m2:Z --build-arg JAVA_BUILDER=$(KAR_JAVA_SDK) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(REEFER_ACTORS) .

reeferSimulators:
	cd simulators && docker build --rm -v $(MAVEN_REPOSITORY):/root/.m2:Z --build-arg JAVA_BUILDER=$(KAR_JAVA_SDK) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(REEFER_SIMULATORS) .

reeferRest:
	cd reefer-rest && docker build --rm -v $(MAVEN_REPOSITORY):/root/.m2:Z --build-arg JAVA_BUILDER=$(KAR_JAVA_SDK) --build-arg JAVA_RUNTIME=$(KAR_JAVA_RUNTIME) -t $(REEFER_REST) .

# dockerPushReefers:
# 	docker push $(REEFER_FRONTEND)
# 	docker push $(REEFER_ACTORS)
# 	docker push $(REEFER_SIMULATOR)
# 	docker push $(REEFER_REST)

.PHONY: docker
