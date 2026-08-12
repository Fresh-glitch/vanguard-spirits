"""Draw the Nymph's 128x128 entity sheet.

She guards a flower forest, so she is drawn out of one. Every colour below
descends from a measurement taken off the real blocks in the client jar rather
than from taste:

    birch_log        #36342A #605E54 #D8D8CE #F0EEEB #FFFFFF
    oak_leaves       #686468 #777577 #989998 #B9BCB9   x foliage tint #59AE30
                     = #244414 #2A5016 #35681D #418023
    allium           #7B4EA0 #A65EE1 #B878ED #D2A6F6 #E8CFFE

The leaf numbers are the ones worth pausing on. Leaves ship as a near-grey
sheet and are multiplied at draw time by a tint the biome looks up in
`colormap/foliage.png`; flower forest sits at temperature 0.7, downfall 0.8,
which lands on #59AE30. Sampling `oak_leaves.png` on its own gives a washed
olive that exists nowhere in the world, and it would have looked like a
measurement.

The allium ramp is a small gift: this mod's own colour is already purple --
Fractured Memories, echo runes, vanilla's purple italic LORE -- and the flower
the biome plants most is lilac. She can belong to the biome and to the mod
without compromise.

## What the guide's rules turn into here

From `<http://rjanes.com/tutorials/introduction_to_pixel_art.php>`, as recorded
in CLAUDE.md. Three of them bite on the sampled numbers directly:

- **Adjacent shades need about 25 luminance between them.** The lit oak ramp
  runs 53, 62, 80, 99 -- gaps of 9, 18, 19, which is the guide's own example of
  shades nobody can tell apart. Held at that hue and respread below.
- **Five shades is the ceiling.** Birch alone ships eight; the ramp takes five.
- **Temperature has to have a direction.** Bark shadows go cool and green,
  as bark in leaf-shade actually does, and the highlight goes warm cream.

Two rules from the item work deliberately do *not* apply. There is no outline
convention here: an entity sheet's faces meet each other in three dimensions,
so ringing each rectangle would draw a grid over her. And the alpha bounding
box means nothing, because every rectangle is filled edge to edge.

## Where the rectangles come from

`CUBES` is transcribed from `blockbench/nymph.bbmodel`, which is the authority.
The per-face rectangles are then *derived* from the box-UV convention, and
[CONTROL] holds what Blockbench itself reported for five cubes of deliberately
unlike proportion, so the derivation is checked against the real thing on every
run rather than trusted. If the model changes, re-read both out of Blockbench; a
stale table here paints the right picture onto the wrong faces, which looks like
a modelling bug and is not one.
"""

from __future__ import annotations

import colorsys
import math
import os

from PIL import Image

SIZE = 128
OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "src", "main", "resources", "assets", "vanguard-spirits", "textures", "entity",
)

# name -> ((width, height, depth), (u, v)), straight out of Blockbench.
CUBES = {
    "waist":                 ((3, 4, 3), (32, 24)),
    "chest":                 ((5, 6, 4), (0, 14)),
    "neck_post":             ((2, 3, 2), (80, 24)),
    "skull":                 ((5, 5, 5), (80, 0)),
    "hair_top":              ((6, 1, 6), (8, 24)),
    "hair_back":             ((5, 13, 1), (0, 0)),
    "lock_right":            ((1, 9, 4), (12, 0)),
    "lock_left":             ((1, 9, 4), (22, 0)),
    "petal_b":               ((3, 1, 3), (88, 24)),
    "petal_br":              ((3, 1, 3), (100, 24)),
    "petal_bl":              ((3, 1, 3), (112, 24)),
    "petal_fr":              ((3, 1, 3), (0, 32)),
    "petal_fl":              ((3, 1, 3), (12, 32)),
    "petal_f":               ((3, 1, 3), (24, 32)),
    "bloom":                 ((3, 2, 3), (44, 24)),
    "sash":                  ((7, 5, 5), (32, 0)),
    "arm_right":             ((2, 8, 2), (36, 14)),
    "arm_left":              ((2, 8, 2), (44, 14)),
    "forearm_right":         ((2, 7, 2), (68, 14)),
    "forearm_left":          ((2, 7, 2), (84, 14)),
    "thigh_right":           ((2, 8, 2), (52, 14)),
    "thigh_left":            ((2, 8, 2), (60, 14)),
    "shin_right":            ((2, 6, 2), (100, 14)),
    "shin_left":             ((2, 6, 2), (116, 14)),
    "foot_right":            ((2, 1, 4), (56, 24)),
    "foot_left":             ((2, 1, 4), (68, 24)),

    # ---- the outer layer ----
    #
    # Seven of the boxes above, drawn again at an inflation and mapped
    # elsewhere -- exactly how a player skin carries a hat, a jacket and
    # sleeves. Most of each rectangle is left transparent; that is what
    # makes it a garment rather than a thicker limb.
    "skull_overlay":         ((5, 5, 5), (100, 0)),
    "chest_overlay":         ((5, 6, 4), (18, 14)),
    "sash_overlay":          ((7, 5, 5), (56, 0)),
    "forearm_right_overlay": ((2, 7, 2), (76, 14)),
    "forearm_left_overlay":  ((2, 7, 2), (92, 14)),
    "shin_right_overlay":    ((2, 6, 2), (108, 14)),
    "shin_left_overlay":     ((2, 6, 2), (0, 24)),
}

