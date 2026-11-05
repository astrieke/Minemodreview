#!/usr/bin/env node
/**
 * Blockbench-MCP-Server
 *
 * Claude startet diesen Server über MCP (stdio). Der Server öffnet einen WebSocket auf
 * 127.0.0.1:<BLOCKBENCH_MCP_PORT> (Standard: erster freier aus 9333–9336). Das Blockbench-Plugin „Claude Bridge“
 * (plugin/claude_bridge.js) verbindet sich damit und führt die Befehle in Blockbench aus.
 *
 * Umgebungsvariablen:
 *   BLOCKBENCH_MCP_PORT        Port für das Plugin (Standard 9333)
 *   BLOCKBENCH_MCP_ALLOW_EVAL  "1" schaltet das Werkzeug eval_js frei (beliebiger JS-Code in Blockbench)
 */
import fs from 'node:fs';
import path from 'node:path';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { WebSocketServer } from 'ws';
import { z } from 'zod';

// Ohne BLOCKBENCH_MCP_PORT nimmt der Server den ersten freien Port aus 9333–9336, denn die
// Claude-App startet ihn oft mehrfach. Das Plugin verbindet sich mit allen vier Ports.
const FIXED_PORT = process.env.BLOCKBENCH_MCP_PORT ? Number(process.env.BLOCKBENCH_MCP_PORT) : null;
const PORTS = FIXED_PORT ? [FIXED_PORT] : [9333, 9334, 9335, 9336];
let PORT = PORTS[0];
const ALLOW_EVAL = process.env.BLOCKBENCH_MCP_ALLOW_EVAL === '1';
const TIMEOUT_MS = 60_000;

// stdout gehört dem MCP-Protokoll; Logs nur nach stderr.
const log = (...args) => console.error('[blockbench-mcp]', ...args);

// ------------------------------------------------------------------ Brücke zum Plugin

let plugin = null;
let nextId = 1;
const pending = new Map();

// Nur Blockbench selbst darf sich verbinden: Desktop-App (kein Origin, file:// oder app://)
// oder die offizielle Web-Version. Andere Webseiten im Browser werden abgewiesen.
const ALLOWED_ORIGINS = [/^$/, /^null$/, /^file:\/\//, /^app:\/\//, /^https:\/\/web\.blockbench\.net$/,
    /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/];
// Ist ein Port belegt (z. B. vom Server eines zweiten Claude-Fensters), kommt der nächste dran.
// Sind alle belegt, wird es nach 2 Sekunden von vorn versucht.
function listen(i = 0) {
    PORT = PORTS[i];
    const wss = new WebSocketServer({
        host: '127.0.0.1',
        port: PORT,
        verifyClient: ({ origin }) => {
            const ok = ALLOWED_ORIGINS.some(re => re.test(origin || ''));
            if (!ok) log('Verbindung abgelehnt von Origin', origin);
            return ok;
        },
    });
    wss.on('listening', () => log(`wartet auf Blockbench-Plugin auf ws://127.0.0.1:${PORT}`));
    wss.on('error', e => {
        if (e.code === 'EADDRINUSE') {
            wss.close();
            if (i + 1 < PORTS.length) {
                listen(i + 1);
            } else {
                log(`Ports ${PORTS.join(', ')} sind belegt, neuer Versuch in 2 s`);
                setTimeout(listen, 2000);
            }
        } else {
            log('WebSocket-Fehler:', e.message);
        }
    });
    wss.on('connection', onConnection);
}
listen();

function onConnection(socket) {
    if (plugin) plugin.close();
    plugin = socket;
    log('Blockbench verbunden');
    socket.on('message', data => {
        let msg;
        try {
            msg = JSON.parse(data.toString());
        } catch {
            return;
        }
        if (msg.type === 'hello') {
            log('Blockbench-Version', msg.blockbench);
            return;
        }
        const entry = pending.get(msg.id);
        if (!entry) return;
        pending.delete(msg.id);
        clearTimeout(entry.timer);
        if (msg.ok) entry.resolve(msg.result);
        else entry.reject(new Error(msg.error));
    });
    socket.on('close', () => {
        if (plugin === socket) plugin = null;
        log('Blockbench getrennt');
    });
}

