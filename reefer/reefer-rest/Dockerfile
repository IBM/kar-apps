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

ARG JAVA_BUILDER
ARG JAVA_RUNTIME

#
# Build the artifacts using the builder image
#
FROM $JAVA_BUILDER as builder

WORKDIR /kar/app
COPY reefer-rest/pom.xml ./
COPY reefer-rest/src src
CMD export MAVEN_OPTS="-Dmstr.clientNameLookup=false"
RUN mvn package

#
# Copy the artifacts to the runtime image
#
FROM $JAVA_RUNTIME
COPY  --from=builder --chown=default:root /kar/app/target/liberty/wlp /opt/ol/wlp

#
# Steps below based on: https://github.com/OpenLiberty/ci.docker#building-an-application-image
#

RUN features.sh

RUN configure.sh

