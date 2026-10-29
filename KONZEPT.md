# STRATA – Tiefenzeit

Ein Set aus drei Mods für Minecraft 1.21.1 (NeoForge zuerst), gebaut für Modpacks.

> Entstanden aus einer Runde mit Projektmanager, Game Designer, Software-Architekt und Programmierer.
> Gewählt wurde die Idee des Game Designers. Architektur, Aufwand und Scope wurden von den anderen drei Rollen geschärft.

---

## 1. Die Idee in einem Absatz

Jede Welt hat eine verborgene Erdgeschichte. Mit einem **Kernbohrer** zieht der Spieler Bohrkerne aus dem Boden. Ein Kern verrät, welche **Epoche** unter diesem Ort liegt, etwa Kristallzeit, Sporenzeit oder Aschezeit. Aus dem Kern baut man ein Portal in genau diese Epoche. Jede Epoche ist eine kleine eigene Dimension mit Urzeit-Ressourcen, Fossilien und Gefahren. Bleibt man zu lange, **verfestigt** sich die Zeit um einen herum. Wer eine Epoche im **Erdkodex** vollständig erforscht hat, kann sie in die Oberwelt zurückholen: Der **Epochen-Transplantator** verwandelt ein Gebiet dauerhaft in eine farmbare Urzeit-Landschaft.

## 2. Gameplay-Loop

| Zeitraum | Was der Spieler tut |
|---|---|
| Minütlich | bohren, Kerne analysieren, Fossilien präparieren (Meißel mit Bruchrisiko) |
| Stündlich | Expedition in eine Epoche, gegen die Verfestigung arbeiten, Stabilisatoren bauen |
| Über Wochen | Kodex vervollständigen, Epochen in die Oberwelt transplantieren, „Museum der Erdgeschichte“ bauen |

**Belohnungen:** Urzeit-Erze mit Sondereigenschaften (z. B. Kristallzeit-Quarz), Fossil-Begleiter mit passiven Boni, Epochen-Samen (Pflanzen, die es sonst nirgends gibt), Zeitessenz als Fluid/Item für Magie-Mods.

**Gefahren:** Verfestigung (erst Blöcke, dann Inventar-Slots), Zeitschwund-Mobs, die Verzauberungen „altern“ lassen, später Verwerfungen (Zeitrisse in der Oberwelt).

**Wow-Moment:** Der Transplantator. Der Boden bebt, Gesteinsschichten falten sich sichtbar nach oben (Block-Display-Entities), und ein ausgestorbener Wald wächst in Sekunden.

## 3. Die drei Mods

```
strata-core  ◄──  strata-epochs        (Inhalt)
     ▲
     └──────────  strata-bridge        (Integrationen, alle Fremd-Mods optional)
```

Die Abhängigkeit zeigt immer nur zum Core. Der Core kennt keine Content-Mod, und die Content-Mods kennen sich nicht gegenseitig.

### 3.1 `strata-core`: Kodex- und Progressions-Framework
- Enthält keinen Spielinhalt, nur Infrastruktur und eine kleine, stabile API.
- **Datapack-Registries:** `strata:epoch` (Definition einer Epoche), `strata:codex_node` (Forschungsknoten), `strata:condition_type` und `strata:reward_type` (per `MapCodec`-Dispatch erweiterbar).
- **Spielerstand** als NeoForge Data Attachment (bzw. Team-Attachment) mit Codec, Sync über eigene Payloads: voller Sync beim Login, danach nur Änderungen.
- **Gating** über Tags (`#strata:gated/tier2`): Rezepte, Items, Dimensionen.
- **Öffentliche Events:** `CodexNodeUnlockedEvent`, cancelbares `GateCheckEvent`. Dazu eine Fassade `StrataAPI.hasNode(player, id)`.
- `/strata`-Debug-Befehl. Fehlermeldungen beim Parsen nennen Datei und Feld.

