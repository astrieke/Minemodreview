#!/usr/bin/env bash
# Startet den Client auf einem virtuellen Bildschirm, lädt die Welt aus dem letzten
# Smoke-Test, teleportiert den Spieler in eine Epoche und speichert einen Screenshot.
#
# Aufruf: scripts/client-screenshot.sh <version> <epoche> <ausgabe.png>
#   z. B. scripts/client-screenshot.sh 1.21.1 crystal_age shot.png
# Voraussetzung: scripts/smoke-test.sh <version> lief vorher (Welt und Ankunftsplatz existieren).
# Optional: PYTHON=<python mit python-xlib>, um die Warnung „experimentelle Einstellungen“
# automatisch zu bestätigen (Vanilla zeigt sie einmal pro Welt mit Datapack-Dimensionen).
set -uo pipefail
cd "$(dirname "$0")/.."
v="$1"; epoch="$2"; out="$(realpath -m "$3")"

server_world="bridge/versions/$v/run/server/world"
client_dir="bridge/versions/$v/run/client"
log="$client_dir/screenshot.log"
[ -d "$server_world" ] || { echo "Erst scripts/smoke-test.sh $v ausführen"; exit 1; }

# Ankunftsplatz aus dem Selbsttest-Log lesen, z. B. "Ankunft=12, 55, 8".
pos=$(grep -oE "strata_epochs:$epoch: .*Ankunft=-?[0-9]+, -?[0-9]+, -?[0-9]+" "bridge/versions/$v/run/server/smoke.log" \
      | grep -oE 'Ankunft=.*' | sed 's/Ankunft=//; s/,//g' | tail -1)
read -r x y z <<< "$pos"

rm -rf "$client_dir/saves/strata_shot"
mkdir -p "$client_dir/saves"
cp -r "$server_world" "$client_dir/saves/strata_shot"
rm -rf "$client_dir/saves/strata_shot/playerdata"

# Kleines Datapack: Spieler einmalig zur Epoche teleportieren, Blick zum Tor (Osten).
dp="$client_dir/saves/strata_shot/datapacks/strata-shot"
mkdir -p "$dp/data/strata_shot/function" "$dp/data/minecraft/tags/function"
cp example-datapacks/strata-demo/pack.mcmeta "$dp/pack.mcmeta"
cat > "$dp/data/strata_shot/function/tick.mcfunction" <<MC
execute as @a[tag=!strata_shot] in strata_epochs:$epoch run tp @s $x.5 $y $z.5 -90 35
gamemode creative @a[tag=!strata_shot]
item replace entity @a[tag=!strata_shot] hotbar.0 with strata_epochs:core_drill
item replace entity @a[tag=!strata_shot] hotbar.1 with strata_epochs:core_sample
item replace entity @a[tag=!strata_shot] hotbar.2 with strata_epochs:crystal_quartz
item replace entity @a[tag=!strata_shot] hotbar.3 with strata_epochs:amber_shard
item replace entity @a[tag=!strata_shot] hotbar.4 with strata_epochs:time_anchor
item replace entity @a[tag=!strata_shot] hotbar.5 with strata_epochs:epoch_gate
item replace entity @a[tag=!strata_shot] hotbar.6 with strata_epochs:crystal_quartz_ore
item replace entity @a[tag=!strata_shot] hotbar.7 with strata_epochs:amber_ore
item replace entity @a[tag=!strata_shot] hotbar.8 with strata_epochs:chronostone
tag @a add strata_shot
MC
echo '{"values":["strata_shot:tick"]}' > "$dp/data/minecraft/tags/function/tick.json"

cat > "$client_dir/options.txt" <<OPT
lang:de_de
tutorialStep:none
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
renderDistance:6
gamma:1.0
OPT

display=":97"
Xvfb "$display" -screen 0 "${XVFB_SCREEN:-1280x720x24}" +extension GLX +render >/dev/null 2>&1 &
xpid=$!
sleep 2
DISPLAY="$display" ./gradlew --console=plain ":bridge-$v:runClient" -PquickPlay=strata_shot > "$log" 2>&1 &
gpid=$!

# Warten, bis die Welt geöffnet wird, dann die Experimentell-Warnung bestätigen.
for _ in $(seq 1 120); do
    if grep -qE 'Found new data pack|Crash report|BUILD FAILED' "$log"; then break; fi
    sleep 2
done
sleep 6
if [ -n "${PYTHON:-}" ]; then
    DISPLAY="$display" "$PYTHON" tools/xclick.py 798 447   # „Ich weiß, was ich tue!“ bei 1280x720
fi

ok=0
for _ in $(seq 1 60); do
    if grep -qE 'joined the game|hat das Spiel betreten' "$log"; then ok=1; break; fi
    if grep -qE 'Crash report|BUILD FAILED' "$log"; then break; fi
    sleep 3
done
if [ $ok -eq 1 ]; then
    sleep 25
    DISPLAY="$display" import -window root "$out"
    echo "Screenshot: $out"
    if [ -n "${PYTHON:-}" ]; then
        # Zweites Bild mit geöffnetem Inventar (zeigt z. B. die JEI-Itemliste).
        DISPLAY="$display" "$PYTHON" tools/xclick.py key e
        sleep 4
        DISPLAY="$display" import -window root "${out%.png}_inventar.png"
        echo "Screenshot: ${out%.png}_inventar.png"
    fi
else
    echo "Client hat die Welt nicht geladen, siehe $log"
fi
kill "$gpid" 2>/dev/null; wait "$gpid" 2>/dev/null
kill "$xpid" 2>/dev/null
[ $ok -eq 1 ]
