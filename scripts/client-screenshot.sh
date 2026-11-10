#!/usr/bin/env bash
# Startet den Client auf einem virtuellen Bildschirm, lädt die Welt aus dem letzten
# Smoke-Test, teleportiert den Spieler in eine Epoche und speichert einen Screenshot.
#
# Aufruf: scripts/client-screenshot.sh <version> <epoche> <ausgabe.png>
#   z. B. scripts/client-screenshot.sh 1.21.1 crystal_age shot.png
# Voraussetzung: scripts/smoke-test.sh <version> lief vorher (Welt und Ankunftsplatz existieren).
# Optional: PYTHON=<python mit python-xlib>, um die Warnung „experimentelle Einstellungen“
# automatisch zu bestätigen (Vanilla zeigt sie einmal pro Welt mit Datapack-Dimensionen).
# Optional: SHOT_MOB=<entity-id>, z. B. strata_epochs:geode_crawler. Dann baut das Skript neben
# dem Ankunftsplatz eine kleine Bühne (Calcit-Boden, ein Kristallquarz-Erz) und stellt zwei
# Exemplare darauf: links von der Seite, rechts von vorn. Der Spieler schaut von oben darauf.
# Optional mit SHOT_MOB: SHOT_ACT=1 stellt statt der starren Exemplare ein Tier mit KI neben ein Erz
# und schlägt es nach 7 s einmal. Das Skript nimmt dann eine Bildserie (alle 0,5 s, 14 s lang) als
# <ausgabe>_act_NN.png auf, um Laufen, Fähigkeiten und Animationen im Spiel zu prüfen.
# SHOT_SURVIVAL=1 schaltet den Spieler dafür in den Überlebensmodus (für feindliche Mobs).
# SHOT_GEAR=1: Spieler trägt die Geodenrüstung und hält Strata-Werkzeuge; das Bild zeigt ihn von vorn (F5).
# SHOT_AWAKEN=1 (mit SHOT_MOB): Verfestigung nach 20 s, Bildserie von oben (F5), bis ein Steinzeuge erwacht.
# SHOT_PITCH=<Grad> ändert den Blickwinkel nach unten (Standard 28, für große Mobs kleiner).
# SHOT_TRANSPLANT=<epoche>: in der Oberwelt (flache Testwelt) startet nach 31 s eine Transplantation
#   (Radius 10) vor dem Spieler; Bildserie 40 s lang (alle 1 s), damit man Welle, Displays und Wachsen sieht.
# SHOT_CURIOS=1: legt nach 3 s einen Zeitanker in den Curios-Talisman-Slot und schreibt /strata_epochs status ins Log.
# SHOT_SPIRALIT=1: in der Epoche schlüpft nach 29 s ein Spiralit für den Spieler; Bildserie 20 s lang.
# SHOT_BIOME=<biom>, z. B. geode_vaults: Spieler landet auf dem Höhlenboden dieses Bioms der Epoche
#   (Fundort aus dem Selbsttest-Log), mit Nachtsicht, damit man Oberfläche und Features sieht.
# SHOT_CROPS=1: neben dem Ankunftsplatz stehen die vier Wachstumsstufen der Epochen-Pflanze dieser Epoche
#   auf ihrem Boden (je zwei Reihen), mit Nachtsicht; die Samen liegen in der Schnellleiste.
# SHOT_BEARING=1: Kurz vor dem Bild peilt der Spieler die Biome (/strata_epochs bearing), die Zeilen stehen im Chat.
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

# Der vorige Client speichert beim Beenden evtl. noch: Löschen notfalls wiederholen.
for _ in 1 2 3 4 5 6 7 8 9 10; do rm -rf "$client_dir/saves/strata_shot" 2>/dev/null && break; sleep 3; done
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
if [ -n "${SHOT_BIOME:-}" ]; then
    bpos=$(grep -oE "Biome strata_epochs:$epoch: .*" "bridge/versions/$v/run/server/smoke.log" | tail -1 \
           | grep -oE "$SHOT_BIOME [0-9]+% ab -?[0-9]+ Blöcken \(bei -?[0-9]+ -?[0-9]+\)" | grep -oE 'bei -?[0-9]+ -?[0-9]+' | sed 's/bei //')
    [ -n "$bpos" ] || { echo "Biom $SHOT_BIOME nicht im Selbsttest-Log"; exit 1; }
    read -r bx bz <<< "$bpos"
    f="$dp/data/strata_shot/function/tick.mcfunction"
    sed -i "s|^execute as @a\[tag=!strata_shot\].*|execute as @a[tag=!strata_shot] in strata_epochs:$epoch run spreadplayers $bx $bz 0 12 under 100 false @s\nexecute as @a[tag=!strata_shot] at @s run tp @s ~ ~ ~ -90 ${SHOT_PITCH:-12}\neffect give @a[tag=!strata_shot] minecraft:night_vision infinite 0 true|" "$f"
