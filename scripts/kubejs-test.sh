#!/usr/bin/env bash
# Prüft die KubeJS-Anbindung: Server mit KubeJS und Rhino, Testskript scripts/kubejs-test/strata_test.js.
# Voraussetzung: scripts/smoke-test.sh <version> lief vorher (Welt im Server-Ordner).
# Aufruf: scripts/kubejs-test.sh [1.21.1|26.1]
set -uo pipefail
cd "$(dirname "$0")/.."
v="${1:-1.21.1}"
run="bridge/versions/$v/run/server"
mkdir -p "$run/kubejs/server_scripts"
cp scripts/kubejs-test/strata_test.js "$run/kubejs/server_scripts/"
log="$run/kubejs.log"
./gradlew --console=plain ":bridge:$v:runServer" -PwithKubejs > "$log" 2>&1 &
pid=$!
result="TIMEOUT"
for _ in $(seq 1 150); do
    if grep -qE 'Strata-KubeJS\].*-> OK' "$log" "$run/logs/kubejs/server.log" 2>/dev/null; then result="OK"; break; fi
    if grep -qE 'Strata-KubeJS\].*FEHLER|Crash report|BUILD FAILED|Failed to start' "$log" "$run/logs/kubejs/server.log" 2>/dev/null; then result="FEHLER"; break; fi
    sleep 2
done
kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
grep -hE 'Strata-KubeJS|ERROR.*kubejs' "$log" "$run/logs/kubejs/server.log" 2>/dev/null | head -10
echo "KubeJS $v: $result"
[ "$result" = "OK" ]
