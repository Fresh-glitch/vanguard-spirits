"""Block texture for the Unfinished Epitaph.

The Grave block it stands in is *earth* -- sampled at #745C45 / #614B39 / #3A2C1C,
a mound of dug soil. So the marker is deliberately the opposite material: carved
deepslate, in the same ramp as the item sprite and the Fractured Memory. Brown
mound, grey stone. Nothing else in the graveyard is grey, so it reads at range.

Laid out in two halves rather than one tiled stone, because the shaft's front
face is the only one that carries the carving:

  x 0..7   plain weathered stone, used by every face
  x 8..15  the same stone with the first stroke and the start of the second

The front face then names the right half explicitly in its UV. Auto UV would
scatter the carving across all six faces of all three cubes.
"""

import os
import random

from PIL import Image

BLOCKS = os.path.join(
    "P:", os.sep, "ClaudeMods", "vanguard-spirits-26.2", "src", "main",
    "resources", "assets", "vanguard-spirits", "textures", "block",
)
ITEMS = os.path.join(
    "P:", os.sep, "ClaudeMods", "vanguard-spirits-26.2", "src", "main",
    "resources", "assets", "vanguard-spirits", "textures", "item",
)
SIZE = 16

STONE_DEEP = (0x33, 0x36, 0x3F, 255)
STONE_MID = (0x41, 0x46, 0x53, 255)
STONE_HIGH = (0x4E, 0x54, 0x62, 255)
STONE_LIT = (0x64, 0x6B, 0x7C, 255)
STONE_FRESH = (0x93, 0x9C, 0xAD, 255)
GROOVE = (0x23, 0x26, 0x2E, 255)


def speckle(px, x0, x1, rng):
    """Weathered stone: mostly mid, with grain so a large flat face is not flat.

    Deliberately sparse. A block face is 16 pixels of a texture stretched over a
    whole face at range, and heavy noise turns into visible tiling.
    """
    for y in range(SIZE):
        for x in range(x0, x1):
            roll = rng.random()
            if roll < 0.10:
                px[x, y] = STONE_HIGH
            elif roll < 0.18:
                px[x, y] = STONE_DEEP
            elif roll < 0.21:
                px[x, y] = STONE_LIT
            else:
                px[x, y] = STONE_MID


def carve_ABANDONED(px):
    """Superseded. Kept only so the reason is not lost.

    Painting an inscription into the texture was always going to fail: six
    pixels of face cannot depict writing, so the marks read as stripes no matter
    how they are drawn -- and two rounds of fixing the *marks* never touched
    that, because the problem was the medium.

    Minecraft already solves this. A sign draws its text as geometry submitted
    over the block at font resolution, entirely independent of the 16 pixel
    texture underneath. So the stone stays blank and the name is real text.
    """
    """The one motif, same as the item: a first stroke and two pixels of a second.

    Cut as a dark groove with a fresh lip above it. On the item sprite these were
    drawn solid bright and read as a painted highlight -- a real chisel mark is a
    shadow in the stone with light catching the cut edge, and at block scale
    there is finally room to show both.
    """
    # Inset by two pixels each side. The first version ran x 9..14, which spans
    # the whole six wide window the shaft's face maps to -- and a mark touching
    # both edges of its window stops being a mark and becomes a band, which is
    # the same thing that happened to the Binding Altar's rune. Stone has to
    # show either side of a cut or it is not a cut, it is a stripe.
    for x in range(10, 14):
        px[x, 4] = GROOVE
        px[x, 3] = STONE_FRESH
    for x in range(10, 12):
        px[x, 7] = GROOVE
        px[x, 6] = STONE_FRESH


def build():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(0x1CE)

    # One stone across the whole sheet now that no face needs a carved variant.
    # The right half stays a separate speckle pass so the two halves do not share
    # a grain pattern -- the shaft's front and back would otherwise be identical
    # and the repeat is visible when you walk round it.
    speckle(px, 0, 8, rng)
    speckle(px, 8, 16, rng)
    return img


# --- the item ----------------------------------------------------------------
# Dome over straight sides over a stepped plinth, 12 by 14 inside a 16x16 sheet,
# which is the proportion every shipped charm uses.
OUTLINE = (0x15, 0x17, 0x1C, 255)
CLEAR = (0, 0, 0, 0)

ROWS = {1: (6, 9), 2: (5, 10), 3: (4, 11)}
for _y in range(4, 13):
    ROWS[_y] = (4, 11)
ROWS[13] = (3, 12)
ROWS[14] = (2, 13)


def item_sprite():
    """The marker in the hand, carrying no writing at all.

    Three drafts went into this and the last one is the shortest. Two earlier
    attempts painted an inscription -- solid rules, then dashes -- and both read
    as stripes or as damage, because six pixels of interior cannot depict text.

    It turned out not to need any. The item is the *unfinished* epitaph, so
    writing does not exist yet: it comes into being when the stone is placed and
    a name is cut into it, and the placed block draws that as real text. A blank
    marker is therefore both the easier drawing and the more truthful one.
    """
    img = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    px = img.load()

    for y, (x0, x1) in ROWS.items():
        for x in range(x0, x1 + 1):
            px[x, y] = STONE_HIGH if y < 8 else STONE_MID

    for y, (x0, x1) in ROWS.items():
        px[x0 + 1, y] = STONE_LIT if y < 10 else STONE_HIGH
        px[x1 - 1, y] = STONE_DEEP
    for x in range(ROWS[3][0] + 1, ROWS[3][1]):
        px[x, 3] = STONE_LIT

    for y in (13, 14):
        for x in range(ROWS[y][0], ROWS[y][1] + 1):
            px[x, y] = STONE_DEEP if y == 14 else STONE_MID

    filled = {(x, y) for y, (x0, x1) in ROWS.items() for x in range(x0, x1 + 1)}
    for (x, y) in filled:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in filled:
                px[x, y] = OUTLINE
                break

    return img


def check(sprite):
    """The two numbers that catch what the eye forgives at 16 pixels."""
    box = sprite.getchannel("A").getbbox()
    w, h = box[2] - box[0], box[3] - box[1]
    tones = sorted({(p[0] + p[1] + p[2]) // 3 for p in sprite.getdata() if p[3]})
    print(f"item bbox {w}x{h}, value spread {tones[0]}-{tones[-1]}")

    assert w <= 12 and h <= 14, "outside the charm convention"
    # Darkness was never what killed the first Fractured Memory -- darkness with
    # no internal spread was. This is the guard for that.
    assert tones[-1] - tones[0] >= 60, "too flat to read at 16 pixels"


if __name__ == "__main__":
    block = os.path.join(BLOCKS, "unfinished_epitaph.png")
    build().save(block)
    print("wrote", block)

    sprite = item_sprite()
    check(sprite)
    item = os.path.join(ITEMS, "unfinished_epitaph.png")
    sprite.save(item)
    print("wrote", item)
