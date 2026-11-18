# Changelog

Alle drei Mods (Strata Core, Strata Epochs, Strata Bridge) tragen dieselbe Versionsnummer. Jedes Jar gibt es
für Minecraft 1.21.1, 1.21.11, 26.1, 26.2 und 26.3 (NeoForge); der Dateiname nennt die Version, z. B.
`strata-epochs-neoforge-0.1.0+1.21.1.jar`.

## 0.1.0 – erste Ausgabe

### Strata Core
- Kodex- und Progressions-Framework: Epochen und Kodex-Knoten kommen per Datapack (`strata_core/epoch`,
  `strata_core/codex_node`), mit Voraussetzungen und Fehlerprüfung beim Laden.
- Mob-Werte per Datapack (`strata_core/mob_tuning`): Attribute, Spawns und Fähigkeiten aller Strata-Mobs.
- API für Addons (`StrataAPI`): Kodex, Epochen, Epoche unter einem Chunk.

### Strata Epochs
- Kernbohrer (drei Stufen), Bohrkerne, Kernanalyse-Tisch (auch per Trichter und Rohr), Epochentor.
- Drei Höhlen-Epochen mit je drei Biomen: Kristallzeit, Sporenzeit, Aschezeit; Biom-Peilung am Tor.
- Verfestigung: Die Zeit in einer Epoche ist begrenzt; danach versteinern Blöcke und Gegenstände.
- Fossilien: Fossilbrocken abbauen, am Präparationstisch freilegen, Kodex-Knoten „gemeistert“.
- Transplantator: holt die Landschaft einer Epoche in die Oberwelt (übersteht Neustarts).
- Epochen-Pflanzen (Quarzdistel, Harzfarn, Glutknolle), Zeitrisse in der Oberwelt, Museum mit Vitrinen.
- Acht Kreaturen (Geodenkriecher, Sporenbalg, Steinzeuge, Glutgräber, Spiralit, Resonanzling, Bernsteinmotte,
  Harzschnecke) und der Stratolith als Boss mit drei Phasen (750 Leben, auf Endspiel-Ausrüstung abgestimmt); eigene Modelle, Texturen und Klänge.
- Ausrüstung: Kristallquarz-Werkzeuge, Geodenrüstung, Bernsteinklinge, Zeitanker, Herz der Tiefenzeit.

### Strata Bridge
- JEI-, EMI- und Jade-Anbindung, Curios-Slot für den Zeitanker, KubeJS-Bindings,
  ein Advancement je Kodex-Knoten für Quest-Mods. Alle Fremd-Mods sind optional.

### Geprüft
- Pro Version: Build, Serverstart mit dem ganzen Set, Selbsttest, Durchspieltest des Kern-Ablaufs,
  Format-Wächter für alle Datendateien (`scripts/smoke-test.sh`, auch in der CI).
- Balance-Messung über echte Spielzeit (`scripts/balance.sh`): Harzschnecke, Harzfalle, Stratolith-Kampf.
- Leistungsmessung (`scripts/perf.sh`): Strata-Mobs kosten etwa so viel wie Zombies, eine Transplantation unter 1 ms je Tick.
- Noch nicht von einem Menschen durchgespielt; offene Punkte stehen in der README unter „Noch offen“.
