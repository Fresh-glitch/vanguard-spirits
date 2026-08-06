"""The mod's logo: an echo rune burning in deepslate.

Earlier attempts drew the mausoleum shaft from above -- accurate, and unusable.
Seen from directly over a thirty-block drop the treads foreshorten into slivers,
so the stair never resolved; three renders and four pixel passes all came out as
concentric squares. The lesson is worth keeping: **a logo is a silhouette, not a
scene.** At the ninety-six pixels Modrinth actually shows, an image has one shape
to spend and no room for a subject that needs perspective to be understood.

So this one takes the mark the ruins already wear. The echo runes are the sigils
that surface on a Guarded Ruin's stonework -- six of them, bone-white over amber,
drawn for `EchoRuneParticle`. One of those, carved large into deepslate and lit
from inside the cut, is the whole mod in a single form: ancient stone, and
something still alive in it.

The glyph here is of that family rather than a blown-up copy of a sixteen-pixel
particle -- a full stem with arms swept up and down from it, so it reads as
something grown as much as written. Same three-colour ramp as the particles:
#B17D2A ember, #D6A244 amber, #FFF8E0 bone.

## What makes it work at thumbnail size

- **One shape, centred, high contrast.** Warm light on cold stone is the whole
  read, and it survives any amount of downscaling.
- **The cut, not just the glyph.** A chisel groove of shadow around the light is
  what makes the rune sit *in* the stone instead of floating on it.
- **Banded light, never a smooth gradient.** A soft falloff is the one thing
  that will not sit in pixel art -- it reads as a photograph behind the bricks.
  Minecraft lights the world in steps and so does this.

Native 128, eight deepslate blocks square, scaled up nearest-neighbour so the
pixels stay hard.
"""

import os

import numpy as np
from PIL import Image

OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\branding"
ICON_DIR = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits"

BLOCK = 16
GRID = 8
NATIVE = BLOCK * GRID       # 128
SCALE = 4                   # -> 512

# Our own masonry, not vanilla's deepslate.
#
# Copying the block texture gave neutral machine-cut grey, and it fought the
# rune. The mod's own stone is *cool* -- the Mourner is blue-black (#1C1E25),
# the Fractured Memory's plate runs #33363F to #4E5462 -- and warm light on cold
# stone is a far better pairing than warm on neutral. So this ramp is the mod's
# blue-grey rather than Mojang's, and the bricks are laid by hand: irregular
# lengths, ragged course offsets, chipped corners and the odd crack. Ruin
# masonry that has been standing a long time, not a fresh crafting recipe.
STONE = [
    (0x1A, 0x1C, 0x22),
    (0x22, 0x25, 0x2D),
    (0x2B, 0x2F, 0x39),
    (0x35, 0x3A, 0x46),
    (0x41, 0x47, 0x55),
    (0x4E, 0x55, 0x66),
    (0x5C, 0x64, 0x78),
]
MORTAR = (0x0E, 0x10, 0x16)

COURSE = 8                  # brick height
BRICK_MIN, BRICK_MAX = 7, 16
CHIP_CHANCE = 0.22
CRACKS = 5

# The echo runes' own three colours.
EMBER = np.array((0xB1, 0x7D, 0x2A), float)
AMBER = np.array((0xD6, 0xA2, 0x44), float)
BONE = np.array((0xFF, 0xF8, 0xE0), float)

CORE = 1.20                 # half-width of the bone-white centre of a stroke
STROKE = 2.45               # half-width of the whole lit stroke
GROOVE = 4.10               # how far the chisel shadow reaches past it
LIGHT_LEVELS = 4
# Kept short deliberately. At twice this the bands sprawled into lumpy brown
# clouds that buried the brickwork -- the stone has to stay legible as stone,
# or the rune is floating on mud rather than cut into a wall.
REACH = 13.0
INSET = 0.93                # pulls the glyph off the edges of the frame


