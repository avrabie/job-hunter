# Design

## Context

`k8s/` is empty. The application is three independent Gradle builds (`backend`, `auth`, `gateway`) that do not yet have images or Dockerfiles. The target is the single-node Kubernetes cluster already installed with Kubespray on a fresh Hetzner server (plain Kubernetes, nothing else installed, so no bundled ingress, load balancer or storage provisioner), with Gateway API at the edge and the Spring Cloud Gateway behind it (see PLAN.md sections 2 and 8). Images go to Docker Hub (decided). Granite-Security/sample-shop is the reference for the Gateway/HTTPRoute layout, probes and rollout lessons; its cluster is shared and is not reused. See proposal.md for scope.

Assumptions recorded here because they were not stated: the Gateway API implementation is **Traefik**, installed with Helm and exposed through `hostPort` 80 and 443 on the node (no cloud load balancer exists; MetalLB is the alternative); the default storage class is **local-path-provisioner** (Rancher), installed by us; cert-manager is installed with Helm instead of Kubespray's addon so its version is pinned here and Gateway API support can be enabled; and the local test cluster is **kind** (vanilla Kubernetes, closest to Kubespray) with the same Traefik. The public hostname is `s4v3.net` (UI, `/auth` and `/api` on that one host) and images are `moldovean/job-hunter-backend`, `moldovean/job-hunter-auth`, `moldovean/job-hunter-gateway` on Docker Hub (assumed public; a private repository needs an image pull secret). Operators reach the Kubernetes API only through an SSH tunnel, and the server firewall is `ufw`.

## Goals / Non-Goals

**Goals:**
- The existing cluster can be prepared from `k8s/README.md` alone, and the same manifests can be tested locally on kind.
- Every manifest is rendered and schema-checked by one command.
- The routes in `edge-routing` are proven on the local cluster before anything is deployed to production.

**Non-Goals:**
- CI/CD, image build automation, backups, observability, autoscaling, HA, secrets managers.
- Changes to application code. Where an application must change (see Decisions: extension handling, `auth` probes), that is recorded as a requirement on its own change.

## Decisions

**Layout.** `k8s/README.md`, `k8s/platform/` (cluster-wide pieces: Gateway API CRDs reference, Traefik values, cert-manager issuers), `k8s/base/` (namespace, config, postgres, backend, auth, gateway, ui, network policies, edge), `k8s/overlays/local` and `k8s/overlays/prod`, `k8s/validate.sh`. Platform pieces are applied once per cluster by the operator; base and overlays are applied per environment. Alternative: Helm chart. Rejected: Kustomize is plain YAML, is built into `kubectl`, and matches sample-shop.

**Kubernetes Gateway API at the edge, not Ingress.** One `Gateway` with listeners `https` and `http` for the hostname; `HTTPRoute`s: `/` to `ui`, `/api` and `/auth` to `gateway`, plus an HTTP route with a `RequestRedirect` filter (scheme `https`, status 301) in prod. Alternative: route `/api` and `/auth` straight to the services. Rejected for now because the Spring gateway is a decided part of the architecture; the route targets change in one place if that decision is revisited. Path matching uses `PathPrefix`; no URL rewrite, so `auth` keeps its `/auth` context path and `backend` sees `/api/...`.

**Fixed public issuer.** `auth` and `backend` are configured with the issuer `https://<host>/auth`. `backend` fetches discovery over that public URL from inside the cluster, so the prod hostname must resolve in-cluster (it does via the node's public IP with Traefik's host port, or with a CoreDNS rewrite as sample-shop did in `coredns-custom.yaml`). We verify this on the first prod rollout and add the rewrite if needed; the local overlay uses the kind host mapping. Alternative: a separate in-cluster issuer URL. Rejected because the JWT `iss` must be one stable value.

**Forwarded headers.** Traefik sets `X-Forwarded-Host/Proto`; the Spring gateway (Spring Cloud Gateway, reactive) must forward them untouched and must not enable the forwarded-header strategy, as sample-shop's README documents. That requirement belongs to `bootstrap-gateway`; this change only verifies it end to end (the "Issuer is public" scenario).

**PostgreSQL.** StatefulSet, one replica, image `pgvector/pgvector` at a pinned `pg16` tag (same as the tests), a PVC from the default storage class, `PGDATA` in a subdirectory. An init script in a ConfigMap (mounted at `/docker-entrypoint-initdb.d`) creates two roles and two databases and runs `CREATE EXTENSION IF NOT EXISTS vector` in `jobhunter` as the superuser. Reason: creating the extension may require superuser rights that the application role should not have. This means the baseline Liquibase changeset in `bootstrap-modulith-skeleton` must use `CREATE EXTENSION IF NOT EXISTS vector` so it is a no-op here; that is a one-line note for that change's task 3.2. Passwords come from a Secret. Alternative: CloudNativePG. Deferred, as in PLAN.md.

