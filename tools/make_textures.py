"""Erzeugt die 16x16-Texturen für Strata Epochs.

Aufruf:
  python3 tools/make_textures.py             Texturen schreiben
  python3 tools/make_textures.py --preview P  zusätzlich ein vergrößertes Vorschaublatt nach P

Gestaltungsregeln (an Vanilla angelehnt):
  - kleine Paletten (4-6 Töne pro Material), Licht kommt von oben links
  - Items haben einen dunklen, farbigen Umriss und keine Einzelpixel-Störungen
  - Blöcke kacheln nahtlos (Rauschen wiederholt sich an den Rändern)
"""
import os
import random
import sys

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), '..', 'epochs', 'src', 'shared', 'resources',
                   'assets', 'strata_epochs', 'textures')


# ---------------------------------------------------------------- Hilfen

def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def canvas():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def from_grid(rows, palette, base=None):
    """Zeichnet ein Zeichenraster. '.' bleibt durchsichtig bzw. zeigt das Basisbild."""
    img = base.copy() if base else canvas()
    for y, row in enumerate(rows):
        assert len(row) == 16, (len(row), row)
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), rgb(palette[ch]))
    return img


def outline(img, color):
    """Setzt einen Umriss um alle sichtbaren Pixel (4er-Nachbarschaft), wie bei Vanilla-Items."""
    out = img.copy()
    c = rgb(color)
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and img.getpixel((nx, ny))[3]:
                    out.putpixel((x, y), c)
                    break
    return out


def tile_noise(seed, scale=4):
    """Glattes, kachelbares Rauschen (Werte 0..1) auf 16x16."""
    rnd = random.Random(seed)
    n = 16 // scale
    grid = [[rnd.random() for _ in range(n)] for _ in range(n)]

    def at(x, y):
        gx, gy = x / scale, y / scale
        x0, y0 = int(gx) % n, int(gy) % n
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        fx, fy = gx - int(gx), gy - int(gy)
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        top = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
        bottom = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
        return top * (1 - fy) + bottom * fy

    return [[at(x, y) for x in range(16)] for y in range(16)]


def pick(palette, value):
    i = max(0, min(len(palette) - 1, int(value * len(palette))))
    return rgb(palette[i])


# ---------------------------------------------------------------- Blöcke

# Zeitgestein: violettgrauer Stein. Die Schichten (Strata) sind sanfte Farbbänder mit leicht
# wechselndem Farbton; Fugen sind kurze Stücke in einem mittleren Dunkelton mit Licht darunter.
CHRONO_COOL = ['#2c2638', '#383047', '#433a55', '#4f4563', '#5b5071', '#695e80']
CHRONO_WARM = ['#2f2636', '#3c3144', '#483c52', '#544760', '#61536d', '#6f617b']
SEAM = '#332b40'
SEAM_LIGHT = '#6a5f82'
# (erste Zeile, Helligkeitsversatz, Palette)
BANDS = [(0, 0.03, CHRONO_COOL), (3, -0.05, CHRONO_WARM), (6, 0.06, CHRONO_COOL),
         (10, -0.02, CHRONO_WARM), (13, 0.02, CHRONO_COOL)]


def band_for(y):
    current = BANDS[0]
    for b in BANDS:
        if y >= b[0]:
            current = b
    return current


def chronostone(seed=11, seams=True):
    img = canvas()
    rnd = random.Random(seed)
    coarse = tile_noise(seed + 1, 8)
    for y in range(16):
        _, offset, pal = band_for(y)
        for x in range(16):
            v = 0.48 + offset + (coarse[y][x] - 0.5) * 0.25 + rnd.uniform(-0.11, 0.11)
            img.putpixel((x, y), pick(pal, v))
    if seams:
        # Kurze Fugen an den Bandgrenzen, nie über den Kachelrand.
        for y, x0, length in [(2, 2, 4), (5, 9, 5), (9, 1, 3), (9, 11, 3), (12, 5, 4)]:
            for x in range(x0, x0 + length):
                img.putpixel((x, y), rgb(SEAM))
                img.putpixel((x, y + 1), rgb(SEAM_LIGHT))
    return img


def stamp(img, x0, y0, rows, pal):
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x0 + dx, y0 + dy), rgb(pal[ch]))


QUARTZ = {'k': '#2c2470', 'd': '#4a43b8', 'm': '#6f6cf0', 'l': '#a9a8ff', 'w': '#e6e6ff'}


