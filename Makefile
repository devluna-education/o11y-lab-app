OVERLAY   ?= deploy/kubernetes/overlays/lab
NAMESPACE ?= o11y-lab
KUBECTL   ?= kubectl

.PHONY: help test test-python test-java render cluster cluster-down deploy deploy-local undeploy status load up down rum rum-reuse rum-remove

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-14s %s\n", $$1, $$2}'

test: test-python test-java ## Run all unit tests

test-python: ## Run catalog-service tests
	cd services/catalog-service && pip install -q -r requirements-dev.txt && pytest -q

test-java: ## Run order-service tests
	cd services/order-service && mvn -B -q verify

render: ## Render Kubernetes manifests for OVERLAY
	$(KUBECTL) kustomize $(OVERLAY)

cluster: ## Create/ensure the local kind cluster
	bash scripts/cluster-up.sh

cluster-down: ## Delete the local kind cluster
	kind delete cluster --name o11y-lab

deploy: ## Deploy OVERLAY (published GHCR images) to the current kube context
	$(KUBECTL) apply -k $(OVERLAY)
	$(KUBECTL) -n $(NAMESPACE) rollout status deploy --timeout=300s

deploy-local: ## Build images from source, load into kind and deploy
	bash scripts/deploy-local.sh

undeploy: ## Remove the application from the cluster
	$(KUBECTL) delete -k $(OVERLAY) --ignore-not-found

status: ## Show pods and services
	$(KUBECTL) -n $(NAMESPACE) get pods,svc,ingress

load: ## Run k6 from this machine against the NodePort (BASE_URL overrides)
	BASE_URL=$${BASE_URL:-http://localhost:30081} DURATION=$${DURATION:-5m} k6 run services/load-generator/scripts/load-test.js

up: ## Run locally with Docker Compose (incl. load generator)
	docker compose --profile load up --build

down: ## Stop local Docker Compose stack
	docker compose --profile load down -v

rum: ## Inject a RUM snippet into the frontend (paste it, or SNIPPET=file); ARGS for extra flags
	scripts/rum-inject $(if $(SNIPPET),--snippet $(SNIPPET)) $(ARGS)

rum-reuse: ## Re-apply the last RUM snippet (ARGS for extra flags, e.g. ARGS="--env lab")
	scripts/rum-inject --reuse $(ARGS)

rum-remove: ## Remove the RUM injection from the frontend
	scripts/rum-inject --remove