**Workload rollout.** `strategy: Recreate` and one replica for `postgres`, `backend`, `auth` and `gateway`, because a surge pod may not fit on a single node (sample-shop saw a rollout stay pending). Cost: a few seconds of downtime per deploy. `ui` is stateless and may use RollingUpdate.

**Probes.** HTTP probes on `/actuator/health/liveness` and `/actuator/health/readiness` on each Spring app's management/server port. Readiness includes the database; liveness does not (per `platform-runtime` in the skeleton change; `bootstrap-auth` must provide the same). `gateway` readiness must not depend on `auth`. `startupProbe` with a generous failure threshold avoids liveness kills during the JVM start.

**Resources.** Starting values (adjust after measuring): `backend` request 512Mi / limit 1Gi, `auth` 384Mi / 768Mi, `gateway` 256Mi / 512Mi, `postgres` 512Mi / 1Gi, `ui` 32Mi / 64Mi; CPU requests only (100m to 250m), no CPU limits. JVM heap follows container limits (`-XX:MaxRAMPercentage`).

**Configuration and secrets.** A ConfigMap holds non-secret settings; a Secret created from `secrets.yaml` (git-ignored, with `secrets.yaml.example` committed) holds database passwords. Environment variable names follow Spring's relaxed binding (`SPRING_DATASOURCE_URL`, and so on) and the names `bootstrap-gateway` defines for its targets; mismatches are caught by the local end-to-end task. The Anthropic key, BYO-key encryption key and SMTP credentials are added by the changes that introduce them.

**Network policies.** Default-deny ingress in the namespace; allow edge to `ui` and `gateway`; `gateway` to `backend` and `auth`; `backend` and `auth` to `postgres` and to each other's public issuer path through the edge; Traefik to the namespace. Kubespray's default CNI (Calico) enforces NetworkPolicy; the guide's first check confirms it, and kind's default CNI is checked in task 7.1. Egress is left open for now (Claude API, job sites, DNS); restricting it is later work.

