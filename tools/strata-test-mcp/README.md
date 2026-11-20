# Strata-Test-MCP

Damit kann Claude die Strata-Mods **selbstständig testen**: bauen, den Smoke-Test laufen lassen, einen Testserver starten und per Befehl bespielen, Logs lesen, Balance und Leistung messen und Screenshots aus dem Spiel ansehen. Der Server ruft nur die Skripte des Repos auf (`scripts/*.sh`), eigene Testlogik hat er nicht.

```
Claude ──MCP (stdio)──> server.js ──> gradlew / scripts/*.sh / RCON zum Testserver
```

## Werkzeuge

| Werkzeug | Was es tut |
|---|---|
| `versions` | unterstützte Minecraft-Versionen, ob ein Testserver läuft |
| `build` | Core, Epochs und Bridge für Versionen bauen; gibt Compilerfehler zurück |
| `smoke_test` | Server mit allen Mods starten und auswerten: Selbsttest, Durchspieltest (Bohrkern bis Anbau), Format-Wächter, Advancements |
| `server_start` | Testserver starten (flache Welt, Demo-Datapack, JEI, Jade), wartet auf den Selbsttest |
| `server_command` | Befehl an den Testserver schicken (RCON), Antwort zurück |
| `server_log` | Log des Testservers lesen, mit Filter |
| `server_stop` | Testserver beenden |
| `balance` | Balance-Messung im Schnelllauf (Harzschnecke, Harzfalle, Stratolith-Kampf) |
| `perf` | Leistungsmessung (Tickdauer mit Strata-Mobs, Vergleich mit Zombies) |
| `screenshot` | Client starten und Bilder zurückgeben; Schalter wie `{"MOB": "strata_epochs:stratolith"}`, `{"INVENTORY": "1"}` |

## Einrichtung

Node.js ab 18 und Java (Gradle lädt die JDKs selbst). Für `screenshot` unter Linux ohne Bildschirm: Xvfb, ImageMagick (`import`) und für 26.x Mesa lavapipe (`mesa-vulkan-drivers`).

```bash
cd tools/strata-test-mcp
npm install
```

**Claude Code im Repo:** Die Datei `.mcp.json` im Hauptordner meldet den Server an; Claude Code fragt beim ersten Start einmal nach.

**Von Hand:**

```bash
claude mcp add strata-test -- node /voller/pfad/zu/tools/strata-test-mcp/server.js
```

Umgebungsvariablen: `STRATA_REPO` (Pfad zum Repo, Standard: zwei Ordner über `server.js`), `STRATA_MCP_PYTHON` (Python mit `python-xlib`, damit der Screenshot die Vanilla-Warnung „experimentelle Einstellungen“ selbst bestätigt).

## Beispiel

> „Bau 26.3, lass den Smoke-Test laufen, starte dann einen Testserver, ruf in der Aschezeit einen Glutgräber und sag mir, was im Log passiert.“

Claude ruft dann `build`, `smoke_test`, `server_start`, `server_command` (`execute in strata_epochs:ash_age run summon strata_epochs:ember_burrower 0 80 0`) und `server_log` nacheinander auf.

## Test

```bash
npm test            # startet den MCP-Server, darüber einen Testserver 1.21.1, schickt Befehle, liest das Log (etwa 2 min)
```
