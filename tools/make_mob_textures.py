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
    'gill':          ['#2a2116', '#3a2d1d', '#4a3a26', '#5a4730', '#6b5639'],
    'cap':           ['#3a2f1c', '#4a3c24', '#5b4a2e', '#6e5a38', '#837046'],
    # Warnfarbe am Hutrand: Gegenfarbe zum Grün der Sporenzeit
    'rim':           ['#6e2a14', '#8f3a1d', '#c0502a', '#d8703f', '#e8915a'],
    'root':          ['#3e2a18', '#563b22', '#6f4e2e', '#8a663e', '#a5804f'],
    'gold':          ['#6a4708', '#9c6a12', '#d9a43a', '#f2c45a', '#ffe08a'],
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
                pal = PAL[mat]
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


DETAILS = {'lizard': d_lizard, 'snout': d_snout, 'claws': d_claws, 'spore': d_spore, 'amber_dots': d_amber_dots,
           'amber_top': d_amber_top,
           'witness': d_witness, 'niche': d_niche, 'hourglass': d_hourglass}


# Leucht-Ebenen: Name -> Materialien, deren Pixel im Dunkeln leuchten (texture_glow_<name>.png).
# Die Stärke je Zustand (0..1) legt der Mob im Code fest.
GLOW = {
    'geode_crawler': {'crystals': {'quartz'}},
}


def paint_glow(model, img, mob_id):
    """Schreibt pro Leucht-Ebene eine Maske: nur die Pixel der leuchtenden Würfel, etwas aufgehellt."""
    outs = []
    for layer, materials in GLOW.get(mob_id, {}).items():
        glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
        for cube in model['cubes']:
            if cube['material'] not in materials:
                continue
            for x0, y0, w, h in faces(cube).values():
                for y in range(y0, y0 + h):
                    for x in range(x0, x0 + w):
                        r, g, b, a = img.getpixel((x, y))
                        if a:
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
        if cube.get('back') == 'ammonite':
            d_ammonite(img, f, rnd)
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
        if cube['material'] == 'glass':                     # leuchtendes Glas vorn ist gewollt
            continue
        f = faces(cube)
        if brightness(img, f['up']) + 0.5 < brightness(img, f['north']):
            bad.append(f"{cube['name']} (oben {brightness(img, f['up']):.0f} < vorn {brightness(img, f['north']):.0f})")
    if bad:
        sys.exit(f'{mob_id}: Oberseite dunkler als Vorderseite bei ' + ', '.join(bad))


if __name__ == '__main__':
    ids = sys.argv[1:] or sorted(d for d in os.listdir(ROOT) if os.path.isfile(os.path.join(ROOT, d, 'model.json')))
    for mob in ids:
        print('Textur:', os.path.normpath(paint(mob)))
