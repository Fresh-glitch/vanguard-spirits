"""Mourner's Feather: what the bird leaves behind instead of a body.

The design brief is unusual, so it is worth stating. Killing a Mourner breaks
its ruin's vigil for good, so the mod must never make the kill the good move.
This item is therefore something you get by *startling* one -- and it has to
look like a prize, or the trade reads as a consolation.

Palette is measured off `textures/entity/mourner.png` rather than invented. The
bird is almost entirely near-black blue: #1C1E25, #121318, #0A0B0E, #262932,
topping out at #343844, with a warm brown in the beak and a two-pixel amber eye
at #B07426 / #ECB65C.

Those are the values used, near enough. The first version lifted them three
steps on the Fractured Memory's precedent -- that item was drawn at about this
darkness and vanished into the inventory slot, so the rule written down was
"lift the bird's hue until it is legible at sixteen pixels". Held against the
mob in game the result was obviously wrong: a pale blue-grey feather beside a
near-black bird, plainly off a different animal.

**The Memory's lesson does not transfer, because the Memory is not part of
anything.** It is free to be whatever value reads best; a feather is a piece of
a mob standing right next to it, and matching the mob outranks any amount of
internal polish. What made the Memory vanish was not darkness on its own but
darkness with no internal spread -- so the fix here is to keep the mass on the
bird's own two commonest colours and spend the whole remaining range on
contrast *within* the sprite. The silhouette never needed the help: even the
darkest of these sits far below either inventory grey.

The one warm accent is the calamus, which is where a real feather is a bare horn
shaft rather than vane -- and it lands on the bird's own eye amber, so the item
carries exactly one colour from the animal and it is the colour you actually
noticed while it was circling.

## Why this one has no outline ring

Every other item in this mod is drawn as a solid mass inside an unbroken
near-black ring. Two drafts here tried to hold that rule and neither survived
being looked at: the first read as a lozenge on a stick, the second as a spruce
tree. Vanilla's own `feather.png` says why. It is **sixty-five pixels, four
greys, and no outline anywhere**, laid on a diagonal and full of holes -- row 8
is `......D.FD9DFD..`, with a gap punched straight through the middle of it.

The ring is not a house style that happens to be applied to plates; it is a
*plate* technique. Around a shape five pixels across it leaves three pixels of
interior, and a vane cannot be drawn in three pixels. And the raggedness is not
decoration either: on a shape this small the holes in the silhouette are the
only thing that says *barbs*, and without them a tapered blob is a leaf.

So this follows vanilla's idiom rather than the mod's: diagonal, sparse, ragged,
no ring. It still holds a slot at both inventory greys, because the mass is dark
and the rachis is pale -- the contrast is inside the sprite instead of around it.

The sprite is authored as an ASCII map rather than computed. Two attempts at
generating barbs from a formula produced dither, not feathers; at sixteen pixels
there are few enough decisions that making each one by hand is both quicker and
better, and the map is legible in the diff.
"""

import os
import struct
import zlib

OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\textures\item"
SIZE = 16

# The bird's own values. `d` and `h` are its two commonest body colours almost
# exactly, `l` is its highlight, and only `w` -- five pixels along the lit edge
# -- goes a step past anything on the mob. See the module docstring for why an
# earlier version sat three steps lighter than all of this.
#
# A dark vane with a pale spine down it was tried and abandoned: at sixteen
# pixels that arrangement is a *knife*, and the amber calamus at the base
# obligingly finished the job as a pommel. Vanilla's feather has it the other way
# round -- a light vane with the rachis as its darkest line -- and that reads,
# because a spine is a shadow between two banks of barbs, not a highlight along
# the middle of a blade.
PALETTE = {
    "k": (0x07, 0x08, 0x0A),   # the rachis, and nothing else -- [SPINE] relies on that
    "d": (0x12, 0x13, 0x18),   # the shadow side; the bird's second commonest colour
    "h": (0x1F, 0x22, 0x2B),   # near its dominant #1C1E25
    "l": (0x31, 0x36, 0x44),   # its own highlight, #343844
    "w": (0x4E, 0x55, 0x68),   # catching the light, up and to the left
    "q": (0xB0, 0x74, 0x26),   # calamus, on the Mourner's own eye amber
    "Q": (0xEC, 0xB6, 0x5C),
}

# The lightest channel on the bird's body, measured off its entity texture
# (#343844). The vane may not go past it, and the lit edge only a little -- this
# is the drift that put a pale feather next to a near-black bird, and it is not
# visible from reading hex.
BIRD_LIGHTEST = 0x44
LIT_EDGE_ALLOWANCE = 0x2A