# What Blockbench answered for three cubes of unlike proportion -- a near-cube,
# a tall thin one, and a flat one. The positive control for [face_rects].
CONTROL = {
    "skull": {
        "north": (85, 5, 90, 10), "south": (95, 5, 100, 10),
        "east":  (80, 5, 85, 10), "west":  (90, 5, 95, 10),
        "up":    (85, 0, 90, 5),  "down":  (90, 0, 95, 5),
    },
    "lock_right": {
        "north": (16, 4, 17, 13), "south": (21, 4, 22, 13),
        "east":  (12, 4, 16, 13), "west":  (17, 4, 21, 13),
        "up":    (16, 0, 17, 4),  "down":  (17, 0, 18, 4),
    },
    # A cube wider than it is tall -- the net's top strip is taller than its
    # bottom one here, which is the case most likely to expose a transposed
    # derivation.
    "hair_top": {
        "north": (14, 30, 20, 31), "south": (26, 30, 32, 31),
        "east":  (8, 30, 14, 31),  "west":  (20, 30, 26, 31),
        "up":    (14, 24, 20, 30), "down":  (20, 24, 26, 30),
    },
    # Her foot: two wide, one tall, four deep. The most lopsided box on the
    # model, and so the one where swapping height for depth in the derivation
    # would produce the most obviously wrong rectangles.
    "foot_right": {
        "north": (60, 28, 62, 29), "south": (66, 28, 68, 29),
        "east":  (56, 28, 60, 29), "west":  (62, 28, 66, 29),
        "up":    (60, 24, 62, 28), "down":  (62, 24, 64, 28),
    },
    # One from the outer layer, which is the half most likely to drift: the
    # overlays are the same sizes as the parts beneath them, so a table that
    # copied a base offset by mistake would still look plausible.
    "chest_overlay": {
        "north": (22, 18, 27, 24), "south": (31, 18, 36, 24),
        "east":  (18, 18, 22, 24), "west":  (27, 18, 31, 24),
        "up":    (22, 14, 27, 18), "down":  (27, 14, 32, 18),
    },
}

# Which ramp each cube is drawn from.
MATERIAL = {
    "chest": "bark", "waist": "bark", "neck_post": "bark", "skull": "bark",
    "arm_right": "bark", "arm_left": "bark",
    "forearm_right": "bark", "forearm_left": "bark",
    "thigh_right": "bark", "thigh_left": "bark",
    "shin_right": "bark", "shin_left": "bark",
    "foot_right": "bark", "foot_left": "bark",
    "sash": "leaf",
    "hair_back": "petal", "lock_right": "petal", "lock_left": "petal",
    "hair_top": "petal",
    "petal_b": "petal", "petal_br": "petal", "petal_bl": "petal",
    "petal_fr": "petal", "petal_fl": "petal", "petal_f": "petal",
    "bloom": "bloom",
    "skull_overlay": "accessory", "chest_overlay": "accessory",
    "sash_overlay": "accessory",
    "forearm_right_overlay": "accessory", "forearm_left_overlay": "accessory",
    "shin_right_overlay": "accessory", "shin_left_overlay": "accessory",
}

