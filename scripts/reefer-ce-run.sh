#!/bin/bash

# Script to launch reefer components using IBM code-engine

if [ -z "$1" ]; then
    echo "usage: reefer-ce-run.sh path-to-kar-clone"
    exit 1
fi
KAR=$1

if [ ! -f ${KAR}/scripts/kar-ce-run.sh ]; then
    echo "invalid path-to-kar-clone"
    exit 1
fi


${KAR}/scripts/kar-ce-run.sh -app reefer -image us.icr.io/research/kar-dev/reefer/simulators -name reefer-simulators -service simservice -actors simhelper -port 7080 -nowait -v info
${KAR}/scripts/kar-ce-run.sh -app reefer -image us.icr.io/research/kar-dev/reefer/monitor -name reefer-monitor -port 7082 -nowait -v info
${KAR}/scripts/kar-ce-run.sh -app reefer -image us.icr.io/research/kar-dev/reefer/actors -name reefer-actors -actors order,reefer,voyage,reefer-provisioner -port 8080 -nowait -v info
${KAR}/scripts/kar-ce-run.sh -app reefer -image us.icr.io/research/kar-dev/reefer/reefer-rest -name reefer-rest -service reeferservice -port 9080 -externalize -nowait -v info

# ibmcloud ce app create -image us.icr.io/research/kar-dev/reefer/frontend -name reefer-frontend --min-scale 1 --max-scale 1 --port 9088 --cmd /kar/bin/fe-start.sh --registry-secret kar.ibm.com.image-pull --env REST_URL={reefer-rest-url}

