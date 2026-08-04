"""Fractured Memory: a shard of ruin-stone whose fracture is also the glyph.

Four drafts got here, and the useful failure was the third. It had a crack AND a
severed rune, both bone-white, both adjacent -- and at sixteen pixels the eye
merged them into one gold squiggle. Neither idea survived. The charms work
because each carries exactly ONE motif: a rune on a plate. There is no room here
for two.

So the two ideas are made one. The break branches like a break and reaches the
stone's edge like a break, but every segment is straight and the fork is
deliberate, so it reads as written. The memory is damaged and the damage is what
you read.

Technique is hand-placement over procedure, learned the same way: a distance
field glow with a three pixel falloff swallowed forty per cent of the sprite,
and stacking gradients and halos gave forty-two colours over eighty-three pixels
where the shipped charms carry seventeen over a hundred and eighteen.

Palette is measured off the shipped art, not invented -- the charms' deepslate
ramp for the stone, the echo runes' bone-white over amber for the light.
"""

import os
import struct
import zlib

OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\textures\item"
SIZE = 16

# The ring stays near-black; the body does not. Drawn at the charms' darkest
# plate values the shard vanished into the inventory slot at actual size and all
# that survived was the bright fork -- the item read as a glowing letter Y. The
# charm plates that survive small are MID value masses (#44305E, the golds), and
# real deepslate is about #4C4C4F anyway, so this is both more legible and more
# accurate than what it replaces. Caught only by rendering at 1:1; at 40x the
# dark version looked fine.
OUTLINE = (0x15, 0x17, 0x1C)
STONE_DEEP = (0x33, 0x36, 0x3F)
STONE_MID = (0x41, 0x46, 0x53)
STONE_HIGH = (0x4E, 0x54, 0x62)
STONE_LIT = (0x64, 0x6B, 0x7C)
GLOW_DEEP = (0xB1, 0x7D, 0x2A)

# --- what the light in the break does over time ------------------------------
# The four stops are the four charms' own rune colours, read off the shipped
# item art rather than picked by eye: Delver gold, Leaper blue, Wanderer green,
# Returned violet. The shard is the raw material all four are made from, so it
# holds none of those colours and drifts through all of them -- it has not
# decided yet what it is going to become.
#
# Each stop is the (core, lip) pair for one charm, and the four are visited in
# order before returning to the first.
#
# Rotating the hue between them was the obvious approach and was wrong. Gold and
# blue sit almost opposite on the wheel, so every possible arc between them runs
# through either green or magenta -- and green is one of the four states. The
# first version showed green three separate times a cycle and finished the
# violet-to-gold leg through pink, salmon and orange, none of which any charm
# uses. It read as a rainbow that happened to pass the right colours rather than
# as four colours taking turns.
#
# So: interpolate straight between the stops, and HOLD each one. See [PLATEAU].
#
# ** These four colours, STEPS, FRAMETIME and the hold figures are duplicated in
# src/main/kotlin/io/github/freshglitch/vanguardspirits/item/MemoryHue.kt, which
# colours the item's NAME to match the frame showing here. Change either side and
# you must change the other, or the word and the picture drift apart. Nothing
# checks it -- but this script prints its cycle length on every run, and
# MemoryHue states the same figure, so the two are a glance apart. **
GLOW_STOPS = [
    ((0xFD, 0xF0, 0xBD), (0xEC, 0xCD, 0x76)),   # Delver, gold
    ((0xDC, 0xF7, 0xFF), (0x9F, 0xE4, 0xF2)),   # Leaper, blue
    ((0xD3, 0xF6, 0xCF), (0x5F, 0xBB, 0x63)),   # Wanderer, green
    ((0xEC, 0xDC, 0xFF), (0xC3, 0xA3, 0xEC)),   # Returned, violet
    ((0xFD, 0xF0, 0xBD), (0xEC, 0xCD, 0x76)),   # round to gold again
]

