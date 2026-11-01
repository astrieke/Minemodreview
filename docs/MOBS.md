# Strata – Custom Mobs

Ergebnis einer Runde mit Game Designer, 3D-Modellierer/Animator, Programmierer und Projektmanager.

## Stand

Die drei Start-Mobs sind als Blockbench-Modelle fertig (`mobs/<id>/`). Ein Designer-Agent hat sie in drei Runden anhand von Screenshots geprüft und freigegeben: Geodenkriecher 8/10, Sporenbalg 7/10, Steinzeuge 7,5/10. Die genauen Maße stehen in [`docs/BRIEFING.md`](BRIEFING.md), Abschnitt 8. Als Nächstes folgt die Grundlage im Code (M0).

## Entscheidung in Kürze

- **Erst 2 Mobs, dann 1 weiterer, danach Phase 3** (Fossilien). Mehr auf einmal würde die Client-Arbeit für 5 Versionen und die Textur-Prüfrunden verstopfen.
- **Nur Bodenmobs am Anfang.** Fliegen, Schweben in der Luft, Schwärme und Bosse sind technisch die teuersten Fälle. Sie kommen erst, wenn die Pipeline steht.
- **Jeder Mob bedient genau einen Schritt im Spielablauf:** Erz finden → abbauen → unter Zeitdruck geraten → Kodex-Fortschritt.
- **Keine Fremd-Bibliothek** wie GeckoLib, weil unklar ist, ob es sie für 26.x gibt. Modelle und Animationen kommen aus Blockbench, über das eigene Blockbench-MCP.

## Das Start-Set

### 1. Geodenkriecher (`geode_crawler`): Kristallzeit, neutral, Pilot-Mob

Eine träge Urzeitechse, vierbeinig, mit Rückenplatten aus Calcit und blauvioletten Kristallspitzen in Schichtoptik.

- **Verhalten:** Sie wandert langsam zu Kristallquarz-Erz in der Nähe und kratzt daran. Wer ihr folgt, findet Erz. Lockbar mit Kristallquarz. Greift nur an, wenn sie angegriffen wird.
- **Drop:** Calcitschuppe (Material für den Bohrkopf, Stufe 2).
- **Thema:** Sie unterstützt das Erz-Gameplay hinter den Bohrkernen.
- **Warum zuerst:** Eine Vierbeiner-Form gilt beim Modellierer als „leicht“. Der Programmierer empfiehlt ausdrücklich einen friedlichen Laufmob mit Vanilla-Goals, um die ganze Kette einmal sauber durch alle 5 Versionen zu bauen.
- **Technik:** 64×64-Textur, ca. 12 Würfel, 8 Knochen. Laufzyklus und Schwanzwelle prozedural, Kratz-Animation als Keyframes.

### 2. Sporenbalg (`spore_bellows`): Sporenzeit, Gegner

Ein pilziger Sack auf kurzen Wurzelbeinen, mit Bernsteinpunkten auf dem Hut.

- **Verhalten:** Er hüpft auf Spieler zu, pumpt sich auf und stößt eine grüne Sporenwolke aus. Die Wolke macht keinen Schaden, sondern verdoppelt 3 Sekunden lang die **Verfestigung**.
- **Drop:** Sporenbeutel, Zutat für „Keimfrische“, die die Verfestigung kurz zurücksetzt (kommt später).
- **Thema:** Er setzt den Spieler direkt unter Zeitdruck. Das ist der „Zeitschwund“ aus dem Konzept, als echter Gegner umgesetzt.
- **Technik:** Laut Modellierer die einfachste Form überhaupt: 32×32, ca. 5 Würfel, 4 Knochen, Squash & Stretch beim Hüpfen. Pump-Animation als Keyframes.

### 3. Steinzeuge (`petrified_witness`): beide Epochen, Gegner (danach)

Ein breiter, blockiger Steinkörper in Schichtoptik. Auf der Brust leuchtet ein goldener Sanduhr-Kern.

- **Verhalten:** Er entsteht aus versteinerten Blöcken bei Spielern, die zu lange bleiben, als Echo eines Reisenden. Er erwacht langsam und schlägt wie ein Golem zu.
- **Drop:** Kodex-Fragment (schaltet Erdkodex-Knoten frei), selten versteinerter Stein mit Ammonit.
- **Thema:** Er macht die Strafmechanik der Verfestigung sichtbar.
- **Technik:** 64×64, ca. 14 Würfel, Leucht-Ebene für den Sanduhr-Kern. Er ist die Vorlage für einen späteren Mini-Boss.
- **Warum erst als Dritter:** Er hängt an der Verfestigungs-Mechanik und braucht eine Leucht-Ebene und eine Aufwach-Animation. Das ist mehr Neues als bei 1 und 2.

## Später (gesammelt, nicht eingeplant)

