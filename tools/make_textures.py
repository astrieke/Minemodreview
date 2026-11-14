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
OUT_EGGS = os.path.join(os.path.dirname(__file__), '..', 'epochs', 'src', 'reg_1_21_11_plus', 'resources',
                        'assets', 'strata_epochs', 'textures', 'item')


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
SEAM_LIGHT = '#5a4f6e'
# (erste Zeile, Helligkeitsversatz, Palette)
BANDS = [(0, 0.04, CHRONO_COOL), (3, -0.09, CHRONO_WARM), (6, 0.08, CHRONO_COOL),
         (10, -0.07, CHRONO_WARM), (13, 0.03, CHRONO_COOL)]


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
        for y, x0, length, lit in [(2, 2, 4, True), (5, 9, 5, False), (9, 1, 3, True),
                                   (9, 11, 3, False), (12, 5, 4, True)]:
            for x in range(x0, x0 + length):
                img.putpixel((x, y), rgb(SEAM))
                if lit and x != x0 + length - 1:
                    img.putpixel((x, y + 1), rgb(SEAM_LIGHT))
    return img


def stamp(img, x0, y0, rows, pal):
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x0 + dx, y0 + dy), rgb(pal[ch]))


QUARTZ = {'k': '#2c2470', 'd': '#4a43b8', 'm': '#6f6cf0', 'l': '#a9a8ff', 'w': '#e6e6ff', 'p': '#262031'}


def crystal_quartz_ore():
    img = chronostone(seed=23)
    # Kristalle ohne schwarzen Umriss: Licht oben links, dunkle Kante unten rechts.
    # Gesteinsmulden ('p') unter den Kristallen, damit sie im Stein sitzen statt darauf.
    stamp(img, 2, 1, ['.w..', 'wlm.', 'wlmd', 'lmdk', 'lmdk', 'pdkp'], QUARTZ)          # groß, senkrecht
    stamp(img, 10, 4, ['...l', '..lm', '.lmd', 'lmdk', 'mdk.', 'pkp.'], QUARTZ)         # schräg, gedämpft
    stamp(img, 5, 10, ['.w.', 'wlm', 'lmd', 'mdk', 'pkp'], QUARTZ)                     # mittel
    stamp(img, 12, 12, ['..m', '.md', 'mdk', 'pp.'], QUARTZ)                            # klein, dunkel
    return img


AMBER = {'k': '#5a2a06', 'd': '#a2560f', 'm': '#d9821c', 'l': '#f6ad3e', 'y': '#ffd27a', 'w': '#fff3c8'}


def amber_ore():
    img = chronostone(seed=37)
    stamp(img, 2, 2, ['.lmm.', 'lwymd', 'mymdk', 'lmddk', '.dkk.'], AMBER)   # großer Einschluss
    stamp(img, 9, 8, ['..lm.', '.wymd', 'lymmd', 'mmdk.', '.kk..'], AMBER)   # zweiter Einschluss
    stamp(img, 11, 2, ['ld', 'dk'], AMBER)                                  # Splitter
    stamp(img, 1, 7, ['y', 'd'], AMBER)                                     # Splitter
    return img


# Vulkanasche: weiches, körniges Grau mit warmem Stich und vereinzelten Glutfunken.
ASH = ['#2f2b2b', '#3b3636', '#474141', '#544d4c', '#625a58', '#736a66']


def volcanic_ash():
    img = canvas()
    rnd = random.Random(71)
    coarse = tile_noise(72, 8)
    fine = tile_noise(73, 4)
    for y in range(16):
        for x in range(16):
            v = 0.5 + (coarse[y][x] - 0.5) * 0.45 + (fine[y][x] - 0.5) * 0.25 + rnd.uniform(-0.12, 0.12)
            img.putpixel((x, y), pick(ASH, v))
    # Glutfunken (2er-Gruppen, nie am Rand) und helle Bimskörner
    for x, y in [(3, 4), (11, 9), (6, 13)]:
        img.putpixel((x, y), rgb('#e0602a'))
        img.putpixel((x + 1, y), rgb('#8f3a1d'))
    for x, y in [(9, 2), (1, 10), (13, 14), (7, 7)]:
        img.putpixel((x, y), rgb('#8a817b'))
    return img


EMBER = {'k': '#4a0f05', 'd': '#9e2a0c', 'm': '#e0531c', 'l': '#ff9a3c', 'y': '#ffd27a', 'w': '#fff1c4', 'p': '#1f1a1c'}


def ember_ore():
    # Glühende Adern im Zeitgestein, die heller werden, je weiter sie im Inneren liegen.
    img = chronostone(seed=81)
    stamp(img, 2, 2, ['..lm.', '.lymd', 'lywmd', 'mymdk', 'pddkp'], EMBER)
    stamp(img, 9, 7, ['.lm..', 'lymm.', 'mwymd', '.mmdk', '..pk.'], EMBER)
    stamp(img, 11, 1, ['lm', 'dk'], EMBER)
    stamp(img, 3, 11, ['.l.', 'lym', 'mdk'], EMBER)
    return img


