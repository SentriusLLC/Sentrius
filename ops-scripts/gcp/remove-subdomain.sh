#!/bin/bash
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


source ${SCRIPT_DIR}/base.sh

DOMAIN=$1

gcloud dns record-sets transaction start --zone=${ZONE}
gcloud dns record-sets transaction remove --zone=${ZONE} \
    --name=${DOMAIN}.sentrius.cloud --type=A --ttl=300 "192.0.2.1"
gcloud dns record-sets transaction execute --zone=${ZONE}
