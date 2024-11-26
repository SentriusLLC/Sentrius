#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd)
JETTY_VERSION="10.0.18"
JETTY_BASE=jetty-home-${JETTY_VERSION}

java -Xms4096m -Xmx4096m -jar ${SCRIPT_DIR}/../${JETTY_BASE}/start.jar STOP.PORT=28282 STOP.KEY=systemguard --stop