def ember_shard():
    # Kantiger, rotglühender Splitter mit heller Mitte.
    pal = {'k': '#5a1205', 'd': '#9e2a0c', 'm': '#e0531c', 'l': '#ff9a3c', 'y': '#ffd27a', 'w': '#fff1c4'}
    rows = [
        '................',
        '................',
        '.........l......',
        '........lyl.....',
        '.......lywm.....',
        '......lyywmd....',
        '......lywymd....',
        '.....lyywmmd....',
        '.....lywymmdk...',
        '....lyywmmmdk...',
        '....lmywmmddk...',
        '.....mmmmddk....',
        '......mdddk.....',
        '.......dkk......',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#2a0904')


# Versteinerter Stein: Sandgrau mit Violettstich (entsteht aus Zeitgestein) und gerippter Ammonit.
PETRI = ['#4a4152', '#5e5468', '#74697c', '#8a7f8c', '#a0959c', '#b8adae']


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
    cx, cy, radius = 12.5, 11.0, 4.8                      # sitzt über der Ecke -> kein Stempelraster
    turn = 2.6                                            # Pixel Abstand pro Windung
    for y in range(16):
        for x in range(16):
            dx = (x + 0.5 - cx + 8) % 16 - 8                # Abstand mit Umbruch über den Kachelrand
            dy = (y + 0.5 - cy + 8) % 16 - 8
            r = math.hypot(dx, dy)
            if r > radius:
                continue
            light = -(dx + dy) / (radius * 1.41)          # +1 oben links, -1 unten rechts
            angle = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            phase = (r / turn - angle) % 1.0
            if r > radius - 0.8:
                color = PETRI[5] if light > 0.3 else PETRI[0] if light < -0.2 else PETRI[2]  # Außenkante im Licht
            elif phase < 0.28:
                color = PETRI[0] if light < 0.4 else PETRI[1]        # Spiralrille
            else:
                color = PETRI[5] if light > 0.3 else PETRI[4] if light > -0.25 else PETRI[3]
            img.putpixel((x, y), rgb(color))
    # kurze Haarrisse, nicht an den Ecken
    for x, y in [(2, 4), (3, 5), (6, 7), (7, 8)]:
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
        'ABSsgvqqqqqVhsCC',
        'ABSssgvvvVVhssCC',
        'ABSsssgvVhsssSCC',
        'ABSsssgqvhsssSCC',
        'ABSssgqqvqqhssCC',
        'ABSsgqqvVVqqhsCC',
        'ABSsgvvvVVVVhsCC',
        'ABSGGGGGGGGGGhCC',
        'ABKSSSSSSSSSSKCC',
        'AgCCCCCCCCCCCCgC',
        'BCCCCCCCCCCCCCCK',
    ]
    img = from_grid(rows, pal)
    # Leuchten: dunkle Innenpixel neben der Sanduhr bekommen einen violetten Schimmer.
    glow = rgb('#33275a')
    frame = {rgb(pal[c]) for c in 'Ggh'}
    inner = {rgb(pal['S']), rgb(pal['s'])}
    halo = []
    for y in range(2, 14):
        for x in range(2, 14):
            if img.getpixel((x, y)) in inner and any(
                    img.getpixel((x + dx, y + dy)) in frame for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                halo.append((x, y))
    for xy in halo:
        img.putpixel(xy, glow)
    return img


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


DRILL_COPPER = {-1: '#f2a56a', 0: '#c66d33', 1: '#8a4520', 'shade': '#6a3418'}
DRILL_STEEL = {-1: '#eef3f7', 0: '#a9b4bf', 1: '#5d6670'}


def core_drill(copper=DRILL_COPPER, steel=DRILL_STEEL, sleeve='#3b3b44', wood=('#b0804e', '#83593a', '#5a3b22')):
    # Seitenansicht: Querbalken-Griff aus Holz, Holzschaft, Muffe, Rohr, Bohrkrone mit Sägezähnen.
    # Stufe 1: Kupferrohr und Stahlkrone; höhere Stufen tauschen Rohr und Krone.
    wood_l, wood_m, wood_d = wood

    def spec(t, du):
        if t in (-12, -11):                     # Querbalken quer zum Schaft, 2 px dick
            if -3 <= du <= 5:                   # mittig zum Schaft (der liegt bei du 0..1)
                return wood_l if t == -11 else wood_d if du in (-3, 5) else wood_m
            return None
        if t <= -5:                             # Holzschaft, 2 breit
            return {0: wood_l, 1: wood_d}.get(du)
        if t <= -3:                             # Muffe
            return sleeve if -1 <= du <= 1 else None
        if t <= 2:                              # Rohr, mit zusätzlicher Schattenkante
            return copper.get(du) or (copper['shade'] if du == 2 and t % 2 == 0 else None)
        if t <= 8:                              # Stahlkrone, so breit wie das Rohr
            return steel.get(du)
        if t <= 10:                             # Sägezähne: abwechselnd hell und dunkel
            if du in (-1, 1):
                return steel[-1] if (t + du) % 4 == 0 else steel[1]
            return None
        return None

    return outline(diag_shape(spec, (-12, 10)), '#1e140c')


def crystal_core_drill():
    # Stufe 2: Rohr mit Kristallquarz-Einlage, Krone aus Kristallquarz.
    tube = {-1: '#b8b6ff', 0: '#6f6cf0', 1: '#3d379e', 'shade': '#2c2470'}
    crown = {-1: '#f2f2ff', 0: '#a9a8ff', 1: '#4a43b8'}
    return core_drill(tube, crown, sleeve='#2c2638')


def deep_core_drill():
    # Stufe 3: Bernsteinrohr, Diamantkrone, dunkler Griff aus Zeitgestein-Holz.
    tube = {-1: '#ffd27a', 0: '#d9821c', 1: '#8a4710', 'shade': '#5a2a06'}
    crown = {-1: '#e9fffd', 0: '#6fe0d6', 1: '#1f8f88'}
    return core_drill(tube, crown, sleeve='#1b1624', wood=('#7a6a8a', '#584a68', '#3a2f48'))


def core_sample():
    # Breiter Bohrkern (5 px), Schichten als volle Querbänder in grauem Gestein.
    grey = ('#948ea0', '#76708a', '#57516a', '#433e52')
    bands = [  # (t von, t bis, hell, mittel, dunkel, sehr dunkel)
        (-7, -4, ('#9cc08e', '#77a06d', '#567d4f', '#42613d')),   # Sporenzeit
        (-1, 2, ('#e3ae6c', '#c38c4a', '#916434', '#704c26')),     # Bernstein
        (5, 7, ('#b19de3', '#8d76c6', '#68529c', '#4e3d78')),      # Kristallzeit
    ]

    def spec(t, du):
        if not -3 <= du <= 3:
            return None
        if t == -10 and du in (-3, 0, 1):        # zackiges Bruchende unten links
            return None
        if t == -9 and du == 3:
            return None
        if t >= 9:                               # Stirnfläche als Ellipse, heller
            if t == 10 and abs(du) >= 2:
                return None
            if t == 9 and abs(du) <= 1:
                return '#a798c4'                 # angedeuteter Schichtring
            return '#d0c9dc' if du <= 0 else '#b2aac2'
        colors = grey
        for lo, hi, c in bands:
            if lo <= t <= hi:
                colors = c
        light, mid, dark, darker = colors
        if du <= -2:
            return light
        if du == 2:
            return dark
        if du == 3:
            return darker
        if colors is grey and (t * 5 + du * 3) % 7 == 0:   # Körnung im Gestein
            return dark
        return mid

    return outline(diag_shape(spec, (-10, 10)), '#16121c')


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
        '....kkkkk..kk...',
        '................',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1a1450')


def amber_shard():
    # Kantiger Bernsteinsplitter (gerade Bruchkante oben rechts) mit eingeschlossenem Insekt.
    pal = {'k': '#6e380a', 'd': '#a2560f', 'm': '#d9821c', 'l': '#f6ad3e', 'y': '#ffd27a', 'w': '#fff3c8',
           'b': '#3e1e06', 'n': '#5c2e0a', 'B': '#9a5a1a'}
    rows = [
        '................',
        '..........w.....',
        '.........wyy....',
        '........wyyyy...',
        '.......wyymyl...',
        '......lyymmmld..',
        '.....lyymBmBmd..',
        '....lyymmmnmmd..',
        '....lymBnbbnBd..',
        '...lyymmmbnmmd..',
        '...lymBnbbnBdk..',
        '...lmmmmmnmmdk..',
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
        '...gqvqqqVVQh...',
        '....gqvvvVQh....',
        '.....gqvVQh.....',
        '......gvVh......',
        '......gqvh......',
        '.....gqvVQh.....',
        '....gqvvVVQh....',
        '...gqvvvVVVQh...',
        '...gvvvvvVVVh...',
        '..GGGGGGGGGGGG..',
        '..gggggggggggh..',
        '................',
    ]
    return outline(from_grid(rows, pal), '#3a2606')


def calcite_scale():
    # Drop des Geodenkriechers: gewölbte Calcitschuppe mit Wachstumsringen und violettem Schimmer am Rand.
    pal = {'w': '#fbf8f2', 'l': '#e6e0d6', 'm': '#cfc6b8', 'd': '#a89c8c', 'v': '#b48cff', 'V': '#7b55d6'}
    rows = [
        '................',
        '................',
        '.....wwwww......',
        '....wllllllv....',
        '...wllmmmmlll...',
        '...wlmlllllmld..',
        '..wllmlwwllmlv..',
        '..wlmlwllllmld..',
        '..wlmllmmmlmld..',
        '..vlmllllllmlV..',
        '...lllmmmmllld..',
        '...vlllllllldV..',
        '....dlllllldd...',
        '.....Vddddd.....',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#4a4054')


def spore_sac():
    # Drop des Sporenbalgs: praller Membranbeutel mit Adern, oben zugebunden, grüne Sporen schimmern durch.
    pal = {'w': '#efe7c8', 'l': '#e1d8b5', 'm': '#cfc39a', 'd': '#a99e7c', 'v': '#8c8162',
           'g': '#7fb069', 'G': '#4f7a3a', 'k': '#5b4a2e', 'o': '#c0502a'}
    rows = [
        '................',
        '.......kk.......',
        '......kook......',
        '.......kk.......',
        '.....lmmmmd.....',
        '....lwlmmmmd....',
        '...lwlmgmvmmd...',
        '...llmgGgmvmd...',
        '..lllmmgmmmvmd..',
        '..llmvmmmmgmmd..',
        '..lmmmvmmgGgmd..',
        '..dmmmmvmmgmdd..',
        '...dmmmmmmmdd...',
        '....ddmmmddd....',
        '......dddd......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#3a2f1c')


def germ_tonic():
    # Keimfrische: kleine Flasche mit hellgrüner, klarer Flüssigkeit und Bernsteinkorken.
    pal = {'a': '#e0a030', 'A': '#8a5a10', 'c': '#d7eef6', 'C': '#a8c8d8',
           'g': '#b8f0a0', 'G': '#7fd66a', 'h': '#4f9a3a', 'w': '#ffffff'}
    rows = [
        '................',
        '......aaaa......',
        '......aAAa......',
        '.......cC.......',
        '.......cC.......',
        '......cccC......',
        '.....cwggGC.....',
        '....cwgggGGC....',
        '....cgggGGGC....',
        '....cggGGGhC....',
        '....cgGGGGhC....',
        '....cGGGGhhC....',
        '.....cGGhhC.....',
        '......CCCC......',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#24402a')


def spore_bellows_spawn_egg():
    # Spawn-Ei: Membran mit Umbra-Kappe, orange-rotem Rand und Bernsteinpunkten.
    pal = {'u': '#5b4a2e', 'U': '#3a2f1c', 'o': '#c0502a', 'O': '#8f3a1d', 'a': '#e0a030', 'y': '#ffd27a',
           'm': '#cfc39a', 'l': '#e1d8b5', 'd': '#a99e7c'}
    rows = [
        '................',
        '......uuuu......',
        '.....uuyauU.....',
        '....uuaUuuuU....',
        '....uuuuuyaU....',
        '...oooooooooO...',
        '...lmmmmmmmmd...',
        '..lmmmmmmmmmmd..',
        '..lmmdmmmmdmmd..',
        '..lmmmmmmmmmmd..',
        '..lmmmmmmmmmdd..',
        '..dmmmmmmmmmdd..',
        '...dmmmmmmmdd...',
        '....ddmmmddd....',
        '......dddd......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#2a2116')


def ember_burrower_spawn_egg():
    # Spawn-Ei: aschegraue Schale mit glühenden Rissen und einem Glutkamm oben.
    pal = {'e': '#ffd27a', 'E': '#e0531c', 'r': '#9e2a0c', 'g': '#7d726a', 'G': '#948880', 'm': '#655c56',
           'd': '#4f4844', 'v': '#a8381a'}
    rows = [
        '................',
        '.......e........',
        '......eE.e......',
        '.....rEEeE......',
        '.....GGrrrg.....',
        '....GgggggggG...',
        '...GggvgggggmG..',
        '...ggggvggggmm..',
        '..Gggggvvgggmmd.',
        '..gggggggvggmmd.',
        '..gggvgggggmmmd.',
        '..ggggvvggmmmdd.',
        '...gggggvmmmdd..',
        '....mmmmmmmdd...',
        '......dddd......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1f1a18')


def stratolith_spawn_egg():
    # Spawn-Ei: Gesteinsschichten (Grundgestein, Myzel mit Grün, Calcit) mit Quarzspitze und goldenem Kern.
    pal = {'q': '#a9a8ff', 'Q': '#6f6cf0', 'c': '#dcd6c8', 'C': '#b3ad9f', 'g': '#4f7a3a', 'm': '#84757c',
           'M': '#6a5c64', 's': '#5e5868', 'S': '#3c3742', 'k': '#2c2736', 'y': '#ffd27a'}
    rows = [
        '................',
        '.......q........',
        '......qQ........',
        '......cQc.......',
        '.....cccCC......',
        '....gggggggg....',
        '...mmmmmmmmMM...',
        '...mmmmmmmmMM...',
        '..ssssssssssSS..',
        '..ssssyysssssS..',
        '..SSSSyySSSSSk..',
        '..sssssssssSSk..',
        '...SSSSSSSSSk...',
        '....kkkkkkkk....',
        '......kkkk......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1a1622')


def codex_fragment():
    # Drop des Steinzeugen: abgebrochene Steintafel mit Gesteinsschichten und goldener Sanduhr-Rune.
    pal = {'s': '#3c3742', 'S': '#5e5868', 'g': '#77707c', 'o': '#b08a5a', 'c': '#dcd6cf',
           'G': '#ffd86b', 'y': '#d9a43a'}
    rows = [
        '................',
        '...gggggg.......',
        '..gSSSSSSg......',
        '..gScccccSg.....',
        '..oooooooooo....',
        '..sSSSGGGSSSg...',
        '..sSSSSGSSSSSg..',
        '..sSSSSySSSSSg..',
        '..sSSSGGGSSSSg..',
        '..oooooooooooo..',
        '..sssssssssss...',
        '..sSSSSSSSSs....',
        '...ssssssss.....',
        '................',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1c1824')


def petrified_witness_spawn_egg():
    # Spawn-Ei: Gesteinsschichten mit goldener Ader und glühender Sanduhr in der Mitte.
    pal = {'s': '#3c3742', 'S': '#5e5868', 'g': '#77707c', 'o': '#b08a5a', 'c': '#dcd6cf',
           'G': '#ffd86b', 'y': '#e6b84a', 'n': '#1c1824'}
    rows = [
        '................',
        '......gggg......',
        '.....gSSSSS.....',
        '....gSSSSySs....',
        '....ooooyooo....',
        '...gSSSnnSSSs...',
        '...SSSSGGSSSs...',
        '..gSSSSnGSySSs..',
        '..cccccGGccccc..',
        '..sSSySnnSSSSs..',
        '..sSSSySSSSSss..',
        '..soooooooooss..',
        '...sSSSSSSSss...',
        '....ssSSSsss....',
        '......ssss......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1c1824')


# ---------------------------------------------------------------- Ausrüstung (Werkzeuge, Bernsteinklinge, Geodenrüstung)

GEAR_QUARTZ = {'w': '#f2f2ff', 'l': '#b8b6ff', 'm': '#7471f2', 'd': '#4a44c0', 'h': '#3c3742', 'H': '#77707c', 'c': '#dcd6cf'}
GEAR_AMBER = {'w': '#fff3c8', 'l': '#ffd27a', 'm': '#d9821c', 'd': '#a2560f', 'h': '#3c3742', 'H': '#77707c', 'c': '#5c2e0a', 'b': '#3e1e06'}
SWORD = [
    '................',
    '.............ww.',
    '............wlm.',
    '...........wlmd.',
    '..........wlmd..',
    '.........wlmd...',
    '........wlmd....',
    '.......wlmd.....',
    '...c..wlmd......',
    '....cwlmd.......',
    '.....cmd........',
    '....hHc.........',
    '...hH..c........',
    '..hH............',
    '.hh.............',
    '................',
]
AMBER_BLADE = [
    '................',
    '............wwl.',
    '...........wllm.',
    '..........wllmd.',
    '.........wlbmd..',
    '........wllmd...',
    '.......wllmd....',
    '......wllmd.....',
    '...c.wllmd......',
    '....cllmd.......',
    '.....cmd........',
    '....hHc.........',
    '...hH..c........',
    '..hH............',
    '.hh.............',
    '................',
]
PICKAXE = [
    '................',
    '.......wwwl.....',
    '.....wwllllmm...',
    '....wl......mm..',
    '...wl......hHmd.',
    '..wl......hH..md',
    '..l......hH....d',
    '..l.....hH.....d',
    '.......hH.......',
    '......hH........',
    '.....hH.........',
    '....hH..........',
    '...hH...........',
    '..hH............',
    '.hh.............',
    '................',
]
AXE = [
    '................',
    '........wwl.....',
    '......wwllmm....',
    '.....wllllmmd...',
    '....wlllmmmhHd..',
    '....llmmmdhH.d..',
    '.....lmmdhH.....',
    '......mdhH......',
    '.......hH.......',
    '......hH........',
    '.....hH.........',
    '....hH..........',
    '...hH...........',
    '..hH............',
    '.hh.............',
    '................',
]
SHOVEL = [
    '................',
    '..........wwl...',
    '.........wllmm..',
    '.........wllmmd.',
    '.........llmmmd.',
    '..........mmdd..',
    '.........hHd....',
    '........hH......',
    '.......hH.......',
    '......hH........',
    '.....hH.........',
    '....hH..........',
    '...hH...........',
    '..hH............',
    '.hh.............',
    '................',
]
HOE = [
    '................',
    '.......wwllmd...',
    '......wllmmmdd..',
    '......lm...hHd..',
    '......m...hH....',
    '.........hH.....',
    '........hH......',
    '.......hH.......',
    '......hH........',
    '.....hH.........',
    '....hH..........',
    '...hH...........',
    '..hH............',
    '.hh.............',
    '................',
    '................',
]
GEAR_GEODE = {'w': '#f8f5ec', 'c': '#ddd7c9', 'C': '#c8c1b2', 'k': '#ada697', 'v': '#64566a', 'V': '#4f4356',
         'q': '#a9a8ff', 'Q': '#6f6cf0', 'e': '#e6e6ff'}
HELMET = [
    '................',
    '.......e........',
    '......qQ........',
    '....wccqccC.....',
    '...wcccQcccC....',
    '...cccccccccC...',
    '...cCvvvvvvCk...',
    '...cV......Vk...',
    '...cV......Vk...',
    '...kV......Vk...',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
]
CHESTPLATE = [
    '................',
    '..wcC......Cck..',
    '.qwcCC....CCckQ.',
    '.qcccCCCCCCcckQ.',
    '..ccccccccccck..',
    '...cccvqQvccC...',
    '...ccccQQcccC...',
    '...ccccccccck...',
    '...cvvvvvvvvk...',
    '...ccccccccck...',
    '...ccccccccck...',
    '...cCCCCCCCCk...',
    '...kkkkkkkkkk...',
    '................',
    '................',
    '................',
]
LEGGINGS = [
    '................',
    '................',
    '...wccccccccC...',
    '...vvvvQqvvvV...',
    '...cccccccccC...',
    '...cccC..cccC...',
    '...cccC..cccC...',
    '...cccC..cccC...',
    '...cvvV..cvvV...',
    '...cccC..cccC...',
    '...cccC..cccC...',
    '...cccC..cccC...',
    '...kkkk..kkkk...',
    '................',
    '................',
    '................',
]
BOOTS = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '...wccC..wccC...',
    '...cccC..cccC...',
    '...cqcC..cqcC...',
    '...cccC..cccC...',
    '..vccccCvccccC..',
    '..vvvvVVvvvvVV..',
    '................',
    '................',
    '................',
    '................',
    '................',
]


def gear(rows, pal, edge):
    return lambda: outline(from_grid(rows, pal), edge)


GEAR = {
    'crystal_quartz_sword': gear(SWORD, GEAR_QUARTZ, '#1a1450'),
    'crystal_quartz_pickaxe': gear(PICKAXE, GEAR_QUARTZ, '#1a1450'),
    'crystal_quartz_axe': gear(AXE, GEAR_QUARTZ, '#1a1450'),
    'crystal_quartz_shovel': gear(SHOVEL, GEAR_QUARTZ, '#1a1450'),
    'crystal_quartz_hoe': gear(HOE, GEAR_QUARTZ, '#1a1450'),
    'amber_blade': gear(AMBER_BLADE, GEAR_AMBER, '#3a1b04'),
    'geode_helmet': gear(HELMET, GEAR_GEODE, '#231c2a'),
    'geode_chestplate': gear(CHESTPLATE, GEAR_GEODE, '#231c2a'),
    'geode_leggings': gear(LEGGINGS, GEAR_GEODE, '#231c2a'),
    'geode_boots': gear(BOOTS, GEAR_GEODE, '#231c2a'),
}


def armor_layers():
    """Geodenrüstung am Körper (64 x 32, Box-UV wie Vanilla): Calcit, violette Kanten, Kristallquarz-Akzente.

    Ebene 1 (humanoid): Helm (Kopf), Brustpanzer (Körper, Arme), Stiefel (untere Beinzeilen).
    Ebene 2 (humanoid_leggings): Gürtel (untere Körperzeilen) und Beine.
    """
    import random
    rnd = random.Random('geode_armor')
    calcite = ['#8f887c', '#ada697', '#c8c1b2', '#ddd7c9', '#efebe0']
    trim, trim_dark = '#64566a', '#4f4356'
    quartz = ['#4a43b8', '#6f6cf0', '#a9a8ff', '#e6e6ff']

    def box(u, v, w, h, d):
        return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'east': (u, v + d, d, h),
                'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h)}

    def fill(img, rect, rows=None, cols=None, step=0, trim_rows=(), gem=None):
        x0, y0, w, h = rect
        for y in range(h):
            if rows is not None and y not in rows:
                continue
            for x in range(w):
                if cols is not None and x not in cols:
                    continue
                if y in trim_rows:
                    c = trim if (x + y) % 3 else trim_dark
                else:
                    k = 2 + step + rnd.uniform(-0.6, 0.6) + (0.5 if y == 0 else 0) - (0.6 if y == h - 1 else 0)
                    c = calcite[max(0, min(4, int(round(k))))]
                img.putpixel((x0 + x, y0 + y), rgb(c))
        if gem:
            for gx, gy, gi in gem:
                img.putpixel((x0 + gx, y0 + gy), rgb(quartz[gi]))

    l1 = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    head = box(0, 0, 8, 8, 8)
    fill(l1, head['up'], step=1, gem=[(3, y, 2) for y in range(8)] + [(4, y, 1) for y in range(8)])
    fill(l1, head['north'], rows=range(0, 2), gem=[(3, 0, 3), (4, 0, 2), (3, 1, 1), (4, 1, 0)])
    fill(l1, head['north'], rows=range(2, 8), cols=(0, 7), trim_rows=(7,))
    for side in ('east', 'west'):
        fill(l1, head[side], rows=range(0, 7), step=-1, trim_rows=(6,))
    fill(l1, head['south'], rows=range(0, 7), trim_rows=(6,))
    body = box(16, 16, 8, 12, 4)
    fill(l1, body['up'], step=1)
    fill(l1, body['north'], trim_rows=(0, 10), gem=[(3, 3, 2), (4, 3, 3), (3, 4, 1), (4, 4, 2), (3, 5, 0), (4, 5, 1)])
    fill(l1, body['south'], trim_rows=(0, 10), step=-1)
    for side in ('east', 'west'):
        fill(l1, body[side], step=-1, trim_rows=(10,))
    arm = box(40, 16, 4, 12, 4)
    fill(l1, arm['up'], gem=[(x, y, 2 if (x + y) % 2 else 3) for x in range(1, 3) for y in range(1, 3)])
    for side in ('north', 'east', 'west', 'south'):
        fill(l1, arm[side], rows=range(0, 7), trim_rows=(6,), step=0 if side == 'north' else -1,
             gem=[(1, 0, 2), (2, 0, 1)] if side in ('east', 'west') else None)
    leg = box(0, 16, 4, 12, 4)
    for side in ('north', 'east', 'west', 'south'):
        fill(l1, leg[side], rows=range(8, 12), trim_rows=(8,), step=0 if side == 'north' else -1,
             gem=[(1, 10, 2), (2, 10, 1)] if side == 'north' else None)
    fill(l1, leg['down'], step=-1)

    l2 = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for side in ('north', 'east', 'west', 'south'):
        fill(l2, body[side], rows=range(8, 12), trim_rows=(8,), step=0 if side == 'north' else -1,
             gem=[(3, 9, 3), (4, 9, 2)] if side == 'north' else None)
    fill(l2, leg['up'])
    for side in ('north', 'east', 'west', 'south'):
        fill(l2, leg[side], rows=range(0, 9), trim_rows=(8,), step=0 if side == 'north' else -1,
             gem=[(1, 4, 2), (2, 4, 1)] if side == 'north' else None)
    return l1, l2


def geode_crawler_spawn_egg():
    # Spawn-Ei (ab 1.21.5 eigene Textur): Echsenhaut mit Calcitband und Kristallflecken.
    pal = {'d': '#4a4054', 'm': '#6d6078', 'l': '#918190', 'c': '#e6e0d6', 'C': '#bfb6a8',
           'v': '#b48cff', 'V': '#7b55d6', 'w': '#f2ecff'}
    rows = [
        '................',
        '......llll......',
        '.....lmmmmm.....',
        '....lmmvwmmd....',
        '....lmmVvmmd....',
        '...lmmmmmmmmd...',
        '...ccccccccCC...',
        '..lmmmmmmvwmmd..',
        '..lmvwmmmVvmmd..',
        '..lmVvmmmmmmmd..',
        '..lmmmmmmmmmdd..',
        '..dmmmmvwmmmdd..',
        '...dmmmVvmmdd...',
        '....ddmmmddd....',
        '......dddd......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#231c2a')


# ---------------------------------------------------------------- Ausgabe


# ---------------------------------------------------------------- Fossilien (Phase 3)

BONE = {'w': '#f6efd9', 'l': '#e6d8b4', 'm': '#cbb98f', 'd': '#9c8a64', 'k': '#5e5038'}
ROCK = {'w': '#a8a2ae', 'l': '#8c8694', 'm': '#706a78', 'd': '#55505d', 'k': '#3a3641'}


def fossil_chunk():
    # Rundlicher Gesteinsbrocken, aus dem eine Rippe und ein Spiralrand herausschauen.
    pal = dict(ROCK)
    pal.update({'B': BONE['l'], 'b': BONE['m'], 'n': BONE['d']})
    rows = [
        '................',
        '................',
        '.....llmm.......',
        '....lwllmmd.....',
        '...lwlmmBbmd....',
        '..lwllmBbnmmd...',
        '..llmmmbnmmdd...',
        '.lllmmmmmmmmdd..',
        '.llmBBbmmmmddd..',
        '.lmmmbbnmmmdddk.',
        '.lmmmmmnmmddddk.',
        '..mmmmmmdddddk..',
        '..dmmmdddddkk...',
        '...ddddddkk.....',
        '.....kkkk.......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1f1c24')


def chisel():
    # Flacher Stahlmeißel mit Holzgriff, diagonal.
    steel = {-1: '#eef3f7', 0: '#a9b4bf', 1: '#5d6670'}
    wood = {-1: '#b0804e', 0: '#83593a', 1: '#5a3b22'}

    def spec(t, du):
        if t <= -3:                              # Holzgriff, 3 breit
            return wood.get(du)
        if t == -2:                              # Zwinge
            return '#3b3b44' if -1 <= du <= 1 else None
        if t <= 6:                               # Stahlschaft, 2 breit
            return {0: steel[-1], 1: steel[1]}.get(du)
        if t <= 9:                               # Schneide, breiter und heller
            if -1 <= du <= 2:
                return steel[-1] if du <= 0 else steel[0] if du == 1 else steel[1]
        return None

    return outline(diag_shape(spec, (-12, 9)), '#1e140c')


def ammonite_fossil():
    # Spiralschale, cremefarben: klare dunkle Rille, Licht oben links, Mündung unten rechts.
    import math
    img = canvas()
    cx, cy, radius, turn = 7.6, 7.4, 6.7, 3.0
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if r > radius:
                continue
            light = -(dx + dy) / (radius * 1.41)
            angle = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            phase = (r / turn - angle) % 1.0
            if phase < 0.3 and r > 1.0:
                c = BONE['k'] if light < 0.25 else BONE['d']
            elif phase < 0.45:
                c = BONE['w'] if light > 0.0 else BONE['l']       # Grat neben der Rille
            else:
                c = BONE['l'] if light > 0.3 else BONE['m'] if light > -0.35 else BONE['d']
            img.putpixel((x, y), rgb(c))
    return outline(img, '#3a2f22')


def fern_fossil():
    # Flache Schieferplatte mit dunklem Farnwedel-Abdruck.
    pal = {'w': '#9fa7a2', 'l': '#87908b', 'm': '#717a75', 'd': '#5a625e', 'k': '#3e4542',
           'f': '#3b4a36', 'F': '#56704c'}
    rows = [
        '................',
        '...lllllllll....',
        '..lwwlllllllm...',
        '..lwl.F..llmmd..',
        '..ll.FfF.lmmmd..',
        '..lmFfFfFmmmmd..',
        '..lmmFfFlmmmmd..',
        '..lmFfFfFmmmmd..',
        '..lmmFfFmmmmmd..',
        '..lmFfFfFmmmdd..',
        '..lmmmfFmmmmdd..',
        '..lmmmmfmmmddd..',
        '..mmmmmmfmdddd..',
        '...dddddddddk...',
        '................',
        '................',
    ]
    img = from_grid([r.replace('.', '.') for r in rows], pal)
    # Lücken in der Platte (die Punkte im Wedel) mit Plattenfarbe füllen
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.' and 3 <= y <= 12 and 3 <= x <= 12:
                img.putpixel((x, y), rgb(pal['l'] if y < 5 else pal['m']))
    return outline(img, '#25292a')


def ancient_skull():
    # Seitenansicht eines Echsenschädels mit Glutrest in der Augenhöhle.
    pal = dict(BONE)
    pal.update({'e': '#2a1a12', 'g': '#e0531c', 'G': '#ffb24a', 't': '#fff8e6'})
    rows = [
        '................',
        '................',
        '....lllll.......',
        '...lwwllllm.....',
        '..lwllllmmmmd...',
        '..lleGllmmmmmd..',
        '..legeelmmmmmmd.',
        '..leeedlmmmmmmd.',
        '..lmmmddmmmddmmd',
        '...dmmmmmmdtdtdd',
        '....dddkdtdtdt..',
        '.....kddtdtd....',
        '......kkkk......',
        '................',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#2a2014')


def fossil_table_top():
    # Glatte Steinplatte mit Holzrahmen, darauf ein halb freigelegtes Ammonit und Pinselstriche.
    img = canvas()
    stone = ['#9a9a9a', '#a6a6a6', '#b2b2b2']
    n = tile_noise(91, 8)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), pick(stone, 0.3 + n[y][x] * 0.5))
    for i in range(16):
        for xy in ((i, 0), (0, i)):
            img.putpixel(xy, rgb('#b0804e'))
        for xy in ((i, 15), (15, i)):
            img.putpixel(xy, rgb('#5a3b22'))
    stamp(img, 4, 4, ['.mmm..', 'mlwlm.', 'mlkdlm', 'mdlkdm', '.mdddm', '..mmm.'], dict(BONE, k='#5e5038'))
    for x, y in [(11, 3), (12, 4), (11, 11), (12, 12), (3, 12)]:
        img.putpixel((x, y), rgb('#c9c4bb'))
    return img


def fossil_table_side():
    # Holzbeine und Zarge unter einer Steinplatte, mit Werkzeugen an der Seite.
    pal = {'s': '#b2b2b2', 'S': '#8a8a8a', 'w': '#b0804e', 'm': '#83593a', 'd': '#5a3b22', 'k': '#3a2614',
           'i': '#a9b4bf', 'I': '#5d6670'}
    rows = [
        'ssssssssssssssss',
        'SSSSSSSSSSSSSSSS',
        'wwwwwwwwwwwwwwww',
        'mmmmmmmmmmmmmmmm',
        'dddddddddddddddd',
        'wm.kkkkkkkkkk.wm',
        'wm.kiikkkkkkk.wm',
        'wm.kkIkkkwkkk.wm',
        'wm.kkkikkwkkk.wm',
        'wm.kkkkkkmkkk.wm',
        'wm.kkkkkkkkkk.wm',
        'wm............wm',
        'wm............wm',
        'wm............wm',
        'wd............wd',
        'dd............dd',
    ]
    img = from_grid(rows, pal)
    # Zwischenraum hinter den Beinen: dunkles Holz statt Loch (der Block ist voll)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.':
                img.putpixel((x, y), rgb('#2b1d10'))
    return img


def transplanter_side():
    # Rahmen aus Zeitgestein mit Goldsäulen; innen die drei Epochen-Schichten als Bänder (Asche, Sporen, Kristall).
    pal = {'A': '#6f6488', 'B': '#4c4262', 'C': '#2e2739', 'G': '#ffe08a', 'g': '#d9a43a', 'h': '#8f6414',
           'a': '#5b3a2e', 'e': '#e0531c', 's': '#567d4f', 'S': '#77a06d', 'v': '#8d76c6', 'V': '#b19de3', 'k': '#140f1e'}
    rows = [
        'AAAAAAAAAAAAAAAB',
        'AgGGGGGGGGGGGGhC',
        'AgkkkkkkkkkkkkhC',
        'AgVVvVVVVvVVVVhC',
        'AgvVVVvVVVVvVvhC',
        'AgvvvvvvvvvvvvhC',
        'AgSSsSSSSsSSSShC',
        'AgsSSsSSSSSsSshC',
        'AgsssssssssssshC',
        'AgaaeaaaaaeaaahC',
        'AgaaaaaaeaaaaahC',
        'AgaaaaaaaaaaaahC',
        'AgkkkkkkkkkkkkhC',
        'AghhhhhhhhhhhhhC',
        'ABCCCCCCCCCCCCCC',
        'BCCCCCCCCCCCCCCC',
    ]
    return from_grid(rows, pal)


def transplanter_top():
    # Goldrahmen, darin konzentrische Schichtringe um einen leuchtenden Zeitkern.
    import math
    img = canvas()
    rings = ['#5b3a2e', '#567d4f', '#8d76c6', '#b19de3', '#e6dcff']
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = '#6f6488' if (x == 0 or y == 0) else '#2e2739'
            elif x in (1, 14) or y in (1, 14):
                c = '#ffe08a' if (x == 1 or y == 1) else '#8f6414'
            else:
                r = math.hypot(x - 7.5, y - 7.5)
                c = rings[max(0, min(4, 4 - int(r / 1.4)))] if r < 6.3 else '#140f1e'
            img.putpixel((x, y), rgb(c))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), rgb('#d9a43a'))
    return img