def stone_wall(w, seed=20260806):
    """A whole wall of hand-laid ruin masonry.

    Built in one piece rather than as a repeating tile, because a tile repeated
    eight times across shows its seams immediately -- the eye finds the period
    long before it reads the stone. Laid course by course with bricks of
    varying length and each course started at its own offset, so no two rows
    line up and nothing repeats.

    Each brick is lit along its top edge and falls into shadow at its foot,
    which is the one thing vanilla's texture does that is worth keeping: it is
    what makes a flat wall read as stacked blocks rather than as a grid.
    """
    rng = np.random.default_rng(seed)
    mortar = np.array(MORTAR, float)
    wall = np.tile(mortar, (w, w, 1))
    palette = np.array(STONE, float)

    for y0 in range(0, w, COURSE):
        x = -int(rng.integers(0, BRICK_MAX))    # ragged start, so courses stagger
        while x < w:
            length = int(rng.integers(BRICK_MIN, BRICK_MAX + 1))
            tone = palette[rng.integers(1, len(STONE))] * (0.86 + 0.28 * rng.random())

            for k in range(COURSE - 1):
                # Lit at the top of the brick, falling to its foot.
                v = tone * (1.0 - (k / (COURSE - 1)) * 0.52)
                x0, x1 = max(0, x), min(w, x + length - 1)
                if x1 > x0 and y0 + k < w:
                    wall[y0 + k, x0:x1] = v * (0.97 + 0.06 * rng.random())

            # Chipped corner: a bite out of one end, the way old stone goes.
            if rng.random() < CHIP_CHANCE:
                cx = x if rng.random() < 0.5 else x + length - 2
                cy = y0 if rng.random() < 0.5 else y0 + COURSE - 3
                if 0 <= cx < w - 1 and 0 <= cy < w - 1:
                    # Knocked back to the darkest stone rather than to mortar.
                    # At full mortar the chips read as punched holes -- black
                    # dots scattered over the wall -- instead of as broken edges.
                    wall[cy:cy + 2, cx:cx + 2] = palette[0]

            x += length

    # The bed joint under every course.
    for y0 in range(0, w, COURSE):
        if y0 + COURSE - 1 < w:
            wall[y0 + COURSE - 1, :] = mortar

    # A few cracks wandering across the face. Ruins are cracked; fresh brick is
    # not, and this is the cheapest thing that says which one this is.
    for _ in range(CRACKS):
        cx, cy = float(rng.integers(0, w)), float(rng.integers(0, w))
        ang = rng.random() * 6.283
        for _ in range(int(rng.integers(w // 5, w // 2))):
            if 0 <= cx < w and 0 <= cy < w:
                wall[int(cy), int(cx)] = mortar
            ang += (rng.random() - 0.5) * 0.7
            cx += float(np.cos(ang))
            cy += float(np.sin(ang))

    return np.clip(wall, 0, 255)


def glyph_segments():
    """The rune, as stroke endpoints in 0..1 space.

    Of the same family as the six particle sigils -- straight strokes, hard
    angles, a strong vertical axis -- but drawn with the room a logo has and a
    particle does not. A stem with arms swept up and down from it: something
    grown as well as written, which is what a memory left in stone should be.
    """
    return [
        # The ward: a diamond enclosing everything. Six glyphs were drawn and
        # compared side by side, and this is what the enclosure buys -- a
        # closed, symmetrical outer silhouette that still reads at thirty-two
        # pixels, where an open glyph goes to a smudge. It suits the fiction
        # too: these ruins are *guarded*, and a seal is what a ward looks like.
        ((0.500, 0.065), (0.935, 0.500)),
        ((0.935, 0.500), (0.500, 0.935)),
        ((0.500, 0.935), (0.065, 0.500)),
        ((0.065, 0.500), (0.500, 0.065)),
        # The spirit inside it: a stem with arms swept up and down.
        ((0.500, 0.205), (0.500, 0.795)),
        ((0.500, 0.435), (0.305, 0.265)),
        ((0.500, 0.435), (0.695, 0.265)),
        ((0.500, 0.600), (0.325, 0.750)),
        ((0.500, 0.600), (0.675, 0.750)),
    ]


def distance_field(w):
    """Shortest distance from every pixel to the glyph, in pixels."""
    ys, xs = np.mgrid[0:w, 0:w]
    px = xs + 0.5
    py = ys + 0.5

    best = np.full((w, w), 1e9)
    for (ax, ay), (bx, by) in glyph_segments():
        ax = (0.5 + (ax - 0.5) * INSET) * w
        ay = (0.5 + (ay - 0.5) * INSET) * w
        bx = (0.5 + (bx - 0.5) * INSET) * w
        by = (0.5 + (by - 0.5) * INSET) * w
        vx, vy = bx - ax, by - ay
        L2 = vx * vx + vy * vy
        t = np.clip(((px - ax) * vx + (py - ay) * vy) / L2, 0.0, 1.0)
        dx = px - (ax + t * vx)
        dy = py - (ay + t * vy)
        best = np.minimum(best, np.sqrt(dx * dx + dy * dy))
    return best


def main():
    os.makedirs(OUT, exist_ok=True)
    w = NATIVE

    # --- layer 1: the stonework -------------------------------------------
    # Dropped well down: this is masonry thirty blocks under a graveyard, and
    # the darker the wall the further the rune carries. It is also what the
    # thumbnail needs -- at ninety-six pixels the read is warm light on cold
    # stone, and that contrast is worth more than seeing the brick clearly.
    img = stone_wall(w) * 0.60

    d = distance_field(w)

    # --- layer 2: the chisel groove ---------------------------------------
    # Cut before lighting, so the shadow sits under the glow rather than over
    # it. This is what makes the rune read as carved instead of painted on.
    cut = np.clip((GROOVE - d) / (GROOVE - STROKE), 0.0, 1.0)
    img *= (1.0 - 0.80 * cut)[..., None]

    # --- layer 3: light spilling across the stone -------------------------
    # Banded, because a smooth falloff is the one thing that breaks pixel art.
    #
    # Lighting a whole block at a time was tried -- the game's own model, and it
    # should have been the native-looking answer. At eight blocks square it is
    # far too coarse: four levels over eight cells turned the wall into a
    # patchwork quilt that fought the rune for attention. The grid has to be a
    # lot finer than the light before per-block lighting reads as lighting.
    spill = np.clip(1.0 - (d - STROKE) / REACH, 0.0, 1.0) ** 1.9
    spill = np.ceil(spill * LIGHT_LEVELS) / LIGHT_LEVELS
    img = img + AMBER * spill[..., None] * 0.44

    # --- layer 4: the rune itself -----------------------------------------
    # Two flat colours, no ramp between them. A gradient across a five-pixel
    # stroke reads as a glass tube lit from within -- a neon sign, not a cut in
    # rock. Hard bands are what the particle sigils use and what keeps this in
    # the same idiom.
    lit = d <= STROKE
    core = d <= CORE
    img = np.where(lit[..., None], AMBER, img)
    img = np.where(core[..., None], BONE, img)

    # An ember rim on the outermost pixel of the stroke, so the glyph has a
    # warm edge against the groove rather than stopping dead.
    rim = (d > STROKE) & (d <= STROKE + 1.1)
    img = np.where(rim[..., None], EMBER * 1.15, img)

    # --- layer 5: vignette --------------------------------------------------
    ax = (np.arange(w) + 0.5) / w * 2.0 - 1.0
    gx, gy = np.meshgrid(ax, ax)
    vig = np.clip(1.0 - 0.42 * np.maximum(np.abs(gx), np.abs(gy)) ** 2.6, 0.0, 1.0)
    img *= vig[..., None]

    img = np.clip(img, 0, 255).astype(np.uint8)
    native = Image.fromarray(img, "RGB")

    # The in-game mod icon, at native resolution. Fabric declares it through the
    # `icon` field in fabric.mod.json and ModMenu is what puts it on screen. It
    # happens to want 128, which is exactly what this is drawn at -- so the icon
    # is the artwork itself with no resampling anywhere in the path, which is
    # the only way pixel art stays sharp.
    icon = os.path.join(ICON_DIR, "icon.png")
    os.makedirs(ICON_DIR, exist_ok=True)
    native.convert("RGBA").save(icon)
    print(f"wrote {icon} {native.size}  (native, for ModMenu)")

    # The store listing, scaled up nearest-neighbour for Modrinth and CurseForge.
    im = native.resize((w * SCALE, w * SCALE), Image.NEAREST)

    path = os.path.join(OUT, "logo.png")
    im.save(path)
    print(f"wrote {path} {im.size}  ({NATIVE}px native, {GRID}x{GRID} blocks)")


if __name__ == "__main__":
    main()
