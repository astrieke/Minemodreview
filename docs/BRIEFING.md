# Briefing: Strata – Tiefenzeit

> **Wofür diese Datei ist:** Sie ist der Einstiegs-Prompt für Claude (oder einen Menschen), der an diesem Repo weiterarbeitet. Am besten in eine neue Sitzung kopieren oder mit „Lies docs/BRIEFING.md“ starten. Sie beschreibt das Projekt, den Aufbau des Repos, die Regeln und die geplanten Mobs bis auf den Würfel genau.

---

## 1. Das Projekt in drei Sätzen

**Strata – Tiefenzeit** ist ein Set aus drei Minecraft-Mods für Modpacks (NeoForge). Spieler ziehen mit einem **Kernbohrer** Bohrkerne aus dem Boden. Jeder Kern zeigt eine Urzeit-**Epoche**, in die man über ein **Epochentor** reisen kann. Bleibt man zu lange, **verfestigt** sich die Zeit: Man wird langsamer, und um einen herum versteinern Blöcke.

Das ausführliche Konzept steht in [`KONZEPT.md`](../KONZEPT.md), der Mob-Plan in [`docs/MOBS.md`](MOBS.md).

## 2. Die drei Mods

| Mod | ID | Inhalt |
|---|---|---|
| **Strata Core** | `strata_core` | Datapack-Registries für Epochen und Kodex-Knoten, Kodex-Fortschritt am Spieler, `/strata`-Befehle, öffentliche API (`StrataAPI`, `CodexNodeUnlockedEvent`) |
| **Strata Epochs** | `strata_epochs` | Kernbohrer, Bohrkern, Epochentor, Zeitanker, Verfestigung, Dimensionen **Kristallzeit** und **Sporenzeit**, Blöcke und Erze |
| **Strata Bridge** | `strata_bridge` | JEI-Infoseiten, Jade-Hinweise, Advancements je Kodex-Knoten (für FTB Quests & Co.), alle Fremd-Mods optional |

**Unterstützte Versionen:** Minecraft 1.21.1, 1.21.11, 26.1, 26.2, 26.3, jeweils mit NeoForge. 26.x braucht Java 25, 1.21.x Java 21.

## 3. Die Welt (Stil und Stimmung)

| | Kristallzeit | Sporenzeit |
|---|---|---|
| Nebel/Himmel | Violett `#6e56b8` / `#2a1f4d` | Grün `#4f7a3a` / `#1f3318` |
| Boden | Calcit über Basalt | Myzel über Erde |
| Grundgestein | Zeitgestein (violettgrau, mit Schichten) | Zeitgestein |
| Erz | Kristallquarz (blauviolett) | Bernstein (mit eingeschlossenen Insekten) |
| Vorlage-Biom | Tropfsteinhöhlen | Üppige Höhlen |

**Mod-Farben:** Gold `#d9a43a` / `#ffe08a` und Violett `#7a4fd6` / `#b48cff`.
**Zeit-Motive:** Sanduhr, Gesteinsschichten (Strata), Ammonit.

**Stilregeln für Pixel-Art und Modelle:**
- Vanilla-Stil: kleine Paletten (4–6 Töne pro Material), Licht von oben links, keine weichen Verläufe.
- Blöcke kacheln nahtlos. Items haben einen dunklen, farbigen Umriss.
- Mobs werden nur aus Würfeln gebaut (Box-UV), mit 5–15 Würfeln und 4–12 Knochen. Normale Mobs haben eine 64×64-Textur, kleine 64×32 oder 32×32.
- Jede Textur wird von einem Prüf-Agenten mit **mindestens 7/10** freigegeben, bevor sie ins Spiel kommt.

## 4. Aufbau des Repos