def analysis_table_top():
    # Kupferplatte mit Holzrahmen; darauf ein Bohrkern im Halter und eine Lupe mit Glas.
    img = canvas()
    cu = ['#9c5a32', '#b8693a', '#c97a48']
    n = tile_noise(95, 8)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), pick(cu, 0.3 + n[y][x] * 0.5))
    for i in range(16):
        for xy in ((i, 0), (0, i)):
            img.putpixel(xy, rgb('#b0804e'))
        for xy in ((i, 15), (15, i)):
            img.putpixel(xy, rgb('#5a3b22'))
    # Bohrkern quer (Schichtfarben)
    for x, c in zip(range(3, 10), ['#567d4f', '#77a06d', '#c38c4a', '#e3ae6c', '#8d76c6', '#b19de3', '#948ea0']):
        img.putpixel((x, 4), rgb(c))
        img.putpixel((x, 5), rgb(c))
        img.putpixel((x, 6), rgb('#433e52'))
    # Lupe: Messingring, helles Glas, Griff
    ring = {(10, 8), (11, 8), (12, 8), (9, 9), (13, 9), (9, 10), (13, 10), (9, 11), (13, 11), (10, 12), (11, 12), (12, 12)}
    for xy in ring:
        img.putpixel(xy, rgb('#d9a43a'))
    for xy in ((10, 9), (11, 9), (12, 9), (10, 10), (11, 10), (12, 10), (10, 11), (11, 11), (12, 11)):
        img.putpixel(xy, rgb('#bfe8f0'))
    img.putpixel((10, 9), rgb('#ffffff'))
    for xy in ((8, 12), (7, 13), (6, 14)):
        img.putpixel(xy, rgb('#5a3b22'))
    return img


