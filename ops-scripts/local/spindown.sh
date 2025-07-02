#!/bin/bash

set -e

TENANT="dev"
RELEASE="sentrius"

echo "This will delete the Helm release '$RELEASE' and the entire TENANT '$TENANT'."
read -p "Are you sure? (y/N): " CONFIRM

if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
  echo "❌ Aborted."
  exit 1
fi

echo "Uninstalling Helm release..."
helm uninstall "$RELEASE" -n "$TENANT" || echo "Helm release not found."

echo "🧹 Deleting TENANT '$TENANT'..."
kubectl delete namespace "$TENANT" || echo "namespace not found."
kubectl delete namespace "$TENANT-agents" || echo "namespace not found."

echo "⏳ Waiting for TENANT deletion to complete..."
while kubectl get namespace "$TENANT" &> /dev/null; do
  echo "  ... still deleting ..."
  sleep 2
done

while kubectl get namespace "$TENANT-agents" &> /dev/null; do
  echo "  ... still deleting ..."
  sleep 2
done

echo "TENANT '$TENANT' deleted."

echo "Done."
