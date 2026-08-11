"""Art and assets for the Binding Altar: GUI panel, block top, and the JSON.

Built on `make_mural_textures` rather than beside it. The altar and the murals
are the same masonry seen from two sides -- a panel drawn from its own palette
would read as a different mod's block sitting in the same room -- so the wall,
the dressed slab and the chisel bevel all come from there. Only the things that
are genuinely the altar's own live here: the slot wells, the ring on the block
top, and the layout.

## The two things worth knowing

**The slot coordinates are the anvil's.** 27, 76 and 134 on row 47, with the
player inventory at (8, 84) and the hotbar at (8, 142). Those are not a choice
this file gets to make: `ItemCombinerMenu` puts the inventory there itself --
verified in the bytecode, `addStandardInventorySlots(inventory, 8, 84)` -- and
`BindingAltarMenu` names the other three. Change either and the artwork stops
lining up with the slots a player can actually click.

**A slot well is 18x18 and the bevel goes on the outer ring.** Inside the
16x16 the item shifts up-left against its own frame and a row of them reads as
crooked. This is the same rule the Reliquary's panel follows.
"""

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from make_mural_textures import (  # noqa: E402
    AMBER,
    EMBER,
    MOD_MORTAR,
    MOD_STONE,
    bevel,
    dressed_slab,
    mod_masonry,
)

ROOT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources"
ASSETS = os.path.join(ROOT, "assets", "vanguard-spirits")
DATA = os.path.join(ROOT, "data", "vanguard-spirits")

MOD = "vanguard-spirits"

# Vanilla container geometry. The panel sits top-left in a 256x256 sheet.
PANEL_W, PANEL_H = 176, 166
SHEET = 256

SLOT = 18

# Must agree with BindingAltarMenu.slotLayout(). Those are slot *origins*, which
# vanilla draws at origin-1 with an 18x18 well around a 16x16 item.
CHARM_XY = (27, 47)
PAYMENT_XY = (76, 47)
RESULT_XY = (134, 47)

INV_XY = (8, 84)
HOTBAR_Y = 142

# Must agree with BindingAltarScreen.PRICE_Y.
PRICE_Y = 33


def field(w, h, seed):
    """A dressed face light enough for items and dark slots to read against it.

    `dressed_slab` is tuned for the mural, where the only thing on it is bone
    text and the darkness is what makes the carving glow. A container is the
    opposite problem: the slots are the dark shapes, so the face they are cut
    into has to be the lighter one or the whole panel goes flat. Same grain,
    lifted -- keeping the texture is what stops it reading as a painted
    rectangle.
    """
    return np.clip(dressed_slab(w, h, seed=seed).astype(float) * 1.45, 0, 255)


def inlay(panel, x, y, colour):
    """An amber ring set one pixel outside a slot well.

    The Reliquary frames its working grid in gold and leaves the player's own
    inventory plain, which is what tells you at a glance which slots belong to
    the block. The altar says the same thing in its own colour rather than
    inventing a second convention.
    """
    x0, y0 = x - 2, y - 2
    x1, y1 = x0 + SLOT + 2, y0 + SLOT + 2

    panel[y0, x0:x1] = colour
    panel[y1 - 1, x0:x1] = colour
    panel[y0:y1, x0] = colour
    panel[y0:y1, x1 - 1] = colour


def slot_well(panel, x, y, raised=False):
    """One 18x18 well, cut into the face at the item's own position minus one.

    The bevel goes on the outer ring, never inside the 16x16. Inside, every
    item's weight shifts up-left against its own frame and a grid of them reads
    as if it were misaligned.
    """
    x0, y0 = x - 1, y - 1
    x1, y1 = x0 + SLOT, y0 + SLOT

    panel[y0:y1, x0:x1] = np.array(MOD_STONE[0], float) * 0.7
    bevel(
        panel,
        x0, y0, x1, y1,
        np.array(MOD_STONE[5], float),
        np.array(MOD_MORTAR, float),
        inset_dark_top=not raised,
    )