def analysis_table_side():
    # Holzgestell mit Kupferkante und Schublade mit Messinggriff.
    pal = {'c': '#c97a48', 'C': '#9c5a32', 'w': '#b0804e', 'm': '#83593a', 'd': '#5a3b22', 'k': '#2b1d10', 'g': '#d9a43a'}
    rows = [
        'cccccccccccccccc',
        'CCCCCCCCCCCCCCCC',
        'wwwwwwwwwwwwwwww',
        'mddddddddddddddm',
        'md.mmmmmmmmmm.dm',
        'md.mmmmggmmmm.dm',
        'md.mmmmmmmmmm.dm',
        'mddddddddddddddm',
        'wm............wm',
        'wm............wm',
        'wm............wm',
        'wm............wm',
        'wm............wm',
        'wm............wm',
        'wd............wd',
        'dd............dd',
    ]
    img = from_grid(rows, pal)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.':
                img.putpixel((x, y), rgb('#2b1d10'))
    return img


# ---------------------------------------------------------------- Epochen-Pflanzen (4 Stufen, ohne Umriss wie Vanilla)

THISTLE_PAL = {'g': '#8fae94', 'G': '#5e7d68', 'd': '#3a5444', 'q': '#5b3d8f', 'p': '#8a5fc8',
               'w': '#f2f2ff', 'l': '#b8b6ff', 'm': '#7471f2', 'k': '#4a44c0'}


