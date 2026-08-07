"""Blockstate, models, item model and loot table for the mural.

A generator rather than eight hand-written files plus a thirty-two entry
blockstate, because that is what the mural's two properties multiply out to:
four facings times eight passages. Hand-maintaining that is a transcription
job, and a transcription job with thirty-two nearly identical lines is one
where a wrong digit is invisible in review and shows up in game as a single
mural wearing the wrong glyph.

Block assets in this mod live in `src/main/resources` and are written by hand
-- only item models, lang, recipes and advancements come out of `runDatagen`.
This keeps that layout, and keeps the thirty-two variants reproducible: run the
script, commit what it emits.

Everything here is derived from `PASSAGES`, which must match `MuralLore.COUNT`.

## The two things worth knowing

**The carved face is the model's north face**, and the blockstate rotates it.
`MuralBlock.getStateForPlacement` stores `horizontalDirection.opposite` -- the
direction the front points -- so `facing=north` needs the model unrotated, and
east/south/west take y=90/180/270. Same convention the Gilded Reliquary uses.

**The loot table copies `passage` into the item.** `BlockItem` applies
`DataComponents.BLOCK_STATE` when it places, so `copy_state` is the entire
mechanism for a mural carried home keeping its text -- no block entity, no
custom item, no code. Note `facing` is deliberately *not* copied: which way a
mural points is a fact about the wall it was in, and a player placing one wants
it facing them.
"""

import json
import os

MOD = "vanguard-spirits"
ROOT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources"

ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data", MOD)

# Must equal MuralLore.COUNT. Asserted against the Kotlin source below rather
# than trusted, because the two files have no other connection and a mismatch
# would surface as a missing model for the passage nobody thought to place.
PASSAGES = 8

# Steps of the wake-on-approach glow, from MuralBlockEntity.MAX_GLOW. This does
# not change the model at all -- light emission is read off the blockstate by
# the light engine, not drawn -- but every combination still has to be listed
# or the game logs a missing variant and renders the block untextured.
GLOW_STEPS = 4

# Vanilla's own deepslate brick, referenced rather than copied. The mural
# generates set into deepslate walls, so its sides should simply *be* the wall
# -- and referencing keeps a Mojang texture out of our jar, which matters for a
# jam entry.
SIDE = "minecraft:block/deepslate_bricks"

FACING_ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}


def write(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")
    return path


SRC = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\kotlin\io\github\freshglitch\vanguardspirits"


def declared_int(path, marker):
    """Reads one `const val` out of a Kotlin file."""
    with open(path, encoding="utf-8") as handle:
        source = handle.read()
    at = source.index(marker) + len(marker)
    return int(source[at:source.index("\n", at)].strip())


def check_counts_match_kotlin():
    """Fails loudly if this script and the Kotlin have drifted apart.

    Both numbers multiply into the blockstate, and a shortfall does not fail
    the build -- it produces a block that renders untextured for exactly the
    property values nobody generated, which in the glow case means a mural that
    turns into a missing-model cube the moment a player walks up to it.
    """
    passages = declared_int(
        os.path.join(SRC, "lore", "MuralLore.kt"), "const val COUNT: Int = "
    )
    assert passages == PASSAGES, (
        f"MuralLore.COUNT is {passages} but this script generates {PASSAGES} passages"
    )

    max_glow = declared_int(
        os.path.join(SRC, "block", "entity", "MuralBlockEntity.kt"),
        "const val MAX_GLOW: Int = ",
    )
    assert max_glow + 1 == GLOW_STEPS, (
        f"MuralBlockEntity.MAX_GLOW is {max_glow} (so {max_glow + 1} steps) "
        f"but this script generates {GLOW_STEPS}"
    )


def block_models():
    """One model per passage *and glow step*: 8 x 4.

    The carving brightens with the light the block emits, so the face is a
    different texture at every step and each needs its own model. Only the north
    face changes -- the other five are vanilla deepslate at every step, since
    the sides of the slab are not what is lit.
    """
    for passage in range(PASSAGES):
        for glow in range(GLOW_STEPS):
            face = f"{MOD}:block/mural_{passage}_{glow}"
            write(
                os.path.join(ASSETS, "models", "block", f"mural_{passage}_{glow}.json"),
                {
                    "parent": "minecraft:block/cube",
                    "textures": {
                        # Break particles come off the carved face, which is the
                        # one the player is looking at when they break it.
                        "particle": face,
                        "north": face,
                        "south": SIDE,
                        "east": SIDE,
                        "west": SIDE,
                        "up": SIDE,
                        "down": SIDE,
                    },
                },
            )


def blockstate():
    variants = {}
    for passage in range(PASSAGES):
        for facing, rotation in FACING_ROTATION.items():
            for glow in range(GLOW_STEPS):
                entry = {"model": f"{MOD}:block/mural_{passage}_{glow}"}
                if rotation:
                    entry["y"] = rotation
                # Property order must match the game's, which sorts by the order
                # the properties were added to the state definition: FACING,
                # PASSAGE, GLOW.
                variants[f"facing={facing},passage={passage},glow={glow}"] = entry

    expected = PASSAGES * len(FACING_ROTATION) * GLOW_STEPS
    assert len(variants) == expected, f"expected {expected} variants, built {len(variants)}"

    write(os.path.join(ASSETS, "blockstates", "mural.json"), {"variants": variants})
    return expected


def item_model():
    """The held item shows the passage it will actually place.

    The first version hardcoded passage 0, so every mural mined out of a ruin
    looked like the dedication in the inventory and then placed as whatever it
    really was. The passage *is* on the stack -- the loot table copies it into
    `minecraft:block_state` -- so the item model can simply select on it. This
    is the same mechanism vanilla uses for the copper golem statue's pose,
    which is where the schema below was read from rather than guessed at.

    Shown at full glow. A mural in a wall is dark until somebody walks up to
    it, but an inventory slot has no such thing as a nearby player, and the
    whole point of this fix is that the rune is identifiable in the slot.
    """
    lit = GLOW_STEPS - 1

    write(
        os.path.join(ASSETS, "items", "mural.json"),
        {
            "model": {
                "type": "minecraft:select",
                "property": "minecraft:block_state",
                "block_state_property": "passage",
                "cases": [
                    {
                        "when": str(passage),
                        "model": {
                            "type": "minecraft:model",
                            "model": f"{MOD}:block/mural_{passage}_{lit}",
                        },
                    }
                    for passage in range(PASSAGES)
                ],
                # A stack with no block_state component at all -- one from the
                # creative tab, or /give without the component.
                "fallback": {
                    "type": "minecraft:model",
                    "model": f"{MOD}:block/mural_0_{lit}",
                },
            }
        },
    )


def loot_table():
    write(
        os.path.join(DATA, "loot_table", "blocks", "mural.json"),
        {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1,
                    "entries": [
                        {
                            "type": "minecraft:item",
                            "name": f"{MOD}:mural",
                            "functions": [
                                {
                                    "function": "minecraft:copy_state",
                                    "block": f"{MOD}:mural",
                                    "properties": ["passage"],
                                }
                            ],
                        }
                    ],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }
            ],
        },
    )


def main():
    check_counts_match_kotlin()

    block_models()
    variants = blockstate()
    item_model()
    loot_table()

    print(
        f"wrote {PASSAGES * GLOW_STEPS} block models, {variants} blockstate variants, "
        f"a {PASSAGES}-case item model and the loot table"
    )


if __name__ == "__main__":
    main()
