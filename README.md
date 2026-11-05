# Strata – Tiefenzeit

Ein Modset für Minecraft-Modpacks. Das Konzept steht in [KONZEPT.md](KONZEPT.md), der Plan für eigene Mobs in [docs/MOBS.md](docs/MOBS.md).

Eine Hauptmod und zwei Addons. Alle Texte gibt es auf Deutsch und Englisch.

| Mod | Rolle | Inhalt |
|---|---|---|
| **Strata Core** | Hauptmod (Pflicht) | Epochen, Kodex-Knoten und Mob-Werte per Datapack, Kodex-Fortschritt, `/strata`-Befehle, öffentliche API |
| **Strata Epochs** | Addon (braucht Core) | Kernbohrer, Epochentor, Verfestigung, Kristallzeit und Sporenzeit, 3 Mobs, Ausrüstung |
| **Strata Bridge** | Addon (braucht Core und Epochs) | JEI-Infoseiten, Jade-Hinweise, Advancements für Quest-Mods, Event für KubeJS |

## Unterstützte Versionen

| Minecraft | NeoForge | Java |
|---|---|---|
| 1.21.1 | 21.1.265 | 21 |
| 1.21.11 | 21.11.48 | 21 |
| 26.1.x | 26.1.2.124 | 25 |
| 26.2 | 26.2.0.93 | 25 |
| 26.3 | 26.3.0.99 | 25 |

Für jede Version gilt: Alle drei Mods bauen, und der Server startet mit dem ganzen Set plus JEI und Jade. Ein Selbsttest generiert beide Epochen-Dimensionen, findet alle Advancements und prüft die Kette „Kodex-Knoten freischalten → Event → Advancement“.

| Fremd-Mod | 1.21.1 | 1.21.11 | 26.1 | 26.2 | 26.3 |
|---|---|---|---|---|---|
| JEI | 19.70 | 27.54 | 29.53 | 30.48 | 31.17 |
| Jade | 15.10 | 21.1 | 26.1 | 26.2 | 26.3 |

Beide sind optional. Strata läuft auch ohne sie.

Nicht jede 1.21-Zwischenversion (1.21.2 bis 1.21.10) wird unterstützt. Modpacks sammeln sich auf wenigen Versionen: 1.21.1 ist die große Modpack-Basis, 1.21.11 die letzte 1.21-Version. Eine Zwischenversion lässt sich bei konkreter Nachfrage ergänzen (siehe unten).

## So spielt sich Strata Epochs

| Kristallzeit | Sporenzeit |
|---|---|
| ![Kristallzeit](docs/screenshots/crystal_age.png) | ![Sporenzeit](docs/screenshots/spore_age.png) |

*Ankunft in den Epochen (Minecraft 1.21.1). Vorne das Epochentor für den Rückweg, unten alle neuen Items.*

1. **Kernbohrer** bauen (Eisen, Kupfer, Stock) und auf den Boden rechtsklicken. Du bekommst einen **Bohrkern**. Er zeigt, welche Epoche unter diesem Ort liegt. Jede Region von 8 × 8 Chunks hat ihre eigene Epoche. Epochen liegen verschieden tief:
   - Der Kernbohrer (Stufe 1) erreicht die Kristallzeit.
   - Der **Kristall-Kernbohrer** (Stufe 2: Kernbohrer und 4 Kristallquarz) erreicht auch die Sporenzeit.
   - Der **Tiefenkernbohrer** (Stufe 3: Kristall-Kernbohrer, Bernstein und eine Spitze aus dem Tag `strata_epochs:deep_drill_tips`, Standard Diamant) erreicht auch die **Aschezeit**.
2. **Epochentor** bauen (Obsidian, Amethyst, Kompass) und mit dem Bohrkern rechtsklicken. Der Kern wird verbraucht, und du landest in der Epoche. Neben dir steht ein Tor für den Rückweg.
3. Alle drei Epochen sind Höhlenwelten aus **Zeitgestein**:
   - In der **Kristallzeit** findest du Kristallquarz.
   - In der **Sporenzeit** findest du Bernstein.
   - In der **Aschezeit** liegen Basaltdeltas mit Vulkanasche. Dort findest du Glutsteinerz, das **Glutsplitter** liefert (Brennstoff wie eine Lohenrute).
