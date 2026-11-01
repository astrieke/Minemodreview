# Blockbench-MCP („Claude Bridge“)

Damit kann Claude Modelle direkt in Blockbench bauen: Knochen und Würfel setzen, Texturen laden, Animationen anlegen, Screenshots ansehen und als `.bbmodel` oder Java-Code exportieren.

```
Claude ──MCP (stdio)──> server.js ──WebSocket 127.0.0.1:9333──> Plugin in Blockbench
```

- **`server.js`**: der MCP-Server. Claude startet ihn selbst.
- **`plugin/claude_bridge.js`**: das Blockbench-Plugin. Es verbindet sich mit dem Server und führt die Befehle aus. Es läuft in der Desktop-App und in der Web-Version.
- **`headless.js`**: startet Blockbench ohne Bildschirm (für Cloud und CI). Am eigenen Rechner nicht nötig.

## Einrichtung am eigenen Rechner

Du brauchst Node.js ab Version 18 und Blockbench (Desktop oder web.blockbench.net).

**1. Abhängigkeiten installieren**

```bash
cd tools/blockbench-mcp
npm install
```

**2. Server bei Claude anmelden**

Für Claude Code:

```bash
claude mcp add blockbench -- node /voller/pfad/zu/tools/blockbench-mcp/server.js
```

Für Claude Desktop trägst du ihn in `claude_desktop_config.json` ein:

```json
{
  "mcpServers": {
    "blockbench": {
      "command": "node",
      "args": ["/voller/pfad/zu/tools/blockbench-mcp/server.js"]
    }
  }
}
```

**3. Plugin in Blockbench laden**

*Datei → Plugins → „Load Plugin from File“ → `plugin/claude_bridge.js`*. Unter *Tools* erscheint dann „Claude Bridge: verbunden“, sobald Claude den Server gestartet hat. Das Plugin versucht es alle 2 Sekunden erneut, die Reihenfolge ist also egal.

Danach kannst du Claude zum Beispiel sagen: *„Bau in Blockbench einen Zeitschwund-Mob: schwebender Kristallkörper, zwei Arme, Idle-Animation. Zeig mir einen Screenshot und exportier ihn nach models/zeitschwund.bbmodel.“*

## Werkzeuge

| Werkzeug | Zweck |
|---|---|
| `status` | Verbindung und Infos zum offenen Projekt |
| `new_project` | Neues Projekt (für Mod-Mobs: `modded_entity`, Box-UV) |
| `add_bone`, `add_cube` | Knochen (bewegliche Teile) und Würfel hinzufügen |
| `build_model` | Viele Knochen und Würfel in einem Aufruf |
| `update_element`, `delete_element` | Teile ändern oder löschen |
| `outline` | Modellaufbau plus UV-Layout (wo jede Würfelseite auf der Textur liegt) |
| `set_texture`, `get_texture` | PNG-Textur laden (Datei oder Base64) bzw. auslesen |
| `add_animation` | Animation mit Keyframes (Rotation, Position, Skalierung) |
| `screenshot` | Bild des Modells (Ansichten iso/front/side/back/top oder eigene Kamera), auch zu einem Zeitpunkt einer Animation |
| `export` | `bbmodel`, `modded_entity` (Java), `java_block`, `bedrock`, `gltf`, `obj` … optional direkt als Datei |
| `import_bbmodel` | Vorhandene `.bbmodel` öffnen und weiterbearbeiten |
| `eval_js` | Nur mit Freigabe, siehe unten |

Koordinaten sind Blockbench-Pixel: 16 = 1 Block, y zeigt nach oben, die Füße stehen bei y = 0. Was Claude ändert, lässt sich in Blockbench mit Strg+Z rückgängig machen.

## Test

```bash
npm test
```

Der Test startet den Server wie Claude, öffnet Blockbench headless im Browser (Playwright mit Chromium) und baut über die MCP-Werkzeuge ein kleines Testwesen. Geprüft werden:
- Aufbau, Textur und Animation
- Screenshots (auch mit Animation)
- Export als `.bbmodel` und als Java-Code
- erneutes Öffnen der exportierten Datei
- Fehlermeldungen
- Abweisung fremder Webseiten

Die Ergebnisse landen in `test/out/`.

## Sicherheit

- Der Server lauscht nur auf `127.0.0.1` und nimmt nur Verbindungen von Blockbench an: von der Desktop-App oder von `web.blockbench.net`. Andere Webseiten werden abgewiesen.
- **`eval_js`** führt beliebigen JavaScript-Code in Blockbench aus. Es ist doppelt gesperrt:
  - Der Server bietet es nur an, wenn er mit `BLOCKBENCH_MCP_ALLOW_EVAL=1` gestartet wurde.
  - Blockbench führt es nur aus, wenn es dort freigegeben ist. Dazu in der Blockbench-Konsole `localStorage.setItem('claude_bridge_allow_eval', '1')` eingeben.

## Hinweis zum Java-Export

`export` mit `modded_entity` nutzt die Vorlage von Blockbench („1.17 or later, Mojang mappings“). Der wichtige Teil ist `createBodyLayer()` (Würfel, Knochen, UV). Die Klasse drumherum ist für alte Versionen geschrieben. Ab Minecraft 1.21.2 arbeiten Modelle mit „Render States“, ab 1.21.11 heißt `ResourceLocation` `Identifier`. Für Strata übernimmt Claude deshalb nur `createBodyLayer()` und schreibt Modell- und Renderer-Klasse passend zur jeweiligen Version.
