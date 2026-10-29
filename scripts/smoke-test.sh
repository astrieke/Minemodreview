#!/usr/bin/env bash
# Startet für jede Minecraft-Version einen Server mit dem Demo-Datapack
# und prüft, ob Strata Core lädt und die Datapack-Inhalte erkennt.
#
# Aufruf: scripts/smoke-test.sh [version ...]   (ohne Argument: alle Versionen)
set -uo pipefail
cd "$(dirname "$0")/.."

versions=("$@")
if [ ${#versions[@]} -eq 0 ]; then
    for dir in core/versions/*/; do versions+=("$(basename "$dir")"); done
fi

expected='\[Strata\] 2 Epochen und 3 Kodex-Knoten geladen, 0 Fehler'
failed=()

for v in "${versions[@]}"; do
    run="core/versions/$v/run/server"
    rm -rf "$run"
    mkdir -p "$run/world/datapacks"
    cp -r example-datapacks/strata-demo "$run/world/datapacks/"
    echo "eula=true" > "$run/eula.txt"
    printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\n' > "$run/server.properties"

    log="$run/smoke.log"
    echo "== $v: Server startet …"
    ./gradlew --console=plain ":core-$v:runServer" > "$log" 2>&1 &
    pid=$!
    result="TIMEOUT"
    for _ in $(seq 1 300); do
        if grep -qE "$expected" "$log"; then result="OK"; break; fi
        if grep -qE '\[Strata\] .* Fehler|Failed to start|Crash report|BUILD FAILED' "$log"; then result="FEHLER"; break; fi
        if ! kill -0 "$pid" 2>/dev/null; then result="BEENDET"; break; fi
        sleep 2
    done
    # Gradle-Client beenden: Gradle bricht dann auch den Server-Prozess ab.
    kill "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    # Warten, bis der Port wieder frei ist, damit die nächste Version starten kann.
    for _ in $(seq 1 30); do
        (exec 3<>/dev/tcp/127.0.0.1/25565) 2>/dev/null || break
        sleep 1
    done
    echo "== $v: $result"
    grep -E '\[Strata\]|Strata Core geladen' "$log" | sed 's/^/   /'
    [ "$result" = "OK" ] || failed+=("$v")
done

if [ ${#failed[@]} -gt 0 ]; then
    echo "Fehlgeschlagen: ${failed[*]}"
    exit 1
fi
echo "Alle Versionen OK: ${versions[*]}"
