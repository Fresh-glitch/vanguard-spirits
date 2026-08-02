"""Generate the charm item textures.

The first pass was three concentric ovals with the middle colour swapped, which
read as three versions of one icon rather than three artefacts. These are
medallions instead: a deepslate plate hung from a gold ring, carved with a rune
in the style of the echo runes the ruins give off, lit from the upper left like
the spawn eggs so the whole item set agrees on where the light is.

The rune is the only saturated colour on the item, and it carries a one pixel
halo bled into the plate around it, so the glyph reads as cut into the stone and
lit from inside rather than painted on top.
"""
import math
import os
import struct
import zlib

OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\textures\item"

LIGHT = (-0.50, -0.60, 0.62)
AMBIENT = 0.26

# Plate hung under a ring: ring on rows 1-3, medallion on 4-14. Content runs
# from row 1 to row 14, so the blank row above and below match -- hanging it
# from the very top edge left a two pixel gap under it and none over, which
# reads as the icon having slipped down in its slot.
PLATE = [
    "................",
    "................",
    "................",
    "................",
    ".....PPPPPP.....",
    "....PPPPPPPP....",
    "...PPPPPPPPPP...",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "..PPPPPPPPPPPP..",
    "...PPPPPPPPPP...",
    "....PPPPPPPP....",
    ".....PPPPPP.....",
    "................",
]

RING = [
    "................",
    "......RRRR......",
    "......R..R......",
    "......RRRR......",
] + ["................"] * 12


def ramp(*hexes):
    out = []
    for h in hexes:
        h = h.lstrip("#")
        out.append((int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255))
    return out


# Deepslate, matching the Sentinel and the ruins it comes out of.
STONE = ramp("#0d0e12", "#15171c", "#23252c", "#2e313a", "#3a3d46", "#4e525d")
GOLD = ramp("#5c4110", "#6b4c12", "#8c6419", "#c79a38", "#e6cb7c", "#f5e3ab")

# One ramp per charm, dark rim through to the lit core of the glyph.
SKY = ramp("#1d4d63", "#2a7391", "#3d9dbe", "#63c4dd", "#9fe4f2", "#dcf7ff")
LEAF = ramp("#1d4a24", "#2a6e33", "#3d9448", "#5fbb63", "#93dd90", "#d3f6cf")
EMBER = ramp("#5a3a0e", "#7d5615", "#a8791f", "#d2a63c", "#eccd76", "#fdf0bd")
VIOLET = ramp("#33184f", "#4d2673", "#6b3a9c", "#9b6bd8", "#c3a3ec", "#ecdcff")


# --------------------------------------------------------------- the glyphs

# Rising: a chevron over a stem. Whoever this was, they went up.
LEAPER_RUNE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......##.......",
    "......####......",
    ".....##..##.....",
    "....##....##....",
    ".......##.......",
    ".......##.......",
    ".......##.......",
    "................",
    "................",
    "................",
]

# Two chevrons running, one behind the other.
WANDERER_RUNE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "....##....##....",
    ".....##....##...",
    "......##....##..",
    ".....##....##...",
    "....##....##....",
    "................",
    "................",
    "................",
    "................",
]

# Driving down into the dark.
DELVER_RUNE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......##.......",
    ".......##.......",
    ".......##.......",
    "....##....##....",
    ".....##..##.....",
    "......####......",
    ".......##.......",
    "................",
    "................",
    "................",
]


# A shaft turned back on itself: it comes in from the left, meets a wall, and
# leaves the way it came. The only rune in the set that reads as two things
# happening rather than one direction.
RETURNED_RUNE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....##...##....",
    "....##....##....",
    "...#####..##....",
    "....##....##....",
    ".....##...##....",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def shade_of(x, y, cx, cy, rx, ry):
    u = (x + 0.5 - cx) / rx
    v = (y + 0.5 - cy) / ry
    d = u * u + v * v
    if d > 1.0:
        u, v = u / math.sqrt(d), v / math.sqrt(d)
        d = 1.0
    nz = math.sqrt(max(0.0, 1.0 - d))
    lx, ly, lz = LIGHT
    ln = math.sqrt(lx * lx + ly * ly + lz * lz)
    lam = max(0.0, (u * lx + v * ly + nz * lz) / ln)
    return AMBIENT + (1.0 - AMBIENT) * lam * (0.55 + 0.45 * nz)


