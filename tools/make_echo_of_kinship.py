"""Generate the animated Echo of Kinship item texture.

Twelve frames of a ringed relic turning, stacked vertically the way Minecraft
wants an animated sprite, with a .mcmeta beside it to play them.

The turn is sold by four markers orbiting a tilted ring rather than by spinning
the whole sprite: a ring rotated in its own plane is the same ellipse at every
angle, so nothing would appear to move. The markers are what carries it -- they
sweep wide across the front, dim and narrow as they pass behind the core, and
the two halves of the orbit draw in different order so the front ones cross in
front of the orb.
"""
import math
import os
import struct
import zlib
import json

from item_glow import glow_model

OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\textures\item"

SIZE = 16
FRAMES = 12

CX = CY = 7.5

# The orbit, as a circle seen nearly edge on.
RING_X = 6.6
RING_Y = 2.7

ORB = 3.1
MARKERS = 4

LIGHT = (-0.50, -0.60, 0.62)


def ramp(*hexes):
    out = []
    for h in hexes:
        h = h.lstrip("#")
        out.append((int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255))
    return out


# Light purple, matching the shine the dropped item gives off in world.
VIOLET = ramp("#2a1140", "#432066", "#63339b", "#8b52cf", "#b98ae8", "#e2d0fa")
PALE = ramp("#4a2c6b", "#6b4796", "#9370c4", "#bba1e0", "#dccbf3", "#ffffff")


def blend(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def orb_shade(x, y):
    """Lit from the upper left, like everything else in the mod."""
    u = (x + 0.5 - CX) / ORB
    v = (y + 0.5 - CY) / ORB
    d = u * u + v * v
    if d > 1.0:
        return None
    nz = math.sqrt(max(0.0, 1.0 - d))
    lx, ly, lz = LIGHT
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    lam = max(0.0, (u * lx + v * ly + nz * lz) / ln)
    return 0.30 + 0.70 * lam


def frame(n):
    px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    theta = 2.0 * math.pi * n / FRAMES

    def dot(fx, fy, table, level, size):
        for oy in range(size):
            for ox in range(size):
                x, y = int(fx) + ox, int(fy) + oy
                if 0 <= x < SIZE and 0 <= y < SIZE:
                    px[y][x] = table[level]

    # The orbit path itself, faint, so there is something for the markers to
    # travel along even at the moment none of them is at the near edge.
    for step in range(96):
        a = 2.0 * math.pi * step / 96
        x = int(round(CX + RING_X * math.cos(a)))
        y = int(round(CY + RING_Y * math.sin(a)))
        if 0 <= x < SIZE and 0 <= y < SIZE and px[y][x][3] == 0:
            px[y][x] = VIOLET[1]

    behind, front = [], []
    for k in range(MARKERS):
        phi = theta + 2.0 * math.pi * k / MARKERS
        mx = CX + RING_X * math.cos(phi) - 0.5
        my = CY + RING_Y * math.sin(phi) - 0.5
        (front if math.sin(phi) > 0 else behind).append((mx, my, math.sin(phi)))

    # Behind the core: small and dim, so depth reads without needing shading.
    for mx, my, _ in behind:
        dot(mx, my, VIOLET, 2, 1)

    for y in range(SIZE):
        for x in range(SIZE):
            s = orb_shade(x, y)
            if s is None:
                continue
            base = VIOLET[min(len(VIOLET) - 1, int(s * len(VIOLET)))]
            # A hot centre, so the relic reads as holding something rather than
            # being a solid bead.
            core = 1.0 - min(1.0, math.hypot(x + 0.5 - CX, y + 0.5 - CY) / ORB)
            px[y][x] = blend(base, PALE[5], core * core * 0.7)

    # In front: bigger and brighter, and drawn last so they cross the orb.
    # Held off the top of the ramp -- pure white made them read as the subject
    # and the relic behind them as a smudge.
    for mx, my, depth in front:
        dot(mx, my, PALE, min(4, 2 + int(round(depth * 2))), 2)

    return px


def chunk(tag, data):
    body = tag + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def write_strip(path, frames):
    height = SIZE * len(frames)
    raw = bytearray()
    for f in frames:
        for y in range(SIZE):
            raw.append(0)
            for x in range(SIZE):
                raw.extend(f[y][x])
    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    open(path, "wb").write(blob)
    print("wrote %s (%dx%d, %d frames)" % (os.path.basename(path), SIZE, height, len(frames)))


os.makedirs(OUT, exist_ok=True)
write_strip(os.path.join(OUT, "echo_of_kinship.png"), [frame(n) for n in range(FRAMES)])

with open(os.path.join(OUT, "echo_of_kinship.png.mcmeta"), "w", encoding="utf-8") as fh:
    fh.write('{\n  "animation": {\n    "frametime": 2,\n    "interpolate": true\n  }\n}\n')
print("wrote echo_of_kinship.png.mcmeta")

# The Echo burns whole, not in part, so this is the one glowing layer in the mod
# with no sheet of its own -- it wears the item's own texture. Which is also why
# it is the only one that cannot fall out of step: there is a single animation
# here, not two that have to agree.
MODEL_OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\models\item"
os.makedirs(MODEL_OUT, exist_ok=True)

model_path = os.path.join(MODEL_OUT, "echo_of_kinship_glow.json")
with open(model_path, "w", encoding="utf-8") as fh:
    json.dump(glow_model("echo_of_kinship", texture="echo_of_kinship"), fh, indent=2)
    fh.write("\n")
print("wrote %s" % os.path.basename(model_path))
