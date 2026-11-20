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
    'crystal_quartz_pickaxe': lambda: v2_pickaxe(),
    'crystal_quartz_axe': lambda: v2_axe(),
    'crystal_quartz_shovel': lambda: v2_shovel(),
    'crystal_quartz_hoe': lambda: v2_hoe(),
    'amber_blade': lambda: v2_amber_blade(),
    'geode_helmet': lambda: armor(V2_HELMET)(),
    'geode_chestplate': lambda: armor(V2_CHESTPLATE)(),
    'geode_leggings': lambda: armor(V2_LEGGINGS)(),
    'geode_boots': lambda: armor(V2_BOOTS)(),
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


# Harzfalle: klebriger Bernsteinharz (dunkler, mit Glanzschlieren und Tropfen oben), dann mit Motte, dann ausgehärtet.
RESIN = ['#5a2a06', '#7a3c08', '#9c4f0c', '#bd6a14', '#d9821c', '#f0a23a']
HARD_AMBER = ['#8a4a0c', '#b4640f', '#d4831e', '#eda23a', '#f8c45c', '#ffe08a']
MOTH_SILHOUETTE = [
    '..dd.....dd.',
    '.dmmd.b.dmmd',
    'dmmmdbbbdmmm',
    '.dmmdbbbdmmd',
    '..dd.bbb.dd.',
    '......b.....',
]


def _resin(seed, pal, smooth):
    img = canvas()
    rnd = random.Random(seed)
    coarse = tile_noise(seed + 1, 8)
    fine = tile_noise(seed + 2, 4)
    for y in range(16):
        for x in range(16):
            v = 0.5 + (coarse[y][x] - 0.5) * (0.35 if smooth else 0.55) + (fine[y][x] - 0.5) * 0.25
            v += rnd.uniform(-0.05, 0.05) if smooth else rnd.uniform(-0.12, 0.12)
            img.putpixel((x, y), pick(pal, v))
    return img


def _moth_inside(img, dark, mid, body):
    for dy, row in enumerate(MOTH_SILHOUETTE):
        for dx, ch in enumerate(row):
            if ch == 'd':
                img.putpixel((2 + dx, 5 + dy), rgb(dark))
            elif ch == 'm':
                img.putpixel((2 + dx, 5 + dy), rgb(mid))
            elif ch == 'b':
                img.putpixel((2 + dx, 5 + dy), rgb(body))


def resin_trap():
    img = _resin(401, RESIN, smooth=False)
    for x, y in [(2, 3), (3, 4), (9, 2), (10, 3), (12, 9), (5, 11)]:   # Glanzschlieren
        img.putpixel((x, y), rgb('#f6ad3e'))
    for x in (4, 11):                                                  # Tropfen
        img.putpixel((x, 0), rgb('#f0a23a'))
        img.putpixel((x, 1), rgb('#d9821c'))
    return img


def resin_trap_moth():
    img = resin_trap()
    _moth_inside(img, '#2a1c12', '#6a3606', '#3b2818')
    return img


