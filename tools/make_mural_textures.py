"""The mural block faces and the reading panel.

Two surfaces, and they answer to different masters -- which is the whole reason
this file is careful about palette.

**The block face** is a wall block. It generates set into deepslate brick,
deepslate tile and polished deepslate, and if its stone is a different grey the
mural reads as a patch somebody stuck on rather than a slab that was always
part of the wall. So the ground here is built on vanilla's own deepslate brick
ramp, sampled straight out of the client jar:

    #242424 #2D2D2D #383737 #414141 #4B4C4F #585858 #6E6E6E

That is the same rule the Mourner's Feather cost us a screenshot to learn --
anything that has to sit beside another texture is drawn in *that* texture's
palette, not in whatever looks good on its own. Vanilla's deepslate is very
nearly neutral; the mod's own stone (see `make_logo.py`) is markedly blue, and
using it here put a cold violet tile in a grey wall.

**The reading panel** is ours. It is a screen, nothing of Mojang's sits next to
it, and it should look like the mod -- so it is laid in the logo's blue-grey
masonry, the same hand-laid courses with the same chips and cracks. The two
surfaces are never on screen together, so the hue difference costs nothing and
buys the panel the mod's identity.

## The glyphs

Eight marks, one per passage, in the family the six `echo_rune_*` particles
established: single-pixel strokes, hard angles, a strong vertical axis, and the
ward diamond that the logo also carries.

They are authored as **line segments in pixel coordinates**, not as ASCII maps.
Sixteen-by-sixteen ASCII would mean hand-maintaining the three-tone halo around
every stroke -- 8 glyphs x 3 rings of hand-placed pixels -- and a single wrong
character is invisible in the source and obvious in game. Segments give hand
control over the only thing that actually needs it (where the strokes go) and
let the halo be derived, so all eight are lit identically by construction.

Carving is done by dilation outward from the stroke:

    core        -> bone      the incised line, lit from inside
    ring 1      -> amber
    ring 2      -> ember over stone
    ring 3      -> stone darkened      the chisel shadow

The shadow ring is what makes the glyph sit *in* the stone rather than painted
on it, and it is the first thing to check if a face looks like a sticker.
"""

import os

import numpy as np
from PIL import Image

ASSETS = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits"
BLOCK_DIR = os.path.join(ASSETS, "textures", "block")
GUI_DIR = os.path.join(ASSETS, "textures", "gui")
PREVIEW = r"C:\Users\Fresh\AppData\Local\Temp\claude\P--ClaudeMods-vanguard-spirits-26-2\1a2ca9f4-ad46-405c-bd5f-83408b9c82cb\scratchpad"

# ---------------------------------------------------------------- palettes

# Vanilla deepslate_bricks.png, every colour it contains, darkest first.
# Sampled from the client jar rather than eyeballed -- see the module docstring.
VANILLA_DEEPSLATE = [
    (0x24, 0x24, 0x24),
    (0x2D, 0x2D, 0x2D),
    (0x38, 0x37, 0x37),
    (0x41, 0x41, 0x41),
    (0x4B, 0x4C, 0x4F),
    (0x58, 0x58, 0x58),
    (0x6E, 0x6E, 0x6E),
]

# The mod's own masonry, from make_logo.py. Panel only.
MOD_STONE = [
    (0x1A, 0x1C, 0x22),
    (0x22, 0x25, 0x2D),
    (0x2B, 0x2F, 0x39),
    (0x35, 0x3A, 0x46),
    (0x41, 0x47, 0x55),
    (0x4E, 0x55, 0x66),
    (0x5C, 0x64, 0x78),
]
MOD_MORTAR = (0x0E, 0x10, 0x16)

# The echo runes' three colours, unchanged since the particles.
EMBER = np.array((0xB1, 0x7D, 0x2A), float)
AMBER = np.array((0xD6, 0xA2, 0x44), float)
BONE = np.array((0xFF, 0xF8, 0xE0), float)

GROOVE_DARKEN = 0.42        # how far the chisel shadow drops the stone
EMBER_OVER_STONE = 0.62     # the lit ring is a blend, not a flat fill