Damit ist der Core auch ohne Epochen als **datengetriebenes Gating-Werkzeug** für Pack-Autoren nützlich.

### 3.2 `strata-epochs`: der Inhalt
- Kernbohrer (3 Stufen), Kernanalyse-Tisch, Fossil-Präparationstisch, Stabilisatoren, Transplantator.
- **Epochen-Dimensionen komplett per JSON** (`dimension_type`, Noise Settings, Biome, Features, Jigsaw-Strukturen) mit 2–3 eigenen `DensityFunction`-Typen. Kein eigener `ChunkGenerator`.
- **Wichtige Entscheidung:** Die Oberwelt-Generierung wird **nicht** verändert. Welche Epoche unter einem Ort liegt, ergibt sich deterministisch aus Welt-Seed und Region (Hash). Das spart Worldgen-Konflikte mit Terralith, Tectonic & Co. und funktioniert in jeder Overworld, auch in Skyblock.
- Portale werden per Ritual aktiviert, Rahmenblock und Aktivator sind über Tags austauschbar.
- Optik nur über `DimensionSpecialEffects` (Nebel, Farben), damit Iris und Shaderpacks nicht stören.

### 3.3 `strata-bridge`: Integrationen
Alle Integrationen sind optional und werden nur geladen, wenn die jeweilige Mod da ist:
- **EMI/JEI:** gesperrte Rezepte mit Kodex-Hinweis. **Jade:** Bohrfortschritt und Epoche.
- **FTB Quests / Heracles:** Kodex-Knoten als Aufgabe und Belohnung. **KubeJS:** Plugin mit Events.
- **Curios/Accessories:** Stabilisator-Amulett.
- **Tech:** Bohrer und Tische nehmen FE und Item- und Fluid-Capabilities an. Mekanism-, Thermal- oder IE-Leitungen funktionieren dadurch ohne eigene Adapter.
- Urzeit-Erze tragen `c:ores/*`-Tags, damit fremde Erzverdopplung greift.

## 4. Was Pack-Autoren steuern können

| Was | Wo |
|---|---|
| Eigene Epochen (Biome, Mobs, Loot, Seltenheit, Tiefe) | Datapack |
| Verfestigungs-Timer pro Epoche | Datapack |
| Kodex-Knoten und Freischaltbedingungen | Datapack / KubeJS |
| Rezept-Tiers (z. B. „Bohrer T3 braucht Stahl aus Mod X“) | Rezepte / Tags |
| Skyblock-Modus: Bohrkerne aus Rezepten statt aus dem Boden | Datapack |
| Verwerfungen an/aus, Griefing, ausgeschlossene Dimensionen | Config (TOML) |
| Performance-Limits, Debug | Config (TOML) |

Faustregel: **Spielbalance gehört ins Datapack** (pro Welt, versionierbar), **Technik in die Config**.

Andere Mods können eigene Epochen nur per JSON beisteuern, zum Beispiel eine „Kupferzeit“ mit Create-Erzen.

## 5. Scope: MVP und Ausbau

Hier hat der Projektmanager kräftig gekürzt, weil Scope Creep das größte Risiko ist.

| Phase | Inhalt | Aufwand (PW) |
|---|---|---|
| 0 – Spike | Eine Test-Epoche per JSON, Portal, Attachment-Sync. Go/No-Go zur Worldgen-Performance | 2 |
| 1 – MVP | Core komplett, **2 Epochen** (Kristallzeit, Sporenzeit), Bohrer T1–T2, Analyse-Tisch, Kodex, Verfestigung (nur Blöcke) | 8–10 |
| 2 – Bridge | EMI/JEI, Jade, FE/Capabilities, KubeJS. Geschlossene Beta mit 3–5 Pack-Teams | 3–4 |
| 3 – Wow | Transplantator, Fossil-Begleiter, 3. Epoche (Aschezeit), Bohrer T3 | 5–6 |
| 4 – Später | Verwerfungen (Zeitrisse), Inventar-Verfestigung, Fabric (nur bei Nachfrage) | offen |