# [PLATEAU] Where in each leg the change actually happens. Below the first
# figure the shard sits on its colour; above the second it sits on the next one;
# in between it crosses over on a smoothstep. Holding for most of the leg is
# what makes the four charm colours read as *states* rather than as points a
# sweep passes through, and it keeps the blended midpoint -- which is a paler,
# less saturated colour than either end -- on screen briefly enough to read as
# the shard changing its mind rather than as a fifth colour.
HOLD_UNTIL = 0.30
HOLD_FROM = 0.72

# Frames per leg, and ticks each is held. Eight and fifteen give a 480 tick
# cycle: twenty-four seconds, so about four and a half seconds sat on each charm
# colour and a second and a half crossing between them. Slow enough that you
# only notice if you stop and look, which is the point.
STEPS = 8
FRAMETIME = 15

# Silhouette by row span, inclusive. Broad across the shoulders, tapering to a
# point at the foot, and deliberately lopsided -- a symmetric outline reads as a
# cut gem, which is the one thing a broken piece of rock must not look like.
SPANS = {
    2: (6, 10),
    3: (4, 11),
    4: (3, 12),
    5: (2, 13),
    6: (2, 13),
    7: (2, 12),
    8: (2, 12),
    9: (2, 11),
    10: (3, 11),
    11: (3, 10),
    12: (4, 9),
    13: (5, 7),
}

# The break. A stem up from the foot, forking into two arms -- the algiz shape,
# which is both what a real crack does when it meets harder rock and a form the
# eye files under "writing". It leaves through the bottom point: a fracture that
# stops in mid-face is a scratch.
#
# The arms run as clean diagonals of unequal length. Kinking them was tried and
# reverted: a horizontal step in a diagonal stroke puts a foot on the end of it
# at this scale, and the fork stopped reading as a break and started reading as
# a mechanical symbol. Asymmetry comes from the arms being different lengths and
# from the hairline off the stem, which is what real cracks throw off -- not
# from bending the strokes.
CRACK = [
    (6, 13), (7, 12), (7, 11), (7, 10), (7, 9), (7, 8),   # stem, up from the foot
    (8, 11),                                               # hairline off the stem
    (6, 7), (5, 6), (4, 5),                                # arm, up-left, short
    (8, 7), (9, 6), (10, 5), (11, 4),                      # arm, up-right, long
]

# Bitten out of the outline after the fact, so the edge is not one smooth
# polygon. Stone that broke does not break evenly.
CHIPS = [(13, 6), (11, 11), (3, 4)]


def mask():
    m = {(x, y) for y, (a, b) in SPANS.items() for x in range(a, b + 1)}
    return m - set(CHIPS)


def crossover(t):
    """Position through a leg, flattened to a hold-cross-hold curve."""
    if t <= HOLD_UNTIL:
        return 0.0
    if t >= HOLD_FROM:
        return 1.0
    u = (t - HOLD_UNTIL) / (HOLD_FROM - HOLD_UNTIL)
    return u * u * (3.0 - 2.0 * u)          # smoothstep, so neither end jerks


def blend(a, b, u):
    return tuple(max(0, min(255, round(a[i] + (b[i] - a[i]) * u))) for i in range(3))


def glow_frames():
    """(core, lip) for every frame of one full turn round the four charms."""
    out = []
    for leg in range(len(GLOW_STOPS) - 1):
        (core_a, lip_a) = GLOW_STOPS[leg]
        (core_b, lip_b) = GLOW_STOPS[leg + 1]
        for step in range(STEPS):
            u = crossover(step / STEPS)
            out.append((blend(core_a, core_b, u), blend(lip_a, lip_b, u)))
    return out


