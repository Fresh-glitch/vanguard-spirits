# 1.10.0 — Charms run deeper

The loop used to end. Four charms and a full attunement was about nineteen Fractured Memories, and after that a Guarded Ruin had nothing left to offer — the Sentinel dead, the Reliquary empty, the graveyard quiet.

Charms can now be bound **deeper**, and a ruin you have stripped keeps giving.

## Added

- **Binding Altar** — a new block that binds a charm one depth further. Costs **8** Fractured Memories to reach II and **16** to reach III. One generates in every sanctum's crypt, beneath the passage that describes the first binding, and it can be crafted from deepslate bricks around a memory.
- **Charm depth.** Every charm except the Returned now has three:

| Charm | I | II | III | Attunement |
|---|---|---|---|---|
| **Leaper** | Jump Boost I | Jump Boost II | Jump Boost III | 1 / 2 / 3 |
| **Wanderer** | Speed I | Speed II | Speed III | 1 / 2 / 3 |
| **Delver** | Night Vision | + Haste I | + Haste II | 1 / 2 / 3 |
| **Returned** | Deflection | — | — | 3 |

- A deeper charm **costs more attunement**, so depth is a decision rather than an upgrade: at the cap of four, a charm at III leaves room for exactly one more.
- The Delver widens instead of strengthening, because night vision has only one strength. A deeper binding makes you a better delver rather than a brighter one.
- Depth lives on the charm, so it travels with the item and shows in its name and tooltip.
- **The hollowing.** Emptying a Gilded Reliquary takes the last of what was holding the people above it together. From then on that graveyard gives them back after dark, permanently, and what rises is carrying what is left of a memory. A stripped ruin stops being dead ground and becomes somewhere worth returning to.
- Two advancements: **Deeper Than We Meant** and **Nothing Left to Settle**.

## Fixed

- **A crypt's floor could open in several places, or in none.** The opening was placed from a per-chunk random, and a ruin's crypt always straddles a chunk border — so 67% of ruins got more than one opening and 6% got no way down at all. Now exactly one, every time.
- **A level of the deeps could hold several Remnant spawners, or none.** Same cause: 31% of levels had no spawner. Now exactly one per level, and one hole down.
- The Binding Altar no longer generates standing over the crypt's opening.

## Notes

- The worldgen fixes change where a crypt's opening and a deeps level's spawner sit, so ruins generated after updating will differ from ones already in your world. Existing ruins are unchanged.
- Charms you already own read as depth I and can be taken to an altar.

## Requires

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Fabric Language Kotlin
- Java 25
