#!/usr/bin/env bash
# Leistungsmessung: startet einen Server mit dem ganzen Set und misst die Tickdauer in vier Szenen
# (siehe epochs/src/shared/java/dev/strata/epochs/Perf.java). Gibt die Messwerte aus dem Log aus.
#
# Aufruf: scripts/perf.sh [version]   (Standard: 1.21.1)
set -uo pipefail
cd "$(dirname "$0")/.."
v="${1:-1.21.1}"
run="bridge/versions/$v/run/server"
rm -rf "$run"
mkdir -p "$run"
echo "eula=true" > "$run/eula.txt"
printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\n' > "$run/server.properties"
log="$run/perf.log"
echo "== $v: Leistungsmessung …"
./gradlew --console=plain ":bridge:$v:runServer" -Pperf > "$log" 2>&1
grep -E "\[Strata-Leistung\]" "$log" | sed 's/.*\]: //'
grep -q "\[Strata-Leistung\] Fertig" "$log" || { echo "Messung nicht fertig geworden, siehe $log"; exit 1; }