def crystal_quartz_ore():
    img = chronostone(seed=23)
    # Kristalle ohne schwarzen Umriss: Licht oben links, dunkle Kante unten rechts.
    stamp(img, 2, 1, ['.w..', 'wlm.', 'wlmd', 'lmdk', 'lmdk', '.dk.'], QUARTZ)          # groß, senkrecht
    stamp(img, 9, 3, ['....w', '...wl', '..wlm', '.wlmd', 'wlmdk', 'lmdk.', '.kk..'], QUARTZ)  # schräg
    stamp(img, 5, 10, ['.w.', 'wlm', 'lmd', 'mdk', '.k.'], QUARTZ)                       # mittel
    stamp(img, 12, 12, ['..w', '.wm', 'wmd', 'dk.'], QUARTZ)                            # klein, schräg
    return img


AMBER = {'k': '#5a2a06', 'd': '#a2560f', 'm': '#d9821c', 'l': '#f6ad3e', 'y': '#ffd27a', 'w': '#fff3c8'}


def amber_ore():
    img = chronostone(seed=37)
    stamp(img, 2, 2, ['.lmm.', 'lwymd', 'mymdk', 'lmddk', '.dkk.'], AMBER)   # großer Einschluss
    stamp(img, 9, 8, ['..lm.', '.wymd', 'lymmd', 'mmdk.', '.kk..'], AMBER)   # zweiter Einschluss
    stamp(img, 11, 2, ['ld', 'dk'], AMBER)                                  # Splitter
    stamp(img, 3, 12, ['yd'], AMBER)                                        # Splitter
    return img


# Versteinerter Stein: Sandgrau mit Violettstich (entsteht aus Zeitgestein) und gerippter Ammonit.
PETRI = ['#4d4650', '#605866', '#756c78', '#8a8188', '#9f9597', '#b5aba8']


def petrified_stone():
    import math
    img = canvas()
    rnd = random.Random(51)
    coarse = tile_noise(52, 8)
    for y in range(16):
        for x in range(16):
            v = 0.55 + (coarse[y][x] - 0.5) * 0.3 + rnd.uniform(-0.15, 0.15)
            img.putpixel((x, y), pick(PETRI[1:], v))
    # Ammonit: runde Schale mit Spiralrille, aus der Mitte gerückt, Licht oben links.
    cx, cy, radius = 9.0, 7.5, 4.8
    turn = 2.6                                            # Pixel Abstand pro Windung
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if r > radius:
                continue
            light = -(dx + dy) / (radius * 1.41)          # +1 oben links, -1 unten rechts
            angle = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            phase = (r / turn - angle) % 1.0
            if r > radius - 0.8:
                color = PETRI[0] if light < 0.25 else PETRI[1]       # Außenkante
            elif phase < 0.28:
                color = PETRI[0] if light < 0.4 else PETRI[1]        # Spiralrille
            else:
                color = PETRI[5] if light > 0.3 else PETRI[4] if light > -0.25 else PETRI[3]
            img.putpixel((x, y), rgb(color))
    # kurze Haarrisse, nicht an den Ecken
    for x, y in [(2, 4), (3, 5), (3, 11), (4, 12), (13, 13)]:
        img.putpixel((x, y), rgb(PETRI[0]))
    return img


def epoch_gate():
    pal = {
        'A': '#6f6488', 'B': '#4c4262', 'C': '#2e2739', 'K': '#1b1624',   # Rahmen aus Zeitgestein
        'G': '#ffe08a', 'g': '#d9a43a', 'h': '#8f6414',                   # Gold
        'S': '#140f1e', 's': '#21183a',                                   # Innenraum
        'q': '#3b2d63',                                                   # Glas
        'v': '#d2b4ff', 'V': '#9466f2',                                   # leuchtender Zeitsand
    }
    rows = [
        'AAAAAAAAAAAAAAAB',
        'AgBBBBBBBBBBBBgC',
        'ABKSSSSSSSSSSKCC',
        'ABSGGGGGGGGGGhCC',
        'ABSsgqqqqqqqhsCC',
        'ABSsgqvvvvVqhsCC',
        'ABSssgqvvVqhssCC',
        'ABSsssgvVhsssSCC',
        'ABSsssgqvhsssSCC',
        'ABSssgqqvqqhssCC',
        'ABSsgqqqvVqqhsCC',
        'ABSsgqvvvVVVhsCC',
        'ABSGGGGGGGGGGhCC',
        'ABKSSSSSSSSSSKCC',
        'AgCCCCCCCCCCCCgC',
        'BCCCCCCCCCCCCCCK',
    ]
    return from_grid(rows, pal)


