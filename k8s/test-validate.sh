#!/usr/bin/env bash
# Proves validate.sh itself: it passes the valid fixture and fails each bad one, naming the culprit.
set -uo pipefail
cd "$(dirname "$0")"

fail=0
expect() { # description, expected exit (0|1), expected text in output (or empty), dir
  local out code
  out="$(./validate.sh "test/fixtures/$4" 2>&1)"; code=$?
  [ "$code" -ne 0 ] && code=1
  if [ "$code" -ne "$2" ] || { [ -n "$3" ] && ! grep -q -- "$3" <<<"$out"; }; then
    echo "NOT OK  $1 (exit $code)"; echo "$out" | sed 's/^/        /'; fail=1
  else
    echo "ok      $1"
  fi
}

expect "valid fixture passes"                    0 ""             valid
expect "wrong field type fails, names resource"  1 "Deployment demo" wrong-type
expect "missing resources fails, names container" 1 "demo/web"       no-resources
expect "latest tag fails, names container"        1 "demo/web uses image nginx:latest" latest-tag
expect "missing tag fails, names container"       1 "demo/web uses image nginx" no-tag
exit $fail
