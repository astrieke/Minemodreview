#!/usr/bin/env node
/**
 * Strata-Test-MCP-Server
 *
 * Damit kann Claude die Strata-Mods selbstständig testen: bauen, den Smoke-Test (Selbsttest, Durchspieltest,
 * Format-Wächter) laufen lassen, einen Testserver starten und per RCON bespielen, Logs lesen, Balance und Leistung
 * messen und Client-Screenshots aufnehmen. Alle Werkzeuge rufen die Skripte des Repos auf (scripts/*.sh), es gibt
 * keine eigene Testlogik daneben.
 *
 * Claude startet den Server über MCP (stdio). Umgebungsvariablen:
 *   STRATA_REPO         Pfad zum Repo (Standard: zwei Ordner über dieser Datei)
 *   STRATA_MCP_PYTHON   Python mit python-xlib für Client-Screenshots (bestätigt die Vanilla-Warnung)
 */
import { spawn } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';

const REPO = path.resolve(process.env.STRATA_REPO || path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..'));
const log = (...args) => console.error('[strata-test-mcp]', ...args);   // stdout gehört dem MCP-Protokoll

// ------------------------------------------------------------------ Hilfen

function versions() {
    return fs.readdirSync(path.join(REPO, 'versions'), { withFileTypes: true })
        .filter(d => d.isDirectory()).map(d => d.name).sort();
}

function checkVersions(list) {
    const known = versions();
    const wanted = list && list.length ? list : known;
    const bad = wanted.filter(v => !known.includes(v));
    if (bad.length) throw new Error(`Unbekannte Version(en): ${bad.join(', ')}. Vorhanden: ${known.join(', ')}`);
    return wanted;
}

/** Führt einen Befehl im Repo aus und sammelt die Ausgabe (gekürzt auf das Ende). */
function run(cmd, args, { timeoutMs = 30 * 60_000, env = {} } = {}) {
    return new Promise(resolve => {
        const child = spawn(cmd, args, { cwd: REPO, env: { ...process.env, ...env } });
        let out = '';
        const add = d => { out += d; if (out.length > 400_000) out = out.slice(-200_000); };
        child.stdout.on('data', add);
        child.stderr.on('data', add);
        const timer = setTimeout(() => { child.kill('SIGTERM'); out += '\n[Zeitlimit überschritten]'; }, timeoutMs);
        child.on('close', code => { clearTimeout(timer); resolve({ code, out }); });
    });
}

const tail = (s, n = 40) => s.split('\n').filter(l => l.trim() && !l.startsWith('Picked up JAVA_TOOL_OPTIONS')).slice(-n).join('\n');

/** Die Ergebniszeilen der Strata-Tests aus einem Log (ohne Zeitstempel). */
function strataLines(logText) {
    return logText.split('\n')
        .filter(l => /\[Strata(-[A-Za-z]+)?\]/.test(l) || /Exception|Caused by|Crash report/.test(l))
        .map(l => l.replace(/^\[[^\]]*\] \[[^\]]*\] \[[^\]]*\]: /, '').slice(0, 600));
}

function summarizeSmoke(v) {
    const file = path.join(REPO, 'bridge', 'versions', v, 'run', 'server', 'smoke.log');
    if (!fs.existsSync(file)) return { version: v, log: null };
    const lines = strataLines(fs.readFileSync(file, 'utf8'));
    const failures = lines.filter(l => /FEHLER|Fertig, [1-9]|Exception|Crash report|Feld geht verloren|nicht lesen/.test(l));
    return {
        version: v,
        selftest: lines.find(l => l.includes('[Strata-Test] Fertig')) || 'kein Ergebnis',
        playthrough: lines.find(l => /\[Strata-Durchspiel\] .*-> (OK|FEHLER)/.test(l)) || 'kein Ergebnis',
        dataformat: lines.find(l => l.includes('Datenformat: ') && /-> /.test(l)) || 'kein Ergebnis',
        failures: failures.slice(0, 30),
        log: path.relative(REPO, file),
    };
}

// ------------------------------------------------------------------ RCON (Minecraft-Fernsteuerung)

function rconPacket(id, type, body) {
    const b = Buffer.from(body, 'utf8');
    const buf = Buffer.alloc(14 + b.length);
    buf.writeInt32LE(10 + b.length, 0);
    buf.writeInt32LE(id, 4);
    buf.writeInt32LE(type, 8);
    b.copy(buf, 12);
    return buf;
}