function call(command, args = {}) {
    if (!plugin || plugin.readyState !== 1) {
        return Promise.reject(new Error(
            'Blockbench ist nicht verbunden. Blockbench öffnen und das Plugin „Claude Bridge“ laden ' +
            `(Datei > Plugins > Load Plugin from File > tools/blockbench-mcp/plugin/claude_bridge.js). Port: ${PORT}.`));
    }
    const id = nextId++;
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            pending.delete(id);
            reject(new Error(`Zeitüberschreitung bei ${command}`));
        }, TIMEOUT_MS);
        pending.set(id, { resolve, reject, timer });
        plugin.send(JSON.stringify({ id, command, args }));
    });
}

// ------------------------------------------------------------------ MCP-Werkzeuge

const server = new McpServer({ name: 'blockbench', version: '0.1.0' });

const text = value => ({ content: [{ type: 'text', text: typeof value === 'string' ? value : JSON.stringify(value, null, 2) }] });
const fail = e => ({ isError: true, content: [{ type: 'text', text: String(e.message || e) }] });
const vec3 = z.array(z.number()).length(3);

function tool(name, description, schema, handler) {
    server.registerTool(name, { description, inputSchema: schema }, async args => {
        try {
            return await handler(args);
        } catch (e) {
            return fail(e);
        }
    });
}

function writeFile(target, content, encoding) {
    const resolved = path.resolve(target);
    fs.mkdirSync(path.dirname(resolved), { recursive: true });
    fs.writeFileSync(resolved, content, encoding);
    return resolved;
}

tool('status', 'Zeigt, ob Blockbench verbunden ist, und Infos zum offenen Projekt (Knochen, Würfel, Texturen, Animationen).',
    {}, async () => text(await call('status')));

tool('new_project',
    'Legt ein neues Blockbench-Projekt an. Für Minecraft-Java-Mods (NeoForge/Fabric) ist "modded_entity" richtig. ' +
    'Koordinaten sind Blockbench-Pixel (16 = 1 Block), y nach oben, Füße bei y=0.',
    {
        format: z.string().default('modded_entity').describe('z. B. modded_entity, java_block, bedrock, free'),
        name: z.string().default('model'),
        texture_width: z.number().int().default(64),
        texture_height: z.number().int().default(64),
        box_uv: z.boolean().default(true).describe('Box-UV wie bei Vanilla-Mobs (uv_offset pro Würfel)'),
    },
    async args => text(await call('new_project', args)));

tool('add_bone', 'Fügt einen Knochen (Gruppe) hinzu. Knochen sind die beweglichen Teile für Animationen.',
    {
        name: z.string(),
        parent: z.string().optional().describe('Name des Elternknochens'),
        origin: vec3.optional().describe('Drehpunkt [x, y, z]'),
        rotation: vec3.optional().describe('Grundrotation in Grad'),
    },
    async args => text(await call('add_bone', args)));

tool('add_cube', 'Fügt einen Würfel hinzu, optional in einem Knochen.',
    {
        name: z.string(),
        bone: z.string().optional(),
        from: vec3.describe('untere Ecke [x, y, z]'),
        to: vec3.describe('obere Ecke [x, y, z]'),
        uv_offset: z.array(z.number()).length(2).optional().describe('Box-UV-Startpunkt auf der Textur [u, v]'),
        inflate: z.number().optional(),
        rotation: vec3.optional(),
        origin: vec3.optional().describe('Drehpunkt; Standard: Ursprung des Knochens'),
        mirror_uv: z.boolean().optional(),
    },
    async args => text(await call('add_cube', args)));

tool('build_model',
    'Baut viele Knochen und Würfel in einem Aufruf (schneller als einzeln). Knochen werden vor den Würfeln angelegt, in der angegebenen Reihenfolge.',
    {
        bones: z.array(z.object({
            name: z.string(), parent: z.string().optional(), origin: vec3.optional(), rotation: vec3.optional(),
        })).default([]),
        cubes: z.array(z.object({
            name: z.string(), bone: z.string().optional(), from: vec3, to: vec3,
            uv_offset: z.array(z.number()).length(2).optional(), inflate: z.number().optional(),
            rotation: vec3.optional(), origin: vec3.optional(), mirror_uv: z.boolean().optional(),
        })).default([]),
    },
    async ({ bones, cubes }) => {
        for (const b of bones) await call('add_bone', b);
        for (const c of cubes) await call('add_cube', c);
        return text(await call('status'));
    });

tool('update_element', 'Ändert einen Knochen oder Würfel (Position, Größe, Rotation, UV, Name, Elternknochen).',
    {
        name: z.string(),
        new_name: z.string().optional(),
        parent: z.string().optional(),
        from: vec3.optional(), to: vec3.optional(), origin: vec3.optional(), rotation: vec3.optional(),
        uv_offset: z.array(z.number()).length(2).optional(), inflate: z.number().optional(), mirror_uv: z.boolean().optional(),
    },
    async args => text(await call('update_element', args)));

