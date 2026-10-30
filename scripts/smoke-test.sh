#!/usr/bin/env bash
# Startet für jede Minecraft-Version einen Server mit dem ganzen Set
# (Strata Core + Epochs + Bridge, dazu JEI und Jade) und dem Demo-Datapack, und prüft das Log:
#  - Strata Core erkennt alle Epochen und Kodex-Knoten ohne Fehler,
#  - der Selbsttest von Strata Epochs generiert jede Epochen-Dimension erfolgreich,
#  - Strata Bridge findet alle mitgelieferten Advancements.
#
# Aufruf: scripts/smoke-test.sh [version ...]   (ohne Argument: alle Versionen)
set -uo pipefail
cd "$(dirname "$0")/.."

versions=("$@")
if [ ${#versions[@]} -eq 0 ]; then
    for dir in versions/*/; do versions+=("$(basename "$dir")"); done
fi

# Demo-Datapack (2 Epochen, 3 Knoten) + Strata Epochs (2 Epochen, 3 Knoten)
expected_core='\[Strata\] 4 Epochen und 6 Kodex-Knoten geladen, 0 Fehler'
expected_test='\[Strata-Test\] Fertig, 0 Fehler'
expected_bridge='\[Strata-Bridge-Test\] 4 von 4 Advancements geladen'
expected_chain='\[Strata-Bridge-Test\] Freischaltung: .* -> OK'
failed=()

for v in "${versions[@]}"; do
    run="bridge/versions/$v/run/server"
    rm -rf "$run"
    mkdir -p "$run/world/datapacks"
    cp -r example-datapacks/strata-demo "$run/world/datapacks/"
    echo "eula=true" > "$run/eula.txt"
    printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\n' > "$run/server.properties"

    log="$run/smoke.log"
    echo "== $v: Server startet …"
    ./gradlew --console=plain ":bridge-$v:runServer" > "$log" 2>&1 &
    pid=$!
    result="TIMEOUT"
    for _ in $(seq 1 300); do
        if grep -qE "$expected_core" "$log" && grep -qE "$expected_test" "$log" && grep -qE "$expected_bridge" "$log" && grep -qE "$expected_chain" "$log"; then result="OK"; break; fi
        if grep -qE '\[Strata-Test\] Fertig, [1-9]|Advancement fehlt|Freischaltung: .* -> FEHLER|\[Strata\] .*, [1-9][0-9]* Fehler|Failed to start|Crash report|Exception in server tick|BUILD FAILED' "$log"; then result="FEHLER"; break; fi
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
    grep -E '\[Strata|Strata Core geladen|ERROR' "$log" | head -40 | sed 's/^/   /'
    [ "$result" = "OK" ] || failed+=("$v")
done

if [ ${#failed[@]} -gt 0 ]; then
    echo "Fehlgeschlagen: ${failed[*]}"
    exit 1
fi
echo "Alle Versionen OK: ${versions[*]}"