# How lit the carving is at each step of MuralBlockEntity's GLOW property.
#
# The block wakes as a player approaches, and the light it emits ramps with it.
# Emitting light without the carving itself brightening reads as a bug -- the
# room gets lighter and the thing lighting it does not -- so every face is drawn
# four times and the blockstate picks the one matching the current glow.
#
# Step 0 is not quite zero. At a flat zero the rune is only its chisel shadow,
# which in an unlit crypt is invisible rather than dormant; a tenth of the way
# up leaves it legible as a carving that is not doing anything yet.
GLOW_RAMP = [0.10, 0.42, 0.72, 1.00]

# ------------------------------------------------------------------ glyphs
#
# Segments are (x0, y0) -> (x1, y1) in 16x16 pixel space, y down. Everything
# is kept inside x 3..12 / y 2..13 so a stone margin survives on all four
# sides -- a glyph touching the edge reads as a texture that got cropped.


def glyphs():
    """The eight marks, in passage order.

    Two rules learned by rendering the first draft and looking at the strip.

    **The ward diamond is used twice, not four times.** III and V are the same
    diamond cracked and then struck out, because those passages are the memory
    found and the memory emptied, and a player who reads both should feel the
    rhyme. Giving VI and VII the same outer form as well meant four glyphs with
    one silhouette, and at sixteen pixels the silhouette is nearly all a player
    gets -- they were four amber lozenges. VI and VII are now a barred doorway
    and a shrine, which differ before any interior detail is read.

    **Strokes stay three or more pixels apart.** Each one occupies three pixels
    once the lit ring is on it, so anything closer welds shut.
    """
    ward = [
        ((8, 2), (13, 7)),
        ((13, 7), (8, 12)),
        ((8, 12), (3, 7)),
        ((3, 7), (8, 2)),
    ]

    return [
        # I -- The Watch Set Down. A standard planted: stem, swept arms, and a
        # bar at the foot holding it into the ground.
        [((8, 3), (8, 11)), ((8, 7), (5, 4)), ((8, 7), (11, 4)), ((5, 12), (11, 12))],

        # II -- Why the Graves Lie Above. Two markers on a ground line and the
        # shaft dropping through it: the structure of the whole ruin, in one
        # mark, on the wall of the room the stair starts in.
        #
        # Headstones rather than mounds. Mounds were drawn first and welded
        # into the ground line -- a two-pixel-tall peak is three pixels once
        # its ring is on, so there was nothing left of the gap that made it a
        # peak, and the glyph came out as a plain crossbar.
        [
            ((3, 7), (13, 7)),
            ((5, 4), (5, 7)),
            ((11, 4), (11, 7)),
            ((8, 8), (8, 13)),
        ],

        # III -- What the Stone Keeps. The ward diamond, cracked across: broken
        # but all still there.
        ward + [((6, 5), (10, 9))],

        # IV -- The First Binding. Two stems lashed together.
        [((4, 3), (4, 12)), ((11, 3), (11, 12)), ((4, 6), (11, 6)), ((4, 9), (11, 9))],

        # V -- What We Took. The same diamond, struck through both ways.
        ward + [((6, 5), (10, 9)), ((10, 5), (6, 9))],

        # VI -- The Sealing. A doorway with a bar laid across it, the bar
        # running past both posts so it reads as something added rather than
        # part of the frame.
        [
            ((4, 4), (4, 12)), ((11, 4), (11, 12)), ((4, 4), (11, 4)),
            ((2, 8), (13, 8)),
        ],

        # VII -- The One Who Stayed. A figure with its arms up, standing in a
        # shrine. She is the only person in the eight who gets a building.
        [
            ((3, 5), (8, 2)), ((8, 2), (13, 5)),
            ((3, 5), (3, 12)), ((13, 5), (13, 12)),
            ((8, 7), (8, 12)), ((8, 9), (6, 7)), ((8, 9), (10, 7)),
        ],

        # VIII -- The Last Hand. Five strokes off one base, meeting at the
        # palm the way fingers actually do.
        [
            ((4, 12), (12, 12)),
            ((4, 12), (2, 8)), ((6, 12), (5, 6)), ((8, 12), (8, 5)),
            ((10, 12), (11, 6)), ((12, 12), (14, 8)),
        ],
    ]


