# Strata – Custom Mobs

Ergebnis einer Runde mit Game Designer, 3D-Modellierer/Animator, Programmierer und Projektmanager.

## Stand

Die drei Start-Mobs sind als Blockbench-Modelle fertig (`mobs/<id>/`). Die früheren Noten aus einer einzelnen Designer-Prüfung (8 / 7 / 7,5) sind überholt. Es gilt die Nachprüfung durch drei Prüfer unten. Danach ist nur der Geodenkriecher freigegeben. Die genauen Maße stehen in [`docs/BRIEFING.md`](BRIEFING.md), Abschnitt 8.

**Sporenbalg freigegeben (05.11.2026, 2. Runde):** Design 8, Game Design 7, Technik 8. Behoben wurden die Lücke zwischen Hut und Sack beim Pumpen (Sack-Drehpunkt unten, neuer Stiel), die gekreuzten Wurzeln beim Hüpfen und der schwache Rand vor dem Nebel. Optional für später: oberste Randzeile auf `#f6b98a`, Stauchung beim Hüpfen erst ab 0,5 s, Ventilrand auch seitlich auf der Leucht-Ebene.

**Steinzeuge freigegeben (05.11.2026, nach Umbau zum „wandelnden Felsvorsprung“):** Runde 1 6/6/6, Runde 2 7/7/7. Geändert in Runde 2: Schichtplatten verankert (Überhang statt Hut), Hammerschlag mit 0,5 s Ausholen und Bodenkontakt, dunkle Adern, die nur bei der Steinhaut leuchten, Geräusche aus Stein statt Eisengolem. Optional für später: Oberseite der oberen Platte im Schieferton statt Ocker (wirkt sonst wie Holz), Hitbox 1,5 × 2,3 statt 1,3 × 2,4.

**Glutwühler freigegeben (06.11.2026, Gegner der Aschezeit, `strata_epochs:ember_burrower`):**
- Ergebnisse: Runde 1 5/6/6, Runde 2 7/6/6, Runde 3 8/7/8.
- Umfang: 10 Knochen, 14 Würfel, 7 Animationen. Hitbox 0,9 × 0,8, abgetaucht 0,9 × 0,55, beim Grollen 0,9 × 0,75.
- Die Flosse ragt abgetaucht 8 px aus der Asche, beim Grollen 11 px. Der Kamm flackert, ein Partikelring zeigt den echten Wirkradius (2 Blöcke).
- Fester Boden schützt (die Bodensäule unter dem Ziel zählt, Springen hilft nicht). Er bleibt höchstens 10 s abgetaucht.
- Er bricht mit dem Rücken zum Spieler hervor und steckt 1,5 s fest; Treffer auf den Glutkamm von hinten +50 %.
- Zustand und Aktion werden nicht gespeichert: Nach dem Neuladen steht er an der Oberfläche (harmlos, bewusst so).
- Im Client gesehen in 1.21.1 und 26.3. Im Spiel beobachten: ob zwei Glutwühler zusammen zu hart sind (Wurf auf Wurf).

**Spiralit freigegeben (05.11.2026, Begleiter aus Phase 3, `strata_epochs:spiralit`):**
- Ergebnisse: Runde 1 6/5/6, Runde 2 7/6/6, Runde 3 8/7/7.
- Die Uhr hat jetzt 4 Leucht-Segmente, die von außen nach innen erlöschen; die Spirale ist dunkel und leuchtet hell, damit man sie auch bei Tag liest.
- Er schwebt rechts vorn auf Schulterhöhe, damit er am Bildrand bleibt.
- Schlüpfen: Er bricht aus einer Steinhülle in den Farben des Ammonit-Items und bleibt dabei über dem Tisch.
- Beim Zeigen schwebt er neben statt auf der Sichtlinie zum Erz.
- Erzsuche nur mit Epochen-Erzen (eigener Tag), nur in Epochen, alle 15 s.
- Die Bremse stapelt nicht mit der Geodenrüstung. Es gibt höchstens einen gebundenen Spiralit pro Spieler: Der Spieler merkt sich die UUID seines aktuellen Spiralits (Attachment `spiralit_id`, übersteht Tod und Neustart); ein anderer Spiralit mit seinem Namen löst sich beim nächsten Laden und wird herrenlos.
- Optional für später: Rippen auf der Oberseite quer zur Windung, Aschezeit-Hintergrund im Prüfblatt.