# What each piece of the outer layer actually is: which ramp it is drawn from,
# and which shape of coverage [paint_accessory] gives it.
#
# The coverage is the whole design. An overlay that filled its rectangle would
# just be a fatter limb -- what turns it into a garment is leaving most of it
# transparent and choosing *where* the cloth falls. A hood is solid at the back
# and open at the face; a mantle covers the shoulders and stops; a wrap is two
# bands round a forearm and nothing else.
ACCESSORY = {
    "skull_overlay":         ("petal", "hood"),
    "chest_overlay":         ("leaf", "mantle"),
    "sash_overlay":          ("leaf", "fringe"),
    # On the forearms and shins rather than the whole limb, now that she has
    # elbows and knees. A wrap that spanned an arm from shoulder to wrist would
    # be a sleeve, and worse, it would cross the joint -- a band drawn over the
    # elbow tears in half the moment the elbow bends.
    "forearm_right_overlay": ("leaf", "wrap"),
    "forearm_left_overlay":  ("leaf", "wrap"),
    "shin_right_overlay":    ("leaf", "moss"),
    "shin_left_overlay":     ("leaf", "moss"),
}


# ------------------------------------------------------------------ palette


# (hue, saturation, value), darkest first.
#
# Bark holds birch's own range but turns its shadows cool and green -- which is
# what bark under a canopy actually does -- and its highlight warm and nearly
# neutral. That is the guide's warm-highlight-against-cold-shadow read carried
# by saturation and a small hue walk, the same correction the Unfinished
# Epitaph needed when a literal hue shift turned deepslate amber.
RAMPS = {
    "bark": [
        (0.34, 0.26, 0.26),
        (0.30, 0.20, 0.45),
        (0.19, 0.15, 0.66),
        (0.13, 0.14, 0.85),
        (0.11, 0.13, 0.99),
    ],
    # Oak's four lit tones, respread. Hue walks from a cold blue-green shadow
    # to a warm yellow-green highlight, which is how a leaf actually catches
    # the sun.
    "leaf": [
        (0.33, 0.70, 0.20),
        (0.31, 0.70, 0.33),
        (0.28, 0.72, 0.48),
        (0.25, 0.68, 0.65),
    ],
    # Allium, respread so the two middle steps are further apart than the 22
    # luminance the flower ships with.
    "petal": [
        (0.72, 0.56, 0.40),
        (0.74, 0.55, 0.62),
        (0.76, 0.52, 0.84),
        (0.78, 0.38, 0.93),
        (0.79, 0.15, 0.98),
    ],
}

# The bloom on her temple is the same flower one step hotter -- it is the part
# of her that is meant to catch the eye, and a shared ramp made it vanish into
# the hair it sits on.
RAMPS["bloom"] = RAMPS["petal"]

# Light through leaves. Deliberately in no ramp, so her eyes are the one thing
# on the sheet that cannot be mistaken for foliage or flower.
EYE = (0xDA, 0xF0, 0xA8)
EYE_DARK = (0x5E, 0x7A, 0x3C)


def rgb(hsv):
    r, g, b = colorsys.hsv_to_rgb(*hsv)
    return (round(r * 255), round(g * 255), round(b * 255))


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def palette(name):
    return [rgb(h) for h in RAMPS[name]]


# ------------------------------------------------------------------ checks


def check_ramp(name):
    """The guide's colour rules, as asserts rather than as intentions."""
    pal = palette(name)
    assert len(pal) <= 5, f"{name}: {len(pal)} shades, the guide caps a ramp at five"

    gaps = [lum(pal[i + 1]) - lum(pal[i]) for i in range(len(pal) - 1)]
    assert min(gaps) >= 25, (
        f"{name}: shades too close to tell apart, gaps {[round(g) for g in gaps]}"
    )

    hsv = RAMPS[name]
    # The direction, not merely the existence, of a temperature walk.
    #
    # The first draft asserted only that hue *or* saturation moved, and passed a
    # bark ramp whose highlight was the least saturated shade on it -- the exact
    # inversion the guide names, shipped under a green check. Warmth has to be
    # measured on the hue circle rather than as a signed hue difference, because
    # "warmer" is a lower hue for the greens and a higher one for the purples.
    warm = [math.cos(2 * math.pi * h) for h, _, _ in hsv]
    assert warm[-1] > warm[0] + 0.15, (
        f"{name}: highlight is not warmer than the shadow "
        f"(warmth {warm[0]:+.2f} -> {warm[-1]:+.2f})"
    )
    # A floor on saturation rather than a direction for it.
    #
    # The guide's letter is a *more* saturated highlight, and a second draft
    # asserted exactly that -- which immediately rejected the allium ramp, whose
    # top shade is pale by measurement (vanilla's own `#E8CFFE` is saturation
    # 0.19). CLAUDE.md has already settled this from the Epitaph: saturation
    # need not climb toward the highlight so long as the ramp has a temperature
    # direction, which the check above now tests properly.
    #
    # What was actually wrong with the bark ramp that prompted the assert was
    # not the direction but the magnitude -- every shade sat between 0.04 and
    # 0.18, so she rendered as poured concrete. That is the thing worth
    # asserting, and it is a floor.
    flattest = min(s for _, s, _ in hsv)
    assert flattest >= 0.10, (
        f"{name}: shade at saturation {flattest:.2f} is grey, not a colour"
    )
    return gaps


