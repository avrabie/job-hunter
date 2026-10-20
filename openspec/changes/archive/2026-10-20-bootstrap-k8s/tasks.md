# Tasks

Apply order for the whole project: `bootstrap-modulith-skeleton`, `bootstrap-auth`, `bootstrap-gateway`, then this change. Groups 1 to 7 only need the placeholder `ui` and can be done and verified earlier; group 8 needs the three application images.

## 1. Layout and validation tooling

- [x] 1.1 Create `k8s/{platform,base,overlays/local,overlays/prod}` and a `k8s/.gitignore` for `**/secrets.yaml`; verify `git check-ignore k8s/base/secrets.yaml` reports it ignored
- [x] 1.2 Write `k8s/validate.sh` (render each overlay with `kubectl kustomize`, validate with `kubeconform` using Kubernetes, Gateway API and cert-manager schemas, fail on any error); verify it passes on a minimal valid overlay and exits non-zero naming the resource when a field is given a wrong type
- [x] 1.3 Add checks to `validate.sh` that every container has CPU/memory requests and a memory limit and that no image uses `latest`; verify each check fails on a deliberately bad fixture and passes on the real manifests

## 2. Cluster guide (`k8s/README.md`, `k8s/platform/`)

- [x] 2.1 Write the starting checks of the guide: SSH tunnel and tunnelled kubeconfig (`127.0.0.1:6443`, with the `--tls-server-name` fallback), node Ready and schedulable (taint removal command if needed), CNI enforces network policies; verify each command against the existing Hetzner cluster and record the output in the guide
- [x] 2.2 Document the `ufw` rules (allow 22, 80, 443; forward policy and pod/service CIDR rules so pod networking works; API server closed) and the DNS A record for `s4v3.net`; verify from another machine that only 22, 80 and 443 answer, and on the cluster that pod-to-pod, DNS and outbound traffic still work
- [x] 2.3 Document and pin the Gateway API CRDs and Traefik (Helm values `platform/traefik-values.yaml`: Gateway provider on, `hostPort` 80 and 443, `GatewayClass`); verify on kind that the gateway class is accepted, then on the Hetzner cluster that port 80 answers from outside
- [x] 2.4 Document and pin cert-manager with its Gateway API support enabled, and add `platform/cluster-issuers.yaml` (Let's Encrypt staging and production, HTTP-01 through the Gateway); verify both issuers register (Ready) on a cluster with outbound access
- [x] 2.5 Document installing local-path-provisioner (pinned) as the default storage class, and state that local disk is a single point of failure with no backups yet; verify a test PVC without a class is bound on the Hetzner cluster and on kind
- [x] 2.6 Document the local test cluster (kind create command with the port mapping to `localhost:8080`, delete command); verify the cluster comes up and is deleted again as written
- [x] 2.7 Document building each image with `bootBuildImage` and pushing to Docker Hub as `moldovean/job-hunter-backend`, `-auth` and `-gateway` with explicit tags (including an optional pull secret for private images); verify `./gradlew bootBuildImage` succeeds for `backend` using the documented command

## 3. Base: namespace, configuration, secrets

- [x] 3.1 Add `base/namespace.yaml`, `base/config.yaml` (ConfigMap with issuer URI, service addresses, database hosts) and `base/kustomization.yaml`; verify `kubectl kustomize k8s/base` renders
- [x] 3.2 Add `base/secrets.yaml.example` (database passwords, placeholders only) and a README section on creating the real file; verify a pod referencing a missing secret stays `CreateContainerConfigError` with the secret named in the event, and that a repository search finds no real credential

## 4. PostgreSQL

- [x] 4.1 Add the `postgres` StatefulSet (pinned `pgvector/pgvector` `pg16` tag, PVC, `PGDATA` subdirectory, probes, resources, `Recreate`-equivalent single replica) and a ClusterIP Service; verify on kind that the pod is ready and `psql` connects
- [x] 4.2 Add the init script ConfigMap creating roles and databases `authdb` and `jobhunter` and `CREATE EXTENSION IF NOT EXISTS vector` in `jobhunter`; verify on a fresh volume that both databases exist and `\dx` lists `vector` in `jobhunter`
- [x] 4.3 Verify role separation (the `auth` role is refused on `jobhunter`, the `backend` role on `authdb`) and that deleting the pod keeps the data and does not re-run initialisation
- [x] 4.4 Add a note to `bootstrap-modulith-skeleton` task 3.2 and its design that the baseline changeset uses `CREATE EXTENSION IF NOT EXISTS vector`; verify `openspec validate bootstrap-modulith-skeleton --strict` still passes

## 5. Application workloads

- [x] 5.1 Add `backend` Deployment and Service (container port 8081 via `SERVER_PORT`, to match the gateway's local default; image reference, env from ConfigMap and Secret, startup/liveness/readiness probes, resources, `Recreate`); verify `validate.sh` passes
- [x] 5.2 Add `auth` Deployment and Service in the same way (context path `/auth`, fixed public issuer); verify `validate.sh` passes
- [x] 5.3 Add `gateway` Deployment and Service (targets from `MICROSERVICES_BACKEND_URI` and `MICROSERVICES_AUTH_URI`, as defined by `bootstrap-gateway`; port 8080, readiness independent of `auth`); verify `validate.sh` passes
- [x] 5.4 For each Deployment, confirm the environment variable names against the other changes' specs and design (`bootstrap-modulith-skeleton`, `bootstrap-auth`, `bootstrap-gateway`) and record any mismatch as a note in that change; verify by listing each name next to the setting it maps to in the README

## 6. Placeholder UI and edge

- [x] 6.1 Add the `ui` placeholder (pinned public nginx image, ConfigMap with a static page, RollingUpdate, resources, probes) and its Service; verify on kind that its page is served inside the cluster
- [x] 6.2 Add `base/edge.yaml` with the `Gateway` (HTTP and HTTPS listeners, cert-manager issuer annotation left to overlays) and `HTTPRoute`s for `/` to `ui` and `/api`, `/auth` to `gateway`; verify `validate.sh` passes and, on kind, that the routes are reported as accepted
- [x] 6.3 Verify on kind with a stub backend response (for example a temporary echo pod behind the `gateway` Service name) that `/` returns the page and `/api/x` and `/auth/x` reach the gateway Service; remove the stub afterwards

## 7. Isolation and overlays

- [x] 7.1 Add `base/network-policies.yaml` (default deny ingress, allowed paths from the design); verify on kind that `backend` reaches `postgres` and that a throwaway pod labelled like `gateway` or `ui` is blocked
- [x] 7.2 Add `overlays/local` (HTTP-only listener on `localhost`, local image names, small resources, a way for pods to reach the issuer URL, e.g. host alias or in-cluster issuer for local use); verify it renders and passes `validate.sh`
- [x] 7.3 Add `overlays/prod` (hostname `s4v3.net`, issuer `https://s4v3.net/auth`, HTTPS listener with certificate, HTTP-to-HTTPS redirect route, production issuer annotation, explicit image tags, production resources); verify it renders and passes `validate.sh`, and with the staging issuer that a certificate becomes ready for `s4v3.net` once its A record points at the server
- [x] 7.4 Verify the two overlays differ only in hostname, TLS and issuer settings, image references and resource sizes by diffing the rendered output, and document the result

## 8. Integration (needs the `backend`, `auth` and `gateway` images)

- [x] 8.1 Build the three images with the documented commands, load them into the kind cluster, apply the local overlay, and verify all pods become ready
- [x] 8.2 Verify through `localhost:8080`: `/` returns the page, `/auth/.well-known/openid-configuration` returns a document whose `issuer` is the configured public URL, and `/api/` without a token returns 401
- [x] 8.3 Verify the full login path: obtain a token from `auth` through the edge, call `/api/` with it, and confirm a user record is created in `jobhunter` and the call succeeds; then stop `auth`, confirm `backend` stays running and rejects requests, and confirm it recovers when `auth` is back
- [x] 8.4 Verify rollout and persistence: roll out a new `backend` tag on the single node without the pod staying pending, and delete the `postgres` pod and confirm the data is kept
- [x] 8.5 Walk through `k8s/README.md` from a clean state on a fresh kind cluster and fix every step that does not work as written

## Workflow follow-up

- Archive the change after groups 1 to 8 pass.
- Follow-up changes: CI image build and publish, backup CronJob and off-node target, real UI workload, observability, and production rollout on the new server.
