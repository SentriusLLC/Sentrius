#!/bin/bash

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)


source ${SCRIPT_DIR}/base.sh


gcloud container clusters resize ${CLUSTER} \
    --region ${REGION} \
    --num-nodes 0

