#!/bin/sh
# Exercise deployment decisions with an isolated fake Docker client.
set -eu
fixture=$(mktemp -d /tmp/pkb-start-test.XXXXXX)
case "$fixture" in /tmp/pkb-start-test.*) ;; *) exit 1 ;; esac
trap 'rm -rf "$fixture"' EXIT HUP INT TERM
cp "$(dirname "$0")/../deploy.sh" "$fixture/deploy.sh"
printf 'WEB_PORT=4173\nTRAINING_SERVICE_TOKEN=synthetic-only\nCODE_LAB_SERVICE_TOKEN=synthetic-only\n' > "$fixture/.env"
mkdir "$fixture/bin"
cat > "$fixture/bin/docker" <<'MOCK'
#!/bin/sh
printf '%s\n' "$*" >> "$CALLS"
case "$*" in
  'compose config --services') printf '%s\n' mysql redis minio clamav backend model-worker code-lab training frontend ;;
  'compose ps --all --format {{.Service}}')
    if [ "${MISSING:-false}" != true ]; then printf '%s\n' mysql redis minio clamav backend model-worker code-lab training frontend; fi ;;
  'compose start'*) if [ "${FAIL_START:-false}" = true ]; then exit 7; fi ;;
esac
MOCK
chmod +x "$fixture/bin/docker"
export PATH="$fixture/bin:$PATH" CALLS="$fixture/calls"
sh "$fixture/deploy.sh" > "$fixture/output"
grep -Fqx 'compose start --wait --wait-timeout 240 mysql redis backend frontend' "$CALLS"
if grep -Eq '^compose (up|build|pull|down|rm)' "$CALLS"; then exit 1; fi
echo 'PASS: Daily start never builds, pulls or recreates containers'
: > "$CALLS"
sh "$fixture/deploy.sh" --build > "$fixture/output"
grep -Fqx 'compose build backend frontend code-lab' "$CALLS"
grep -Fqx 'compose stop frontend model-worker backend' "$CALLS"
grep -Fqx 'compose up -d --no-deps --no-build --wait --wait-timeout 240 backend model-worker code-lab' "$CALLS"
test "$(tail -n 1 "$CALLS")" = 'compose up -d --no-deps --no-build --wait --wait-timeout 240 frontend'
if grep -Eq '^compose up.* (mysql|minio|redis|clamav)' "$CALLS"; then exit 1; fi
echo 'PASS: Application update retires old processes and preserves storage'
: > "$CALLS"
MISSING=true sh "$fixture/deploy.sh" > "$fixture/output"
grep -Fqx 'compose up -d --build mysql redis minio clamav backend model-worker code-lab frontend' "$CALLS"
echo 'PASS: First deployment installs missing services'
if MISSING=true sh "$fixture/deploy.sh" --skip-build > "$fixture/output" 2>&1; then exit 1; fi
if FAIL_START=true sh "$fixture/deploy.sh" > "$fixture/output" 2>&1; then exit 1; fi
if grep -q '^Ready:' "$fixture/output"; then exit 1; fi
if sh "$fixture/deploy.sh" --bad > "$fixture/output" 2>&1; then exit 1; fi
echo 'PASS: Incomplete installations, startup failures and invalid options fail explicitly'

: > "$CALLS"
COMPOSE_PROFILES=training sh "$fixture/deploy.sh" --build > "$fixture/output"
grep -Fqx 'compose build backend frontend code-lab training' "$CALLS"
echo 'PASS: Optional training is only built when requested'
