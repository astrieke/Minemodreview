"""Malt die Mob-Texturen passend zu den Bauplänen in mobs/<id>/model.json.

Aufruf: python3 tools/make_mob_textures.py [mob-id ...]

Jeder Würfel hat ein Material. Der Maler kennt das Box-UV-Layout von Minecraft und bemalt
jede Würfelseite an der richtigen Stelle: oben heller, unten dunkler, Seiten mit leichtem
Verlauf. Danach kommen Details pro Mob (Augen, Schuppen, Schichten, Sanduhr, Ammonit).
"""
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'mobs')


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3]) + (255,)


# Paletten von dunkel nach hell
MATERIALS = {
    'hide': ['#2c2638', '#3d3450', '#4f4565', '#62577a', '#77699a'],
    'hide_dark': ['#221d2c', '#2f2839', '#3d3450', '#4c4262', '#5c5174'],
    'belly': ['#6b5f73', '#857888', '#9e919a', '#b5a9ae', '#c9bfc0'],
    'calcite': ['#8c8578', '#aaa394', '#c5beaf', '#dcd6c8', '#eeeadf'],
    'quartz': ['#2c2670', '#4a43b8', '#6f6cf0', '#a9a8ff', '#e6e6ff'],
    'sack': ['#6d6a4a', '#8a875f', '#a7a57a', '#c2c095', '#d8d6b0'],
    'cap': ['#284a22', '#3a6331', '#4f7d42', '#679a56', '#86b870'],
    'root': ['#3e2a18', '#563b22', '#6f4e2e', '#8a663e', '#a5804f'],
    'stone': ['#4a4152', '#5e5468', '#746a7c', '#8a7f8c', '#a0959c'],
    'stone_dark': ['#352e3d', '#463d50', '#5a5065', '#6d6377', '#82778a'],
    'gold': ['#5a3d08', '#9c6a12', '#d9a43a', '#f2c45a', '#ffe08a'],
}


def faces(cube):
    """Box-UV-Rechtecke (x0, y0, w, h) je Seite. 'north' ist vorne (Mobs schauen nach -z)."""
    w = math.ceil(cube['to'][0] - cube['from'][0])
    h = math.ceil(cube['to'][1] - cube['from'][1])
    d = math.ceil(cube['to'][2] - cube['from'][2])
    u, v = cube['uv_offset']
    return {
        'up': (u + d, v, w, d),
        'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h),
        'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h),
        'south': (u + 2 * d + w, v + d, w, h),
    }


FACE_LIGHT = {'up': 1.12, 'north': 1.0, 'east': 0.93, 'west': 0.93, 'south': 0.88, 'down': 0.72}


def paint_face(img, rect, pal, face, rnd, pattern):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            # Grundton aus der Palette (Mitte), etwas Rauschen, Verlauf auf Seitenflächen
            v = 2 + rnd.uniform(-0.6, 0.6)
            if face not in ('up', 'down') and h > 2:
                v += 0.5 * (1 - y / (h - 1)) - 0.25          # oben heller, unten dunkler
            v = pattern(x, y, w, h, face, v)
            c = rgb(pal[max(0, min(4, int(round(v))))])
            img.putpixel((x0 + x, y0 + y), shade(c, FACE_LIGHT[face]))
        # dunkle Unterkante an Seitenflächen (Bodenschatten)
    if face not in ('up', 'down') and h > 3:
        for x in range(w):
            px = img.getpixel((x0 + x, y0 + h - 1))
            img.putpixel((x0 + x, y0 + h - 1), shade(px, 0.82))


# ------------------------------------------------------------------ Muster

def flat(x, y, w, h, face, v):
    return v


