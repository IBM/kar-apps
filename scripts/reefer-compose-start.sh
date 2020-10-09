#!/bin/bash

# Script to launch reefer components using docker-compose

SCRIPTDIR=$(cd $(dirname "$0") && pwd)

cd $SCRIPTDIR

docker-compose -f reefer-compose.yaml -p reefer up -d
