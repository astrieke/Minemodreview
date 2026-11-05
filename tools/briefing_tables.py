"""Schreibt die Würfel-Tabellen, Texturgröße und Umfang der Mobs in docs/BRIEFING.md neu aus mobs/<id>/model.json.

Aufruf: python3 tools/briefing_tables.py

Erwartet pro Mob einen Abschnitt "### … (`<id>`)" mit den Zeilen "| Textur | …", "| Umfang | …"
und einer Tabelle, die mit "| Würfel | Knochen | …" beginnt.
"""
import json
import os
import re

HERE = os.path.dirname(__file__)
BRIEFING = os.path.join(HERE, '..', 'docs', 'BRIEFING.md')
MOBS = os.path.join(HERE, '..', 'mobs')


def num(v):
    return str(int(v)) if float(v).is_integer() else str(v)


def vec(v):
    return '[' + ', '.join(num(x) for x in v) + ']'


def cube_rows(model):
    rows = []
    for c in model['cubes']:
        uv = vec(c['uv_offset']) + (' (gespiegelt)' if c.get('mirror_uv') else '')
        rot = vec(c['rotation']) if c.get('rotation') else '–'
        rows.append(f"| `{c['name']}` | `{c['bone']}` | {vec(c['from'])} | {vec(c['to'])} | {uv} | {c['material']} | {rot} |")
    return rows


def update(lines, mob_id, model):
    start = next(i for i, l in enumerate(lines) if l.startswith('### ') and f'(`{mob_id}`)' in l)
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith('### ')), len(lines))
    w, h = model['texture_size']
    for i in range(start, end):
        if lines[i].startswith('| Textur |'):
            lines[i] = f'| Textur | {w}×{h}, Box-UV |'
        elif lines[i].startswith('| Umfang |'):
            lines[i] = (f"| Umfang | {len(model['bones'])} Knochen, {len(model['cubes'])} Würfel, "
                        f"{len(model.get('animations', []))} Animationen |")
        elif lines[i].startswith('| Würfel | Knochen |'):
            first = i + 2                                   # nach Kopf- und Trennzeile
            last = first
            while last < end and lines[last].startswith('|'):
                last += 1
            lines[first:last] = cube_rows(model)
            break
    return lines


def main():
    with open(BRIEFING, encoding='utf-8') as f:
        text = f.read()
    lines = text.split('\n')
    for mob_id in sorted(os.listdir(MOBS)):
        path = os.path.join(MOBS, mob_id, 'model.json')
        if os.path.isfile(path) and re.search(rf'\(`{mob_id}`\)', text):
            with open(path, encoding='utf-8') as f:
                lines = update(lines, mob_id, json.load(f))
            print('Briefing aktualisiert:', mob_id)
    with open(BRIEFING, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines))


if __name__ == '__main__':
    main()
