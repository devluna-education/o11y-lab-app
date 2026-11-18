# Overlay `rum`: RUM no frontend sem rebuild

Injeta o snippet de RUM (Real User Monitoring) de qualquer fornecedor no frontend nginx,
**sem alterar a imagem** nem o `index.html` do repositório.

## Forma automática (recomendada): `scripts/rum-inject`

Sem editar arquivo nenhum: cole o snippet do fornecedor (todos os passos, do jeito que ele gera)
e o script faz o resto.

```bash
scripts/rum-inject --replay-rate 100 --service o11y-lab-frontend --env lab
```

Cole o snippet, pressione Enter e depois **Ctrl-D**. O script:

1. extrai as tags `<script>`/`<link>` do snippet e gera o `/rum-init.js`;
2. troca o placeholder do `allowedTracingUrls` (`your-api-domain...`) por chamadas same-origin
   de `/api/` (propagação de `traceparent` para o backend);
3. baixa o SDK da CDN do fornecedor **e os chunks carregados sob demanda** (ex.: o gravador do
   session replay) e serve tudo pelo nginx com MIME JavaScript. Isso contorna CDNs que entregam
   chunks como `binary/octet-stream` (caso do OpenObserve 0.4.3, que quebra o replay);
4. gera o overlay em `overlays/rum-generated/` (ignorado pelo git, pois contém o token), aplica,
   espera o rollout, remove ConfigMaps antigos e valida HTML e `Content-Type` dos arquivos.

| Comando | O que faz |
|---|---|
| `scripts/rum-inject` | Lê o snippet colado e aplica |
| `scripts/rum-inject --snippet arquivo.html` | Lê o snippet de um arquivo |
| `scripts/rum-inject --reuse --env staging` | Reaplica o último snippet, mudando um campo |
| `scripts/rum-inject --remove` | Remove o RUM (frontend volta ao original) |
| `scripts/rum-inject --dry-run ...` | Só gera e mostra os manifests |
| `scripts/rum-inject --help` | Todas as opções |

Opções úteis: `--service`, `--env`, `--app-id`, `--version`, `--replay-rate N`,
`--session-rate N`, `--cdn` (não hospedar o SDK), `--base lab|local` (padrão: detecta pela
imagem em uso), `--deployment`/`--container`/`--namespace`/`--html-root` (outro frontend nginx),
`--url` (endereço usado na validação, padrão `http://localhost:30081`).

Atalhos no Makefile: `make rum`, `make rum SNIPPET=arquivo.html ARGS="--replay-rate 100"`,
`make rum-reuse`, `make rum-remove`.

O restante deste README descreve a forma **manual** (editando os arquivos desta pasta), que é
o que o script automatiza.

## Como funciona

| Arquivo | Conteúdo | Onde é montado no container |
|---|---|---|
| `rum-init.js` | JavaScript do fornecedor (loader + init), sem as tags `<script>` | `/usr/share/nginx/html/rum-init.js` → servido em `/rum-init.js` |
| `rum.conf.template` | Regra `sub_filter` do nginx | `/etc/nginx/templates/` → renderizado em `/etc/nginx/conf.d/rum.conf` |
| `kustomization.yaml` | Gera o ConfigMap `frontend-rum-<hash>` e monta os dois arquivos | – |

O `sub_filter` troca `<head>` por `<head><script src="/rum-init.js"></script>` em toda resposta
HTML. Respostas JSON das APIs e arquivos estáticos não são alterados.

## Passo a passo

1. **Escolha a base** em `kustomization.yaml` → `resources`: `../lab` (imagens GHCR, padrão)
   ou `../local` (se você subiu com `make deploy-local`). Tem que ser a mesma usada no deploy.
2. **Cole o snippet** do fornecedor em `rum-init.js`, sem `<script>`/`</script>`:
   primeiro o loader da CDN (se houver), depois o bloco de init.
