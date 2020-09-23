ARG JAVA_BUILDER=us.icr.io/research/kar-dev/sdk-java-builder-11
ARG JAVA_RUNTIME=us.icr.io/research/kar-dev/sdk-java-runtime-11

#
# Build the artifacts using the builder image
#
FROM $JAVA_BUILDER as builder

WORKDIR /kar/app
COPY pom.xml ./
COPY src src
CMD export MAVEN_OPTS="-Dmstr.clientNameLookup=false"
RUN mvn package

#
# Copy the artifacts to the runtime image
#
FROM $JAVA_RUNTIME
COPY  --from=builder --chown=default:root /kar/app/target/liberty/wlp /opt/ol/wlp

#
# Run Open Liberty configure script.
# Doing this now reduces startup time of the final image
#
RUN /opt/ol/helpers/build/configure.sh default