# ---------------------------------------------------------------- Items

def diag_shape(spec, t_range, u_center=15):
    """Hilfsraster für diagonale Items (unten links -> oben rechts).

    t = x - y wächst entlang der Achse, u = x + y quer dazu.
    spec(t, du) gibt eine Farbe oder None zurück; du = u - u_center.
    """
    img = canvas()
    for y in range(16):
        for x in range(16):
            t, du = x - y, x + y - u_center
            if t_range[0] <= t <= t_range[1]:
                c = spec(t, du)
                if c:
                    img.putpixel((x, y), rgb(c))
    return img


def core_drill():
    wood = {-1: '#a8774a', 0: '#7f5634', 1: '#5c3c22'}
    copper = {-1: '#f2a56a', 0: '#c66d33', 1: '#8a4520'}
    steel = {-1: '#eef3f7', 0: '#a9b4bf', 1: '#5d6670'}

    def spec(t, du):
        if t < -2:                       # Griff, 2 Pixel breit, Maserung
            if du not in (0, 1):
                return None
            c = wood[-1 if du == 0 else 1]
            return wood[0] if (t % 3 == 0 and du == 0) else c
        if t <= -1:                      # Muffe
            return '#3b3b44' if du in (-1, 0, 1) else None
        if t <= 4:                       # Kupferrohr, 3 breit mit Glanzstreifen
            return copper.get(du)
        if t <= 8:                       # Stahlkrone, 5 breit
            if -2 <= du <= 2:
                return steel[-1] if du <= -2 else steel[1] if du >= 2 else steel[0] if du >= 0 else steel[-1]
            return None
        if t == 9:                       # Zähne
            return steel[1] if du in (-2, 0, 2) else None
        return None

    return outline(diag_shape(spec, (-14, 9)), '#1e140c')