fi
if [ -n "${SHOT_BEARING:-}" ]; then
    # Peilung kurz vor dem Bild auslösen, damit die Chatzeilen noch zu sehen sind.
    f="$dp/data/strata_shot/function/tick.mcfunction"
    sed -i "s|^tag @a add strata_shot|execute if entity @a[tag=!strata_shot] run schedule function strata_shot:bearing 485t\ntag @a add strata_shot|" "$f"
    printf 'execute as @a run strata_epochs bearing\nschedule function strata_shot:bearing 60t\n' > "$dp/data/strata_shot/function/bearing.mcfunction"
fi
if [ -n "${SHOT_TRANSPLANT:-}" ]; then
    f="$dp/data/strata_shot/function/tick.mcfunction"
    sed -i "s|^execute as @a\[tag=!strata_shot\].*|execute as @a[tag=!strata_shot] in minecraft:overworld run tp @s 0.5 -55 -15.5 0 18\nexecute if entity @a[tag=!strata_shot] run schedule function strata_shot:transplant 620t|" "$f"
    cat > "$dp/data/strata_shot/function/transplant.mcfunction" <<MC
time set day
execute in minecraft:overworld positioned 0 -60 0 run strata_epochs transplant strata_epochs:$SHOT_TRANSPLANT 10
MC
fi
if [ -n "${SHOT_CURIOS:-}" ]; then
    GRADLE_ARGS="${GRADLE_ARGS:-} -PwithCurios"
    f="$dp/data/strata_shot/function/tick.mcfunction"
    sed -i "s|^tag @a add strata_shot|execute if entity @a[tag=!strata_shot] run schedule function strata_shot:curios 60t\ntag @a add strata_shot|" "$f"
    cat > "$dp/data/strata_shot/function/curios.mcfunction" <<MC
item replace entity @a hotbar.4 with minecraft:air
execute as @a run strata_epochs status
# Curios kennt seine Slot-Typen erst nach dem Laden der Funktionen: Befehl daher über einen Befehlsblock.
execute at @p run setblock ~ ~-3 ~ minecraft:command_block{Command:"curios replace charm 0 @p with strata_epochs:time_anchor",auto:1b}
schedule function strata_shot:curios_check 20t
MC
    cat > "$dp/data/strata_shot/function/curios_check.mcfunction" <<MC
execute as @a run strata_epochs status
MC
fi
if [ -n "${SHOT_SPIRALIT:-}" ]; then
    f="$dp/data/strata_shot/function/tick.mcfunction"
    sed -i "s|^tag @a add strata_shot|execute if entity @a[tag=!strata_shot] run schedule function strata_shot:spiralit 580t\ntag @a add strata_shot|" "$f"
    cat > "$dp/data/strata_shot/function/spiralit.mcfunction" <<MC
effect give @a minecraft:night_vision infinite 0 true
execute as @a at @s run tp @s ~ ~ ~ -90 15
execute as @a at @s run strata_epochs spiralit
MC
fi
if [ -n "${SHOT_GEAR:-}" ]; then
    f="$dp/data/strata_shot/function/tick.mcfunction"
    sed -i '/^tag @a add strata_shot/d' "$f"
    for slot in "armor.head geode_helmet" "armor.chest geode_chestplate" "armor.legs geode_leggings" "armor.feet geode_boots" \
                "hotbar.0 amber_blade" "hotbar.1 crystal_quartz_sword" "hotbar.2 crystal_quartz_pickaxe" "hotbar.3 crystal_quartz_axe" \
                "hotbar.4 crystal_quartz_shovel" "hotbar.5 crystal_quartz_hoe"; do
        set -- $slot
        echo "item replace entity @a[tag=!strata_shot] $1 with strata_epochs:$2" >> "$f"
    done
    echo "effect give @a[tag=!strata_shot] minecraft:night_vision infinite 0 true" >> "$f"
    echo "tag @a add strata_shot" >> "$f"
