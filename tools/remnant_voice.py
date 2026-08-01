"""
Synthesises the Remnant's voice.

Not a remix of vanilla samples: every file here is generated from scratch by a
small source-filter voice model, which is the same idea real speech synthesis
uses. A jittery glottal pulse train stands in for vocal folds, three bandpass
filters stand in for the throat and mouth shaping it into a vowel, and breath
noise rides on top.

That model is why it reads as something that used to talk. Detune the pulse
train, widen the jitter and let the noise dominate, and a voice becomes a voice
that no longer works properly -- which is exactly what a Remnant is.

Writes 16-bit mono WAV; ffmpeg turns them into the Ogg Vorbis that Minecraft
wants.
"""

import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100


# ------------------------------------------------------------------ filters

class Biquad:
    """RBJ cookbook biquad. Enough to build formants out of."""

    def __init__(self, kind, freq, q, rate=RATE):
        w0 = 2.0 * math.pi * freq / rate
        cos_w0 = math.cos(w0)
        alpha = math.sin(w0) / (2.0 * q)

        if kind == "bandpass":
            b0, b1, b2 = alpha, 0.0, -alpha
        elif kind == "lowpass":
            b0 = (1.0 - cos_w0) / 2.0
            b1 = 1.0 - cos_w0
            b2 = b0
        elif kind == "highpass":
            b0 = (1.0 + cos_w0) / 2.0
            b1 = -(1.0 + cos_w0)
            b2 = b0
        else:
            raise ValueError(kind)

        a0 = 1.0 + alpha
        a1 = -2.0 * cos_w0
        a2 = 1.0 - alpha

        self.b = (b0 / a0, b1 / a0, b2 / a0)
        self.a = (a1 / a0, a2 / a0)
        self.x1 = self.x2 = self.y1 = self.y2 = 0.0

    def step(self, x):
        b0, b1, b2 = self.b
        a1, a2 = self.a
        y = b0 * x + b1 * self.x1 + b2 * self.x2 - a1 * self.y1 - a2 * self.y2
        self.x2, self.x1 = self.x1, x
        self.y2, self.y1 = self.y1, y
        return y


def formants(signal, table, rate=RATE):
    """Runs a signal through a bank of bandpasses that slide over its length."""
    out = [0.0] * len(signal)
    n = len(signal)

    for start, end, q, gain in table:
        # Re-tuning a biquad every sample is expensive and pointless; stepping
        # it in blocks is inaudible and an order of magnitude cheaper.
        block = 256
        filt = None
        for i in range(n):
            if i % block == 0:
                t = i / max(1, n - 1)
                filt = Biquad("bandpass", start + (end - start) * t, q, rate)
            out[i] += filt.step(signal[i]) * gain
    return out


# ------------------------------------------------------------------ sources

def glottal(n, f_start, f_end, jitter, rng):
    """
    A pulse train with unstable pitch.

    Real vocal folds have a little jitter. Far too much of it is what makes a
    voice sound wrong in a way listeners read as damaged rather than synthetic.
    """
    out = [0.0] * n
    phase = 0.0
    for i in range(n):
        t = i / max(1, n - 1)
        f = f_start + (f_end - f_start) * t
        f *= 1.0 + rng.uniform(-jitter, jitter)
        phase += f / RATE
        if phase >= 1.0:
            phase -= 1.0
        # Sawtooth-ish glottal pulse: strong harmonics for the formants to bite.
        out[i] = 2.0 * phase - 1.0
    return out


def breath(n, rng):
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def envelope(n, points):
    """Piecewise-linear envelope from (fraction, level) points."""
    out = [0.0] * n
    for i in range(n):
        t = i / max(1, n - 1)
        for j in range(len(points) - 1):
            t0, v0 = points[j]
            t1, v1 = points[j + 1]
            if t0 <= t <= t1:
                span = t1 - t0
                k = 0.0 if span <= 0 else (t - t0) / span
                out[i] = v0 + (v1 - v0) * k
                break
    return out


# ------------------------------------------------------------------- voices

def rasp(seed):
    """Ambient: a hollow half-word that never finishes."""
    rng = random.Random(seed)
    n = int(RATE * rng.uniform(1.1, 1.5))

    cords = glottal(n, rng.uniform(78, 96), rng.uniform(62, 74), 0.055, rng)
    voiced = formants(cords, [
        (rng.uniform(390, 460), rng.uniform(330, 400), 6.0, 1.0),
        (rng.uniform(1050, 1250), rng.uniform(900, 1050), 9.0, 0.55),
        (2500, 2300, 12.0, 0.22),
    ])

    air = breath(n, rng)
    hp = Biquad("highpass", 900, 0.7)
    air = [hp.step(s) for s in air]

    env = envelope(n, [(0.0, 0.0), (0.16, 1.0), (0.55, 0.85), (1.0, 0.0)])
    waver = [1.0 + 0.25 * math.sin(2 * math.pi * 4.5 * i / RATE) for i in range(n)]

    return [(voiced[i] * 0.75 + air[i] * 0.3) * env[i] * waver[i] * 0.5 for i in range(n)]


