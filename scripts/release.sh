#!/usr/bin/env bash
# Baut alle drei Mods für alle Minecraft-Versionen (versions/*), prüft jedes Jar und legt sie mit Prüfsummen in dist/<version>/ ab.
#
# Geprüft wird je Jar: Metadaten vollständig eingesetzt (keine ${...}), Version und Minecraft-Bereich passen zum
# Dateinamen, Logo und Lizenz liegen bei. Den Spieltest macht scripts/smoke-test.sh (vor einem Release ausführen).
#
# Aufruf: scripts/release.sh
set -euo pipefail
cd "$(dirname "$0")/.."
version=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
out="dist/$version"
echo "== Strata $version: bauen …"
# collectJars löscht nie: Alte Jars (frühere Builds, entfernte Versionen) vorher entfernen, sonst landen sie im Release.
rm -f core/build/libs/*.jar epochs/build/libs/*.jar bridge/build/libs/*.jar
./gradlew --console=plain -q :core:build :epochs:build :bridge:build
rm -rf "$out"
mkdir -p "$out"
problems=0
count=0
for mod in core epochs bridge; do
    id="strata_$mod"
    for jar in "$mod"/build/libs/strata-"$mod"-neoforge-"$version"+*.jar; do
        name=$(basename "$jar")
        mc=${name#*+}; mc=${mc%.jar}
        toml=$(unzip -p "$jar" META-INF/neoforge.mods.toml)
        check() { if ! eval "$1"; then echo "FEHLER $name: $2"; problems=$((problems + 1)); fi; }
        check '! grep -q "\${" <<<"$toml"' "Platzhalter nicht ersetzt"
        check 'grep -q "version = \"$version+$mc\"" <<<"$toml"' "Version im mods.toml passt nicht"
        check 'grep -q "versionRange = \"\[$mc" <<<"$toml"' "Minecraft-Bereich passt nicht zu $mc"
        check 'unzip -l "$jar" "${id}_logo.png" >/dev/null' "Logo fehlt"
        check 'unzip -l "$jar" "LICENSE_$id" >/dev/null' "Lizenz fehlt"
        cp "$jar" "$out/"
        count=$((count + 1))
    done
done
(cd "$out" && sha256sum ./*.jar > SHA256SUMS)
cp CHANGELOG.md LICENSE "$out/"
echo "== $count Jars in $out, $problems Probleme"
versions=$(find versions -mindepth 1 -maxdepth 1 -type d | wc -l)
[ "$count" -eq $((3 * versions)) ] || { echo "FEHLER: erwartet $((3 * versions)) Jars (3 Mods × $versions Versionen)"; exit 1; }
[ "$problems" -eq 0 ]
