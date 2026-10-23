# Kubernetes deployment

Everything needed to run Job Hunter on one Kubernetes node: how the cluster is prepared (`platform/`), what is deployed
(`base/`), and how it differs per environment (`overlays/local` for a laptop, `overlays/prod` for `https://s4v3.net`).

```
k8s/
  README.md            this guide
  validate.sh          renders both overlays and checks them (schemas, resources, image tags)
  test-validate.sh     proves validate.sh itself, using test/fixtures
  platform/            applied once per cluster: Traefik and cert-manager values, Let's Encrypt issuers
  base/                namespace, config, secrets template, postgres, backend, auth, gateway, ui, network policies, edge
  overlays/local/      http://localhost:8080 with locally built images and small resources (no cluster uses it now)
  overlays/prod/       s4v3.net: HTTPS, Let's Encrypt, tagged images, full-size resources
```

Traffic path: browser -> node port 80/443 -> Traefik (Gateway API `edge`) -> `/` to `ui`; `/api` and `/auth` to the
Spring `gateway` -> `backend` / `auth`. One host, so the browser sees a single origin.

## 1. Tools on your machine

| Tool | Version used | Install |
|---|---|---|
| kubectl (includes kustomize) | 1.36.1 | `brew install kubectl` |
| helm | 3.14.3 | `brew install helm` |
| kubeconform | 0.8.0 | `brew install kubeconform` |
| yq (mikefarah) | 4.54.1 | `brew install yq` |
| Docker | 29.x | Docker Desktop |