**Im Code (05.11.2026):** M0 steht. Geodenkriecher (M1), Sporenbalg (M2) und Steinzeuge (M3) laufen in allen 5 Versionen, Reste siehe unten. **Gate B ist erreicht:** Im Client-Screenshot unter 1.21.1 sieht er aus wie in Blockbench, und die Kristalle leuchten auch im Dunkeln (`mobs/geode_crawler/shots/ingame_1.21.1.png`, `ingame_dark_1.21.1.png`).

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

## Umsetzung im Code

Alles liegt in Strata Epochs (`epochs/src/`). Der Mob-Code ist bis auf wenige Adapter für alle Versionen gleich.

| Teil | Wo | Was |
|---|---|---|
| Modell-Daten | `shared/…/mob/MobModelData` | liest `mobs/<id>/model.json` (wird beim Bauen ins Jar kopiert) |
| Abspieler | `shared/…/mob/MobAnimation` | Blockbench-Regeln: additiv, Catmull-Rom/linear/step, Euler komponentenweise |
| Mob-Basis | `shared/…/mob/StrataMob`, `StrataMobDef` | Aktion und Leuchtstärke als synchronisierte Zahl, Datapack-Werte, Advancement „Begegnung“ |
| Fähigkeiten | `shared/…/mob/StrataAbility` | Vorwarnung → Wirkung → Abklingzeit, Werte aus dem Datapack |
| Geodenkriecher | `shared/…/mob/GeodeCrawler` | Erzwitterung, Kratzfund, Kristallpanzer, anlockbar mit Kristallquarz |
| Steinzeuge | `shared/…/mob/PetrifiedWitness`, `WitnessAwakening` | Erwachen aus versteinertem Stein (bei Spielern in SOLIDIFYING), Hammerschlag mit Bodenwelle, Sanduhr-Puls (−10 s), Steinhaut (Spitzhacke auf den Kern bricht sie); Drop Kodex-Fragment → Knoten „Echo eines Reisenden“ |
| Sporenbalg | `shared/…/mob/SporeBellows`, `SporeClouds` | hüpft, Sporenstoß (Wolke: Verfestigung ×2,5), Wurzelgriff, Letzter Atemzug; Drop Sporenbeutel → Keimfrische (`GermTonic`, dreht die Uhr 60 s zurück) |
| Modell-Builder, Pose | `shared/…/client/MobModelBuilder`, `MobPoser` | Blockbench → `LayerDefinition`; Idle + Laufen + Aktion + Kopfdrehung |
| Renderer | `client_1_21_1`, `client_states` + `glow_crumbling`/`glow_uvmap` | 1.21.1 ohne Render States; ab 1.21.11 mit Render States, Leucht-Ebene je nach Version |
| Registrierung | `reg_*/…/MobReg`, `MobCompat` | Entity-Typ, Spawn-Ei, Sounds, Schaden, Partikel, Grundattribute |
| Datapack-Werte | `data/strata_epochs/strata_core/mob_tuning/geode_crawler.json` | Registry `strata_core:mob_tuning` (in Strata Core) |
| Spawns | `epochs/worldgen/epochs.json` → `mobs` | Biome-Modifier `neoforge:add_spawns`, beim Bauen im Format der Version erzeugt |
| Config | `EpochsConfig` | Mob an/aus, Höchstzahl pro Epoche |
| Verfestigungs-API | `Solidification.speedUp`, `advance` | Zeitfaktor pro Spieler, Zeit abziehen (für Sporenbalg und Steinzeuge) |

**Neuen Mob anlegen:** Dateien in `mobs/<id>/`, den Namen in `epochs.json` → `mobs` eintragen (Kopie ins Jar und Spawns), eine Entity-Klasse mit `StrataMobDef`, Registrierung in `EpochsMobs`, Renderer in beiden `MobClient`-Klassen, Datapack-Werte, Loot, Sprache, Sounds und einen Eintrag im Selbsttest.