**Logging.** Containers log to stdout; Traefik access logs stay off, or at minimum exclude the `Authorization` header (Traefik's default access log does not record request headers unless configured).

**Local overlay.** kind cluster (control-plane node labelled for the ingress `hostPort`) with ports mapped to `localhost:8080`; Gateway listener on HTTP only, hostname `localhost`; issuer `http://localhost:8080/auth`. Because the pods cannot resolve `localhost` to the edge, the overlay adds a `hostAliases`/CoreDNS entry or sets the issuer to an in-cluster name for local use only. Alternative: skip token validation locally. Rejected because it would not test the real path. Exact trick is settled in task 7.2.

**Cluster access and firewall.** `ufw` allows 22, 80 and 443 only. The API server (6443) stays closed to the internet; the guide documents `ssh -L 6443:127.0.0.1:6443 <user>@<server>` and a kubeconfig whose server is `https://127.0.0.1:6443`. This works only if the API server certificate lists `127.0.0.1` as a name, which Kubespray normally does; the guide's first check verifies it and gives the `--tls-server-name` fallback. `ufw` and Kubernetes can conflict: a default-deny forward policy blocks pod traffic, so the guide sets the forward policy and pod/service CIDR rules deliberately and then checks pod-to-pod, DNS and outbound traffic. `hostPort` traffic is forwarded by the CNI's iptables rules rather than ufw's input rules, so the check that only 22, 80 and 443 answer from outside is run from another machine, not assumed.

**Images.** Built by hand with the Spring Boot Gradle plugin's `bootBuildImage` (buildpacks, no Dockerfile) and pushed to Docker Hub with explicit tags; the README documents the commands. Dockerfiles and CI publishing are a later change. The `ui` placeholder uses a pinned public nginx image with a ConfigMap page.

**Validation.** `k8s/validate.sh` runs `kubectl kustomize` for each overlay and pipes it to `kubeconform` with the Gateway API and cert-manager CRD schemas, plus a few `grep`/`yq` assertions (no `latest` tag, resources present). New tools: `kubeconform`, `kind`, `helm`, `kubectl`. The existing stack has none of them, and they are needed only on the developer machine.

**Module boundaries and database schema.** No Modulith module changes. Database: databases and roles are created by the cluster init script, not by Liquibase; Liquibase keeps owning all tables. The only application-side effect is the `IF NOT EXISTS` note above.

**Implementation notes (learned while building).**
- *Existing cluster state.* The Kubespray cluster already had Calico (enforces NetworkPolicy, proven on the cluster), local-path-provisioner v0.0.37 as the default storage class, a `ufw` ruleset that already allows pod traffic, and an A record for `s4v3.net`. The guide therefore verifies these instead of installing them; only Gateway API CRDs v1.6.3, Traefik chart 42.0.0 and cert-manager v1.21.2 were installed.
- *Listener ports.* Traefik's container ports are 8000 and 8443, published as the node's 80 and 443 with `hostPort`, so Gateway listeners use 8000 and 8443. `hostPort` traffic bypasses `ufw`'s input chain (ports 80 and 443 answered before the allow rules existed), so `ufw` rules document intent and the external port check is the real test. Traefik uses the `Recreate` update strategy, because a surge pod cannot bind the same host ports.
- *Chart CRDs.* The Traefik chart ships its own Gateway API CRDs; it is installed with `--skip-crds` so the pinned v1.6.3 set stays the only source.
- *Neutral base.* The base carries no hostname and placeholder public URLs; each overlay sets its own, so the overlays differ only in the places the spec lists.
- *Local issuer.* A pod cannot reach `http://localhost:8080`, so the local overlay gives `backend` the signing-key URL of `auth` (`...jwt.jwk-set-uri`) while it still requires the public issuer in every token. No host alias or CoreDNS rewrite was needed. The production overlay relies on the public name resolving from inside the cluster; this is to be confirmed at the first production rollout.
- *Memory.* The buildpack's memory calculator reserves 240 MB of code cache and fails below about 420 MB of fixed regions; all three services set `JAVA_TOOL_OPTIONS=-XX:ReservedCodeCacheSize=64M`.
- *Image build on Docker Desktop.* The Boot Gradle plugin fails with `'username' must not be null` while pulling; pre-pulling the builder and run image with the docker CLI and `--pullPolicy=IF_NOT_PRESENT` works. Production images must be built with `--imagePlatform=linux/amd64` on an Apple-silicon machine.
- *Follow-ups found.* `auth` generates its signing key at startup, so each restart invalidates all tokens; and a cold `backend` takes about 30 seconds to reject a token when `auth` is down. Both belong to the application changes, not to this one.

## Risks / Trade-offs

- [Applications are not built yet, so manifests cannot be fully proven] → Tasks split into things checkable now (render, schema, dry-run, placeholder routing) and an integration group that waits for the three images; the stated apply order is skeleton, auth, gateway, then this change.
- [Environment variable names may not match what the apps read] → Names are taken from the other changes as they are written; the local end-to-end task catches mismatches.
- [Public issuer must resolve from inside the cluster] → Verified on the first rollout, with a CoreDNS rewrite as the documented fix.
- [Gateway API and Traefik versions move] → Pin versions in the guide, check the Traefik release notes for Boot-independent breaking changes before upgrades.
- [Recreate strategy causes brief downtime] → Accepted for one node; revisit with a second node.
- [Local disk is the single point of failure and there are no backups yet] → Documented in the guide; backup CronJob is the next infrastructure change.
- [Assumed Traefik via hostPort and local-path storage] → If you prefer MetalLB or another implementation or provisioner, `platform/` and the guide change; `base/` and `overlays/` do not, apart from the `GatewayClass` name.
- [Control-plane taint] → On a single-node Kubespray cluster the node must accept ordinary pods; the guide's first check verifies this and gives the command to remove the taint if present.
- [ufw vs pod networking] → Misconfigured forwarding can silently break pods or DNS; the guide ends with pod-to-pod, DNS, outbound and external-port checks.
- [App on the apex domain] → `s4v3.net` serves only this application, so its `/auth` and `/api` paths and the JWT issuer `https://s4v3.net/auth` belong to it alone. If another site or app ever needs `s4v3.net`, the issuer URL would have to change (it is the JWT `iss`), so a subdomain would be safer then. The apex also needs an A record, not a CNAME, and the DNS zone's existing records (for example MX for mail) are left as they are.
- [Hostname and DNS] → The A record for `s4v3.net` must point at the node's public IP before HTTP-01 can issue a certificate; no DNS provider API is needed for HTTP-01.

## Open Questions

- Public hostname and DNS provider (needed only for the prod overlay and for certificates).
- Docker Hub namespace and whether images are public or private (private needs an image pull secret).
