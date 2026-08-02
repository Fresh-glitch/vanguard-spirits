"""Generate the grave block textures.

Two faces: the turned earth on top and the cut soil at the sides.

The top has to be readable as *disturbed* from standing height, which plain
noise is not -- scattered clods on flat dirt look like coarse dirt and a player
walks over it. So the mound carries a raked texture: short diagonal furrows
running the length of the plot, the way earth looks when it has been shovelled
back in rather than settled. Two pale slivers show through, which at 16x16 is as
much bone as will read without turning into confetti.

Everything is lit from the upper left, matching the item set, and the deep
shadows carry a slight violet cast so the block belongs to the same palette as
the echo runes rather than to vanilla's dirt family.
"""
import os
import struct
import zlib

OUT = r"P:\ClaudeMods\vanguard-spirits-26.2\src\main\resources\assets\vanguard-spirits\textures\block"

SIZE = 16

# Deterministic value noise. random.seed would do, but a hash keeps the texture
# identical whatever Python version regenerates it.
def noise(x, y, salt):
    h = (x * 73_856_093) ^ (y * 19_349_663) ^ (salt * 83_492_791)
    h &= 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1_274_126_177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65535.0


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def clamp(v):
    return max(0, min(255, round(v)))


def shade(colour, amount):
    """Lighten or darken, letting shadow drift violet and highlight drift warm."""
    if amount >= 0:
        warm = (colour[0] + 14, colour[1] + 8, colour[2] + 2)
        return tuple(clamp(warm[i] + (255 - warm[i]) * amount * 0.55) for i in range(3))
    cool = (colour[0], colour[1] - 2, colour[2] + 8)
    return tuple(clamp(cool[i] * (1.0 + amount * 0.75)) for i in range(3))


SOIL_DARK = (48, 36, 30)
SOIL_MID = (69, 52, 40)
SOIL_LIGHT = (92, 72, 54)
BONE = (176, 168, 146)
ROOT = (58, 44, 28)

# Where the two bone slivers surface, and the furrow phase, hand placed rather
# than drawn from the noise so they land somewhere deliberate.
BONES_TOP = [(4, 5), (5, 5), (10, 10), (10, 11)]
BONES_SIDE = [(3, 9), (12, 6)]


def grave_top():
    px = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            # Furrows run diagonally so neither the block grid nor the plot's
            # long axis lines up with them; a straight rake reads as corduroy.
            furrow = ((x + y * 2) % 5) / 4.0
            grain = noise(x, y, 1)
            clod = noise(x // 2, y // 2, 2)

            base = mix(SOIL_DARK, SOIL_MID, clod)
            base = mix(base, SOIL_LIGHT, grain * 0.45)

            # The furrow's near wall catches the light, the trough loses it.
            lit = (furrow - 0.5) * 0.42
            colour = shade(base, lit)

            if (x, y) in BONES_TOP:
                colour = mix(BONE, colour, 0.18)
            elif grain > 0.93:
                colour = ROOT

            row.append(colour)
        px.append(row)
    return px


def grave_side():
    px = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            grain = noise(x, y, 3)
            clod = noise(x // 2, y // 3, 4)

            # Cut earth is packed and darker the further down it goes, with the
            # top two rows the crumbling lip of the mound.
            depth = y / (SIZE - 1.0)
            base = mix(SOIL_MID, SOIL_DARK, depth * 0.7)
            base = mix(base, SOIL_LIGHT, grain * 0.34)

            colour = shade(base, 0.10 if y < 2 else -0.05 + grain * 0.14)

            if (x, y) in BONES_SIDE:
                colour = mix(BONE, colour, 0.3)
            elif clod > 0.95:
                colour = shade(SOIL_LIGHT, 0.12)

            row.append(colour)
        px.append(row)
    return px


def write_png(path, px):
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *px[y][x], 255) for x in range(SIZE))
        for y in range(SIZE)
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as fh:
        fh.write(png)
    print(f"wrote {path} ({len(png)} bytes)")


os.makedirs(OUT, exist_ok=True)
write_png(os.path.join(OUT, "grave_top.png"), grave_top())
write_png(os.path.join(OUT, "grave_side.png"), grave_side())
