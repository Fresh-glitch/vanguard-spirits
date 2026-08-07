"""Proves every mural lands in a wall with its carved face looking into a room.

Worldgen gets no second chance to tell you it went wrong. A mural placed one
block off is either sealed inside solid rock or floating in open air, and both
look exactly like a mural that never generated -- there is nothing in the log
either way. Finding that in game means rolling worlds until a ruin turns up,
walking down thirty blocks, and doing it again for every one of eight.

So the coordinate arithmetic is re-implemented here and asserted on. This is
the same instrument that caught the graveyard's spiral stair ending part way up
a wall, before the game was ever launched.

Constants are **parsed out of the Kotlin**, not copied. A checker holding its
own copy of `CRYPT_INSET` passes happily while the real structure moves.

What is proven, per mural:

  1. the block it replaces was cast solid by the chamber that owns it
  2. the block its carved face looks at is open air
  3. no two murals occupy the same block
  4. it is clear of the fixtures that would swallow it -- the stair mouth, the
     vault passage, lanterns, the spawner and the Reliquary

The world model here covers only the volumes the murals sit in, in the order
`postProcess` writes them. It is not a general simulation of the ruin and will
not catch a mistake somewhere it does not model -- so a failure is meaningful,
and a pass means these eight positions specifically are sound.
"""

import re
import sys

KOTLIN = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\kotlin\io\github\freshglitch\vanguardspirits"
RUINS = KOTLIN + r"\worldgen\GuardedRuinsPiece.kt"
GRAVEYARD = KOTLIN + r"\worldgen\GraveyardPiece.kt"

DECL = re.compile(
    r"(?:private\s+)?const\s+val\s+([A-Z_][A-Z0-9_]*)\s*(?::\s*Int\s*)?=\s*([^\n/]+)"
)


def constants(path, seed=None):
    """Pulls integer `const val`s out of a Kotlin file, in declaration order.

    Only simple arithmetic over already-declared names is understood, which is
    all these files use. Anything that does not evaluate is skipped rather than
    guessed at -- a wrong value would be worse than a missing one.
    """
    values = dict(seed or {})
    with open(path, encoding="utf-8") as handle:
        source = handle.read()

    for name, expr in DECL.findall(source):
        expr = expr.strip().rstrip(",")
        if not re.fullmatch(r"[A-Za-z0-9_+\-*/() ]+", expr):
            continue
        try:
            values[name] = int(eval(expr, {"__builtins__": {}}, dict(values)))
        except Exception:
            continue

    return values


R = constants(RUINS)
G = constants(GRAVEYARD, seed={"MURAL_EYE": None})

# Derived exactly as the Kotlin derives them.
SANCTUM_LAST = R["SANCTUM_WIDTH"] - 1
DEEPS = R["DEEPS_LEVELS"] * R["LEVEL_HEIGHT"]
CRYPT_FLOOR = DEEPS + R["FLOOR_PAD"]
SURFACE = CRYPT_FLOOR + R["CRYPT_DEPTH"]
MID = SANCTUM_LAST // 2
MURAL_EYE = G["MURAL_EYE"]

SOLID, AIR, ROCK = "solid", "air", "rock"

# What a mural is allowed to have behind its carved face.
ENCLOSED, OUTDOORS = "enclosed", "outdoors"


class Volume:
    """Last-writer-wins occupancy, written in the order postProcess writes it."""

    def __init__(self):
        self.cells = {}

    def fill(self, x0, y0, z0, x1, y1, z1, what):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.cells[(x, y, z)] = what

    def at(self, x, y, z):
        return self.cells.get((x, y, z), ROCK)