def quartz_thistle(stage):
    # Distel aus der Kristallzeit: graugrüne, stachlige Blätter; reif öffnen sich die violetten Knospen
    # zu Köpfen aus Kristallquarz-Spitzen.
    grids = [
        ['................'] * 11 + [
            '................',
            '....g......g....',
            '....Gg....gG....',
            '.....G....G.....',
            '.....d....d.....'],
        ['................'] * 8 + [
            '........g.......',
            '..g.....G....g..',
            '..Gg....G...gG..',
            '...Gg..gGg.gG...',
            '....GgGdGGgG....',
            '.....dGdGdG.....',
            '......d.dd......',
            '......d..d......'],
        ['................'] * 4 + [
            '.....q.....q....',
            '....qpq...qpq...',
            '....qpq...qpq...',
            '.....G.....G....',
            '.g...G....G..g..',
            '.Gg..G....G.gG..',
            '..Gg.G...G.gG...',
            '...GgG...GgG....',
            '....GG..gGG.....',
            '.g...dG.Gd...g..',
            '.Gg..dGGd...gG..',
            '..Gd..dd...dG...'],
        [
            '...w.w....w.w...',
            '..lwlwl..lwlwl..',
            '...mlm....mlm...',
            '..qmkmq..qmkmq..',
            '...qpq....qpq...',
            '....G..w...G....',
            '....G.lwl..G....',
            '.g..G.mkm..G.g..',
            '.Gg.G.qpq.G.gG..',
            '..GgG..G..GgG...',
            '...GG..G.GG.....',
            '....GG.GGG......',
            '.g...dGGd....g..',
            '.Gg..dGGd...gG..',
            '..Gd..dd...dG...',
            '......dd........'],
    ]
    return from_grid(grids[stage], THISTLE_PAL)


