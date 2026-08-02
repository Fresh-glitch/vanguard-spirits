"""Generate the three spawn egg item textures.

26.2 no longer tints a shared template: every vanilla spawn egg is its own
16x16 painting, and they read as an egg wearing the mob's features -- bat ears,
parrot crest, ravager horns. These follow that.

**Lighting is computed, not drawn.** Vanilla lights its eggs from the upper
left: measuring the brightest quartile of `zombie_spawn_egg.png` puts it 0.8px
left and 0.4px above the egg's centre, and `iron_golem_spawn_egg.png` 1.8 left
and 2.2 above, with a dark rim down the right and bottom. Shading by hand from
a symmetric ramp gives the pillow-shaded look instead, so each pixel's tone
here comes from a surface normal against a fixed light vector, and only the
features are placed by hand.

Colours are sampled from each mob's own entity texture. The one liberty is the
Remnant's moss, lifted a couple of steps brighter: at sixteen pixels the mob's
real olives are all within a few values of the black around them, and faithful
sampling produced an unreadable blob.
"""
import math
import os
import struct
import zlib

OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\textures\item"

# Upper left, slightly toward the viewer. Matches the vanilla measurement above.
LIGHT = (-0.50, -0.60, 0.62)

# How much of the tone survives in full shadow. Without a floor the unlit side
# goes to pure black and the egg loses its silhouette against a dark slot.
AMBIENT = 0.22


# The vanilla outline, lifted from zombie_spawn_egg.png.
MASK_STD = [
    "................",
    "......XXXX......",
    ".....XXXXXX.....",
    "....XXXXXXXX....",
    "...XXXXXXXXXX...",
    "...XXXXXXXXXX...",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "..XXXXXXXXXXXX..",
    "...XXXXXXXXXX...",
    "....XXXXXXXX....",
    ".....XXXXXX.....",
    "................",
]

# A smaller egg, matching the bat's proportions: its body runs eight wide
# through the shoulders where the standard egg runs twelve.
MASK_SMALL = [
    "................",
    "................",
    "......XXXX......",
    ".....XXXXXX.....",
    "....XXXXXXXX....",
    "....XXXXXXXX....",
    "...XXXXXXXXXX...",
    "...XXXXXXXXXX...",
    "...XXXXXXXXXX...",
    "...XXXXXXXXXX...",
    "....XXXXXXXX....",
    "....XXXXXXXX....",
    ".....XXXXXX.....",
    "......XXXX......",
    "................",
    "................",
]


def ramp(*hexes):
    out = []
    for h in hexes:
        h = h.lstrip("#")
        out.append((int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255))
    return out


STONE = ramp("#0e0f13", "#15171c", "#23252c", "#2e313a", "#3a3d46", "#4e525d")
GOLD = ramp("#5c4110", "#6b4c12", "#8c6419", "#c79a38", "#e6cb7c", "#f5e3ab")
GRAVE = ramp("#070809", "#0d0e12", "#101116", "#191b22", "#23262e", "#2e323b")
MOSS = ramp("#1b1f16", "#23281c", "#2a3026", "#3a372f", "#4d5a3a", "#63704a")
CROW = ramp("#07080a", "#0a0b0e", "#121318", "#1c1e25", "#262932", "#343844")
QUILL = ramp("#16181e", "#22252d", "#2e323d", "#3d4352", "#4a5163", "#5b6377")
AMBER = ramp("#5e3d12", "#7a4f18", "#96601f", "#b07426", "#c98c3a", "#dda757")
WHITE = ramp("#c9d2dd", "#dde4ec", "#eef3f8", "#ffffff", "#ffffff", "#ffffff")


def shade_of(x, y, cx, cy, rx, ry):
    """Ramp position 0..1 for a pixel, from an ellipsoid lit by LIGHT."""
    u = (x + 0.5 - cx) / rx
    v = (y + 0.5 - cy) / ry
    d = u * u + v * v
    if d > 1.0:
        # Just past the silhouette edge; treat as the rim rather than skipping.
        u, v = u / math.sqrt(d), v / math.sqrt(d)
        d = 1.0
    nz = math.sqrt(max(0.0, 1.0 - d))

    lx, ly, lz = LIGHT
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    lam = max(0.0, (u * lx + v * ly + nz * lz) / ln)

    # A touch of rim darkening so the edge reads as a curve rolling away.
    return AMBIENT + (1.0 - AMBIENT) * lam * (0.55 + 0.45 * nz)


