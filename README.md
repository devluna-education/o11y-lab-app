# O11y Lab Shop

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://codespaces.new/devluna-education/o11y-lab-app?quickstart=1)
[![ci](https://github.com/devluna-education/o11y-lab-app/actions/workflows/ci.yaml/badge.svg)](https://github.com/devluna-education/o11y-lab-app/actions/workflows/ci.yaml)
[![e2e](https://github.com/devluna-education/o11y-lab-app/actions/workflows/e2e.yaml/badge.svg)](https://github.com/devluna-education/o11y-lab-app/actions/workflows/e2e.yaml)

Aplicação de microserviços **sem nenhuma instrumentação de observabilidade** (sem SDKs, agentes,
anotações ou variáveis de fornecedor), pronta para rodar em Kubernetes e servir de alvo para testar
soluções de observabilidade: auto‑instrumentação, coleta de logs, APM, tracing distribuído,
métricas de infraestrutura, alertas e detecção de anomalias.

## Arquitetura

```
             ┌──────────────┐
 browser ──► │  frontend    │  nginx (estático + reverse proxy)
 k6 ───────► │  :8080       │
             └──┬────────┬──┘
   /api/catalog │        │ /api/orders
                ▼        ▼
   ┌────────────────┐  ┌────────────────┐   HTTP    ┌────────────────┐
   │ catalog-service│◄─┤ order-service  │──────────►│  (catalog)     │
   │ Python/FastAPI │  │ Java/Spring    │           └────────────────┘
   └───────┬────────┘  └───────┬────────┘
           ▼                   ▼
       ┌───────┐         ┌────────────┐
       │ Redis │         │ PostgreSQL │
       └───────┘         └────────────┘
```

| Serviço | Stack | O que gera de telemetria interessante |
|---|---|---|
| `frontend` | nginx 1.27 (unprivileged) | access logs, proxy para 2 backends |
| `catalog-service` | Python 3.12, FastAPI, Uvicorn, Redis | chamadas Redis, cache hit/miss, endpoint CPU‑bound (`/recommendations`), thread de restock em background |
| `order-service` | Java 21, Spring Boot 3.3, JPA/Hibernate, Flyway | HTTP de saída para o catálogo, JDBC no PostgreSQL, job agendado (`CREATED → PAID → SHIPPED`), erros 4xx/5xx mapeados |
| `load-generator` | k6 | tráfego contínuo de navegação + compras, ~5% de requisições inválidas (400/404) e 409 por falta de estoque |
| `postgres` / `redis` | imagens oficiais | dependências de dados |

### Fluxo de um pedido (trace distribuído)

`POST /api/orders` → nginx → order-service → `GET catalog/products/{id}` → Redis →
`POST catalog/products/{id}/reserve` → Redis → `INSERT` no PostgreSQL.

## Injeção de falhas (chaos)

Pela UI (aba **Chaos**) ou por API:

| Endpoint (via frontend) | Efeito |
|---|---|
| `/api/catalog/chaos/error`, `/api/order-chaos/error` | exceção não tratada (HTTP 500) |
| `/api/catalog/chaos/cpu?duration_ms=2000`, `/api/order-chaos/cpu?durationMs=2000` | CPU 100% durante N ms |
| `/api/catalog/chaos/slow?duration_ms=3000`, `/api/order-chaos/slow?durationMs=3000` | resposta lenta |

Falhas contínuas por configuração (ConfigMaps `config.env` de cada serviço):

- `CHAOS_ERROR_RATE` – fração (0.0–1.0) de requisições de negócio que retornam 500
- `CHAOS_LATENCY_MS` – latência extra aleatória (0..N ms)

## Início rápido no GitHub Codespaces

1. Clique no botão **Open in GitHub Codespaces** acima (ou *Code → Codespaces → Create codespace on main*).
   Máquina mínima: 4 vCPU / 8 GB (definido em `hostRequirements`).
2. Aguarde a criação. O devcontainer ([`.devcontainer/`](.devcontainer/)) já vem com:
   Docker (docker‑in‑docker), **kind**, kubectl, Helm, **k6**, k9s, kubeconform, **Java 21 + Maven**,
   **Python 3.12** e GitHub CLI. Na inicialização, [`scripts/cluster-up.sh`](scripts/cluster-up.sh)
   cria (ou religa) o cluster Kubernetes `kind-o11y-lab` e deixa o `kubectl` apontando para ele.
3. No terminal, faça o deploy:

   ```bash
   kubectl apply -k deploy/kubernetes/overlays/lab
   ```

   Usa as imagens publicadas no GHCR. Para testar alterações no código sem publicar imagens:
   `make deploy-local` (build local → `kind load` → deploy do overlay `local`).
4. Acompanhe: `kubectl -n o11y-lab get pods -w` (ou `k9s`). O order-service leva ~1 min para ficar pronto.
5. Abra a aba **Ports** → porta **30081** (*O11y Lab Shop*) para acessar a UI.

Outros comandos úteis: `make status`, `make load` (k6 rodando a partir do Codespace contra a
NodePort), `make undeploy`, `make cluster-down`.

Ao parar e retomar o Codespace, o cluster é religado automaticamente; se não puder ser
recuperado, ele é recriado (e o deploy precisa ser refeito).

> O mesmo fluxo (`cluster-up.sh` + `deploy-local.sh` + smoke test) roda no workflow
> [`e2e`](.github/workflows/e2e.yaml) a cada push, garantindo que o deploy em kind funciona.

## Deploy no Kubernetes

Pré‑requisitos: `kubectl` ≥ 1.27 (Kustomize embutido) e um cluster com StorageClass padrão
(ex.: k3s `local-path`).

```bash
kubectl apply -k deploy/kubernetes/overlays/lab
```

```bash
kubectl -n o11y-lab get pods
```

Acesso:
- Ingress (Traefik por padrão): `http://<host-do-cluster>/`
- NodePort: `http://<ip-do-node>:30081/`
- Port-forward: `kubectl -n o11y-lab port-forward svc/frontend 8080:8080`

Remover:

```bash
kubectl delete -k deploy/kubernetes/overlays/lab
```

### Estrutura dos manifestos

```
deploy/kubernetes/
├── base/                    # manifestos neutros (sem namespace, sem registry)
│   ├── catalog-service/     # Deployment, Service, config.env -> ConfigMap
│   ├── order-service/
│   ├── frontend/
│   ├── load-generator/
│   ├── postgres/            # StatefulSet + PVC
│   └── redis/
└── overlays/
    ├── lab/                 # namespace, imagens GHCR, Secret do DB, Ingress, NodePort
    ├── local/               # = lab, com imagens buildadas localmente (kind / Codespaces)
    └── rum/                 # injeta snippet de RUM no frontend via nginx sub_filter (sem rebuild)
```

Nenhum código fica dentro dos YAMLs: o código vive em `services/*` e é empacotado em imagens
construídas pelo GitHub Actions. Configuração vem de arquivos `.env` transformados em ConfigMaps
pelo Kustomize (o hash no nome força rollout quando a configuração muda).

Para criar um novo ambiente (ex.: outro namespace, outro ingress, tags fixas), copie
`overlays/lab` e ajuste. Para testar uma ferramenta de observabilidade, adicione as anotações/labels
dela num overlay próprio via `patches`, mantendo a base limpa.

Para RUM (monitoramento do navegador), o overlay [`overlays/rum`](deploy/kubernetes/overlays/rum/)
já vem pronto: cole o snippet do fornecedor em `rum-init.js` e rode
`kubectl apply -k deploy/kubernetes/overlays/rum`. Passo a passo no
[README do overlay](deploy/kubernetes/overlays/rum/README.md).

### Imagens

O workflow [`.github/workflows/ci.yaml`](.github/workflows/ci.yaml) roda testes, valida os
manifestos e publica imagens multi‑arch (amd64/arm64) em
`ghcr.io/<owner>/o11y-lab-app/<serviço>` com as tags `latest` (main), `sha-<commit>` e semver
(`v*`).

Se o repositório for privado, os pacotes no GHCR também serão privados. Nesse caso, torne os pacotes
públicos (GitHub → Packages → Package settings → Change visibility) ou crie um `imagePullSecret`:

```bash
kubectl -n o11y-lab create secret docker-registry ghcr --docker-server=ghcr.io --docker-username=<usuario> --docker-password=<PAT com read:packages>
```

e referencie‑o via patch no overlay (`spec.template.spec.imagePullSecrets`).

## Rodando localmente

```bash
docker compose --profile load up --build
```

UI em http://localhost:8080. Testes unitários: `make test`.

## Boas práticas aplicadas

- Código separado da infraestrutura; imagens imutáveis e versionadas por commit
- Dockerfiles multi‑stage, usuários não‑root, imagens base glibc (compatíveis com agentes de APM)
- `securityContext` restritivo (non‑root, `readOnlyRootFilesystem`, `drop: ALL`, seccomp)
- Probes de startup/readiness/liveness, requests/limits, graceful shutdown
- Kustomize base + overlays, ConfigMaps/Secrets gerados com hash
- Migrações de schema versionadas (Flyway), validação de entrada, erros como RFC 7807 (ProblemDetail)
- Testes unitários e validação de manifestos (kubeconform) no CI