4. **Verfestigung:** Nach 75 % der erlaubten Zeit (Kristallzeit 10 min, Sporenzeit 7 min, Aschezeit 5 min) kommt eine Warnung. Danach wirst du langsamer, und um dich herum versteinern Blöcke.
5. Ein **Zeitanker** im Inventar (Kristallquarz, Bernstein, Gold) verdoppelt die erlaubte Zeit.
6. Rechtsklick auf das Tor bringt dich zurück. Tore in Epochen lassen sich nicht abbauen, damit der Rückweg nie verloren geht.

**Kernanalyse-Tisch** (Kupfer, Fernrohr, Holz, Steinstufe): Rechtsklick mit Bohrkernen analysiert den Stapel. Die Notiz am Kern zeigt:
- Tiefe und Zeitlimit
- Fundstücke
- den Kodex-Stand für den Transplantator (besucht und Fossil, jeweils ✔ oder ✘)

Wer mit einem analysierten Kern eine Epoche betritt, hat **25 % mehr Zeit** bis zur Verfestigung.

**Fossilien:** Zeitgestein (2 %) und versteinerter Stein (15 %) lassen in den Epochen **Fossilbrocken** fallen. So präparierst du sie:
- **Meißel** in die Haupthand, Brocken in die zweite Hand, dann Rechtsklick auf den **Präparationstisch**.
- Fünf Schläge legen das Fossil frei.
- Wer hastig schlägt, riskiert bis zu 30 % Bruch pro Schlag. Mit gut einer Sekunde Pause sind es nur 3 %.

Je nach Epoche bekommst du einen Ammonit, einen Farnabdruck oder einen Urzeitschädel. Das erste Fossil einer Epoche schließt sie im Kodex ab (`mastered_<epoche>`).

**Spiralit (Begleiter):** Ammonit in die Haupthand, Keimfrische in die zweite, Rechtsklick auf den Präparationstisch. Er schlüpft und schwebt rechts vor dir (einer pro Spieler).
- **Uhr:** Seine Spirale zeigt in 4 Segmenten die Restzeit in der Epoche. In der Warnphase blinkt das innerste Segment, und er läutet.
- **Ruhe:** In seiner Nähe läuft die Verfestigung 20 % langsamer. Das stapelt nicht mit der Geodenrüstung.
- **Erzsuche:** Alle 15 s zeigt er auf ein nahes Epochen-Erz.
- Er reist durch Epochentore mit. Schleichen + Rechtsklick: warten oder folgen; Kristallquarz heilt ihn.

**Epochen-Transplantator** (Kristallquarz, Bernstein, Glutsplitter, Zeitanker, Zeitgestein): Rechtsklick mit einem Bohrkern einer erforschten Epoche verwandelt die Umgebung (Radius 12) dauerhaft in diese Epoche. Er verbraucht den Kern und ein Fossil der Epoche. Der Ablauf:
1. Eine Welle läuft nach außen, die Bodenschichten wölben sich sichtbar auf.
2. Das Biom wechselt.
3. Je nach Epoche wachsen Amethystspitzen mit Kristallquarzerz, Riesenpilze oder Basaltgruppen mit Glutsteinerz.

Nur natürliche Blöcke (Tag `strata_epochs:transplantable`) werden verändert, Bauten bleiben. Er wirkt nur in der Oberwelt. Radius, Tempo und Anzahl stehen in der Config (`[transplanter]`), die Landschaft im Datapack (Feld `landscape` der Epoche).

| Transplantation (Kristallzeit, 26.3) | Spiralit (1.21.1) |
|---|---|
| ![Transplantation](docs/screenshots/phase3/transplant_crystal_26.3.png) | ![Spiralit](docs/screenshots/phase3/spiralit_follow_1.21.1.png) |

**Mobs:** Geodenkriecher (Kristallzeit, neutral, findet Erz), Sporenbalg (Sporenzeit, Wolken beschleunigen die Verfestigung) und Steinzeuge (erwacht aus versteinertem Stein bei Spielern, die zu lange bleiben). Details in [docs/MOBS.md](docs/MOBS.md).

