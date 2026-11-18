"""Erzeugt die Mod-Logos (Modliste von NeoForge) für Strata Core, Epochs und Bridge.

Aufruf:
  python3 tools/make_logo.py              Logos schreiben (<mod>/src/shared/resources/<modid>_logo.png)
  python3 tools/make_logo.py --preview P  zusätzlich alle drei nebeneinander nach P

Gemeinsames Motiv: ein Gesteinsprofil aus geneigten Schichten in den Farben des Zeitgesteins, im Kreis.
  - Core:   ein Bohrkern (Kupferrohr mit Schichtprobe) senkt sich durch die Schichten.
  - Epochs: in drei Schichten je ein Fund der Epochen (Kristallquarz, Sporenmoos, Glut).
  - Bridge: ein Bogen aus Bernstein verbindet zwei Schichtblöcke.
Pixel-Art auf 64 x 64, für die Anzeige verlustfrei auf 256 x 256 vergrößert (logoBlur = false).
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..')
SIZE = 64
SCALE = 4

# Zeitgestein (wie tools/make_textures.py), von dunkel (unten) nach hell (oben).
BANDS = ['#2e2639', '#3b3149', '#4a3e5a', '#594b6b', '#68597a', '#776889']
SEAM = '#241e2f'
SKY_TOP, SKY_BOTTOM = '#3a3050', '#4a3f63'
RIM_DARK, RIM_LIGHT = '#120e18', '#c9b8f0'


def rgb(h, a=255):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def lerp(c1, c2, t):
    return tuple(round(a + (b - a) * t) for a, b in zip(c1, c2))


def inside(x, y, r=29.5):
    return (x - 31.5) ** 2 + (y - 31.5) ** 2 <= r * r


def band_at(x, y):
    """Geneigte, leicht gewellte Schichten; 0 = oberste."""
    tilt = (x - 32) * 0.18 + math.sin(x * 0.21) * 1.2
    v = y - 18 + tilt
    if v < 0:
        return None   # Himmel über dem Profil
    return min(len(BANDS) - 1, int(v // 7.5))


def base():
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                continue
            b = band_at(x, y)
            if b is None:
                px[x, y] = lerp(rgb(SKY_TOP), rgb(SKY_BOTTOM), y / 24)
                continue
            col = rgb(BANDS[len(BANDS) - 1 - b])
            # Fuge an der Oberkante jeder Schicht, Licht von oben links (hellere Kante)
            tilt = (x - 32) * 0.18 + math.sin(x * 0.21) * 1.2
            v = y - 18 + tilt
            frac = v % 7.5
            if frac < 1.0 and b > 0:
                col = rgb(SEAM)
            elif frac < 2.0:
                col = lerp(col, (255, 255, 255, 255), 0.10)
            # feine Körnung, gleichbleibend (kein Zufall: Logo soll reproduzierbar sein)
            if (x * 7 + y * 13) % 23 == 0:
                col = lerp(col, (0, 0, 0, 255), 0.08)
            px[x, y] = col
    rim(img)
    return img


def rim(img):
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if inside(x, y) and not inside(x, y, 26.6):
                light = (x + y) < 60   # oben links heller
                px[x, y] = rgb(RIM_LIGHT if light else '#8a78b8') if inside(x, y, 28.6) else rgb(RIM_DARK)


def rect(img, x0, y0, x1, y1, color):
    px = img.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if inside(x, y, 26.5):
                px[x, y] = rgb(color) if isinstance(color, str) else color


def gem(img, cx, cy, pal, size=4):
    """Raute mit Licht oben links: pal = (dunkel, mittel, hell, glanz)."""
    px = img.load()
    for dy in range(-size, size + 1):
        w = size - abs(dy)
        for dx in range(-w, w + 1):
            x, y = cx + dx, cy + dy
            edge = abs(dx) == w or abs(dy) == size
            if edge:
                c = pal[0]
            elif dx + dy < -1:
                c = pal[2]
            elif dx + dy > 1:
                c = pal[1]
            else:
                c = pal[1]
            px[x, y] = rgb(c)
    px[cx - 1, cy - size + 2] = rgb(pal[3])


# ---------------------------------------------------------------- Motive

def core_logo():
    img = base()
    px = img.load()
    # Bohrloch: kurze, breite dunkle Öffnung mit heller Kante oben (der Kern wurde hier herausgezogen).
    hx, hy = 31, 47
    for dy in range(-2, 3):
        w = 7 - abs(dy)
        for dx in range(-w, w + 1):
            px[hx + dx, hy + dy] = rgb('#0e0a13' if abs(dx) < w else '#1a1422')
    for dx in range(-5, 6):
        px[hx + dx, hy - 3] = rgb('#9a8aa8')
    # Gezogene Probe: breiter Zylinder, oben gerade, Bänder in den Farben der Schichten (+ ein warmes, ein violettes)
    x0, x1 = 25, 38
    top, bottom = 10, 38
    bands = [BANDS[5], BANDS[4], '#b07a48', BANDS[3], '#7a52b8', BANDS[2], BANDS[1]]
    for y in range(top, bottom + 1):
        for x in range(x0, x1 + 1):
            band = min(len(bands) - 1, (y - top) * len(bands) // (bottom - top + 1))
            c = rgb(bands[band])
            t = (x - x0) / (x1 - x0)
            if t < 0.2:
                c = lerp(c, (255, 255, 255, 255), 0.35)
            elif t > 0.78:
                c = lerp(c, (0, 0, 0, 255), 0.35)
            if x in (x0, x1):
                c = rgb('#120e18')
            px[x, y] = c
    for x in range(x0, x1 + 1):
        px[x, top - 1] = rgb('#120e18')
        if x0 < x < x1:
            px[x, top] = rgb('#d9cce6')   # Schnittfläche oben
    # Kupferring direkt über der Krone, Bohrkrone am unteren Ende (3 px Zacken)
    for x in range(x0 - 1, x1 + 2):
        px[x, bottom - 4] = rgb('#120e18')
        px[x, bottom - 3] = rgb('#f2a56a')
        px[x, bottom - 2] = rgb('#c66d33')
        px[x, bottom - 1] = rgb('#8a4520')
        px[x, bottom] = rgb('#120e18')
    for i, x in enumerate(range(x0, x1 + 1)):   # Bohrkrone: metallgraue Dreieckszacken
        px[x, bottom + 1] = rgb('#8e93a0')
        if i % 3 != 2:
            px[x, bottom + 2] = rgb('#c8ccd6' if i % 3 == 0 else '#5c606c')
        if i % 3 == 0:
            px[x, bottom + 3] = rgb('#c8ccd6')
    # Seil, an dem die Probe hochgezogen wird
    for y in range(4, top - 1):
        px[31, y] = rgb('#d9c8a0' if y % 2 else '#a8946c')
        px[32, y] = rgb('#a8946c' if y % 2 else '#d9c8a0')
    return img


def prism(px, x, base_y, height, width, pal):
    """Senkrechtes Kristallprisma mit Spitze; pal = (Kontur, Schatten, Mitte, Licht)."""
    for dy in range(height):
        y = base_y - dy
        w = width if dy < height - width else max(0, height - dy - 1)
        for dx in range(-w, w + 1):
            c = pal[3] if dx < 0 else pal[2] if dx == 0 else pal[1]
            if abs(dx) == w:
                c = pal[0]
            px[x + dx, y] = rgb(c)
    px[x, base_y - height] = rgb(pal[0])


def epochs_logo():
    img = base()
    px = img.load()
    # Kristallzeit (oben): Bündel aus drei violetten Prismen
    quartz = ('#2a1458', '#6a3cc0', '#9b5cf0', '#c99bff')
    prism(px, 15, 30, 13, 2, quartz)
    prism(px, 20, 31, 17, 3, quartz)
    prism(px, 25, 31, 11, 2, quartz)
    # Sporenzeit (Mitte): Pilzkappe auf Moos, darüber helle Sporen
    cx, cy = 45, 34
    for dy in range(-5, 1):
        w = int(math.sqrt(max(0, 36 - dy * dy)) * 1.3)
        for dx in range(-w, w + 1):
            c = '#1f3a14' if abs(dx) == w or dy == -5 else '#9fd06a' if dx < -2 and dy < -2 else '#5c8f3c'
            px[cx + dx, cy + dy] = rgb(c)
    for dx in range(-7, 8):
        px[cx + dx, cy + 1] = rgb('#1f3a14')
    for y in range(cy + 1, cy + 7):
        for x in range(cx - 1, cx + 2):
            px[x, y] = rgb('#d9cfa8' if x < cx + 1 else '#a89a74')
    for x in range(cx - 6, cx + 7):
        px[x, cy + 7] = rgb('#3f6b2a' if x % 2 else '#5c8f3c')
    for sx, sy in ((cx - 4, cy - 10), (cx + 3, cy - 13), (cx + 7, cy - 8)):
        for ox, oy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            px[sx + ox, sy + oy] = rgb('#e8ffb0')
    # Aschezeit (unten): Glutbrocken aus Holzkohle mit glühenden Rissen, unregelmäßig, Funken darüber
    rock = [
        "...kkkk.kkk...",
        "..kddddkdddk..",
        ".kddmdddrdddk.",
        "kddrrddddrdddk",
        "kdddrrddrrddk.",
        "kmddyrrrrdddk.",
        ".kddddrydddmdk",
        "kdddddrddddddk",
        ".kkdddddddkkk.",
        "...kkkkkkk....",
    ]
    pal = {'k': '#1a1010', 'd': '#2a1a1a', 'm': '#4a2a22', 'r': '#ff9a2a', 'y': '#ffd25a'}
    ex, ey = 19, 44
    for dy, row in enumerate(rock):
        for dx, ch in enumerate(row):
            if ch != '.':
                px[ex + dx, ey + dy] = rgb(pal[ch])
    for fx, fy, c in ((ex + 4, ey - 3, '#ffd25a'), (ex + 8, ey - 5, '#fff1c4'), (ex + 11, ey - 2, '#ff9a2a')):
        px[fx, fy] = rgb(c)
    return img


def bridge_logo():
    img = base()
    px = img.load()
    # Bogen aus Bernstein über dem Himmel, von Schichtblock zu Schichtblock.
    cx, cy, r_out, r_in = 31.5, 33, 21, 15
    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y, 26.5) or y > cy:
                continue
            d = math.hypot(x - cx, (y - cy) * 1.25)
            if r_in <= d <= r_out:
                t = (d - r_in) / (r_out - r_in)
                c = '#fff3c8' if t < 0.18 else '#f6ad3e' if t < 0.45 else '#d9821c' if t < 0.8 else '#5a2a06'
                ang = math.degrees(math.atan2(cy - y, x - cx))
                if 0.18 <= t < 0.8 and int(ang) % 22 in (0, 1):
                    c = '#8a4a10'   # Fugen zwischen den Bogensteinen
                px[x, y] = rgb(c)
            elif d < r_in and y > cy - 12:
                px[x, y] = lerp(px[x, y], (0, 0, 0, 255), 0.25)   # Schatten unter dem Bogen
    # Schlussstein im Scheitel des Bogens
    rect(img, 29, 14, 34, 17, '#ffd27a')
    rect(img, 29, 17, 34, 17, '#a2560f')
    rect(img, 29, 14, 29, 17, '#5a2a06')
    rect(img, 34, 14, 34, 17, '#5a2a06')
    return img


LOGOS = {
    'core': ('strata_core', core_logo),
    'epochs': ('strata_epochs', epochs_logo),
    'bridge': ('strata_bridge', bridge_logo),
}


def main():
    images = []
    for mod, (mod_id, make) in LOGOS.items():
        img = make().resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST)
        out = os.path.join(ROOT, mod, 'src', 'shared', 'resources', f'{mod_id}_logo.png')
        img.save(out, optimize=True)
        images.append(img)
        print('geschrieben:', os.path.relpath(out, ROOT))
    if '--preview' in sys.argv:
        path = sys.argv[sys.argv.index('--preview') + 1]
        sheet = Image.new('RGBA', (len(images) * (SIZE * SCALE + 16) + 16, SIZE * SCALE + 32), rgb('#3a3a3a'))
        for i, img in enumerate(images):
            sheet.alpha_composite(img, (16 + i * (SIZE * SCALE + 16), 16))
        sheet.save(path)
        print('Vorschau:', path)


if __name__ == '__main__':
    main()
