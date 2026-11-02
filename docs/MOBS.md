# Strata – Custom Mobs

Ergebnis einer Runde mit Game Designer, 3D-Modellierer/Animator, Programmierer und Projektmanager.

## Stand

Die drei Start-Mobs sind als Blockbench-Modelle fertig (`mobs/<id>/`). Ein Designer-Agent hat sie in drei Runden anhand von Screenshots geprüft und freigegeben: Geodenkriecher 8/10, Sporenbalg 7/10, Steinzeuge 7,5/10. Die genauen Maße stehen in [`docs/BRIEFING.md`](BRIEFING.md), Abschnitt 8. Als Nächstes folgt die Grundlage im Code (M0).

**Nachprüfung am 02.11.2026** (nach der Anpassung an die Stilregeln: höchstens 15 Würfel, 64×64, feste Paletten), drei Prüfer:

| Mob | Designer | Game Design | Technik |
|---|---|---|---|
| Geodenkriecher | 7 | 7 | 7 |
| Sporenbalg | 7 | 6 | 7 |
| Steinzeuge | 6 | 6 | 6 |

Offen sind vor allem:
- **Lesbarkeit:** Alle drei Mobs verschwinden in den Farben ihrer Epoche.
- **Steinzeuge:** Er sieht dem Eisengolem zu ähnlich und ist von der Seite nur eine flache Säule.
- **Technische Fehler:** sind behoben.
  - Der Schwanz des Geodenkriechers dreht nicht mehr doppelt.
  - Beim Kratzen schweben die Hinterbeine nicht mehr.
  - Glas und Brauenleiste des Steinzeugen flackern nicht mehr (kein Z-Fighting).
  - Der Kern des Steinzeugen wird beim Erwachen nicht mehr negativ skaliert, und der Steinzeuge lehnt sich danach nicht mehr zurück.
  - Das Aufpumpen des Sporenbalgs ist auf 1,2 begrenzt.
  - Haltephasen sind linear.

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

- **Verhalten:** Er hüpft auf Spieler zu, pumpt sich auf und stößt eine grüne Sporenwolke aus. Die Wolke macht keinen Schaden, sondern beschleunigt die **Verfestigung** (Startwert ×2,5 für 5 Sekunden, siehe „Spezialfähigkeiten“).
- **Drop:** Sporenbeutel, Zutat für „Keimfrische“, die die Verfestigung kurz zurücksetzt (kommt später).
- **Thema:** Er setzt den Spieler direkt unter Zeitdruck. Das ist der „Zeitschwund“ aus dem Konzept, als echter Gegner umgesetzt.
- **Technik:** Laut Modellierer die einfachste Form überhaupt: ca. 5 Würfel (gebaut wurden 9, die Textur ist daher 64×64 statt 32×32), 4 Knochen, Squash & Stretch beim Hüpfen. Pump-Animation als Keyframes.

### 3. Steinzeuge (`petrified_witness`): beide Epochen, Gegner (danach)

Ein breiter, blockiger Steinkörper in Schichtoptik. Auf der Brust leuchtet ein goldener Sanduhr-Kern.

- **Verhalten:** Er entsteht aus versteinerten Blöcken bei Spielern, die zu lange bleiben, als Echo eines Reisenden. Er erwacht langsam und schlägt wie ein Golem zu.
- **Drop:** Kodex-Fragment (schaltet Erdkodex-Knoten frei), selten versteinerter Stein mit Ammonit.
- **Thema:** Er macht die Strafmechanik der Verfestigung sichtbar.
- **Technik:** 64×64, ca. 14 Würfel, Leucht-Ebene für den Sanduhr-Kern. Er ist die Vorlage für einen späteren Mini-Boss.
- **Warum erst als Dritter:** Er hängt an der Verfestigungs-Mechanik und braucht eine Leucht-Ebene und eine Aufwach-Animation. Das ist mehr Neues als bei 1 und 2.

## Spezialfähigkeiten

Jeder Mob hat 3–4 Fähigkeiten, die zu seiner Rolle im Spielablauf passen. Für alle gelten drei Regeln:

- **Jede Fähigkeit kündigt sich an.** Bevor sie wirkt, gibt es mindestens 0,5 s lang ein Signal: eine Pose, Leuchten oder einen Ton. Der Spieler kann darauf reagieren.
- **Jede Fähigkeit hat eine Antwort.** Es gibt einen Gegenzug, mit Ausrüstung oder mit Verhalten. Nichts wirkt einfach nur unfair.
- **Werte stehen im Datapack.** Schaden, Dauer, Abklingzeit, Radius und Stärke kommen aus `strata:mob_tuning` (siehe unten). Die Zahlen hier sind Startwerte.

