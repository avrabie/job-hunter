#!/usr/bin/env bash
# Renders each overlay and checks the result. Needs kubectl, kubeconform and yq (see README).
#
#   k8s/validate.sh                     both overlays
#   k8s/validate.sh path/to/dir ...     the given kustomize directories (used by test-validate.sh)
#
# Checks, per directory:
#   1. `kubectl kustomize` renders without error
#   2. every resource matches its schema (Kubernetes, Gateway API, cert-manager); unknown fields are errors
#   3. every container has CPU and memory requests and a memory limit
#   4. no image uses the tag `latest` or has no tag
# Exits non-zero if any check fails and names the resource or container that failed.
set -euo pipefail

cd "$(dirname "$0")"

# Schemas are fetched from the network and cached by kubeconform. Kubernetes version of the cluster in use.
K8S_VERSION="${K8S_VERSION:-1.34.0}"
CRD_CATALOG='https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json'

for tool in kubectl kubeconform yq; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 2; }
done

if [ "$#" -gt 0 ]; then dirs=("$@"); else dirs=(overlays/local overlays/prod); fi

failed=0
for dir in "${dirs[@]}"; do
  echo "== $dir"
  rendered="$(mktemp)"
  trap 'rm -f "$rendered"' EXIT

  if ! kubectl kustomize "$dir" >"$rendered"; then
    echo "FAIL $dir: kustomize could not render it" >&2
    failed=1
    continue
  fi

  # -strict: unknown fields are errors. No -ignore-missing-schemas: a resource without a schema must fail.
  if ! kubeconform -strict -summary -kubernetes-version "$K8S_VERSION" \
      -schema-location default -schema-location "$CRD_CATALOG" <"$rendered"; then
    echo "FAIL $dir: schema validation" >&2
    failed=1
  fi

  workloads='select(.kind == "Deployment" or .kind == "StatefulSet" or .kind == "DaemonSet" or .kind == "Job")'
  containers='(.spec.template.spec | (.containers // []) + (.initContainers // []))[]'

  unbounded="$(yq eval -N "$workloads | .metadata.name as \$n | $containers
    | select(.resources.requests.cpu == null or .resources.requests.memory == null or .resources.limits.memory == null)
    | \$n + \"/\" + .name" "$rendered")"
  if [ -n "$unbounded" ]; then
    echo "FAIL $dir: containers without CPU/memory requests and a memory limit:" >&2
    echo "$unbounded" | sed 's/^/  /' >&2
    failed=1
  fi

  badtags="$(yq eval -N "$workloads | .metadata.name as \$n | $containers
    | (.image | sub(\"^.*/\"; \"\")) as \$i
    | select((\$i | test(\":latest\$\")) or (\$i | test(\"[:@]\") | not))
    | \$n + \"/\" + .name + \" uses image \" + .image" "$rendered")"
  if [ -n "$badtags" ]; then
    echo "FAIL $dir: images without an explicit tag, or tagged latest:" >&2
    echo "$badtags" | sed 's/^/  /' >&2
    failed=1
  fi
done

if [ "$failed" -ne 0 ]; then
  echo "validation failed" >&2
  exit 1
fi
echo "validation passed"