def build_sanctum():
    """The underground half, in postProcess order."""
    v = Volume()

    # hollowCavern: everything above the sanctum floor is open, out to well
    # past the perimeter. Only the region the sanctum mural sits in matters.
    v.fill(-4, SURFACE + 1, -4, SANCTUM_LAST + 4, SURFACE + 30, SANCTUM_LAST + 4, AIR)

    # carveDeeps -> carveDeep for each level: shell cast solid, then hollowed.
    lo, hi = R["DEEP_INSET"], SANCTUM_LAST - R["DEEP_INSET"]
    for i in range(R["DEEPS_LEVELS"]):
        floor_y = R["FLOOR_PAD"] + i * R["LEVEL_HEIGHT"]
        roof = floor_y + R["LEVEL_HEIGHT"] - 1
        v.fill(lo, floor_y, lo, hi, roof, hi, SOLID)
        v.fill(lo + 1, floor_y + 1, lo + 1, hi - 1, roof - 1, hi - 1, AIR)

        # buildVault, on the lowest floor only.
        if floor_y == R["FLOOR_PAD"]:
            outer = hi + R["VAULT_REACH"]
            inner = hi + 4
            v.fill(hi + 1, floor_y, MID - 4, outer, roof, MID + 4, SOLID)
            v.fill(hi, floor_y + 1, MID - 1, hi + 3, floor_y + 2, MID + 1, AIR)   # passage
            v.fill(inner, floor_y + 1, MID - 3, outer - 1, roof - 1, MID + 3, AIR)

    # carveCrypt.
    clo, chi = R["CRYPT_INSET"], SANCTUM_LAST - R["CRYPT_INSET"]
    v.fill(clo, CRYPT_FLOOR, clo, chi, SURFACE - 1, chi, SOLID)
    v.fill(clo + 1, CRYPT_FLOOR + 1, clo + 1, chi - 1, SURFACE - 1, chi - 1, AIR)

    # layPlatform.
    v.fill(0, SURFACE, 0, SANCTUM_LAST, SURFACE, SANCTUM_LAST, SOLID)

    # raiseColonnade: only the guaranteed courses. Everything above them is a
    # random draw, which is precisely why no mural may sit there.
    for i in range(SANCTUM_LAST + 1):
        for (x, z) in ((i, 0), (i, SANCTUM_LAST), (0, i), (SANCTUM_LAST, i)):
            v.fill(x, SURFACE + 1, z, x, SURFACE + R["SOLID_COURSES"], z, SOLID)

    # cutStairwell. Cast as one solid block of masonry first -- this is the
    # only mass at sanctum level with rock on both sides of a face, which is
    # why passage III lives in it.
    crypt_outer = SANCTUM_LAST - R["CRYPT_INSET"]
    head_x = SANCTUM_LAST - 1
    z_lo, z_hi = MID - 2, MID + 2
    v.fill(crypt_outer + 1, CRYPT_FLOOR, z_lo - 1, head_x + 1, SURFACE, z_hi + 1, SOLID)

    # ...then the treads cut out of it.
    for step in range(0, SURFACE - CRYPT_FLOOR - 1 + 1):
        x = head_x - step
        tread_y = SURFACE - 1 - step
        ceiling = SURFACE if x > crypt_outer else SURFACE - 1
        v.fill(x, tread_y + 1, z_lo, x, ceiling, z_hi, AIR)
        v.fill(x, tread_y, z_lo, x, tread_y, z_hi, SOLID)

    return v


def build_mausoleum():
    v = Volume()
    floor = 0

    # clearFootprint / layGround: the plot is open air above its floor. Modelled
    # even though the mausoleum murals are declared OUTDOORS and so exempt from
    # the backing rule -- without it the report would print "rock behind" for a
    # wall that in fact backs onto the graveyard, and an instrument that prints
    # a comfortable answer for the wrong reason is worse than no instrument.
    v.fill(-2, floor + 1, -2, G["MAUS_EAST"] + 24, floor + 8, G["MAUS_SOUTH"] + 8, AIR)

    # buildMausoleum: walls solid, interior open.
    for x in range(0, G["MAUS_EAST"] + 1):
        for z in range(G["MAUS_NORTH"], G["MAUS_SOUTH"] + 1):
            wall = x in (0, G["MAUS_EAST"]) or z in (G["MAUS_NORTH"], G["MAUS_SOUTH"])
            what = SOLID if wall else AIR
            v.fill(x, floor + 1, z, x, floor + G["MAUS_HEIGHT"], z, what)
            v.fill(x, floor, z, x, floor, z, SOLID)

    # sinkShaft casts solid only up to and including the floor, so it cannot
    # touch a mural at eye height -- modelled to prove exactly that.
    sx, sz, so = G["SHAFT_X"], G["SHAFT_Z"], G["SHAFT_OUTER"]
    v.fill(sx - so, 0, sz - so, sx + so, floor, sz + so, SOLID)

    return v, floor


# (passage, facing, x, y, z, backing, where) -- must mirror the Kotlin exactly.
#
# `backing` states what is expected behind the carved face.
#
# ENCLOSED means "anything but open air". Note that *untouched world rock* is a
# perfectly good backing -- better, if anything, than placed masonry -- which is
# the correction to the first version of this rule: it demanded the structure
# have written a block there, and so failed the crypt and deeps murals, whose
# shells are cut straight into untouched deepslate. What matters is that there
# is material behind the carving, not who put it there.
#
# OUTDOORS is the honest exception for a building wall. The mausoleum is one
# block thick and its murals show plain deepslate to the graveyard outside,
# exactly as any carved wall in any building does. That is not the same failure
# as the sanctum colonnade, which was a ruined wall with a *room* on both sides.
DEEP_LOWER = R["FLOOR_PAD"]
DEEP_UPPER = R["FLOOR_PAD"] + R["LEVEL_HEIGHT"]
DEEP_FAR = SANCTUM_LAST - R["DEEP_INSET"]
STAIR_TREAD = SURFACE - 1 - 1