def notice(seed):
    """A dry intake, then a thin cry. Played the moment its eyes kindle."""
    rng = random.Random(seed)
    n = int(RATE * 0.95)
    gasp = int(n * 0.35)

    out = [0.0] * n

    # Intake: noise swept upward by a rising highpass.
    air = breath(gasp, rng)
    for i in range(gasp):
        if i % 128 == 0:
            hp = Biquad("highpass", 300 + 2200 * (i / gasp), 0.8)
        out[i] = hp.step(air[i]) * envelope(gasp, [(0.0, 0.0), (0.7, 1.0), (1.0, 0.4)])[i] * 0.5

    # Cry: pitch climbing, formants opening out.
    tail = n - gasp
    cords = glottal(tail, 150, 420, 0.03, rng)
    voiced = formants(cords, [(500, 900, 7.0, 1.0), (1400, 2200, 10.0, 0.6)])
    env = envelope(tail, [(0.0, 0.0), (0.12, 1.0), (0.6, 0.7), (1.0, 0.0)])
    for i in range(tail):
        out[gasp + i] += voiced[i] * env[i] * 0.6

    return out


def hurt(seed):
    """A cracked bark. Short, and it breaks halfway through."""
    rng = random.Random(seed)
    n = int(RATE * rng.uniform(0.34, 0.44))

    cords = glottal(n, rng.uniform(180, 220), rng.uniform(80, 105), 0.12, rng)
    voiced = formants(cords, [(620, 400, 5.0, 1.0), (1500, 1000, 8.0, 0.5)])

    air = breath(n, rng)
    hp = Biquad("highpass", 1400, 0.7)
    air = [hp.step(s) for s in air]

    env = envelope(n, [(0.0, 0.0), (0.05, 1.0), (0.35, 0.55), (1.0, 0.0)])
    return [(voiced[i] * 0.8 + air[i] * 0.45) * env[i] * 0.75 for i in range(n)]


def death(seed):
    """Everything holding it together lets go at once."""
    rng = random.Random(seed)
    n = int(RATE * 1.9)

    cords = glottal(n, 130, 38, 0.09, rng)
    voiced = formants(cords, [(560, 200, 5.0, 1.0), (1300, 600, 8.0, 0.5), (2400, 1400, 12.0, 0.2)])

    air = breath(n, rng)
    hp = Biquad("highpass", 700, 0.6)
    air = [hp.step(s) for s in air]

    env = envelope(n, [(0.0, 0.0), (0.06, 1.0), (0.45, 0.6), (0.8, 0.25), (1.0, 0.0)])

    out = [(voiced[i] * 0.8 + air[i] * 0.35) * env[i] * 0.7 for i in range(n)]

    # A dry rattle over the last third: the noise bed chopped into grains.
    start = int(n * 0.6)
    gate = 1.0
    for i in range(start, n):
        if (i - start) % int(RATE * 0.035) == 0:
            gate = rng.choice([0.0, 0.35, 1.0])
        out[i] += air[i] * gate * 0.28 * (1.0 - (i - start) / (n - start))

    return out


def step(seed):
    """A light scuff. Dry, and gone almost before it registers."""
    rng = random.Random(seed)
    n = int(RATE * rng.uniform(0.10, 0.15))

    air = breath(n, rng)
    bp = Biquad("bandpass", rng.uniform(900, 1600), 1.1)
    air = [bp.step(s) for s in air]

    env = envelope(n, [(0.0, 0.0), (0.04, 1.0), (1.0, 0.0)])
    return [air[i] * env[i] * 0.5 for i in range(n)]


# -------------------------------------------------------------------- write

def save(samples, path):
    """Normalises to a safe peak and writes 16-bit mono."""
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = 0.89 / peak

    # Short fades stop the file starting or ending on a step, which clicks.
    fade = min(int(RATE * 0.006), len(samples) // 4)
    data = bytearray()
    for i, s in enumerate(samples):
        g = 1.0
        if i < fade:
            g = i / fade
        elif i > len(samples) - fade:
            g = (len(samples) - i) / fade
        v = int(max(-1.0, min(1.0, s * scale * g)) * 32767)
        data += struct.pack("<h", v)

    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(bytes(data))


VOICES = [
    ("rasp", rasp, 3),
    ("notice", notice, 1),
    ("hurt", hurt, 3),
    ("death", death, 1),
    ("step", step, 4),
]


def main(out_dir):
    out = Path(out_dir)
    made = []
    for name, fn, count in VOICES:
        for i in range(count):
            suffix = "" if count == 1 else str(i + 1)
            path = out / f"{name}{suffix}.wav"
            save(fn(hash(name) & 0xFFFF ^ (i * 7919)), path)
            made.append(path.name)
    print("\n".join(made))


if __name__ == "__main__":
    import sys
    main(sys.argv[1])
