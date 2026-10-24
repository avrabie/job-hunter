# pcv-g7 report (task 7.1, edge route for /cv/<handle>)

## Tasks
- 7.1 done: rule added to the `gateway` HTTPRoute in `k8s/base/edge.yaml`. Not committed. Not applied (no kubectl against the cluster).
- 0.1 ticked in `openspec/changes/public-cv-page/tasks.md`; answer and source written into `design.md` decision 7.

## Rendered rule (`kubectl kustomize k8s/overlays/prod`, same in local)
```
  - backendRefs:
    - name: gateway
      port: 8080
    filters:
    - type: URLRewrite
      urlRewrite:
        path:
          replacePrefixMatch: /api/public/cv
          type: ReplacePrefixMatch
    matches:
    - path:
        type: PathPrefix
        value: /cv
```
The prod overlay patches the same HTTPRoute (hostnames `s4v3.net`, sectionName `https`), so the rule inherits both. Local adds `localhost` as hostname.

## validate.sh
- `k8s/validate.sh` passed: local 24 resources valid, 0 errors; prod 25 resources valid, 0 errors; "validation passed".
- kubeconform fetched schemas from the network (cached), no cluster contact.
- `kubectl kustomize` renders of both overlays exit 0.
- `test-validate.sh` not run.

## Overlays (step 2)
No change. Neither overlay defines HTTPRoute rules; prod patches only hostnames and sectionName on `ui` and `gateway`, and local only adds hostnames to all routes. The new rule comes from base.

## Files changed
- `k8s/base/edge.yaml` (rule and comment)
- `k8s/README.md` (routing paragraph in section 1, new 6.3 "Verify after deploy" with curl checks and table)
- `openspec/changes/public-cv-page/design.md` (decision 7 verified sentence)
- `openspec/changes/public-cv-page/tasks.md` (0.1 ticked)
- `handoff/pcv-g7.report.md` (this file)

## Unconfirmed (to confirm with curl after deploy, README 6.3)
- `/cv` and `/cv/`: expected answers not known; trailing-slash behaviour is not stated by any source.
- `/cv/ada/` (trailing slash after a handle): not known.
- `/cv/a/b`: expected 404 from backend (spec scenario), not yet seen.
- `/cv/some-handle`: expected JSON `{"error":"not_found"}` from backend, not UI HTML.
- Rule ordering: `/cv` (in `gateway`) vs `/` (in `ui`) assumed longest prefix wins; not confirmed for Traefik 3.7.14.
- ReplacePrefixMatch named separately in the conformance report: not confirmed (only HTTPRoutePathRewrite).

## Notes for the lead
- Task 7.1 text says `k8s/base/gateway.yaml`; the routes are in `k8s/base/edge.yaml`. I left tasks.md 7.1 text unchanged.
- Backend must accept unauthenticated `GET /api/public/cv/*` at the gateway (outside this group).
- Fallback if the rewrite misbehaves: Traefik Middleware via ExtensionRef (needs CRD provider), see the research report section 5.
