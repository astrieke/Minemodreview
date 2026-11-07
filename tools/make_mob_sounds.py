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


def normalize(x, rms_db=-18.0, peak_db=-1.0):
    """Gleiche Lautheit für alle Sounds (RMS), Spitzen höchstens peak_db; das Ende blendet 25 ms aus."""
    # Hochpass statt Mittelwert abziehen: kein Gleichanteil, die Stille bleibt still.
    x = high(x, 30)
    rms = np.sqrt(np.mean(x ** 2))
    if rms < 1e-9:
        return x
    x = x * (10 ** (rms_db / 20) / rms)
    peak = np.max(np.abs(x))
    if peak > 10 ** (peak_db / 20):
        x = x * (10 ** (peak_db / 20) / peak)
    x = fade(x, ms=6)
    n = int(0.025 * SR)
    x[-n:] *= np.linspace(1, 0, n) ** 2
    return x


# ---------------------------------------------------------------- Mobs

def geode_crawler(kind, i, rng):
    if kind == "ambient":
        # Ein paar helle Kristall-Klicks und ein leises Schaben der Panzerplatten.
        dur = 0.9
        x = silence(dur)
        at = 0.0
        for _ in range(rng.integers(2, 4)):
            place(x, chime(rng, rng.uniform(1700, 2500), rng.uniform(0.18, 0.32)) * rng.uniform(0.5, 1.0), at)
            at += rng.uniform(0.09, 0.22)
        place(x, band(rng.normal(0, 1, int(0.08 * SR)), 2500, 6000) * env(0.08, 0.01, 0.03) * 0.25, rng.uniform(0.0, 0.3))
        return x
    if kind == "hurt":
        # Knacks: kurzer Splitter-Burst plus tieferer, schnell gedämpfter Klang.
        dur = 0.45
        x = silence(dur)
        place(x, high(rng.normal(0, 1, int(0.03 * SR)), 2500) * env(0.03, 0.0005, 0.008), 0)
        place(x, chime(rng, rng.uniform(1100, 1400), 0.25, bright=1.3) * 0.9, 0.004)
        place(x, grains(rng, 0.2, 60, 3000, 7000, amp=0.4), 0.01)
        return x
    # death: Zerspringen, die Splitter rieseln aus.
    dur = 1.4
    x = silence(dur)
    place(x, thud(160, 60, 0.25) * 0.8, 0)
    place(x, high(rng.normal(0, 1, int(0.05 * SR)), 2000) * env(0.05, 0.0005, 0.015) * 0.8, 0)
    for _ in range(28):
        at = rng.exponential(0.25)
        if at < dur - 0.3:
            place(x, chime(rng, rng.uniform(2200, 5200), rng.uniform(0.08, 0.2)) * rng.uniform(0.15, 0.6), at)
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
        grind = low(rng.normal(0, 1, len(t)), 500) * (0.5 + 0.5 * np.sin(np.pi * t / dur)) * 0.6
        x = grind + grains(rng, dur, 70, 300, 900, grain=0.02, amp=0.8)
        f = rng.choice([98.0, 110.0, 87.3])
        hum = (np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * f * 1.5 * 1.003 * t)) * np.sin(np.pi * t / dur) ** 2
        return x + hum * 0.35
    if kind == "hurt":
        # Steinriss: dumpfer Schlag, Knacken, Kies.
        dur = 0.55
        x = silence(dur)
        place(x, thud(120, 45, 0.3), 0)
        place(x, band(rng.normal(0, 1, int(0.04 * SR)), 900, 4000) * env(0.04, 0.0005, 0.01) * 0.8, 0.002)
        place(x, grains(rng, 0.35, 90, 400, 2500, amp=0.5, decay_rate=6), 0.02)
        return x
    # death: Zerbröckeln, das Echo verklingt nach unten.
    dur = 2.0
    t = t_axis(dur)
    x = grains(rng, dur, 220, 250, 2200, grain=0.018, decay_rate=1.8, amp=0.9)
    place(x, thud(100, 35, 0.5) * 1.2, 0)
    ph, _ = sweep(110, 55, dur)
    x += np.sin(ph) * env(dur, 0.05, 0.7, hold=0.3) * 0.4
    return x


def ember_burrower(kind, i, rng):
    if kind == "ambient":
        # Tiefes Knurren mit knisternden Glutfunken.
        dur = 1.0
        f = rng.uniform(62, 78)
        x = growl(rng, f, f * rng.uniform(0.85, 1.05), dur) * env(dur, 0.08, 0.35, hold=0.35)
        x += grains(rng, dur, 40, 2500, 7000, grain=0.004, amp=0.5)
        return x
    if kind == "hurt":
        # Zischen und ein gepresstes Aufjaulen.
        dur = 0.55
        x = silence(dur)
        place(x, high(rng.normal(0, 1, int(0.4 * SR)), 3000) * env(0.4, 0.003, 0.12) * 0.5, 0)
        place(x, growl(rng, 130, 85, 0.35, cutoff=1400, rough=40) * env(0.35, 0.005, 0.12), 0.01)
        return x
    if kind == "death":
        # Lange verzischende Glut, ein letztes Knurren, Brocken fallen.
        dur = 1.9
        t = t_axis(dur)
        hiss = high(rng.normal(0, 1, len(t)), 2500) * env(dur, 0.01, 0.8, hold=0.2) * 0.35
        x = hiss + growl(rng, 90, 40, dur, cutoff=600) * env(dur, 0.01, 0.4, hold=0.2)
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
        # Grollen unter den Füßen, das anschwillt (die Warnung vor dem Hervorbrechen).
        dur = 0.9
        t = t_axis(dur)
        swell = (t / dur) ** 1.5 * np.clip((dur - t) / 0.08, 0, 1)
        rumble = low(rng.normal(0, 1, len(t)), 180, order=4) * 3.0
        sub = np.sin(2 * np.pi * 55 * t) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t))
        x = (rumble + sub * 0.8) * swell
        x += grains(rng, dur, 30, 600, 2000, amp=0.4) * swell
        return x
    # emerge: Ausbruch aus dem Boden.
    dur = 1.0
    x = silence(dur)
    place(x, thud(110, 38, 0.6) * 1.2, 0)
    place(x, low(rng.normal(0, 1, int(0.6 * SR)), 2200) * env(0.6, 0.002, 0.15), 0)
    place(x, growl(rng, 95, 120, 0.45, cutoff=1200) * env(0.45, 0.01, 0.2) * 0.8, 0.05)
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


MOBS = {
    "geode_crawler": (geode_crawler, {"ambient": 3, "hurt": 2, "death": 2}),
    "spore_bellows": (spore_bellows, {"ambient": 3, "hurt": 2, "death": 2}),
    "petrified_witness": (petrified_witness, {"ambient": 3, "hurt": 2, "death": 1}),
    "ember_burrower": (ember_burrower, {"ambient": 3, "hurt": 2, "death": 2, "dig": 3, "tremor": 2, "emerge": 2}),
    "spiralit": (spiralit, {"ambient": 3, "hurt": 2, "death": 1}),
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
                x = normalize(fn(kind, i, rng), -20.0 if kind == "ambient" else -17.0)
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
