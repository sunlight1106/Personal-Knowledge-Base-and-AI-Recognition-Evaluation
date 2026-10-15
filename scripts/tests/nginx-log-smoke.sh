#!/usr/bin/env bash
# Synthetic CI-only container: verifies the stock parent config cannot reintroduce raw URL logs.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
IMAGE=nginx:1.27-alpine
headers="$ROOT/cle/security-headers.conf:/etc/nginx/snippets/security-headers.conf:ro"
for config in cle/nginx.conf scripts/public/nginx.conf; do
    docker run --rm --add-host backend:127.0.0.1 -v "$ROOT/$config:/etc/nginx/conf.d/default.conf:ro" -v "$headers" "$IMAGE" nginx -t
done
container=$(docker run --rm -d --add-host backend:127.0.0.1 -p 127.0.0.1::80 -v "$ROOT/cle/nginx.conf:/etc/nginx/conf.d/default.conf:ro" -v "$headers" "$IMAGE")
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
binding=$(docker port "$container" 80/tcp)
port=${binding##*:}
curl -fsS --retry 10 --retry-connrefused --retry-delay 1 --retry-max-time 20 "http://127.0.0.1:$port/" >/dev/null
status=$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/v1/notes/shared/SYNTHETIC_PROXY_PRIVATE_TOKEN?key=SYNTHETIC_PROXY_PRIVATE_TOKEN")
[[ "$status" == 502 ]]
logs=$(docker logs "$container" 2>&1)
if grep -q 'SYNTHETIC_PROXY_PRIVATE_TOKEN' <<<"$logs"; then
    echo 'FAIL: synthetic private URL appeared in Nginx logs' >&2
    exit 1
fi
grep -q '"GET" 502 ' <<<"$logs"
for path in / /index.html /app/notes /assets/missing.js /api/v1/private; do
    response=$(curl -sS -D - -o /dev/null "http://127.0.0.1:$port$path")
    grep -qi "^Content-Security-Policy: .*script-src 'self'" <<<"$response"
    grep -qi '^X-Content-Type-Options: nosniff' <<<"$response"
    grep -qi '^X-Frame-Options: DENY' <<<"$response"
    grep -qi '^Referrer-Policy: no-referrer' <<<"$response"
done
grep -qi '^Cache-Control: no-store' <<<"$response"
echo 'PASS: privacy logs, security headers on pages/assets/errors, and private API cache policy'
