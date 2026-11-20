#!/usr/bin/env bash
# Macht aus dem Mehrversions-Stand einen Stand für genau eine Minecraft-Version (für die Branches release/<version>).
#
# Entfernt alle anderen Versionen (versions/<v>) und alle Quellordner (<mod>/src/<gruppe>), die diese Version nicht
# nutzt, stellt CI, Standardversion der Skripte und das Release-Skript auf die eine Version um und vermerkt das in der
# README. Läuft im aktuellen Arbeitsverzeichnis; gedacht für einen frischen Branch bzw. Worktree.
#
# Aufruf: scripts/split-version.sh <version>      z. B. scripts/split-version.sh 26.3
set -euo pipefail
cd "$(dirname "$0")/.."
v="${1:?Version fehlt, z. B. 1.21.1}"
[ -f "versions/$v/gradle.properties" ] || { echo "Unbekannte Version: $v"; exit 1; }

# 1. Andere Versionen entfernen.
for dir in versions/*/; do
    other=$(basename "$dir")
    [ "$other" = "$v" ] || git rm -rq "versions/$other"
done

# 2. Quellgruppen, die diese Version nicht nutzt, entfernen.
for mod in core epochs bridge; do
    groups=$(grep "^${mod}_src_groups=" "versions/$v/gradle.properties" | cut -d= -f2 | tr ',' ' ')
    for dir in "$mod"/src/*/; do
        g=$(basename "$dir")
        if ! grep -qw "$g" <<<"$groups"; then git rm -rq "$mod/src/$g"; fi
    done
done

# 3. Skripte für andere Versionen (KubeJS-Test gibt es nur für 1.21.1 und 26.1).
if ! grep -q "kubejs" "versions/$v/gradle.properties"; then git rm -q scripts/kubejs-test.sh; fi
git rm -q scripts/split-version.sh   # wird auf dem Versions-Branch nicht mehr gebraucht

# 4. CI, Standardversion und Hinweise auf diese Version stellen.
python3 - "$v" <<'PY'
import re, sys
v = sys.argv[1]
def edit(path, fn):
    with open(path) as f:
        s = f.read()
    s2 = fn(s)
    with open(path, 'w') as f:
        f.write(s2)
edit('.github/workflows/build.yml', lambda s: re.sub(r'version: \[[^\]]*\]', f'version: ["{v}"]', s))
edit('settings.gradle', lambda s: s.replace("?: '1.21.1'", f"?: '{v}'").replace('(Standard 1.21.1)', f'(Standard {v})'))
for script in ('scripts/balance.sh', 'scripts/perf.sh'):
    edit(script, lambda s: s.replace('v="${1:-1.21.1}"', f'v="${{1:-{v}}}"').replace('Standard: 1.21.1', f'Standard: {v}'))
note = (f'> **Dieser Branch (`release/{v}`) enthält nur Minecraft {v}.** Entwickelt wird für alle Versionen gemeinsam '
        f'auf dem Hauptbranch; dieser Stand wurde daraus mit `scripts/split-version.sh {v}` erzeugt. '
        'Abschnitte der README zu anderen Versionen gelten hier nicht.\n\n')
edit('README.md', lambda s: s.replace('\n', '\n\n' + note, 1) if s.startswith('# ') else note + s)
PY
git add -A .github settings.gradle scripts README.md
echo "Stand für Minecraft $v vorbereitet."
