#!/usr/bin/env node
/**
 * Startet Blockbench (Web-Version) ohne Bildschirm in Chromium und lädt das Plugin „Claude Bridge“.
 * Gedacht für Umgebungen ohne Desktop (Cloud, CI). Am eigenen Rechner einfach Blockbench
 * öffnen und das Plugin dort laden.
 *
 * Aufruf: node headless.js [--port 9333] [--url https://web.blockbench.net/]
 * Benötigt Playwright mit Chromium (require('playwright')).
 */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const here = path.dirname(fileURLToPath(import.meta.url));
const arg = (name, fallback) => {
    const i = process.argv.indexOf(name);
    return i >= 0 ? process.argv[i + 1] : fallback;
};
const port = Number(arg('--port', process.env.BLOCKBENCH_MCP_PORT || 9333));
const url = arg('--url', 'https://web.blockbench.net/');

let playwright;
try {
    playwright = require('playwright');
} catch {
    playwright = require(path.join(process.env.NODE_PATH || '/usr/lib/node_modules', 'playwright'));
}

export async function startHeadless({ port: p = port, url: u = url } = {}) {
    const browser = await playwright.chromium.launch({ args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] });
    const page = await browser.newPage({ viewport: { width: 1280, height: 860 } });
    page.on('pageerror', e => console.error('[headless] Seitenfehler:', e.message));
    await page.goto(u, { waitUntil: 'load', timeout: 120_000 });
    await page.waitForFunction(() => window.Blockbench && window.Formats && Object.keys(Formats).length > 3, null, { timeout: 120_000 });
    await page.addScriptTag({ content: fs.readFileSync(path.join(here, 'plugin', 'claude_bridge.js'), 'utf8') });
    await page.evaluate(portNumber => window.ClaudeBridge.start(portNumber), p);
    console.error(`[headless] Blockbench ${await page.evaluate(() => Blockbench.version)} läuft, Plugin verbindet sich mit Port ${p}`);
    return { browser, page };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
    const { browser } = await startHeadless();
    const shutdown = async () => { await browser.close(); process.exit(0); };
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
}