FERN_PAL = {'c': '#b3cc6a', 'f': '#8db04a', 'F': '#5f8a2e', 'D': '#3d5c1d', 'r': '#6b4a22',
            'y': '#ffd27a', 'a': '#f6ad3e', 'A': '#d9821c'}


def resin_fern(stage):
    # Farn der Sporenzeit: erst eingerollte Triebe, dann Wedel; reif hängen Harztropfen (Bernstein) daran.
    grids = [
        ['................'] * 10 + [
            '.....cc.........',
            '....c..c...cc...',
            '....c.cF..c..c..',
            '.....FF...c.cF..',
            '......F....FF...',
            '......D....D....'],
        ['................'] * 8 + [
            '..cc........cc..',
            '.c..f......f..c.',
            '.c.fF.f..f.Ff.c.',
            '..fFFfF..FfFFf..',
            '...fFFF..FFFf...',
            '....DFF..FFD....',
            '.....DF..FD.....',
            '......D..D......'],
        ['................'] * 4 + [
            '.f............f.',
            'fFf..........fFf',
            '.Ff.f......f.fF.',
            'fFFfF..cc..FfFFf',
            '.fFFf.c..c.fFFf.',
            '..fFFf.fFf.fFf..',
            '...fFFfFFFfFf...',
            '....fFFFFFFf....',
            '.....DFFFFD.....',
            '......DrrD......',
            '......D..D......',
            '................'],
        [
            '.......f........',
            '.f....fFf.....f.',
            'fFf..f.F.f...fFf',
            '.Ff.fFfFfFf..fF.',
            'fFFfF.fFf.FffFFf',
            '.fFFfafFfFf.fFFf',
            '..fFFyfFFf.afFf.',
            '..afFFfFFfFyFf..',
            '..Ay.fFFFFfA.f..',
            '...A..fFFf..a...',
            '......DFFD..A...',
            '.....DFrrFD.....',
            '....fD.rr.Df....',
            '......D..D......',
            '......D..D......',
            '................'],
    ]
    return from_grid(grids[stage], FERN_PAL)


TUBER_PAL = {'e': '#86603f', 'E': '#5e412b', 'x': '#36241a', 'v': '#c2401a',
             'o': '#ffcf73', 'O': '#ff8a1f', 'R': '#c8380e', 'n': '#5a1e0a'}


def ember_tubers(stage):
    # Glutknollen der Aschezeit: aschgraue Blätter mit roten Adern; reif glühen die Knollen am Boden.
    grids = [
        ['................'] * 11 + [
            '................',
            '.....e....e.....',
            '....eEe..eEe....',
            '.....xE..Ex.....',
            '......x..x......'],
        ['................'] * 8 + [
            '................',
            '...ee......ee...',
            '..eEve....evEe..',
            '..EvEe.ee.eEvE..',
            '...xEeeEEeeEx...',
            '....xEExxEEx....',
            '.....xx..xx.....',
            '......x..x......'],
        ['................'] * 5 + [
            '....ee....ee....',
            '...eEve..evEe...',
            '.ee.EvEeeEvE.ee.',
            'eEve.xEEEEx.evEe',
            'EvEEe.xEEx.eEEvE',
            '.xEvEexEExeEvEx.',
            '..xxEEeEEeEExx..',
            '....xEEvvEEx....',
            '.....xEvvEx.....',
            '......xxxx......',
            '......x..x......'],
        ['................'] * 3 + [
            '....ee....ee....',
            '...eEve..evEe...',
            '.ee.EvEeeEvE.ee.',
            'eEve.xEEEEx.evEe',
            'EvEEe.xEEx.eEEvE',
            '.xEvEexEExeEvEx.',
            '..xxEEeEEeEExx..',
            '....xEEvvEEx....',
            '..nRn.xvvx.nRn..',
            '.nOoRn.xx.nRoOn.',
            '.ROooRnxxnRooOR.',
            '.nRORn.xx.nROOn.',
            '..nnn..x...nnn..'],
    ]
    return from_grid(grids[stage], TUBER_PAL)


# ---------------------------------------------------------------- Saatgut (Items mit Umriss)

