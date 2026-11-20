#!/usr/bin/env bash
# Zieht die Versions-Branches release/<version> auf den aktuellen Hauptstand nach.
#
# Entwickelt wird für alle Versionen gemeinsam auf dem Hauptbranch. Dieses Skript übernimmt für jede Version den
# neuesten Hauptstand, teilt ihn mit scripts/split-version.sh auf, baut, prüft mit dem Smoke-Test und legt das als
# neuen Commit auf release/<version> an (die bisherige Historie des Branches bleibt erhalten). Gepusht wird nur,
# wenn Build und Smoke-Test bestehen. Gearbeitet wird in eigenen Worktrees unter .release-worktrees/ (ignoriert).
#
# Aufruf: scripts/update-release-branches.sh [version ...]   (ohne Argument: alle Versionen unter versions/)
#         PUSH=0 scripts/update-release-branches.sh          nur vorbereiten und testen, nicht pushen
set -uo pipefail
cd "$(dirname "$0")/.."
root=$(pwd)
main=$(git rev-parse --abbrev-ref HEAD)
case "$main" in release/*) echo "Bitte vom Hauptbranch aus aufrufen, nicht von $main"; exit 1;; esac
[ -z "$(git status --porcelain)" ] || { echo "Arbeitsverzeichnis nicht sauber, erst committen"; exit 1; }
head=$(git rev-parse --short HEAD)

versions=("$@")
if [ ${#versions[@]} -eq 0 ]; then
    for dir in versions/*/; do versions+=("$(basename "$dir")"); done
fi

# Jeder Schritt wird ausdrücklich geprüft (kein set -e: in einer mit || geprüften Funktion greift es nicht).
update_one() {
    local v=$1 wt=$2
    cd "$wt" || return 1
    # Baum auf den Hauptstand setzen (Eltern-Commit bleibt der bisherige release/<v>), dann aufteilen.
    git read-tree -u --reset "$head" || return 1
    scripts/split-version.sh "$v" > update.log 2>&1 || { echo "   Aufteilen fehlgeschlagen"; return 1; }
    # Sicherheitsnetz: Es darf nur noch diese eine Version geben.
    [ "$(git ls-files versions | cut -d/ -f2 | sort -u)" = "$v" ] || { echo "   Aufteilung unvollständig"; return 1; }
    ./gradlew --console=plain -q ":core:$v:build" ":epochs:$v:build" ":bridge:$v:build" >> update.log 2>&1 \
        || { echo "   Build fehlgeschlagen"; return 1; }
    scripts/smoke-test.sh >> update.log 2>&1 || { echo "   Smoke-Test fehlgeschlagen"; return 1; }
    rm -f update.log
    if git diff --cached --quiet; then
        echo "   keine Änderungen"
    else
        git commit -q -m "release/$v: auf $main $head nachgezogen

Erzeugt mit scripts/update-release-branches.sh (split-version.sh $v),
gebaut und mit scripts/smoke-test.sh geprüft." || return 1
    fi
    if [ "${PUSH:-1}" = 1 ]; then
        local i
        for i in 1 2 3 4; do git push -q -u origin "release/$v" 2>/dev/null && break; [ $i = 4 ] && return 1; sleep $((i * 2)); done
    fi
    echo "   OK: $(git log --oneline -1)"
    cd "$root" || return 1
}

failed=()
for v in "${versions[@]}"; do
    cd "$root" || exit 1
    echo "== release/$v aus $main ($head)"
    wt="$root/.release-worktrees/$v"
    git fetch -q origin "release/$v" 2>/dev/null || true
    if [ ! -d "$wt" ]; then
        if git rev-parse -q --verify "origin/release/$v" >/dev/null; then
            git worktree add -q -B "release/$v" "$wt" "origin/release/$v"
        else
            git worktree add -q -b "release/$v" "$wt" HEAD   # neuer Versions-Branch
        fi
    fi
    if ! update_one "$v" "$wt"; then
        echo "   FEHLER bei $v (Worktree: $wt, Log: $wt/update.log)"
        failed+=("$v")
    fi
done

if [ ${#failed[@]} -gt 0 ]; then
    echo "Fehlgeschlagen: ${failed[*]}"
    exit 1
fi
echo "Alle Versions-Branches aktuell: ${versions[*]}"
