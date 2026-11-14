#!/usr/bin/env python3
"""Erzeugt die Mob-Sounds von Strata Epochs per Synthese (keine Aufnahmen, keine fremden Samples).

Jeder Sound ist mono (sonst hört man im Spiel nicht, woher er kommt), 44,1 kHz, als Ogg Vorbis.
Feste Zufalls-Seeds: Derselbe Aufruf erzeugt dieselben Klänge.

    python tools/make_mob_sounds.py            # alle Sounds neu erzeugen
    python tools/make_mob_sounds.py --sheet x.png   # zusätzlich Spektrogramme zum Prüfen

Braucht numpy, scipy und ffmpeg (mit libvorbis). Ziel:
epochs/src/shared/resources/assets/strata_epochs/sounds/entity/<mob>/<ereignis><n>.ogg
"""
import argparse
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy import signal

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "epochs/src/shared/resources/assets/strata_epochs/sounds/entity")


# ---------------------------------------------------------------- Bausteine

def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def silence(dur):
    return np.zeros(int(dur * SR))


def env(dur, attack=0.005, decay=0.2, hold=0.0, curve=1.0):
    """Hüllkurve: schneller Anstieg, Halten, exponentielles Abklingen."""
    t = t_axis(dur)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    d = np.where(t < attack + hold, 1.0, np.exp(-((t - attack - hold) / max(decay, 1e-4)) ** curve))
    return a * d


def fade(x, ms=6):
    n = min(len(x) // 2, int(SR * ms / 1000))
    if n > 0 and ms > 0:
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    return x


def sweep(f0, f1, dur, shape="exp"):
    """Phase eines Tons, der von f0 nach f1 gleitet."""
    t = t_axis(dur)
    if shape == "exp":
        f = f0 * (f1 / f0) ** (t / dur)
    else:
        f = f0 + (f1 - f0) * t / dur
    return 2 * np.pi * np.cumsum(f) / SR, f


def band(x, lo, hi, order=2):
    lo = max(lo, 20)
    hi = min(hi, SR / 2 - 100)
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def low(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype="low", fs=SR, output="sos"), x)


def high(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype="high", fs=SR, output="sos"), x)


def swept_band(x, centers, q=4.0, block=256):
    """Bandpass, dessen Mitte über die Zeit wandert (blockweise, für Atem- und Zischlaute)."""
    out = np.zeros_like(x)
    zi = None
    for i in range(0, len(x), block):
        c = float(np.clip(centers[min(i, len(centers) - 1)], 60, SR / 2 - 2000))
        sos = signal.butter(2, [c / (1 + 1 / q), c * (1 + 1 / q)], btype="band", fs=SR, output="sos")
        if zi is None:
            zi = signal.sosfilt_zi(sos) * 0
        out[i:i + block], zi = signal.sosfilt(sos, x[i:i + block], zi=zi)
    return out


def place(dst, src, at):
    i = int(at * SR)
    if i >= len(dst):
        return dst
    n = min(len(src), len(dst) - i)
    # Jeder Teilklang blendet kurz aus, sonst knackt sein Ende.
    dst[i:i + n] += fade(np.array(src[:n], dtype=float), ms=4)
    return dst


def chime(rng, f0, dur=0.35, ratios=(1.0, 2.76, 5.40, 8.93), bright=1.0):
    """Kristallton: unharmonische Teiltöne, die oberen klingen schneller ab."""
    t = t_axis(dur)
    x = np.zeros_like(t)
    for k, r in enumerate(ratios):
        amp = (0.6 ** k) * bright ** k
        x += amp * np.sin(2 * np.pi * f0 * r * (1 + rng.uniform(-0.004, 0.004)) * t) * np.exp(-t / (dur / (1 + 1.6 * k)))
    return x * env(dur, 0.001, dur, hold=0.0)


def grains(rng, dur, rate, lo, hi, grain=0.012, decay_rate=None, amp=1.0):
    """Körniges Geräusch (Kies, Splitter, Funken): viele kurze gefilterte Klicks."""
    x = silence(dur)
    n = max(1, int(rate * dur))
    for _ in range(n):
        at = rng.uniform(0, dur)
        if decay_rate is not None and rng.uniform() > np.exp(-at * decay_rate):
            continue
        g = rng.normal(0, 1, int(grain * SR)) * env(grain, 0.0005, grain / 3)
        c = rng.uniform(lo, hi)
        g = band(g, c * 0.7, c * 1.4)
        place(x, g * rng.uniform(0.3, 1.0) * amp, at)
    return x


