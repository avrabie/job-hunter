# gateway

A small, stateless Spring Cloud Gateway (reactive) that sits behind the edge (Traefik / Gateway API in the cluster) and
fans requests out to the two services. It validates no token, authorizes nothing and keeps no state or session; each
service enforces its own access rules. Requirements: `../openspec/`. Listens on **8080**.

| Path | Goes to | Default target (local) | Variable |
|---|---|---|---|
| `/api/**` | `backend` | `http://localhost:8081` | `MICROSERVICES_BACKEND_URI` |
| `/auth/**` | `auth` | `http://localhost:9090` | `MICROSERVICES_AUTH_URI` |
| anything else | nothing | 404 | |

Paths are forwarded unchanged (no rewriting): `auth` keeps its `/auth` context path and `backend` serves `/api/...`.

## Run the three services together

Each in its own terminal, from the repository root. `auth` and `backend` start their own database through Docker.

```bash
# 1. auth (port 9090); its issuer is the public gateway address
(cd auth && ./gradlew bootRun --args='--spring.profiles.active=dev')

# 2. backend (port 8081, 8080 is the gateway's)
(cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev')

# 3. gateway (port 8080)
(cd gateway && ./gradlew bootRun)
```

The `dev` defaults already agree: issuer `http://localhost:8080/auth`, backend on 8081, auth on 9090.

```bash
curl -s http://localhost:8080/auth/.well-known/openid-configuration   # the discovery document, issuer http://localhost:8080/auth
curl -i http://localhost:8080/api/                                    # 401: backend wants a token
```

Sign in at <http://localhost:8080/auth/login> (dev accounts are listed in `auth/README.md`).

## Settings

| Setting | Variable | Default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8080` |
| `microservices.backend.uri` | `MICROSERVICES_BACKEND_URI` | `http://localhost:8081` |
| `microservices.auth.uri` | `MICROSERVICES_AUTH_URI` | `http://localhost:9090` |
| `spring.cloud.gateway.server.webflux.trusted-proxies` | `SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES` | private and loopback addresses |

Probes: `/actuator/health/liveness` and `/actuator/health/readiness` (independent of `backend` and `auth`). Nothing
else of the actuator is exposed.

### Gateway property keys (Spring Cloud Gateway 5.x)

Spring Cloud Gateway 5.x binds its settings under **`spring.cloud.gateway.server.webflux`**. The older
`spring.cloud.gateway.*` prefix is ignored without any error. All keys below are checked against the release's
configuration metadata by `GatewayConfigurationTests`, which also asserts the effective values.

| Key (under `spring.cloud.gateway.server.webflux`) | Value |
|---|---|
| `routes` | `backend` and `auth`, path predicates only |
| `default-filters` | `SecureHeaders` |
| `filter.secure-headers.disable` | `content-security-policy` (the login page of `auth` needs inline content) |
| `httpclient.connect-timeout` | `5000` ms |
| `httpclient.response-timeout` | `60s` |
| `httpclient.pool.max-idle-time` / `max-life-time` / `eviction-interval` | `15s` / `60s` / `15s` (stale pooled connections after a deploy) |
| `trusted-proxies` | regex of callers whose `X-Forwarded-*` headers are believed |
| `x-forwarded.*-append` | left at the default (the edge's value stays first) |

**`trusted-proxies` is required.** Without it the gateway treats every caller as untrusted, drops the forwarded host and
scheme sent by the edge and adds none of its own, so `auth` would build redirects with the wrong address.

## Behaviour worth knowing

- A downstream service that refuses or drops the connection gives **502** (`{"error":"bad_gateway"}`, no address); a
  service that does not answer within 60 s gives **504**. Spring Cloud Gateway alone would answer 500 for the first.
- Forwarded headers: `X-Forwarded-Host/Proto` from the edge arrive first, the gateway's own view is appended.
- No request or response bodies, tokens, cookies or query strings are logged.

## Tests

```bash
./gradlew test      # no network, no other service: stub servers are started by the tests
```