function rcon(port, password, command, timeoutMs = 20_000) {
    return new Promise((resolve, reject) => {
        const sock = net.connect(port, '127.0.0.1');
        let buf = Buffer.alloc(0);
        let authed = false;
        const timer = setTimeout(() => { sock.destroy(); reject(new Error('RCON: keine Antwort')); }, timeoutMs);
        sock.on('connect', () => sock.write(rconPacket(1, 3, password)));
        sock.on('data', data => {
            buf = Buffer.concat([buf, data]);
            while (buf.length >= 4 && buf.length >= 4 + buf.readInt32LE(0)) {
                const len = buf.readInt32LE(0);
                const id = buf.readInt32LE(4);
                const body = buf.subarray(12, 4 + len - 2).toString('utf8');
                buf = buf.subarray(4 + len);
                if (!authed) {
                    if (id === -1) { clearTimeout(timer); sock.destroy(); reject(new Error('RCON: Passwort abgelehnt')); return; }
                    authed = true;
                    sock.write(rconPacket(2, 2, command));
                } else {
                    clearTimeout(timer);
                    sock.end();
                    resolve(body);
                    return;
                }
            }
        });
        sock.on('error', e => { clearTimeout(timer); reject(e); });
    });
}

// ------------------------------------------------------------------ Testserver

let srv = null;   // { version, child, port, password, logFile, exited }

function prepareRunDir(v, fresh) {
    const dir = path.join(REPO, 'bridge', 'versions', v, 'run', 'server');
    if (fresh) fs.rmSync(dir, { recursive: true, force: true });
    fs.mkdirSync(path.join(dir, 'world', 'datapacks'), { recursive: true });
    const demo = path.join(dir, 'world', 'datapacks', 'strata-demo');
    if (!fs.existsSync(demo)) fs.cpSync(path.join(REPO, 'example-datapacks', 'strata-demo'), demo, { recursive: true });
    fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n');
    return dir;
}

async function waitFor(file, pattern, timeoutMs, child) {
    const end = Date.now() + timeoutMs;
    while (Date.now() < end) {
        if (child && child.exitCode !== null) return false;
        if (fs.existsSync(file) && pattern.test(fs.readFileSync(file, 'utf8'))) return true;
        await new Promise(r => setTimeout(r, 2000));
    }
    return false;
}

async function stopServer() {
    if (!srv) return 'Kein Testserver läuft.';
    const s = srv;
    srv = null;
    try { await rcon(s.port, s.password, 'stop', 5000); } catch { /* läuft evtl. schon nicht mehr */ }
    const deadline = Date.now() + 60_000;
    while (s.child.exitCode === null && Date.now() < deadline) await new Promise(r => setTimeout(r, 1000));
    if (s.child.exitCode === null) s.child.kill('SIGKILL');
    return `Testserver ${s.version} beendet.`;
}

process.on('exit', () => { if (srv && srv.child.exitCode === null) srv.child.kill('SIGKILL'); });

// ------------------------------------------------------------------ MCP-Werkzeuge

const server = new McpServer({ name: 'strata-test', version: '0.1.0' });
const text = value => ({ content: [{ type: 'text', text: typeof value === 'string' ? value : JSON.stringify(value, null, 2) }] });

function tool(name, description, schema, handler) {
    server.registerTool(name, { description, inputSchema: schema }, async args => {
        try {
            return await handler(args);
        } catch (e) {
            return { isError: true, content: [{ type: 'text', text: String(e.message || e) }] };
        }
    });
}

const versionList = z.array(z.string()).optional().describe('Minecraft-Versionen, z. B. ["1.21.1"]; leer = alle');
const oneVersion = z.string().describe('Minecraft-Version, z. B. "1.21.1" oder "26.3"');

tool('versions', 'Listet die unterstützten Minecraft-Versionen und ob gerade ein Testserver läuft.', {},
    async () => text({ repo: REPO, versions: versions(), testserver: srv ? { version: srv.version, port: srv.port } : null }));

tool('build',
    'Baut Strata Core, Epochs und Bridge für die angegebenen Versionen. Gibt je Version OK oder die Compilerfehler zurück.',
    { versions: versionList },
    async ({ versions: list }) => {
        const result = {};
        for (const v of checkVersions(list)) {
            const r = await run('./gradlew', ['--console=plain', '-q', `:core:${v}:build`, `:epochs:${v}:build`, `:bridge:${v}:build`]);
            result[v] = r.code === 0 ? 'OK' : tail(r.out.split('\n').filter(l => /error|FAIL|warning:|\^/.test(l)).join('\n') || r.out, 60);
        }
        return text(result);
    });

