/**
 * End-to-End-Test: startet den MCP-Server wie Claude es tun würde (stdio), startet Blockbench
 * headless mit dem Plugin und baut über MCP-Werkzeuge ein kleines Testwesen.
 * Ergebnisse landen in test/out/ (Screenshot, .bbmodel, Java-Modell).
 */
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { startHeadless } from '../headless.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(here, 'out');
fs.mkdirSync(out, { recursive: true });
const PORT = 9399;

// Kleiner PNG-Encoder für eine Testtextur (64x64, Streifen), damit der Test ohne Zusatzpakete läuft.
function png(width, height, pixel) {
    const raw = Buffer.alloc((width * 4 + 1) * height);
    for (let y = 0; y < height; y++) {
        raw[y * (width * 4 + 1)] = 0;
        for (let x = 0; x < width; x++) {
            const [r, g, b, a] = pixel(x, y);
            raw.set([r, g, b, a], y * (width * 4 + 1) + 1 + x * 4);
        }
    }
    const crcTable = Array.from({ length: 256 }, (_, n) => {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        return c >>> 0;
    });
    const crc = buf => { let c = 0xffffffff; for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
    const chunk = (type, data) => {
        const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
        const td = Buffer.concat([Buffer.from(type), data]);
        const c = Buffer.alloc(4); c.writeUInt32BE(crc(td));
        return Buffer.concat([len, td, c]);
    };
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr[8] = 8; ihdr[9] = 6;
    return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr),
        chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

const checks = [];
const check = (name, ok, detail = '') => { checks.push({ name, ok }); console.log(`${ok ? 'OK    ' : 'FEHLER'} ${name}${detail ? ' – ' + detail : ''}`); };
const textOf = r => r.content.filter(c => c.type === 'text').map(c => c.text).join('\n');

const client = new Client({ name: 'e2e', version: '0' });
await client.connect(new StdioClientTransport({
    command: process.execPath, args: [path.join(here, '..', 'server.js')],
    env: { ...process.env, BLOCKBENCH_MCP_PORT: String(PORT) }, stderr: 'inherit',
}));
const { browser } = await startHeadless({ port: PORT });

const call = async (name, args = {}) => {
    const r = await client.callTool({ name, arguments: args });
    if (r.isError) throw new Error(`${name}: ${textOf(r)}`);
    return r;
};

try {
    // warten, bis das Plugin verbunden ist
    for (let i = 0; i < 30; i++) {
        const r = await client.callTool({ name: 'status', arguments: {} });
        if (!r.isError) break;
        await new Promise(res => setTimeout(res, 1000));
    }
    const tools = (await client.listTools()).tools.map(t => t.name);
    check('Werkzeuge registriert', tools.includes('build_model') && tools.includes('screenshot'), tools.join(', '));

    await call('new_project', { format: 'modded_entity', name: 'testwesen', texture_width: 64, texture_height: 64 });
    await call('build_model', {
        bones: [
            { name: 'body', origin: [0, 10, 0] },
            { name: 'head', parent: 'body', origin: [0, 16, -4] },
            { name: 'leg_left', origin: [2, 6, 0] },
            { name: 'leg_right', origin: [-2, 6, 0] },
        ],
        cubes: [
            { name: 'torso', bone: 'body', from: [-4, 6, -4], to: [4, 14, 6], uv_offset: [0, 0] },
            { name: 'skull', bone: 'head', from: [-3, 14, -9], to: [3, 20, -3], uv_offset: [0, 18] },
            { name: 'leg_l', bone: 'leg_left', from: [1, 0, -1], to: [4, 6, 2], uv_offset: [36, 0] },
            { name: 'leg_r', bone: 'leg_right', from: [-4, 0, -1], to: [-1, 6, 2], uv_offset: [36, 0], mirror_uv: true },
        ],
    });
    const outline = JSON.parse(textOf(await call('outline')));
    check('Modellaufbau', outline.outliner.length === 3 && outline.uv_layout.length === 4,
        `${outline.outliner.length} Wurzelknochen, ${outline.uv_layout.length} Würfel`);

    const texture = png(64, 64, (x, y) => ((x >> 2) + (y >> 2)) % 2 ? [122, 92, 192, 255] : [70, 60, 90, 255]);
    fs.writeFileSync(path.join(out, 'texture.png'), texture);
    await call('set_texture', { name: 'testwesen', png_path: path.join(out, 'texture.png') });

    await call('add_animation', {
        name: 'animation.testwesen.walk', length: 1, loop: 'loop',
        keyframes: [
            { bone: 'leg_left', time: 0, value: [25, 0, 0] },
            { bone: 'leg_left', time: 0.5, value: [-25, 0, 0] },
            { bone: 'leg_left', time: 1, value: [25, 0, 0] },
            { bone: 'leg_right', time: 0, value: [-25, 0, 0] },
            { bone: 'leg_right', time: 0.5, value: [25, 0, 0] },
            { bone: 'leg_right', time: 1, value: [-25, 0, 0] },
        ],
    });
    const status = JSON.parse(textOf(await call('status')));
    check('Textur und Animation', status.project.textures === 1 && status.project.animations === 1, JSON.stringify(status.project));

    const shot = await call('screenshot', { view: 'iso', save_to: path.join(out, 'screenshot_iso.png') });
    const image = shot.content.find(c => c.type === 'image');
    check('Screenshot', image && Buffer.from(image.data, 'base64').length > 2000, image ? `${Buffer.from(image.data, 'base64').length} Bytes` : 'kein Bild');
    const imgAt = async (time, file) => (await call('screenshot', {
        view: 'side', animation: 'animation.testwesen.walk', time, save_to: path.join(out, file),
    })).content.find(c => c.type === 'image').data;
    const pose0 = await imgAt(0, 'screenshot_walk_0.png');
    const pose5 = await imgAt(0.5, 'screenshot_walk_05.png');
    check('Animation wirkt im Screenshot', pose0 !== pose5, 'Bilder bei 0 s und 0,5 s unterscheiden sich');
    await call('add_cube', { name: 'tail', bone: 'body', from: [-1, 10, 6], to: [1, 12, 10], uv_offset: [36, 10] });
    check('Nach Animations-Screenshot wieder editierbar', JSON.parse(textOf(await call('status'))).project.cubes === 5);
    await call('delete_element', { name: 'tail' });

    await call('export', { format: 'bbmodel', save_to: path.join(out, 'testwesen.bbmodel') });
    const bb = JSON.parse(fs.readFileSync(path.join(out, 'testwesen.bbmodel'), 'utf8'));
    check('Export .bbmodel', bb.elements && bb.elements.length === 4 && bb.animations && bb.animations.length === 1,
        `${bb.elements?.length} Elemente, ${bb.animations?.length} Animationen`);

    await call('export', { format: 'modded_entity', save_to: path.join(out, 'TestwesenModel.java') });
    const java = fs.readFileSync(path.join(out, 'TestwesenModel.java'), 'utf8');
    check('Export Java (Modded Entity)', /class\s+\w+/.test(java) && java.includes('leg_left'), `${java.split('\n').length} Zeilen`);

    await call('import_bbmodel', { path: path.join(out, 'testwesen.bbmodel') });
    const reloaded = JSON.parse(textOf(await call('status')));
    check('Re-Import .bbmodel', reloaded.project.cubes === 4, JSON.stringify(reloaded.project));

    // Fremde Webseiten dürfen sich nicht als Plugin ausgeben.
    const { default: WebSocket } = await import('ws');
    const rejected = await new Promise(resolve => {
        const ws = new WebSocket(`ws://127.0.0.1:${PORT}`, { origin: 'https://evil.example' });
        ws.on('open', () => { ws.close(); resolve(false); });
        ws.on('error', () => resolve(true));
    });
    check('Fremde Origin wird abgewiesen', rejected);

    const err = await client.callTool({ name: 'add_cube', arguments: { name: 'x', bone: 'gibtsnicht', from: [0, 0, 0], to: [1, 1, 1] } });
    check('Fehlermeldung bei falschem Knochen', err.isError && textOf(err).includes('nicht gefunden'), textOf(err));
} finally {
    await browser.close();
    await client.close();
}

const failed = checks.filter(c => !c.ok).length;
console.log(failed ? `\n${failed} Prüfung(en) fehlgeschlagen` : `\nAlle ${checks.length} Prüfungen bestanden`);
process.exit(failed ? 1 : 0);
