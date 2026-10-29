"""Erzeugt einfache 16x16-Platzhaltertexturen für Strata Epochs.

Aufruf: python3 tools/make_textures.py
Die Bilder sind bewusst schlicht. Sie lassen sich später durch echte Pixel-Art ersetzen.
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'epochs', 'src', 'shared', 'resources',
                   'assets', 'strata_epochs', 'textures')


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def noise_block(base, seed, spread=0.18):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            img.putpixel((x, y), shade(base, 1 + rnd.uniform(-spread, spread)))
    return img


def sprinkle(img, color, seed, count, size=2):
    rnd = random.Random(seed)
    for _ in range(count):
        x, y = rnd.randrange(1, 15 - size), rnd.randrange(1, 15 - size)
        for dx in range(size):
            for dy in range(size):
                img.putpixel((x + dx, y + dy), shade(color, 1.25 if dx == 0 and dy == 0 else 1.0))
    return img


def save(img, kind, name):
    path = os.path.join(OUT, kind, name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def blocks():
    chrono = hexrgb('#4a3f63')
    save(noise_block(chrono, 1), 'block', 'chronostone')
    save(sprinkle(noise_block(chrono, 2), hexrgb('#c9a7ff'), 3, 6), 'block', 'crystal_quartz_ore')
    save(sprinkle(noise_block(chrono, 4), hexrgb('#ffae3b'), 5, 5), 'block', 'amber_ore')
    save(noise_block(hexrgb('#7d7468'), 6, 0.12), 'block', 'petrified_stone')
    gate = noise_block(hexrgb('#1c2a3a'), 7, 0.1)
    cyan = hexrgb('#4fe3e8')
    for i in range(16):
        for edge in (0, 15):
            gate.putpixel((i, edge), cyan)
            gate.putpixel((edge, i), cyan)
    for i in range(4, 12):
        gate.putpixel((i, 7), shade(cyan, 0.8))
        gate.putpixel((7, i), shade(cyan, 0.8))
    save(gate, 'block', 'epoch_gate')


def item(pixels, palette):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(pixels):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), hexrgb(palette[ch]))
    return img


def items():
    save(item([
        '................',
        '............cc..',
        '...........cCCc.',
        '..........cCCc..',
        '.........iIc....',
        '........iIi.....',
        '.......iIi......',
        '......iIi.......',
        '.....iIi........',
        '....wWi.........',
        '...wWw..........',
        '..sSw...........',
        '.sSs............',
        '.ss.............',
        '................',
        '................'], {'c': '#b5651d', 'C': '#e08a3c', 'i': '#6b6b6b', 'I': '#bdbdbd',
                              'w': '#5a4632', 'W': '#7a5f43', 's': '#3c3c3c', 'S': '#8a8a8a'}), 'item', 'core_drill')
    save(item([
        '................',
        '......aaaa......',
        '.....aAAAAa.....',
        '.....aAAAAa.....',
        '.....bBBBBb.....',
        '.....bBBBBb.....',
        '.....cCCCCc.....',
        '.....cCCCCc.....',
        '.....dDDDDd.....',
        '.....dDDDDd.....',
        '.....aAAAAa.....',
        '.....bBBBBb.....',
        '.....bBBBBb.....',
        '......bbbb......',
        '................',
        '................'], {'a': '#5b4a7a', 'A': '#8c74b8', 'b': '#3e3a4a', 'B': '#66607a',
                              'c': '#7a5c2e', 'C': '#c2924a', 'd': '#3f6b3a', 'D': '#6fa864'}), 'item', 'core_sample')
    save(item([
        '................',
        '.......v........',
        '......vVv.......',
        '.....vVLVv......',
        '.....vVLVv......',
        '....vVVLVVv.....',
        '....vVLLLVv.....',
        '....vVVLVVv.....',
        '.....vVLVv......',
        '.....vVVVv......',
        '......vVv.......',
        '.......v........',
        '................',
        '................',
        '................',
        '................'], {'v': '#5e3fa3', 'V': '#9b74e6', 'L': '#e2d4ff'}), 'item', 'crystal_quartz')
    save(item([
        '................',
        '................',
        '.......oo.......',
        '......oOOo......',
        '.....oOYOOo.....',
        '....oOYYOOo.....',
        '....oOOYOOOo....',
        '...oOOOOOOOo....',
        '...oOOOOOOo.....',
        '....oOOOOo......',
        '.....oooo.......',
        '................',
        '................',
        '................',
        '................',
        '................'], {'o': '#9a5a12', 'O': '#f0a030', 'Y': '#ffe08a'}), 'item', 'amber_shard')
    save(item([
        '................',
        '......gggg......',
        '.....gG..Gg.....',
        '.....g....g.....',
        '......gGGg......',
        '.......gg.......',
        '.......vv.......',
        '......vVVv......',
        '....g.vVVv.g....',
        '...gG.vVVv.Gg...',
        '...gG..vv..Gg...',
        '....gG.gg.Gg....',
        '.....gGGGGg.....',
        '......gggg......',
        '................',
        '................'], {'g': '#a07a10', 'G': '#f2c94c', 'v': '#5e3fa3', 'V': '#b08cff'}), 'item', 'time_anchor')


if __name__ == '__main__':
    blocks()
    items()
    print('Texturen geschrieben nach', os.path.normpath(OUT))
