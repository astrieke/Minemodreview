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
    # Haut heller und wärmer, damit sich der Rücken vom violetten Nebel abhebt (Projektmanager 03.11.)
    'hide':          ['#3b3142', '#4f4356', '#64566a', '#7a6b7c', '#918190'],
    'hide_dark':     ['#1b1724', '#262131', '#332c42', '#423a55', '#524866'],
    'belly':         ['#5d5470', '#746a88', '#8a7f9c', '#a094b0', '#b6abc2'],
    'calcite':       ['#8f887c', '#ada697', '#c8c1b2', '#ddd7c9', '#efebe0'],
    'calcite_light': ['#b3ad9f', '#cbc5b6', '#dcd6c8', '#ebe6da', '#f8f5ec'],
    'quartz':        ['#2c2670', '#4a43b8', '#6f6cf0', '#a9a8ff', '#e6e6ff'],
    'membrane':      ['#8c8162', '#a99e7c', '#c0b591', '#cfc39a', '#e1d8b5'],
    # Lamellen fast schwarz: dunkle Unterkante unter dem Warnrand (hebt ihn auch ohne Farbton ab, 3,3:1 zum Nebel)
    'gill':          ['#1a1008', '#2a1a10', '#3a2a1a', '#4a3a26', '#5a4730'],
    'cap':           ['#3a2f1c', '#4a3c24', '#5b4a2e', '#6e5a38', '#837046'],
    # Hut-Oberseiten heller, damit sie sich vom Sporennebel abheben (Prüfung 05.11.)
    'cap_top':       ['#6e5a38', '#857048', '#98804f', '#a8905e', '#b8a06c'],
    # Warnfarbe am Hutrand: Gegenfarbe zum Grün der Sporenzeit, hell genug für 2:1 zum Nebel
    'rim':           ['#8f3a1d', '#c0502a', '#e8915a', '#f6b98a', '#ffc89a'],
    'root':          ['#3e2a18', '#563b22', '#6f4e2e', '#8a663e', '#a5804f'],
    'gold':          ['#6a4708', '#9c6a12', '#d9a43a', '#f2c45a', '#ffe08a'],
    # Schiefer ohne Ockerband, für gekippte Platten (sonst wirkt die Ockerseite wie Holz)
    'slate':         ['#2e2a35', '#3c3742', '#4a4552', '#5e5868', '#77707c'],
    'shell':         ['#8a7458', '#b39c78', '#d6c29a', '#e9dbb8', '#f7efd8'],
    'hood':          ['#5a3438', '#7a4a4a', '#9a6560', '#b8837a', '#d4a596'],
    'eye':           ['#6a4708', '#9c6a12', '#d9a43a', '#f2c45a', '#ffe08a'],
    'fossil':        ['#6e5f44', '#9c8a64', '#cbb98f', '#e6d8b4', '#f2e8cc'],
    'glass':         ['#241a3a', '#2e2350', '#3a2c5c', '#4a3a70', '#5c4a86'],
}
# Gesteinsschichten (von unten nach oben wiederholt): Schiefer, Grau, Ocker, Grau, Calcit-Linie
STRATA = [('#3c3742', 3), ('#77707c', 3), ('#b08a5a', 2), ('#5e5868', 3), ('#dcd6cf', 1), ('#77707c', 2), ('#3c3742', 2)]
STRATA_PERIOD = sum(h for _, h in STRATA)

# Licht als Stufe in der Palette (nicht als Farbmultiplikation), damit pro Material nur feste Töne vorkommen
FACE_STEP = {'up': 1, 'north': 0, 'east': 0, 'west': 0, 'south': -1, 'down': -2}
# Gesteinsschichten: 4 feste Töne je Schicht (Riss/Schatten, Seite, Vorderseite, Oberseite)
STRATA_TONES = [0.7, 0.85, 1.0, 1.12]


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


