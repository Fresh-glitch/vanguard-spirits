"""Block and item textures for the Unfinished Epitaph.

The Grave block it stands in is *earth* -- sampled at #745C45 / #614B39 /
#3A2C1C, a mound of dug soil. So the marker is deliberately the opposite
material: carved deepslate. Brown mound, grey stone. Nothing else in the
graveyard is grey, so it reads at range.

## The ramp

The first version of this ramp had two faults, both of which have names:

- **Its shades were too close to tell apart.** Luminances ran 23, 38, 54, 70,
  84, 107 -- adjacent steps 14 to 16 apart, which at sixteen pixels is mush. It
  also topped out at 107 while *defining* a #939CAD it never drew, left over
  from the abandoned carving pass below. The sprite's own highlight was sitting
  unused in its palette.
- **It had no temperature.** Every shade sat at hue 0.617-0.625 and varied only
  in brightness, with saturation running backwards -- brightest shade least
  saturated.

The fix carries the temperature split **by saturation rather than by hue**. A
draft did it by hue, the textbook way, and came out amber at the top: correct
in the abstract and wrong here, because this texture drives the Blockbench
model and the marker has to keep looking like deepslate. So the hue stays
inside the original blue-grey band, the shadow becomes the most saturated blue,
and the highlight goes nearly neutral. Cold against warm, without leaving grey.

## Block and item share hues, not brightness

They are generated from one ramp but weighted differently, and that is
deliberate. An item sprite is a lit three-quarter view sitting on an inventory
grey; a block face is ambient stone standing among grave mounds that measure 64
and 69 mean luminance. Centring the block on the ramp's midtone put it at 110 --
pale limestone against brown earth. The weights below hold it near 66.
"""

import colorsys
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
CLEAR = (0, 0, 0, 0)

# Four body shades, shadow first. Four rather than five because a fifth was
# drawn *nowhere*: every pixel that would have taken the darkest body tone sits
# on the silhouette, and the edge pass overwrites those. A shade that exists
# only in the palette is a shade the sprite does not have.
RAMP_HSV = [
    (0.630, 0.22, 0.28),  # deepest body shade: most saturated blue, coldest
    (0.615, 0.18, 0.42),
    (0.600, 0.12, 0.56),
    (0.580, 0.06, 0.70),  # highlight: nearly neutral, and so the warm end
]

# The edge in two tones. The shadow side takes the darker; the lit upper left
# takes the lighter. That is selective outlining rather than ringing the whole
# shape in one flat black -- but the ring stays *unbroken*, which is this mod's
# own convention and what lets an item hold a slot at either inventory grey.
EDGE_HSV = (0.645, 0.28, 0.11)
EDGE_LIT_HSV = (0.633, 0.24, 0.19)

# What shipped in 1.12.0, kept as the positive control for check_ramp.
OLD_RAMP = [(0x15, 0x17, 0x1C), (0x23, 0x26, 0x2E), (0x33, 0x36, 0x3F),
            (0x41, 0x46, 0x53), (0x4E, 0x54, 0x62), (0x64, 0x6B, 0x7C)]


def rgb(hsv):
    r, g, b = colorsys.hsv_to_rgb(*hsv)
    return (round(r * 255), round(g * 255), round(b * 255))


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp():
    return [rgb(h) for h in RAMP_HSV]


# --- checks ------------------------------------------------------------------


def check_ramp(pal):
    """Shades must be separable, and the ramp must have a temperature."""
    order = sorted(pal, key=lum)
    gaps = [lum(order[i + 1]) - lum(order[i]) for i in range(len(order) - 1)]
    assert min(gaps) >= 25, f"shades too close to tell apart: {[round(g) for g in gaps]}"

    hsv = [colorsys.rgb_to_hsv(*[c / 255 for c in col]) for col in order]
    # Either direction counts. This ramp carries its temperature by saturation
    # (saturated blue shadow, neutral highlight); a hue-shifted one would carry
    # it by hue. What is rejected is the old arrangement, where hue was flat to
    # three decimals *and* saturation ran backwards, so there was no
    # temperature anywhere at all.
    hue_shift = abs(hsv[-1][0] - hsv[0][0])
    sat_shift = hsv[0][1] - hsv[-1][1]
    assert hue_shift >= 0.02 or sat_shift >= 0.10, (
        f"no temperature direction: hue moves {hue_shift:.3f}, saturation {sat_shift:+.2f}"
    )
    assert len(pal) <= 5, "five shades is the ceiling worth having at this size"
    return gaps