def pick(table, s):
    return table[min(len(table) - 1, max(0, int(s * len(table))))]


def blend(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def bounds(mask, ch):
    """Centre and radii of a mask, so moving the art cannot desync its lighting."""
    rows = [y for y in range(16) if ch in mask[y]]
    cols = [x for y in rows for x in range(16) if mask[y][x] == ch]
    cy = (min(rows) + max(rows) + 1) / 2.0
    cx = (min(cols) + max(cols) + 1) / 2.0
    return cx, cy, (max(cols) - min(cols) + 1) / 2.0, (max(rows) - min(rows) + 1) / 2.0


PLATE_BOX = bounds(PLATE, "P")
RING_BOX = bounds(RING, "R")


def bake(rune, glow):
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]

    # The plate, as a disc lit from the upper left.
    for y in range(16):
        for x in range(16):
            if PLATE[y][x] == "P":
                px[y][x] = pick(STONE, shade_of(x, y, *PLATE_BOX))

    # The ring it hangs from, lit off its own small circle so the highlight
    # sits on the same side as the plate's rather than fighting it.
    for y in range(16):
        for x in range(16):
            if RING[y][x] == "R":
                px[y][x] = pick(GOLD, shade_of(x, y, *RING_BOX))

    # A halo first, so the glyph can overwrite anything it touches.
    for y in range(16):
        for x in range(16):
            if rune[y][x] == "#" or PLATE[y][x] != "P":
                continue
            near = any(
                0 <= y + dy < 16 and 0 <= x + dx < 16 and rune[y + dy][x + dx] == "#"
                for dy in (-1, 0, 1) for dx in (-1, 0, 1)
            )
            if near:
                px[y][x] = blend(px[y][x], glow[1], 0.55)

    # And the glyph itself, brightest where the plate is already lit.
    for y in range(16):
        for x in range(16):
            if rune[y][x] != "#":
                continue
            px[y][x] = pick(glow, 0.45 + 0.55 * shade_of(x, y, *PLATE_BOX))

    return px


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
write(os.path.join(OUT, "charm_of_the_leaper.png"), bake(LEAPER_RUNE, SKY))
write(os.path.join(OUT, "charm_of_the_wanderer.png"), bake(WANDERER_RUNE, LEAF))
write(os.path.join(OUT, "charm_of_the_delver.png"), bake(DELVER_RUNE, EMBER))
write(os.path.join(OUT, "charm_of_the_returned.png"), bake(RETURNED_RUNE, VIOLET))

# The status effect icon, which vanilla draws at eighteen square.
#
# Glyph only, on nothing. Vanilla's icons are bare marks -- jump boost is an
# arrow and no more -- so the medallion's plate behind ours made it the only
# badge in the HUD sitting on a coin. Drawn at its own size rather than scaled
# up from the charm's, which at sixteen pixels had to fit inside a plate and so
# came out half the size the icon has room for.
EFFECT_OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\textures\mob_effect"

DEFLECTION_ICON = [
    "..................",
    "..................",
    "..................",
    "..............##..",
    "......##......##..",
    ".....##.......##..",
    "....##........##..",
    "...##.........##..",
    "..##########..##..",
    "..##########..##..",
    "...##.........##..",
    "....##........##..",
    ".....##.......##..",
    "......##......##..",
    "..............##..",
    "..................",
    "..................",
    "..................",
]


def bake_icon():
    px = [[(0, 0, 0, 0)] * 18 for _ in range(18)]
    for y in range(18):
        for x in range(18):
            if DEFLECTION_ICON[y][x] != "#":
                continue
            # Lit from the same upper left as everything else in the mod, so the
            # badge and the charm look like they were drawn by one hand.
            px[y][x] = pick(VIOLET, 0.42 + 0.58 * shade_of(x, y, 9.0, 9.0, 9.0, 9.0))
    return px


def write_icon(path, px):
    raw = bytearray()
    for y in range(18):
        raw.append(0)
        for x in range(18):
            raw.extend(px[y][x])
    blob = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 18, 18, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))
    open(path, "wb").write(blob)
    print("wrote", os.path.basename(path))


os.makedirs(EFFECT_OUT, exist_ok=True)
write_icon(os.path.join(EFFECT_OUT, "deflection.png"), bake_icon())
