# Traefik URLRewrite / ReplacePrefixMatch for /cv/<handle>

## Answer
Supported: YES for the filter and path rewrite (Traefik v3.7.14 chart 42.0.0, Gateway API v1.6.3 Standard channel).
Rewrite behaviour at the edges (trailing slash, `/cv`) is UNVERIFIED: no source states it; test on prod.
Rule ordering "longest prefix wins" is UNVERIFIED for Gateway API in the docs (strong indirect evidence only).

## Findings
1. URLRewrite: Traefik v3.7 Gateway API reference lists `URLRewrite` ("rewrite the request URL path and/or hostname") and says
   it supports all HTTPRoute core and some extended features. Conformance report (Traefik v3.7.10, v1.6, experimental
   mode, default profile) lists HTTPRoutePathRewrite and HTTPRouteHostRewrite as supported, GATEWAY-HTTP extended 26 passed, 0 failed.
   HTTPRoutePathRewrite is Extended support level of HTTPRoute, in the Standard channel (URLRewrite is Standard since v0.5/v1.0).
   Our bundle is v1.6.3 standard (k8s/README.md 4.2), so the field exists. The report gives no per-test result, and
   does not name ReplacePrefixMatch separately (unverified, but PathRewrite covers both types in the spec).
   Traefik v3.7 provider "supports Gateway API v1.6.3": exact match with our bundle.
2. Ordering: Traefik docs v3.7 Gateway page has nothing on priority. Traefik core: router priority = rule length by default, so
   `PathPrefix(`/cv/`)` outranks `PathPrefix(`/`)` (older docs). Issue traefik/traefik#12795 shows equal-length ties are
   resolved oddly, irrelevant here as lengths differ. Gateway spec itself: longest prefix wins. Unverified for 3.7.14 specifically.
3. Trailing slash (all unverified; Gateway API spec says prefix matching is by path element, so `/cv/` also matches `/cv`?):
   - Spec: PathPrefix `/cv/` is treated as `/cv` (trailing slash ignored), matches `/cv`, `/cv/`, `/cv/x`, not `/cvx`.
   - Spec table for ReplacePrefixMatch `/api/public/cv/`: `/cv/ada` -> `/api/public/cv/ada`; `/cv/ada/` -> `/api/public/cv/ada/`;
     `/cv` and `/cv/` -> `/api/public/cv` or `/api/public/cv/` (a trailing slash on the replacement can yield `//` in some implementations).
   - Safer: use PathPrefix `/cv` and replacePrefixMatch `/api/public/cv` (no trailing slashes in either), per the kgateway doc
     warning that `/` replacements can give empty paths. Spring's gateway should ideally treat `/cv/ada/` leniently (backend decision).
   Verify after deploy: `curl -i https://s4v3.net/cv/ada-example`, `/cv/ada-example/`, `/cv`, `/cv/`.
4. YAML: add a rule to HTTPRoute `gateway` in k8s/base/edge.yaml (same HTTPRoute so one place; `ui` route keeps `/`):
```yaml
    - matches:
        - path:
            type: PathPrefix
            value: /cv
      filters:
        - type: URLRewrite
          urlRewrite:
            path:
              type: ReplacePrefixMatch
              replacePrefixMatch: /api/public/cv
      backendRefs:
        - name: gateway
          port: 8080
```
   No network policy or Helm change needed: same Service, same traefik -> gateway:8080 path. Remember the Spring gateway
   must route `/api/public/**` to backend without a token.
5. Fallback if the rewrite misbehaves (not needed if 4 works): Traefik Middleware via ExtensionRef (documented in the v3.7 page:
   group traefik.io, kind Middleware, needs the kubernetesCRD provider). Needs `providers.kubernetesCRD.enabled: true`
   in k8s/platform/traefik-values.yaml, the Traefik CRDs (chart installed with --skip-crds, so apply them separately),
   RBAC (chart does it), and a Middleware in job-hunter:
```yaml
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata: {name: cv-rewrite, namespace: job-hunter}
spec:
  replacePathRegex: {regex: "^/cv/(.*)", replacement: "/api/public/cv/$1"}
# in the rule: filters: [{type: ExtensionRef, extensionRef: {group: traefik.io, kind: Middleware, name: cv-rewrite}}]
```
   Network policies need no change. Uses a non-standard CRD, so prefer option 4.

## Sources
- https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/kubernetes/gateway-api/
- https://github.com/kubernetes-sigs/gateway-api/tree/main/conformance/reports/v1.6/traefik-traefik (experimental-v3.7.10-default-report.yaml)
- https://doc.traefik.io/traefik/v2.11/routing/routers/ (priority = rule length)
- https://github.com/traefik/traefik/issues/12795 (HTTPRoute priority tie)
- https://kgateway.dev/docs/envoy/main/migrate/examples/url-rewriting/ (empty-path edge case, other implementation)
- Local: k8s/platform/traefik-values.yaml, k8s/README.md 4.2/4.3, k8s/base/edge.yaml (the Gateway and routes are in edge.yaml, not gateway.yaml, which holds the Spring Deployment)