# Tip at the upper right, quill at the lower left, the way vanilla lays a
# feather -- a diagonal fills a square canvas where an upright shape wastes the
# corners. The rachis is the `r`/`R` run; everything left of it catches the
# light, everything right of it is in shadow.
#
# The gaps matter as much as the pixels. Each notch in an edge and each hole in
# the vane is a barb group coming apart, which is the whole reason a Mourner
# sheds one -- and it is what stops the silhouette closing up into a leaf.
#
# **But never against the rachis.** A hole one pixel off the shaft does not read
# as barbs coming apart, it reads as a pixel someone forgot: the shaft is the one
# line the eye follows end to end, so a gap touching it is damage to the spine
# rather than a gap in the vane. It was drawn that way once and spotted
# immediately in game. Splits stay two or more pixels out, and the shaft runs
# unbroken from tip to calamus -- [SPINE] asserts both, since neither survives
# being eyeballed in a diff.
ART = [
    "................",
    "................",
    "............lk..",
    "...........wkl..",
    "..........lhkl..",
    ".........lwkh...",
    "........hlkhd...",
    ".......lwlkh....",
    ".....h.lwkhd....",
    "....lwllkhd.....",
    "...h.llkhd......",
    "..lwlhkhd.......",
    "..hlhkd.........",
    "...dk...........",
    "..qQ............",
    "................",
]


def build():
    px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    for y, row in enumerate(ART):
        for x, ch in enumerate(row):
            if ch != ".":
                px[y][x] = PALETTE[ch] + (255,)
    return px


def write(path, px):
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *px[y][x]) for x in range(SIZE))
        for y in range(SIZE)
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n"
                 + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
                 + chunk(b"IDAT", zlib.compress(raw, 9))
                 + chunk(b"IEND", b""))

    # The map is hand-typed, so the one mistake worth guarding is a short row --
    # Python would pad nothing and the sprite would simply lose its right edge.
    for y, row in enumerate(ART):
        assert len(row) == SIZE, f"row {y} is {len(row)} wide, not {SIZE}"

    # The vane belongs to the bird. Only the amber is allowed to be bright, and
    # only because it is the bird's eye colour.
    for ch in "kdhl":
        assert max(PALETTE[ch]) <= BIRD_LIGHTEST, \
            f"{ch} is lighter than anything on the Mourner"
    assert max(PALETTE["w"]) <= BIRD_LIGHTEST + LIT_EDGE_ALLOWANCE, \
        "the lit edge has drifted away from the bird"

    # [SPINE] `k` is the rachis and only the rachis, so its pixels are the shaft.
    # Two things have to hold and neither is visible by reading the map: the
    # shaft runs unbroken from tip to calamus, and no barb split opens up
    # against it. A gap one pixel off the shaft reads as a missing pixel rather
    # than as barbs parting -- it shipped that way once and was spotted in game
    # straight away.
    shaft = {}
    for y, row in enumerate(ART):
        for x, ch in enumerate(row):
            if ch == "k":
                assert y not in shaft, f"row {y} has two rachis pixels"
                shaft[y] = x

    ys = sorted(shaft)
    for a, b in zip(ys, ys[1:]):
        assert b == a + 1, f"the rachis breaks between rows {a} and {b}"
        assert abs(shaft[b] - shaft[a]) <= 1, f"the rachis jumps sideways at row {b}"

    # The calamus has to carry on from where the rachis stops, or the shaft ends
    # in mid-air and the amber reads as a bead lying next to the feather.
    quill = [(x, y) for y, row in enumerate(ART)
             for x, ch in enumerate(row) if ch in "qQ"]
    assert quill, "no calamus"
    foot = (shaft[ys[-1]], ys[-1])
    assert any(abs(x - foot[0]) <= 1 and y - foot[1] == 1 for x, y in quill), \
        "the calamus does not join the rachis"

    splits = 0
    for y, row in enumerate(ART):
        solid = [x for x, ch in enumerate(row) if ch != "."]
        if not solid:
            continue
        for x in range(solid[0], solid[-1]):
            if row[x] != ".":
                continue
            splits += 1
            if y in shaft:
                assert abs(x - shaft[y]) >= 2, \
                    f"the split at {x},{y} touches the rachis"

    cols = [x for x in range(SIZE) if any(px[y][x][3] for y in range(SIZE))]
    rows = [y for y in range(SIZE) if any(px[y][x][3] for x in range(SIZE))]
    opaque = [p for r in px for p in r if p[3]]

    print(f"wrote {path}")
    print(f"  margins L{cols[0]} R{SIZE - 1 - cols[-1]} "
          f"T{rows[0]} B{SIZE - 1 - rows[-1]}")
    print(f"  {len({p[:3] for p in opaque})} colours over {len(opaque)} px "
          f"(vanilla's feather: 4 over 65)")
    # Printed rather than merely asserted: zero splits would pass every check
    # above and leave a leaf.
    print(f"  rachis unbroken across {len(ys)} rows, {splits} barb splits, "
          f"none touching it")
    vane = max(max(PALETTE[ch]) for ch in "kdhl")
    print(f"  vane tops out at 0x{vane:02X}, lit edge 0x{max(PALETTE['w']):02X}"
          f"  (the bird: 0x{BIRD_LIGHTEST:02X})")


os.makedirs(OUT, exist_ok=True)
write(os.path.join(OUT, "mourner_feather.png"), build())
