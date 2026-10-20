# Proposal

## Why

`backend`, `auth` and `gateway` are three independently built services that only work together behind one public HTTPS address (`/` to the UI, `/auth` and `/api` through the gateway). Nothing yet describes how the cluster is prepared for them or how they are wired together. Writing that down as code and instructions, in a new `k8s/` folder, makes the deployment reproducible on the single-node cluster and lets us test the routing locally before any real traffic exists.

## What Changes

- Add `k8s/` with a README that starts from the existing single-node Kubespray cluster on Hetzner (plain Kubernetes, nothing else installed) and adds what the application needs: a default storage class, the Kubernetes Gateway API CRDs, a Gateway API implementation (Traefik, exposed on the node's ports 80 and 443), and cert-manager with Let's Encrypt issuers.
- Add Kustomize `base/` manifests: namespace, config, secrets template, PostgreSQL with pgvector (StatefulSet, PVC, init of the `authdb` and `jobhunter` databases), and the `backend`, `auth` and `gateway` workloads with probes, resource requests/limits and a rollout strategy suited to one node.
- Add the edge: one `Gateway` with HTTPS and HTTP listeners, an `HTTPRoute` sending `/` to `ui` and `/api`, `/auth` to the Spring gateway, an HTTP-to-HTTPS redirect, and certificates issued by cert-manager.
- Add a placeholder `ui` workload (stock nginx, one static page) so the `/` route can be verified before the real UI exists.
- Add `overlays/local` (kind, no TLS, no real domain) and `overlays/prod` (hostname `s4v3.net`, Let's Encrypt).
- Add network policies so the database is reachable only from `backend` and `auth`.
- Add a validation script (`kustomize build` plus schema validation) and document how to build and push images to Docker Hub (`moldovean/...`) by hand, and how to reach the cluster through an SSH tunnel.

**Explicitly out of scope** (later changes): CI that builds and publishes images, a CD tool such as Argo CD, the real UI workload and its image, backup CronJob and its off-node target, metrics/log/trace stack, Claude API key and BYO-key encryption secret (added with the `llm` work), SMTP credentials, autoscaling, multi-node or HA setups, any change to the application code or to its Liquibase changelogs beyond the extension handling in design.md.

## Capabilities

### New Capabilities
- `cluster-platform`: the documented, reproducible setup of a new single-node cluster with Gateway API, a Gateway implementation, certificate management and persistent storage.
- `edge-routing`: public hostname, TLS, HTTP-to-HTTPS redirect and path routing from the edge to `ui` and through the Spring gateway to `auth` and `backend`.
- `workload-deployment`: how `postgres`, `backend`, `auth`, `gateway` and the placeholder `ui` run in the cluster: configuration, secrets, health probes, resources, rollout, network isolation, and the local and production overlays.

### Modified Capabilities
<!-- None. The application capabilities (accounts, platform-runtime, ...) are unchanged. -->

## Impact

- **Modules touched:** none of the Modulith modules. Application code is unchanged. The change relies on the endpoints and settings defined in `bootstrap-modulith-skeleton` (health endpoints, issuer URI and database settings from environment), `bootstrap-auth` (fixed issuer, `/auth` context path) and `bootstrap-gateway` (target service URIs from environment). Where those names are not yet fixed, this change records the names it expects and the apply order is skeleton, auth, gateway, then k8s.
- **New files:** everything under `k8s/`.
- **Dependencies (cluster side):** Gateway API CRDs, Traefik, cert-manager, a local-path storage provisioner, Helm, kind for local testing, `kubectl`, `kustomize` (via `kubectl kustomize`), and a schema validator such as kubeconform.
- **Personal data:** the Postgres volume will hold user accounts, and later CVs. This change defines where that volume lives, who can reach the database, and where credentials come from. Real secrets are never committed; only `*.example` templates are. No CV content exists yet.
- **Per-user scoping:** unaffected. Isolation between users stays an application rule.
- **Claude cost:** none. No LLM calls and no Anthropic key in this change.
- **External systems:** Let's Encrypt (certificates), Docker Hub namespace `moldovean` (images), and a DNS A record for `s4v3.net` pointing at the server.
