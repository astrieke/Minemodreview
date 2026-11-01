"""Malt die Mob-Texturen passend zu den Bauplänen in mobs/<id>/model.json.

Aufruf: python3 tools/make_mob_textures.py [mob-id ...]

Jeder Würfel hat ein Material. Der Maler kennt das Box-UV-Layout von Minecraft und bemalt
jede Würfelseite an der richtigen Stelle (Licht von oben, Seiten mit Verlauf, Unterseite dunkel).
Gesteinsschichten richten sich nach der Welthöhe, damit sie über Torso, Arme und Beine
auf gleicher Höhe durchlaufen. Danach kommen Details pro Mob (Augen, Krallen, Sanduhr …).
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


# Paletten von dunkel nach hell (5 Töne)
PAL = {
    'hide':          ['#241f2e', '#322b40', '#423a55', '#544a6a', '#665b7e'],
    'hide_dark':     ['#1b1724', '#262131', '#332c42', '#423a55', '#524866'],
    'belly':         ['#4a4258', '#5d5470', '#706684', '#867b97', '#9c91aa'],
    'calcite':       ['#8f887c', '#ada697', '#c8c1b2', '#ddd7c9', '#efebe0'],
    'calcite_light': ['#b3ad9f', '#cbc5b6', '#dcd6c8', '#ebe6da', '#f8f5ec'],
    'quartz':        ['#2c2670', '#4a43b8', '#6f6cf0', '#a9a8ff', '#e6e6ff'],
    'membrane':      ['#8c8162', '#a99e7c', '#c0b591', '#cfc39a', '#e1d8b5'],
    'gill':          ['#2a2116', '#3a2d1d', '#4a3a26', '#5a4730', '#6b5639'],
    'cap':           ['#3a2f1c', '#4a3c24', '#5b4a2e', '#6e5a38', '#837046'],
    'root':          ['#3e2a18', '#563b22', '#6f4e2e', '#8a663e', '#a5804f'],
    'gold':          ['#6a4708', '#9c6a12', '#d9a43a', '#f2c45a', '#ffe08a'],
    'glass':         ['#241a3a', '#2e2350', '#3a2c5c', '#4a3a70', '#5c4a86'],
}
# Gesteinsschichten (von unten nach oben wiederholt): Schiefer, Grau, Ocker, Grau, Calcit-Linie
STRATA = [('#3c3742', 3), ('#77707c', 3), ('#b08a5a', 2), ('#5e5868', 3), ('#dcd6cf', 1), ('#77707c', 2), ('#3c3742', 2)]
STRATA_PERIOD = sum(h for _, h in STRATA)

FACE_LIGHT = {'up': 1.12, 'north': 1.0, 'east': 0.92, 'west': 0.92, 'south': 0.86, 'down': 0.7}


def faces(cube):
    """Box-UV-Rechtecke (x0, y0, w, h) je Seite. 'north' ist vorne (Mobs schauen nach -z)."""
    w, h, d = (math.ceil(cube['to'][i] - cube['from'][i] - 1e-9) for i in range(3))
    u, v = cube['uv_offset']
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


def put(img, rect, x, y, color):
    x0, y0, w, h = rect
    if 0 <= x < w and 0 <= y < h:
        img.putpixel((x0 + x, y0 + y), rgb(color) if isinstance(color, str) else color)


def strata_color(world_y, dark=False):
    k = int(math.floor(world_y)) % STRATA_PERIOD
    for color, height in STRATA:
        if k < height:
            c = rgb(color)
            return shade(c, 0.78) if dark else c
        k -= height
    return rgb(STRATA[0][0])


def paint_cube(img, cube, rnd):
    f = faces(cube)
    mat = cube['material']
    top_y = cube['to'][1]
    for face, (x0, y0, w, h) in f.items():
        light = FACE_LIGHT[face]
        for y in range(h):
            for x in range(w):
                noise = rnd.uniform(-0.55, 0.55)
                if mat in ('strata', 'strata_dark'):
                    if face in ('up', 'down'):
                        base = strata_color(top_y if face == 'up' else cube['from'][1], mat == 'strata_dark')
                    else:
                        base = strata_color(top_y - 1 - y, mat == 'strata_dark')
                    c = shade(base, light * (1 + noise * 0.12))
                    if face not in ('up', 'down') and (x * 7 + y * 3 + int(top_y)) % 23 == 0:
                        c = shade(c, 0.6)                    # Risse
                    img.putpixel((x0 + x, y0 + y), c)
                    continue
                pal = PAL[mat]
                v = 2 + noise
                if face not in ('up', 'down') and h > 2:
                    v += 0.6 * (1 - y / (h - 1)) - 0.3
                if mat == 'hide':
                    if face == 'up':
                        v -= 0.7                             # dunkler Rücken
                    if face == 'down' or (face not in ('up',) and h >= 3 and y >= h - 1):
                        pal = PAL['belly']                   # heller Bauch / Flankenunterkante
                    elif (y % 3 == 2) and ((x + y // 3) % 3 != 0):
                        v -= 1.0                             # Schuppenreihen
                if mat == 'membrane' and ((x * 3 + y * 5) % 11 == 0 or (x + 2 * y) % 13 == 0):
                    v = 0.6                                  # Adern
                if mat == 'gill':
                    v = 1.0 if x % 2 == 0 else 2.2
                if mat == 'quartz':
                    v = 4 if face == 'up' else (3 if x == 0 else 2 if x < w - 1 else 1)
                if mat == 'calcite' and (x * 5 + y * 7) % 9 == 0:
                    v -= 1.2                                 # Calcit-Sprenkel
                c = rgb(pal[max(0, min(4, int(round(v))))])
                img.putpixel((x0 + x, y0 + y), shade(c, light))
        if face not in ('up', 'down') and h > 3 and mat not in ('quartz', 'glass'):
            for x in range(w):
                px = img.getpixel((x0 + x, y0 + h - 1))
                img.putpixel((x0 + x, y0 + h - 1), shade(px, 0.85))
    return f


# ------------------------------------------------------------------ Details

def d_lizard(img, f, rnd):
    for side in ('east', 'west'):
        r = f[side]
        ex = r[2] - 3 if side == 'east' else 1              # zur Schnauze hin
        put(img, r, ex, 1, '#f2c14e'); put(img, r, ex + 1, 1, '#f2c14e')
        put(img, r, ex + (1 if side == 'east' else 0), 1, '#1a1206')
    # Goldflecken als Erz-Hinweis und Calcit-Sprenkel an den Flanken
    for side in ('east', 'west'):
        r = f[side]
        put(img, r, 2, 2, '#d9a43a')


def d_snout(img, f, rnd):
    r = f['north']
    put(img, r, 0, 0, '#120e18'); put(img, r, r[2] - 1, 0, '#120e18')


def d_claws(img, f, rnd):
    r = f['north']
    for x in range(0, r[2], 2):
        put(img, r, x, 0, '#d8d2c4')


def d_spore(img, f, rnd):
    r = f['north']                                          # 10 x 4
    # schmale, schräge Augen
    for x, y in [(2, 0), (3, 1), (7, 0), (6, 1)]:
        put(img, r, x, y, '#ffb847')
    # Sporenventil: 3x2 dunkel mit Bernsteinrand
    for x in range(3, 7):
        put(img, r, x, 2, '#c07a1a')
    for x in range(4, 6):
        put(img, r, x, 3, '#1e1a10')
    put(img, r, 3, 3, '#c07a1a'); put(img, r, 6, 3, '#c07a1a')


def d_amber_dots(img, f, rnd):
    r = f['up']
    w, h = r[2], r[3]
    spots = [(2, 2), (w - 3, 3), (4, h - 3), (w - 4, h - 4), (w // 2, h // 2 - 1)]
    for x, y in spots:
        put(img, r, x, y, '#e0a030'); put(img, r, x + 1, y, '#8a5a10'); put(img, r, x, y + 1, '#8a5a10')
        put(img, r, x, y - 1 if y > 0 else y, '#ffd27a')
    # gelbgrüner Rand an den Seiten
    for face in ('north', 'south', 'east', 'west'):
        rr = f[face]
        for x in range(rr[2]):
            put(img, rr, x, rr[3] - 1, '#8a9a3a')


def d_witness(img, f, rnd):
    r = f['north']                                          # 8 x 7
    for x in range(r[2]):
        put(img, r, x, 2, '#2e2a35')                        # Schattenkante unter der Stirn
    for x in (1, 2, 5, 6):
        put(img, r, x, 3, '#ffd86b')                        # glühende Augenschlitze
    put(img, r, 2, 3, '#f2b43a'); put(img, r, 5, 3, '#f2b43a')
    for x, y in [(3, 5), (4, 5), (4, 6)]:
        put(img, r, x, y, '#2e2a35')                        # Mundriss


def d_niche(img, f, rnd):
    r = f['north']                                          # Torso 14 x 13; Sanduhr bei x 4.5..9.5, y 4..10 (von oben)
    for y in range(3, 11):
        for x in range(4, 10):
            put(img, r, x, y, '#1c1824')
    for x, y in [(2, 6), (3, 7), (11, 3), (12, 4)]:
        put(img, r, x, y, '#d9a43a')                        # Goldadern in Rissen


def d_hourglass(img, f, rnd):
    r = f['north']                                          # 4 x 4 Glas
    rows = ['vvvv', '.vv.', '.v..', 'vVVv']
    pal = {'v': '#ffd27a', 'V': '#e0a030', '.': '#4a3a70'}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            put(img, r, x, y, pal[ch])
    put(img, r, 0, 0, '#5c4a86'); put(img, r, 3, 0, '#5c4a86')


def d_ammonite(img, f, rnd):
    r = f['south']                                          # 8 x 8, Spirale aus Calcit auf dunklem Grund
    rows = [
        '..cccc..',
        '.cddddc.',
        'cdcccddc',
        'cdcddcdc',
        'cdcdccdc',
        'cdcddddc',
        '.cdcccc.',
        '..cddd..',
    ]
    pal = {'c': '#dcd6cf', 'd': '#2e2a35', '.': None}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if pal[ch]:
                put(img, r, x, y, pal[ch])
    put(img, r, 2, 2, '#f4f0ea'); put(img, r, 3, 2, '#f4f0ea')   # Glanz oben links


DETAILS = {'lizard': d_lizard, 'snout': d_snout, 'claws': d_claws, 'spore': d_spore, 'amber_dots': d_amber_dots,
           'witness': d_witness, 'niche': d_niche, 'hourglass': d_hourglass}


def paint(mob_id):
    model = json.load(open(os.path.join(ROOT, mob_id, 'model.json')))
    w, h = model['texture_size']
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    rnd = random.Random(mob_id)
    done = set()
    for cube in model['cubes']:
        key = tuple(cube['uv_offset'])
        f = faces(cube)
        if key not in done:                                 # gespiegelte Teile teilen sich die UV
            done.add(key)
            paint_cube(img, cube, rnd)
        if cube.get('face') in DETAILS:
            DETAILS[cube['face']](img, f, rnd)
        if cube.get('back') == 'ammonite':
            d_ammonite(img, f, rnd)
    out = os.path.join(ROOT, mob_id, 'texture.png')
    img.save(out)
    return out


if __name__ == '__main__':
    ids = sys.argv[1:] or sorted(d for d in os.listdir(ROOT) if os.path.isfile(os.path.join(ROOT, d, 'model.json')))
    for mob in ids:
        print('Textur:', os.path.normpath(paint(mob)))