def core_sample():
    import math
    layers = [  # entlang der Achse: Bruchende unten links ... Querschnitt oben rechts
        ('#6d6680', '#565068', '#413c50'),
        ('#7cc274', '#5a9e54', '#3f7a3c'),
        ('#6d6680', '#565068', '#413c50'),
        ('#f0a64a', '#d0802a', '#a05f1c'),
        ('#f0a64a', '#d0802a', '#a05f1c'),
        ('#6d6680', '#565068', '#413c50'),
        ('#a98ce0', '#7a5cc0', '#5a3f98'),
        ('#a98ce0', '#7a5cc0', '#5a3f98'),
    ]

    def spec(t, du):
        if not -3 <= du <= 2:
            return None
        if t == -7 and du in (-3, 0, 2):  # gebrochenes Ende unten links
            return None
        if t >= 6:                        # Querschnitt oben rechts (Ellipse mit Körnung)
            if t == 7 and du in (-3, 2):
                return None
            return '#e2d6f5' if du <= -2 else '#c3b2e6' if du <= 0 else '#a592cf'
        wave = int(round(math.sin((du + 4) * 1.4) * 0.7))
        idx = max(0, min(len(layers) - 1, (t + 7 + wave) * len(layers) // 13))
        light, mid, dark = layers[idx]
        return light if du <= -2 else dark if du == 2 else mid

    return outline(diag_shape(spec, (-7, 7)), '#16121c')


def crystal_quartz():
    # Eigene Form statt Amethyst-Cluster: eine große Bergkristall-Spitze und eine kleine daneben.
    pal = {'w': '#f2f2ff', 'l': '#b8b6ff', 'm': '#7471f2', 'd': '#4a44c0', 'k': '#2c2670'}
    rows = [
        '................',
        '......w.........',
        '.....wlm........',
        '....wllmd.......',
        '...wllmmmd......',
        '...wllmmmd......',
        '...wllmmmd..w...',
        '...lllmmmd.wld..',
        '...lllmmmd.lmd..',
        '...lllmmdd.lmd..',
        '...llmmmdd.lmd..',
        '....lmmmd..mdk..',
        '....kmmdk..kk...',
        '.....k.k........',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1a1450')


def amber_shard():
    # Kantiger Bernsteinsplitter (gerade Bruchkante oben rechts) mit eingeschlossenem Insekt.
    pal = {'k': '#6e380a', 'd': '#a2560f', 'm': '#d9821c', 'l': '#f6ad3e', 'y': '#ffd27a', 'w': '#fff3c8',
           'b': '#3e1e06', 'B': '#7a4210'}
    rows = [
        '................',
        '..........w.....',
        '.........wyl....',
        '........wyyml...',
        '.......wyymmmd..',
        '......lyymmmmd..',
        '.....lyymBmBmd..',
        '....lyymmmbmmd..',
        '....lymBbbbbBd..',
        '...lyymmmbbmmd..',
        '...lymBbbbbBdk..',
        '...lmmmmmbmmdk..',
        '....dmmmmmmdk...',
        '.....ddmmddk....',
        '.......kkk......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#3a1b04')


def time_anchor():
    pal = {
        'G': '#ffe08a', 'g': '#e0a82e', 'h': '#9c6a12',
        'q': '#d7eef6', 'Q': '#a8c8d8',
        'v': '#c6a4ff', 'V': '#8a5ce6',
    }
    rows = [
        '................',
        '..GGGGGGGGGGGG..',
        '..gggggggggggh..',
        '...gqqqqqqqQh...',
        '...gqvvvvvVQh...',
        '....gqvvvVQh....',
        '.....gqvVQh.....',
        '......gvVh......',
        '......gqvh......',
        '.....gqqvQh.....',
        '....gqqqvQQh....',
        '...gqqvvvVVQh...',
        '...gqvvvvvVVh...',
        '..GGGGGGGGGGGG..',
        '..gggggggggggh..',
        '................',
    ]
    return outline(from_grid(rows, pal), '#3a2606')


# ---------------------------------------------------------------- Ausgabe

BLOCKS = {
    'chronostone': chronostone,
    'crystal_quartz_ore': crystal_quartz_ore,
    'amber_ore': amber_ore,
    'petrified_stone': petrified_stone,
    'epoch_gate': epoch_gate,
}
ITEMS = {
    'core_drill': core_drill,
    'core_sample': core_sample,
    'crystal_quartz': crystal_quartz,
    'amber_shard': amber_shard,
    'time_anchor': time_anchor,
}


def save_all():
    images = {}
    for kind, table in (('block', BLOCKS), ('item', ITEMS)):
        for name, fn in table.items():
            img = fn()
            path = os.path.join(OUT, kind, name + '.png')
            os.makedirs(os.path.dirname(path), exist_ok=True)
            img.save(path)
            images[(kind, name)] = img
    return images


def preview(images, path):
    """Vorschaublatt: jede Textur 8-fach, Blöcke zusätzlich 3x3 gekachelt (zum Prüfen der Nahtstellen)."""
    s = 8
    cell = 16 * s
    pad = 20
    width = 5 * (cell + pad) + pad
    height = 3 * (cell + 40) + pad * 2
    sheet = Image.new('RGBA', (width, height), (44, 44, 52, 255))
    draw = ImageDraw.Draw(sheet)
    blocks = [k for k in images if k[0] == 'block']
    items = [k for k in images if k[0] == 'item']
    for row, keys in enumerate((blocks, items)):
        for i, key in enumerate(keys):
            x = pad + i * (cell + pad)
            y = pad + row * (cell + 40)
            sheet.alpha_composite(images[key].resize((cell, cell), Image.NEAREST), (x, y))
            draw.text((x, y + cell + 6), key[1], fill=(235, 235, 235, 255))
    # Kachelreihe: Blöcke 3x3 bei 2,67-facher Größe
    for i, key in enumerate(blocks):
        tile = Image.new('RGBA', (48, 48))
        for tx in range(3):
            for ty in range(3):
                tile.paste(images[key], (tx * 16, ty * 16))
        x = pad + i * (cell + pad)
        y = pad + 2 * (cell + 40)
        sheet.alpha_composite(tile.resize((cell, cell), Image.NEAREST), (x, y))
        draw.text((x, y + cell + 6), key[1] + ' (3x3)', fill=(235, 235, 235, 255))
    sheet.save(path)


if __name__ == '__main__':
    imgs = save_all()
    if '--preview' in sys.argv:
        target = sys.argv[sys.argv.index('--preview') + 1]
        preview(imgs, target)
        print('Vorschau:', target)
    print('Texturen geschrieben nach', os.path.normpath(OUT))