def prove_the_check_bites():
    """Run the ramp check against a ramp known to be wrong.

    The lit oak leaf tones as sampled -- 53, 62, 80, 99 -- are exactly what the
    guide says nobody can distinguish, and they are the numbers this file exists
    to correct. If [check_ramp] accepts them it is measuring nothing, and a
    check that passes everything looks identical to a check that passes.
    """
    saved = RAMPS.get("_control")
    RAMPS["_control"] = [
        colorsys.rgb_to_hsv(*[c / 255 for c in col])
        for col in ((0x24, 0x44, 0x14), (0x2A, 0x50, 0x16),
                    (0x35, 0x68, 0x1D), (0x41, 0x80, 0x23))
    ]
    try:
        check_ramp("_control")
    except AssertionError as e:
        return f"rejects the raw oak ramp: {e}"
    finally:
        if saved is None:
            del RAMPS["_control"]
    raise AssertionError("the ramp check accepts the very ramp it exists to reject")


# ------------------------------------------------------------------ geometry


def face_rects(size, off):
    """The six face rectangles of a box-UV cube, as (x0, y0, x1, y1).

    The layout is the one verified in CLAUDE.md and re-checked against
    [CONTROL] on every run: the top strip is `d` tall and holds UP then DOWN,
    and the row beneath it is `h` tall and holds EAST, NORTH, WEST, SOUTH left
    to right.
    """
    w, h, d = (int(n) for n in size)
    u, v = off
    return {
        "up":    (u + d,             v,     u + d + w,         v + d),
        "down":  (u + d + w,         v,     u + d + w + w,     v + d),
        "east":  (u,                 v + d, u + d,             v + d + h),
        "north": (u + d,             v + d, u + d + w,         v + d + h),
        "west":  (u + d + w,         v + d, u + d + w + d,     v + d + h),
        "south": (u + d + w + d,     v + d, u + d + w + d + w, v + d + h),
    }


def check_layout():
    """Nothing overlaps, nothing runs off the sheet, and the net is the net."""
    for name, faces in CONTROL.items():
        derived = face_rects(*CUBES[name])
        for f, want in faces.items():
            got = derived[f]
            assert got == want, (
                f"{name}.{f}: derived {got}, Blockbench says {want} -- the box-UV "
                f"convention in face_rects does not match the model"
            )

    seen = {}
    for name, (size, off) in CUBES.items():
        for f, (x0, y0, x1, y1) in face_rects(size, off).items():
            assert 0 <= x0 < x1 <= SIZE and 0 <= y0 < y1 <= SIZE, (
                f"{name}.{f} at {(x0, y0, x1, y1)} falls off a {SIZE}x{SIZE} sheet"
            )
            for x in range(x0, x1):
                for y in range(y0, y1):
                    other = seen.get((x, y))
                    assert other is None, (
                        f"{name}.{f} overlaps {other} at {(x, y)}"
                    )
                    seen[(x, y)] = f"{name}.{f}"
    return len(seen)


# ---------------------------------------------------------------- lighting


# How much light each face catches. Up is the sky, north is her front, down is
# the ground. A *direction*, not a distance from an edge -- which is what keeps
# this off the pillow shading the guide calls a crime.
FACE_LIGHT = {
    "up": 1.00, "north": 0.78, "east": 0.60, "west": 0.60,
    "south": 0.42, "down": 0.20,
}