def growl(rng, f0, f1, dur, cutoff=700, rough=28.0):
    """Knurren: Sägezahn mit Zittern und rauer Amplitudenmodulation, tiefpassgefiltert."""
    ph, f = sweep(f0, f1, dur)
    jitter = np.cumsum(rng.normal(0, 0.02, len(ph)))
    jitter -= np.linspace(jitter[0], jitter[-1], len(jitter))
    saw = signal.sawtooth(ph + jitter * 0.6)
    am = 0.65 + 0.35 * np.sin(2 * np.pi * rough * t_axis(dur) + rng.uniform(0, 6))
    return low(saw * am, cutoff, order=4)


def thud(f0, f1, dur):
    ph, _ = sweep(f0, f1, dur)
    return np.sin(ph) * env(dur, 0.002, dur / 3)


def loudness_db(x):
    """Wahrnehmungsnahe Lautheit (vereinfachte K-Gewichtung wie bei LUFS): Bässe unter 100 Hz zählen kaum,
    Höhen über 1,5 kHz etwas mehr. Stille Anteile zählen nicht mit (nur Blöcke über -60 dB)."""
    k = high(x, 100, order=2)
    k = k + 0.6 * high(k, 1500, order=1)
    block = int(0.05 * SR)
    levels = [np.mean(k[i:i + block] ** 2) for i in range(0, max(1, len(k) - block), block // 2)]
    levels = [l for l in levels if l > 1e-6] or [1e-12]
    return 10 * np.log10(np.mean(levels))


def soft_limit(x, ceiling, knee=0.7):
    """Unterhalb von knee * ceiling unverändert (keine Verzerrung leiser, tonaler Klänge), darüber weich
    in die Obergrenze gebogen."""
    k = knee * ceiling
    a = np.abs(x)
    over = a > k
    y = x.copy()
    y[over] = np.sign(x[over]) * (k + (ceiling - k) * np.tanh((a[over] - k) / (ceiling - k)))
    return y


def normalize(x, target_db=-20.0, peak_db=-1.0):
    """Gleiche wahrgenommene Lautheit für alle Sounds. Spitzen über peak_db werden weich begrenzt
    (tanh) statt alles leiser zu machen, damit der Zielwert auch bei Knacks-Spitzen erreicht wird."""
    # Hochpass statt Mittelwert abziehen: kein Gleichanteil, die Stille bleibt still.
    x = high(x, 30)
    ceiling = 10 ** (peak_db / 20)
    for _ in range(6):
        x = x * 10 ** ((target_db - loudness_db(x)) / 20)
        x = soft_limit(x, ceiling)
    x = fade(x, ms=6)
    n = int(0.025 * SR)
    x[-n:] *= np.linspace(1, 0, n) ** 2
    return x


def variant(x, i):
    """Varianten klingen verschieden hoch (Abspielgeschwindigkeit), damit Wiederholungen nicht auffallen."""
    # Faktor als Bruch: Abspielgeschwindigkeit 1, 0,93 (= 93/100) oder 1,08 (= 27/25). resample_poly filtert
    # dabei gegen Aliasing (sonst Spiegelfrequenzen bei hohen Tönen).
    up, down = [(1, 1), (100, 93), (25, 27)][(i - 1) % 3]
    return x if up == down else signal.resample_poly(x, up, down)


# ---------------------------------------------------------------- Mobs

def geode_crawler(kind, i, rng):
    if kind == "ambient":
        # Klappernde Panzerbeine und zwei, drei tiefere Kristalltöne (tiefer als Amethyst-Schritte).
        dur = 0.9
        x = grains(rng, 0.5, 26, 1200, 3200, grain=0.006, amp=0.7)
        x = np.concatenate([x, silence(dur - 0.5)])
        at = rng.uniform(0.05, 0.2)
        for _ in range(rng.integers(2, 4)):
            place(x, chime(rng, rng.uniform(1050, 1650), rng.uniform(0.16, 0.28)) * rng.uniform(0.5, 0.9), at)
            at += rng.uniform(0.1, 0.2)
        return x
    if kind == "hurt":
        # Knacks im Panzer, ein kurzes Quieken, nur ein gedämpfter Kristallrest.
        dur = 0.45
        x = silence(dur)
        place(x, band(rng.normal(0, 1, int(0.03 * SR)), 1500, 7000) * env(0.03, 0.0005, 0.006) * 1.2, 0)
        ph, _ = sweep(rng.uniform(1300, 1500), 700, 0.16)
        squeak = (np.sin(ph) + 0.4 * np.sign(np.sin(ph)) * 0.3) * env(0.16, 0.004, 0.07)
        place(x, low(squeak, 4000) * 0.8, 0.02)
        place(x, chime(rng, rng.uniform(900, 1100), 0.12, bright=0.6) * 0.35, 0.01)
        place(x, grains(rng, 0.2, 50, 2000, 6000, amp=0.4), 0.01)
        return x
    # death: Zerspringen, die Splitter rieseln aus.
    dur = 1.4
    x = silence(dur)
    place(x, thud(220, 90, 0.25) * 0.8, 0)
    place(x, high(rng.normal(0, 1, int(0.05 * SR)), 1500) * env(0.05, 0.0005, 0.015) * 0.8, 0)
    for _ in range(28):
        at = rng.exponential(0.25)
        if at < dur - 0.3:
            place(x, chime(rng, rng.uniform(1800, 4800), rng.uniform(0.08, 0.2)) * rng.uniform(0.15, 0.6), at)
    return x


def spore_bellows(kind, i, rng):
    if kind == "ambient":
        # Ein- und Ausatmen eines Blasebalgs mit feuchtem Pfeifen.
        dur = 1.2
        t = t_axis(dur)
        breath = np.sin(np.pi * t / dur) ** 1.5
        centers = 500 + 900 * np.sin(np.pi * t / dur) ** 2
        x = swept_band(rng.normal(0, 1, len(t)), centers, q=3) * breath
        ph, _ = sweep(rng.uniform(150, 190), rng.uniform(120, 140), dur)
        wheeze = low(signal.sawtooth(ph + 0.3 * np.sin(2 * np.pi * 5 * t)), 900) * breath * 0.12
        x = x + wheeze
        place(x, band(rng.normal(0, 1, int(0.05 * SR)), 300, 1200) * env(0.05, 0.002, 0.015) * 1.2, dur * rng.uniform(0.75, 0.9))
        return x
    if kind == "hurt":
        # Quietschen und ein Sporenstoß.
        dur = 0.5
        ph, _ = sweep(rng.uniform(620, 700), rng.uniform(1100, 1250), 0.12)
        up = np.sin(ph) * env(0.12, 0.004, 0.2)
        ph2, _ = sweep(1150, 700, 0.18)
        down = np.sin(ph2) * env(0.18, 0.001, 0.08)
        x = silence(dur)
        place(x, up * 0.7, 0)
        place(x, down * 0.6, 0.11)
        place(x, swept_band(rng.normal(0, 1, int(0.3 * SR)), np.linspace(2000, 600, int(0.3 * SR)), q=2) * env(0.3, 0.005, 0.08), 0.02)
        return x
    # death: lange Luft entweicht, der Ton sinkt und flattert.
    dur = 1.7
    t = t_axis(dur)
    centers = 1600 * (200 / 1600) ** (t / dur)
    flutter = 0.6 + 0.4 * np.sin(2 * np.pi * (18 - 10 * t / dur) * t)
    x = swept_band(rng.normal(0, 1, len(t)), centers, q=3) * env(dur, 0.02, 0.6, hold=0.3) * flutter
    ph, _ = sweep(240, 70, dur)
    x += low(signal.sawtooth(ph), 800) * env(dur, 0.02, 0.5, hold=0.4) * 0.25
    return x


def petrified_witness(kind, i, rng):
    if kind == "ambient":
        # Mahlender Stein und ein entfernter, hohler Ton (ein Echo).
        dur = 1.5
        t = t_axis(dur)
        grind = band(rng.normal(0, 1, len(t)), 250, 2200) * (0.5 + 0.5 * np.sin(np.pi * t / dur)) * 0.5
        x = grind + grains(rng, dur, 90, 400, 1800, grain=0.02, amp=0.9)
        f = rng.choice([196.0, 220.0, 174.6])
        hum = sum((0.7 ** k) * np.sin(2 * np.pi * f * r * t) for k, r in enumerate([1.0, 1.5 * 1.003, 2.0, 3.0]))
        return x + hum * np.sin(np.pi * t / dur) ** 2 * 0.3
    if kind == "hurt":
        # Steinriss: dumpfer Schlag, Knacken, Kies.
        dur = 0.55
        x = silence(dur)
        place(x, thud(180, 70, 0.3) * 0.35, 0)
        place(x, band(rng.normal(0, 1, int(0.06 * SR)), 600, 4000) * env(0.06, 0.0005, 0.015) * 1.2, 0.002)
        place(x, grains(rng, 0.35, 140, 400, 2500, grain=0.015, amp=0.9, decay_rate=6), 0.02)
        return x
    # death: Zerbröckeln, das Echo verklingt nach unten.
    dur = 2.0
    t = t_axis(dur)
    x = grains(rng, dur, 260, 300, 2200, grain=0.018, decay_rate=1.8, amp=1.2)
    place(x, thud(160, 60, 0.5) * 0.4, 0)
    ph, _ = sweep(220, 98, dur)
    x += (np.sin(ph) + 0.5 * np.sin(2 * ph)) * env(dur, 0.05, 0.7, hold=0.3) * 0.35
    return x


def growl_voiced(rng, f0, f1, dur, rough=28.0):
    """Knurren mit Stimmfärbung: Sägezahn plus zwei Formanten (300–900 Hz), damit es auch aus kleinen
    Lautsprechern kommt, nicht nur als Bass."""
    g = growl(rng, f0, f1, dur, cutoff=2200, rough=rough)
    return 0.5 * g + 0.9 * band(g, 280, 520) + 0.6 * band(g, 650, 1100)


def ember_burrower(kind, i, rng):
    if kind == "ambient":
        # Knurren mit knisternden Glutfunken.
        dur = 1.0
        f = rng.uniform(88, 104)
        x = growl_voiced(rng, f, f * rng.uniform(0.85, 1.05), dur) * env(dur, 0.08, 0.35, hold=0.35)
        x += grains(rng, dur, 45, 2500, 7000, grain=0.004, amp=0.4)
        return x
    if kind == "hurt":
        # Kurzes, hohes Aufjaulen, nur ein Hauch Zischen (Tod: langes Zischen).
        dur = 0.45
        x = silence(dur)
        place(x, growl_voiced(rng, 190, 120, 0.3, rough=45) * env(0.3, 0.005, 0.1), 0)
        place(x, high(rng.normal(0, 1, int(0.12 * SR)), 3500) * env(0.12, 0.002, 0.04) * 0.15, 0)
        return x
    if kind == "death":
        # Lange verzischende Glut, ein letztes, absinkendes Knurren, Brocken fallen.
        dur = 1.9
        t = t_axis(dur)
        hiss = high(rng.normal(0, 1, len(t)), 2500) * env(dur, 0.01, 0.8, hold=0.2) * 0.3
        x = hiss + growl_voiced(rng, 120, 55, dur) * env(dur, 0.01, 0.45, hold=0.2)
        place(x, grains(rng, 0.8, 50, 300, 1500, grain=0.02, amp=0.6, decay_rate=3), 0.5)
        return x
    if kind == "dig":
        # Graben in Asche: rasche Folge körniger Schaufelstöße.
        dur = 0.6
        x = silence(dur)
        at = 0.0
        while at < dur - 0.06:
            n = int(0.05 * SR)
            s = band(rng.normal(0, 1, n), rng.uniform(700, 1100), rng.uniform(2600, 3600)) * env(0.05, 0.004, 0.015)
            place(x, s * rng.uniform(0.6, 1.0), at)
            at += rng.uniform(0.07, 0.11)
        return x
    if kind == "tremor":
        # Die Warnung vor dem Hervorbrechen: 0,8 s (= 16 Ticks; im Spiel an die Vorwarnzeit angepasst).
        # Schnell anschwellendes, verzerrtes Grollen mit rieselndem Kies, laut ab etwa 0,35 s bis kurz vor Schluss.
        dur = 0.8
        t = t_axis(dur)
        swell = np.clip(t / 0.35, 0, 1) ** 1.3 * np.clip((dur - t) / 0.05, 0, 1)
        rumble = low(rng.normal(0, 1, len(t)), 220, order=4) * 4.0
        rumble = np.tanh(rumble * 2.5) * 0.6
        ph, _ = sweep(55, 75, dur)
        sub = np.tanh(2.0 * np.sin(ph)) * (0.6 + 0.4 * np.sin(2 * np.pi * 11 * t))
        # Mitten: ratterndes Geröll (Rauschen 250–1200 Hz, 11 Hz moduliert) und rieselnder Kies, damit die
        # Warnung auch aus Laptop-Lautsprechern kommt; der Bass gibt nur das Gewicht dazu.
        rattle = band(rng.normal(0, 1, len(t)), 250, 1200) * (0.55 + 0.45 * np.sin(2 * np.pi * 11 * t)) * 1.4
        gravel = grains(rng, dur, 260, 500, 2600, grain=0.008, amp=2.5)
        return (0.35 * rumble + 0.3 * sub + rattle + gravel) * swell
    # emerge: Ausbruch aus dem Boden.
    dur = 1.0
    x = silence(dur)
    place(x, thud(140, 50, 0.6) * 0.5, 0)
    place(x, low(rng.normal(0, 1, int(0.6 * SR)), 2500) * env(0.6, 0.002, 0.15), 0)
    place(x, growl_voiced(rng, 110, 140, 0.45) * env(0.45, 0.01, 0.2) * 0.8, 0.05)
    place(x, grains(rng, 0.8, 90, 400, 3000, amp=0.5, decay_rate=4), 0.05)
    return x


def spiralit(kind, i, rng):
    if kind == "ambient":
        # Freundliches Zwitschern mit einer kleinen Luftblase.
        dur = 0.8
        x = silence(dur)
        at = 0.0
        for _ in range(rng.integers(2, 4)):
            d = rng.uniform(0.07, 0.13)
            f = rng.uniform(950, 1450)
            t = t_axis(d)
            ph, _ = sweep(f, f * rng.uniform(1.1, 1.35), d)
            note = (np.sin(ph) + 0.25 * np.sin(2 * ph)) * np.sin(np.pi * t / d) ** 0.7 * (1 + 0.08 * np.sin(2 * np.pi * 30 * t))
            place(x, note * 0.7, at)
            at += d + rng.uniform(0.02, 0.07)
        ph, _ = sweep(300, 900, 0.03)
        place(x, np.sin(ph) * env(0.03, 0.002, 0.012) * 0.4, at + 0.05)
        return x
    if kind == "hurt":
        dur = 0.3
        ph, _ = sweep(rng.uniform(1500, 1700), 850, 0.14)
        x = silence(dur)
        place(x, (np.sin(ph) + 0.3 * np.sin(2 * ph)) * env(0.14, 0.002, 0.07), 0)
        place(x, band(rng.normal(0, 1, int(0.05 * SR)), 2000, 5000) * env(0.05, 0.001, 0.015) * 0.2, 0)
        return x
    # death: traurig absinkender Pfeifton, das Vibrato erlahmt.
    dur = 1.2
    t = t_axis(dur)
    vib = 0.03 * np.sin(2 * np.pi * (7 - 5 * t / dur) * t)
    f = 1200 * (400 / 1200) ** (t / dur) * (1 + vib)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = (np.sin(ph) + 0.2 * np.sin(2 * ph)) * env(dur, 0.02, 0.5, hold=0.4)
    for k in range(3):
        bp, _ = sweep(400, 1100, 0.025)
        place(x, np.sin(bp) * env(0.025, 0.001, 0.01) * 0.3, 0.75 + 0.12 * k)
    return x


def stratolith(kind, i, rng):
    if kind == "ambient":
        # Mahlender Schichtstapel: tiefes, langsames Steinmahlen und ein Grundgestein-Brummen,
        # darüber ein fernes Kristallklingen (die Krone) und ein Hauch Sporenzischen.
        dur = 1.8
        t = t_axis(dur)
        swell = np.sin(np.pi * t / dur) ** 2
        grind = band(rng.normal(0, 1, len(t)), 120, 1400) * (0.4 + 0.6 * swell) * 0.5
        grind = grind + grains(rng, dur, 70, 200, 1200, grain=0.025, amp=0.8)
        f = rng.choice([110.0, 123.5, 98.0])  # tiefer als der Steinzeuge
        hum = sum((0.7 ** k) * np.sin(2 * np.pi * f * r * t) for k, r in enumerate([1.0, 1.5 * 1.002, 2.0, 2.5]))
        x = grind + hum * swell * 0.5
        at = rng.uniform(0.3, 0.6)                    # Kristallkrone: ein, zwei leise hohe Töne
        for _ in range(rng.integers(1, 3)):
            place(x, chime(rng, rng.uniform(1400, 2100), rng.uniform(0.3, 0.5)) * rng.uniform(0.15, 0.3), at)
            at += rng.uniform(0.4, 0.7)
        x += high(rng.normal(0, 1, len(t)), 4000) * swell * 0.02   # Sporenhauch, dezent
        return x
    if kind == "hurt":
        # Tiefer Steinriss mit splitterndem Kristall: dumpfer Schlag, Knacken, ein heller Ting.
        dur = 0.6
        x = silence(dur)
        place(x, thud(140, 55, 0.35) * 0.4, 0)
        place(x, band(rng.normal(0, 1, int(0.07 * SR)), 500, 3500) * env(0.07, 0.0005, 0.02) * 1.2, 0.002)
        place(x, grains(rng, 0.4, 150, 300, 2000, grain=0.016, amp=0.9, decay_rate=5), 0.02)
        place(x, chime(rng, rng.uniform(1600, 2200), 0.25) * 0.3, 0.03)   # Kristallsplitter
        return x
    if kind == "death":
        # Der ganze Stapel bricht zusammen: Kristall zerspringt, lange Steinlawine,
        # das Echo sinkt tief ab, ein letzter Grundgestein-Schlag.
        dur = 2.6
        t = t_axis(dur)
        x = grains(rng, dur, 300, 200, 2000, grain=0.02, decay_rate=1.4, amp=1.3)
        place(x, thud(150, 45, 0.6) * 0.5, 0)
        at = 0.0                                       # Kristall zersplittert am Anfang
        for _ in range(5):
            place(x, chime(rng, rng.uniform(1300, 2200), rng.uniform(0.2, 0.4)) * rng.uniform(0.2, 0.4), at)
            at += rng.uniform(0.04, 0.12)
        ph, _ = sweep(160, 70, dur)                    # absinkendes Echo
        x += (np.sin(ph) + 0.5 * np.sin(2 * ph)) * env(dur, 0.05, 0.9, hold=0.4) * 0.35
        place(x, thud(110, 38, 0.7) * 0.45, dur - 0.9)  # letzter Schlag
        return x
    if kind == "rise":
        # Aufsteigen aus dem Boden: anschwellendes unterirdisches Grollen, mahlender Stein,
        # ein aufwärts gleitender Ton, am Ende ein schwerer Schlag, als er steht.
        dur = 1.8
        x = silence(dur)
        nr = int(1.5 * SR)
        up = np.clip(np.arange(nr) / SR / 1.3, 0, 1)
        rumble = np.tanh(low(rng.normal(0, 1, nr), 180, order=4) * 7.0) * 0.55 * up ** 1.4
        place(x, rumble, 0)
        ph, _ = sweep(40, 90, 1.5)
        place(x, np.tanh(1.8 * np.sin(ph)) * up * 0.4, 0)
        place(x, band(rng.normal(0, 1, nr), 200, 1600) * up ** 1.3 * 0.7, 0)
        place(x, band(rng.normal(0, 1, nr), 1000, 3000) * up ** 1.6 * 0.18, 0)   # Präsenz für kleine Lautsprecher
        place(x, grains(rng, 1.4, 120, 300, 2200, amp=0.5, decay_rate=1.5), 0.1)
        place(x, thud(130, 45, 0.6) * 0.55, 1.4)       # steht
        return x
    if kind == "rewind":
        # Zurückspulen (die Kernmechanik): die ~2-s-Vorwarnung. Ein sofort hörbarer Auftakt
        # (Glocke + kurzer Abwärts-Ton, damit der Tell gleich am Anfang steht), danach rückwärts
        # laufende Zeit: ein nach oben saugender Ton und rückwärts gedrehte Chimes, die zum
        # Zurückspulen hin anschwellen. Gegenzug (Spitzhacke von vorn) hat so volle 2 s Zeit.
        dur = 2.0
        t = t_axis(dur)
        x = silence(dur)
        place(x, chime(rng, 660, 1.4, ratios=(1.0, 2.0, 3.0), bright=0.6) * env(1.4, 0.005, 1.1) * 0.4, 0.0)
        ph0, _ = sweep(520, 300, 0.25)                 # Auftakt-Abwärtston, gleich am Anfang
        place(x, (np.sin(ph0) + 0.4 * np.sin(2 * ph0)) * env(0.25, 0.004, 0.12) * 0.35, 0.0)
        rev = np.clip(t / dur, 0, 1) ** 1.4            # umgekehrte Hülle: schwillt zum Ende an
        ph, _ = sweep(180, 560, dur, shape="exp")
        x += (np.sin(ph) + 0.4 * np.sin(2 * ph)) * rev * 0.4
        at = 0.15                                      # rückwärts gedrehte Chimes
        while at < dur - 0.35:
            place(x, chime(rng, rng.uniform(900, 1800), 0.3)[::-1] * 0.25, at)
            at += rng.uniform(0.28, 0.42)
        x += band(rng.normal(0, 1, len(t)), 400, 3000) * rev * 0.1
        return x
    # slam: Hammerschlag — tiefes, mahlendes Brüllen des Bosses, kurz und schwer.
    dur = 0.7
    x = silence(dur)
    place(x, growl_voiced(rng, 90, 70, 0.5, rough=35) * env(0.5, 0.01, 0.25, hold=0.1), 0)
    place(x, low(rng.normal(0, 1, int(0.3 * SR)), 2000) * env(0.3, 0.002, 0.12) * 0.4, 0)
    place(x, grains(rng, 0.5, 100, 250, 1800, amp=0.5, decay_rate=4), 0.02)
    place(x, band(rng.normal(0, 1, int(0.025 * SR)), 1500, 5000) * env(0.025, 0.0004, 0.01) * 0.5, 0.0)  # heller Crack
    return x


def fork_tone(f0, dur, decay, clang=0.35):
    """Stimmgabel: fast reiner Grundton, dazu der kurze, hohe Anschlag-Teilton (6,27-fach), der schnell verklingt."""
    t = t_axis(dur)
    x = np.sin(2 * np.pi * f0 * t) * np.exp(-t / decay)
    x += clang * np.sin(2 * np.pi * f0 * 6.27 * t) * np.exp(-t / 0.06)
    x += 0.15 * np.sin(2 * np.pi * f0 * 2.0 * t) * np.exp(-t / (decay * 0.4))
    return x * env(dur, 0.002, dur, hold=0.0)


def resonant_shard(kind, i, rng):
    if kind == "ambient":
        # Leises Summen einer Stimmgabel: zwei fast gleiche Töne schweben gegeneinander, dazu ein Kristallklang.
        dur = 1.4
        t = t_axis(dur)
        f = rng.choice([880.0, 932.3, 830.6])
        beat = rng.uniform(2.5, 4.0)
        x = (np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * (f + beat) * t)) * 0.4
        x += 0.12 * np.sin(2 * np.pi * f * 2 * t)
        x *= np.sin(np.pi * t / dur) ** 1.5
        place(x, chime(rng, f * 1.5, 0.4) * 0.25, rng.uniform(0.2, 0.6))
        return x
    if kind == "resonate":
        # Gleichklang: ein klarer Gabelanschlag, der lange nachklingt (der Ruheplatz „läutet“).
        dur = 1.8
        x = fork_tone(660.0, dur, 0.7)
        x += 0.5 * fork_tone(990.0, dur, 0.5, clang=0.15)
        return x
    if kind == "dissonance":
        # Unruhe (0,6 s = Vorwarnzeit): zwei schiefe Töne im Tritonus, die schneller gegeneinander schweben und steigen.
        dur = 0.6
        t = t_axis(dur)
        ph1, _ = sweep(700, 900, dur)
        ph2, _ = sweep(990, 1290, dur)
        trem = 0.6 + 0.4 * np.sin(2 * np.pi * (8 + 22 * t / dur) * t)
        x = (np.sin(ph1) + 0.8 * np.sin(ph2) + 0.25 * np.sin(2 * ph1)) * trem
        return x * np.clip(t / 0.08, 0, 1) * np.clip((dur - t) / 0.04, 0, 1) * (0.5 + 0.5 * t / dur)
    if kind == "shriek":
        # Aufschrei: greller, unharmonischer Ausbruch, der abwärts kippt, dazu splitternder Quarz.
        dur = 0.7
        t = t_axis(dur)
        x = silence(dur)
        for r in (1.0, 1.41, 2.13, 2.97, 4.1):
            ph, _ = sweep(1200 * r, 700 * r, dur)
            x += np.sin(ph) * env(dur, 0.003, 0.22) * (0.6 / r ** 0.5)
        x += band(rng.normal(0, 1, len(t)), 1500, 6000) * env(dur, 0.002, 0.08) * 0.5
        place(x, grains(rng, 0.4, 120, 2000, 6000, grain=0.006, amp=0.6, decay_rate=5), 0.02)
        return x
    if kind == "hurt":
        # Knacks im Quarz: heller Anschlag, kurzes Klirren.
        dur = 0.4
        x = silence(dur)
        place(x, band(rng.normal(0, 1, int(0.02 * SR)), 2000, 7000) * env(0.02, 0.0003, 0.006), 0)
        place(x, chime(rng, rng.uniform(1300, 1600), 0.3) * 0.8, 0.003)
        place(x, fork_tone(rng.uniform(600, 700), 0.35, 0.1, clang=0.2) * 0.5, 0.0)
        return x
    # death: die Gabel zerspringt; Splitter klirren herab, der letzte Ton sinkt und erstirbt.
    dur = 1.5
    t = t_axis(dur)
    ph, _ = sweep(660, 330, dur)
    x = np.sin(ph) * env(dur, 0.005, 0.5, hold=0.1) * 0.6
    at = 0.0
    for _ in range(7):
        place(x, chime(rng, rng.uniform(1200, 2600), rng.uniform(0.15, 0.35)) * rng.uniform(0.3, 0.6), at)
        at += rng.uniform(0.04, 0.12)
    place(x, grains(rng, 0.8, 160, 1500, 6000, grain=0.006, amp=0.6, decay_rate=3), 0.0)
    return x


MOBS = {
    "geode_crawler": (geode_crawler, {"ambient": 3, "hurt": 2, "death": 2}),
    "spore_bellows": (spore_bellows, {"ambient": 3, "hurt": 2, "death": 2}),
    "petrified_witness": (petrified_witness, {"ambient": 3, "hurt": 2, "death": 1}),
    "ember_burrower": (ember_burrower, {"ambient": 3, "hurt": 2, "death": 2, "dig": 3, "tremor": 2, "emerge": 2}),
    "spiralit": (spiralit, {"ambient": 3, "hurt": 2, "death": 1}),
    "stratolith": (stratolith, {"ambient": 3, "hurt": 2, "death": 1, "rise": 1, "rewind": 2, "slam": 2}),
    "resonant_shard": (resonant_shard, {"ambient": 2, "hurt": 2, "death": 1, "resonate": 1, "dissonance": 1, "shriek": 1}),
}


# ---------------------------------------------------------------- Ausgabe

def write_ogg(x, path):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    try:
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav_path, "-ac", "1", "-ar", str(SR),
                        "-c:a", "libvorbis", "-q:a", "5", "-map_metadata", "-1", "-fflags", "+bitexact",
                        "-flags:a", "+bitexact", path], check=True)
    finally:
        os.unlink(wav_path)