**Ausrüstung:**

| Item | Herstellung | Besonderheit |
|---|---|---|
| Kristallquarz-Schwert, -Spitzhacke, -Axt, -Schaufel, -Hacke | Kristallquarz und Stöcke (Vanilla-Muster) | zwischen Eisen und Diamant (700 Haltbarkeit, Tempo 7) |
| Geodenrüstung (Helm, Brustpanzer, Beinschutz, Stiefel) | Calcitschuppen (Drop des Geodenkriechers) und Kristallquarz | etwas stärker als Eisen; volles Set: Verfestigung läuft 25 % langsamer |
| Bernsteinklinge | Bernstein, Sporenbeutel, Stock | Treffer verlangsamen das Ziel 2 s |
| Keimfrische | Sporenbeutel, Bernstein, Glasflasche | Rechtsklick in einer Epoche: Uhr 60 s zurück |
| Kodex-Fragment | Drop des Steinzeugen | Rechtsklick: Kodex-Knoten „Echo eines Reisenden“ |

Kodex-Knoten werden automatisch freigeschaltet:
- `strata_epochs:first_core` mit dem ersten Bohrkern
- `visited_<epoche>` beim ersten Besuch einer Epoche
- `fossil_<epoche>` und `mastered_<epoche>` mit dem ersten präparierten Fossil
- `first_analysis` nach der ersten Kernanalyse
- `first_transplant` nach der ersten Transplantation

## Strata Bridge: Anbindung an andere Mods

![Jade-Hinweis am Epochentor](docs/screenshots/jade_gate.png)

| Mod | Was Strata Bridge macht |
|---|---|
| **JEI** | Infoseiten für Kernbohrer, Bohrkern, Epochentor, Zeitanker, beide Erze und versteinerten Stein. **EMI** kann JEI-Plugins mitnutzen, wenn JEI daneben installiert ist (nicht getestet). |
| **Jade** | Hinweis beim Anschauen: am Tor „Reise in die Epoche“ bzw. in der Epoche „Rückweg nach Hause“, an Erzen der Fundort. |
| **FTB Quests, Heracles & Co.** | Für jeden Kodex-Knoten `ns:pfad` vergibt Strata das Advancement `ns:strata_codex/pfad`, falls es existiert. Quests prüfen das als Advancement-Aufgabe. Für die Knoten von Strata Epochs sind die Advancements dabei. Als Quest-Belohnung schaltet `/strata codex grant @p <knoten>` Knoten frei. |
| **KubeJS** | Kein Plugin nötig: Strata feuert bei jeder Freischaltung das NeoForge-Event `dev.strata.core.api.CodexNodeUnlockedEvent` (mit `getPlayer()` und `getNode()`). KubeJS kann es über `NativeEvents.onEvent(...)` abfangen (mit KubeJS selbst noch nicht getestet). |

Eigene Kodex-Knoten bekommen ein Advancement, indem der Pack-Autor einfach `data/<ns>/advancement/strata_codex/<pfad>.json` mit dem Kriterium `"trigger": "minecraft:impossible"` anlegt.

## Aufbau

```
versions/<v>/gradle.properties   MC-, NeoForge- und Java-Version, Quellordner je Mod
common/                          Reine Spiellogik ohne Minecraft + JUnit-Tests
core/src/shared/                 Strata Core, in allen Versionen gleich (inkl. api/StrataAPI)
core/src/mc_*/, dpreg_*/         Versionsanbindung von Core (Klassen Mc, McDatapack)
epochs/src/shared/               Strata Epochs, in allen Versionen gleich; Assets, Tags, Lang
epochs/src/reg_*/, break_*/      Versionsanbindung von Epochs (Reg, BreakHook)
epochs/worldgen/                 Epochen-Spezifikation und Rezepte (Quelle für das Worldgen)
epochs/worldgen.gradle           Erzeugt Worldgen, Loot-Tabellen und Rezepte pro Version
bridge/src/shared/               Strata Bridge: Advancement-Anbindung, Texte für JEI/Jade
bridge/src/mc_*/                 JEI- und Jade-Plugins (nur geladen, wenn die Mod installiert ist)
bridge/generated.gradle          Erzeugt das Wurzel-Advancement passend zur Version
example-datapacks/strata-demo/   Demo-Datapack (2 Epochen ohne Dimension, 3 Kodex-Knoten)
scripts/smoke-test.sh            Startet pro Version einen Server und prüft das Log
scripts/client-screenshot.sh     Startet den Client auf einem virtuellen Bildschirm und macht einen Screenshot
tools/make_textures.py           Erzeugt die Texturen (mit --preview <datei> ein Vorschaublatt)
tools/blockbench-mcp/            MCP-Server + Blockbench-Plugin, damit Claude Modelle in Blockbench bauen kann
```