**Im Spiel geprüft (1.21.1, Bildserien mit `SHOT_ACT=1`):**
- Geodenkriecher: Spur zum Erz, Hinlaufen, Kratzen mit Partikeln, Kristallquarz fällt, nach einem Treffer Einrollen und Aufstehen (`mobs/geode_crawler/shots/ingame_actions_1.21.1.png`).
- Steinzeuge: läuft heran, Sanduhr-Puls als goldener Ring, Hammerschlag mit Ausholen über dem Kopf (`mobs/petrified_witness/shots/ingame_attack_1.21.1.png`). Erwachen aus Stein mit `SHOT_AWAKEN`: Nach 20 s Verfestigung versteinert der Boden, ein Steinzeuge erwacht (Advancement kommt) und greift an.
- Sporenbalg: hüpft auf den Spieler zu, Sporenwolke mit grüner Statusmeldung, Advancement „Begegnung“ (`mobs/spore_bellows/shots/ingame_cloud_1.21.1.png`).

**Geodenkriecher, offen für die Definition of Done:**
- Sounds: eigene, synthetisch erzeugte Klänge (siehe „Mob-Sounds“ unten).
- Die Calcitschuppe wird gebraucht: Zweitrezept für den Zeitanker, Geodenrüstung und als Exponat im Museum der Erdgeschichte. Der früher geplante „Bohrkopf Stufe 2“ ist im Kristall-Kernbohrer aufgegangen (Kernbohrer + 4 Kristallquarz); die Schuppe bleibt bewusst optional, damit die Bohrer-Stufen auch mit abgeschalteten Mobs erreichbar sind.

## Entscheidung in Kürze

- **Erst 2 Mobs, dann 1 weiterer, danach Phase 3** (Fossilien). Mehr auf einmal würde die Client-Arbeit für 5 Versionen und die Textur-Prüfrunden verstopfen.
- **Nur Bodenmobs am Anfang.** Fliegen, Schweben in der Luft, Schwärme und Bosse sind technisch die teuersten Fälle. Sie kommen erst, wenn die Pipeline steht.
- **Jeder Mob bedient genau einen Schritt im Spielablauf:** Erz finden → abbauen → unter Zeitdruck geraten → Kodex-Fortschritt.
- **Keine Fremd-Bibliothek** wie GeckoLib, weil unklar ist, ob es sie für 26.x gibt. Modelle und Animationen kommen aus Blockbench, über das eigene Blockbench-MCP.

## Entscheidungen des Projektmanagers (03.11.2026)

Der Projektmanager trifft die offenen Entscheidungen. Kurzfassung:

- **Freigabe:** Jeder der drei Prüfer (Design, Game Design, Technik) gibt mindestens 7. Es zählt die niedrigste Note, nicht der Durchschnitt.
- **Steinzeuge wird jetzt umgebaut, vor M0.** Ziel ist ein „wandelnder Felsvorsprung“ statt eines Golems:
  - Höhe: Kopf bis y 31, Schichtplatten bis 38 px.
  - Torso [−8, 10, −5] bis [8, 26, 5], Beine y 0–10.
  - Kopf vorgezogen: [−4, 24, −10] bis [4, 31, −3].
  - lange Arme
  - Sanduhr als ein Würfel 5×7×1 mit gemalter Taille
  - 2 gedrehte Schichtplatten am neuen Knochen `back_plates`
  - Ammonit auf dem unteren Rücken
  - insgesamt 15 Würfel und 11 Knochen, ohne Mantelsaum
  - Höchstens 2 Prüfrunden, sonst wird er geparkt. Dann blockiert er nur M3.
- **Farben für Lesbarkeit:**
  - **Geodenkriecher:** Der Rücken wird die hellste Fläche, die Haut heller und wärmer (`#3b3142 … #918190`). Dazu ein heller Bauchsaum. Abnahme: Kontrast mindestens 3:1 zu `#2a1f4d`.
  - **Sporenbalg:** Der Hutrand wird orange-rot (`#6e2a14 … #e8915a`). Bernsteinpunkte gibt es nur noch auf den Oberseiten, damit nur ein Gesicht bleibt. Grün ist nur für Wolke und Partikel reserviert.
  - **Steinzeuge:** Goldadern als 1-px-Risse auf Rücken, Seiten, Armen und Platten. Sie leuchten nur während der Steinhaut.
