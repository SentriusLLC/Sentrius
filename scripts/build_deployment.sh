#!/bin/bash

JETTY_VERSION="10.0.18"
DEPLOYMENT_VERSION="4.1.00"
MAVEN_PROJECT_DIR=""
DISABLE_HTTP="false"
JETTY_FILE="jetty-ssl.xml"
ENABLE_SSL="false"
RUN_DIR_NAME="systemguard-install"
CONFIG_DIR_NAME="systemguard-config"
JETTY_HOME_DOWNLOAD="https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz"
DEPLOYMENT_DIRECTORY="deploy"
STAGING_DIRECTORY="deploy-staging"
WAR_UNZIP_DIR_NAME="systemguard"
JETTY_BASE=jetty-home-${JETTY_VERSION}
DEPLOYMENT_WAR="bastillion-${DEPLOYMENT_VERSION}.war"
DEPLOYMENT_WAR_LOC="target/bastillion-${DEPLOYMENT_VERSION}.war"
JETTY_HOME=${DEPLOYMENT_DIRECTORY}/${JETTY_BASE}

while [[ $# -gt 0 ]]; do
  case $1 in
    --maven)
      MAVEN_PROJECT_DIR="$2"
      shift # past argument
      shift # past value
      ;;
    --http_disable)
      DISABLE_HTTP="true"
      shift # past value
      ;;
    --enable_ssl)
        ENABLE_SSL="true"
        shift # past argument
        ;;
    --jetty_ssl_path)
        JETTY_FILE="$2"
        shift # past argument
        shift # past value
        ;;
    -*|--*)
      echo "Unknown option $1"
      exit 1
      ;;
    *)
      POSITIONAL_ARGS+=("$1") # save positional arg
      shift # past argument
      ;;
  esac
done


set -- "${POSITIONAL_ARGS[@]}" # restore positional parameters


DISABLE_HTTP=${DISABLE_HTTP:-false}
ENABLE_SSL=${ENABLE_SSL:-false}


# Initialize module list with mandatory modules
MODULES="server,deploy,websocket"

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
mkdir ${DEPLOYMENT_DIRECTORY}/${CONFIG_DIR_NAME} > /dev/null

cp start.sh ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}

cp stop.sh ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}

mkdir ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}/start.d > /dev/null

cp ${JETTY_FILE} ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}/start.d/

cp -R ssl/ ${DEPLOYMENT_DIRECTORY}/${CONFIG_DIR_NAME}/

pushd  ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}

# Add SSL module if ENABLE_SSL is true
if [ "$ENABLE_SSL" == "true" ]; then
    MODULES="$MODULES,ssl,https"
    # Specify the path to your SSL configuration file
    SSL_CONFIG="${DEPLOYMENT_DIRECTORY}/${CONFIG_DIR_NAME}/jetty-ssl.xml"
    CONFIG_OPTION="" #--config=$SSL_CONFIG
else
    CONFIG_OPTION=""
fi

# Add HTTP module only if DISABLE_HTTP is false
if [ "$DISABLE_HTTP" != "true" ]; then
    MODULES="$MODULES,http"
fi

START_COMMAND="java -jar ../$JETTY_BASE/start.jar"

# Construct the full start command
FULL_COMMAND="$START_COMMAND --add-module=$MODULES $CONFIG_OPTION"

echo "Executing ${FULL_COMMAND}"
${FULL_COMMAND}
#java -jar ../${JETTY_BASE}/start.jar --add-module=server,http,deploy,websocket

popd

mkdir ${STAGING_DIRECTORY}/${WAR_UNZIP_DIR_NAME}

cp ${MAVEN_PROJECT_DIR}/${DEPLOYMENT_WAR_LOC} ${STAGING_DIRECTORY}/${WAR_UNZIP_DIR_NAME}

pushd ${STAGING_DIRECTORY}/${WAR_UNZIP_DIR_NAME}

unzip ${DEPLOYMENT_WAR} > /dev/null

rm ${DEPLOYMENT_WAR}

popd

mv ${STAGING_DIRECTORY}/${WAR_UNZIP_DIR_NAME} ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}

echo ${DEPLOYMENT_VERSION} > ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}/version.txt

cp bastillion.xml  ${DEPLOYMENT_DIRECTORY}/${RUN_DIR_NAME}/webapps/

mv ${DEPLOYMENT_DIRECTORY} ${WAR_UNZIP_DIR_NAME}

## remove the database files
find ${WAR_UNZIP_DIR_NAME} -type f -name "bastillion.*.db" -exec rm {} +

tar -czf ${WAR_UNZIP_DIR_NAME}.tar.gz ${WAR_UNZIP_DIR_NAME}