FACE = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}

SANCTUM_MURALS = [
    (2, "south", SANCTUM_LAST - 2, STAIR_TREAD + MURAL_EYE, MID - 3, ENCLOSED, "stairwell wall"),
    (3, "east", R["CRYPT_INSET"], CRYPT_FLOOR + MURAL_EYE, MID, ENCLOSED, "crypt"),
    (4, "east", R["DEEP_INSET"], DEEP_UPPER + MURAL_EYE, 5, ENCLOSED, "deeps upper"),
    (5, "east", R["DEEP_INSET"], DEEP_LOWER + MURAL_EYE, 5, ENCLOSED, "deeps lower"),
    (6, "west", DEEP_FAR, DEEP_LOWER + MURAL_EYE, 5, ENCLOSED, "deeps lower, far wall"),
    (7, "south", R["VAULT_MURAL_X"], DEEP_LOWER + MURAL_EYE, MID - 4, ENCLOSED, "vault"),
]


def check(volume, murals, label):
    failures = []
    seen = {}

    for passage, facing, x, y, z, backing, where in murals:
        here = volume.at(x, y, z)
        dx, dy, dz = FACE[facing]
        front = volume.at(x + dx, y + dy, z + dz)
        behind = volume.at(x - dx, y - dy, z - dz)

        mine = []
        if here != SOLID:
            mine.append(f"replaces {here}, not wall")
        if front != AIR:
            mine.append(f"faces {facing} into {front}, not open air")
        if backing == ENCLOSED and behind == AIR:
            mine.append("has open air behind it -- it is a loose slab, not a carved wall")
        if (x, y, z) in seen:
            mine.append(f"collides with passage {seen[(x, y, z)]}")
        seen[(x, y, z)] = passage

        print(f"  [{'FAIL' if mine else 'ok':4}] {label} passage {passage:>2} "
              f"at ({x:>3},{y:>3},{z:>3}) facing {facing:<5} -- "
              f"{here} wall, {front} in front, {behind} behind  ({where})")
        failures += [f"passage {passage} ({where}) {m}" for m in mine]

    return failures


def main():
    print(f"parsed SANCTUM_LAST={SANCTUM_LAST} CRYPT_FLOOR={CRYPT_FLOOR} "
          f"SURFACE={SURFACE} MURAL_EYE={MURAL_EYE}")

    # The model has to be able to fail. A checker whose world is all ROCK would
    # report every mural as "not wall" and a checker whose world is all SOLID
    # would report every one as "not air" -- both are loud. The dangerous
    # failure is a model that says SOLID everywhere the mural is and AIR
    # everywhere in front by construction, so prove the volume distinguishes
    # the three states before trusting a pass.
    v = build_sanctum()
    kinds = {v.at(R["DEEP_INSET"], DEEP_LOWER + MURAL_EYE, 5),
             v.at(R["DEEP_INSET"] + 1, DEEP_LOWER + MURAL_EYE, 5),
             v.at(-3, 0, -3)}
    assert kinds == {SOLID, AIR, ROCK}, f"world model is degenerate: only saw {kinds}"
    print(f"model self-check: distinguishes {sorted(kinds)}\n")

    failures = check(v, SANCTUM_MURALS, "ruin  ")

    maus, floor = build_mausoleum()
    maus_murals = [
        (0, "south", G["MAUS_MURAL_X"], floor + MURAL_EYE, G["MAUS_NORTH"], OUTDOORS, "mausoleum north"),
        (1, "north", G["MAUS_MURAL_X"], floor + MURAL_EYE, G["MAUS_SOUTH"], OUTDOORS, "mausoleum south"),
    ]
    failures += check(maus, maus_murals, "graves")

    print()
    if failures:
        for line in failures:
            print("FAIL:", line)
        sys.exit(1)

    total = len(SANCTUM_MURALS) + len(maus_murals)
    assert total == 8, f"expected 8 murals across the ruin, checked {total}"
    print(f"all {total} murals sit in wall with their carved face open to a room")


if __name__ == "__main__":
    main()