def raster(segments, w):
    """Bresenham every segment onto a boolean grid."""
    grid = np.zeros((w, w), bool)

    for (x0, y0), (x1, y1) in segments:
        dx, dy = abs(x1 - x0), abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx - dy
        x, y = x0, y0
        while True:
            if 0 <= x < w and 0 <= y < w:
                grid[y, x] = True
            if x == x1 and y == y1:
                break
            e2 = 2 * err
            if e2 > -dy:
                err -= dy
                x += sx
            if e2 < dx:
                err += dx
                y += sy

    return grid


def grow(mask):
    """One step of 4-connected dilation."""
    out = mask.copy()
    out[1:, :] |= mask[:-1, :]
    out[:-1, :] |= mask[1:, :]
    out[:, 1:] |= mask[:, :-1]
    out[:, :-1] |= mask[:, 1:]
    return out


def carve(ground, core, glow):
    """Cuts a groove into `ground` along `core`, lit to step `glow`.

    Three pixels of stroke, total, and no more. The first draft dilated three
    rings outward from every line -- core, amber, ember, shadow -- which is
    five pixels per stroke. In a twelve-pixel field with strokes three or four
    apart that is not a glyph, it is a solid amber lozenge, and all four marks
    built on the ward diamond came out identical. At this size the halo is not
    decoration, it is most of the ink.

    So: one bright core, one blended ring, and the shadow taken as the **outer
    shell of the whole glyph** rather than around each stroke separately.
    Taking it per-stroke fills the gaps between close strokes with black and
    welds them shut again -- the shell leaves interior gaps as clean stone,
    which is exactly where the shape lives.
    """
    out = ground.copy()

    lit = grow(core)
    shell = grow(lit) & ~lit

    # The chisel shadow does not brighten. It is the shape of the cut, not
    # anything the carving is doing, so it stays put at every glow step -- which
    # is also what keeps the rune reading as *carved* rather than as a decal
    # that fades in.
    out[shell] = out[shell] * GROOVE_DARKEN

    amount = GLOW_RAMP[glow]

    # Each lit band is drawn between what it looks like dead and what it looks
    # like fully woken, rather than by scaling the woken colour toward black.
    # Scaling would drag the whole ramp toward a flat grey; interpolating from
    # the unlit *cut* keeps the dark end looking like stone in shadow.
    ring = lit & ~core
    ring_woken = out[ring] * (1.0 - EMBER_OVER_STONE) + EMBER * EMBER_OVER_STONE
    ring_dead = out[ring] * GROOVE_DARKEN
    out[ring] = ring_dead * (1.0 - amount) + ring_woken * amount

    core_dead = ground[core] * (GROOVE_DARKEN * 0.85)
    out[core] = core_dead * (1.0 - amount) + BONE * amount

    return np.clip(out, 0, 255)


# ------------------------------------------------------------------- stone


def vanilla_ground(w=16, seed=26_2001):
    """A deepslate slab in vanilla's own greys.

    Deliberately *not* a copy of vanilla's brick pattern: this is a single
    dressed slab, so it has no courses at all, just mottling and a couple of
    chips. That also keeps the glyph the only structure on the face -- brick
    joints running behind a rune fight it for the eye at sixteen pixels.
    """
    rng = np.random.default_rng(seed)
    palette = np.array(VANILLA_DEEPSLATE, float)

    # Weighted toward the middle of the ramp, which is where deepslate brick
    # spends most of its pixels.
    weights = np.array([0.10, 0.14, 0.18, 0.18, 0.14, 0.18, 0.08])
    idx = rng.choice(len(palette), size=(w, w), p=weights / weights.sum())
    ground = palette[idx]

    # A lit top edge and a shadowed foot, the one thing vanilla's block does
    # that is worth keeping -- it is what makes a flat square read as stone
    # with a top and a bottom rather than as noise.
    ground[0, :] = np.minimum(ground[0, :] * 1.28, 255)
    ground[w - 1, :] = ground[w - 1, :] * 0.72

    # Two knocked corners, back to the darkest tone rather than to black.
    for _ in range(2):
        cx, cy = int(rng.integers(1, w - 2)), int(rng.integers(2, w - 3))
        ground[cy:cy + 2, cx:cx + 2] = palette[0]

    return ground


