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

# Script to launch reefer components using IBM code-engine
# To use this script, you must
# - have a personal IBM cloud account
# - belong to the "RIS-HCLS Foundational Innovation" account
# - have the ibmcloud CLI installed
# - have the code engine CLI installed
#   ic plugin install code-engine
# - have created Kafka and Redis services with common service-key. See KAR documentation for details
# - have created registry access key from RIS login:
#   ic iam api-key-create cliapikey -d “kar-CLI-APIkey” --file ..stash/kar-cli-apikey
# - have created a code engine project
#   login to ibmcloud and select personal group
#   ic target -g Default
#   ic ce project create -n kar-project
#   $KAR/scripts/kar-ce-project-enable.sh <service-key> <cr-apikey>
#
# Then for each new session
# 1. login to ibmcloud and select RIS group
# 2. access the registry
#    ibmcloud cr login
# 3. login to ibmcloud and select personal group
#
# That's it!

start_backend=""
start_frontend=""
stop_backend=""
stop_frontend=""
kardir=""
rest_url=""
args=""
parse=true

function usage() {
    cat << EOF
Usage: reefer-ce-run.sh [ -start_backend \$KAR | -start_frontend \$rest_url | -stop_backend | -stop_frontend ]
where
    \$KAR        is path to git clone of https://github.com/IBM/kar-apps.git
    \$rest_url   is the URL of the reefer-rest application

EOF
    exit $1
}

while [ -n "$1" ]; do
    if [ -z "$parse" ]; then
        args="$args '$1'"
        shift
        continue
    fi
    case "$1" in
        -h|-help|--help) help="1"; break;;
        -start_backend)
	    start_backend=1
            shift;
            kardir="$1"
            ;;
        -start_frontend)
	    start_frontend=1
            shift;
            rest_url="$1"
            ;;
        -stop_backend)
	    stop_backend="1"
            ;;
        -stop_frontend)
	    stop_frontend="1"
            ;;
        --) parse=;;
        *) args="$args '$1'";;
    esac
    shift
done

if [ -n "$help" ]; then
    usage 0
fi

numc=$(echo "$start_backend $start_frontend $stop_backend $stop_frontend" | wc -w)
if [ "$numc" != "1" ]; then
    echo
    echo "please specify one of [ -start_backend | -start_frontend | -stop_backend | -stop_frontend ]"
    echo
    usage 1
fi

if [ -n "$start_backend" ]; then
    if [ ! -f ${kardir}/scripts/kar-ce-run.sh ]; then
	echo
	echo "\$KAR=$kardir is invalid path-to-kar-clone"
	echo
	usage 1
    fi
    #check if backend already running
    response=$(ibmcloud ce app list | egrep "reefer-actors|reefer-rest|reefer-simulators|reefer-monitor")
    if [ -n "$response" ]; then
	echo "reefer backend components already running:"
	printf "$response"
	echo
	echo
	echo "did you mean -stop_backend ?"
	echo
	exit 1
    fi
    
    # erase any existing reefer app state
    kar purge -app reefer
    if [ "$?" != 0 ]; then
	echo "app state purge failed"
	echo "please update environment with \"source $kardir/scripts/kar-env-ibmcloud.sh <service-key>\""
	echo
	exit 1
    fi
    
    ${kardir}/scripts/kar-ce-run.sh -app reefer -image quay.io/ibm/kar-app-reefer-simulators -name reefer-simulators\
	  -service simservice -actors simhelper -port 7080 -nowait -v info
    ${kardir}/scripts/kar-ce-run.sh -app reefer -image quay.io/ibm/kar-app-reefer-monitor -name reefer-monitor -port 7082\
	  -nowait -v info
    ${kardir}/scripts/kar-ce-run.sh -app reefer -image quay.io/ibm/kar-app-reefer-actors -name reefer-actors\
	  -actors order,reefer,voyage -port 8080 -nowait -v info
    ${kardir}/scripts/kar-ce-run.sh -app reefer -image quay.io/ibm/kar-app-reefer-reefer-rest -name reefer-rest\
	  -service reeferservice -port 9080 -externalize -nowait -v info
    echo "backend started. To check status: ibmcloud ce app list"
    exit 0
fi

if [ -n "$start_frontend" ]; then
    if [ -z $rest_url ]; then
	echo
	echo "\$rest_url not specified"
	echo
	usage 1
    fi
    # check if rest is running
    response=$(curl -s -H "Content-Type: application/json" -X POST ${rest_url}/time/currentDate)
    if [ -n "$response" ]; then
	echo $response | egrep "[0-9]{4}\-[0-9]{2}\-[0-9]{2}T[\:.0-9]+Z" > /dev/null
	w=$?
	if [ $w != 0 ]; then
	    echo
	    echo "\$rest_url=$rest_url gives invalid response. Please confirm reefer-rest URL"
	    echo
	    exit 1
	fi
    else
	echo
	echo "\$rest_url=$rest_url is not responding. Please confirm reefer-rest app status and URL"
	echo
	exit 1
    fi
    ibmcloud ce app create -image quay.io/ibm/kar-app-reefer-frontend -name reefer-frontend --min-scale 1\
	     --max-scale 1 --port 9088 --cmd /kar/bin/fe-start.sh --registry-secret kar.ibm.com.image-pull\
	     --env REST_URL=$rest_url
fi

if [ -n "$stop_backend" ]; then
    response=$(ibmcloud ce app list | awk '{print $1}'|grep reefer)
    if [ -z "$response" ]; then
	echo "no reefer backend components are running"
	exit 1
    fi

    for f in $(echo -n $response); do
        echo " stopping $f"
	ibmcloud ce app delete --name $f --force
    done
fi


if [ -n "$stop_frontend" ]; then
    response=$(ibmcloud ce app list | grep reefer-frontend)
    if [ -z "$response" ]; then
	echo "reefer frontend not running"
	exit 1
    fi

    echo "stopping reefer-frontend"
    ibmcloud ce app delete --name reefer-frontend --force
fi