def build(core, lip):
    m = mask()
    crack = set(CRACK) & m
    px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]

    for (x, y) in m:
        near = lambda dx, dy: (x + dx, y + dy) in m
        border = not (near(1, 0) and near(-1, 0) and near(0, 1) and near(0, -1))

        if border:
            # Unbroken near-black ring, no exceptions. This is what lets the
            # charms hold a slot at either background grey, and the first attempt
            # at a lit edge replaced part of it -- which turned the highlight
            # into a flat grey band reading as a separate plate behind the stone.
            col = OUTLINE
        else:
            # The key light sits INSIDE the ring: the first interior course on
            # the up-and-left facing side, over the upper-left third only. Same
            # arrangement the charm plates use.
            rim = not (near(-1, -1) and near(0, -1) and near(-1, 0))
            reach = x + y
            if rim and reach < 12:
                col = STONE_LIT
            else:
                col = STONE_HIGH if reach < 13 else STONE_MID if reach < 19 else STONE_DEEP

        px[y][x] = col + (255,)

    # The break itself, then a lip down ONE consistent side. Lighting both the
    # right and the lower neighbour fills in the staircase of a diagonal stroke
    # from alternating sides, and the arms come out as a row of beads instead of
    # a line. One side only, always the right, and the diagonals stay strokes.
    for (x, y) in crack:
        px[y][x] = core + (255,)
    for (x, y) in crack:
        n = (x + 1, y)
        if n in m and n not in crack:
            px[n[1]][n[0]] = lip + (255,)

    return px


def write_sheet(path, frames):
    """One 16 wide strip, frames stacked downward -- vanilla's animation layout."""
    height = SIZE * len(frames)
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *frame[y][x]) for x in range(SIZE))
        for frame in frames
        for y in range(SIZE)
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n"
                 + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, height, 8, 6, 0, 0, 0))
                 + chunk(b"IDAT", zlib.compress(raw, 9))
                 + chunk(b"IEND", b""))

    first = frames[0]
    cols = [x for x in range(SIZE) if any(first[y][x][3] for y in range(SIZE))]
    rows = [y for y in range(SIZE) if any(first[y][x][3] for x in range(SIZE))]
    left, right, top, bot = cols[0], SIZE - 1 - cols[-1], rows[0], SIZE - 1 - rows[-1]
    opaque = [p for r in first for p in r if p[3]]
    lit = sum(1 for p in opaque if max(p[:3]) > 0x90)

    # Every frame must have the identical silhouette. Only the light in the
    # break moves; if a frame ever differed in shape the item would appear to
    # wobble, and at twenty ticks a frame that is very hard to spot by eye.
    shape = {(x, y) for y in range(SIZE) for x in range(SIZE) if first[y][x][3]}
    for i, f in enumerate(frames):
        other = {(x, y) for y in range(SIZE) for x in range(SIZE) if f[y][x][3]}
        assert other == shape, f"frame {i} has a different silhouette"

    print(f"wrote {path}")
    print(f"  {SIZE}x{height}, {len(frames)} frames, "
          f"{FRAMETIME * len(frames)} ticks per cycle "
          f"({FRAMETIME * len(frames) / 20:.0f}s)")
    print(f"  margins L{left} R{right} T{top} B{bot}"
          f"  {'balanced' if left == right and top == bot else '*** UNEVEN ***'}")
    print(f"  {len({p[:3] for p in opaque})} colours over {len(opaque)} px, "
          f"{100 * lit // len(opaque)}% lit  (frame 0)")
    print(f"  silhouette identical across all {len(frames)} frames")


os.makedirs(OUT, exist_ok=True)
frames = [build(core, lip) for core, lip in glow_frames()]
write_sheet(os.path.join(OUT, "fractured_memory.png"), frames)

with open(os.path.join(OUT, "fractured_memory.png.mcmeta"), "w", encoding="utf-8") as fh:
    fh.write('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": true\n  }\n}\n'
             % FRAMETIME)
print(f"wrote {os.path.join(OUT, 'fractured_memory.png.mcmeta')}")
