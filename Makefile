OVERLAY   ?= deploy/kubernetes/overlays/lab
NAMESPACE ?= o11y-lab
KUBECTL   ?= kubectl

.PHONY: help test test-python test-java render deploy undeploy status up down

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-14s %s\n", $$1, $$2}'

test: test-python test-java ## Run all unit tests

test-python: ## Run catalog-service tests
	cd services/catalog-service && pip install -q -r requirements-dev.txt && pytest -q

test-java: ## Run order-service tests
	cd services/order-service && mvn -B -q verify

render: ## Render Kubernetes manifests for OVERLAY
	$(KUBECTL) kustomize $(OVERLAY)

deploy: ## Deploy OVERLAY to the current kube context
	$(KUBECTL) apply -k $(OVERLAY)
	$(KUBECTL) -n $(NAMESPACE) rollout status deploy --timeout=300s

undeploy: ## Remove the application from the cluster
	$(KUBECTL) delete -k $(OVERLAY) --ignore-not-found

status: ## Show pods and services
	$(KUBECTL) -n $(NAMESPACE) get pods,svc,ingress

up: ## Run locally with Docker Compose (incl. load generator)
	docker compose --profile load up --build

down: ## Stop local Docker Compose stack
	docker compose --profile load down -v
