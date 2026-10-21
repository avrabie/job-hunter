#!/usr/bin/env bash
# Checks a built UI image the way the cluster runs it: read-only root, no capabilities, non-root user.
#   ui/test-image.sh [image]        default image: job-hunter-ui:test
set -euo pipefail

IMAGE="${1:-job-hunter-ui:test}"
PORT="${PORT:-18080}"
NAME="ui-image-test-$$"
fail=0

docker run -d --rm --name "$NAME" --read-only --cap-drop ALL --security-opt no-new-privileges \
  --user 101 --tmpfs /tmp -p "$PORT:8080" "$IMAGE" >/dev/null
trap 'docker stop "$NAME" >/dev/null 2>&1 || true' EXIT

for _ in $(seq 1 30); do
  curl -sf -o /dev/null "http://localhost:$PORT/" && break
  sleep 1
done

check() { # description, command that must succeed
  if eval "$2" >/dev/null 2>&1; then echo "ok   $1"; else echo "FAIL $1"; fail=1; fi
}
headers() { curl -s -D - -o /dev/null "http://localhost:$PORT$1"; }
status() { curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT$1"; }
asset="$(curl -s "http://localhost:$PORT/" | grep -o '/assets/[^"]*\.js' | head -1)"

check "/ answers 200" '[ "$(status /)" = 200 ]'
check "/callback?code=x answers 200 with index.html" '[ "$(status "/callback?code=x")" = 200 ] && curl -s "http://localhost:$PORT/callback?code=x" | grep -q "<div id=\"root\">"'
check "a missing asset answers 404" '[ "$(status /assets/missing.js)" = 404 ]'
check "/api/ is not the app" '[ "$(status /api/)" = 404 ]'
check "/auth/ is not the app" '[ "$(status /auth/login)" = 404 ]'
check "index.html is no-cache" 'headers / | grep -qi "^cache-control: no-cache"'
check "a hashed asset is immutable (found: $asset)" 'headers "$asset" | grep -qi "^cache-control: public, max-age=31536000, immutable"'
for path in / "$asset" /callback; do
  check "security headers on $path" 'h="$(headers "$path")"; for k in content-security-policy x-content-type-options referrer-policy x-frame-options; do echo "$h" | grep -qi "^$k:" || exit 1; done'
done
check "CSP has no unsafe-inline or unsafe-eval" '! headers / | grep -i "^content-security-policy" | grep -qE "unsafe-(inline|eval)"'
check "runs as uid 101" '[ "$(docker exec "$NAME" id -u)" = 101 ]'
check "root filesystem is read-only" '! docker exec "$NAME" sh -c "touch /usr/share/nginx/html/x"'

exit "$fail"