Effekte nutzen nur Vanilla-Partikel und -Sounds. Das spart Assets und funktioniert in allen 5 Versionen. Wenn die Mod eigene Sounds bekommt, können sie später ersetzt werden.

### Geodenkriecher: 3 Fähigkeiten (neutral, hilft beim Erzfinden)

| Fähigkeit | Auslöser | Wirkung | Signal und Effekte | Gegenzug | Startwerte |
|---|---|---|---|---|---|
| **Erzwitterung** | passiv, alle 20–40 s | Sucht im Umkreis nach Kristallquarz-Erz. Bei einem Fund hebt er den Kopf, und eine Spur aus violetten Partikeln zieht 3 Blöcke weit in Richtung Erz. Danach läuft er hin. | Animation `sniff` (Kopf hoch, Schnauze zuckt). Partikel `dust` #b48cff in Erzrichtung, `electric_spark` an den Kristallspitzen. Sound `block.amethyst_block.chime`, Tonhöhe 1,4. | – (Hilfe für den Spieler) | Radius 12, Intervall 20–40 s |
| **Kratzfund** | kratzt 3 s an einem Erz | Lockert dabei einen **Kristallquarz-Splitter** (Item-Drop), ohne den Block abzubauen. Wer ihn mit Kristallquarz anlockt, bekommt ihn schneller. | Animation `scratch`. Partikel `block` des Erzes an der Pfote, am Ende ein `crit`-Funkenstoß. Sound `block.amethyst_cluster.hit` im Kratztakt. | – | Abklingzeit 120 s, angelockt 60 s |
| **Kristallpanzer** | wird angegriffen | Rollt sich 3 s ein. Er nimmt 60 % weniger Schaden, und wer ihn in dieser Zeit im Nahkampf trifft, bekommt 2 Schaden zurück (Splitter). Danach greift er wie gewohnt an. | 0,4 s Vorwarnung: Die Kristalle blitzen auf. Animation `curl` (Kopf und Schwanz eingezogen, Kristalle aufgestellt). Partikel `block` Calcit beim Einrollen, beim Rücktreffer `item` Kristallquarz. Sound `block.amethyst_block.resonate`. | Abwarten oder mit Bogen angreifen. Fernkampf löst keinen Rücktreffer aus. | Dauer 3 s, Schaden −60 %, Rücktreffer 2, Abklingzeit 20 s |

### Sporenbalg: 3 Fähigkeiten (Gegner, setzt unter Zeitdruck)

| Fähigkeit | Auslöser | Wirkung | Signal und Effekte | Gegenzug | Startwerte |
|---|---|---|---|---|---|
| **Sporenstoß** | Spieler ≤ 3 Blöcke | Pumpt sich auf und stößt eine Wolke aus (Area Effect Cloud). Wer darin steht, **verfestigt doppelt so schnell**. Kein Schaden. | 0,6 s Aufpumpen mit Zittern (Animation `pump`). Die Bernsteinpunkte leuchten auf. Partikel `spore_blossom_air` und `dust` #4f7a3a, Wolke als `entity_effect` in Grün. Sound `entity.puffer_fish.blow_up`, dann `block.fungus.break`. | Während des Aufpumpens zurückweichen oder ihn mit einem Treffer unterbrechen (Rückstoß bricht ab). **Keimfrische** setzt die Uhr zurück. Damit der Spieler die Strafe bemerkt, flackert die Verfestigungsanzeige im HUD grün, solange der Faktor wirkt. | Radius 2,5, Dauer 5 s, Faktor ×2,5, Abklingzeit 8 s |
| **Wurzelgriff** | landet nach einem Sprung ≤ 2 Blöcke neben einem Spieler | Myzelwurzeln greifen nach den Füßen: Langsamkeit II für 2 s. | Beim Absprung blitzen die Wurzelbeine hell auf. Bei der Landung ein Partikelring `block` aus Myzel und `falling_spore_blossom`. Sound `block.roots.break` und `block.mud.step`. | Hochspringen, im Moment der Landung nicht neben ihm stehen. Milch hebt den Effekt auf. | Langsamkeit II, 2 s, Abklingzeit 8 s |
| **Letzter Atemzug** | Tod | Er platzt und hinterlässt eine schwächere Sporenwolke. | Animation: kurzes, starkes Aufblähen, dann Vanilla-Tod. Partikel `spore_blossom_air` in großer Menge, `poof`. Sound `entity.slime.squish`, Tonhöhe 0,6. | Nach dem Todesstoß zurücktreten oder ihn auf Distanz töten. | Radius 2, Dauer 2 s, Faktor ×1,5 |