- **Lichtstufen:** Oberseite +1, Unterseite −2, unterste Seitenzeile −1 ab 3 px Höhe. Der Texturmaler prüft automatisch, dass jede Oberseite mindestens so hell ist wie ihre Vorderseite.
- **Aktionen deutlicher:**
  - **Geodenkriecher:** Er kratzt mit 55° Ausschlag, der Kopf senkt sich um 10°, dazu Erzpartikel.
  - **Sporenbalg, Pumpen:** Er pumpt auf 1,2 mit Höhepunkt bei 0,6 s, der Hut hebt sich um 2 px, das Ventil leuchtet.
  - **Sporenbalg, Hüpfen:** Die Wurzeln werden 2 px länger, der Sprung geht 5 px hoch. Dabei wird er auf y 1,15 gestreckt und auf 0,8 gestaucht, die Wurzeln spreizen sich um z ±10°.
  - Das Prüfblatt zeigt die Animationen am größten Ausschlag.
- **Leucht-Ebenen:**
  - Sie sind in M0 Pflicht. `StrataMobDef` bekommt eine Liste von Leucht-Ebenen mit einer Stärke von 0 bis 1 je Zustand.
  - Die `*_glow*.png` erzeugt der Texturmaler schon jetzt.
- **Animationen für Fähigkeiten** entstehen jetzt im Grafik-Block, nicht erst im jeweiligen Meilenstein: `sniff` und `curl` für den Geodenkriecher, `pulse` und `harden` für den Steinzeugen.
- **Hitboxen (Breite × Höhe):**
  - Geodenkriecher 0,9 × 0,7. Bei 1,0 bräuchte die Wegfindung 2 Blöcke breite Gänge.
  - Sporenbalg 0,75 × 1,0.
  - Steinzeuge 1,3 × 2,4. Er erwacht nur, wenn 2×3 Blöcke frei sind.
- **Regelauslegung:**
  - Jede Schicht-Grundfarbe zählt als eigenes Material: höchstens 5 Grundfarben mit je höchstens 5 Tönen.
  - Hilfsteile für gedrehte Würfel (`<würfel>_pivot`, nie animiert) zählen nicht zur Grenze von 12 Knochen.
- **Reihenfolge:** zuerst der Grafik-Block (ca. 3 Tage), dann M0, dann M1. Die Gates stehen unter „Meilensteine“.

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

### 4. Glutwühler (`ember_burrower`): Aschezeit, Gegner (Phase 3)

Ein gedrungener Grab-Salamander mit breiten Schaufelklauen, einer Nasenplatte aus Basalt und einem Kamm aus glühenden Glutplatten auf dem Rücken. Unter der aschegrauen Haut glimmen Risse.

- **Verhalten:** Er taucht in Vulkanasche und andere lockere Böden ab. Dann ragt nur noch der glühende Kamm heraus und zieht wie eine Haifischflosse auf den Spieler zu. Neben ihm bricht er nach kurzer Vorwarnung hervor und schleudert ihn hoch. An der Oberfläche ist er für einige Sekunden verwundbar und kämpft mit den Klauen.
- **Drop:** Glutsplitter; beim Abtauchen wühlt er gelegentlich einen **Fossilbrocken** der Aschezeit frei (Bezug zur Fossil-Präparation).
- **Thema:** Er macht die eigene Mechanik der Aschezeit spürbar: Der Boden selbst ist gefährlich, man achtet auf die Asche um sich herum.
- **Technik:** 64×64, ca. 14 Würfel, Leucht-Ebenen `crest` (Kamm und Augen) und `veins` (Risse, nur vor dem Hervorbrechen). Abtauchen ist eine gehaltene Animation (`burrowed`, Körper 10 px unter dem Boden, nur der Kamm ragt heraus); der Server braucht dafür keine Unsichtbarkeit.

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

### Glutwühler: 4 Fähigkeiten (Gegner, Aschezeit)

