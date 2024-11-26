#!/bin/bash

if [ $# -ne 1 ]
then
    echo "Usage: $0 <path to tarball> <path to install directory>"
    exit 1;
fi;

JETTY_VERSION="10.0.18"
DEPLOYMENT_VERSION="4.0.00"
TARBALL_LOCATION=$1
MAVEN_PROJECT_DIR="$2"
RUN_DIR_NAME="systemguard-install"
JETTY_HOME_DOWNLOAD="https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz"
DEPLOYMENT_DIRECTORY="deploy"
STAGING_DIRECTORY="deploy-staging"
WAR_UNZIP_DIR_NAME="systemguard"
JETTY_BASE=jetty-home-${JETTY_VERSION}
DEPLOYMENT_WAR="bastillion-${DEPLOYMENT_VERSION}.war"
DEPLOYMENT_WAR_LOC="target/bastillion-${DEPLOYMENT_VERSION}.war"
JETTY_HOME=${DEPLOYMENT_DIRECTORY}/${JETTY_BASE}

pushd ${MAVEN_PROJECT_DIR}

mvn package -DskipTests

popd


mkdir ${STAGING_DIRECTORY} >/dev/null
mkdir ${DEPLOYMENT_DIRECTORY} >/dev/null

wget ${JETTY_HOME_DOWNLOAD} -O ${STAGING_DIRECTORY}/jetty-home.tar.gz

pushd ${STAGING_DIRECTORY}

tar -xzf jetty-home.tar.gz

popd

mv ${STAGING_DIRECTORY}/${JETTY_BASE} ${DEPLOYMENT_DIRECTORY}

mkdir ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME} > /dev/null

cp start.sh ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}

pushd  ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}

java -jar ../${JETTY_BASE}/start.jar --add-module=server,http,deploy,websocket

popd

mkdir ${STAGING_DIRECTORY}/${WAR_UNZIP_DIR_NAME}

cp ${MAVEN_PROJECT_DIR}/${DEPLOYMENT_WAR_LOC} ${STAGING_DIRECTORY}/${WAR_UNZIP_DIR_NAME}

pushd ${STAGING_DIRECTORY}/${WAR_UNZIP_DIR_NAME}

unzip ${DEPLOYMENT_WAR} > /dev/null

rm ${DEPLOYMENT_WAR}

popd

mv ${STAGING_DIRECTORY}/${WAR_UNZIP_DIR_NAME} ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}

cp bastillion.xml  ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}/webapps/

mv ${DEPLOYMENT_DIRECTORY} ${WAR_UNZIP_DIR_NAME}

tar -czf ${WAR_UNZIP_DIR_NAME}.tar.gz ${WAR_UNZIP_DIR_NAME}



