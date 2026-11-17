#!/usr/bin/env bash
# Builds every service image from local source, loads it into the kind cluster and
# deploys the 'local' overlay. Lets you test code changes without pushing images.
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-o11y-lab}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVICES=(catalog-service order-service frontend load-generator)

for svc in "${SERVICES[@]}"; do
  echo "==> Building o11y-lab/${svc}:dev"
  docker build -q -t "o11y-lab/${svc}:dev" "$ROOT/services/${svc}"
done

echo "==> Loading images into kind cluster '$CLUSTER_NAME'"
kind load docker-image --name "$CLUSTER_NAME" $(printf 'o11y-lab/%s:dev ' "${SERVICES[@]}")

echo "==> Applying local overlay"
kubectl apply -k "$ROOT/deploy/kubernetes/overlays/local"

# Same tag on every build: force a rollout so new images are picked up.
kubectl -n o11y-lab rollout restart deploy catalog-service order-service frontend load-generator >/dev/null
kubectl -n o11y-lab rollout status statefulset/postgres --timeout=300s
kubectl -n o11y-lab rollout status deploy --timeout=300s

echo "==> Done. UI: http://localhost:30081"