tool('smoke_test',
    'Startet für jede Version einen Server mit allen drei Mods, JEI und Jade und wertet das Log aus: Selbsttest '
    + '(Dimensionen, Mobs, Fähigkeiten, Loot, Biome …), Durchspieltest (Bohrkern bis Anbau über echte Ticks), '
    + 'Format-Wächter (alle Datendateien), Advancements. Dauert etwa 1–2 Minuten je Version.',
    { versions: versionList },
    async ({ versions: list }) => {
        const vs = checkVersions(list);
        if (srv && vs.includes(srv.version)) throw new Error(`Testserver ${srv.version} läuft; erst server_stop aufrufen.`);
        const r = await run('scripts/smoke-test.sh', vs);
        return text({ overall: tail(r.out, 1), results: vs.map(summarizeSmoke) });
    });

tool('server_start',
    'Startet einen Testserver (alle drei Mods, JEI, Jade, Demo-Datapack, flache Testwelt) und wartet, bis er bereit ist. '
    + 'Danach nimmt server_command Befehle an. Der Selbsttest läuft beim Start mit. fresh=false behält die Welt vom letzten Lauf.',
    { version: oneVersion, fresh: z.boolean().default(true) },
    async ({ version, fresh }) => {
        checkVersions([version]);
        if (srv) await stopServer();
        const dir = prepareRunDir(version, fresh);
        const port = 25575 + versions().indexOf(version);
        const password = crypto.randomBytes(12).toString('hex');
        fs.writeFileSync(path.join(dir, 'server.properties'), [
            'online-mode=false', 'level-type=minecraft\\:flat', 'spawn-protection=0', 'max-tick-time=-1',
            'enable-rcon=true', `rcon.port=${port}`, `rcon.password=${password}`, 'broadcast-rcon-to-ops=false', '',
        ].join('\n'));
        const logFile = path.join(dir, 'mcp.log');
        const out = fs.openSync(logFile, 'w');
        const child = spawn('./gradlew', ['--console=plain', `:bridge:${version}:runServer`, '-PwithCurios'],
            { cwd: REPO, stdio: ['ignore', out, out], detached: false });
        srv = { version, child, port, password, logFile };
        const ready = await waitFor(logFile, /\]: Done \(|RCON running on/, 8 * 60_000, child);
        if (!ready) {
            const msg = fs.existsSync(logFile) ? tail(fs.readFileSync(logFile, 'utf8'), 40) : '';
            await stopServer();
            throw new Error(`Server ${version} kam nicht hoch:\n${msg}`);
        }
        await waitFor(logFile, /RCON running on/, 60_000, child);
        await waitFor(logFile, /\[Strata-Test\] Fertig/, 120_000, child);   // Selbsttest abwarten
        return text({ started: version, rcon_port: port, log: path.relative(REPO, logFile),
            selftest: strataLines(fs.readFileSync(logFile, 'utf8')).filter(l => /Fertig|FEHLER/.test(l)).slice(0, 10) });
    });