def seed_for(mob, kind, i):
    return sum(ord(c) * (k + 1) for k, c in enumerate(f"{mob}/{kind}/{i}")) % (2 ** 31)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", help="Spektrogramme aller Sounds als PNG speichern")
    ap.add_argument("--wav", help="zusätzlich WAV-Dateien in diesen Ordner schreiben (zum Anhören)")
    args = ap.parse_args()
    made = []
    for mob, (fn, kinds) in MOBS.items():
        os.makedirs(os.path.join(OUT, mob), exist_ok=True)
        for kind, count in kinds.items():
            for i in range(1, count + 1):
                rng = np.random.default_rng(seed_for(mob, kind, i))
                x = normalize(variant(fn(kind, i, rng), i) if kind != "tremor" else fn(kind, i, rng), -22.0 if kind == "ambient" else -18.0)
                write_ogg(x, os.path.join(OUT, mob, f"{kind}{i}.ogg"))
                if args.wav:
                    os.makedirs(args.wav, exist_ok=True)
                    with wave.open(os.path.join(args.wav, f"{mob}_{kind}{i}.wav"), "wb") as w:
                        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
                        w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
                made.append((mob, kind, i, x))
    print(f"{len(made)} Sounds nach {os.path.relpath(OUT, ROOT)} geschrieben")
    if args.sheet:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        cols = 6
        rows = (len(made) + cols - 1) // cols
        fig, axes = plt.subplots(rows, cols, figsize=(cols * 3, rows * 2.2))
        for ax in axes.flat:
            ax.axis("off")
        for ax, (mob, kind, i, x) in zip(axes.flat, made):
            ax.axis("on")
            ax.specgram(x, NFFT=512, Fs=SR, noverlap=384, cmap="magma", vmin=-120)
            ax.set_ylim(0, 8000)
            ax.set_title(f"{mob}\n{kind}{i} ({len(x) / SR:.2f}s)", fontsize=7)
            ax.tick_params(labelsize=5)
        fig.tight_layout()
        fig.savefig(args.sheet, dpi=110)
        print("Spektrogramme:", args.sheet)


if __name__ == "__main__":
    sys.exit(main())
