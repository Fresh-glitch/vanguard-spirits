"""Generate the stone_wake particle frames.

An arc of displaced air: a tapered crescent that widens, thins and fades as it
ages. 16x16 because at 8x8 an arc has one pixel of thickness to work with and
collapses into a smudge.
"""
import math
import struct
import zlib
import os

SIZE = 16
FRAMES = 6
OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\textures\particle"

# An even sprite has no pixel at its centre; the four innermost sit 0.707 away.
CENTRE = (SIZE - 1) / 2.0

# Pale, cold, and very slightly blue -- displaced air over dark deepslate.
CORE = (232, 244, 255)
EDGE = (150, 186, 214)


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def frame(i):
    p = i / (FRAMES - 1.0)

    radius = 2.7 + 4.3 * p          # the arc travels outward as it ages
    thick = 1.8 - 0.8 * p           # and thins while it goes
    span = math.radians(175 - 50 * p)

    # Holds its brightness through the middle and drops late, so the stroke is
    # legible for most of its life instead of only on the frame it appears.
    fade = 1.0 - 0.75 * p ** 2.0

    px = bytearray()
    for y in range(SIZE):
        px.append(0)
        for x in range(SIZE):
            dx = x - CENTRE
            dy = y - CENTRE
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)

            # Radial falloff across the band of the arc.
            band = smooth(1.0 - abs(r - radius) / thick)

            # Angular window, tapered at both ends so it reads as a stroke
            # rather than a slice out of a ring.
            off = abs((ang + math.pi) % (2 * math.pi) - math.pi)
            wing = smooth(1.0 - off / (span / 2.0))

            a = band * (wing ** 0.8) * fade
            if a <= 0.004:
                px.extend((0, 0, 0, 0))
                continue

            # Brightest along the spine of the arc.
            mix = band
            col = tuple(int(EDGE[c] + (CORE[c] - EDGE[c]) * mix) for c in range(3))
            px.extend((col[0], col[1], col[2], min(255, int(a * 255))))

    return bytes(px)


def chunk(tag, data):
    body = tag + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def png(raw):
    head = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", head)
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


os.makedirs(OUT, exist_ok=True)
for i in range(FRAMES):
    path = os.path.join(OUT, "stone_wake_%d.png" % i)
    with open(path, "wb") as fh:
        fh.write(png(frame(i)))
    print("wrote", path)