**Bis zur Beta:** etwa 13–16 Personenwochen, also 2–3 Monate mit 2 Entwicklern. **Pro MC-Update:** etwa 1–2 PW Portierung.

## 6. Risiken und Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| Scope Creep (Verwerfungen, Transplantator, viele Epochen) | Harter Feature-Freeze pro Phase, MVP mit nur 2 Epochen |
| Worldgen-Performance | JSON und Vanilla-Bausteine statt eigenem Generator; Chunky-Pregen-Benchmark und Spark in CI |
| Transplantator verändert große Gebiete | Budgetierte Tick-Queue, nur geladene Chunks, max. Radius per Config |
| Inventar-Verfestigung frustriert Spieler | Erst in Phase 4, per Datapack abschaltbar |
| Shader-Konflikte (Iris/Sodium) | Keine Custom-Shader, nur Fog und Farben; Sodium+Iris-Test vor jedem Release |
| Große Packs (300+ Mods) | Mixins nur, wo kein Event existiert, kein `@Overwrite`; Gate-Checks gecacht; Boot-Test mit großer Modliste |
| MC-Updates | Datagen für alles; `"format": 1` im Datenformat; 1.21.1 als LTS-Branch |

## 7. Technische Basis

**Repo-Struktur (Mono-Repo, Gradle-Multiprojekt, ModDevGradle):**

```
strata/
├─ build-logic/              # Convention-Plugins (java, neoforge, publishing)
├─ gradle/libs.versions.toml
├─ core/                     # API (api/) + Implementierung (impl/)
├─ epochs/                   # Inhalt, Worldgen, src/generated/resources
├─ bridge/                   # compat/{emi,jei,jade,ftbquests,kubejs,curios}
├─ example-datapacks/        # Beispiel-Epoche, Beispiel-Gating
├─ docs/                     # Datenformat-Spezifikation, JSON-Schemas
└─ .github/workflows/        # build, datagen-check, gametest, release
```

**Versionierung:** `1.21.1-<semver>`. Ein Major-Sprung bedeutet einen Bruch von API oder Datenformat. Content-Mods deklarieren Versionsbereiche auf den Core, z. B. `[1.0,2.0)`.

**Tests und CI:**
- GameTests auf einem Headless-Server (`gameTestServer`): Portal-Aktivierung, Kodex-Freischaltung, Rezept-Gating, Speichern und Laden.
- JUnit für Codecs und reine Logik.
- Worldgen-Smoke-Test: Chunks vorgenerieren und das Log auf Fehler prüfen.
- Datagen-Diff-Check: Der Build schlägt fehl, wenn generierte Dateien nicht committet sind.
- Release über Tags: Modrinth (Minotaur) und CurseForge, Tokens in GitHub Secrets.

## 8. Erfolgskriterien
- 6 Monate nach Release: mindestens 10 öffentliche Packs nutzen Strata.
- Mindestens 3 Epochen, die die Community per Datapack beisteuert.
- Keine Worldgen-Exceptions und unter 5 % zusätzliche Server-Tickzeit in einem Referenz-Pack.

## 9. Verworfene Alternativen (für später)
- **Packsmith** (Favorit des Projektmanagers): ein QA-Tool, das Packs automatisch prüft (unerreichbare Items, Rezeptkonflikte, leere Tags). Großer Nutzen, aber nur für eine Nischen-Zielgruppe. Als eigenständiges Folgeprojekt sehr sinnvoll.
- **Karawanserei:** eine wandernde Wüstendimension mit Handelslogistik.
- **Wirt:** die Dimension ist der Körper eines schlafenden Leviathans.
- **Umweltgefahren-Framework:** überschneidet sich zu stark mit Tough As Nails und Cold Sweat.