tool('server_command',
    'Schickt einen Befehl an den laufenden Testserver (ohne führenden Schrägstrich, z. B. "strata_epochs status", '
    + '"summon strata_epochs:geode_crawler 0 -60 0", "execute in strata_epochs:crystal_age run ..."). '
    + 'Gibt die Antwort des Servers zurück. Es gibt keinen Spieler; Befehle mit @p/@s wirken nur über execute.',
    { command: z.string() },
    async ({ command }) => {
        if (!srv) throw new Error('Kein Testserver läuft; erst server_start aufrufen.');
        const reply = await rcon(srv.port, srv.password, command.replace(/^\//, ''));
        return text(reply || '(keine Ausgabe)');
    });

tool('server_log',
    'Liest das Log des laufenden (oder zuletzt gelaufenen) Testservers. grep filtert per regulärem Ausdruck '
    + '(z. B. "Strata|Exception"); lines begrenzt auf die letzten N Treffer.',
    { grep: z.string().optional(), lines: z.number().int().min(1).max(2000).default(100) },
    async ({ grep, lines }) => {
        const file = srv ? srv.logFile : null;
        if (!file || !fs.existsSync(file)) throw new Error('Kein Testserver-Log vorhanden.');
        let all = fs.readFileSync(file, 'utf8').split('\n').filter(l => !l.startsWith('Picked up'));
        if (grep) {
            const re = new RegExp(grep);
            all = all.filter(l => re.test(l));
        }
        return text(all.slice(-lines).join('\n'));
    });

tool('server_stop', 'Beendet den laufenden Testserver.', {}, async () => text(await stopServer()));

tool('balance',
    'Balance-Messung im Schnelllauf (scripts/balance.sh): Harzschnecken-Ertrag, Harzfallen, simulierter '
    + 'Stratolith-Kampf (3 Ausrüstungen × 2 Spielweisen). minutes = Spielminuten (60 dauern etwa 2 Minuten).',
    { version: oneVersion, minutes: z.number().int().min(1).max(240).default(30) },
    async ({ version, minutes }) => {
        checkVersions([version]);
        if (srv && srv.version === version) throw new Error('Testserver dieser Version läuft; erst server_stop.');
        const r = await run('scripts/balance.sh', [version, String(minutes)]);
        return text(r.out.split('\n').filter(l => /Strata-Balance|nicht fertig/.test(l) && !/Stand:/.test(l)).join('\n'));
    });

tool('perf',
    'Leistungsmessung (scripts/perf.sh): Tickdauer leer, mit 80 Zombies, 80 Strata-Mobs, 2 Transplantationen und '
    + 'jeder Strata-Art einzeln. Dauert etwa 3 Minuten.',
    { version: oneVersion },
    async ({ version }) => {
        checkVersions([version]);
        if (srv && srv.version === version) throw new Error('Testserver dieser Version läuft; erst server_stop.');
        const r = await run('scripts/perf.sh', [version]);
        return text(r.out.split('\n').filter(l => /Strata-Leistung|nicht fertig/.test(l)).join('\n'));
    });

tool('screenshot',
    'Startet den Client auf einem virtuellen Bildschirm in einer Epoche und gibt Screenshots zurück '
    + '(scripts/client-screenshot.sh). Voraussetzung: smoke_test lief vorher für diese Version (Welt). '
    + 'options sind die SHOT_*-Schalter des Skripts ohne Präfix, z. B. {"MOB": "strata_epochs:stratolith"}, '
    + '{"INVENTORY": "1"}, {"CROPS": "1"}, {"BIOME": "geode_vaults"}, {"GEAR": "1"}. Bildserien werden auf max_images gekürzt.',
    {
        version: oneVersion,
        epoch: z.enum(['crystal_age', 'spore_age', 'ash_age']).default('crystal_age'),
        options: z.record(z.string(), z.string()).default({}),
        max_images: z.number().int().min(1).max(8).default(3),
    },
    async ({ version, epoch, options, max_images }) => {
        checkVersions([version]);
        if (srv && srv.version === version) throw new Error('Testserver dieser Version läuft; erst server_stop.');
        const dir = fs.mkdtempSync(path.join(fs.realpathSync(process.env.TMPDIR || '/tmp'), 'strata-shot-'));
        const out = path.join(dir, 'shot.png');
        const env = Object.fromEntries(Object.entries(options).map(([k, v]) => [`SHOT_${k.toUpperCase().replace(/^SHOT_/, '')}`, v]));
        if (process.env.STRATA_MCP_PYTHON) env.PYTHON = process.env.STRATA_MCP_PYTHON;
        const r = await run('scripts/client-screenshot.sh', [version, epoch, out], { env });
        const files = fs.readdirSync(dir).filter(f => f.endsWith('.png')).sort();
        if (!files.length) throw new Error(`Kein Screenshot entstanden:\n${tail(r.out, 30)}`);
        const pick = files.length <= max_images ? files
            : Array.from({ length: max_images }, (_, i) => files[Math.round(i * (files.length - 1) / (max_images - 1 || 1))]);
        const content = [{ type: 'text', text: `${files.length} Bild(er) in ${dir}; gezeigt: ${pick.join(', ')}` }];
        for (const f of pick) content.push({ type: 'image', data: fs.readFileSync(path.join(dir, f)).toString('base64'), mimeType: 'image/png' });
        return { content };
    });

await server.connect(new StdioServerTransport());
log('MCP-Server läuft, Repo:', REPO);