tool('delete_element', 'Löscht einen Knochen oder Würfel.', { name: z.string() },
    async args => text(await call('delete_element', args)));

tool('outline',
    'Liefert den Aufbau des Modells (Knochen-Baum mit Würfeln) und das UV-Layout: wo jede Würfelseite auf der Textur liegt.',
    {}, async () => text(await call('outline')));

tool('set_texture', 'Lädt eine PNG-Textur ins Projekt (aus einer Datei oder als Base64) und weist sie allen Würfeln zu.',
    {
        name: z.string().default('texture'),
        png_path: z.string().optional().describe('Pfad zu einer PNG-Datei auf diesem Rechner'),
        png_base64: z.string().optional(),
    },
    async ({ name, png_path, png_base64 }) => {
        const data = png_base64 || (png_path ? fs.readFileSync(png_path).toString('base64') : null);
        if (!data) throw new Error('png_path oder png_base64 angeben');
        return text(await call('set_texture', { name, png_base64: data }));
    });

tool('get_texture', 'Liest eine Textur aus Blockbench und speichert sie optional als PNG.',
    { name: z.string().optional(), save_to: z.string().optional() },
    async ({ name, save_to }) => {
        const result = await call('get_texture', { name });
        if (save_to) {
            const file = writeFile(save_to, Buffer.from(result.png_base64, 'base64'));
            return text({ name: result.name, saved: file });
        }
        return { content: [{ type: 'image', data: result.png_base64, mimeType: 'image/png' }] };
    });

tool('add_animation',
    'Legt eine Animation mit Keyframes an. channel: rotation (Grad), position (Pixel) oder scale. time in Sekunden.',
    {
        name: z.string().describe('z. B. animation.zeitschwund.walk'),
        length: z.number().default(1),
        loop: z.enum(['once', 'hold', 'loop']).default('loop'),
        keyframes: z.array(z.object({
            bone: z.string(),
            channel: z.enum(['rotation', 'position', 'scale']).default('rotation'),
            time: z.number(),
            value: vec3,
            interpolation: z.enum(['linear', 'catmullrom', 'step', 'bezier']).optional(),
        })),
    },
    async args => text(await call('add_animation', args)));

tool('screenshot',
    'Macht ein Bild vom Modell und gibt es zurück, damit man das Ergebnis sehen kann. Optional mit Animation zu einem Zeitpunkt.',
    {
        view: z.union([
            z.enum(['iso', 'front', 'side', 'back', 'top']),
            z.object({ position: vec3, target: vec3 }),
        ]).default('iso'),
        width: z.number().int().default(512),
        height: z.number().int().default(512),
        animation: z.string().optional(),
        time: z.number().optional(),
        save_to: z.string().optional().describe('zusätzlich als PNG speichern'),
    },
    async ({ save_to, ...args }) => {
        const result = await call('screenshot', args);
        const content = [{ type: 'image', data: result.png_base64, mimeType: 'image/png' }];
        if (save_to) {
            const file = writeFile(save_to, Buffer.from(result.png_base64, 'base64'));
            content.push({ type: 'text', text: `gespeichert: ${file}` });
        }
        return { content };
    });

tool('export',
    'Exportiert das Modell. bbmodel = Blockbench-Projekt; modded_entity = Java-Code für Mod-Entities; ' +
    'außerdem java_block, bedrock, gltf, obj. Mit save_to wird die Datei geschrieben.',
    { format: z.string().default('bbmodel'), save_to: z.string().optional() },
    async ({ format, save_to }) => {
        const result = await call('export', { format });
        if (save_to) return text({ format, saved: writeFile(save_to, result.content, 'utf8') });
        return text(result.content);
    });

tool('import_bbmodel', 'Öffnet eine vorhandene .bbmodel-Datei in Blockbench, um sie weiter zu bearbeiten.',
    { path: z.string() },
    async ({ path: file }) => text(await call('import_bbmodel', { content: fs.readFileSync(file, 'utf8') })));

if (ALLOW_EVAL) {
    tool('eval_js', 'Führt JavaScript in Blockbench aus (Notausgang für alles, was die anderen Werkzeuge nicht können).',
        { code: z.string().describe('Funktionskörper; der Rückgabewert wird als JSON zurückgegeben') },
        async args => text(await call('eval_js', args)));
}

await server.connect(new StdioServerTransport());
log('MCP-Server läuft');