fi
if [ -n "${SHOT_CROPS:-}" ]; then
    crop=quartz_thistle; seed=quartz_thistle_seeds; floor=minecraft:calcite
    [ "$epoch" = spore_age ] && { crop=resin_fern; seed=resin_fern_spores; floor=minecraft:mycelium; }
    [ "$epoch" = ash_age ] && { crop=ember_tubers; seed=ember_tuber; floor=strata_epochs:volcanic_ash; }
    f="$dp/data/strata_shot/function/tick.mcfunction"
    sed -i "s|^execute as @a\[tag=!strata_shot\].*|execute as @a[tag=!strata_shot] in strata_epochs:$epoch run tp @s $x.5 $((y + 1)) $((z + 3)).5 0 ${SHOT_PITCH:-40}\nexecute if entity @a[tag=!strata_shot] run schedule function strata_shot:crops 60t\neffect give @a[tag=!strata_shot] minecraft:night_vision infinite 0 true|" "$f"
    sed -i "s|^tag @a add strata_shot|item replace entity @a[tag=!strata_shot] hotbar.0 with strata_epochs:$seed\ntag @a add strata_shot|" "$f"
    {
        echo "execute in strata_epochs:$epoch run fill $((x - 5)) $((y - 2)) $((z + 2)) $((x + 5)) $((y + 6)) $((z + 10)) strata_epochs:chronostone hollow"
        echo "execute in strata_epochs:$epoch run fill $((x - 4)) $((y - 1)) $((z + 3)) $((x + 4)) $((y - 1)) $((z + 9)) $floor"
        for a in 0 1 2 3; do
            for row in 5 7; do
                echo "execute in strata_epochs:$epoch run setblock $((x - 3 + 2 * a)) $y $((z + row)) strata_epochs:$crop[age=$a]"
            done
        done
    } > "$dp/data/strata_shot/function/crops.mcfunction"
fi
if [ -n "${SHOT_MOB:-}" ]; then
    # Bühne erst nach 3 s bauen, wenn die Chunks am Ankunftsplatz geladen sind. Geschlossener Kasten
    # (sonst läuft Wasser hinein), Boden wie in der Epoche.
    floor=minecraft:calcite
    [ "$epoch" = spore_age ] && floor=minecraft:mycelium
    [ "$epoch" = ash_age ] && floor=strata_epochs:volcanic_ash
    sed -i "s|^execute as @a\[tag=!strata_shot\].*|execute as @a[tag=!strata_shot] in strata_epochs:$epoch run tp @s $x.5 $((y + 1)) $((z + 3)).5 0 ${SHOT_PITCH:-28}\nexecute if entity @a[tag=!strata_shot] run schedule function strata_shot:stage 60t|" \
        "$dp/data/strata_shot/function/tick.mcfunction"
    cat > "$dp/data/strata_shot/function/stage.mcfunction" <<MC
execute in strata_epochs:$epoch run fill $((x - 6)) $((y - 2)) $((z + 2)) $((x + 6)) $((y + 6)) $((z + 14)) strata_epochs:chronostone hollow
execute in strata_epochs:$epoch run fill $((x - 5)) $((y - 1)) $((z + 3)) $((x + 5)) $((y - 1)) $((z + 13)) $floor
execute in strata_epochs:$epoch run setblock $((x + 3)) $y $((z + 10)) strata_epochs:crystal_quartz_ore
MC
    if [ -n "${SHOT_AWAKEN:-}" ]; then
        # Erwachen aus Stein testen: Verfestigung nach 20 s statt 10 min, Spieler ohne Zeitanker im Überlebensmodus
        # mitten auf der Bühne. Ein Steinzeuge erwacht dann aus einem versteinerten Bodenblock in der Nähe.
        mkdir -p "$dp/data/strata_epochs/strata_core/epoch"
        printf '{\n  "weight": 3,\n  "solidify_seconds": 20,\n  "dimension": "strata_epochs:%s"\n}\n' "$epoch" \
            > "$dp/data/strata_epochs/strata_core/epoch/$epoch.json"
        cat >> "$dp/data/strata_shot/function/stage.mcfunction" <<MC
clear @a strata_epochs:time_anchor
gamemode survival @a
execute in strata_epochs:$epoch run tp @a $x.5 $y $((z + 8)).5 0 ${SHOT_PITCH:-80}
MC
    elif [ -n "${SHOT_ACT:-}" ]; then
        # Start, sobald der Spieler Hotbar-Platz 9 wählt (das Skript drückt dazu die Taste 9).
        sed -i "1i execute as @a[tag=strata_shot,tag=!strata_act] if items entity @s weapon.mainhand strata_epochs:chronostone run function strata_shot:act" \
            "$dp/data/strata_shot/function/tick.mcfunction"
        cat > "$dp/data/strata_shot/function/act.mcfunction" <<MC
