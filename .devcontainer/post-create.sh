#!/usr/bin/env bash
# Runs once when the Codespace is created: warms up language dependencies so that
# tests and local image builds are fast afterwards.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Installing Python dev dependencies (catalog-service)"
pip install --user -q -r services/catalog-service/requirements-dev.txt

echo "==> Resolving Maven dependencies (order-service)"
(cd services/order-service && mvn -B -q dependency:go-offline) || echo "WARN: Maven warm-up failed (will retry on first build)"

echo "==> Shell helpers"
grep -q "alias k=kubectl" ~/.bashrc 2>/dev/null || cat >> ~/.bashrc <<'EOF'
alias k=kubectl
source <(kubectl completion bash)
complete -o default -F __start_kubectl k
EOF

echo "==> post-create finished"
