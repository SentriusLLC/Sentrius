#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd)
JETTY_VERSION="10.0.18"
JETTY_BASE_INSTALL=jetty-home-${JETTY_VERSION}

JETTY_HOME=${SCRIPT_DIR}/../${JETTY_BASE_INSTALL}/
JETTY_BASE=${JETTY_HOME}

pushd ${SCRIPT_DIR}

java -Xms4096m -Xmx4096m -jar ${JETTY_HOME}/start.jar -DCONFIG_DIR=${SCRIPT_DIR}/../systemguard-config STOP.PORT=28282 STOP.KEY=systemguard > ${SCRIPT_DIR}/app.out 2>&1 &
javaPID=$!
echo PID is $javaPID

popd