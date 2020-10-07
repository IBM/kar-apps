#!/bin/bash

# Script to launch reefer components using docker-compose

SCRIPTDIR=$(cd $(dirname "$0") && pwd)

cd $SCRIPTDIR

docker-compose -p reefer down