3. **Correlação com o backend** (opcional): no init, permita propagação de `traceparent` para
   as chamadas same-origin de `/api/`:
   ```js
   match: function (url) { return url.indexOf(window.location.origin + '/api/') === 0; },
   propagatorTypes: ['tracecontext'],
   ```
4. **(Opcional) preconnect**: em `rum.conf.template`, use a variante comentada com
   `<link rel="preconnect">` para os domínios do fornecedor.
5. **Confira e aplique**:
   ```bash
   kubectl kustomize deploy/kubernetes/overlays/rum | grep -n 'frontend-rum'
   ```
   ```bash
   kubectl apply -k deploy/kubernetes/overlays/rum
   ```
   ```bash
   kubectl -n o11y-lab rollout status deploy/frontend
   ```
6. **Valide**:
   ```bash
   curl -s http://localhost:30081/ | head -5
   ```
   ```bash
   curl -s http://localhost:30081/rum-init.js | head -5
   ```
   No navegador (DevTools com *Disable cache*): `rum-init.js`, os bundles do fornecedor e os
   envios de eventos aparecem em **Network**; no Console, o objeto global do SDK existe.

## Alterar o snippet depois

Edite `rum-init.js` (ou `rum.conf.template`) e rode de novo `kubectl apply -k
deploy/kubernetes/overlays/rum`. O hash do ConfigMap muda e o frontend faz rollout sozinho.
Recarregue o navegador sem cache (Ctrl/Cmd+Shift+R).

ConfigMaps de versões anteriores ficam órfãos; para limpar:

```bash
kubectl -n o11y-lab get configmap | grep frontend-rum
```

## Remover o RUM

Reaplique a base usada no deploy:

```bash
kubectl apply -k deploy/kubernetes/overlays/lab
```

## Problemas comuns

| Sintoma | Causa | Correção |
|---|---|---|
| Pod novo preso em `ContainerCreating`, volume aponta para `frontend-rum` sem hash | `namespace:` ausente no `kustomization.yaml` (ConfigMap criado em `default`) | Manter `namespace: o11y-lab`; apagar o ConfigMap que foi para `default` |
| `ErrImageNeverPull` / `ImagePullBackOff` | `resources` aponta para a base errada (`../local` vs `../lab`) | Usar a mesma base do deploy |
| `CrashLoopBackOff` no frontend | Erro de sintaxe no `rum.conf.template` (geralmente aspas simples) | `kubectl -n o11y-lab logs deploy/frontend` |
| `curl /rum-init.js` devolve HTML | Montagem do `rum-init.js` falhou (o nginx cai no `index.html`) | Conferir `volumeMounts` / nomes dos arquivos |
| `curl` OK, mas o navegador não carrega os `.js` | Cache, porta errada, filtro do Network ou bloqueador de anúncios | Ctrl/Cmd+Shift+R, porta 30081, filtro *All*, janela anônima |

## Usar em outro frontend

Funciona para qualquer frontend servido por **nginx** com imagem oficial (ou `nginx-unprivileged`),
que renderiza `/etc/nginx/templates/*.template` na inicialização. Ajuste:

- `patches[].target.name` e `metadata.name` → nome do Deployment do frontend;
- `containers[].name` → nome do container;
- `mountPath` do `rum-init.js` → diretório `root` do nginx desse frontend;
- `namespace:` → namespace do Deployment;
- o alvo do `sub_filter` se o HTML usar `<head ...>` com atributos (ex.: `'</head>'`).

Se o frontend for uma SPA com bundler (React, Angular etc.), o mesmo overlay funciona desde que
o HTML final seja servido por nginx. Para outros servidores (Node, Apache), a injeção precisa de
um mecanismo equivalente ou da instalação via NPM no build.

## Segurança

O `clientToken` de RUM é público por natureza (vai para o navegador de todos os visitantes),
mas em repositório público prefira não comitar o token real: mantenha o `rum-init.js` editado só
no seu ambiente ou rotacione o token quando necessário.
