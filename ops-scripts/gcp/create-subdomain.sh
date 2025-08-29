#!/bin/bash
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


source ${SCRIPT_DIR}/base.sh

TENANT=$1

gcloud dns record-sets transaction start --zone=${ZONE}
gcloud dns record-sets transaction add --zone=${ZONE} \
  --name=${TENANT}.sentrius.cloud. \
  --type=CNAME \
  --ttl=300 \
  app-loadbalancer.region.cloud.goog &&
gcloud dns record-sets transaction execute --zone=${ZONE}