def strata_color(world_y, step):
    """Farbe der Schicht auf Welthöhe world_y, in Tonstufe step (0 = dunkel … 3 = hell)."""
    k = int(math.floor(world_y)) % STRATA_PERIOD
    base = STRATA[0][0]
    for color, height in STRATA:
        if k < height:
            base = color
            break
        k -= height
    return shade(rgb(base), STRATA_TONES[max(0, min(3, step))])


def paint_cube(img, cube, rnd):
    f = faces(cube)
    mat = cube['material']
    top_y = cube['to'][1]
    top_layer_y = top_y
    if mat in ('strata', 'strata_dark'):
        front_h = f['north'][3]
        front = [strata_color(top_y - 1 - y, 2) for y in range(front_h)]
        front_lum = sum(lum(c) for c in front) / max(len(front), 1)
        rows = [top_y] + [top_y - 1 - y for y in range(front_h)]
        if lum(strata_color(top_y, 3)) < front_lum:          # Licht von oben: Oberseite heller als vorn
            brighter = [r for r in rows if lum(strata_color(r, 3)) >= front_lum]
            top_layer_y = min(brighter, key=lambda r: lum(strata_color(r, 3))) if brighter else                 max(rows, key=lambda r: lum(strata_color(r, 3)))
    for face, (x0, y0, w, h) in f.items():
        side = face not in ('up', 'down')
        for y in range(h):
            for x in range(w):
                noise = rnd.uniform(-0.55, 0.55)
                if mat in ('strata', 'strata_dark'):
                    step = 2 + FACE_STEP[face] - (1 if mat == 'strata_dark' else 0)
                    if face in ('east', 'west'):
                        step -= 1                            # Seiten eine Stufe dunkler als vorn
                    if noise < -0.4:
                        step -= 1                            # Körnung nur aus den festen Tönen
                    if side and (x * 7 + y * 3 + int(top_y)) % 23 == 0:
                        step = 0                             # Risse
                    world_y = (top_layer_y if face == 'up' else cube['from'][1]) if not side else top_y - 1 - y
                    img.putpixel((x0 + x, y0 + y), strata_color(world_y, step))
                    continue
                pal = PAL['cap_top'] if mat == 'cap' and face == 'up' else PAL[mat]
                v = 2 + noise * 0.8 + FACE_STEP[face]
                if side and h > 2:
                    v += 0.6 * (1 - y / (h - 1)) - 0.3
                if side and h >= 3 and y == h - 1 and mat not in ('quartz', 'glass'):
                    v -= 1                                   # dunkle Unterkante
                if mat == 'hide':
                    if face == 'down':
                        pal = PAL['belly']                   # Bauch
                    elif side and h >= 5 and y == h - 1:
                        pal, v = PAL['belly'], 3.5 + noise * 0.5   # heller Bauchsaum (eine Zeile)
                    elif (y % 3 == 2) and ((x + y // 3) % 3 != 0):
                        v -= 1.0                             # Schuppenreihen
                if mat == 'membrane' and ((x * 3 + y * 5) % 11 == 0 or (x + 2 * y) % 13 == 0):
                    v = 0.6                                  # Adern
                if mat == 'gill':
                    v = 1.0 if x % 2 == 0 else 2.2
                if mat == 'quartz':
                    v = 4 if face == 'up' else (3 if x == 0 else 2 if x < w - 1 else 1)
                if mat == 'shell':
                    if (side and y % 2 == 1) or (face in ('up', 'down') and y % 2 == 1):
                        v -= 0.9                             # Anwachsstreifen quer über die Schale
                    if (x * 7 + y * 3) % 11 == 0:
                        v -= 0.6                             # Körnung
                if mat == 'eye':
                    v = 2 if face == 'down' else 4           # Augen hell, Unterseite dunkler (leuchten mit Segment 4)
                if mat == 'calcite' and (x * 5 + y * 7) % 9 == 0:
                    v -= 1.2                                 # Calcit-Sprenkel
                img.putpixel((x0 + x, y0 + y), rgb(pal[max(0, min(4, int(round(v))))]))
    return f


# ------------------------------------------------------------------ Details

def d_lizard(img, f, rnd):
    for side in ('east', 'west'):
        r = f[side]
        ex = r[2] - 3 if side == 'east' else 1              # zur Schnauze hin
        put(img, r, ex, 1, '#f2c14e'); put(img, r, ex + 1, 1, '#f2c14e')
        put(img, r, ex + (1 if side == 'east' else 0), 1, '#1a1206')
    # Augen auch von vorn erkennbar: Goldpixel an den vorderen Schädelecken
    r = f['north']
    for x in range(r[2]):
        put(img, r, x, 0, PAL['hide'][0])                   # Brauenwulst als Kante statt eigenem Würfel
    put(img, r, 0, 1, '#f2c14e'); put(img, r, r[2] - 1, 1, '#f2c14e')
    # Goldfleck als Erz-Hinweis an den Flanken
    for side in ('east', 'west'):
        r = f[side]
        put(img, r, 2, 2, '#d9a43a')


def d_snout(img, f, rnd):
    r = f['north']
    put(img, r, 0, 0, '#2a2333'); put(img, r, r[2] - 1, 0, '#2a2333')


def d_claws(img, f, rnd):
    r = f['north']
    for x in range(0, r[2], 2):
        put(img, r, x, r[3] - 1, '#d8d2c4')                 # Krallen an der Unterkante des Beins


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


def d_amber_dots(img, f, rnd, rim=True):
    r = f['up']
    w, h = r[2], r[3]
    spots = [(2, 2), (w - 3, 3), (3, h - 3), (w - 4, h - 4), (w // 2, h // 2 - 1)]
    for x, y in spots:
        put(img, r, x, y, '#e0a030'); put(img, r, x + 1, y, '#8a5a10'); put(img, r, x, y + 1, '#8a5a10')
        put(img, r, x, max(0, y - 1), '#ffd27a')
    if not rim:
        return
    for face in ('north', 'south', 'east', 'west'):    # orange-roter Warnrand, keine Punkte an den Seiten
        rr = f[face]                                    # (sonst wirken sie wie ein zweites Augenpaar)
        for x in range(rr[2]):
            put(img, rr, x, rr[3] - 1, PAL['rim'][2 if x % 3 else 3])


def d_amber_top(img, f, rnd):
    d_amber_dots(img, f, rnd, rim=False)


def d_witness(img, f, rnd):
    for side in ('east', 'west', 'south'):                  # Ockerband auf Augenhöhe rundum
        rr = f[side]
        for x in range(rr[2]):
            put(img, rr, x, 3, '#b08a5a')
    r = f['north']                                          # 8 x 7
    for x in range(r[2]):
        put(img, r, x, 3, '#b08a5a')
    for x in range(r[2]):
        put(img, r, x, 2, '#2e2a35')                        # Schattenkante unter der Stirn
    for x in (1, 2, 5, 6):
        put(img, r, x, 3, '#ffd86b')                        # glühende Augenschlitze
    put(img, r, 2, 3, '#f2b43a'); put(img, r, 5, 3, '#f2b43a')
    for x, y in [(3, 5), (4, 5), (4, 6)]:
        put(img, r, x, y, '#2e2a35')                        # Mundriss


def d_niche(img, f, rnd):
    r = f['north']                                          # Torso vorn; dunkle Nische hinter der Sanduhr (5 x 7)
    w = r[2]
    x0 = (w - 7) // 2
    for y in range(3, 13):
        for x in range(x0, x0 + 7):
            put(img, r, x, y, '#1c1824')
    for x, y in [(2, 6), (3, 7), (4, 8), (w - 3, 3), (w - 2, 4)]:
        put(img, r, x, y, VEIN)                             # Goldadern in Rissen


def d_hourglass(img, f, rnd):
    r = f['north']                                          # 5 x 7: Kappen oben und unten, Glas mit Taille und Sand
    rows = ['GGGGG', '.vvv.', '..v..', '..v..', '.vVv.', 'vVVVv', 'ggggg']
    pal = {'G': '#f2c45a', 'g': '#9c6a12', 'v': '#ffd27a', 'V': '#e0a030', '.': '#3a2c5c'}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            put(img, r, x, y, pal[ch])


def d_veins(img, f, rnd, faces_=('south', 'east', 'west')):
    """Goldadern: 1-px-Risse, die schräg nach unten laufen. Sie leuchten nur während der Steinhaut."""
    for face in faces_:
        x0, y0, w, h = f[face]
        if w < 3 or h < 4:
            continue
        for _ in range(1 + (w * h) // 90):
            x = rnd.randrange(1, w - 1)
            y = rnd.randrange(0, max(1, h // 3))
            length = rnd.randrange(h // 2, h)
            for i in range(length):
                if 0 <= x < w and 0 <= y < h:
                    img.putpixel((x0 + x, y0 + y), rgb(VEIN_END if i == length - 1 else VEIN))
                y += 1
                if rnd.random() < 0.45:
                    x += rnd.choice((-1, 1))


# Gemalte Adern dunkel (Ocker), damit die Sanduhr der hellste Punkt bleibt; hell sind sie nur auf der Leucht-Ebene.
VEIN = '#9a6812'
VEIN_END = '#6b4a10'


def d_ammonite(img, f, rnd):
    # Von Hand gezeichnet: Auf 8 x 8 Pixeln liest sich eine berechnete Spirale nicht.
    rows = [
        '..lLLl..',
        '.L....L.',
        'L..LL..L',
        'l.L..L.l',
        'L.L.L..L',
        'l..L.LLl',
        '.L.....L',
        '..LlLlL.',
    ]
    pal = {'L': '#efe9e0', 'l': '#c9c1b6', '.': '#2e2a35'}
    r = f['south']
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            put(img, r, x, y, pal[ch])


# Spiralrille in 4 Segmenten (außen -> innen), je eine Leucht-Ebene: Die Uhr erlischt von außen nach innen.
# Grundfarbe einheitlich dunkel (erloschen gut von Leuchten zu unterscheiden); die Segmente unterscheiden sich
# nur um eine unsichtbare Stufe im Blau, damit die Leucht-Masken sie trennen können.
SPIRAL_SEGMENTS = ['#4a3590', '#4a3591', '#4a3592', '#4a3593']
SPIRAL_CENTER = '#2a1d50'
SPIRAL_GLOW = '#c9a8ff'
SPIRAL_STRIPE = '#8a5a34'


def spiral_cells(w, h, margin=1):
    """Eckige Spirale vom Zentrum nach außen, mit einer Zeile Schale zwischen den Windungen
    und einem freien Schalenrand von {margin} Pixeln. Liefert die Zellen in Reihenfolge (innen zuerst)."""
    x, y = w // 2 - 1, h // 2 - 1
    cells = [(x, y)]
    dirs = [(1, 0), (0, 1), (-1, 0), (0, -1)]
    length, d = 2, 0
    while True:
        for _ in range(2):
            dx, dy = dirs[d % 4]
            for _ in range(length):
                x, y = x + dx, y + dy
                if not (margin <= x < w - margin and margin <= y < h - margin):
                    return cells
                cells.append((x, y))
            d += 1
        length += 2


def spiral_colors(w, h):
    """Zelle -> Farbe: Zentrum dunkel, danach 4 gleich lange Segmente von innen (hell) nach außen (dunkel)."""
    cells = spiral_cells(w, h)
    n = len(cells) - 1
    out = {cells[0]: SPIRAL_CENTER}
    for i, c in enumerate(cells[1:]):
        out[c] = SPIRAL_SEGMENTS[3 - min(3, i * 4 // n)]
    return out


def d_spiral(img, f, rnd):
    # Eckige Spirale auf beiden Seiten der Schale (auf wenigen Pixeln liest sich das besser als rund).
    # Der äußere Arm endet vorn unten an der Mündung beim Mantel.
    r = f['east']
    w, h = r[2], r[3]
    cells = spiral_colors(w, h)
    for side in ('east', 'west'):
        r = f[side]
        for y in range(h):
            for x in range(w):
                c = cells.get((x, y))
                if c is None and (x + 2 * y) % 4 == 0 and (x in (0, w - 1) or y in (0, h - 1)):
                    c = SPIRAL_STRIPE                         # Streifen am Rand der Schale
                if c:
                    # Ost-Seite: u = 0 liegt hinten, West-Seite: vorn. Gespiegelt, damit der Arm vorn endet.
                    put(img, r, w - 1 - x if side == 'east' else x, y, c)


def d_fossil(img, f, rnd):
    # Versteinerte Hülle wie das Ammonit-Item: Spirale als dunkles Relief auf beiden Seiten.
    for side in ('east', 'west'):
        r = f[side]
        cells = spiral_cells(r[2], r[3], margin=1)
        for x, y in cells:
            put(img, r, x, y, '#5e5038')


def d_ribs(img, f, rnd):
    # Rückseite der Schale: senkrechte Rippen statt Flecken.
    r = f['south']
    for y in range(r[3]):
        for x in range(r[2]):
            if x % 2 == 1:
                put(img, r, x, y, PAL['shell'][1])


def d_tip(img, f, rnd):
    # Hellere Fangarm-Spitze (vorn, -z).
    put(img, f['north'], 0, 0, PAL['hood'][4])
    put(img, f['east'], f['east'][2] - 1, 0, PAL['hood'][4])
    put(img, f['west'], 0, 0, PAL['hood'][4])


def d_hood(img, f, rnd):
    # Vorn an der Kapuze: dunkle Öffnung, aus der die Fangarme kommen, mit hellem Rand oben.
    r = f['north']
    for y in range(r[3]):
        for x in range(r[2]):
            if y >= 2 and 1 <= x <= r[2] - 2:
                put(img, r, x, y, PAL['hood'][0] if y >= 3 else PAL['hood'][1])
    for x in range(r[2]):
        put(img, r, x, 0, PAL['hood'][4])


DETAILS = {'lizard': d_lizard, 'snout': d_snout, 'claws': d_claws, 'spore': d_spore, 'amber_dots': d_amber_dots,
           'amber_top': d_amber_top,
           'spiral': d_spiral, 'hood': d_hood, 'fossil': d_fossil,
           'witness': d_witness, 'niche': d_niche, 'hourglass': d_hourglass}


# Leucht-Ebenen: Name -> Materialien (ganze Würfel) oder Farben (einzelne Pixel, z. B. Augen),
# die im Dunkeln leuchten (texture_glow_<name>.png). Die Stärke je Zustand (0..1) legt der Mob im Code fest.
GLOW = {
    'geode_crawler': {'crystals': {'materials': {'quartz'}}},
    # Augen, Ventilrand und Bernsteinpunkte: schwach immer, voll beim Aufpumpen
    'spore_bellows': {'spores': {'colors': {'#ffb847', '#c07a1a', '#e0a030', '#ffd27a'}}},
    # Kern (Sanduhr) und Augen pulsieren; die Goldadern leuchten nur während der Steinhaut
    'petrified_witness': {'core': {'colors': {'#ffd27a', '#e0a030', '#f2c45a', '#ffd86b', '#f2b43a'}},
                          'veins': {'colors': {'#9a6812', '#6b4a10'}, 'glow_color': '#ffe08a'}},
    # Die Spiralrille ist die Uhr: 4 Segmente (außen 1 -> innen 4), die von außen nach innen erlöschen.
    # Die Augen leuchten mit dem innersten Segment.
    'spiralit': {'spiral_1': {'colors': {'#4a3590'}, 'glow_color': '#c9a8ff'},
                 'spiral_2': {'colors': {'#4a3591'}, 'glow_color': '#c9a8ff'},
                 'spiral_3': {'colors': {'#4a3592'}, 'glow_color': '#c9a8ff'},
                 # innerstes Segment und die Augen (die eigene Leuchtfarbe je Farbe über glow_map)
                 'spiral_4': {'colors': {'#4a3593', '#2a1d50', '#ffe08a'},
                              'glow_map': {'#4a3593': '#c9a8ff', '#2a1d50': '#c9a8ff', '#ffe08a': '#fff0b0'}}},
}


def paint_glow(model, img, mob_id):
    """Schreibt pro Leucht-Ebene eine Maske: nur die Pixel der leuchtenden Würfel, etwas aufgehellt."""
    outs = []
    for layer, spec in GLOW.get(mob_id, {}).items():
        glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
        materials = spec.get('materials', set())
        colors = {rgb(c)[:3] for c in spec.get('colors', set())}
        fixed = rgb(spec['glow_color']) if 'glow_color' in spec else None   # eigene Leuchtfarbe statt Texturfarbe
        glow_map = {rgb(k)[:3]: rgb(v) for k, v in spec.get('glow_map', {}).items()}
        for cube in model['cubes']:
            if materials and cube['material'] not in materials:
                continue
            for x0, y0, w, h in faces(cube).values():
                for y in range(y0, y0 + h):
                    for x in range(x0, x0 + w):
                        r, g, b, a = img.getpixel((x, y))
                        if a and (r, g, b) in glow_map:
                            glow.putpixel((x, y), glow_map[(r, g, b)])
                        elif a and (not colors or (r, g, b) in colors) and fixed:
                            glow.putpixel((x, y), fixed)
                        elif a and (not colors or (r, g, b) in colors):
                            glow.putpixel((x, y), (min(255, int(r * 1.15)), min(255, int(g * 1.15)), min(255, int(b * 1.15)), 255))
        out = os.path.join(ROOT, mob_id, f'texture_glow_{layer}.png')
        glow.save(out)
        outs.append(out)
    return outs


def paint(mob_id):
    model = json.load(open(os.path.join(ROOT, mob_id, 'model.json'), encoding='utf-8'))
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
        if cube.get('back') == 'ribs':
            d_ribs(img, f, rnd)
        if cube.get('tip'):
            d_tip(img, f, rnd)
        if cube.get('back') == 'ammonite':
            d_ammonite(img, f, rnd)
        if cube.get('veins'):
            d_veins(img, f, rnd, ('south', 'east', 'west', 'north') if cube['bone'] == 'back_plates' else
                    ('south', 'east', 'west'))
    check_light(model, img, mob_id)
    out = os.path.join(ROOT, mob_id, 'texture.png')
    img.save(out)
    for g in paint_glow(model, img, mob_id):
        print('Leucht-Ebene:', os.path.normpath(g))
    return out


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def brightness(img, rect):
    x0, y0, w, h = rect
    px = [img.getpixel((x0 + x, y0 + y)) for y in range(h) for x in range(w)]
    px = [p for p in px if p[3]]
    return sum(0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2] for p in px) / max(len(px), 1)


def check_light(model, img, mob_id):
    """Licht von oben: Keine Oberseite darf im Mittel dunkler sein als die Vorderseite desselben Würfels."""
    bad = []
    for cube in model['cubes']:
        if cube['material'] == 'glass' or cube.get('tip'):  # leuchtendes Glas / helle Fangarmspitze vorn ist gewollt
            continue
        if max(abs(r) for r in cube.get('rotation') or [0]) > 45:
            continue                                        # stark gekippt: „oben“ ist dann eine andere Seite
        f = faces(cube)
        if brightness(img, f['up']) + 0.5 < brightness(img, f['north']):
            bad.append(f"{cube['name']} (oben {brightness(img, f['up']):.0f} < vorn {brightness(img, f['north']):.0f})")
    if bad:
        sys.exit(f'{mob_id}: Oberseite dunkler als Vorderseite bei ' + ', '.join(bad))


if __name__ == '__main__':
    ids = sys.argv[1:] or sorted(d for d in os.listdir(ROOT) if os.path.isfile(os.path.join(ROOT, d, 'model.json')))
    for mob in ids:
        print('Textur:', os.path.normpath(paint(mob)))