def arrow_between(panel, x, y):
    """The chevron from the payment slot toward the result, in ember.

    Drawn rather than blitted from the mural sheet: that one is 18x11 and points
    left and right for paging, and reusing it would put a page-turn control in
    the middle of a station where nothing pages.

    A shaft plus a head, not a triangle. Seven pixels of solid wedge reads as a
    blob at this size -- the arrow only says *direction* once there is a line for
    the head to sit on the end of.
    """
    ember = EMBER * 0.95
    shade = EMBER * 0.5

    # Shaft, with a darker line under it so it sits on the field rather than
    # floating over it.
    panel[y, x:x + 8] = ember
    panel[y + 1, x:x + 8] = shade

    # Head: four columns narrowing to the point.
    for step in range(4):
        half = 3 - step
        panel[y - half:y + half + 1, x + 8 + step] = ember


def ring(top, cx, cy, radius, colour, strength=1.0):
    """A one-pixel circle, for the binding mark on the block's face."""
    h, w = top.shape[:2]
    for yy in range(h):
        for xx in range(w):
            # Measured from pixel centres. On an even-sided face there is no
            # pixel at the true middle, so using the corner puts the ring half a
            # pixel off and it comes out lopsided.
            d = ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2) ** 0.5
            if abs(d - radius) < 0.55:
                top[yy, xx] = np.array(colour, float) * strength


def build_panel():
    sheet = np.zeros((SHEET, SHEET, 4), np.uint8)

    panel = mod_masonry(PANEL_W, PANEL_H, seed=26_2011)

    dark = np.array(MOD_MORTAR, float)
    lit = np.array(MOD_STONE[5], float)

    # Same frame the mural panel wears, so the two read as one set.
    panel[0, :] = dark
    panel[-1, :] = dark
    panel[:, 0] = dark
    panel[:, -1] = dark
    bevel(panel, 1, 1, PANEL_W - 1, PANEL_H - 1, lit, dark * 1.6, inset_dark_top=False)
    bevel(panel, 4, 4, PANEL_W - 4, PANEL_H - 4, np.array(MOD_STONE[2], float), dark, inset_dark_top=True)

    # The working face: one dressed field holding the title, the price line and
    # the three slots. Same reasoning as the mural panel -- a price over brick
    # courses is exactly as hard to read as a paragraph over them.
    x0, y0, x1, y1 = 7, 14, PANEL_W - 7, 70
    panel[y0:y1, x0:x1] = field(x1 - x0, y1 - y0, seed=44_2026)
    bevel(panel, x0, y0, x1, y1, np.array(MOD_STONE[5], float), dark, inset_dark_top=True)

    # The inventory gets its own field rather than sitting on bare masonry. Wells
    # cut straight into brick courses have mortar lines running through them and
    # the grid stops reading as a grid.
    ix0, iy0 = INV_XY[0] - 2, INV_XY[1] - 2
    ix1, iy1 = ix0 + 9 * SLOT + 4, HOTBAR_Y + SLOT + 1
    panel[iy0:iy1, ix0:ix1] = field(ix1 - ix0, iy1 - iy0, seed=51_2026)
    bevel(panel, ix0, iy0, ix1, iy1, np.array(MOD_STONE[5], float), dark, inset_dark_top=True)

    # What you bring and what you pay are cut in; what you take is set proud and
    # ringed brighter, so the row reads left to right as give, pay, receive.
    for xy in (CHARM_XY, PAYMENT_XY):
        inlay(panel, *xy, EMBER)
        slot_well(panel, *xy)

    inlay(panel, *RESULT_XY, AMBER)
    slot_well(panel, *RESULT_XY, raised=True)

    arrow_between(panel, PAYMENT_XY[0] + SLOT + 4, RESULT_XY[1] + 8)

    for row in range(3):
        for col in range(9):
            slot_well(panel, INV_XY[0] + col * SLOT, INV_XY[1] + row * SLOT)
    for col in range(9):
        slot_well(panel, INV_XY[0] + col * SLOT, HOTBAR_Y)

    sheet[0:PANEL_H, 0:PANEL_W, :3] = panel.astype(np.uint8)
    sheet[0:PANEL_H, 0:PANEL_W, 3] = 255
    return sheet


def build_top():
    """The altar's working surface: dressed stone with a bound ring cut in it."""
    top = dressed_slab(16, 16, seed=9_2026).astype(float)

    ring(top, 8.0, 8.0, 5.5, MOD_MORTAR, 0.8)
    ring(top, 8.0, 8.0, 4.5, AMBER, 0.55)

    # Four notches on the axes, so the mark reads as something made rather than
    # as a stain. Placed off the ring itself, which would just thicken it.
    for dx, dy in ((0, -7), (0, 6), (-7, 0), (6, 0)):
        top[8 + dy, 8 + dx] = np.array(EMBER, float) * 0.8

    out = np.zeros((16, 16, 4), np.uint8)
    out[:, :, :3] = np.clip(top, 0, 255).astype(np.uint8)
    out[:, :, 3] = 255
    return out