| Fähigkeit | Auslöser | Wirkung | Signal und Effekte | Gegenzug | Startwerte |
|---|---|---|---|---|---|
| **Abtauchen** | Ziel in 4–16 Blöcken, Boden unter ihm aus `strata_epochs:burrowable` | Gräbt sich ein (1 s) und zieht mit nur dem Kamm über der Asche auf das Ziel zu: schneller, Schaden −75 %. Hat er Glück, bleibt ein Fossilbrocken der Aschezeit liegen. | Animation `burrow`, dann gehalten `burrowed` (Kamm wackelt). Partikel `block` Vulkanasche als Spur hinter dem Kamm. Sound `block.sand.break`, leise, im Takt. | Auf festen Boden ausweichen (Basalt, Zeitgestein, Bauten): Dort kann er nicht abtauchen. | Tempo ×1,6, Schaden −75 %, Fossil 8 %, Abklingzeit 8 s |
| **Hervorbrechen** | abgetaucht und ≤ 2,5 Blöcke vom Ziel | Nach 0,8 s Vorwarnung bricht er hervor: 6 Schaden, schleudert das Ziel 0,7 hoch. | Vorwarnung: Animation `tremor`, die Risse leuchten auf (Ebene `veins`), Partikel `lava` und Asche aus dem Boden, Sound `block.basalt.break`, tief. Dann Animation `emerge`, Sound `entity.ravager.roar`, leise. | Bei der Vorwarnung zur Seite gehen. Schilde blocken den Schaden, nicht das Hochschleudern. | Schaden 6, Wurf 0,7, Vorwarnung 0,8 s |
| **Glutkamm** | aufgetaucht | 4 s lang verwundbar. Treffer **von hinten** auf den glühenden Kamm machen 50 % mehr Schaden. | Kamm leuchtet voll, Partikel `small_flame` am Kamm. | Hinter ihn gehen und auf den Rücken schlagen. | Dauer 4 s, Bonus +50 % |
| **Klauenhieb** | Nahkampf, aufgetaucht | 4 Schaden, kurzer Rückstoß. | Animation `attack` (Klauen hoch, dann nach unten). Sound `entity.ravager.attack`, hoch. | Abstand halten, Schild. | Schaden 4, Abklingzeit 1,5 s |

### Was das für Modelle und Code bedeutet

- **Neue Animationen** (entstehen im Grafik-Block):
  - Geodenkriecher: `sniff`, `curl`
  - Steinzeuge: `pulse`, `harden`
  - Sporenbalg: keine. Er nutzt `hop` und `pump`, das Aufblähen beim Tod ist eine kurze Skalierung.
  - Alle neuen Animationen werden über das Blockbench-MCP gebaut und gehen durch die Designer-Prüfung.
- **Leucht-Ebene:** Gebraucht bei allen drei Mobs. Die Stärke hängt vom Zustand ab:
  - Kristalle: dauerhaft 0,6, vor dem Einrollen 1,0
  - Ventil und Punkte: nur beim Pumpen
  - Adern: nur bei der Steinhaut
  - Kern: pulsiert

  Die Leucht-Ebene gehört in den generischen Renderer aus M0.
- **Code:** Jede Fähigkeit ist ein eigenes Goal mit Vorwarnzeit, Wirkung und Abklingzeit. Ein gemeinsames `StrataAbility`-Gerüst in M0 hält den Aufwand pro Fähigkeit klein. Effekte laufen über Server-Partikel und Sounds, ohne eigene Pakete.
- **Verfestigung:** Sporenstoß und Sanduhr-Puls greifen in die Uhr ein (`SolidificationClock`). Sie brauchen dafür eine kleine API: einen Zeitfaktor pro Spieler und „Zeit abziehen“.

## Stratolith (Mini-Boss, umgesetzt)

Wandelnder Schichtstapel: Grundgestein-Sockel mit Gesicht (Stirnkante, zwei glühende Augen) und Sanduhr-Kern darunter,
Arme mit Basaltfäusten, darüber die Sporenschicht (Myzel, zwei Pilzhüte) und die Kristallkrone (Calcit, drei Quarzspitzen).
9 Knochen, 15 Würfel, Textur 128 × 128 (Ausnahme für den Boss), Leucht-Ebenen `core` (Kern, Augen) und `crystals`.

