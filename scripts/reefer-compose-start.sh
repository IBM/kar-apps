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

# Script to launch reefer components using docker-compose
# Frontend GUI may need custom URL to get to reefer-rest
# Frontend GUI itself will always be at localhost:9088

SCRIPTDIR=$(cd $(dirname "$0") && pwd)

cd $SCRIPTDIR

# if [ -n "$REST_HOST" ] && [ "$REST_HOST" == "auto" ]; then
#    echo -n "trying to guess host ip ... "
#    REST_HOST=$(host $(hostname) | cut -d' ' -f4)
#    valid=$(echo $REST_HOST | egrep -e "[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+" > /dev/null)
#    if [ $? != 0 ]; then
#        echo nope
#        echo Please specify actual host ip address to use
#        exit
#    else
#      echo "found $REST_HOST"
#    fi
# fi

if [ -z "$REST_URL" ]; then
    resturl=http://localhost:9080
else
    resturl=$REST_URL
fi

echo Deploying application with docker-compose ...

docker-compose -f reefer-compose.yaml -p reefer up -d

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