def scales(x, y, w, h, face, v):
    # versetzte Schuppenreihen: jede 2. Zeile dunkler Bogen
    if (y % 3 == 2) and ((x + (y // 3)) % 3 != 0):
        return v - 1.2
    if (y % 3 == 0) and ((x + (y // 3)) % 3 == 1):
        return v + 0.7
    return v


def strata(x, y, w, h, face, v):
    # waagrechte Gesteinsschichten mit Fugen
    band = (y // 3) % 3
    v += [0.4, -0.2, 0.1][band]
    if y % 3 == 0 and (x * 7 + y) % 5 != 0:
        v -= 1.0
    return v


def pores(x, y, w, h, face, v):
    if (x * 5 + y * 3) % 7 == 0 and (x + y) % 2 == 0:
        return 0.4
    return v


def cap_pattern(x, y, w, h, face, v):
    if face == 'down':                                   # Lamellen unter dem Hut
        return 1.0 if x % 2 == 0 else 1.8
    return v


def crystal(x, y, w, h, face, v):
    if face in ('up',):
        return 4
    return 3 if x == 0 else 2 if x < w - 1 else 1


PATTERNS = {'hide': scales, 'hide_dark': scales, 'stone': strata, 'stone_dark': strata,
            'sack': pores, 'cap': cap_pattern, 'quartz': crystal}


# ------------------------------------------------------------------ Details pro Mob

def px(img, rect, x, y, color):
    x0, y0, w, h = rect
    if 0 <= x < w and 0 <= y < h:
        img.putpixel((x0 + x, y0 + y), rgb(color))


def detail_lizard(img, f):
    # Augen seitlich am Kopf, nahe vorne; Nüstern vorne auf der Schnauze kommen bei 'snout'
    for side in ('east', 'west'):
        x0, y0, w, h = f[side]
        ex = 1 if side == 'west' else w - 2               # Seite zur Schnauze hin
        px(img, f[side], ex, 1, '#ffd27a')
        px(img, f[side], ex, 2, '#2a1a05')
    # dunkle Stirnzeichnung
    for x in range(1, f['up'][2] - 1):
        if x % 2:
            px(img, f['up'], x, 1, '#2c2638')


def detail_spore(img, f):
    r = f['north']
    for x, y in [(2, 2), (5, 2)]:                        # zwei glimmende Bernsteinaugen
        px(img, r, x, y, '#ffb847')
        px(img, r, x, y + 1, '#c06a12')
    for x in range(2, 6):                                # Atemöffnung
        px(img, r, x, 5, '#3a3a22')


def detail_witness(img, f):
    r = f['north']
    for x in range(0, r[2]):
        px(img, r, x, 1, '#352e3d')                      # Stirnkante
    for x in (1, 4):                                     # glühende Augenschlitze
        px(img, r, x, 2, '#ffe08a')
        px(img, r, x + 1, 2, '#d9a43a')
    px(img, r, 2, 4, '#352e3d')                          # Mundriss
    px(img, r, 3, 4, '#352e3d')
    px(img, r, 3, 5, '#352e3d')


def detail_hourglass(img, f):
    r = f['north']                                       # 4 x 6
    rows = ['GGGG', '.vv.', '..v.', '.v..', '.vv.', 'GGGG']
    pal = {'G': '#ffe08a', 'v': '#b48cff', '.': '#3a2c5c'}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            px(img, r, x, y, pal[ch])


def detail_ammonite(img, f):
    r = f['south']
    cx, cy, rad = r[2] / 2, r[3] / 2, min(r[2], r[3]) / 2 - 1
    for y in range(r[3]):
        for x in range(r[2]):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
    # Spiralrille wie beim versteinerten Stein
            rr = math.hypot(dx, dy)
            if rr > rad:
                continue
            phase = (rr / 2.0 - (math.atan2(dy, dx) / (2 * math.pi)) % 1.0) % 1.0
            if rr > rad - 0.8 or phase < 0.3:
                px(img, r, x, y, '#4a4152')
            elif dx + dy < 0:
                px(img, r, x, y, '#a0959c')


def detail_snout(img, f):
    r = f['north']
    px(img, r, 1, 1, '#16121c')
    px(img, r, r[2] - 2, 1, '#16121c')


DETAILS = {'lizard': detail_lizard, 'spore': detail_spore, 'witness': detail_witness,
           'hourglass': detail_hourglass}


def paint(mob_id):
    path = os.path.join(ROOT, mob_id, 'model.json')
    model = json.load(open(path))
    w, h = model['texture_size']
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    rnd = random.Random(mob_id)
    painted = set()
    for cube in model['cubes']:
        key = tuple(cube['uv_offset'])
        f = faces(cube)
        if key not in painted:                            # gemeinsame UV (gespiegelte Teile) nur einmal malen
            painted.add(key)
            pal = MATERIALS[cube['material']]
            pattern = PATTERNS.get(cube['material'], flat)
            for face, rect in f.items():
                face_pal = MATERIALS['belly'] if (face == 'down' and cube['material'] in ('hide',)) else pal
                paint_face(img, rect, face_pal, face, rnd, pattern)
        if cube.get('face') in DETAILS:
            DETAILS[cube['face']](img, f)
        if cube.get('back') == 'ammonite':
            detail_ammonite(img, f)
        if cube['name'] == 'snout':
            detail_snout(img, f)
    out = os.path.join(ROOT, mob_id, 'texture.png')
    img.save(out)
    img.resize((w * 8, h * 8), Image.NEAREST).save(os.path.join(ROOT, mob_id, 'texture_preview.png'))
    return out


if __name__ == '__main__':
    ids = sys.argv[1:] or sorted(d for d in os.listdir(ROOT) if os.path.isfile(os.path.join(ROOT, d, 'model.json')))
    for mob in ids:
        print('Textur:', os.path.normpath(paint(mob)))
