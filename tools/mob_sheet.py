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
               ('Sporenzeit dunkel', '#1f3318'), ('Sporenzeit Nebel', '#4f7a3a')]
# Gegen welche Hintergründe die Oberseiten bestehen müssen (dunkel und Nebel der eigenen Epoche).
EPOCH_BG = {'geode_crawler': ['#2a1f4d', '#6e56b8'], 'spore_bellows': ['#1f3318', '#4f7a3a'],
            'petrified_witness': ['#2a1f4d', '#6e56b8', '#1f3318', '#4f7a3a']}
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


def scale_panel(model):
    """Seitenansicht (Blick von +x) der Würfel in Ruhepose neben einer Spieler-Silhouette."""
    img = Image.new('RGBA', (CELL, CELL), (235, 235, 238, 255))
    d = ImageDraw.Draw(img)
    zs = [v for c in model['cubes'] for v in (c['from'][2], c['to'][2])]
    ys = [c['to'][1] for c in model['cubes']]
    width = (max(zs) - min(zs)) + 6 + 9.6
    height = max(max(ys), PLAYER_PX)
    s = min((CELL - 20) / width, (CELL - 30) / height)
    ground = CELL - 14

    def box(z0, z1, y0, y1, fill):
        d.rectangle([10 + z0 * s, ground - y1 * s, 10 + z1 * s, ground - y0 * s], fill=fill)

    for c in model['cubes']:
        box(c['from'][2] - min(zs), c['to'][2] - min(zs), c['from'][1], c['to'][1], (90, 84, 104, 255))
    px = max(zs) - min(zs) + 6
    box(px, px + 9.6, 0, PLAYER_PX, (60, 120, 200, 140))
    d.line([0, ground, CELL, ground], fill=(30, 30, 30, 255))
    d.text((4, 4), f'Hoehe {max(ys):g} px / Spieler {PLAYER_PX} px', fill=(30, 30, 30, 255))
    return img


def sheet(mob):
    shots = os.path.join(ROOT, mob, 'shots')
    model = json.load(open(os.path.join(ROOT, mob, 'model.json'), encoding='utf-8'))
    tex = Image.open(os.path.join(ROOT, mob, 'texture.png')).convert('RGBA')
    views = ['front', 'side', 'back', 'iso']
    anims = sorted(f for f in os.listdir(shots) if f.startswith('anim_'))
    cols = 4
    anim_rows = (len(anims) + cols - 1) // cols
    rows = 1 + anim_rows + 1 + 1
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
    row += 1
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
    path = os.path.join(ROOT, mob, 'review_sheet.png')
    out.save(path)
    return path, ratio


if __name__ == '__main__':
    for m in sys.argv[1:] or sorted(os.listdir(ROOT)):
        if os.path.isdir(os.path.join(ROOT, m, 'shots')):
            path, ratio = sheet(m)
            print(f'{os.path.normpath(path)}  (Kontrast Oberseiten {ratio:.1f}:1)')