**Warum das Worldgen erzeugt wird:** Mojang ändert das Format von Dimensionen, Biomen, Erz-Features, Loot-Tabellen und Rezepten fast in jeder Version. In 1.21.11 kamen z. B. „attributes“ dazu, in 26.3 wurde `configured_feature` zu `feature`. Der Task `generateEpochData` nimmt deshalb die Vanilla-Dateien genau der gebauten Version als Vorlage (Höhlen-Dimension, Höhlen-Noise, Tropfstein- und Üppige-Höhlen-Biom, Vanilla-Loot) und ändert nur wenige Felder.

## Bauen und testen

Alle drei Mods liegen in einem Repo, gebaut wird aber **pro Mod in ihrem Ordner**. Jeder Ordner hat ein eigenes `gradlew`, das das Gradle des Haupt-Repos nutzt.

```bash
cd core                         # bzw. epochs oder bridge
./gradlew build                 # alle 5 Minecraft-Versionen dieser Mod
./gradlew 1.21.1:build          # nur eine Version
```

Unter Windows heißt der Befehl `gradlew build` bzw. `gradlew 1.21.1:build`.

Die fertigen Jars aller Versionen landen gesammelt in **`<mod>/build/libs/`**, z. B. `core/build/libs/strata-core-neoforge-0.1.0+1.21.1.jar`. `epochs` und `bridge` bauen ihre Abhängigkeiten (`core`, bzw. `core` und `epochs`) automatisch mit.

Gradle braucht zum Starten Java 17 oder neuer. Fehlende JDKs (21 für 1.21.x, 25 für 26.x) lädt Gradle automatisch herunter.

**Vom Hauptordner aus** geht weiterhin alles auf einmal:

```bash
./gradlew :common:test                 # Spiellogik testen
./gradlew build                        # alle Mods für alle Versionen
./gradlew :bridge:1.21.1:runClient     # Spiel mit dem ganzen Set + JEI + Jade starten
scripts/smoke-test.sh                  # Server + Selbsttest in allen Versionen
scripts/smoke-test.sh 1.21.1 26.3      # nur bestimmte Versionen
```

## Eigene Epoche hinzufügen (Pack-Autoren)

Eine Epoche ohne eigene Dimension braucht nur ein Datapack, siehe `example-datapacks/strata-demo`. Bohrkerne zeigen sie dann an, das Tor meldet „noch nicht erreichbar“.

Eine Epoche mit Dimension kommt in `epochs/worldgen/epochs.json` (Vorlage-Biom, Farben, Erze) plus einen Eintrag unter `epochs/src/shared/resources/data/strata_epochs/strata_core/epoch/`. Andere Mods können auch eigene Dimensionen per Datapack mitbringen und im Feld `dimension` darauf verweisen.

## Neue Minecraft-Version hinzufügen

1. `versions/<v>/gradle.properties` anlegen (Vorlage: eine bestehende Version).
2. `./gradlew :core:<v>:compileJava :epochs:<v>:compileJava :bridge:<v>:compileJava` ausführen.
3. Meldet der Compiler Fehler, eine neue Quellgruppe unter `core/src/` bzw. `epochs/src/` mit angepasster Anbindungsklasse anlegen und in `gradle.properties` eintragen.
4. `scripts/smoke-test.sh <v>` ausführen und die Version in die CI-Matrix (`.github/workflows/build.yml`) aufnehmen.

## Datapack-Format (Strata Core)