| Mob | Epoche | Rolle | Warum später |
|---|---|---|---|
| Bernsteinmotte (`amber_moth`) | Sporenzeit | passiv; man fängt sie, und sie reift zu Bernstein mit Einschluss (Fossil-Quelle) | Fliegen ist am teuersten, und das Insektenmodell ist schwer |
| Resonanzling (`resonant_shard`) | Kristallzeit | neutral; lässt Erz in der Nähe aufleuchten und pausiert kurz die Verfestigung | schwebt, braucht also Flug-Navigation |
| Harzschnecke (`resin_snail`) | Sporenzeit | passiv; ihre Spur härtet zu Bernsteinplatten aus | gut, aber ohne Bezug zum Kern-Ablauf |
| Spiralit (`ammonite_familiar`) | Begleiter | präpariertes Ammonit-Fossil; zeigt als Uhr die verbleibende Zeit an | gehört zu Phase 3 (Fossil-Präparation), Begleiter mit Zähmung ist teuer |
| **Stratolith** (`stratolith`) | Arena | Mini-Boss mit drei Gesteinsschichten als Phasen (Kristall, Sporen, Grundgestein mit „Zurückspulen“); Belohnung: Herz der Tiefenzeit für den Transplantator | ein Boss kostet laut Programmierer 5–10 Personentage |

## Technische Architektur

Damit der zweite und jeder weitere Mob billig werden, nach Vorschlag des Programmierers:

- **`StrataMobDef`:** beschreibt einen Mob als Daten: Hitbox, Kategorie, Attribute, Sounds, Textur, Modell, Animationen, Farben des Spawn-Eis.
- **Gemeinsame Basisklasse `StrataMob`** mit einer eigenen, versionsfreien Animations-Uhr.
- **Ein generischer Renderer pro Versionsgruppe:** 1.21.1 ohne Render States, ab 1.21.2 mit Render States, 26.x mit eigener Variante. Neue Mobs brauchen keinen eigenen Renderer.
- **Modelle als neutrale Daten:** Das Blockbench-MCP exportiert Knochen, Würfel und Pivots als JSON. Zur Laufzeit wird daraus die `LayerDefinition` gebaut, deren Bausteine seit 1.17 stabil sind. Den Java-Export von Blockbench (Vorlage für 1.17) nutzen wir nicht direkt.
- **Eigener kleiner Keyframe-Abspieler:** spielt Blockbench-Animationen ab und setzt die Rotationen der Modellteile direkt. Das umgeht die Vanilla-Animations-API, die sich mehrfach geändert hat. Laufen und Atmen laufen prozedural per Sinus.
- **Spawns per Datapack:** NeoForge-Biome-Modifier für die Epochen-Biome, Spawnregeln als gemeinsame Prädikate.

**Aufwand (Programmierer):**
- erster Mob 6–9 Personentage, davon rund 60 % einmalige Infrastruktur
- jeder weitere einfache Mob 1–2 Personentage
- Grafik zusätzlich 1–3 Personentage pro Mob

## Wann ist ein Mob fertig? (Definition of Done, Projektmanager)

1. **Modell:** über das Blockbench-MCP gebaut, als `.bbmodel` im Repo, Hitbox passend.
2. **Textur:** Prüf-Agent ≥ 7/10, auch im Dunkeln lesbar.
3. **Animationen:** Idle, Laufen, Angriff bzw. Interaktion. Treffer und Tod liefert Vanilla.
4. **Sounds:** ambient, hurt, death, mit Untertiteln.
5. **Spawn:** nur in der eigenen Epoche, mit Licht- und Anzahlgrenze.
6. **Drops:** als Loot-Table, mit mindestens einem Drop, der irgendwo gebraucht wird.
7. **Konfigurierbar** (siehe unten).
8. **JEI/Jade:** Spawn-Ei und Drops in JEI, Name und Epoche in Jade, ein Advancement „Begegnung“.
9. **Tests:**
   - Server-Selbsttest: Registrierung, Attribute, Modell lässt sich backen, alle Knochen der Animationen existieren, Loot, Spawn in der Epoche.
   - Client-Screenshot pro Version.
10. **Alle 5 Versionen** bauen und sind grün.

## Was Pack-Autoren einstellen können

- **Datapack:**
  - Spawn-Gewicht, Gruppengröße und Biome (Biome-Modifier, auch in fremden Dimensionen)
  - Drops (Loot-Tables)
  - Stärke über `strata:mob_tuning` (Leben, Schaden, Rüstung, Tempo)
  - Stärke der Sporenwolke
- **Config:**
  - jeden Mob einzeln abschalten
  - Höchstzahl pro Epoche
  - Steinzeuge aus versteinerten Blöcken an/aus

## Meilensteine

| | Inhalt | Aufwand |
|---|---|---|
| **M0 Grundlagen** | Mob-Basis, generischer Renderer für 3 Versionsgruppen, Modell- und Animations-Pipeline aus Blockbench, Selbsttest, Tuning-JSON, Config | 1–1,5 PW |
| **M1 Geodenkriecher** | komplette Definition of Done auf 5 Versionen | 1–1,5 PW |
| **M2 Sporenbalg** | inklusive Sporenwolke und Verfestigungs-Verdopplung | 1 PW |
| **M3 Steinzeuge** | inklusive Entstehung aus versteinerten Blöcken und Leucht-Ebene | 1–1,5 PW |

**Go/No-Go nach M1:** Braucht der Geodenkriecher mehr als 2 Personenwochen, wird zuerst die Pipeline verbessert, bevor ein weiterer Mob dazukommt.

**Bekannte Lücke:** Der Client lässt sich in der Cloud-Testumgebung nur für 1.21.1 starten. Für 26.x braucht der Screenshot-Test entweder eine bessere OpenGL-Umgebung oder einen Test auf einem echten PC, etwa bei dir mit der Claude-App und `scripts/client-screenshot.sh`.