def noise(*key):
    """A small deterministic hash, so the sheet is reproducible.

    Blockbench and the game both cache textures aggressively, and a sheet that
    differed run to run would make "did that change take?" unanswerable.
    """
    n = 0x811C9DC5
    for k in key:
        for ch in str(k):
            n = ((n ^ ord(ch)) * 0x01000193) & 0xFFFFFFFF
    return n


def shade(pal, t):
    """Pick a ramp entry from a 0..1 light level."""
    return pal[max(0, min(len(pal) - 1, int(t * len(pal))))]


# ---------------------------------------------------------------- painting


def paint_bark(px, rect, face, pal, part):
    """Birch: pale, and marked with the horizontal dashes birch is known for.

    The dashes are the whole point. A flat pale limb reads as bone or as stone,
    and CLAUDE.md's Fractured Memory lesson is that a small sprite lives or dies
    on contrast *within* itself rather than on its overall value. Two dashes on
    a forearm are what say birch.
    """
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    base = FACE_LIGHT[face]

    level = []
    for j in range(h):
        # Lighter towards the top of the part: one light source, from above.
        down = j / max(1, h - 1)
        t = base * (1.0 - 0.22 * down)
        idx = max(0, min(len(pal) - 1, int(t * len(pal))))
        level.append(idx)
        for i in range(w):
            px[x0 + i, y0 + j] = pal[idx] + (255,)

    if face in ("up", "down") or w < 2:
        return

    # Her face is bark too, but a band across it is a blindfold. Everything
    # else about the head keeps its markings.
    if part == "skull" and face == "north":
        return

    # Birch's bands, which are the whole reason she does not read as bone.
    #
    # A first pass capped a dash at `min(3, w - 1)` pixels, which on a two-wide
    # arm is one -- so every limb came out flecked with single dark specks that
    # read as dirt rather than as bark. A band has to cross most of the limb to
    # be a band, so on anything narrow it spans the lot.
    #
    # The tone is taken two steps below whatever that row is lit to, rather than
    # from a fixed end of the ramp: a fixed dark on her shaded side would have
    # been the same colour as the ground under it and vanished.
    for j in range(1, h - 1):
        r = noise(part, face, j)
        # Sparse. At one row in four a fifteen-unit leg carries three bands and
        # reads as bandaged; at one in seven it reads as bark.
        if r % (7 if w <= 3 else 6):
            continue
        if w <= 3:
            run, start = w, 0
        else:
            run = 2 + r % max(1, w - 2)
            start = (r >> 7) % max(1, w - run + 1)
        dark = pal[max(0, level[j] - 2)]
        for i in range(start, start + run):
            px[x0 + i, y0 + j] = dark + (255,)


def paint_leaf(px, rect, face, pal, part):
    """The sash: layered leaves, with a ragged hem and a flower tucked in."""
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    base = FACE_LIGHT[face]

    for j in range(h):
        down = j / max(1, h - 1)
        # Darkens sharply towards the hem, which is what makes it hang rather
        # than sit there as a band of colour.
        t = base * (1.0 - 0.45 * down)
        for i in range(w):
            px[x0 + i, y0 + j] = shade(pal, t) + (255,)

    if face in ("up", "down"):
        return

    # A ragged bottom edge: alternate cells on the last row drop to the
    # darkest tone, so the silhouette of the hem is broken up.
    if h >= 3:
        for i in range(w):
            if noise(part, face, "hem", i) % 3:
                continue
            px[x0 + i, y1 - 1] = pal[0] + (255,)

    # Leaf midribs -- one short vertical tick per few columns.
    for i in range(w):
        r = noise(part, face, "vein", i)
        if r % 4:
            continue
        top = 1 + r % max(1, h - 2)
        for j in range(top, min(h - 1, top + 2)):
            px[x0 + i, y0 + j] = pal[max(0, len(pal) - 3)] + (255,)

    # One flower caught in the sash, on her front only, and off to one side.
    # Centred it came out as a symmetrical cross that read as a belt buckle.
    if face == "north" and w >= 5 and h >= 4:
        petal = palette("petal")
        cx, cy = x0 + w // 2 - 1, y0 + h // 2
        px[cx, cy] = petal[4] + (255,)
        px[cx - 1, cy] = petal[3] + (255,)
        px[cx, cy - 1] = petal[3] + (255,)