def prove_the_check_bites():
    """Run check_ramp against the ramp it exists to reject.

    A check that accepts everything and a check that measures nothing produce
    the same silence, so this one is made to fail on the old palette before it
    is trusted on the new.
    """
    try:
        check_ramp(OLD_RAMP)
    except AssertionError:
        return
    raise AssertionError("check_ramp accepts the very ramp it exists to reject")


def check_not_pillow(img, mask):
    """Pillow shading rings a shape; a real light source does not.

    Under pillow shading a pixel and its reflection through the shape's centre
    carry the same shade, both being the same distance from the outline. Under
    directional light they differ.
    """
    xs = [p[0] for p in mask]
    ys = [p[1] for p in mask]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    same = tested = 0
    for x, y in mask:
        mx, my = int(round(2 * cx - x)), int(round(2 * cy - y))
        if (mx, my) not in mask:
            continue
        tested += 1
        same += img.getpixel((x, y)) == img.getpixel((mx, my))
    share = same / max(1, tested)
    assert share < 0.55, f"reads as pillow shading: {share:.0%} of mirrored pairs match"
    return share


# --- the block ---------------------------------------------------------------


def speckle(px, x0, x1, rng, pal, dark):
    """Weathered stone: mostly the dark half of the ramp, with grain.

    Deliberately sparse, and deliberately dark. A block face is these sixteen
    pixels stretched over a whole face at range: heavy noise turns into visible
    tiling, and a mid-weighted mix turns the marker pale against the mounds.
    """
    for y in range(SIZE):
        for x in range(x0, x1):
            roll = rng.random()
            if roll < 0.22:
                px[x, y] = dark + (255,)
            elif roll < 0.40:
                px[x, y] = pal[1] + (255,)
            elif roll < 0.45:
                px[x, y] = pal[2] + (255,)
            else:
                px[x, y] = pal[0] + (255,)


def carve_ABANDONED():
    """Superseded. Kept only so the reason is not lost.

    Painting an inscription into the texture was always going to fail: six
    pixels of face cannot depict writing, so the marks read as stripes no
    matter how they are drawn -- and two rounds of fixing the *marks* never
    touched that, because the problem was the medium.

    Minecraft already solves this. A sign draws its text as geometry submitted
    over the block at font resolution, entirely independent of the 16 pixel
    texture underneath. So the stone stays blank and the name is real text.
    """


def build_block(pal, dark):
    img = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    px = img.load()
    rng = random.Random(0x1CE)

    # Two independent speckle passes rather than one. The shaft's front and
    # back would otherwise share a grain pattern, and the repeat is visible
    # when you walk round a placed marker.
    speckle(px, 0, 8, rng, pal, dark)
    speckle(px, 8, 16, rng, pal, dark)
    return img


# --- the item ----------------------------------------------------------------
# Dome over straight sides over a stepped plinth, 12 by 14 inside a 16x16
# sheet, which is the proportion every shipped charm uses. Each step widens by
# one pixel a side (4, 6, 8 up the dome; 8, 10, 12 down the plinth), which is
# the consistent run that keeps a curve smooth at this size.

ROWS = {1: (6, 9), 2: (5, 10), 3: (4, 11)}
for _y in range(4, 13):
    ROWS[_y] = (4, 11)
ROWS[13] = (3, 12)
ROWS[14] = (2, 13)


def shape():
    return {(x, y) for y, (x0, x1) in ROWS.items() for x in range(x0, x1 + 1)}


def lit_field(mask):
    """How much light each pixel takes, as a slab lit from the upper left.

    Two terms. Across the stone the brightness holds a plateau a little left of
    centre and falls off both ways, which is what makes a slab read as having a
    front face rather than being a flat cut-out -- an earlier version peaked at
    a single value of nx and put the highlight on five pixels out of a hundred
    and twelve, which reads as a stray light pixel. Down the stone it darkens,
    because the plinth sits in the marker's own shadow.

    Both terms are *directional*, neither is a distance from the outline, and
    that is what keeps this from being pillow shading.
    """
    ys = [p[1] for p in mask]
    y0, y1 = min(ys), max(ys)
    field = {}
    for x, y in mask:
        rx0, rx1 = ROWS[y]
        nx = (x - rx0) / max(1, rx1 - rx0)
        ny = (y - y0) / max(1, y1 - y0)
        across = max(0.0, min(1.0, 1.0 - max(0.0, abs(nx - 0.30) - 0.12) / 0.50))
        down = 1.0 - ny
        # Weighted toward the across term so the lit column survives most of
        # the way down the stone, which says "slab" rather than "wedge".
        field[(x, y)] = 0.70 * across + 0.30 * down
    return field


