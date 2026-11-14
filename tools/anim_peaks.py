"""Findet pro Animation den Zeitpunkt mit dem größten Ausschlag (für die Bilder im Prüfblatt).

Aufruf: python3 tools/anim_peaks.py [mob-id ...]   oder   --json <mob-id>

Ausschlag = Summe über alle Knochen: Rotation in Grad + Position × 5 + |Skalierung − 1| × 100.
Abgetastet wird alle 0,05 s, linear interpoliert. Für die Wahl des Zeitpunkts reicht das.
Eine Animation kann den Zeitpunkt mit "peak" selbst festlegen (sonst zählt eine reine Drehung eines
symmetrischen Teils, die man nicht sieht).
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), '..', 'mobs')
WEIGHT = {'rotation': 1.0, 'position': 5.0, 'scale': 100.0}


def value_at(keys, t):
    keys = sorted(keys, key=lambda k: k['time'])
    if t <= keys[0]['time']:
        return keys[0]['value']
    for a, b in zip(keys, keys[1:]):
        if a['time'] <= t <= b['time']:
            f = (t - a['time']) / ((b['time'] - a['time']) or 1)
            return [x + (y - x) * f for x, y in zip(a['value'], b['value'])]
    return keys[-1]['value']


def deflection(anim, t):
    channels = {}
    for k in anim['keyframes']:
        channels.setdefault((k['bone'], k.get('channel', 'rotation')), []).append(k)
    total = 0.0
    for (_, channel), keys in channels.items():
        v = value_at(keys, t)
        base = 1.0 if channel == 'scale' else 0.0
        total += sum(abs(x - base) for x in v) * WEIGHT[channel]
    return total


def peak_time(anim):
    if 'peak' in anim:                                # fest vorgegeben (z. B. wenn ein symmetrischer Ring sich dreht)
        return anim['peak']
    steps = int(round(anim['length'] / 0.05))
    times = [round(i * 0.05, 2) for i in range(steps + 1)]
    return max(times, key=lambda t: deflection(anim, t))


def peaks(mob_id):
    model = json.load(open(os.path.join(ROOT, mob_id, 'model.json'), encoding='utf-8'))
    return {a['name'].split('.')[-1]: peak_time(a) for a in model.get('animations', [])}


if __name__ == '__main__':
    if sys.argv[1:2] == ['--json']:                   # für build_mobs.js: nur die Zeiten eines Mobs als JSON
        print(json.dumps(peaks(sys.argv[2])))
        sys.exit()
    ids = sys.argv[1:] or sorted(d for d in os.listdir(ROOT) if os.path.isfile(os.path.join(ROOT, d, 'model.json')))
    for mob in ids:
        print(mob, json.dumps(peaks(mob)))