def mod_masonry(w, h, seed=20260807):
    """The logo's hand-laid ruin masonry, for the panel.

    Laid in one piece rather than tiled: a repeated tile shows its period long
    before the eye reads it as stone, and this surface is two hundred pixels
    across.
    """
    rng = np.random.default_rng(seed)
    mortar = np.array(MOD_MORTAR, float)
    palette = np.array(MOD_STONE, float)
    wall = np.tile(mortar, (h, w, 1))

    course = 9
    for y0 in range(0, h, course):
        x = -int(rng.integers(0, 18))
        while x < w:
            length = int(rng.integers(9, 19))
            tone = palette[rng.integers(1, len(MOD_STONE))] * (0.86 + 0.28 * rng.random())

            for k in range(course - 1):
                v = tone * (1.0 - (k / (course - 1)) * 0.52)
                x0, x1 = max(0, x), min(w, x + length - 1)
                if x1 > x0 and y0 + k < h:
                    wall[y0 + k, x0:x1] = v * (0.97 + 0.06 * rng.random())

            if rng.random() < 0.22:
                cx = x if rng.random() < 0.5 else x + length - 2
                cy = y0 if rng.random() < 0.5 else y0 + course - 3
                if 0 <= cx < w - 1 and 0 <= cy < h - 1:
                    wall[cy:cy + 2, cx:cx + 2] = palette[0]

            x += length

    for y0 in range(0, h, course):
        if y0 + course - 1 < h:
            wall[y0 + course - 1, :] = mortar

    for _ in range(7):
        cx, cy = float(rng.integers(0, w)), float(rng.integers(0, h))
        ang = rng.random() * 6.283
        for _ in range(int(rng.integers(12, 40))):
            if 0 <= cx < w and 0 <= cy < h:
                wall[int(cy), int(cx)] = mortar
            ang += (rng.random() - 0.5) * 0.7
            cx += float(np.cos(ang))
            cy += float(np.sin(ang))

    return np.clip(wall, 0, 255)


# ------------------------------------------------------------------- panel

PANEL_W, PANEL_H = 216, 190
SHEET = 256

# Must agree with MuralScreen.
#
# One field, not two. The first cut gave the body its own recess and left the
# title sitting on bare masonry, and a title over brick courses is exactly as
# hard to read as a paragraph over them -- the pattern does not care which line
# of text it is behind. So the dressed slab starts above the title and runs to
# below the last line of body, and the masonry survives as a frame and a footer.
RECESS = (12, 10, 204, 163)     # x0, y0, x1, y1 -- exclusive on x1/y1

# Three pixels clear of the marker line, which ends at y=37 with a 9-pixel
# font. At 38 it sat hard against the glyphs and read as an underline on
# "IV of VIII" rather than as a rule separating the header from the body.
DIVIDER_Y = 41

ARROW_W, ARROW_H = 18, 11
ARROW_V = 192


def dressed_slab(w, h, seed=71_2026):
    """The smooth face the text is actually carved on.

    Flat and dark on purpose. The panel behind it is hand-laid masonry, which
    is the right thing for a frame and completely wrong to put a paragraph on:
    the brick courses, the chips and the cracks all run at text height and the
    body copy has to fight every one of them. Body text is drawn at #D8D0BC
    (luminance 209), so the field is held near luminance 28 and its mottle kept
    to a few points -- the contrast has to come from the field being *empty*,
    not from making the letters brighter.

    A slight top-lit gradient and a couple of very faint cracks keep it from
    looking like a flat fill, without putting anything at text contrast.
    """
    rng = np.random.default_rng(seed)
    base = np.array(MOD_STONE[0], float)

    slab = np.tile(base, (h, w, 1))
    slab += rng.normal(0.0, 2.6, (h, w, 1))

    # Dressed stone catches a little light at the top of the cut.
    lift = np.linspace(1.16, 0.94, h)[:, None, None]
    slab = slab * lift

    # Two hairline cracks, at barely above the mottle. Any stronger and they
    # read as strikethrough on whatever line they cross.
    for _ in range(2):
        cx, cy = float(rng.integers(0, w)), float(rng.integers(0, h))
        ang = rng.random() * 6.283
        for _ in range(int(rng.integers(20, 50))):
            if 0 <= cx < w and 0 <= cy < h:
                slab[int(cy), int(cx)] *= 0.82
            ang += (rng.random() - 0.5) * 0.8
            cx += float(np.cos(ang))
            cy += float(np.sin(ang))

    return np.clip(slab, 0, 255)