def paint_petal(px, rect, face, pal, part):
    """Hair and blossom: vertical strands, lit from above.

    Hair is drawn as columns rather than rows. It is the one part of her with
    real length, and banding it horizontally made it read as a striped robe.
    """
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    base = FACE_LIGHT[face]
    blossom = part.startswith("petal") or part == "bloom"

    for i in range(w):
        # Each strand sits one step off its neighbour, and the offset is fixed
        # per column so the strands run the whole length instead of shimmering.
        strand = (noise(part, face, "strand", i) % 3) - 1
        for j in range(h):
            down = j / max(1, h - 1)
            fall = 0.30 if blossom else 0.42
            t = base * (1.0 - fall * down) + strand * 0.07
            px[x0 + i, y0 + j] = shade(pal, t) + (255,)

    # A blossom is brightest at its heart rather than at its rim, which is the
    # opposite of how the hair is lit and is what separates the two at a glance.
    if blossom and w >= 3 and h >= 2 and face not in ("east", "west"):
        for i in range(1, w - 1):
            px[x0 + i, y0] = pal[-1] + (255,)


def paint_face(px, rect):
    """Two eyes and nothing else.

    No mouth. A mouth at five pixels across is a smudge or a grimace, and the
    thing that makes her read as *watching* rather than as a doll is the pair
    of lit marks -- so all of the budget goes there.
    """
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    if w < 5 or h < 5:
        return
    # Dark above bright, not the other way round. The first pass put the lit
    # pixel on top and the shadow under it, which at this size reads as two
    # marks of equal weight rather than as an eye under a lid -- four green
    # specks across a forehead instead of a pair of eyes.
    row = y0 + h // 2
    for dx in (1, w - 2):
        px[x0 + dx, row - 1] = EYE_DARK + (255,)
        px[x0 + dx, row] = EYE + (255,)


def covers(kind, face, i, j, w, h, part):
    """Whether the outer layer has anything at this texel.

    Everything that answers False is left fully transparent, and the render type
    an entity model gets by default is a *cutout* -- alpha is a binary keep or
    discard, so there is no blending to worry about and no draw order to get
    wrong.
    """
    side = face in ("north", "south", "east", "west")

    if kind == "hood":
        # Solid over the crown, down the back and along both temples; open at
        # the face except for a two-row fringe. That gap is the accessory: with
        # the front closed she is wearing a bag over her head.
        if face == "up":
            return True
        if face == "down":
            return False
        if face == "north":
            return j < 2
        return True

    if kind == "mantle":
        if face == "up":
            return True
        if face == "down":
            return False
        if not side:
            return False
        # Shoulders only, with a torn lower hem rather than a ruled line -- a
        # straight edge all the way round reads as a tabard.
        #
        # Two rows, not half the chest. At half she was wearing a barrel: the
        # mantle covered every birch band on her torso, which is the detail that
        # makes her read as birch at all. An accessory that hides the body it is
        # worn over has stopped being an accessory.
        edge = 2 + (1 if noise(part, face, "hem", i) % 3 == 0 else 0)
        return j < edge

    if kind == "fringe":
        if face == "down":
            return True
        if face == "up" or not side:
            return False
        return j >= h - 2 - (noise(part, face, "frill", i) % 2)

    if kind == "wrap":
        # Two bands round the forearm. Placed by fraction rather than by row so
        # they stay put if the limb is ever re-proportioned.
        if not side:
            return False
        for at in (0.42, 0.66):
            band = int(h * at)
            if band <= j <= band + 1:
                return True
        return False

    if kind == "moss":
        if face == "down":
            return True
        if face == "up" or not side:
            return False
        # Moss round the ankles. Two rows of the six-tall shin -- it used to be
        # three of a fifteen-tall whole leg, and before that five, which came
        # out as a pair of wellington boots. The idea is something growing on
        # her from standing in one place.
        return j >= h - 2 + (noise(part, face, "moss", i) % 2)

    return False


