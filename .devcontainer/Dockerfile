FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

ARG TARGETARCH
ARG KIND_VERSION=v0.24.0
ARG K6_VERSION=v0.54.0
ARG K9S_VERSION=v0.32.7
ARG KUBECONFORM_VERSION=v0.6.7

RUN set -eux; \
    arch="${TARGETARCH:-amd64}"; \
    # kind: local Kubernetes cluster in Docker
    curl -fsSLo /usr/local/bin/kind "https://kind.sigs.k8s.io/dl/${KIND_VERSION}/kind-linux-${arch}"; \
    chmod +x /usr/local/bin/kind; \
    # k6: load testing CLI
    curl -fsSL "https://github.com/grafana/k6/releases/download/${K6_VERSION}/k6-${K6_VERSION}-linux-${arch}.tar.gz" \
      | tar -xz --strip-components=1 -C /usr/local/bin "k6-${K6_VERSION}-linux-${arch}/k6"; \
    # k9s: terminal UI for Kubernetes
    curl -fsSL "https://github.com/derailed/k9s/releases/download/${K9S_VERSION}/k9s_Linux_${arch}.tar.gz" \
      | tar -xz -C /usr/local/bin k9s; \
    # kubeconform: manifest validation
    curl -fsSL "https://github.com/yannh/kubeconform/releases/download/${KUBECONFORM_VERSION}/kubeconform-linux-${arch}.tar.gz" \
      | tar -xz -C /usr/local/bin kubeconform; \
    kind version; k6 version; k9s version --short; kubeconform -v