def item_sprite(pal, edge, edge_lit):
    """The marker in the hand, carrying no writing at all.

    Three drafts went into the drawing and the last one is the shortest. Two
    earlier attempts painted an inscription -- solid rules, then dashes -- and
    both read as stripes or as damage, because six pixels of interior cannot
    depict text.

    It turned out not to need any. The item is the *unfinished* epitaph, so
    writing does not exist yet: it comes into being when the stone is placed
    and a name is cut into it, and the placed block draws that as real text. A
    blank marker is therefore both the easier drawing and the more truthful
    one.
    """
    img = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    px = img.load()
    mask = shape()
    field = lit_field(mask)

    for (x, y) in mask:
        idx = max(0, min(len(pal) - 1, int(round(field[(x, y)] * (len(pal) - 1)))))
        px[x, y] = pal[idx] + (255,)

    # A 50:50 chequer where the bands meet, which does two jobs: it softens the
    # transitions without adding a colour, and it gives the stone a grain that
    # ties the sprite to the block face's speckle. Confined to the boundaries,
    # so it reads as a transition rather than as noise over the whole marker.
    steps = len(pal)
    for (x, y) in mask:
        t = field[(x, y)] * (steps - 1)
        low = int(t)
        frac = t - low
        if 0.28 < frac < 0.72 and (x + y) % 2 == 0:
            px[x, y] = pal[min(steps - 1, low + 1)] + (255,)

    # The edge last, so it wins over both passes above.
    for (x, y) in sorted(mask):
        openness = [d for d in ((0, -1), (-1, 0), (1, 0), (0, 1))
                    if (x + d[0], y + d[1]) not in mask]
        if not openness:
            continue
        lit_side = any(d in ((0, -1), (-1, 0)) for d in openness)
        px[x, y] = (edge_lit if lit_side else edge) + (255,)

    return img


def check(sprite):
    """The numbers that catch what the eye forgives at 16 pixels."""
    box = sprite.getchannel("A").getbbox()
    w, h = box[2] - box[0], box[3] - box[1]
    tones = sorted(lum(p[:3]) for p in sprite.getdata() if p[3])
    print(f"item bbox {w}x{h}, luminance {tones[0]:.0f}-{tones[-1]:.0f}")

    assert w <= 12 and h <= 14, "outside the charm convention"
    # Darkness was never what killed the first Fractured Memory -- darkness
    # with no internal spread was. This is the guard for that.
    assert tones[-1] - tones[0] >= 60, "too flat to read at 16 pixels"


if __name__ == "__main__":
    prove_the_check_bites()

    pal = ramp()
    gaps = check_ramp(pal)
    edge, edge_lit = rgb(EDGE_HSV), rgb(EDGE_LIT_HSV)
    print("ramp: " + "  ".join(f"#{r:02X}{g:02X}{b:02X}" for r, g, b in [edge, edge_lit] + pal))
    print(f"body gaps {[round(g) for g in gaps]}  (the ramp this replaces: [15, 16, 16, 14, 23])")

    block = os.path.join(BLOCKS, "unfinished_epitaph.png")
    blk = build_block(pal, edge_lit)
    px = [p[:3] for p in blk.getdata() if p[3]]
    print(f"block mean luminance {sum(lum(p) for p in px) / len(px):.1f}"
          f"   (grave_side 69, grave_top 64 -- it has to stand among them)")
    blk.save(block)
    print("wrote", block)

    sprite = item_sprite(pal, edge, edge_lit)
    check(sprite)
    share = check_not_pillow(sprite, shape())
    print(f"mirrored-pair match {share:.0%} -- not pillow shaded")
    item = os.path.join(ITEMS, "unfinished_epitaph.png")
    sprite.save(item)
    print("wrote", item)
