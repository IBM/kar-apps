# Making a Reefer Release

## Release Contents

A release of the Reefer application requires two artifacts:

+ A source release (github release page)
+ Tagged container images (quay.io)

## Release Procedures

Locally create a new local branch "release-prep": ```git checkout -b release-prep``` and then push it to remote: ```git push -u origin release-prep```

### Update CHANGELOG.md

1. Summarize non-trivial changes from `git log` into CHANGELOG.md
2. Commit update CHANGELOG.md to release-prep

### Build and Test container images

#### Update reefer version

1. Bump version `mvn versions:set -DnewVersion=x.y.z`   
2. Bump version in reefer-rest/src/main/liberty/config/server.xml
    + `location="thin-reefer-kar-rest-server-x.y.z.jar"`
3. Bump version in chart/values.yaml
4. Commit version bump changes to release-prep

#### Build Images and push to quay

1. Build "localhost:5000/kar/xxx:latest" images with `make reeferImages`
2. Tag the images "quay.io/ibm/xxx:latest"
    + `DOCKER_REGISTRY=quay.io DOCKER_NAMESPACE=ibm  make tagReeferImages`
3. Push to quay
    + `DOCKER_REGISTRY=quay.io DOCKER_NAMESPACE=ibm  make pushReeferImages`

#### Test the quay images using one or more of docker-compose, kind, k3d, etc.

#### Create versioned quay images and push
1. Tag the images "quay.io/ibm/xxx:latest"
    + `DOCKER_REGISTRY=quay.io DOCKER_NAMESPACE=ibm DOCKER_IMAGE_TAG=x.y.z  make tagReeferImages`
2. Push to quay
    + `DOCKER_REGISTRY=quay.io DOCKER_NAMESPACE=ibm DOCKER_IMAGE_TAG=x.y.z  make pushReeferImages`

### PR & Merge the release-prep branch

1. PR the release-prep branch. Merge PR.

### Tag repository

1. `git tag -s vx.y.z`
2. `git push --tags`
3. make a github release with source (via github actions)
4. Update the git release with the CHANGELOG

### Update reefer version to SNAPSHOT

1. Bump version `mvn versions:set -DnewVersion=x.y.(z+1)-SNAPSHOT`
2. Bump version in reefer-rest/src/main/liberty/config/server.xml
    + `location="thin-reefer-kar-rest-server-x.y.(z+1)-SNAPSHOT.jar"`
3. Commit changes and push 