| | |
|---|---|
| Beschwörung | Schichtkern (Kristallquarz, Bernstein, Glutsplitter, Zeitessenz, Zeitgestein) auf die Oberseite von Zeitgestein. Nur mit `mastered_<epoche>` für alle drei Epochen, mit 3 × 4 × 3 Blöcken Platz und nicht auf Friedlich. Er steigt 3 s aus dem Boden (`rise`) und ist dabei unverwundbar. |
| Werte | 240 Leben, 12 Schaden, Rüstung 10, langsam (0,18), kein Rückstoß; Boss-Leiste. Trefferfläche 2,3 breit (mit Armen), Höhe je Phase 3,1 / 2,3 / 1,5. |
| Phase 1: Kristallschicht (100–67 %) | **Kristallspitzen** (`shards`): 1,2 s Warnkreis am Boden unter jedem Spieler in 16 Blöcken, dann brechen Spitzen hervor (8 Schaden, Wurf). |
| Phase 2: Sporenschicht (67–33 %) | **Sporenstoß** (`spores`): Sporenwolke (Verfestigung ×2,5 in Epochen), Langsamkeit II, zwei Sporenbälge (höchstens 4 in der Nähe). |
| Phase 3: Grundgestein (33–0 %) | **Zurückspulen** (`rewind`): 2 s Vorwarnung, der Kern tritt hervor und dreht sich; danach holt er sich das Leben der letzten 8 s zurück (höchstens bis 1/3). |
| Kern-Regel | Ein Treffer mit einer **Spitzhacke von vorn** (Angreifer höchstens 45° neben seiner Blickrichtung) während der Vorwarnung bricht das Zurückspulen ab und betäubt ihn 2 s. Von der Seite oder hinten zählt er nicht. Mehr als eine Trefferfläche hat Vanilla nicht; „von vorn“ ersetzt den Treffer auf den Kern. |
| Abwurf | Bei 2/3 und 1/3 Leben kippt die oberste Schicht 1 s lang ab (`shed_crystal`, `shed_spore`), erst dann ist sie weg. Sie lässt Kristallquarz und Quarzdistel-Samen bzw. Bernstein und einen Sporenbeutel fallen. Die Phase steht als Attachment am Mob und übersteht Entladen. |
| Immer | **Hammerschlag** (`slam`): 0,6 s Ausholen, 0,2 s Halten über dem Kopf, Schlag mit Bodenwelle. |
| Belohnung | **Herz der Tiefenzeit** (nur, wenn ein Spieler ihn besiegt), 2–4 Zeitessenzen, Zeitgestein; Kodex-Knoten `stratolith` für den Sieger und Spieler in 32 Blöcken. Das Herz in der zweiten Hand: Transplantator mit 1,5-fachem Radius und ohne Fossil-Kosten, es wird nicht verbraucht. |
| Klang | vorerst Vanilla (Verwüster, Eisengolem, tiefer gestimmt). |
| Ort | Beschwören lässt er sich überall mit Zeitgestein, auch in einer Epoche. Dort beschleunigt der Sporenstoß die Verfestigung (×2,5) und die Inventar-Verfestigung kann einsetzen: ein Kampf in der Epoche ist bewusst die harte Variante, gedacht ist die Oberwelt. |
| Herz | Wird nicht verbraucht und hebt die Fossil-Kosten des Transplantators dauerhaft auf: bewusst als Endspiel-Belohnung (der Transplantator braucht weiter Bohrkern und Kodex-Meisterschaft). |

Prüfung Modell Runde 1: 6/6/7 (Gesicht, Sporenschicht, Hitbox, Kern-Regel, Abwurf-Animationen); Runde 2 mit Code: 7/7/7, freigegeben. Vor einem Release einmal von Hand kämpfen (Diamant und Netherit, Zeit bis zum Sieg, Zahl der Rückspulungen notieren). Bilder: `mobs/stratolith/review_sheet.png`, im Spiel `mobs/stratolith/shots/ingame_phases_1.21.1.png` (links Phase 3, rechts Phase 1).

## Später (gesammelt, nicht eingeplant)

| Mob | Epoche | Rolle | Warum später |
|---|---|---|---|
| Bernsteinmotte (`amber_moth`) | Sporenzeit | passiv; man fängt sie, und sie reift zu Bernstein mit Einschluss (Fossil-Quelle) | Fliegen ist am teuersten, und das Insektenmodell ist schwer |
| Resonanzling (`resonant_shard`) | Kristallzeit | neutral; lässt Erz in der Nähe aufleuchten und pausiert kurz die Verfestigung | schwebt, braucht also Flug-Navigation |
| Harzschnecke (`resin_snail`) | Sporenzeit | passiv; ihre Spur härtet zu Bernsteinplatten aus | gut, aber ohne Bezug zum Kern-Ablauf |

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
2. **Prüfung:** Designer, Game Designer und Technical Artist geben je mindestens 7/10. Der Mob ist auch im Dunkeln lesbar.
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

