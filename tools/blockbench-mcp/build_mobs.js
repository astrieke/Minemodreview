/**
 * Baut die Mobs aus mobs/<id>/model.json über die MCP-Werkzeuge in Blockbench
 * (genau so, wie Claude es in der App tun würde) und speichert:
 *   mobs/<id>/<id>.bbmodel      Blockbench-Projekt
 *   mobs/<id>/shots/*.png       Ansichten für die Design-Prüfung
 *
 * Aufruf: node build_mobs.js [mob-id ...]     (Blockbench läuft dabei headless)
 * Vorher die Texturen malen: python3 tools/make_mob_textures.py
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { startHeadless } from './headless.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const mobsDir = path.join(here, '..', '..', 'mobs');
const PORT = 9398;
const ids = process.argv.slice(2).length ? process.argv.slice(2)
    : fs.readdirSync(mobsDir).filter(d => fs.existsSync(path.join(mobsDir, d, 'model.json')));

const client = new Client({ name: 'build-mobs', version: '0' });
await client.connect(new StdioClientTransport({
    command: process.execPath, args: [path.join(here, 'server.js')],
    env: { ...process.env, BLOCKBENCH_MCP_PORT: String(PORT) }, stderr: 'ignore',
}));
const { browser } = await startHeadless({ port: PORT });
const text = r => r.content.filter(c => c.type === 'text').map(c => c.text).join('\n');
const call = async (name, args = {}) => {
    const r = await client.callTool({ name, arguments: args });
    if (r.isError) throw new Error(`${name}: ${text(r)}`);
    return r;
};
for (let i = 0; i < 30; i++) {
    if (!(await client.callTool({ name: 'status', arguments: {} })).isError) break;
    await new Promise(r => setTimeout(r, 1000));
}

// Kamera aus der Modellgröße ableiten: Mob schaut nach -z (Norden).
function views(model) {
    let min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
    for (const c of model.cubes.filter(c => !c.preview)) for (let i = 0; i < 3; i++) {
        min[i] = Math.min(min[i], c.from[i]); max[i] = Math.max(max[i], c.to[i]);
    }
    const center = min.map((m, i) => (m + max[i]) / 2);
    const size = Math.max(...max.map((m, i) => m - min[i]));
    const dist = size * 1.25 + 10;
    const t = center;
    return {
        front: { position: [t[0], t[1] + 2, t[2] - dist], target: t },
        side: { position: [t[0] + dist, t[1] + 2, t[2]], target: t },
        back: { position: [t[0], t[1] + 4, t[2] + dist], target: t },
        iso: { position: [t[0] - dist * 0.65, t[1] + dist * 0.45, t[2] - dist * 0.65], target: t },
        // Animationsposen etwas weiter weg und höher gezielt: Sprünge und Aufblähen bleiben im Bild.
        anim: { position: [t[0] - dist * 0.85, t[1] + 3 + dist * 0.55, t[2] - dist * 0.85], target: [t[0], t[1] + 3, t[2]] },
    };
}

try {
    for (const id of ids) {
        const dir = path.join(mobsDir, id);
        const model = JSON.parse(fs.readFileSync(path.join(dir, 'model.json'), 'utf8'));
        const shots = path.join(dir, 'shots');
        // Alte Blockbench-Bilder löschen, Aufnahmen aus dem Spiel (ingame_*.png) aber behalten.
        fs.mkdirSync(shots, { recursive: true });
        for (const f of fs.readdirSync(shots)) {
            if (!f.startsWith('ingame_')) fs.rmSync(path.join(shots, f), { force: true });
        }

        await call('new_project', { format: 'modded_entity', name: id, texture_width: model.texture_size[0], texture_height: model.texture_size[1] });
        const strip = ({ material, face, back, preview, veins, tip, ...rest }) => rest;   // Malhinweise nicht an Blockbench schicken
        await call('build_model', { bones: model.bones, cubes: model.cubes.map(strip) });
        await call('set_texture', { name: id, png_path: path.join(dir, 'texture.png') });
        for (const a of model.animations || []) await call('add_animation', a);

        const v = views(model);
        for (const [name, view] of Object.entries(v).filter(([n]) => n !== 'anim')) {
            await call('screenshot', { view, width: 512, height: 512, save_to: path.join(shots, `${name}.png`) });
        }
        // Animationsposen fürs Prüfblatt, schräg von vorne: je Animation die Pose am größten Ausschlag
        // (tools/anim_peaks.py), bei Schleifen zusätzlich der Anfang (z. B. die Landung beim Hüpfen).
        const peaks = JSON.parse(execFileSync('python3', [path.join(here, '..', 'anim_peaks.py'), '--json', id],
            { encoding: 'utf8' }));
        for (const a of model.animations || []) {
            const short = a.name.split('.').pop();
            const poses = [[`anim_${short}`, peaks[short] ?? a.length / 2]];
            if (a.loop === 'loop') poses.push([`anim_${short}_start`, 0]);
            for (const [file, time] of poses) {
                await call('screenshot', { view: v.anim, width: 384, height: 384, animation: a.name, time,
                    save_to: path.join(shots, `${file}.png`) });
            }
        }
        await call('export', { format: 'bbmodel', save_to: path.join(dir, `${id}.bbmodel`) });
        const status = JSON.parse(text(await call('status')));
        console.log(`${id}: ${status.project.bones} Knochen, ${status.project.cubes} Würfel, ${status.project.animations} Animationen`);
    }
} finally {
    await browser.close();
    await client.close();
}
