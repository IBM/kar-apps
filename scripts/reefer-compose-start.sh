#!/bin/bash

# Script to launch reefer components using docker-compose

SCRIPTDIR=$(cd $(dirname "$0") && pwd)

cd $SCRIPTDIR

echo Deploying application with docker-compose ...

docker-compose -f reefer-compose.yaml -p reefer up -d

echo -n waiting for reefer-rest to be available ...
wait=1
while [ $wait != 0 ]; do
    response=$(curl -s -H "Content-Type: application/json" -X POST http://localhost:9080/time/currentDate)
    if [ -n "$response" ]; then
	echo $response | grep -e " "
	w=$?
	if [ $w != 0 ]; then
	    wait=0
	fi
    fi
    sleep 1
done

echo " reefer backend ready to sail"