tag @s add strata_act
execute in strata_epochs:$epoch run setblock $((x + 3)) $y $((z + 8)) strata_epochs:crystal_quartz_ore
execute in strata_epochs:$epoch run summon $SHOT_MOB $((x - 2)).5 $y $((z + 8)).5 {PersistenceRequired:1b,Rotation:[-90f,0f]}
schedule function strata_shot:hit 140t
MC
        # SHOT_SURVIVAL=1: Spieler im Überlebensmodus, damit feindliche Mobs ihn angreifen.
        [ -z "${SHOT_SURVIVAL:-}" ] || echo 'gamemode survival @s' >> "$dp/data/strata_shot/function/act.mcfunction"
        echo "damage @e[type=$SHOT_MOB,limit=1,sort=nearest] 1 minecraft:player_attack by @p" > "$dp/data/strata_shot/function/hit.mcfunction"
    else
        cat >> "$dp/data/strata_shot/function/stage.mcfunction" <<MC
execute in strata_epochs:$epoch run summon $SHOT_MOB $((x - 1)).5 $y $((z + 8)).5 {NoAI:1b,PersistenceRequired:1b,Rotation:[90f,0f]}
execute in strata_epochs:$epoch run summon $SHOT_MOB $((x + 2)).5 $y $((z + 8)).5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}
MC
    fi
    # SHOT_DARK=1: ohne Nachtsicht, so wie man den Mob in der dunklen Epoche sieht.
    [ -n "${SHOT_DARK:-}" ] || echo 'effect give @a minecraft:night_vision infinite 0 true' >> "$dp/data/strata_shot/function/stage.mcfunction"
fi
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

# 26.x: Das Ladefenster von NeoForge braucht ein GLX-Format, das Xvfb nicht hat -> abschalten.
# Minecraft selbst weicht dann auf Vulkan aus; ohne Grafikkarte geht das mit Mesa lavapipe
# (Paket mesa-vulkan-drivers). Unter 1.21.x läuft OpenGL direkt über Xvfb.
mkdir -p "$client_dir/config"
if [ -f "$client_dir/config/fml.toml" ]; then
    sed -i 's/^earlyWindowControl = true/earlyWindowControl = false/' "$client_dir/config/fml.toml"
else
    echo 'earlyWindowControl = false' > "$client_dir/config/fml.toml"
fi
[ -f /usr/share/vulkan/icd.d/lvp_icd.json ] && export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json

display=":97"
Xvfb "$display" -screen 0 "${XVFB_SCREEN:-1280x720x24}" +extension GLX +render >/dev/null 2>&1 &
xpid=$!
sleep 2
DISPLAY="$display" ./gradlew --console=plain ":bridge:$v:runClient" -PquickPlay=strata_shot ${GRADLE_ARGS:-} > "$log" 2>&1 &
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
    if [ -n "${SHOT_GEAR:-}" ] && [ -n "${PYTHON:-}" ]; then
        DISPLAY="$display" "$PYTHON" tools/xclick.py key F5; sleep 1
        DISPLAY="$display" "$PYTHON" tools/xclick.py key F5; sleep 2      # zweimal F5: Kamera von vorn
    fi
    DISPLAY="$display" import -window root "$out"
    echo "Screenshot: $out"
    if [ -n "${SHOT_TRANSPLANT:-}${SHOT_SPIRALIT:-}" ]; then
        n=$([ -n "${SHOT_TRANSPLANT:-}" ] && echo 39 || echo 19)
        for i in $(seq -w 0 "$n"); do
            DISPLAY="$display" import -window root "${out%.png}_act_$i.png"
            sleep 1
        done
        echo "Bildserie: ${out%.png}_act_*.png"
    elif [ -n "${SHOT_AWAKEN:-}" ] && [ -n "${PYTHON:-}" ]; then
        DISPLAY="$display" "$PYTHON" tools/xclick.py key F5; sleep 1
        for i in $(seq -w 0 44); do
            DISPLAY="$display" import -window root "${out%.png}_act_$i.png"
            sleep 1
        done
        echo "Bildserie: ${out%.png}_act_*.png"
    elif [ -n "${SHOT_ACT:-}" ] && [ -n "${PYTHON:-}" ]; then
        DISPLAY="$display" "$PYTHON" tools/xclick.py key 9
        for i in $(seq -w 0 27); do
            DISPLAY="$display" import -window root "${out%.png}_act_$i.png"
            sleep 0.5
        done
        echo "Bildserie: ${out%.png}_act_*.png"
    elif [ -n "${PYTHON:-}" ]; then
        if [ -n "${SHOT_GEAR:-}" ]; then DISPLAY="$display" "$PYTHON" tools/xclick.py key F5; sleep 1; fi
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
