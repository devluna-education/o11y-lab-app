#!/usr/bin/env bash
# Idempotently ensures a healthy local kind cluster exists and kubectl points at it.
# Used by the Codespace (postStartCommand) and by CI.
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-o11y-lab}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> Waiting for Docker daemon"
for _ in $(seq 1 60); do
  docker info >/dev/null 2>&1 && break
  sleep 2
done
docker info >/dev/null 2>&1 || { echo "ERROR: Docker is not available"; exit 1; }

cluster_healthy() {
  kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME" \
    && kubectl --context "kind-$CLUSTER_NAME" get --raw /readyz >/dev/null 2>&1
}

if kind get clusters 2>/dev/null | grep -qx "$CLUSTER_NAME"; then
  # After a Codespace restart the node container may be stopped; try to bring it back.
  docker start "${CLUSTER_NAME}-control-plane" >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    cluster_healthy && break
    sleep 2
  done
fi

if cluster_healthy; then
  echo "==> Cluster '$CLUSTER_NAME' already running"
  kind export kubeconfig --name "$CLUSTER_NAME" >/dev/null
else
  echo "==> Creating kind cluster '$CLUSTER_NAME'"
  kind delete cluster --name "$CLUSTER_NAME" >/dev/null 2>&1 || true
  kind create cluster --name "$CLUSTER_NAME" --config "$SCRIPT_DIR/kind-config.yaml" --wait 120s
fi

kubectl config use-context "kind-$CLUSTER_NAME" >/dev/null
kubectl wait --for=condition=Ready nodes --all --timeout=120s >/dev/null

cat <<EOF

  Kubernetes cluster ready (context: kind-$CLUSTER_NAME)

  Deploy (published images from GHCR):
    kubectl apply -k deploy/kubernetes/overlays/lab

  Or build from local source and deploy:
    make deploy-local

  UI: http://localhost:30081  (Codespaces: Ports tab -> 30081)

EOF
