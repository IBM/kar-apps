#!/bin/bash

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
#   ic ce project create -n reefer-test
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
restart_backend=""
restart_frontend=""
kardir=""
rest_url=""
args=""
parse=true

function usage() {
    cat << EOF
Usage: reefer-ce-run.sh [ -start_backend \$KAR | -start_frontend \$rest_url | -restart_backend | -restart_frontend ]
where
    \$KAR        is path to git clone of https://github.ibm.com/solsa/kar.git
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
        -restart_backend)
	    restart_backend="1"
            ;;
        -restart_frontend)
	    restart_frontend="1"
            ;;
        --) parse=;;
        *) args="$args '$1'";;
    esac
    shift
done

if [ -n "$help" ]; then
    usage 0
fi

numc=$(echo "$start_backend $start_frontend $restart_backend $restart_frontend" | wc -w)
if [ "$numc" != "1" ]; then
    echo
    echo "please specify one of [ -start_backend | -start_frontend | -restart_backend | -restart_frontend ]"
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
	echo "did you mean -restart_backend ?"
	echo
	exit 1
    fi
    
    # erase all existing reefer app state
    kar purge -app reefer
    if [ "$?" != 0 ]; then
	echo "app state purge failed"
	echo "please update environment with \"source $kardir/scripts/kar-env-ibmcloud.sh <service-key>\""
	echo
	exit 1
    fi
    
    ${kardir}/scripts/kar-ce-run.sh -app reefer -image us.icr.io/research/kar-dev/reefer/simulators -name reefer-simulators\
	  -service simservice -actors simhelper -port 7080 -nowait -v info
    ${kardir}/scripts/kar-ce-run.sh -app reefer -image us.icr.io/research/kar-dev/reefer/monitor -name reefer-monitor -port 7082\
	  -nowait -v info
    ${kardir}/scripts/kar-ce-run.sh -app reefer -image us.icr.io/research/kar-dev/reefer/actors -name reefer-actors\
	  -actors order,reefer,voyage,reefer-provisioner -port 8080 -nowait -v info
    ${kardir}/scripts/kar-ce-run.sh -app reefer -image us.icr.io/research/kar-dev/reefer/reefer-rest -name reefer-rest\
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
    ibmcloud ce app create -image us.icr.io/research/kar-dev/reefer/frontend -name reefer-frontend --min-scale 1\
	     --max-scale 1 --port 9088 --cmd /kar/bin/fe-start.sh --registry-secret kar.ibm.com.image-pull\
	     --env REST_URL=$rest_url
fi

if [ -n "$restart_backend" ]; then
    response=$(ibmcloud ce app list)
    if [ -z "$response" ]; then
	echo "no reefer backend components are running"
	exit 1
    fi

    read -p "Please confirm that the simulator is turned off [yN] " res
    case $res in
	[Yy]* ) ;;
	* ) exit 1;;
    esac

    echo "purging all existing reefer app state"
    kar purge -app reefer
    if [ "$?" != 0 ]; then
	echo "purge failed ... please update environment with \"source \$KAR/scripts/kar-env-ibmcloud.sh <service-key>\""
	echo
	exit 1
    fi

    test=$(echo "$response" | grep "reefer-actors")
    if [ -n "$test" ]; then
	echo "restarting reefer-actors"
	ibmcloud ce app update --name reefer-actors --wait=false
    fi

    test=$(echo "$response" | grep "reefer-rest")
    if [ -n "$test" ]; then
	echo "restarting reefer-rest"
	ibmcloud ce app update --name reefer-rest --wait=false
    fi

    test=$(echo "$response" | grep "reefer-simulators")
    if [ -n "$test" ]; then
	echo "restarting reefer-simulators"
	ibmcloud ce app update --name reefer-simulators --wait=false
    fi

    test=$(echo "$response" | grep "reefer-monitor")
    if [ -n "$test" ]; then
	echo "restarting reefer-monitor"
	ibmcloud ce app update --name reefer-monitor --wait=false
    fi

    echo "Check status with: ibmcloud ce app list"
fi


if [ -n "$restart_frontend" ]; then
    response=$(ibmcloud ce app list | grep reefer-frontend)
    if [ -z "$response" ]; then
	echo "reefer frontend not running"
	exit 1
    fi

    echo "restarting reefer-frontend"
    ibmcloud ce app update --name reefer-frontend
    fi
fi
