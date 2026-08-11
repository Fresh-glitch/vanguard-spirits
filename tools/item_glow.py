"""The emissive half of an item, shared by every generator that needs one.

A detail on an item burns in the dark by being a **second model** composited
over the ordinary one, carrying a single flat element with `light_emission`.
`FaceBakery` bakes that into the quad's `MaterialInfo` and
`VertexConsumer.putBakedQuad` reads it back, and since item, entity and block
entity rendering all end at that call, one element covers every place the item
can appear: held, dropped, in a frame, lying on a Binding Altar.

It has to be a separate model rather than a second texture layer, because
`light_emission` belongs to an element and `item/generated` builds its elements
itself out of the sprite -- there is nowhere to hang it.

This module exists so the charms and the Fractured Memory cannot disagree about
any of the numbers below. They are the sort that fail quietly: a display
transform a couple of units out shows only when the item is *held*, never in the
inventory, and would be read as the art being wrong rather than the model.
"""

# Copied out of the client jar's own assets/minecraft/models/item/generated.json,
# not written from memory. Each composited model applies its own display
# transforms, so the glowing half has to carry exactly what the plain half gets.
# The left-hand variants are deliberately absent: the loader mirrors the
# right-hand ones when they are missing, which is how vanilla's file does it.
GENERATED_DISPLAY = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}


def glow_model(name, texture=None):
    """A model wearing the lit part of `name`, at full emission.

    `item/generated` extrudes a sprite between z 7.5 and 8.5, so this straddles
    it at 7.4 to 8.6 -- outside on both faces, clear of the plate without a
    visible gap at sixteen pixels. Both faces are drawn because a dropped item
    spins and is seen from behind half the time, and the back one is mirrored in
    u exactly as the generator does for its own.

    [texture] defaults to `<name>_glow`, a sheet holding only the part that
    burns. Pass the item's **own** texture instead when the whole thing glows:
    the overlay then covers the item exactly, and -- worth more than saving a
    file -- an animated item keeps one sheet and one `.mcmeta`, so the burning
    copy cannot drift out of phase with the copy underneath it.

    What stays unlit either way is the one-pixel rim `item/generated` extrudes
    around the silhouette, since this is a flat face and not a box. That is on
    purpose: those side quads follow the sprite's outline pixel by pixel and a
    plain cuboid cannot reproduce them -- it would smear a strip of the texture
    down the edge. A dark hairline at grazing angles is the better trade.
    """
    texture = texture or f"{name}_glow"

    return {
        "gui_light": "front",
        "display": GENERATED_DISPLAY,
        # `particle` is never used by an item model, but leaving it out logs a
        # "Missing texture references" warning on every load -- and a log with
        # harmless warnings in it is a log nobody reads. It points at the plain
        # texture rather than the glow, because if anything ever does sample it,
        # the item's real face is the honest answer and the glow layer is mostly
        # transparent.
        "textures": {
            "glyph": f"vanguard-spirits:item/{texture}",
            "particle": f"vanguard-spirits:item/{name}",
        },
        "elements": [
            {
                "from": [0, 0, 7.4],
                "to": [16, 16, 8.6],
                # No shading: a lit mark has no side facing away from the light.
                "shade": False,
                "light_emission": 15,
                "faces": {
                    "south": {"uv": [0, 0, 16, 16], "texture": "#glyph"},
                    "north": {"uv": [16, 0, 0, 16], "texture": "#glyph"},
                },
            }
        ],
    }