def bevel(img, x0, y0, x1, y1, lit, dark, inset_dark_top=True):
    """A one-pixel chisel edge around a rectangle.

    `inset_dark_top` picks which way the surface reads. Light falls from above,
    so a *recess* is dark along its top and left inner edges and lit along the
    bottom and right; a *raised* panel is the other way round. Getting this
    backwards is the single reason a carved field can look embossed instead.
    """
    top, bottom = (dark, lit) if inset_dark_top else (lit, dark)
    img[y0, x0:x1] = top
    img[y0:y1, x0] = top
    img[y1 - 1, x0:x1] = bottom
    img[y0:y1, x1 - 1] = bottom


def build_panel():
    sheet = np.zeros((SHEET, SHEET, 4), np.uint8)

    wall = mod_masonry(PANEL_W, PANEL_H)
    panel = wall.copy()

    dark = np.array(MOD_MORTAR, float)
    lit = np.array(MOD_STONE[5], float)

    # Outer frame: a raised border, so the whole panel reads as a slab standing
    # proud of the dimmed world behind it.
    panel[0, :] = dark
    panel[-1, :] = dark
    panel[:, 0] = dark
    panel[:, -1] = dark
    bevel(panel, 1, 1, PANEL_W - 1, PANEL_H - 1, lit, dark * 1.6, inset_dark_top=False)
    bevel(panel, 4, 4, PANEL_W - 4, PANEL_H - 4, np.array(MOD_STONE[2], float), dark, inset_dark_top=True)

    # The reading field, dressed smooth and cut back into the face.
    x0, y0, x1, y1 = RECESS
    panel[y0:y1, x0:x1] = dressed_slab(x1 - x0, y1 - y0)
    bevel(panel, x0, y0, x1, y1, np.array(MOD_STONE[4], float), dark, inset_dark_top=True)

    # A rule under the title, in ember. Short of the field edge on both sides
    # so it reads as inlay rather than as a crack running out of the stone.
    panel[DIVIDER_Y, 30:PANEL_W - 30] = EMBER * 0.85
    panel[DIVIDER_Y + 1, 30:PANEL_W - 30] = np.array(MOD_STONE[0], float) * 0.8

    sheet[0:PANEL_H, 0:PANEL_W, :3] = panel.astype(np.uint8)
    sheet[0:PANEL_H, 0:PANEL_W, 3] = 255

    # Arrows: prev normal, prev hover, next normal, next hover.
    for slot, (facing, hover) in enumerate([(-1, False), (-1, True), (1, False), (1, True)]):
        art = arrow(facing, hover)
        u = slot * ARROW_W
        sheet[ARROW_V:ARROW_V + ARROW_H, u:u + ARROW_W, :] = art

    return sheet


def arrow(facing, hover):
    """A carved chevron with a tail. Ember at rest, amber and bone on hover.

    `facing` is -1 for the back arrow and +1 for the forward one.

    The **vertex sits on the side the arrow points at**, and the arms and tail
    both run back from it. The first draft put the vertex on the far side, so
    the back arrow rendered as `>-` and the forward one as `-<` -- each one
    pointing at the page it was taking you away from, which is a mistake that
    looks perfectly deliberate until you try to use it.
    """
    art = np.zeros((ARROW_H, ARROW_W, 4), np.uint8)
    core = np.zeros((ARROW_H, ARROW_W), bool)

    mid = ARROW_H // 2
    tip_x = (ARROW_W - 7) if facing > 0 else 6

    # Arms, sweeping back from the vertex.
    for i in range(5):
        x = tip_x - facing * i
        for y in (mid - i, mid + i):
            if 0 <= x < ARROW_W and 0 <= y < ARROW_H:
                core[y, x] = True

    # Tail, running back along the axis.
    for k in range(7):
        x = tip_x - facing * k
        if 0 <= x < ARROW_W:
            core[mid, x] = True

    # The arrow has to point where it travels. Cheap to state, and it is the
    # one property of this sprite that is invisible in a thumbnail and obvious
    # the moment somebody tries to turn a page.
    lit_x = np.where(core.any(axis=0))[0]
    leading = lit_x.max() if facing > 0 else lit_x.min()
    assert core[mid, leading], f"arrow facing {facing} has no vertex on its leading edge"
    assert core[:, leading].sum() == 1, f"arrow facing {facing} is blunt, not pointed"

    halo = grow(core) & ~core

    body = AMBER if hover else EMBER
    tip = BONE if hover else AMBER

    art[halo, :3] = (body * 0.55).astype(np.uint8)
    art[halo, 3] = 255
    art[core, :3] = (tip if hover else body).astype(np.uint8)
    art[core, 3] = 255

    return art


