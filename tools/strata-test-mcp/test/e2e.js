// Ende-zu-Ende-Test: startet den MCP-Server wie Claude es tut (stdio), startet darüber einen Testserver,
// schickt Befehle per RCON, liest das Log und beendet ihn wieder.
// Aufruf: node test/e2e.js [version]   (Standard 1.21.1; dauert etwa 2 Minuten)
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const version = process.argv[2] || '1.21.1';
const client = new Client({ name: 'e2e', version: '1' });
await client.connect(new StdioClientTransport({ command: 'node', args: [path.join(here, '..', 'server.js')] }));

let failures = 0;
async function call(name, args = {}, check = () => true) {
    const r = await client.callTool({ name, arguments: args });
    const textOut = r.content.filter(c => c.type === 'text').map(c => c.text).join('\n');
    const ok = !r.isError && check(textOut);
    if (!ok) failures++;
    console.log(`${ok ? 'OK    ' : 'FEHLER'} ${name} ${JSON.stringify(args)}\n${textOut.slice(0, 800)}\n`);
    return textOut;
}

const tools = (await client.listTools()).tools.map(t => t.name);
console.log('Werkzeuge:', tools.join(', '));
await call('versions', {}, t => t.includes(version));
await call('server_start', { version }, t => t.includes('"started"') && /Fertig, 0 Fehler/.test(t));
await call('server_command', { command: 'list' }, t => /players online|Spieler online/.test(t));
await call('server_command', { command: 'execute in strata_epochs:crystal_age run summon strata_epochs:geode_crawler 0 80 0' },
    t => /Summoned|beschworen|erzeugt/i.test(t));
await call('server_command', { command: 'strata codex validate' }, t => /in Ordnung/.test(t));
await call('server_log', { grep: 'Strata-Test\\] Fertig', lines: 5 }, t => /Fertig, 0 Fehler/.test(t));
await call('server_stop', {}, t => t.includes('beendet'));
await client.close();
console.log(failures ? `${failures} Fehler` : 'Alles OK');
process.exit(failures ? 1 : 0);
