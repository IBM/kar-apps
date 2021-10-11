#!/bin/bash

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

# Script to launch reefer components using podman play
# usage: reefer-play-start.sh [release]

#function to check local image date against that pushed to local registry
check_image() {
    name=$1
    digest=$(curl --silent --header "Accept: application/vnd.docker.distribution.manifest.v2+json"\
		  "http://localhost:5000/v2/kar/${name}/manifests/latest" |  jq -r '.config.digest')
    if [[ "null" == "$digest" ]]; then
	return 2
    fi
    registry_date=$(curl --silent --location "http://localhost:5000/v2/kar/${name}/blobs/${digest}" | jq -r '.created')
    local_date=$(podman image inspect "localhost:5000/kar/${name}:latest" 2>/dev/null | jq -r '.[0].Created')
#    echo "$name $digest ::: registry=|${registry_date}| and local=|${local_date}|"

    if [[ "null" == "$local_date" ]] || [[ "$registry_date" == "$local_date" ]]; then
	return 0
    else
	return 1
    fi
}

if [[ -n "$1" ]] && [[ "$1" == "release" ]]; then
    export IMAGE_PREFIX="quay.io/ibm"
fi
if [ -z "$IMAGE_PREFIX" ]; then
    export IMAGE_PREFIX="localhost:5000/kar"
fi

SCRIPTDIR=$(cd $(dirname "$0") && pwd)
cd $SCRIPTDIR

# Frontend GUI may need custom URL to get to reefer-rest
# Frontend GUI itself will always be at localhost:9088
if [ -z "$REST_URL" ]; then
    resturl=http://localhost:9080
else
    resturl=$REST_URL
fi

response=$(podman pod list|grep reefer)
if [ -n "$response" ]; then
    echo $response | grep Running > /dev/null
    w=$?
    if [ $w != 0 ]; then
	echo "reefer pod already running"
	exit 1
    fi
fi

# if running from local registry, check if local images are different from those in local registry
if [[ localhost:5000/kar == "$IMAGE_PREFIX" ]]; then
    notok=0
    nogood=0
    for name in frontend monitor simulators actors rest; do
	check_image "kar-app-reefer-$name"
	rc="$?"
	if [[ "2" == "$rc" ]]; then
	    echo " ERROR: localhost:5000/kar/${name}:latest is not in registry"
	    nogood=1
        fi
	if [[ "1" == "$rc" ]]; then
	    echo " WARNING: localhost:5000/kar/${name}:latest has different image dates local vs registry"
	    notok=1
        fi
    done
    if [[ 2 == $nogood ]]; then
	echo "  missing necessary images in registry"
	exit
    fi
    
    if [[ 1 == $notok ]]; then
	if [[ -n "$1" ]] && [[ "$1" == "force" ]]; then
	    echo " running anyway"
	else
	    printf ' %s\n\n' 'add arg "force" to ignore'
	    exit
	fi
    fi
fi

echo Deploying ${IMAGE_PREFIX} images with podman play

# set default envs and pick up any external overrides
export ORDERSTATS_DELAY="${ORDERSTATS_DELAY:-60}"
export ORDERSTATS_RESET="${ORDERSTATS_RESET:-1}"
export ORDERSTATS_THRESHOLD="${ORDERSTATS_THRESHOLD:-500}"
export ORDERSTATS_COUNTS="${ORDERSTATS_COUNTS:-0}"
export CONTAINER_RESTART="${CONTAINER_RESTART:-Never}"
envsubst < reefer-app.yaml > /tmp/reefer-app.yaml
podman play kube /tmp/reefer-app.yaml -q
if [ $? -ne 0 ]
then
  exit 1
fi

echo -n waiting for reefer-rest to be available
wait=1
while [ $wait != 0 ]; do
    response=$(curl -s -H "Content-Type: application/json" -X POST ${resturl}/time/currentDate)
    if [ -n "$response" ]; then
	echo $response | grep -e " " > /dev/null
	w=$?
	if [ $w != 0 ]; then
	    wait=0
	fi
    fi
    echo -n '.'
    sleep 2
done

echo " reefer GUI available at http://localhost:9088"
rm /tmp/reefer-app.yaml