# -------------------------------------------------------------------- main


def contact_sheet(faces):
    """The whole set as a grid: one passage per row, glow left to right.

    Vanilla deepslate brick sits in the first column of every row, so the two
    questions this has to answer are both on screen at once -- does the stone
    still match the wall it is set into, and does the carving visibly climb
    across the row.

    Written at 1:1 as well as 6x, and the 1:1 is the one that decides it. Every
    glyph looks fine at six times size; the question is whether it still reads
    at the size a player actually walks past.
    """
    vanilla = Image.open(
        os.path.join(PREVIEW, "vanilla", "assets", "minecraft", "textures", "block", "deepslate_bricks.png")
    ).convert("RGB")

    steps = len(GLOW_RAMP)
    grid = Image.new("RGB", ((steps + 1) * 16, len(faces) * 16), (0, 0, 0))

    for row, ramp in enumerate(faces):
        grid.paste(vanilla, (0, row * 16))
        for glow, face in enumerate(ramp):
            grid.paste(Image.fromarray(face.astype(np.uint8)), ((glow + 1) * 16, row * 16))

    grid.save(os.path.join(PREVIEW, "mural_faces_1x.png"))
    grid.resize((grid.width * 6, grid.height * 6), Image.NEAREST).save(
        os.path.join(PREVIEW, "mural_faces_6x.png")
    )


def main():
    os.makedirs(BLOCK_DIR, exist_ok=True)
    os.makedirs(GUI_DIR, exist_ok=True)

    ground = vanilla_ground()
    faces = []

    for i, segments in enumerate(glyphs()):
        core = raster(segments, 16)

        # A stroke touching the border would be cropped by the block edge and
        # read as damage. Cheap to assert, and it has to hold for all eight.
        assert not core[0, :].any() and not core[15, :].any(), f"glyph {i} touches top/bottom"
        assert not core[:, 0].any() and not core[:, 15].any(), f"glyph {i} touches left/right"

        ramp = []
        for glow in range(len(GLOW_RAMP)):
            face = carve(ground, core, glow)
            ramp.append(face)

            out = np.zeros((16, 16, 4), np.uint8)
            out[..., :3] = face.astype(np.uint8)
            out[..., 3] = 255
            Image.fromarray(out).save(os.path.join(BLOCK_DIR, f"mural_{i}_{glow}.png"))

        faces.append(ramp)

    # Every face must actually differ from every other, across both axes.
    #
    # Two ways to ship something broken here and never notice: a copy-paste slip
    # in the glyph table gives two passages the same mark, and a ramp that does
    # not actually vary gives a mural that emits light without changing. Both
    # look deliberate in a screenshot.
    seen = {}
    for i, ramp in enumerate(faces):
        for glow, face in enumerate(ramp):
            key = face.astype(np.uint8).tobytes()
            assert key not in seen, f"mural {i} glow {glow} is identical to mural {seen[key]}"
            seen[key] = f"{i} glow {glow}"

    # And the ramp must be monotonically brighter, or a step is going the wrong
    # way. Measured on the glyph pixels only -- the stone around them does not
    # change, so including it would dilute the signal to near nothing.
    for i, ramp in enumerate(faces):
        core = grow(raster(glyphs()[i], 16))
        means = [float(face[core].mean()) for face in ramp]
        assert all(a < b for a, b in zip(means, means[1:])), (
            f"mural {i} glow ramp is not increasing: "
            + ", ".join(f"{m:.1f}" for m in means)
        )

    Image.fromarray(build_panel()).save(os.path.join(GUI_DIR, "mural.png"))

    contact_sheet(faces)

    print(f"wrote {len(faces)} x {len(GLOW_RAMP)} = {len(faces) * len(GLOW_RAMP)} mural faces to {BLOCK_DIR}")
    print(f"wrote panel to {os.path.join(GUI_DIR, 'mural.png')}")
    print(f"contact sheets in {PREVIEW}")


if __name__ == "__main__":
    main()
