#!/bin/bash

function modify_index_html {
    sleep 10
    /bin/sed -i 's/localhost/'"$REST_HOST"'/' /opt/ol/wlp/usr/servers/defaultServer/apps/expanded/reefer-frontend.war/index.html
}

if [ ! -z $REST_HOST ]; then
    echo "setting REST_HOST=$REST_HOST"
    modify_index_html &
fi

/kar/bin/runner
