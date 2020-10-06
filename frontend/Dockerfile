ARG JAVA_BUILDER=us.icr.io/research/kar-dev/sdk-java-builder-11
ARG JAVA_RUNTIME=us.icr.io/research/kar-dev/sdk-java-runtime-11

#
# Build the artifacts using the builder image
#
FROM $JAVA_BUILDER as builder

WORKDIR /kar/app
COPY pom.xml tsconfig.app.json tsconfig.json tsconfig.spec.json tslint.json package.json package-lock.json angular.json karma.conf.js browserslist extra-webpack.config.js ./
COPY e2e e2e
COPY src src
CMD export MAVEN_OPTS="-Dmstr.clientNameLookup=false"
RUN mvn clean install

#
# Copy the artifacts to the runtime image
#
FROM $JAVA_RUNTIME
COPY --from=builder --chown=default:root /kar/app/target/reefer-frontend.war /opt/ol/wlp/usr/servers/defaultServer/apps
COPY --from=builder --chown=default:root /kar/app/src/main/liberty/config/server.xml /opt/ol/wlp/usr/servers/defaultServer

#
# Run Open Liberty configure script.
# Doing this now reduces startup time of the final image
#
RUN /opt/ol/helpers/build/configure.sh default