def petrified_item():
    # Versteinerter Gegenstand: Brocken aus versteinertem Stein (5 Töne) mit goldener Zeit-Ader (2 Töne); oben
    # rechts ragt noch ein Stück Griff heraus (2 Töne), damit man sieht, dass etwas darin steckt.
    pal = {'k': PETRI[0], 'd': PETRI[1], 'm': PETRI[2], 'n': PETRI[3], 'l': PETRI[5],
           'g': '#d9a43a', 'y': '#ffe08a', 'h': '#b0804e', 'b': '#6b4a2a'}
    rows = [
        '................',
        '............hh..',
        '...........hhb..',
        '..........hhb...',
        '.....llllnhb....',
        '....lllnnnnmd...',
        '...llnnnynnmmd..',
        '..llnnnygnmmmd..',
        '..llnnnygnmmdd..',
        '..lnnnmmgymmdk..',
        '..lnnmmmmgymdk..',
        '..nnmmmmmgmddk..',
        '..nmmmmddmddkk..',
        '...mmdddddkkk...',
        '....ddkkkkk.....',
        '................',
    ]
    return outline(from_grid(rows, pal), '#231c2a')


def time_rift_frames():
    # Zeitriss: senkrechter, gezackter Spalt mit fast schwarzem Kern, violetten Rändern (Mod-Farbe) und goldenen
    # Funken. Vier Bilder, in denen Rand und Funken flackern (Animation über .mcmeta).
    pal = {'k': '#0d0820', 'K': '#1f1240', 'v': '#4b2f9a', 'V': '#7a4fd6', 'l': '#b48cff', 'g': '#d9a43a', 'y': '#ffe08a'}
    # Breite des Kerns pro Zeile (Mitte bei x = 7,5), leicht gezackt.
    core = [0, 1, 1, 2, 2, 1, 2, 3, 3, 2, 2, 1, 2, 1, 1, 0]
    shift = [0, 0, 1, 1, 0, 0, -1, -1, 0, 0, 1, 1, 0, -1, -1, 0]
    frames = []
    for f in range(4):
        rnd = random.Random(900 + f)
        rows = []
        for y in range(16):
            row = ['.'] * 16
            c = 7 + shift[y]
            w = core[y]
            for x in range(c - w, c + w + 1):
                row[x] = 'k' if abs(x - c) < w else 'K'
            for side in (c - w - 1, c + w + 1):
                if 0 <= side < 16:
                    row[side] = 'V' if (y + f) % 3 else 'l'
            for side in (c - w - 2, c + w + 2):
                if 0 <= side < 16 and rnd.random() < 0.55:
                    row[side] = 'v'
            rows.append(''.join(row))
        img = from_grid(rows, pal)
        for _ in range(3):                                     # Funken neben dem Spalt
            x, y = rnd.choice([(3, rnd.randrange(2, 14)), (12, rnd.randrange(2, 14)), (rnd.randrange(4, 12), 0)])
            img.putpixel((x, y), rgb(pal['y'] if rnd.random() < 0.5 else pal['g']))
        frames.append(img)
    return frames


def display_case_base():
    # Sockel der Vitrine: poliertes Zeitgestein mit Goldleiste oben und unten und eingelassener Schichtlinie.
    pal = {'g': '#d9a43a', 'y': '#ffe08a', 'G': '#9c6f1e', 'a': '#5c5468', 'b': '#4a4356', 'c': '#3a3446', 'd': '#2c2736'}
    rows = ['................'] * 11 + [
        'yyyyyyyyyyyyyyyy',
        'aaabaaaaabaaaaba',
        'bbbbccbbbbbccbbb',
        'cddcccccdcccccdc',
        'GGGGGGGGGGGGGGGG',
    ]
    img = from_grid(rows, pal)
    # Obere Hälfte (vom Modell nicht genutzt) neutral füllen, damit die Kachel vollständig ist.
    for y in range(11):
        for x in range(16):
            img.putpixel((x, y), rgb(pal['b']))
    return img


def display_case_base_top():
    # Oberseite des Sockels (unter dem Glas sichtbar): dunkler Samt auf Zeitgestein mit Goldrand.
    pal = {'y': '#ffe08a', 'g': '#d9a43a', 'r': '#5a1f2e', 'R': '#46172a', 'k': '#2c2736'}
    rows = ['ggggggggggggggggg'[:16]] + ['g' + ('rR' * 8)[:14] + 'g' if y % 2 else 'g' + ('Rr' * 8)[:14] + 'g' for y in range(14)] + ['gggggggggggggggg']
    return from_grid(rows, pal)


def display_case_trim():
    # Goldener Rahmen auf der Glashaube, innen frei.
    pal = {'y': '#ffe08a', 'g': '#d9a43a', 'G': '#9c6f1e'}
    rows = ['yyyyyyyyyyyyyyyy'] + ['gG............Gg'] * 14 + ['GGGGGGGGGGGGGGGG']
    return from_grid(rows, pal)


def time_essence():
    # Zeitessenz: Tropfen verdichteter Zeit mit Spitze oben; violetter Kern, goldene Spiegelung
    # schräg oben links (Licht von oben links). Keine losen Pixel außerhalb der Silhouette.
    pal = {'w': '#fff6d8', 'y': '#ffe08a', 'g': '#d9a43a', 'l': '#b48cff', 'v': '#7a4fd6', 'V': '#4b2f9a', 'k': '#2a1a5c'}
    rows = [
        '................',
        '................',
        '.......l........',
        '......lll.......',
        '......lwl.......',
        '.....lwlvl......',
        '....lyglvvl.....',
        '....lwgVVVv.....',
        '...lwvVVVVVv....',
        '...lvVVVVVVv....',
        '...lvVVVVVVv....',
        '...lvVVVVVVv....',
        '....vVVVVVv.....',
        '.....vVVVv......',
        '......kkk.......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1a0f3a')


