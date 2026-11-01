"""Fasst die Screenshots eines Mobs zu einem Prüfblatt zusammen: mobs/<id>/review_sheet.png"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', 'mobs')


def sheet(mob):
    shots = os.path.join(ROOT, mob, 'shots')
    views = ['front', 'side', 'back', 'iso']
    anims = sorted(f for f in os.listdir(shots) if f.startswith('anim_'))
    cell = 256
    cols = 4
    rows = 1 + (len(anims) + cols - 1) // cols + 1
    out = Image.new('RGBA', (cols * cell, rows * (cell + 18)), (235, 235, 238, 255))
    d = ImageDraw.Draw(out)

    def put(path, i, label, size=cell):
        x, y = (i % cols) * cell, (i // cols) * (cell + 18)
        im = Image.open(path).convert('RGBA').resize((size, size), Image.LANCZOS)
        out.alpha_composite(im, (x, y))
        d.text((x + 4, y + size + 2), label, fill=(30, 30, 30, 255))

    for i, v in enumerate(views):
        put(os.path.join(shots, v + '.png'), i, v)
    for j, a in enumerate(anims):
        put(os.path.join(shots, a), cols + j, a[5:-4])
    tex = Image.open(os.path.join(ROOT, mob, 'texture.png')).convert('RGBA')
    scale = max(1, min(cell * 2 // tex.width, cell // tex.height))
    y = (rows - 1) * (cell + 18)
    out.alpha_composite(tex.resize((tex.width * scale, tex.height * scale), Image.NEAREST), (0, y))
    d.text((4, y + tex.height * scale + 2), 'texture', fill=(30, 30, 30, 255))
    path = os.path.join(ROOT, mob, 'review_sheet.png')
    out.save(path)
    return path


if __name__ == '__main__':
    for m in sys.argv[1:] or sorted(os.listdir(ROOT)):
        if os.path.isdir(os.path.join(ROOT, m, 'shots')):
            print(os.path.normpath(sheet(m)))
