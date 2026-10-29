# Strata – Tiefenzeit

Ein Modset für Minecraft-Modpacks. Das Konzept steht in [KONZEPT.md](KONZEPT.md).

Dieses Repo enthält bisher **Strata Core**, das Fundament des Sets: Epochen und Kodex-Knoten per Datapack, Kodex-Fortschritt am Spieler, Prüfung der Datapacks beim Serverstart und `/strata`-Befehle. Die Inhalts-Mod (`strata-epochs`) und die Integrations-Mod (`strata-bridge`) folgen.

## Unterstützte Versionen

| Minecraft | NeoForge | Java | Status |
|---|---|---|---|
| 1.21.1 | 21.1.265 | 21 | baut, Server startet |
| 1.21.11 | 21.11.48 | 21 | baut, Server startet |
| 26.1.x | 26.1.2.124 | 25 | baut, Server startet |
| 26.2 | 26.2.0.93 | 25 | baut, Server startet |
| 26.3 | 26.3.0.99 | 25 | baut, Server startet |

Nicht jede 1.21-Zwischenversion (1.21.2 bis 1.21.10) wird unterstützt. Modpacks sammeln sich auf wenigen Versionen: 1.21.1 ist die große Modpack-Basis, 1.21.11 die letzte 1.21-Version. Jede weitere Version kostet Pflege und Tests bei jedem Release. Eine Zwischenversion lässt sich bei konkreter Nachfrage mit wenig Aufwand ergänzen (siehe unten).

## Aufbau

```
common/                 Reine Spiellogik ohne Minecraft (Epochenwahl, Kodex-Graph, Verfestigung) + JUnit-Tests
core/src/shared/        NeoForge-Code, der in allen Versionen gleich ist
core/src/mc_1_21_1/     Anbindung an Minecraft 1.21.1
core/src/mc_1_21_11_plus/  Anbindung ab 1.21.11 (ResourceLocation heißt hier Identifier)
core/src/dpreg_event/   Datapack-Registries anmelden, bis 26.2
core/src/dpreg_builder/ Datapack-Registries anmelden, ab 26.3
core/versions/<v>/      Ein Gradle-Projekt pro Minecraft-Version; gradle.properties wählt die Quellordner
example-datapacks/      Demo-Datapack (2 Epochen, 3 Kodex-Knoten)
scripts/smoke-test.sh   Startet pro Version einen Server und prüft, ob alles lädt
```

Unterschiede zwischen Minecraft-Versionen stecken nur in den kleinen Klassen `Mc` und `McDatapack`. Der restliche Code ist für alle Versionen derselbe.

## Bauen und testen

Gradle braucht zum Starten Java 17 oder neuer. Fehlende JDKs (21 für 1.21.x, 25 für 26.x) lädt Gradle automatisch herunter.

```bash
./gradlew :common:test                 # Spiellogik testen
./gradlew build                        # alle Versionen bauen
./gradlew :core-26.3:build             # nur eine Version
scripts/smoke-test.sh                  # Server-Start in allen Versionen prüfen
scripts/smoke-test.sh 1.21.1 26.3      # nur bestimmte Versionen
```

Die fertigen Jars liegen unter `core/versions/<v>/build/libs/`.

## Neue Minecraft-Version hinzufügen

1. Ordner `core/versions/<v>/` mit einer `gradle.properties` anlegen (Vorlage: eine bestehende Version). Darin die NeoForge-Version, die Java-Version und die Quellordner (`src_groups`) eintragen.
2. `./gradlew :core-<v>:compileJava` ausführen.
3. Meldet der Compiler Fehler, einen neuen Quellordner unter `core/src/` mit einer angepassten `Mc`- bzw. `McDatapack`-Klasse anlegen und in `src_groups` eintragen.
4. `scripts/smoke-test.sh <v>` ausführen und die Version in die CI-Matrix (`.github/workflows/build.yml`) aufnehmen.

## Datapack-Format

```
data/<namespace>/strata_core/epoch/<name>.json
{ "weight": 3, "solidify_seconds": 600, "dimension": "strata_epochs:crystal_age" }

data/<namespace>/strata_core/codex_node/<name>.json
{ "requires": ["<namespace>:first_core"] }
```

- `weight`: Häufigkeit der Epoche unter der Oberwelt (0 = nie).
- `solidify_seconds`: Zeit bis zur Verfestigung (0 = aus, Standard 600).
- `dimension`: optional.

## Befehle

| Befehl | Wer | Zweck |
|---|---|---|
| `/strata epoch` | alle | Welche Epoche liegt unter mir? |
| `/strata codex list` | alle | Eigener Kodex-Fortschritt und nächste Schritte |
| `/strata codex unlock <knoten>` | OP | Knoten freischalten |
| `/strata codex validate` | OP | Kodex-Datapack auf Tippfehler und Zyklen prüfen |
