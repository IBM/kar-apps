#!/bin/bash

function modify_index_html {
    sleep 10
    /bin/sed -i 's|http://localhost:9080|'"$REST_URL"'|' /opt/ol/wlp/usr/servers/defaultServer/apps/expanded/reefer-frontend.war/index.html
}

if [ ! -z $REST_URL ]; then
    echo "setting REST_URL=$REST_URL"
    modify_index_html &
else
    echo "using default REST_URL=http://localhost:9080"
fi

/kar/bin/runner