```
KONZEPT.md                 Konzept des ganzen Sets
docs/BRIEFING.md           diese Datei
docs/MOBS.md               Mob-Plan (Team-Abstimmung, Architektur, Meilensteine)
versions/<v>/              MC-, NeoForge- und Java-Version + Quellgruppen pro Mod
common/                    reine Spiellogik ohne Minecraft (Epochenwahl, Kodex-Graph, Verfestigung) + JUnit
core/src/shared/           Strata Core, in allen Versionen gleich
core/src/mc_*/, dpreg_*/   Versionsanbindung (Klassen Mc, McDatapack)
epochs/src/shared/         Strata Epochs, in allen Versionen gleich (Code, Assets, Daten)
epochs/src/reg_*/, break_*/  Versionsanbindung (Reg, BreakHook)
epochs/worldgen/           Epochen-Spezifikation und Rezepte (Quelle für das Worldgen)
epochs/worldgen.gradle     erzeugt Worldgen, Loot und Rezepte pro Version aus Vanilla-Vorlagen
bridge/src/                Strata Bridge (JEI- und Jade-Plugins pro Versionsgruppe)
mobs/<id>/                 Mob-Baupläne (model.json), Textur, .bbmodel, Screenshots, Prüfblatt
tools/make_textures.py     Block- und Item-Texturen
tools/make_mob_textures.py Mob-Texturen aus den Bauplänen
tools/mob_sheet.py         Prüfblatt aus den Mob-Screenshots
tools/blockbench-mcp/      MCP-Server + Blockbench-Plugin (Claude baut Modelle in Blockbench)
scripts/smoke-test.sh      Server + Selbsttest in allen Versionen
scripts/client-screenshot.sh  Client-Screenshot in einer Epoche
```

**Prinzip:** Versionsunterschiede stecken nur in kleinen Anbindungsklassen. Alles, was Mojang zwischen Versionen umformatiert (Worldgen, Loot, Rezepte, Advancements), wird beim Bauen aus den Vanilla-Dateien genau dieser Version abgeleitet.

## 5. Bauen und testen

```bash
./gradlew :common:test                  # Spiellogik
./gradlew build                         # alle 3 Mods × 5 Versionen
scripts/smoke-test.sh                   # Server-Start + Selbsttests in allen Versionen
./gradlew :bridge-1.21.1:runClient      # Spiel mit dem ganzen Set + JEI + Jade
```

## 6. Arbeitsweise (Regeln für Claude)

1. **Nichts behaupten, was nicht geprüft ist.** Jede Änderung in allen 5 Versionen kompilieren und den Smoke-Test laufen lassen. Ungetestetes klar als ungetestet benennen.
2. **Bilder prüfen lassen.** Texturen und Modelle gehen an einen Prüf-Agenten (Designer). Freigabe erst bei mindestens 7/10, Kritik konkret umsetzen.
3. **Konzept vor Code.** Neue Inhalte zuerst kurz mit Rollen besprechen (Game Design, Programmierung, Projektmanagement), dann umsetzen.
4. **Pack-Autoren mitdenken.** Spielbalance gehört ins Datapack, technische Schalter in die Config.
5. **Kleine Schritte, jeder Schritt committet und gepusht.**

## 7. Mobs bauen mit Blockbench

**Werkzeuge:** Das Blockbench-MCP (`tools/blockbench-mcp/`) gibt Claude die Werkzeuge `new_project`, `build_model`, `set_texture`, `add_animation`, `screenshot`, `export` und weitere. Einrichtung steht in `tools/blockbench-mcp/README.md`.

**Ablauf für einen Mob:**
1. Bauplan in `mobs/<id>/model.json` schreiben: Knochen, Würfel mit Box-UV-Startpunkt und Material, Animationen.
2. `python3 tools/make_mob_textures.py <id>` malt die Textur passend zum UV-Layout.
3. In Blockbench bauen:
   - mit der Claude-App per MCP-Werkzeugen, oder
   - automatisch mit `node tools/blockbench-mcp/build_mobs.js <id>` (startet Blockbench headless, speichert `.bbmodel` und Screenshots).
4. `python3 tools/mob_sheet.py <id>` erzeugt das Prüfblatt `mobs/<id>/review_sheet.png`.
5. Ein **Designer-Agent prüft** das Prüfblatt mit Noten für Silhouette, Proportionen, Textur, Animation und Thema. Nachbessern, bis jeder Mob **mindestens 7/10** hat.

**Konventionen:**
- Format `modded_entity`, Box-UV.
- 16 Einheiten = 1 Block, y nach oben, Füße bei y = 0.
- Der Mob schaut nach **−z** (Norden).
- Drehpunkte (`origin`) liegen am Gelenk.
- Knochennamen in `snake_case`, Animationen heißen `animation.<mob>.<name>`.

## 8. Die Mobs

*(Wird nach der Design-Prüfung mit den endgültigen Werten gefüllt.)*