def write_png(path, array):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(array, "RGBA").save(path)
    return path


def write_json(path, payload):
    import json

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    return path


SIDE = "minecraft:block/deepslate_bricks"


def write_assets():
    """Blockstate, block model, item model and loot table.

    The altar is a 14x12x14 slab rather than a cube, so it needs a real model
    with an element. Sides are vanilla deepslate brick, referenced not copied --
    the altar is built out of the ruin's own masonry, and referencing keeps a
    Mojang texture out of the jar.
    """
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{MOD}:block/binding_altar_top",
            "top": f"{MOD}:block/binding_altar_top",
            "side": SIDE,
        },
        "elements": [
            {
                "from": [1, 0, 1],
                "to": [15, 12, 15],
                "faces": {
                    "down": {"texture": "#side", "cullface": "down"},
                    "up": {"texture": "#top"},
                    "north": {"texture": "#side"},
                    "south": {"texture": "#side"},
                    "west": {"texture": "#side"},
                    "east": {"texture": "#side"},
                },
            }
        ],
    }
    write_json(os.path.join(ASSETS, "models", "block", "binding_altar.json"), model)

    # FACING exists so the block can be placed to face the player, but the altar
    # is symmetric about its own axis, so every facing shows the same model. The
    # variants still have to be listed: a blockstate value nobody generated
    # renders as a missing model, which is the word "Unknown" in the log.
    write_json(
        os.path.join(ASSETS, "blockstates", "binding_altar.json"),
        {
            "variants": {
                f"facing={facing}": {"model": f"{MOD}:block/binding_altar", **({"y": rot} if rot else {})}
                for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
            }
        },
    )

    write_json(
        os.path.join(ASSETS, "items", "binding_altar.json"),
        {"model": {"type": "minecraft:model", "model": f"{MOD}:block/binding_altar"}},
    )

    write_json(
        os.path.join(DATA, "loot_table", "blocks", "binding_altar.json"),
        {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1,
                    "entries": [{"type": "minecraft:item", "name": f"{MOD}:binding_altar"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }
            ],
        },
    )


def check_layout():
    """The panel is only correct if it agrees with the menu and the screen.

    Parsed out of the Kotlin rather than trusted, for the same reason the mural
    generator checks its counts: nothing else connects these two files, and a
    disagreement shows up as slot art that is one row off -- which looks like a
    drawing mistake, not like two numbers that stopped matching.
    """
    src = os.path.join(
        r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\kotlin\io\github\freshglitch\vanguardspirits",
        "menu",
        "BindingAltarMenu.kt",
    )
    with open(src, encoding="utf-8") as handle:
        kotlin = handle.read()

    for label, (x, y) in (
        ("charm", CHARM_XY),
        ("payment", PAYMENT_XY),
        ("result", RESULT_XY),
    ):
        needle = f"{x}, {y}"
        assert needle in kotlin, (
            f"{label} slot is drawn at {needle} but BindingAltarMenu does not place a slot there"
        )

    screen = os.path.join(
        r"P:\ClaudeMods\vanguard-spirits-26.2\src\client\kotlin\io\github\freshglitch\vanguardspirits\client",
        "screen",
        "BindingAltarScreen.kt",
    )
    with open(screen, encoding="utf-8") as handle:
        assert f"PRICE_Y = {PRICE_Y}" in handle.read(), (
            f"the price line is drawn clear of y={PRICE_Y} here but the screen writes it elsewhere"
        )


def main():
    check_layout()

    panel = write_png(
        os.path.join(ASSETS, "textures", "gui", "container", "binding_altar.png"),
        build_panel(),
    )
    top = write_png(
        os.path.join(ASSETS, "textures", "block", "binding_altar_top.png"),
        build_top(),
    )
    write_assets()

    print(f"wrote {panel}")
    print(f"wrote {top}")
    print("wrote blockstate, block model, item model and loot table")


if __name__ == "__main__":
    main()