```
data/<namespace>/strata_core/epoch/<name>.json
{ "weight": 3, "solidify_seconds": 600, "dimension": "strata_epochs:crystal_age", "depth": 1,
  "landscape": { "biome": "strata_epochs:crystal_hollows", "top": "minecraft:calcite", "under": "minecraft:smooth_basalt",
                 "filler": "strata_epochs:chronostone", "depth": 10,
                 "features": ["strata_epochs:crystal_spire"], "feature_count": 6 } }

data/<namespace>/strata_core/codex_node/<name>.json
{ "requires": ["<namespace>:first_core"] }
```

- `weight`: Häufigkeit der Epoche unter der Oberwelt (0 = nie).
- `solidify_seconds`: Zeit bis zur Verfestigung (0 = aus, Standard 600).
- `dimension`: optional.
- `depth`: 1–3, ab welcher Bohrer-Stufe die Epoche erreichbar ist (Standard 1).
- `landscape`: optional, für den Transplantator.
  - `features` nimmt konfigurierte Features wie `minecraft:huge_red_mushroom` oder die eigenen Wuchsformen `strata_epochs:crystal_spire` und `strata_epochs:basalt_cluster`.
  - `feature_count` gilt für Radius 12 und wächst mit der Fläche.

## Befehle

| Befehl | Wer | Zweck |
|---|---|---|
| `/strata epoch` | alle | Welche Epoche liegt unter mir? |
| `/strata codex list` | alle | Eigener Kodex-Fortschritt und nächste Schritte |
| `/strata codex unlock <knoten>` | OP | Knoten für sich selbst freischalten |
| `/strata codex grant <spieler> <knoten>` | OP | Knoten für Spieler freischalten (z. B. Quest-Belohnung) |
| `/strata codex validate` | OP | Kodex-Datapack auf Tippfehler und Zyklen prüfen |
| `/strata_epochs transplant <epoche> [radius]` | OP | Transplantation an der eigenen Position (ohne Voraussetzungen, zum Testen) |
| `/strata_epochs spiralit` | OP | Lässt einen Spiralit für sich schlüpfen |

## Noch offen

- Automatisch getestet ist der Server in allen Versionen. Der Client lief in 1.21.1, 1.21.11 und 26.3 (Mobs, Rüstung, Werkzeuge, siehe `docs/screenshots/clients/`). Unter 26.x startet er ohne Grafikkarte über Vulkan mit Mesa lavapipe (`apt-get install mesa-vulkan-drivers`); `scripts/client-screenshot.sh` stellt das selbst ein. 26.1 und 26.2 haben denselben Renderer-Code wie 1.21.11, ihr Client wurde nicht einzeln gestartet. Richtig gespielt (Bohren, Tor, Verfestigung von Hand) wurde noch nicht.
- Die Mob-Sounds sind eigene Ereignisse mit Untertiteln, klingen aber nach Vanilla (Gürteltier, Schleim, Schleifstein). Eigene Aufnahmen fehlen.
- Die Texturen sind von Hand gestaltete Pixel-Art aus `tools/make_textures.py` (von einem Prüf-Agenten in 4 Runden auf mindestens 7/10 pro Textur gebracht); ein menschlicher Pixel-Artist könnte sie weiter verfeinern.
- Welten mit Epochen zeigen beim ersten Laden die Vanilla-Warnung „experimentelle Einstellungen“. Das ist bei Datapack-Dimensionen normal; einmal bestätigen reicht.
- Phase 3 ist umgesetzt: Bohrer T3, Aschezeit, Fossilien, Spiralit, Transplantator (übersteht Neustarts), dazu der Kernanalyse-Tisch aus Phase 1. Im Client gesehen: Spiralit (1.21.1), Transplantation (1.21.1 und 26.3), siehe `docs/screenshots/phase3/`.
- Die Grenze „ein Spiralit pro Spieler“ zählt nur Spiralits in geladenen Gebieten.
- Weitere Biome pro Epoche und ein eigener Mob für die Aschezeit (bisher Vanilla-Magmawürfel und Schreiter).
- Bridge: eigenes EMI-Plugin (EMI gibt es nur für 1.21.1), KubeJS-Plugin mit eigenen Bindings, Curios-Slot für den Zeitanker.
