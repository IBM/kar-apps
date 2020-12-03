#!/bin/bash
cd $(dirname "$0")

if [ ! -d "node_modules" ]; then
    echo "to use this command, need to run \"npm install yargs redis\" in directory $(pwd)"
    exit
fi

node actor-state.js "$@"