`k8s/validate.sh` needs kubectl, kubeconform and yq. The Kubernetes version used for schema checks is `K8S_VERSION`
(default `1.34.0`, the newest schema set that kubeconform's schema repository publishes at the time of writing).

## 2. Reaching the real cluster

The Kubernetes API (6443) is closed to the internet. Open an SSH tunnel and leave it running:

```
ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -L 16443:127.0.0.1:6443 root@s4v3.net
```

The kubeconfig context `s4v3` points at `https://127.0.0.1:16443`. The API server certificate lists `127.0.0.1`, so
no `--tls-server-name` is needed (if a future cluster's certificate does not, add `--tls-server-name=kubernetes` to
`kubectl`). Always name the context, because other contexts exist on the same machine:

```
alias k='kubectl --context s4v3'
```

### Starting checks (run once, results recorded 2026-10-20)

```
k get nodes -o wide
# node1  Ready  control-plane  v1.36.4  46.224.228.182  Ubuntu 26.04.1 LTS  containerd://2.3.5

k get nodes -o jsonpath='{.items[*].spec.taints}'
# (empty) the node accepts ordinary pods. If it ever shows a control-plane taint, remove it:
#   k taint nodes node1 node-role.kubernetes.io/control-plane:NoSchedule-

k -n kube-system get pods | grep calico
# calico-node-...  1/1 Running   calico-kube-controllers-...  1/1 Running   (Calico v3.31.7 enforces NetworkPolicy)

k get storageclass
# local-path (default)   rancher.io/local-path   Delete   WaitForFirstConsumer
```

Proof that the CNI enforces policies (run on the Hetzner cluster, 2026-10-20): in a throwaway namespace a pod served HTTP,
a `NetworkPolicy` with `policyTypes: [Ingress]` and no rules was applied to it, and a second pod's request timed out
(`BLOCKED`). Before the policy the same request returned the page.

## 3. Firewall (`ufw`)

State found on the server (Kubespray + `ufw`): default deny incoming, allow routed, and these rules:

```
22/tcp                     ALLOW IN  Anywhere
Anywhere on lo             ALLOW IN  Anywhere
Anywhere on cali+          ALLOW IN  Anywhere
Anywhere on vxlan.calico   ALLOW IN  Anywhere
Anywhere                   ALLOW IN  10.233.0.0/16        (pod and service CIDRs)
```

`DEFAULT_FORWARD_POLICY` is `ACCEPT`, so pod traffic is forwarded. Pod CIDR `10.233.64.0/18`, service CIDR
`10.233.0.0/18`. The API server (6443) has no allow rule and stays closed. The two public web ports were added:

```
ssh root@s4v3.net 'ufw allow 80/tcp && ufw allow 443/tcp && ufw status verbose'
```

(Run on 2026-10-20; the rules now appear as `80/tcp` and `443/tcp` ALLOW IN, IPv4 and IPv6.)

Do not touch the `22/tcp` rule, the `cali+` / `vxlan.calico` rules or the `10.233.0.0/16` rule: without them pods lose
networking or you lose SSH.

DNS: `s4v3.net` needs an **A record** to the node's public IPv4 (`46.224.228.182`, already set). The apex cannot be a
CNAME. Leave other records (for example MX) alone.

Check from **another machine** (not the server) that only the intended ports answer:

```
for p in 22 80 443 6443 10250; do nc -z -w 4 s4v3.net $p && echo "$p open" || echo "$p closed/filtered"; done
# 22, 80 and 443 open; 6443 and 10250 closed/filtered
```

Traefik publishes ports 80 and 443 with `hostPort`; that traffic is forwarded by the CNI rules and not by `ufw`'s
input chain (ports 80 and 443 answered even before the `ufw` rules above were added), so this external check is the real
test and must not be skipped.

Pod networking after the firewall rules, verified in a throwaway namespace on the cluster (2026-10-20): a claim without a
class bound, a pod reached another pod through a Service, cluster DNS resolved `kubernetes.default.svc.cluster.local`, and
an outbound HTTPS request to `acme-v02.api.letsencrypt.org` succeeded.

## 4. Platform components (once per cluster)

| Component | Pinned version | Source | Status on the Hetzner cluster |
|---|---|---|---|
| Kubernetes | v1.36.4 | Kubespray | installed |
| Calico (CNI) | v3.31.7 | Kubespray | installed |
| local-path-provisioner | v0.0.37 | Kubespray addon | installed, default storage class |
| Gateway API CRDs (standard channel) | v1.6.3 | kubernetes-sigs/gateway-api | to install |
| Traefik | Helm chart `traefik/traefik` 42.0.0 (Traefik v3.7.14) | traefik.github.io/charts | to install |
| cert-manager | Helm chart `jetstack/cert-manager` v1.21.2 | charts.jetstack.io | to install |

Add the Helm repositories once:

```
helm repo add traefik https://traefik.github.io/charts
helm repo add jetstack https://charts.jetstack.io
helm repo update
```

Every command below names the context explicitly (`--context s4v3`); the default context is the real cluster too.

### 4.1 Storage

local-path-provisioner is already the default class. Verify a claim without a class gets bound (it binds when a pod
uses it, because the class waits for the first consumer; verified on the Hetzner cluster, 2026-10-20: `Bound`,
`STORAGECLASS local-path`, the pod wrote and read a file):

```
cat <<'EOF' | kubectl --context s4v3 apply -f -
apiVersion: v1
kind: PersistentVolumeClaim
metadata: {name: pvc-check, namespace: default}
spec: {accessModes: [ReadWriteOnce], resources: {requests: {storage: 10Mi}}}
---
apiVersion: v1
kind: Pod
metadata: {name: pvc-check, namespace: default}
spec:
  restartPolicy: Never
  containers:
    - name: c
      image: busybox:1.37.0
      command: [sh, -c, "echo ok > /data/x && cat /data/x"]
      volumeMounts: [{name: d, mountPath: /data}]
      resources: {requests: {cpu: 10m, memory: 8Mi}, limits: {memory: 32Mi}}
  volumes: [{name: d, persistentVolumeClaim: {claimName: pvc-check}}]
EOF
kubectl --context s4v3 get pvc pvc-check            # Bound
kubectl --context s4v3 delete pod/pvc-check pvc/pvc-check
```

**Local disk is a single point of failure.** Volumes live on the node's disk (`/opt/local-path-provisioner`). There are
no backups yet; if the server's disk is lost, the database is lost. A backup job is the next infrastructure change.
The class's reclaim policy is `Delete`: deleting a claim deletes its data.

### 4.2 Gateway API CRDs

```
kubectl --context s4v3 apply --server-side -f \
  https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.6.3/standard-install.yaml
kubectl --context s4v3 get crd gateways.gateway.networking.k8s.io \
  -o jsonpath='{.metadata.annotations.gateway\.networking\.k8s\.io/bundle-version}'     # v1.6.3
```

### 4.3 Traefik (the Gateway API implementation)

```
helm --kube-context s4v3 upgrade --install traefik traefik/traefik --version 42.0.0 --skip-crds \
  -n traefik --create-namespace -f k8s/platform/traefik-values.yaml --wait
kubectl --context s4v3 get gatewayclass            # traefik   traefik.io/gateway-controller   ACCEPTED True
```

`--skip-crds` keeps the CRDs from step 4.2 as the only source. The values file explains each choice; the important ones:
Traefik listens on container ports 8000/8443 published as the node's 80/443 (`hostPort`), the Service is `ClusterIP`
(no cloud load balancer exists), the update strategy is `Recreate` (a second pod could not bind the same host ports),
access logs are off (requests carry `Authorization` headers and authorization codes in query strings), and the chart's
own `Gateway` is off because `base/edge.yaml` defines ours.

Check from outside: `curl -i http://s4v3.net/` answers `404 page not found` from Traefik until the application is deployed
(verified 2026-10-20, from a machine outside the server).

### 4.4 cert-manager and the Let's Encrypt issuers

```
helm --kube-context s4v3 upgrade --install cert-manager jetstack/cert-manager --version v1.21.2 \
  -n cert-manager --create-namespace -f k8s/platform/cert-manager-values.yaml --wait
sed "s/ACME_EMAIL/you@example.org/" k8s/platform/cluster-issuers.yaml | kubectl --context s4v3 apply -f -
kubectl --context s4v3 get clusterissuer           # letsencrypt-staging and letsencrypt-production, READY True
```

Both issuers registered and showed `READY True` on the Hetzner cluster (2026-10-20).

`ACME_EMAIL` is the contact address registered with Let's Encrypt (expiry warnings go there); it is substituted at apply
time so the address is not committed. The issuers solve HTTP-01 challenges through the application's Gateway
(`job-hunter/edge`), so the Gateway must exist before a certificate can be issued. `enableGatewayAPI` is switched on in
the values file.

## 5. Building and publishing images

The three Spring Boot images are built with the Spring Boot Gradle plugin (Paketo buildpacks), without a Dockerfile; the
UI image is built from `ui/Dockerfile`. Names: `moldovean/job-hunter-backend`, `moldovean/job-hunter-auth`,
`moldovean/job-hunter-gateway`, `moldovean/job-hunter-ui` on Docker Hub.

**Docker Desktop on macOS:** the plugin fails with `'username' must not be null` while reading Docker Hub credentials
from the credential helper. Work around it by pulling the builder and run image with the `docker` CLI first and telling
the plugin not to pull. The run image tag is printed in the failing build's output; it is currently `0.0.138`.

```
docker pull paketobuildpacks/builder-noble-java-tiny:latest
docker pull paketobuildpacks/ubuntu-noble-run-tiny:0.0.138
```

For production the server is **amd64**; on an Apple-silicon Mac pass the platform, with the amd64 builder and run
images pulled first (`docker pull --platform linux/amd64 ...` for both images above):

```
cd backend && ./gradlew bootBuildImage --imageName=moldovean/job-hunter-backend:0.1.0 \
  --imagePlatform=linux/amd64 --pullPolicy=IF_NOT_PRESENT -x test
docker login -u moldovean
docker push moldovean/job-hunter-backend:0.1.0        # same for -auth and -gateway
```

Pulling the builder for one platform replaces the local copy for the other, so pull again (without `--platform`) before the
next native build. The amd64 backend build was verified (`docker image inspect` reports `amd64`).

The UI image is a normal Docker build (Node 24 build stage, nginx run stage). Test it first, as the cluster runs it
(read-only root filesystem, no capabilities, uid 101), then build for amd64 and push:

```
docker build -t job-hunter-ui:test ui && ui/test-image.sh job-hunter-ui:test
docker build --platform linux/amd64 -t moldovean/job-hunter-ui:0.1.0 ui
docker push moldovean/job-hunter-ui:0.1.0
```

The tag in `overlays/prod/kustomization.yaml` (`images:`) must match what you pushed; `latest` is never used. The
Docker Hub repositories are assumed **public**. For private repositories create a pull secret and add
`imagePullSecrets` to the four Deployments:

```
kubectl --context s4v3 -n job-hunter create secret docker-registry dockerhub \
  --docker-username=moldovean --docker-password=<access token>
```

## 6. Deploying

### 6.1 Secrets

Real credentials are created by hand, never committed (`k8s/.gitignore` ignores `**/secrets.yaml`):

```
cp k8s/base/secrets.yaml.example k8s/base/secrets.yaml
# replace every CHANGE_ME with a long random value, e.g.  openssl rand -base64 24 | tr -d '/+='
kubectl --context <ctx> apply -f k8s/base/namespace.yaml
kubectl --context <ctx> apply -f k8s/base/secrets.yaml
```

Without this secret the `postgres` pod stays in `CreateContainerConfigError` and the event reads
`secret "job-hunter-secrets" not found`. The database passwords are used only on first start (the init script creates
the roles then); changing the Secret later does not change the passwords inside an existing database.

#### Google login and administrators (optional)

The `auth` Deployment reads `APP_GOOGLE_CLIENT_ID`, `APP_GOOGLE_CLIENT_SECRET` and `APP_ADMIN_EMAILS` from an optional Secret
named `auth-google`; without it Google login is off and nobody is an administrator. Create the OAuth client in the Google
console (type "Web application") with the redirect URI `https://s4v3.net/auth/login/oauth2/code/google`, then:

Keep the values in `k8s/private/auth-google.yaml` (the `private/` folder is git-ignored; copy `k8s/auth-google.yaml.example`
there). It survives between deploys, so you edit and re-apply it whenever a value changes:

```sh
kubectl --context s4v3 apply -f k8s/private/auth-google.yaml
kubectl --context s4v3 -n job-hunter rollout restart deploy/auth
```

`ADMIN_EMAILS` is comma-separated. Restarting `auth` regenerates the signing key, so everyone signs in again.

### 6.2 Production

```
kubectl --context s4v3 apply -f k8s/base/namespace.yaml -f k8s/base/secrets.yaml
kubectl --context s4v3 apply -k k8s/overlays/prod
kubectl --context s4v3 -n job-hunter get pods,gateway,httproute,certificate
```

The certificate is requested automatically for the HTTPS listener. **Try the staging issuer first**: change
`letsencrypt-production` to `letsencrypt-staging` in `overlays/prod/kustomization.yaml` (annotation on the Gateway),
apply, and wait for `kubectl -n job-hunter get certificate` to show `READY True` (about a minute; the HTTP-01 challenge
goes `valid` first, and the Gateway needs a few more seconds to serve the new certificate). Staging certificates are not trusted by
browsers; they prove that DNS, port 80 and the HTTP-01 solver work without using the production rate limits. Then switch
back to `letsencrypt-production`, delete the `edge-tls` secret and apply again.

This was done on 2026-10-20 with only the Gateway and routes applied (the images did not exist yet): the certificate for
`s4v3.net` became ready, issued by `(STAGING) Dastardly Durum YR1`, `http://s4v3.net/api/x` answered
`301 Location: https://s4v3.net/api/x`, and the TLS handshake served the staging certificate. The test resources were then
removed.

`backend` validates tokens against the public issuer `https://s4v3.net/auth`. From inside the cluster that name resolves
to the node's public IP and the request leaves and re-enters through Traefik's host ports. This works on the cluster
(checked 2026-10-20: a token issued by `https://s4v3.net/auth` is accepted by `backend`; no override is needed). If it
ever stops working, add a CoreDNS rewrite for `s4v3.net` to `traefik.traefik.svc.cluster.local`, or set
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://auth:9090/auth/oauth2/jwks` on `backend` as the local overlay
does.

**Deployed and verified on 2026-10-20** (images `moldovean/job-hunter-{backend,auth,gateway}:0.1.0`): all five Pods
Ready, certificate issued by Let's Encrypt (`YR2`, trusted by `curl` without `-k`), `http://` answers 301, the OIDC
discovery document names `https://s4v3.net/auth`, and a PKCE login through `https://s4v3.net` returns a token with
`iss=https://s4v3.net/auth`. `/api/` answers 404 with that token (token accepted, no endpoint yet), 401 without a token
and 401 with a malformed one. The login created the `app_user` row. The check used the account
`deploy-check@example.com`.

**Profile release, 2026-10-21:** `moldovean/job-hunter-backend:0.2.0` and `moldovean/job-hunter-ui:0.2.0` (amd64, anonymous pull checked) applied with `kubectl --context s4v3 apply -k k8s/overlays/prod`. `backend` ran Liquibase changeset `004-profile` on start (table `profile` exists), both rollouts completed, `postgres` was not restarted, and anonymous `GET` and `PUT /api/profile` answer 401.

**Silent sign-in release, 2026-10-21:** `moldovean/job-hunter-auth:0.2.0` and `moldovean/job-hunter-ui:0.3.0` (amd64, anonymous pull checked) applied with `kubectl --context s4v3 apply -k k8s/overlays/prod`; both rollouts completed and `postgres`, `backend` and `gateway` were not restarted. An authorization request with `prompt=none` and no session answers `302` to `https://s4v3.net/callback?error=login_required&state=...`; the same request with an unregistered `redirect_uri` answers 400. Replacing the `auth` Pod regenerated the signing key, so **everyone signs in once more** after this release.

**Postings release, 2026-10-22:** `moldovean/job-hunter-backend:0.3.0` (amd64, digest `sha256:34c22242912f479e9041f03302d78210c6c4d44d6e51ef11a911067f3830653d`) applied with `kubectl --context s4v3 apply -k k8s/overlays/prod`. `backend` ran Liquibase changesets `005-posting` and `006-posting-source` on start (tables `posting` and `posting_source` exist), the rollout completed, and `postgres`, `auth`, `gateway` and `ui` were not restarted. The change adds no endpoint, so there is nothing to call from outside yet.

**Sources release, 2026-10-22:** `moldovean/job-hunter-backend:0.4.0` (amd64, digest `sha256:36730e058a1428c3c07a309592c4aa2cf81b5d15591fd58d52cf5ec5ab6b5ee5`) applied with `kubectl --context s4v3 apply -k k8s/overlays/prod` after a `kubectl diff` that showed only the image tag. It adds the Greenhouse source adapter and the table `source_fetch_state` (changeset `007`); the rollout completed and no other workload restarted. The first real crawl was run once by setting `JOBHUNTER_SOURCES_GREENHOUSE_RUN_ON_STARTUP=true` for one rollout, then removing it again: 10 boards completed, 40 postings created, none skipped, and the next pod made no crawl.

**Scheduler release, 2026-10-22:** `moldovean/job-hunter-backend:0.5.0` (amd64, digest `sha256:1cc261a54f7906d16326b849dc20915b7e04d63b971598c505fe7d55ec4af550`) applied with `kubectl --context s4v3 apply -k k8s/overlays/prod` after a `kubectl diff` that showed only the image tag and the new env var `JOBHUNTER_SOURCES_SCHEDULE_ENABLED=true`. `backend` ran changeset `008-crawl-schedule` on start and the rollout completed; no other workload restarted. The crawl now runs hourly (Europe/Zurich, on the hour). The `auth` image with `APP_ADMIN_EMAILS` is **not** deployed: Google login is not configured in this cluster yet, so nobody can hold the `ADMIN` role in production and the schedule API (`/api/admin/crawl-schedule`) answers 403 to everyone until it is.

**API docs release, 2026-10-22:** `moldovean/job-hunter-backend:0.6.0` (amd64, digest `sha256:ed629fad346030ed93175b17bb76278b72e4dd60badc9f7959abc6b3e96e32ef`) applied with `kubectl --context s4v3 apply -k k8s/overlays/prod` after a `kubectl diff` that showed only the image tag; the rollout completed and no other workload restarted. From outside: `GET https://s4v3.net/api/openapi.json` answers 200 (OpenAPI 3.1.0 with `/api/profile` and `/api/admin/crawl-schedule`), `/api/docs` redirects to `/api/swagger-ui/index.html` (200) whose initializer points at `/api/openapi.json`, and `GET /api/profile` still answers 401 without a token. `moldovean/job-hunter-auth:0.3.0` (digest `sha256:3a2e919c5dd46e0fb72fec5b493af5785c0bf0a7b7f886653c3bcc5470d51c52`, with `APP_ADMIN_EMAILS`) is pushed but **not deployed**: it waits for Google login. The page first failed in the browser with "Failed to load remote configuration": its config URL `/api/openapi.json/swagger-config` still needed a token. Fixed in `moldovean/job-hunter-backend:0.6.1` (digest `sha256:699f2fa11eb66c42aa3a8bddb44f0e3a71133b1d0a7868e017a0da48d7d6ff81`); that URL now answers 200 and a test fetches it anonymously.

**Postings browser release, 2026-10-23:** `moldovean/job-hunter-backend:0.8.0` (digest `sha256:21f2d8576c1bb6fd7e480b1db5a7e038ebc9c3cab481918284794e3f9cffda1d`) and `moldovean/job-hunter-ui:0.5.0` (digest `sha256:201f763433291f66643ed874abc5e6a90892a4dee7d37bc787bab57edac18ffc`), amd64, applied with `kubectl --context s4v3 apply -k k8s/overlays/prod` after a `kubectl diff` that showed only the two image tags (the apply also printed `configured` for other objects, which was last-applied annotation noise). Both rollouts completed; `auth` was not touched, so nobody was signed out. From outside: `GET /api/postings` answers 401 without a token and `/postings` answers 200. The user opened `/postings` in a browser and confirmed the list works.

**Matching and sign-out fix release, 2026-10-22:** `moldovean/job-hunter-backend:0.7.0` (digest `sha256:829e4d2a2c0d63b773af51af04e66a5d304c6afb82c9f72afaa2dccd137b7b20`) and `moldovean/job-hunter-auth:0.3.1` (digest `sha256:60fb42852725d0c4cf438d7cbf853885eaa6e1355d6cff64cba1e61c292c0175`), amd64, applied with `kubectl --context s4v3 apply -k k8s/overlays/prod` after a `kubectl diff` that showed only the two image tags. `backend` ran changeset `009-posting-match` on start; both rollouts completed and `gateway`, `ui` and `postgres` were not restarted. Replacing the `auth` Pod regenerated the signing key, so **everyone signs in once more**. From outside: `GET /api/matches` answers 401 without a token and `/api/openapi.json` lists it; `/auth/connect/logout?client_id=spa&post_logout_redirect_uri=https://s4v3.net/` now answers 302 to `https://s4v3.net/` while an unregistered URI answers 400. `APP_ADMIN_EMAILS` is supported by this auth image but not set (Google login is not configured yet).

The user checked `https://s4v3.net` in a browser on 2026-10-21 and reported it worked.

**Profile field checks release, 2026-10-21:** `moldovean/job-hunter-ui:0.4.0` (amd64, anonymous pull checked) applied the same way; the `ui` rollout completed, no other workload restarted, and anonymous `GET /api/profile` still answers 401.

The user checked the profile page on `https://s4v3.net` (sign in, save, reload, field messages, focus after a failed save, console) on 2026-10-21 and reported it fine.

## 7. Environment variables

The names below are the ones each application documents in its own README; the Deployments set exactly these.

| Service | Setting | Environment variable | Value comes from |
|---|---|---|---|
| backend | `server.port` | `SERVER_PORT` | `8081` literal |
| backend | `spring.datasource.url` | `SPRING_DATASOURCE_URL` | literal, `postgres:5432/jobhunter` |
| backend | `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | literal, `backend` |
| backend | `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | Secret `BACKEND_DB_PASSWORD` |
| backend | `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | ConfigMap `ISSUER_URI` |
| backend (local only) | `...jwt.jwk-set-uri` | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | overlay literal |
| auth | `spring.datasource.*` | `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | literals; Secret `AUTH_DB_PASSWORD` |
| auth | `app.issuer-uri` | `APP_ISSUER_URI` | ConfigMap `ISSUER_URI` |
| auth | `app.ui-base-url` | `APP_UI_BASE_URL` | ConfigMap `UI_BASE_URL` |
| auth | `app.spa.redirect-uris` | `APP_SPA_REDIRECT_URIS` | ConfigMap `SPA_REDIRECT_URIS` |
| auth | `app.spa.post-logout-redirect-uris` | `APP_SPA_POST_LOGOUT_REDIRECT_URIS` | ConfigMap `SPA_POST_LOGOUT_URIS` |
| auth | `app.admin-emails` | `APP_ADMIN_EMAILS` | Secret `ADMIN_EMAILS` (the real address is never committed) |
| backend | `jobhunter.sources.schedule.enabled` | `JOBHUNTER_SOURCES_SCHEDULE_ENABLED` | prod overlay literal `true` (initial value; a schedule stored through the API wins) |
| gateway | `microservices.backend.uri` | `MICROSERVICES_BACKEND_URI` | ConfigMap `BACKEND_URI` (`http://backend:8081`) |
| gateway | `microservices.auth.uri` | `MICROSERVICES_AUTH_URI` | ConfigMap `AUTH_URI` (`http://auth:9090`) |
| all three | JVM options | `JAVA_TOOL_OPTIONS` | `-XX:ReservedCodeCacheSize=64M` (see below) |

Google login (`APP_GOOGLE_CLIENT_ID` / `APP_GOOGLE_CLIENT_SECRET`) is not configured yet; add them to the Secret and to
the `auth` Deployment when the Google OAuth client exists. Its redirect URI to register is
`https://s4v3.net/auth/login/oauth2/code/google`.

No mismatches were found between these names and the other changes' specs and design.

**Memory.** The buildpack's memory calculator reserves 240 MB of JIT code cache by default; with the gateway's 384 Mi (local)
or 512 Mi limit that leaves no heap or crashes at start (`fixed memory regions require ... which is greater than ...`).
`JAVA_TOOL_OPTIONS=-XX:ReservedCodeCacheSize=64M` fixes it and the calculator sizes the heap from the remainder.

**Health endpoints** (HTTP, unauthenticated): `backend` `/actuator/health/{liveness,readiness}` on 8081; `gateway` the same on
8080; `auth` under its context path, `/auth/actuator/health/{liveness,readiness}` on 9090. Readiness of `backend` and `auth`
includes the database; liveness never does, so a database outage does not restart them. `gateway` readiness does not depend on
`auth` or `backend`.

## 8. Validation and tests

```
k8s/validate.sh            # renders overlays/local and overlays/prod; schema-checks them (Kubernetes, Gateway API,
                           # cert-manager); every container must have CPU/memory requests and a memory limit; no
                           # image may use `latest` or lack a tag. Exits non-zero and names the resource on failure.
k8s/test-validate.sh       # runs validate.sh against bad fixtures (wrong field type, missing resources, latest, no tag)
```

Network policies (default deny, then explicit paths): only `backend` and `auth` reach `postgres`; only the edge reaches `ui`
and `gateway`; only `gateway` reaches `backend`; `gateway` and `backend` reach `auth`. Test on any cluster:

```
kubectl -n job-hunter run np-test --rm -i --restart=Never --image=busybox:1.37.0 \
  --labels=app.kubernetes.io/name=gateway --command -- sh -c 'nc -z -w 4 postgres 5432 && echo OPEN || echo BLOCKED'   # BLOCKED
# the same with --labels=app.kubernetes.io/name=backend prints OPEN
```

## 9. How the overlays differ

Rendered with `kubectl kustomize` and diffed (local vs prod). Both contain the same five workloads and the same policies.

- **Public addresses** (ConfigMap `app-config`): `http://localhost:8080...` vs `https://s4v3.net...`.
- **Edge**: local has one HTTP listener for `localhost`; prod has an HTTP listener (redirect and ACME challenge only) and an
  HTTPS listener for `s4v3.net` with certificate secret `edge-tls`, the issuer annotation, an extra `http-to-https` route
  (301), and application routes attached to the HTTPS listener.
- **Images**: `job-hunter-<service>:local` vs `moldovean/job-hunter-<service>:<tag>` (the four, `ui` included; prod pins `backend` at `0.2.0`, `auth` at `0.2.0`, `ui` at `0.4.0` and `gateway` at `0.1.0`).
- **Resources**: local is smaller (backend 256Mi/768Mi, auth 256Mi/640Mi, gateway 192Mi/384Mi, postgres 128Mi/512Mi);
  prod uses the base values (backend 512Mi/1Gi, auth 384Mi/768Mi, gateway 256Mi/512Mi, postgres 512Mi/1Gi).
- **Issuer settings**: `backend` locally gets the signing-key URL of the in-cluster `auth` service, because a pod cannot
  reach `http://localhost:8080`; it still requires tokens to carry the public issuer.

## 10. Logging and personal data

Containers log to standard output only. Traefik access logs are off; the gateway logs no headers, cookies or query
strings (see `gateway/README.md`). The Postgres volume holds accounts now and CVs later, so the database is reachable
only from `backend` and `auth`, has no NodePort or LoadBalancer service, and its credentials come from a Secret that is
created by hand.

## 11. Things to know

- **`auth` generates its signing key at startup.** After any restart of `auth` every previously issued token stops
  validating (a fresh sign-in is needed) and two replicas would disagree. A persistent key is a follow-up to
  `bootstrap-auth`.
- **Unreachable `auth` with a cold `backend`:** a request with a token is rejected (401) after about 30 seconds, the
  default connect timeout of the key fetch; `backend` stays running and recovers by itself. Tokens whose keys are already
  cached keep validating while `auth` is down.
- **Rollouts** use `Recreate` on one node: a few seconds of downtime per deploy, no pending surge pod.
- **Single node, local disk, no backups.** See 4.1.
- **The gateway trusts every caller** (`SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES=.*`) because the edge sends
  the client's public address in `X-Forwarded-For`, which otherwise makes it discard all forwarded headers. Only the
  `gateway-from-edge` NetworkPolicy limits who can reach it.
