#!/usr/bin/env bash
# Balance-Messung: startet einen Server mit dem ganzen Set und lässt Spielzeit im Schnelllauf ablaufen
# (siehe epochs/src/shared/java/dev/strata/epochs/Balance.java). Gibt die Messwerte aus dem Log aus.
#
# Aufruf: scripts/balance.sh [version] [minuten]   (Standard: 1.21.11, 30 Spielminuten)
set -uo pipefail
cd "$(dirname "$0")/.."
v="${1:-1.21.11}"
minutes="${2:-30}"
run="bridge/versions/$v/run/server"
rm -rf "$run"
mkdir -p "$run"
echo "eula=true" > "$run/eula.txt"
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\n' > "$run/server.properties"
log="$run/balance.log"
echo "== $v: Balance-Messung über $minutes Spielminuten …"
./gradlew --console=plain ":bridge:$v:runServer" -Pbalance="$minutes" > "$log" 2>&1
grep -E "\[Strata-Balance\]" "$log" | sed 's/.*\]: //'
grep -q "\[Strata-Balance\] Fertig" "$log" || { echo "Messung nicht fertig geworden, siehe $log"; exit 1; }