### Steinzeuge: 4 Fähigkeiten (Gegner, Vorlage für den Mini-Boss)

| Fähigkeit | Auslöser | Wirkung | Signal und Effekte | Gegenzug | Startwerte |
|---|---|---|---|---|---|
| **Erwachen aus Stein** | ein Spieler ist in der Verfestigungs-Stufe `SOLIDIFYING`, versteinerte Blöcke ≤ 8 Blöcke entfernt | Ein versteinerter Block wird zum Steinzeugen. Während des Erwachens (2,2 s) ist er unverwundbar. | Animation `awaken`. Der Block bekommt vorher 1 s lang Risse (Block-Break-Overlay). Partikel `block` versteinerter Stein, aus dem Kern `dust` #ffe08a. Sound `block.stone.break`, dann `block.respawn_anchor.charge`. | Die Epoche rechtzeitig verlassen oder einen **Zeitanker** setzen. Stabilisatoren verschieben die Stufe. | Höchstens 1 pro Spieler gleichzeitig, Abklingzeit 60 s |
| **Hammerschlag** | Nahkampf | Schlägt mit beiden Fäusten auf den Boden: 9 Schaden, hebt das Ziel leicht an. Im Umkreis von 2 Blöcken Langsamkeit I für 2 s. | Langes Ausholen (Animation `attack`, Arme über dem Kopf, 0,35 s). Partikel `block` vom Boden und `explosion` (klein). Sound `entity.iron_golem.attack`, dann `block.anvil.land`, leise. | Beim Ausholen zur Seite gehen. Schilde blocken den Schlag, aber nicht die Bodenwelle. | Schaden 9, Rückstoß 0,4 nach oben, Abklingzeit 2,5 s |
| **Sanduhr-Puls** | Spieler 3–8 Blöcke entfernt, alle 12 s | Der Kern sendet eine goldene Welle aus. Getroffene Spieler **verlieren 10 s erlaubte Zeit** (Verfestigung springt vor) und bekommen Abbaulähmung I für 4 s. | 1 s Vorwarnung: Der Kern leuchtet auf und vergrößert sich (Animation `pulse`). Partikel: Ring aus `dust` #ffe08a, der sich ausbreitet, dazu `end_rod` am Kern. Sound `block.bell.resonate`, Tonhöhe 0,5. | Nah bei ihm bleiben (innerhalb von 3 Blöcken wirkt der Puls nicht) oder hinter einen Block treten (die Welle braucht Sichtlinie). | Radius 8, −200 Ticks, Abbaulähmung I 4 s |
| **Steinhaut** | einmal, wenn er unter 50 % Leben fällt | Er verhärtet sich 4 s: 80 % weniger Schaden, regeneriert 4 Leben. Ein Treffer mit einer **Spitzhacke** auf den Kern bricht die Steinhaut sofort und betäubt ihn 1 s. | Risse in der Haut leuchten golden (Leucht-Ebene). Animation `harden` (Arme vor der Brust, Kopf eingezogen). Partikel `wax_on` und `block` Stein. Sound `block.deepslate.place` und `block.grindstone.use`. | Spitzhacke ziehen und den Kern treffen. Das ist der Kniff für den späteren Mini-Boss. | Dauer 4 s, Schaden −80 %, Heilung 4, einmal pro Leben |

### Was das für Modelle und Code bedeutet

- **Neue Animationen:**
  - Geodenkriecher: `sniff`, `curl`
  - Steinzeuge: `pulse`, `harden`
  - Sporenbalg: keine. Er nutzt `hop` und `pump`, das Aufblähen beim Tod ist eine kurze Skalierung.
  - Alle neuen Animationen werden über das Blockbench-MCP gebaut und gehen durch die Designer-Prüfung.
- **Leucht-Ebene:** Gebraucht beim Sporenbalg (Bernsteinpunkte) und beim Steinzeugen (Kern, Risse). Das gehört in den generischen Renderer aus M0.
- **Code:** Jede Fähigkeit ist ein eigenes Goal mit Vorwarnzeit, Wirkung und Abklingzeit. Ein gemeinsames `StrataAbility`-Gerüst in M0 hält den Aufwand pro Fähigkeit klein. Effekte laufen über Server-Partikel und Sounds, ohne eigene Pakete.
- **Verfestigung:** Sporenstoß und Sanduhr-Puls greifen in die Uhr ein (`SolidificationClock`). Sie brauchen dafür eine kleine API: einen Zeitfaktor pro Spieler und „Zeit abziehen“.

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

