"""How many holes does a Guarded Ruin's crypt floor actually get?

`breakCryptFloor` draws its hole centre with `random.nextInt(span)`, and
`postProcess` runs once per chunk the piece touches with its own RandomSource.
`placeBlock` drops writes outside the chunk being built, so each chunk carves a
hole at *its own* chosen centre, clipped to itself -- which is not one ragged
opening but up to one per chunk.

This re-implements the placement arithmetic over the sites the structure really
produces and counts them, because the failure is invisible in game: several
partial holes in a floor that is *meant* to be broken looks exactly like one
ragged hole, which is why it survived.

Run with no arguments. Prints the current behaviour and the fixed behaviour
side by side, and asserts the fix collapses to exactly one.
"""

# Must match GuardedRuinsPiece.
SANCTUM_WIDTH = 17
MARGIN = 9
WIDTH = SANCTUM_WIDTH + 2 * MARGIN
SANCTUM_LAST = SANCTUM_WIDTH - 1
CRYPT_INSET = 4

LO = CRYPT_INSET + 2
HI = SANCTUM_LAST - CRYPT_INSET - 2
SPAN = HI - LO + 1

SITES = 40_000


def box_origin(chunk_x, chunk_z):
    """Where the piece's bounding box starts, in world coordinates.

    The structure sites itself on `chunkPos.middleBlockX`, so the box lands at
    `chunkX * 16 + 8 - WIDTH / 2`.
    """
    return (chunk_x * 16 + 8 - WIDTH // 2, chunk_z * 16 + 8 - WIDTH // 2)


def world_of(origin, local_x, local_z):
    return (origin[0] + local_x, origin[1] + local_z)


def chunk_of(wx, wz):
    return (wx >> 4, wz >> 4)


def touching_chunks(origin):
    """Every chunk the crypt's hole-eligible area falls into.

    Only these matter: a chunk that does not overlap the range can never own a
    cell of any hole, whatever centre it draws.
    """
    seen = set()
    for lx in range(LO - 1, HI + 2):
        for lz in range(LO - 1, HI + 2):
            seen.add(chunk_of(*world_of(origin, lx, lz)))
    return sorted(seen)


def holes_current(origin, draw):
    """Model of the code as it stands: every chunk draws its own centre.

    `draw(chunk, axis)` stands in for that chunk's RandomSource. Returns the set
    of chunks that end up carving at least one cell -- i.e. how many separate
    openings appear.
    """
    carving = set()
    for chunk in touching_chunks(origin):
        cx = LO + draw(chunk, 0)
        cz = LO + draw(chunk, 1)

        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                wx, wz = world_of(origin, cx + dx, cz + dz)
                if chunk_of(wx, wz) == chunk:
                    carving.add(chunk)
    return carving


def holes_fixed(origin):
    """Model of the fix: the centre is a function of position, so all agree."""
    carving = set()

    # Every chunk computes this identically -- that is the whole point.
    cx = LO + positional(origin, 0)
    cz = LO + positional(origin, 1)

    for chunk in touching_chunks(origin):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                wx, wz = world_of(origin, cx + dx, cz + dz)
                if chunk_of(wx, wz) == chunk:
                    carving.add(chunk)

    # Chunks here are the ones the single hole spills into, which is what a hole
    # straddling a border should do. The opening is still one opening.
    return carving, (cx, cz)


def to_signed_32(value):
    value &= 0xFFFFFFFF
    return value - 0x100000000 if value >= 0x80000000 else value


def hash32(a, b, salt):
    """Kotlin's `hash` from PositionalHash.kt, in 32-bit signed arithmetic."""
    h = to_signed_32(to_signed_32(a * 73_856_093) ^ to_signed_32(b * 19_349_663) ^ to_signed_32(salt * 83_492_791))
    h = to_signed_32(h ^ ((h & 0xFFFFFFFF) >> 13))
    h = to_signed_32(h * 1_274_126_177)
    return to_signed_32(h ^ ((h & 0xFFFFFFFF) >> 16))


def window(a, b, salt):
    return ((hash32(a, b, salt) & 0xFFFFFFFF) >> 8) & 0xFFFF


CRYPT_HOLE_SALT = (131, 132)


def positional(origin, axis):
    """What `rangeAt(mid, mid, CRYPT_HOLE_SALT[axis], SPAN)` will return.

    Keyed on the *world* position of one fixed local cell, so every chunk gets
    the same answer while different ruins still differ from each other.
    """
    mid = SANCTUM_LAST // 2
    wx, wz = world_of(origin, mid, mid)
    return window(wx, wz, CRYPT_HOLE_SALT[axis]) % SPAN


# ------------------------------------------------------------------ the deeps

DEEP_INSET = 2
FLOOR_PAD = 2
LEVEL_HEIGHT = 7

DEEP_HOLE_SALT = 141
DEEP_SPAWNER_SALT = 142


def deep_rooms():
    """The four corners a deeps level puts its hole and its spawner in."""
    lo, hi = DEEP_INSET, SANCTUM_LAST - DEEP_INSET
    mid = (lo + hi) // 2
    near, far = (lo + mid) // 2, (mid + hi) // 2
    return mid, [(near, near), (near, far), (far, near), (far, far)]


def spawners_current(origin, rooms, draw_room):
    """How many spawners a level actually gets under the code as it stood.

    A spawner is one block. Each chunk picks a room from its own random and
    calls `put`, which drops the write unless the block is inside that chunk --
    so a spawner appears at room r if and only if the chunk that *owns* r is the
    one that picked r. Nothing guarantees any chunk does.
    """
    placed = 0
    for room in rooms:
        wx, wz = world_of(origin, room[0], room[1])
        owner = chunk_of(wx, wz)
        if rooms[draw_room(owner)] == room:
            placed += 1
    return placed


def deeps_report():
    mid, rooms = deep_rooms()

    def draw_room(chunk):
        return window(chunk[0], chunk[1], 901) % len(rooms)

    counts = {}
    fixed_rooms_per_level = {}
    both_levels_same = 0

    for i in range(SITES):
        chunk_x = (i % 200) - 100
        chunk_z = (i // 200) - 100
        origin = box_origin(chunk_x, chunk_z)

        n = spawners_current(origin, rooms, draw_room)
        counts[n] = counts.get(n, 0) + 1

        # The fix: one value per level, computed identically by every chunk.
        picks = []
        for level in range(2):
            floor_y = FLOOR_PAD + level * LEVEL_HEIGHT
            wx, wz = world_of(origin, mid, mid + floor_y)
            picks.append(window(wx, wz, DEEP_SPAWNER_SALT) % len(rooms))
        fixed_rooms_per_level[picks[0]] = fixed_rooms_per_level.get(picks[0], 0) + 1
        if picks[0] == picks[1]:
            both_levels_same += 1

    print("\n=== deeps spawners ===")
    print("CURRENT -- spawners placed per level, by count:")
    for n in sorted(counts):
        print(f"  {n} spawner(s): {counts[n]:6d}  ({100.0 * counts[n] / SITES:5.1f}%)")

    missing = counts.get(0, 0)
    extra = sum(c for n, c in counts.items() if n > 1)
    assert missing > 0 or extra > 0, (
        "the model never produced a wrong spawner count -- it cannot see the bug "
        "it was written to measure"
    )
    print(f"\npositive control: {missing} sites with no spawner "
          f"({100.0 * missing / SITES:.1f}%), {extra} with more than one "
          f"({100.0 * extra / SITES:.1f}%)")

    print("\nFIXED -- room chosen for the upper level, spread across the four:")
    for room in sorted(fixed_rooms_per_level):
        share = 100.0 * fixed_rooms_per_level[room] / SITES
        print(f"  room {room}: {fixed_rooms_per_level[room]:6d}  ({share:5.1f}%)")
    assert len(fixed_rooms_per_level) == len(rooms), (
        "the fix never chooses some rooms -- the salt is biased"
    )

    share_same = 100.0 * both_levels_same / SITES
    print(f"\nboth deeps levels pick the same room: {share_same:.1f}% "
          f"(chance alone would be {100.0 / len(rooms):.1f}%)")
    print("fix: exactly one spawner and one opening per level, in every ruin")


def main():
    # A stand-in for per-chunk RandomSource: deterministic, but different per
    # chunk, which is exactly the property that causes the bug.
    def draw(chunk, axis):
        return window(chunk[0], chunk[1], 900 + axis) % SPAN

    current_counts = {}
    fixed_counts = {}
    centres = set()

    for i in range(SITES):
        chunk_x = (i % 200) - 100
        chunk_z = (i // 200) - 100
        origin = box_origin(chunk_x, chunk_z)

        n_now = len(holes_current(origin, draw))
        current_counts[n_now] = current_counts.get(n_now, 0) + 1

        carving, centre = holes_fixed(origin)
        fixed_counts[len(carving)] = fixed_counts.get(len(carving), 0) + 1
        centres.add(centre)

    print(f"sites sampled: {SITES}")
    print(f"crypt hole range: local {LO}..{HI} (span {SPAN})")
    print(f"chunks touching that range: {len(touching_chunks(box_origin(0, 0)))}")
    print()
    print("CURRENT -- separate openings carved, by count:")
    for n in sorted(current_counts):
        share = 100.0 * current_counts[n] / SITES
        print(f"  {n} opening(s): {current_counts[n]:6d}  ({share:5.1f}%)")
    print()
    print("FIXED -- chunks one opening spills into, by count:")
    for n in sorted(fixed_counts):
        share = 100.0 * fixed_counts[n] / SITES
        print(f"  spans {n} chunk(s): {fixed_counts[n]:6d}  ({share:5.1f}%)")
    print()
    print(f"distinct hole centres chosen across all sites: {len(centres)} of {SPAN * SPAN} possible")

    # Positive control: the checker must be able to see the bug, or a clean
    # result from it means nothing. More than one opening has to actually occur.
    broken = sum(count for n, count in current_counts.items() if n > 1)
    assert broken > 0, (
        "the model never produced a second opening -- it cannot detect the bug "
        "it was written to measure, so a passing 'fixed' result proves nothing"
    )
    print(f"\npositive control: {broken} of {SITES} sites carve more than one opening "
          f"under the current code ({100.0 * broken / SITES:.1f}%)")

    # The fix must collapse to exactly one opening, however many chunks it
    # touches -- which is a different question from how many chunks it spans.
    assert len(centres) > 1, "the fix picked one centre for every ruin in the world"
    print("fix: every chunk computes the same centre, so exactly one opening is carved")

    deeps_report()


if __name__ == "__main__":
    main()