def resin_trap_hardened():
    img = _resin(403, HARD_AMBER, smooth=True)
    _moth_inside(img, '#5a2a06', '#a2560f', '#4f3620')
    for i in range(5):                                                 # polierter Glanz oben links
        img.putpixel((1 + i, 1 + i // 2), rgb('#fff3c8'))
    img.putpixel((1, 2), rgb('#fff3c8'))
    return img


def amber_inclusion():
    # Bernstein mit Einschluss: runder, polierter Bernstein, darin eine Motte; Glanz oben links.
    pal = {'w': '#fff3c8', 'y': '#ffd27a', 'l': '#f6ad3e', 'm': '#d9821c', 'd': '#a2560f', 'k': '#5a2a06',
           'b': '#3b2818', 'n': '#6a3606'}
    rows = [
        '................',
        '................',
        '.....lllll......',
        '....lwyllllm....',
        '...lwyllllllmd..',
        '...lyllllllmmd..',
        '..llnnllbllnnmd.',
        '..lnnnnbbbnnnmd.',
        '..llnnlbbbnnmmd.',
        '..lllllbbbmmmmd.',
        '...lllllbmmmmd..',
        '...mmmmmmmmmdd..',
        '....mmmmmmddd...',
        '.....dddddkk....',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#3a1a04')


def amber_moth_spawn_egg():
    # Spawn-Ei: dunkles Pelzbraun mit zwei bernsteinfarbenen Flügelflecken und hellem Kragen.
    pal = {'f': '#4f3620', 'F': '#3b2818', 'd': '#2a1c12', 'c': '#c9a77a', 'a': '#eda23a', 'A': '#d07a1a', 'y': '#ffe08a'}
    rows = [
        '................',
        '......ffff......',
        '.....fccccF.....',
        '....ffffffFF....',
        '...fAAffffAAF...',
        '...AaaAffAaaAF..',
        '..fAayAffAyaAF..',
        '..fAaaAffAaaAFd.',
        '..ffAAffffAAFFd.',
        '..fffffffffFFFd.',
        '..fffffffFFFFdd.',
        '...ffffFFFFFdd..',
        '....FFFFFFFdd...',
        '.....dddddd.....',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#1b1110')


def resin_trail():
    # Frische Harzspur: klebriger, glänzender Harz, kachelbar, mit hellen Glanzschlieren.
    img = _resin(411, RESIN[1:], smooth=False)
    for x, y in [(2, 2), (3, 2), (9, 5), (10, 5), (5, 10), (12, 12), (13, 12), (7, 14)]:
        img.putpixel((x, y), rgb('#ffcf6a'))
    return img


def amber_plate():
    # Ausgehärtete Bernsteinplatte: glatt, poliert, mit feinen dunklen Fugen (wie Platten), kachelbar.
    img = _resin(412, HARD_AMBER, smooth=True)
    for i in range(16):
        img.putpixel((i, 7), rgb('#a2560f'))
        img.putpixel((7 if i < 8 else 11, i), rgb('#a2560f'))
    for x, y in [(2, 2), (3, 3), (12, 10), (13, 11), (4, 12)]:
        img.putpixel((x, y), rgb('#fff3c8'))
    return img


def resin_snail_spawn_egg():
    # Spawn-Ei: olivbraune Haut unten, Bernstein-Gehäuse mit Spirale oben, zwei Augenstiele.
    pal = {'s': '#8a7244', 'S': '#6b5530', 'd': '#4a3a1e', 'a': '#e08a26', 'A': '#c26a14', 'k': '#7a3c08',
           'y': '#ffcf6a', 'e': '#1b1110'}
    rows = [
        '................',
        '.....e...e......',
        '.....s...s......',
        '.....s.aaaaa....',
        '....ssaayaaaA...',
        '...ssaakkkkaAA..',
        '...ssakaaakaAA..',
        '...ssakakkaAAA..',
        '..sssakaaaAAAk..',
        '..sssaakkkAAkk..',
        '..ssssaaAAAkk...',
        '..ssssssSSSSd...',
        '...SSSSSSSSdd...',
        '....ddddddd.....',
        '................',
        '................',
    ]
    return outline(from_grid(rows, pal), '#2a1c12')


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
    'resin_trap': lambda: v2_resin_trap(),
    'resin_trap_moth': lambda: v2_resin_trap_moth(),
    'resin_trap_hardened': lambda: v2_resin_trap_hardened(),
    'resin_trail': resin_trail,
    'amber_plate': amber_plate,
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


# ---------------------------------------------------------------- Überarbeitung 11/2026 (einheitlicher Item-Stil)
#
# Formen werden als Masken mit Material-Buchstaben gezeichnet; shade() setzt Licht und Schatten einheitlich
# (Licht oben links: freie Kante oben/links = heller, unten/rechts = dunkler), danach eine farbige Kontur je Item.
# Rampen: (Glanz, hell, mittel, dunkel).

R_WOOD = ('#c49a6c', '#9a7048', '#6f4c2e', '#4a3220')
R_QUARTZ = ('#f2f2ff', '#b8b6ff', '#7471f2', '#4a44c0')
R_QUARTZ_DARK = ('#b8b6ff', '#7471f2', '#4a44c0', '#2c2470')
R_IRON = ('#f4f7fa', '#c5ced6', '#8d98a3', '#5d6670')
R_IRON_DARK = ('#c5ced6', '#8d98a3', '#5d6670', '#3b4148')
R_TEAL = ('#e9fffd', '#8ff0e6', '#3fc7b8', '#1f6f6a')
R_TEAL_DARK = ('#8ff0e6', '#3fc7b8', '#1f6f6a', '#124440')
R_AMBER = ('#fff3c8', '#ffd27a', '#d9821c', '#a2560f')
R_SLATE = ('#9a8fa6', '#776c84', '#584e66', '#3b3348')


def shade(mask, mats):
    """mask: {(x, y): material}; mats: material -> Rampe. Gibt ein Bild mit Licht von oben links zurück."""
    img = canvas()
    for (x, y), m in mask.items():
        tl = (mask.get((x, y - 1)) != m) + (mask.get((x - 1, y)) != m)
        br = (mask.get((x, y + 1)) != m) + (mask.get((x + 1, y)) != m)
        score = tl - br
        ramp = mats[m]
        c = ramp[0] if score >= 2 else ramp[1] if score == 1 else ramp[2] if score == 0 else ramp[3]
        img.putpixel((x, y), rgb(c))
    return img


def grid_mask(rows):
    mask = {}
    for y, row in enumerate(rows):
        assert len(row) == 16, (len(row), row)
        for x, ch in enumerate(row):
            if ch != '.':
                mask[(x, y)] = ch
    return mask


def handle_mask(top_row, bottom_row=14, mat='h'):
    """Diagonaler Stiel, 2 px breit, von unten links bis top_row (wie bei Vanilla-Werkzeugen)."""
    mask = {}
    for r in range(top_row, bottom_row + 1):
        mask[(15 - r, r)] = mat
        mask[(16 - r, r)] = mat
    return mask


def tool(head_rows, handle_top, mats, edge):
    mask = handle_mask(handle_top)
    mask.update(grid_mask(head_rows))
    return outline(shade(mask, mats), edge)


TOOL_MATS = {'h': R_WOOD, 'q': R_QUARTZ}
R_EDGE = ('#ffffff', '#ffffff', '#e6e6ff', '#b8b6ff')   # helle Schneide


def v2_pickaxe():
    # Kopf: Bogen über die obere rechte Ecke, symmetrisch zur Stielachse (Spiegelung (x, y) -> (15-y, 15-x)).
    top = {1: [(4, 10)], 2: [(2, 12)], 3: [(1, 4), (9, 12)], 4: [(1, 3), (11, 12)], 5: [(1, 2)]}
    mask = handle_mask(5, 14)
    for y, spans in top.items():
        for a, b in spans:
            for x in range(a, b + 1):
                mask[(x, y)] = 'q'
                mask[(15 - y, 15 - x)] = 'q'
    return outline(shade(mask, TOOL_MATS), '#1a1450')


def v2_axe():
    # Kopf quer zum Stiel am oberen Ende, ragt nach oben links; flache Rückseite am Stiel, helle gebogene Schneide.
    return tool([
        '................',
        '.......eqq......',
        '......eqqqq.....',
        '.....eqqqqqq....',
        '.....eqqqqq.....',
        '......eqqq......',
        '.......ee.......',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
    ], 3, {**TOOL_MATS, 'e': R_EDGE}, '#1a1450')


def v2_shovel():
    # Blatt als gedrehtes Quadrat (6 x 6) mit dunkler Mittelrinne vom Stielansatz.
    mask = handle_mask(9, 14)
    cx, cy = 11.5, 4.5
    for y in range(16):
        for x in range(16):
            if abs(x - cx) + abs(y - cy) <= 4.0:
                mask[(x, y)] = 'q'
    for x, y in ((8, 8), (9, 7), (10, 6), (11, 5)):
        mask[(x, y)] = 'g'
    mats = {**TOOL_MATS, 'g': ('#4a44c0', '#3a32a0', '#2c2670', '#1a1450')}
    return outline(shade(mask, mats), '#1a1450')


def v2_hoe():
    return tool([
        '................',
        '.....qqqqqqqqq..',
        '....qqqqqqqqqqq.',
        '....qqq.....hh..',
        '....qq..........',
        '....q...........',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
        '................',
    ], 3, TOOL_MATS, '#1a1450')


def diag_mask(spec, t_range, u_center=15):
    """Wie diag_shape, aber spec(t, du) gibt einen Material-Buchstaben zurück (für shade)."""
    mask = {}
    for y in range(16):
        for x in range(16):
            t, du = x - y, x + y - u_center
            if t_range[0] <= t <= t_range[1]:
                m = spec(t, du)
                if m:
                    mask[(x, y)] = m
    return mask


def v2_chisel():
    # Holzgriff (3 breit), 1-px-Zwinge, kurzer Stahlschaft, flache 3 px breite Klinge mit heller Schneide.
    def spec(t, du):
        if t <= -4:
            return 'h' if -1 <= du <= 1 else None
        if t == -3:
            return 'f' if -1 <= du <= 1 else None
        if t <= 1:
            return 's' if 0 <= du <= 1 else None
        if t <= 6:
            return 'e' if t == 6 and -1 <= du <= 1 else ('b' if -1 <= du <= 1 else None)
        return None
    mats = {'h': R_WOOD, 'f': ('#5a5a66', '#4a4a55', '#3b3b44', '#2a2a32'), 's': R_IRON_DARK, 'b': R_IRON,
            'e': ('#ffffff', '#ffffff', '#eef3f7', '#c5ced6')}
    return outline(shade(diag_mask(spec, (-12, 6)), mats), '#1e1a22')


def drill(tier):
    """Kernbohrer: Kurbel am Griffende (kein Querbalken, sonst wirkt er wie ein Schwert), Holzgriff, Gehäuse,
    Stahlschaft und ein konisch zulaufender Bohrkopf mit Spiralstreifen; der Kopf ist je Stufe länger
    (5, 6, 7 px) und aus Eisen, Kristallquarz bzw. türkisem Tiefengestein."""
    head = {1: 9, 2: 10, 3: 11}[tier]
    tip = 11
    start = tip - head

    def spec(t, du):
        if t <= -11:                                     # Kurbel: L-Form, nur zu einer Seite
            if -4 <= du <= 1:
                return 'k' if du <= -3 or t == -11 else 'h'
            return None
        if t <= -8:                                      # Holzgriff
            return 'h' if 0 <= du <= 1 else None
        if t <= -5:                                      # Gehäuse, breiter als der Kopf, in Kontrastfarbe
            return 'f' if -2 <= du <= 3 else None
        if t < start:                                    # Schaft
            return 'r' if 0 <= du <= 1 else None
        k = (t - start) / head                           # Kopf: Basis 4 px, gleichmäßig zur 1-px-Spitze
        half = {1: 3.0, 2: 3.0, 3: 3.5}[tier] * (1 - k) + 0.3
        if -half + 0.5 <= du <= half + 0.5:
            return 's' if t % 2 == 0 else 'b'            # Spiralbänder: alle 2 px ein dunkler Streifen quer zur Achse
        return None
    bit = {1: ('#eef3f7', '#d8e0e8', '#8a929c', '#6a727c'), 2: R_QUARTZ, 3: R_TEAL}[tier]
    stripe = {1: ('#555c66',) * 4, 2: ('#1a1450',) * 4, 3: ('#124440',) * 4}[tier]
    mats = {'h': R_WOOD, 'k': R_WOOD if tier == 1 else R_QUARTZ if tier == 2 else R_AMBER,
            'f': ('#e8a85a', '#d08a3a', '#a0662a', '#6e4418') if tier == 1 else R_WOOD if tier == 2 else R_AMBER,
            'r': R_IRON_DARK, 'b': bit, 's': stripe}
    edge = {1: '#1e1a22', 2: '#1a1450', 3: '#0c2826'}[tier]
    return outline(shade(diag_mask(spec, (-13, tip)), mats), edge)


def ramp_from(base):
    """Glanz/hell/mittel/dunkel aus einer Grundfarbe."""
    r, g, b, _ = rgb(base)
    def mix(f, to):
        return '#%02x%02x%02x' % tuple(round(c + (t - c) * f) for c, t in zip((r, g, b), to))
    return (mix(0.45, (255, 255, 255)), mix(0.22, (255, 255, 255)), base, mix(0.35, (0, 0, 0)))


EGG_ROWS = [  # einheitliche Ei-Form für alle Spawn-Eier (breiteste Stelle im unteren Drittel)
    '................',
    '.......xx.......',
    '......xxxx......',
    '.....xxxxxx.....',
    '.....xxxxxx.....',
    '....xxxxxxxx....',
    '....xxxxxxxx....',
    '...xxxxxxxxxx...',
    '...xxxxxxxxxx...',
    '...xxxxxxxxxx...',
    '...xxxxxxxxxx...',
    '...xxxxxxxxxx...',
    '....xxxxxxxx....',
    '.....xxxxxx.....',
    '......xxxx......',
    '................',
]
EGG_SPOTS = [(7, 2), (8, 4), (9, 4), (6, 6), (5, 7), (6, 7), (10, 7), (11, 8), (10, 8), (7, 9), (8, 10), (5, 11),
             (4, 11), (9, 12), (10, 12), (6, 13)]


def egg(base, spot):
    mask = grid_mask(EGG_ROWS)
    for p in EGG_SPOTS:
        mask[p] = 's'
    img = shade({p: 'x' for p in mask}, {'x': ramp_from(base)})   # Schattierung nach der Eiform
    sp = ramp_from(spot)
    for (x, y) in EGG_SPOTS:
        img.putpixel((x, y), rgb(sp[1] if (x + y) % 2 == 0 else sp[2]))
    return outline(img, ramp_from(ramp_from(base)[3])[3])


V2_EGGS = {
    'geode_crawler_spawn_egg': ('#5a4f66', '#b8b6ff'),
    'spore_bellows_spawn_egg': ('#4f7a3a', '#d8cfa0'),
    'petrified_witness_spawn_egg': ('#8a8478', '#e8c25a'),
    'ember_burrower_spawn_egg': ('#4a2018', '#ff8a2a'),
    'stratolith_spawn_egg': ('#3c3742', '#c8a26a'),
    'resonant_shard_spawn_egg': ('#4a44c0', '#f2f2ff'),
    'amber_moth_spawn_egg': ('#d9821c', '#3a1b04'),
    'resin_snail_spawn_egg': ('#8a3a14', '#ffd27a'),
}


R_CALCITE = ('#fffdf6', '#f0ebdf', '#d6cfbf', '#a59d8c')
R_GEODE_IN = ('#8a7a99', '#6a5a7a', '#4f4360', '#352b44')
R_MOSS = ('#e2f7c4', '#9fd06a', '#5c8f3c', '#355a22')
R_EMBER = ('#fff1c4', '#ff9a3c', '#e0531c', '#9e2a0c')
R_STONE = ('#b3acb8', '#8f8896', '#6e6878', '#4e4858')
R_GOLD = ('#fff1b0', '#ffd25a', '#e0a82e', '#9a6a14')
ARMOR_MATS = {'c': R_CALCITE, 'v': R_QUARTZ, 'g': R_GEODE_IN}


def armor(rows):
    return lambda: outline(shade(grid_mask(rows), ARMOR_MATS), '#231c2a')


V2_HELMET = [
    '................',
    '................',
    '................',
    '....cccccccc....',
    '...ccvccccvcc...',
    '..ccvvvccvvvcc..',
    '..cccccccccccc..',
    '..ccc......ccc..',
    '..cc........cc..',
    '..cc........cc..',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
]
V2_CHESTPLATE = [
    '................',
    '..ccc......ccc..',
    '.ccccc....ccccc.',
    '.cccccccccccccc.',
    '.cccccvvvvccccc.',
    '..ccccvggvcccc..',
    '...cccvggvccc...',
    '...ccccvvcccc...',
    '...cccccccccc...',
    '...cccccccccc...',
    '...cccccccccc...',
    '...cccccccccc...',
    '....cccccccc....',
    '................',
    '................',
    '................',
]
V2_LEGGINGS = [
    '................',
    '...cccccccccc...',
    '...cvcvcvcvcc...',
    '...cccccccccc...',
    '...cccc..cccc...',
    '...ccc....ccc...',
    '...ccc....ccc...',
    '...ccc....ccc...',
    '...ccc....ccc...',
    '...ccc....ccc...',
    '...ccc....ccc...',
    '...cvc....cvc...',
    '...ccc....ccc...',
    '................',
    '................',
    '................',
]
V2_BOOTS = [
    '................',
    '................',
    '................',
    '................',
    '................',
    '...ccc....ccc...',
    '...cvc....cvc...',
    '...ccc....ccc...',
    '...ccc....ccc...',
    '..cccc....cccc..',
    '.ccccc....ccccc.',
    '.ccccc....ccccc.',
    '................',
    '................',
    '................',
    '................',
]


def v2_seeds():
    # Kompaktes Häufchen aus fünf Distelsamen (Pyramide 2-2-1), Körner 3 x 4, zwei mit violettem Spitzchen.
    mask = {}
    seeds = [(4, 10), (8, 10), (6, 7), (10, 7), (7, 4)]
    for sx, sy in seeds:
        for dx in range(3):
            for dy in range(4):
                if (dx, dy) in ((0, 0), (2, 0), (0, 3), (2, 3)):
                    continue                              # Ecken weg: oval
                mask[(sx + dx, sy + dy)] = 's'
    img = shade(mask, {'s': ('#c8e8cc', '#9cc4a4', '#5f8a6a', '#3a5a44')})
    for sx, sy in seeds:                                  # Glanzpixel je Korn
        img.putpixel((sx + 1, sy + 1), rgb('#c8e8cc'))
    for sx, sy in (seeds[4], seeds[3]):
        img.putpixel((sx + 1, sy - 1), rgb('#b8b6ff'))
    return outline(img, '#1e3424')


def v2_spores():
    # Eine Prise Harzfarn-Sporen: drei grüne Kugeln mit Bernsteinglanz.
    mask = grid_mask([
        '................',
        '................',
        '................',
        '................',
        '........ggg.....',
        '.......ggggg....',
        '.......ggggg....',
        '...ggg.ggggg....',
        '..ggggg.ggg.....',
        '..ggggg.........',
        '..ggggg..ggg....',
        '...ggg..ggggg...',
        '........ggggg...',
        '........ggggg...',
        '.........ggg....',
        '................',
    ])
    img = shade(mask, {'g': R_MOSS})
    for x, y in ((8, 5), (3, 8), (9, 11)):
        img.putpixel((x, y), rgb('#ffd27a'))
    return outline(img, '#1f3a14')


def v2_layer_core():
    # Aufrechter Bohrkern 6 breit mit den drei Epochen-Schichten (Kristall, Sporen, Asche) zwischen Zeitgestein.
    bands = ['k', 'k', 'q', 'q', 'q', 'k', 'm', 'm', 'm', 'k', 'e', 'e', 'e', 'k']
    mask = {}
    for i, b in enumerate(bands):
        for x in range(5, 11):
            mask[(x, 1 + i)] = b
    img = shade(mask, {'k': R_SLATE, 'q': R_QUARTZ, 'm': R_MOSS, 'e': R_EMBER})
    for y in range(1, 15):                      # Rundung: linke Kante hell, rechte dunkel
        img.putpixel((5, y), rgb(ramp_from('#%02x%02x%02x' % img.getpixel((7, y))[:3])[1]))
        img.putpixel((10, y), rgb(ramp_from('#%02x%02x%02x' % img.getpixel((7, y))[:3])[3]))
    for x in range(6, 10):                      # Schnittfläche oben
        img.putpixel((x, 1), rgb('#d9cce6'))
    return outline(img, '#1a1422')


def v2_splinter():
    # Bruchstück eines Resonanzlings: unregelmäßig, in der Mitte am breitesten, eine Seite gezackt, unten stumpf.
    mask = grid_mask([
        '................',
        '............q...',
        '...........qq...',
        '..........qqqq..',
        '.........qqqq...',
        '........qqqqqq..',
        '.......qqqqq....',
        '......qqqqqqq...',
        '.....qqqqqq.....',
        '.....qqqqq......',
        '....qqqqq.......',
        '....qqqq........',
        '...qqqq.........',
        '...qqq..........',
        '................',
        '................',
    ])
    img = shade(mask, {'q': R_QUARTZ})
    for x, y in ((11, 3), (10, 4), (10, 5), (9, 6), (8, 7), (8, 8), (7, 9), (6, 10), (6, 11)):   # Zickzack-Riss
        img.putpixel((x, y), rgb('#f2f2ff'))
    img = outline(img, '#1a1450')
    for x, y in ((14, 6), (2, 9)):                           # Resonanz-Funken
        img.putpixel((x, y), rgb('#b8b6ff'))
    return img


def v2_calcite_scale():
    # Fächerförmige Schuppe aus Calcit mit Rillen und violettem Rand.
    mask = grid_mask([
        '................',
        '................',
        '................',
        '....cccccccc....',
        '...cccccccccc...',
        '..cccccccccccc..',
        '..cccccccccccc..',
        '..cccccccccccc..',
        '...cccccccccc...',
        '...cccccccccc...',
        '....cccccccc....',
        '.....cccccc.....',
        '......cccc......',
        '.......cc.......',
        '................',
        '................',
    ])
    img = shade(mask, {'c': R_CALCITE})
    for x0 in (5, 8, 11):                       # Rillen strahlen zum Ansatz unten
        for y in range(4, 12):
            x = round(x0 + (7.5 - x0) * (y - 4) / 9)
            if (x, y) in mask:
                img.putpixel((x, y), rgb('#c9c1b0'))
    for x in range(4, 12):
        img.putpixel((x, 3), rgb('#b8b6ff'))
    return outline(img, '#3a3046')


def v2_codex_fragment():
    # Steintafel, oben rechts schräg abgebrochen, unten links abgeplatzt, Zickzack-Riss, drei Zeilen kleiner Zeichen.
    mask = grid_mask([
        '................',
        '..ssssssssss....',
        '..sssssssssss...',
        '..ssssssssssss..',
        '..sssssssssssss.',
        '..sssssssssssss.',
        '..sssssssssssss.',
        '..sssssssssssss.',
        '..sssssssssssss.',
        '..sssssssssssss.',
        '..sssssssssssss.',
        '..sssssssssssss.',
        '...ssssssssssss.',
        '....sssssssssss.',
        '................',
        '................',
    ])
    img = shade(mask, {'s': ('#b6b0bc', '#9a94a0', '#7a7482', '#57515f')})
    lines = {4: [(4, 1), (6, 2), (9, 1), (11, 1)], 7: [(4, 2), (7, 1), (9, 2)], 10: [(4, 1), (6, 1), (8, 2), (11, 1)]}
    for y, marks in lines.items():                        # Zeichen: kurze Striche und Punkte mit Lücken
        for x, w in marks:
            for dx in range(w):
                if (x + dx, y) in mask:
                    img.putpixel((x + dx, y), rgb('#e8c25a'))
                if (x + dx, y + 1) in mask:
                    img.putpixel((x + dx, y + 1), rgb('#7a5a14'))
        img.putpixel((marks[0][0], y - 1), rgb('#e8c25a'))   # ein Häkchen je Zeile
    for x, y in ((11, 5), (10, 6), (11, 7), (10, 8), (11, 9), (12, 10)):   # Zickzack-Riss
        img.putpixel((x, y), rgb('#3a3540'))
    return outline(img, '#2a2430')


R_ROCK = ('#a8a2ae', '#8c8694', '#706a78', '#4f4a56')
R_BONE = ('#fffaf0', '#f0e6cc', '#d9c9a2', '#a8946a')
R_ASH_STONE = ('#a59a8c', '#857a6c', '#675e53', '#463f37')


def v2_fossil_chunk():
    # Gesteinsbrocken mit großem hellem Knochenabdruck (Wirbel und Rippe).
    mask = grid_mask([
        '................',
        '................',
        '.....rrrrrr.....',
        '...rrrrrrrrrr...',
        '..rrrbbrrrrrrr..',
        '..rrrbbbbrrrrr..',
        '.rrrrrbbbbrrrrr.',
        '.rrrrrrrbbbbrrr.',
        '.rrrbrrrrrbbrrr.',
        '.rrrbbrrrrrrrrr.',
        '..rrrbbbrrrrrr..',
        '..rrrrrbbrrrr...',
        '...rrrrrrrrrr...',
        '.....rrrrrr.....',
        '................',
        '................',
    ])
    return outline(shade(mask, {'r': R_ROCK, 'b': R_BONE}), '#2c2832')


def v2_petrified_item():
    # Versteinerte Stammscheibe frontal, von Hand gezeichnet: warme Rinde, zwei Jahresringe, dunkler Kern, Riss.
    rows = [
        '................',
        '.....bbbbbb.....',
        '...bbbccccbbb...',
        '..bbccccccccbb..',
        '..bccrrrrrrccb..',
        '.bbcrccccccrcbb.',
        '.bccrcrrrrcrccb.',
        '.bccrcrkkrcrkkb.',
        '.bccrcrkkrcrccb.',
        '.bccrcrrrrcrccb.',
        '.bbcrccccccrcbb.',
        '..bccrrrrrrccb..',
        '..bbccccccccbb..',
        '...bbbccccbbb...',
        '.....bbbbbb.....',
        '................',
    ]
    mask = grid_mask(rows)
    img = shade({p: m for p, m in mask.items() if m in 'bc'},
                {'b': ('#8a6e54', '#6b5440', '#54412f', '#3e2e20'), 'c': ('#d4cab2', '#c6bca4', '#b8ae96', '#968c76')})
    for p, m in mask.items():
        if m == 'r':
            img.putpixel(p, rgb('#7a7060'))
        elif m == 'k':
            img.putpixel(p, rgb('#4a4236'))
    return outline(img, '#2a1e14')


def v2_ammonite():
    # Ammonit: helle Schale, darauf eine dunkle Spirallinie (Archimedes, drei Windungen) und Kammerkerben am Rand.
    import math
    mask = {}
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 6.7 ** 2:
                mask[(x, y)] = 'c'
    img = shade(mask, {'c': R_BONE})
    th = 0.0
    while th < 2.3 * 2 * math.pi:
        r = 0.6 + 0.42 * th
        x, y = round(7.5 + r * math.cos(th)), round(7.5 + r * math.sin(th))
        if (x, y) in mask:
            img.putpixel((x, y), rgb('#7a6640'))
        th += 0.05
    for k in range(12):                                   # Kammerkerben am Außenrand
        a2 = k * math.pi / 6
        x, y = round(7.5 + 5.6 * math.cos(a2)), round(7.5 + 5.6 * math.sin(a2))
        if (x, y) in mask:
            img.putpixel((x, y), rgb('#a8946a'))
    return outline(img, '#3a2f1c')


def v2_fern_fossil():
    # Unregelmäßiger Steinbrocken mit großem Farnwedel-Abdruck.
    mask = grid_mask([
        '................',
        '................',
        '....rrrrrrrr....',
        '...rrrrrrrrrrr..',
        '..rrrrrrrrrrrr..',
        '..rrrrrrrrrrrrr.',
        '.rrrrrrrrrrrrrr.',
        '.rrrrrrrrrrrrrr.',
        '.rrrrrrrrrrrrrr.',
        '.rrrrrrrrrrrrr..',
        '..rrrrrrrrrrrr..',
        '..rrrrrrrrrrr...',
        '...rrrrrrrrr....',
        '....rrrrrr......',
        '................',
        '................',
    ])
    img = shade(mask, {'r': R_ROCK})
    for y in range(3, 13):                                    # senkrechter Farnwedel als dunkler Abdruck
        img.putpixel((7, y), rgb('#2e3a2a'))
    for y, n in ((5, 1), (7, 2), (9, 3), (11, 3)):            # Fiedern, nach unten länger, schräg nach oben
        for k in range(1, n + 1):
            for x in (7 - k, 7 + k):
                if (x, y - k + 1) in mask:
                    img.putpixel((x, y - k + 1), rgb('#3e5a36' if k < n else '#5c7a4e'))
    return outline(img, '#2c2832')


def v2_amber_blade():
    # Klinge aus Bernstein mit Spitze, heller Schneide und dunklem Rücken; dunkler Holzgriff, Einschluss.
    mask = grid_mask([
        '................',
        '.............ee.',
        '............eaa.',
        '...........eaad.',
        '..........eaad..',
        '.........eaad...',
        '........eaid....',
        '.......eaad.....',
        '...g..eaad......',
        '....gaaad.......',
        '.....gad........',
        '....hhg.........',
        '...hh..g........',
        '..hh............',
        '.hh.............',
        '................',
    ])
    mats = {'e': ('#fff3c8', '#fff3c8', '#ffe2a0', '#ffd27a'), 'a': R_AMBER, 'd': ('#d9821c', '#a2560f', '#7a3e0a', '#5a2a06'),
            'i': ('#3a1b04', '#3a1b04', '#3a1b04', '#3a1b04'), 'g': R_GOLD, 'h': ('#7a5a3e', '#5a3f2a', '#3e2a1c', '#2a1a10')}
    return outline(shade(mask, mats), '#3a1b04')


def v2_amber_inclusion():
    # Bernsteinbrocken in Tropfenform, darin eine deutlich erkennbare Mücke.
    mask = grid_mask([
        '................',
        '.......aa.......',
        '......aaaa......',
        '.....aaaaaa.....',
        '....aaaaaaaa....',
        '...aaaaaaaaaa...',
        '...aaaaaaaaaa...',
        '..aaaaaaaaaaaa..',
        '..aaaaaaaaaaaa..',
        '..aaaaaaaaaaaa..',
        '..aaaaaaaaaaaa..',
        '...aaaaaaaaaa...',
        '....aaaaaaaa....',
        '......aaaa......',
        '................',
        '................',
    ])
    img = shade(mask, {'a': R_AMBER})
    body = [(7, 9), (8, 8), (9, 7)]                       # kompakter Körper, 3 px diagonal
    wings = [(5, 6), (6, 6), (5, 7), (6, 7), (9, 4), (10, 4), (9, 5), (10, 5)]   # zwei getrennte Flügel als V
    legs = [(6, 10), (8, 10)]
    for x, y in set(body + wings + legs):                 # 1 px dunklerer Hof
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + ox, y + oy)
            if p in mask and p not in body + wings + legs:
                img.putpixel(p, rgb('#a2560f'))
    for x, y in body:
        img.putpixel((x, y), rgb('#3a1b04'))
    img.putpixel((10, 6), rgb('#1e0e02'))                 # Kopf
    for i, (x, y) in enumerate(wings):
        img.putpixel((x, y), rgb('#fff1c4' if i in (0, 4) else '#ffd27a'))
    for x, y in legs:
        img.putpixel((x, y), rgb('#5c2e0a'))
    return outline(img, '#3a1b04')


def v2_heart():
    # Herz aus Kristallquarz, darin ein kleiner goldener Sanduhr-Kern.
    mask = grid_mask([
        '................',
        '................',
        '...qqq....qqq...',
        '..qqqqq..qqqqq..',
        '.qqqqqqqqqqqqqq.',
        '.qqqqqqqqqqqqqq.',
        '.qqqqqqqqqqqqqq.',
        '..qqqqqqqqqqqq..',
        '...qqqqqqqqqq...',
        '....qqqqqqqq....',
        '.....qqqqqq.....',
        '......qqqq......',
        '.......qq.......',
        '................',
        '................',
        '................',
    ])
    img = shade(mask, {'q': R_QUARTZ})
    hourglass = [(6, 4), (7, 4), (8, 4), (9, 4), (7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (6, 8), (7, 8), (8, 8), (9, 8)]
    for x, y in hourglass:
        img.putpixel((x, y), rgb('#ffd25a' if y in (4, 8) else '#fff1b0' if (x, y) == (7, 5) else '#e0a82e'))
    return outline(img, '#1a1450')


def resin_cells(seed, pal, glossy=True):
    """Harz als nahtlos kachelnde Blasen: Zellen in 2-3-px-Clustern, zur Zellmitte heller (statt Pixelrauschen)."""
    rnd = random.Random(seed)
    pts = [(rnd.uniform(0, 16), rnd.uniform(0, 16)) for _ in range(11)]
    img = canvas()
    for y in range(16):
        for x in range(16):
            ds = []
            for px, py in pts:
                dx = min(abs(x - px), 16 - abs(x - px))
                dy = min(abs(y - py), 16 - abs(y - py))
                ds.append((dx * dx + dy * dy) ** 0.5)
            ds.sort()
            edge = ds[1] - ds[0]                      # klein an der Zellgrenze
            v = min(0.85, 0.45 + 0.14 * edge)
            if edge < 0.55:
                v = 0.3                               # dunklere Fuge zwischen den Blasen
            img.putpixel((x, y), pick(pal, v))
    if glossy:
        for x, y in ((3, 2), (4, 2), (2, 3), (11, 9), (12, 9), (7, 12)):   # Glanzpunkte
            img.putpixel((x, y), rgb(pal[-1]))
    return img


def v2_resin_trap():
    img = resin_cells(401, RESIN)
    for y in range(5, 11):                            # klebriger, dunkler Fangkern in der Mitte
        for x in range(5, 11):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 8:
                img.putpixel((x, y), rgb('#7a3c08' if (x + y) % 3 else '#5a2a06'))
    return img


def v2_resin_trap_moth():
    img = resin_cells(401, RESIN)
    _moth_inside(img, '#2a1c12', '#6a3606', '#3b2818')
    return img


def v2_resin_trap_hardened():
    img = resin_cells(403, HARD_AMBER)
    _moth_inside(img, '#5a2a06', '#a2560f', '#4f3620')
    for i in range(5):                                # polierter Glanz oben links
        img.putpixel((1 + i, 1 + i // 2), rgb('#fff3c8'))
    return img


ITEMS = {
    'core_drill': lambda: drill(1),
    'crystal_core_drill': lambda: drill(2),
    'deep_core_drill': lambda: drill(3),
    'ember_shard': ember_shard,
    'fossil_chunk': v2_fossil_chunk,
    'chisel': v2_chisel,
    'ammonite_fossil': v2_ammonite,
    'fern_fossil': v2_fern_fossil,
    'ancient_skull': ancient_skull,
    'core_sample': core_sample,
    'crystal_quartz': crystal_quartz,
    'amber_shard': amber_shard,
    'time_anchor': time_anchor,
    'calcite_scale': v2_calcite_scale,
    'spore_sac': spore_sac,
    'germ_tonic': germ_tonic,
    'codex_fragment': v2_codex_fragment,
    'quartz_thistle_seeds': v2_seeds,
    'petrified_item': v2_petrified_item,
    'time_essence': time_essence,
    'layer_core': v2_layer_core,
    'heart_of_deep_time': v2_heart,
    'resin_fern_spores': v2_spores,
    'ember_tuber': ember_tuber,
    'resonant_splinter': v2_splinter,
    'amber_inclusion': v2_amber_inclusion,
}
SPAWN_EGGS = {name: (lambda b=b, s=s: egg(b, s)) for name, (b, s) in V2_EGGS.items()}


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
