"""Fasst die Screenshots eines Mobs zu einem Prüfblatt zusammen: mobs/<id>/review_sheet.png

Inhalt:
- Ansichten front, side, back, iso
- Animationsposen (shots/anim_*.png, aufgenommen am größten Ausschlag, siehe tools/anim_peaks.py)
- die Schrägansicht vor den vier Epochen-Hintergründen (Kristallzeit, Sporenzeit, je dunkel/hell)
- Größenvergleich mit dem Spieler (1,8 Blöcke = 28,8 px) in der Seitenansicht
- Textur und Kontrast der Oberseiten zum dunklen Epochen-Hintergrund
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from make_mob_textures import faces  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '..', 'mobs')
BACKGROUNDS = [('Kristallzeit dunkel', '#2a1f4d'), ('Kristallzeit Nebel', '#6e56b8'),
               ('Sporenzeit dunkel', '#1f3318'), ('Sporenzeit Nebel', '#4f7a3a'),
               ('Aschezeit dunkel', '#1d110d'), ('Aschezeit Nebel', '#5b3a2e')]
# Gegen welche Hintergründe die Oberseiten bestehen müssen (dunkel und Nebel der eigenen Epoche).
EPOCH_BG = {'geode_crawler': ['#2a1f4d', '#6e56b8'], 'spore_bellows': ['#1f3318', '#4f7a3a'],
            'petrified_witness': ['#2a1f4d', '#6e56b8', '#1f3318', '#4f7a3a'],
            'spiralit': ['#2a1f4d', '#6e56b8', '#1f3318', '#4f7a3a', '#1d110d', '#5b3a2e']}
PLAYER_PX = 28.8
CELL = 256


def hex_rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def luminance(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(v) for v in c[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast(a, b):
    la, lb = sorted((luminance(a), luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def top_color(model, tex):
    """Mittlere Farbe der Oberseiten, gewichtet nach Fläche (das, was man von oben sieht)."""
    acc, n = [0, 0, 0], 0
    for c in model['cubes']:
        x0, y0, w, h = faces(c)['up']
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                p = tex.getpixel((x, y))
                if p[3]:
                    acc = [a + v for a, v in zip(acc, p[:3])]
                    n += 1
    return tuple(round(a / max(n, 1)) for a in acc)


def cube_corners(cube):
    """Die 8 Ecken eines Würfels in Blockbench-Koordinaten, mit seiner eigenen Drehung (Reihenfolge ZYX)."""
    f, t = cube['from'], cube['to']
    pts = [[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
    rot = cube.get('rotation')
    if not rot or not any(rot):
        return pts
    o = cube.get('origin', [0, 0, 0])
    rx, ry, rz = (math.radians(a) for a in rot)

    def rotate(p):
        x, y, z = (p[i] - o[i] for i in range(3))
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)      # X zuerst
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)     # dann Y
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)      # dann Z
        return [x + o[0], y + o[1], z + o[2]]

    return [rotate(p) for p in pts]


def scale_panel(model):
    """Seitenansicht (Blick von +x) der Würfel in Ruhepose neben einer Spieler-Silhouette (gedrehte Würfel gedreht)."""
    img = Image.new('RGBA', (CELL, CELL), (235, 235, 238, 255))
    d = ImageDraw.Draw(img)
    corners = {c['name']: cube_corners(c) for c in model['cubes']}
    zs = [p[2] for ps in corners.values() for p in ps]
    ys = [p[1] for ps in corners.values() for p in ps]
    hover = model.get('hover', 0)                     # Schwebehöhe in px (Begleiter schweben neben dem Spieler)
    top = max(ys) + hover
    width = (max(zs) - min(zs)) + 6 + 9.6
    height = max(top, PLAYER_PX)
    s = min((CELL - 20) / width, (CELL - 30) / height)
    ground = CELL - 14
    sx = lambda z: 10 + (z - min(zs)) * s
    sy_ground = lambda y: ground - y * s
    sy = lambda y: ground - (y + hover) * s

    for c in model['cubes']:
        ps = corners[c['name']]
        if c.get('rotation') and any(c['rotation']):
            # Umriss der gedrehten Seite: konvexe Hülle der projizierten Ecken (z, y)
            pts = sorted({(round(p[2], 3), round(p[1], 3)) for p in ps})
            def half(points):
                h = []
                for q in points:
                    while len(h) >= 2 and (h[-1][0] - h[-2][0]) * (q[1] - h[-2][1]) - (h[-1][1] - h[-2][1]) * (q[0] - h[-2][0]) <= 0:
                        h.pop()
                    h.append(q)
                return h
            hull = half(pts)[:-1] + half(pts[::-1])[:-1]
            d.polygon([(sx(z), sy(y)) for z, y in hull], fill=(90, 84, 104, 255))
        else:
            d.rectangle([sx(c['from'][2]), sy(c['to'][1]), sx(c['to'][2]), sy(c['from'][1])], fill=(90, 84, 104, 255))
    px = max(zs) + 6
    d.rectangle([sx(px), sy_ground(PLAYER_PX), sx(px + 9.6), sy_ground(0)], fill=(60, 120, 200, 140))
    d.line([0, ground, CELL, ground], fill=(30, 30, 30, 255))
    label = f'Hoehe {top:.1f} px / Spieler {PLAYER_PX} px' + (f' (schwebt {hover} px)' if hover else '')
    d.text((4, 4), label, fill=(30, 30, 30, 255))
    return img


def sheet(mob):
    shots = os.path.join(ROOT, mob, 'shots')
    model = json.load(open(os.path.join(ROOT, mob, 'model.json'), encoding='utf-8'))
    tex = Image.open(os.path.join(ROOT, mob, 'texture.png')).convert('RGBA')
    views = ['front', 'side', 'back', 'iso']
    anims = sorted(f for f in os.listdir(shots) if f.startswith('anim_'))
    cols = 4
    anim_rows = (len(anims) + cols - 1) // cols
    clock = os.path.isfile(os.path.join(ROOT, mob, 'texture_glow_spiral_1.png'))
    bg_rows = (len(BACKGROUNDS) + cols - 1) // cols
    rows = 1 + anim_rows + bg_rows + 1 + (1 if clock else 0)
    lh = 18
    out = Image.new('RGBA', (cols * CELL, rows * (CELL + lh)), (235, 235, 238, 255))
    d = ImageDraw.Draw(out)

    def place(im, i, label, bg=None):
        x, y = (i % cols) * CELL, (i // cols) * (CELL + lh)
        im = im.convert('RGBA').resize((CELL, CELL), Image.LANCZOS)
        if bg:
            out.paste(Image.new('RGBA', (CELL, CELL), hex_rgb(bg) + (255,)), (x, y))
        out.alpha_composite(im, (x, y))
        d.text((x + 4, y + CELL + 2), label, fill=(30, 30, 30, 255))

    for i, v in enumerate(views):
        place(Image.open(os.path.join(shots, v + '.png')), i, v)
    for j, a in enumerate(anims):
        place(Image.open(os.path.join(shots, a)), cols + j, a[5:-4])
    row = 1 + anim_rows
    iso = Image.open(os.path.join(shots, 'iso.png'))
    for k, (label, bg) in enumerate(BACKGROUNDS):
        place(iso, row * cols + k, label, bg)
    row += bg_rows
    place(scale_panel(model), row * cols, 'Massstab (blau = Spieler)')
    top = top_color(model, tex)
    bgs = EPOCH_BG.get(mob, ['#2a1f4d'])
    ratios = [contrast(top, hex_rgb(b)) for b in bgs]
    ratio = min(ratios)
    x, y = CELL, row * (CELL + lh)
    texscale = max(1, min(2 * CELL // tex.width, CELL // tex.height))
    out.alpha_composite(tex.resize((tex.width * texscale, tex.height * texscale), Image.NEAREST), (x, y))
    d.text((x + 4, y + CELL + 2), f'texture   Oberseiten #{top[0]:02x}{top[1]:02x}{top[2]:02x}, '
                                  'Kontrast ' + ', '.join(f'{b} {r:.1f}:1' for b, r in zip(bgs, ratios)),
           fill=(30, 30, 30, 255))
    # Aus Spielerhöhe im Spiel (Client-Screenshot im Dunkeln), falls vorhanden.
    ingame = os.path.join(shots, 'ingame_dark_1.21.1.png')
    if os.path.isfile(ingame):
        im = Image.open(ingame).convert('RGBA')
        side = min(im.width, im.height)
        im = im.crop(((im.width - side) // 2, 0, (im.width + side) // 2, side))
        place(im, row * cols + 3, 'im Spiel, dunkel (1.21.1)')
    if clock:
        # Uhr: Seite der Schale mit den Leucht-Segmenten, wie sie je nach Restzeit leuchten (bei Tageslicht).
        row += 1
        core = next(c for c in model['cubes'] if c.get('face') == 'spiral')
        fx, fy, fw, fh = faces(core)['west']
        masks = [Image.open(os.path.join(ROOT, mob, f'texture_glow_spiral_{i}.png')).convert('RGBA') for i in range(1, 5)]
        for k, (lit, label) in enumerate([(4, 'Uhr 4/4 (volle Zeit)'), (3, 'Uhr 3/4'), (1, 'Uhr 1/4'),
                                          (0, 'Warnphase, Blinken aus')]):
            face = tex.crop((fx, fy, fx + fw, fy + fh))
            for i in range(4 - lit, 4):
                face.alpha_composite(masks[i].crop((fx, fy, fx + fw, fy + fh)))
            panel = Image.new('RGBA', (fw + 2, fh + 2), (138, 180, 248, 255))
            panel.alpha_composite(face, (1, 1))
            place(panel.resize((CELL, CELL), Image.NEAREST), row * cols + k, label)
    path = os.path.join(ROOT, mob, 'review_sheet.png')
    out.save(path)
    return path, ratio


if __name__ == '__main__':
    for m in sys.argv[1:] or sorted(os.listdir(ROOT)):
        if os.path.isdir(os.path.join(ROOT, m, 'shots')):
            path, ratio = sheet(m)
            print(f'{os.path.normpath(path)}  (Kontrast Oberseiten {ratio:.1f}:1)')
