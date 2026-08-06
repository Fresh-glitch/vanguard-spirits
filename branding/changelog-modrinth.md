# 1.8.2 — Initial release

First public release, built for the **Echoes of the Past** modjam.

*The version number carries over from development — 1.8.2 is the eighteenth internal build and the first one published.*

## Worldgen

- **Guarded Ruins** — a new Overworld structure in two halves: a walled graveyard on the surface and a sanctum buried thirty blocks below it, in a cavern the structure hollows out for itself. A spiral stair inside the mausoleum is the only way between them.
- Graveyards build in **earth, sand or snow** to match the biome they generate in.
- Twenty-eight grave mounds per plot, each with an occupant.

## Mobs

- **Stone Sentinel** — the ruin's guardian. 150 health, 15 armour, immune to knockback. Slam, Sweep, a Reckoning that hauls you in from 16 blocks, a Sunder that breaks through cover, a Gyre that absorbs half the damage from arrows, and a Bulwark it shells into when attacked from somewhere it cannot reach. Returns to its altar when the fight ends.
- **Remnant** — fast and brittle, rises from disturbed graves and from spawners in the sanctum.
- **Mourner** — circles above a ruin as a landmark visible from a distance, roosts in nearby trees, and sheds a feather when startled. Killing one ends that ruin's vigil permanently. Spawns wild, very rarely, in forest and dark forest.

## Items

- **Fractured Memory** — the ruins' currency, with an animated light that drifts through the four charms' colours and a tooltip name that shifts with it.
- **Charm of the Leaper** (Jump Boost), **Charm of the Wanderer** (Speed), **Charm of the Delver** (Night Vision), **Charm of the Returned** (Deflection).
- **Echo of Kinship** — consumed to raise your attunement cap.
- **Mourner's Feather** — shed rather than dropped, and usable anywhere vanilla wants a feather: arrows, books and quills, brushes, firework stars.
- Spawn eggs for all three mobs.

## Blocks

- **Gilded Reliquary** — a ward-sealed container with its own interface, which will not open while its Sentinel is alive.
- **Grave** — placeable; the ones worldgen lays are occupied, the ones you place are not.

## Systems

- **Attunement** — charms apply from anywhere in your inventory, in slot order, until the budget runs out. Starts at 1, caps at 4. Charms that do not fit are skipped rather than blocking the ones below them. Saved, survives death, and synced only to its owner.
- **Hushing** — sneak and use to switch a charm off and give up its place in the queue. The setting lives on the charm.
- **Deflection** — a custom effect that turns away incoming projectiles by looking a tick ahead, so nothing has to hit you first.

## Other

- Nine advancements covering the whole progression, from finding a ruin to reaching full attunement.
- Custom sounds for the Mourner, the Remnant, the graves and the charms.
- Custom particles for the ruins, the Reliquary and the Sentinel waking.
- Works on dedicated servers.

## Requires

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Fabric Language Kotlin
- Java 25