**Gates:**
- **Gate A, vor M0:** Geodenkriecher und Sporenbalg bekommen von allen drei Prüfern mindestens 7. Für den Steinzeugen gilt dasselbe, oder er wird nach 2 Runden geparkt.
- **Gate B, in M0 nach dem Renderer:** Der Geodenkriecher sieht im Client-Screenshot unter 1.21.1 genauso aus wie in Blockbench, und das Leuchten ist sichtbar. Dafür sind höchstens 5 Personentage eingeplant. Sonst wird die Pipeline überprüft, als Rückfall dient der Blockbench-Java-Export als Codegenerator.
- **Gate C, nach M1:** Braucht der Geodenkriecher mehr als 2 Personenwochen, wird zuerst die Pipeline verbessert, bevor ein weiterer Mob dazukommt.

**Client-Tests:** In der Cloud läuft der Client für 1.21.1 und 1.21.11 über OpenGL (Xvfb), für 26.x über Vulkan mit Mesa lavapipe (Paket `mesa-vulkan-drivers`, das Ladefenster von NeoForge wird dafür abgeschaltet). Alle drei Mobs und die Rüstung sind in 26.3 im Bild bestätigt (`docs/screenshots/clients/`). Das Skript kann auch Bildserien mit KI-Mob (`SHOT_ACT`), in Rüstung (`SHOT_GEAR`) und das Erwachen des Steinzeugen (`SHOT_AWAKEN`, Verfestigung nach 20 s) aufnehmen.

## Mob-Sounds

Alle Ruf-, Schmerz- und Todeslaute sind eigene Klänge aus `tools/make_mob_sounds.py`: per Synthese, ohne Aufnahmen oder fremde Samples, mono (für Richtungshören), feste Seeds. Die Lautheit wird wahrnehmungsnah angeglichen (vereinfachte K-Gewichtung wie bei LUFS: Rufe −22 dB, alles andere −18 dB), Spitzen werden weich begrenzt statt alles leiser zu machen. Jeder Klang hat Anteile über 300 Hz, damit er auch aus Laptop-Lautsprechern kommt. Varianten unterscheiden sich in der Tonhöhe. Neu erzeugen: `python tools/make_mob_sounds.py --sheet spektrogramme.png`.

| Mob | Ruf | Schmerz | Tod |
|---|---|---|---|
| Geodenkriecher | klappernde Panzerbeine, tiefere Kristalltöne als Amethyst | Knacks im Panzer, kurzes Quieken | Zerspringen, Splitter rieseln aus |
| Sporenbalg | Ein- und Ausatmen eines Blasebalgs mit Pfeifen | Quietschen und Sporenstoß | Luft entweicht, Ton sinkt und flattert |
| Steinzeuge | mahlender Stein, hohles Echo | dumpfer Schlag, Riss, Kies | Zerbröckeln, Echo verklingt nach unten |
| Glutwühler | Knurren mit Stimmfärbung und knisternden Funken | kurzes, hohes Aufjaulen | verzischende Glut, letztes Knurren, Brocken |
| Spiralit | freundliches Zwitschern mit Luftblase | kurzes Quieken | absinkender Pfeifton, das Vibrato erlahmt |

**Freigegeben (07.11.2026):** Runde 1 6/5/6, Runde 2 7/7/7 (Designer, Game Design, Technik). Geändert in Runde 2: Grollen mit hörbaren Mitten und an die Vorwarnzeit gekoppelt, Obertöne für Glutwühler und Steinzeuge, wahrnehmungsnahe Lautheit, Schmerz und Tod des Glutwühlers getrennt.

Der Glutwühler hat drei eigene Kampf-Signale: **Graben** (körnige Schaufelstöße, auch leise, solange er unter der Asche zieht), **Grollen** (ratterndes Geröll über tiefem Grollen, laut ab 0,35 s; die Datei dauert 0,8 s = 16 Ticks, und die Tonhöhe passt sie an die eingestellte Vorwarnzeit `emerge.warning` an, sodass es genau mit dem Ausbruch endet) und **Hervorbrechen** (Schlag, Erdstoß, Knurren). So hört man den Angriff kommen, auch ohne hinzusehen. Die übrigen Fähigkeits-Effekte (Kristallpanzer, Sporenstoß usw.) nutzen weiter Vanilla-Klänge.