def layer_core():
    # Schichtkern: schräger Bohrkern mit drei Schichten (Kristall, Myzel mit Grün, Grundgestein) und Goldkappen.
    pal = {'y': '#ffe08a', 'g': '#d9a43a', 'G': '#9c6a12', 'q': '#a9a8ff', 'Q': '#6f6cf0', 'm': '#84757c',
           'M': '#6a5c64', 'v': '#4f7a3a', 's': '#5e5868', 'S': '#3c3742', 'k': '#2c2736'}
    rows = [
        '................',
        '..........yyg...',
        '.........yggG...',
        '........qqgGQ...',
        '.......qqqQQ....',
        '......vqqQQ.....',
        '.....vmmmM......',
        '....vmmmMM......',
        '....mmmMM.......',
        '...sssSS........',
        '..ssssSk........',
        '..sssSk.........',
        '.yggGk..........',
        '.ggGG...........',
        '..G.............',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1a1622')


def heart_of_deep_time():
    # Herz der Tiefenzeit: Herz aus Gesteinsschichten mit goldener Sanduhr in der Mitte und violettem Glanz.
    pal = {'y': '#ffe08a', 'g': '#d9a43a', 'G': '#9c6a12', 'l': '#b48cff', 'v': '#7a4fd6', 'V': '#4b2f9a',
           's': '#77707c', 'S': '#5e5868', 'k': '#3c3742'}
    rows = [
        '................',
        '................',
        '...lvv....vvl...',
        '..lvvVV..vVVvV..',
        '..vvssSSssSSVV..',
        '..vsggggggggSV..',
        '..vss.yyyy.SSV..',
        '..VSS..yy..SSk..',
        '...SS..yg..Sk...',
        '...kSS.gGGSSk...',
        '....kSgGGGSk....',
        '.....kGGGGk.....',
        '......kGGk......',
        '.......kk.......',
        '................',
        '................',
    ]
    img = from_grid(rows, pal)
    for x, y in ((6, 6), (9, 6), (5, 7), (6, 7), (9, 7), (10, 7), (6, 8), (9, 8), (6, 9)):
        img.putpixel((x, y), rgb('#2a1a5c'))                 # dunkles Glas der Sanduhr
    return outline(img, '#14101e')


def quartz_thistle_seeds():
    # Drei graugrüne Samenkapseln der Distel, aus deren Spitze ein kleiner Kristall schaut (nicht wie Kristallquarz).
    pal = {'g': '#a4bfa8', 'G': '#6f8f78', 'd': '#3f5a48', 'k': '#4a44c0', 'm': '#7471f2', 'l': '#c8c6ff'}
    rows = [
        '................',
        '................',
        '..........l.....',
        '.........lmk....',
        '.........gGk....',
        '....l...gGGd....',
        '...lmk..gGGd....',
        '...gGk..gGGd....',
        '..gGGd...gd.....',
        '..gGGd.....l....',
        '..gGGd....lmk...',
        '...gd.....gGk...',
        '.........gGGd...',
        '.........gGGd...',
        '..........gd....',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1c2a20')


def resin_fern_spores():
    # Ein Farnblättchen von unten, mit braunen Sporenhäufchen und einem Harztropfen an der Spitze.
    pal = {'c': '#b3cc6a', 'f': '#8db04a', 'F': '#5f8a2e', 'D': '#3d5c1d', 'r': '#6b4a22',
           's': '#7a4a1c', 'S': '#4e2e10', 'y': '#ffd27a', 'a': '#f6ad3e'}
    rows = [
        '................',
        '................',
        '...........ya...',
        '..........fFa...',
        '.........fsFF...',
        '........fFsFf...',
        '.......fsFFSf...',
        '......fFsFsf....',
        '.....fsFFSFf....',
        '....fFsFsFf.....',
        '....fsFFSf......',
        '...fFFsFf.......',
        '...DFFFf........',
        '..rD.DD.........',
        '.r..............',
        '................',
    ]
    return outline(from_grid(rows, pal), '#22300f')


def ember_tuber():
    # Knolle mit aschgrauer Kruste, durch die Risse glüht es orange.
    pal = {'n': '#5a1e0a', 'R': '#c8380e', 'O': '#ff8a1f', 'o': '#ffcf73', 'x': '#3d3a2f', 'E': '#615b49',
           'e': '#8a8470', 'g': '#7a7563'}
    rows = [
        '................',
        '................',
        '................',
        '.......x........',
        '......xEx.......',
        '.....eegEEx.....',
        '....eggEERnE....',
        '...egEEROoREx...',
        '...gEERooORExx..',
        '..egERoORnEEEx..',
        '..gEEnRREEROnx..',
        '..EEEEEEERoOEx..',
        '...xEEgEERnEx...',
        '....xxEEEExx....',
        '......xxxx......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1c140c')


BLOCKS = {
    'chronostone': chronostone,
    'crystal_quartz_ore': crystal_quartz_ore,
    'amber_ore': amber_ore,
    'petrified_stone': petrified_stone,
    'epoch_gate': epoch_gate,
    'volcanic_ash': volcanic_ash,
    'ember_ore': ember_ore,
    'fossil_table_top': fossil_table_top,
    'fossil_table_side': fossil_table_side,
    'transplanter_top': transplanter_top,
    'transplanter_side': transplanter_side,
    'analysis_table_top': analysis_table_top,
    'analysis_table_side': analysis_table_side,
    'display_case_base': display_case_base,
    'display_case_base_top': display_case_base_top,
    'display_case_trim': display_case_trim,
}
for _crop in (quartz_thistle, resin_fern, ember_tubers):
    for _i in range(4):
        BLOCKS[f'{_crop.__name__}_stage{_i}'] = (lambda c, i: lambda: c(i))(_crop, _i)
def resonant_splinter():
    # Resonanzsplitter: schräger Quarzsplitter einer Stimmgabel mit goldenem Ringband; Licht von oben links.
    pal = {'w': '#e6e6ff', 'l': '#a9a8ff', 'q': '#6f6cf0', 'Q': '#4a43b8', 'v': '#2c2670',
           'y': '#ffe08a', 'g': '#d9a43a', 'G': '#9c6a12', 'k': '#6a4708'}
    rows = [
        '................',
        '............wl..',
        '...........wlqv.',
        '..........wlqQv.',
        '.........wlqQv..',
        '........wlqQv...',
        '.......wlqQv....',
        '......ylqQv.....',
        '.....yggQv......',
        '....ygGGv.......',
        '....gGGk........',
        '...lqQk.........',
        '..lqQv..........',
        '..qQv...........',
        '...v............',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1a1442')


def resonant_shard_spawn_egg():
    # Spawn-Ei: Calcit-Ei mit Gesicht, zwei Quarz-Zinken oben, goldenem Stimmring und Quarzspitze unten.
    pal = {'c': '#dcd6c8', 'w': '#efebe0', 'C': '#ada697', 'y': '#ffe08a', 'g': '#d9a43a', 'G': '#9c6a12',
           'l': '#a9a8ff', 'q': '#6f6cf0', 'e': '#6f6cf0'}
    rows = [
        '................',
        '.....lq..lq.....',
        '.....lq..lq.....',
        '.....lqqqlq.....',
        '.....wcccccC....',
        '....wwcccccCC...',
        '...wcceccecCC...',
        '...ccccccccCC...',
        '..yyyyyyyyyyyg..',
        '..gggggggggggG..',
        '...ccccccccCC...',
        '...cccccccCCC...',
        '....ccccCCCC....',
        '.....CCCCCC.....',
        '.......qq.......',
        '................',
    ]
    return outline(from_grid(rows, pal), '#2a2238')


ITEMS = {
    'core_drill': core_drill,
    'crystal_core_drill': crystal_core_drill,
    'deep_core_drill': deep_core_drill,
    'ember_shard': ember_shard,
    'fossil_chunk': fossil_chunk,
    'chisel': chisel,
    'ammonite_fossil': ammonite_fossil,
    'fern_fossil': fern_fossil,
    'ancient_skull': ancient_skull,
    'core_sample': core_sample,
    'crystal_quartz': crystal_quartz,
    'amber_shard': amber_shard,
    'time_anchor': time_anchor,
    'calcite_scale': calcite_scale,
    'spore_sac': spore_sac,
    'germ_tonic': germ_tonic,
    'codex_fragment': codex_fragment,
    'quartz_thistle_seeds': quartz_thistle_seeds,
    'petrified_item': petrified_item,
    'time_essence': time_essence,
    'layer_core': layer_core,
    'heart_of_deep_time': heart_of_deep_time,
    'resin_fern_spores': resin_fern_spores,
    'ember_tuber': ember_tuber,
    'resonant_splinter': resonant_splinter,
}
SPAWN_EGGS = {
    'geode_crawler_spawn_egg': geode_crawler_spawn_egg,
    'spore_bellows_spawn_egg': spore_bellows_spawn_egg,
    'petrified_witness_spawn_egg': petrified_witness_spawn_egg,
    'ember_burrower_spawn_egg': ember_burrower_spawn_egg,
    'stratolith_spawn_egg': stratolith_spawn_egg,
    'resonant_shard_spawn_egg': resonant_shard_spawn_egg,
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
    for name, fn in GEAR.items():
        img = fn()
        path = os.path.join(OUT, 'item', name + '.png')
        img.save(path)
        images[('item', name)] = img
    # Zeitriss: vier Bilder untereinander, abgespielt über die .mcmeta daneben.
    frames = time_rift_frames()
    strip = Image.new('RGBA', (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, 16 * i))
    strip.save(os.path.join(OUT, 'block', 'time_rift.png'))
    with open(os.path.join(OUT, 'block', 'time_rift.png.mcmeta'), 'w') as fh:
        fh.write('{\n  "animation": {\n    "frametime": 3,\n    "interpolate": false\n  }\n}\n')
    images[('block', 'time_rift')] = frames[0]
    # Rüstung am Körper: 1.21.1 textures/models/armor/<name>_layer_1/2.png, ab 1.21.2 entity/equipment/...
    l1, l2 = armor_layers()
    epochs = os.path.join(os.path.dirname(__file__), '..', 'epochs', 'src')
    for path, img in (
            (('reg_1_21_1', 'textures', 'models', 'armor', 'geode_layer_1.png'), l1),
            (('reg_1_21_1', 'textures', 'models', 'armor', 'geode_layer_2.png'), l2),
            (('reg_1_21_11_plus', 'textures', 'entity', 'equipment', 'humanoid', 'geode.png'), l1),
            (('reg_1_21_11_plus', 'textures', 'entity', 'equipment', 'humanoid_leggings', 'geode.png'), l2)):
        full = os.path.join(epochs, path[0], 'resources', 'assets', 'strata_epochs', *path[1:])
        os.makedirs(os.path.dirname(full), exist_ok=True)
        img.save(full)
    # Spawn-Eier haben erst ab 1.21.5 eigene Texturen (1.21.1 färbt eine Vorlage ein).
    for name, fn in SPAWN_EGGS.items():
        img = fn()
        path = os.path.join(OUT_EGGS, name + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        images[('item', name)] = img
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