def paint_accessory(px, rect, face, pal, part):
    """The outer layer: a garment, painted only where it falls."""
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    _, kind = ACCESSORY[part]
    base = FACE_LIGHT[face]

    for j in range(h):
        for i in range(w):
            if not covers(kind, face, i, j, w, h, part):
                continue
            down = j / max(1, h - 1)
            t = base * (1.0 - 0.30 * down)
            # A step darker than the body beneath it, so the layer separates
            # even where it lies flat against what it covers.
            idx = max(0, min(len(pal) - 1, int(t * len(pal)) - 1))
            px[x0 + i, y0 + j] = pal[idx] + (255,)


PAINTERS = {
    "bark": paint_bark, "leaf": paint_leaf, "petal": paint_petal,
    "bloom": paint_petal, "accessory": paint_accessory,
}


# ------------------------------------------------------------------ output


def build():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()

    for part, (size, off) in CUBES.items():
        material = MATERIAL[part]
        # The outer layer names its ramp in [ACCESSORY] rather than in
        # [MATERIAL], because "accessory" says how it is drawn and not what it
        # is made of -- a leaf mantle and an allium hood share a painter.
        ramp = ACCESSORY[part][0] if material == "accessory" else material
        pal = palette(ramp)
        for face, rect in face_rects(size, off).items():
            PAINTERS[material](px, rect, face, pal, part)

    # Her front, last, so nothing overwrites it.
    paint_face(px, face_rects(*CUBES["skull"])["north"])
    return im


def check_every_shade_appears(im):
    """A shade that exists only in the palette is not in the sprite.

    Both of the Epitaph's ramps failed this, in opposite directions -- one
    defined a highlight and never drew it, the next had its darkest tone painted
    over everywhere by a later pass. Neither showed up as anything but a sprite
    that read flat.
    """
    used = {p[:3] for p in im.getdata() if p[3] > 0}
    missing = []
    for name in ("bark", "leaf", "petal"):
        for c in palette(name):
            if c not in used:
                missing.append(f"{name} {c[0]:02X}{c[1]:02X}{c[2]:02X}")
    assert not missing, "declared but never drawn: " + ", ".join(missing)
    return len(used)


def check_not_pillow(im):
    """Pillow shading rings a shape; a light source does not.

    Tested per face rectangle: under pillow shading a pixel and its reflection
    through the rectangle's centre carry the same tone, because both sit the
    same distance from the edge.
    """
    px = im.load()
    same = tested = 0
    for part, (size, off) in CUBES.items():
        for face, (x0, y0, x1, y1) in face_rects(size, off).items():
            w, h = x1 - x0, y1 - y0
            if w < 3 or h < 3:
                continue
            for i in range(w):
                for j in range(h):
                    mi, mj = w - 1 - i, h - 1 - j
                    a = px[x0 + i, y0 + j]
                    b = px[x0 + mi, y0 + mj]
                    # Two empty texels are not a shading decision.
                    #
                    # Most of the outer layer is transparent by design, so
                    # counting those pairs would have driven this ratio towards
                    # one and failed a check that exists to catch something else
                    # entirely -- an instrument reporting a fault it cannot see.
                    if a[3] == 0 and b[3] == 0:
                        continue
                    tested += 1
                    if a == b:
                        same += 1
    share = same / max(1, tested)
    assert share < 0.55, f"reads as pillow shading: {share:.0%} of mirrored pairs match"
    return share


def main():
    print("control --", prove_the_check_bites())
    for name in ("bark", "leaf", "petal"):
        gaps = check_ramp(name)
        pal = palette(name)
        print(f"\nRAMP {name}, shadow to highlight   gaps {[round(g) for g in gaps]}")
        for c, hsv in zip(pal, RAMPS[name]):
            print(f"   #{c[0]:02X}{c[1]:02X}{c[2]:02X}   hue {hsv[0]:.3f}  "
                  f"sat {hsv[1]:.2f}  val {hsv[2]:.2f}  lum {lum(c):5.1f}")

    covered = check_layout()
    print(f"\nlayout: {len(CUBES)} cubes, {covered} of {SIZE * SIZE} texels used, "
          f"no overlaps, net matches Blockbench on {len(CONTROL)} control cubes")

    im = build()
    shades = check_every_shade_appears(im)
    share = check_not_pillow(im)
    print(f"sheet: {shades} distinct colours, mirror match {share:.0%}, not pillow shaded")

    os.makedirs(OUT, exist_ok=True)
    path = os.path.abspath(os.path.join(OUT, "nymph.png"))
    im.save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    main()