def bake(mask, overlay, ramps, wings=None):
    """mask picks the silhouette, overlay picks which ramp each pixel uses."""
    rows = [y for y, r in enumerate(mask) if "X" in r]
    cols = [x for y in rows for x in range(16) if mask[y][x] == "X"]
    cy = (min(rows) + max(rows) + 1) / 2.0
    cx = (min(cols) + max(cols) + 1) / 2.0
    ry = (max(rows) - min(rows) + 1) / 2.0
    rx = (max(cols) - min(cols) + 1) / 2.0

    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]

    for y in range(16):
        for x in range(16):
            if mask[y][x] != "X":
                continue
            s = shade_of(x, y, cx, cy, rx, ry)
            key = overlay[y][x]
            table = ramps.get(key, ramps["."])
            px[y][x] = table[min(len(table) - 1, max(0, int(s * len(table))))]

    # Wings and other protrusions live outside the egg and are lit the same way,
    # just measured against the egg's own centre so they agree with it.
    for (wx, wy, wkey) in (wings or []):
        s = shade_of(wx, wy, cx, cy, rx, ry)
        table = ramps[wkey]
        px[wy][wx] = table[min(len(table) - 1, max(0, int(s * len(table))))]

    return px


# ---------------------------------------------------------------- sentinel

# g = the trim gold of its crown and flank studs, W = the eyes.
SENTINEL_OVERLAY = [
    "................",
    "................",
    "................",
    "....gggggggg....",
    "...gggggggggg...",
    "................",
    ".....W....W.....",
    ".....W....W.....",
    "................",
    "....gg....gg....",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

# ----------------------------------------------------------------- remnant

# Blotched rather than a solid field. Covering the middle turned it into a
# green egg; the mob is a dark thing with moss taking hold of it in patches.
REMNANT_OVERLAY = [
    "................",
    "................",
    "................",
    "....mmm..mm.....",
    "...mm..mmm.m....",
    "...m.mmm..mm....",
    ".....W.mm.W.....",
    ".....W.mm.W.....",
    "...mm.mmm..m....",
    "....m..mmm.m....",
    ".....mm..mm.....",
    "................",
    "................",
    "................",
    "................",
    "................",
]

# ----------------------------------------------------------------- mourner

MOURNER_OVERLAY = [
    "................",
    "................",
    "................",
    "......AA........",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

# Swept back and slightly down, breaking the outline the way the bat's ears do.
# A black egg with an unbroken silhouette is a blob at this size.
# Tapered to a point rather than squared off: two flat rows of equal length
# read as bars bolted to the sides, not as something folded.
MOURNER_WINGS = (
    [(x, 6, "q") for x in (2, 13)]
    + [(x, 7, "q") for x in (1, 2, 13, 14)]
    + [(x, 8, "q") for x in (0, 1, 2, 13, 14, 15)]
    + [(x, 9, "q") for x in (1, 2, 13, 14)]
    + [(x, 10, "q") for x in (2, 13)]
)


def chunk(tag, data):
    body = tag + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def write(path, px):
    raw = bytearray()
    for y in range(16):
        raw.append(0)
        for x in range(16):
            raw.extend(px[y][x])
    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    open(path, "wb").write(blob)
    print("wrote", os.path.basename(path))


os.makedirs(OUT, exist_ok=True)

write(
    os.path.join(OUT, "stone_sentinel_spawn_egg.png"),
    bake(MASK_STD, SENTINEL_OVERLAY, {".": STONE, "g": GOLD, "W": WHITE}),
)
write(
    os.path.join(OUT, "remnant_spawn_egg.png"),
    bake(MASK_STD, REMNANT_OVERLAY, {".": GRAVE, "m": MOSS, "W": WHITE}),
)
write(
    os.path.join(OUT, "mourner_spawn_egg.png"),
    bake(MASK_SMALL, MOURNER_OVERLAY, {".": CROW, "A": AMBER, "q": QUILL}, MOURNER_WINGS),
)
