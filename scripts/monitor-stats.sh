#!/bin/bash
cd $(dirname "$0")

if [ ! -d "node_modules" ]; then
    echo "to use this command, need to run \"npm install --prod\" in directory $(pwd)"
    exit
fi

KAR_RUNTIME_PORT=${KAR_RUNTIME_PORT:=30666}

kar run -runtime_port $KAR_RUNTIME_PORT -app reefer node monitor-stats.js $1