**Anforderungen an Abspieler und Modell-Builder** (aus der technischen Prüfung der drei Baupläne):
- **Alle drei Kanäle:** Neben Rotation auch Position und Skalierung. Hüpfen, Pumpen und Erwachen brauchen sie.
  - Position wird zur Ruheposition addiert, mit umgedrehtem y für den Minecraft-Raum.
  - Skalierung läuft über `ModelPart.xScale/yScale/zScale`.
- **Addieren statt setzen:** Wie Blockbench addiert der Abspieler die Animationswerte auf die Ruhepose. Also erst `resetPose()`, dann `+=`.
- **Euler-Winkel:** Komponentenweise interpolieren, Reihenfolge ZYX wie `rotationZYX`. Nicht in Quaternionen umrechnen und nicht auf ±180° normalisieren, denn der Steinzeuge holt mit 160° aus.
- **Catmull-Rom wie Blockbench:** Der Spline läuft über die Punkte (Zeit, Wert). Haltephasen in den Bauplänen auf `linear` setzen, sonst schwingen sie über.
- **Gedrehte Würfel:** Vanilla-Würfel haben keine eigene Rotation. Der Builder legt dafür ein Kind-Teil am Drehpunkt des Würfels an. Das betrifft die Kristalle des Geodenkriechers und die Wurzeln des Sporenbalgs.
- **Umrechnung prüfen:** Ein Mob wird einmal per Blockbench-Java-Export erzeugt. Der Selbsttest vergleicht ihn mit dem eigenen Builder, damit nichts seitenverkehrt ist.
- **Überblenden:** Laufen und Hüpfen beginnen nicht in der Ruhepose. Der Abspieler blendet sie über `limbSwingAmount` ein.

**Aufwand (Programmierer):**
- erster Mob 6–9 Personentage, davon rund 60 % einmalige Infrastruktur
- jeder weitere einfache Mob 1–2 Personentage
- Grafik zusätzlich 1–3 Personentage pro Mob

## Wann ist ein Mob fertig? (Definition of Done, Projektmanager)

1. **Modell:** über das Blockbench-MCP gebaut, als `.bbmodel` im Repo, Hitbox passend.
2. **Textur:** Prüf-Agent ≥ 7/10, auch im Dunkeln lesbar.
3. **Animationen:** Idle, Laufen, Angriff bzw. Interaktion, dazu eine Animation pro Fähigkeit mit eigener Pose. Treffer und Tod liefert Vanilla.
3a. **Fähigkeiten:** Alle Fähigkeiten aus „Spezialfähigkeiten“ wirken, haben Vorwarnung und Gegenzug und lassen sich im Datapack einstellen.
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
  - Fähigkeiten über `strata:mob_tuning` → `abilities`: pro Fähigkeit an/aus, Abklingzeit, Dauer, Radius, Stärke (z. B. Faktor der Sporenwolke, Zeitabzug des Sanduhr-Pulses)
- **Config:**
  - jeden Mob einzeln abschalten
  - Höchstzahl pro Epoche
  - Steinzeuge aus versteinerten Blöcken an/aus

## Meilensteine

| | Inhalt | Aufwand |
|---|---|---|
| **M0 Grundlagen** | Mob-Basis, generischer Renderer für 3 Versionsgruppen mit Leucht-Ebene, Modell- und Animations-Pipeline aus Blockbench, `StrataAbility`-Gerüst, Verfestigungs-API (Zeitfaktor, Zeit abziehen), Selbsttest, Tuning-JSON, Config | 1,5–2 PW |
| **M1 Geodenkriecher** | komplette Definition of Done auf 5 Versionen, mit Erzwitterung, Kratzfund, Kristallpanzer | 1,5–2 PW |
| **M2 Sporenbalg** | mit Sporenstoß, Wurzelgriff, Letztem Atemzug | 1–1,5 PW |
| **M3 Steinzeuge** | mit Erwachen aus Stein, Hammerschlag, Sanduhr-Puls, Steinhaut | 1,5–2 PW |

**Go/No-Go nach M1:** Braucht der Geodenkriecher mehr als 2 Personenwochen, wird zuerst die Pipeline verbessert, bevor ein weiterer Mob dazukommt.

**Bekannte Lücke:** Der Client lässt sich in der Cloud-Testumgebung nur für 1.21.1 starten. Für 26.x braucht der Screenshot-Test entweder eine bessere OpenGL-Umgebung oder einen Test auf einem echten PC, etwa bei dir mit der Claude-App und `scripts/client-screenshot.sh`.
