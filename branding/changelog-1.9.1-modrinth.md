# 1.9.1 — The murals

The ruins now answer the question they have been posing since the first release: who dug this, and why is there a graveyard sitting on the lid.

*Includes everything from 1.9.0, which was not published separately.*

## Added

- **Mural** — a carved deepslate block holding one of **eight passages** of a first-hand account, written by the last of the people who built the ruin.
- Passages are placed **by depth, so descending is the reading order** — two in the mausoleum, then the stairwell, the crypt, both levels of the deeps, and the eighth inside the vault, beside the Reliquary it gives you permission to open.
- Murals are **dark until somebody comes near**, then light and carving ramp up together over four steps.
- Reading one opens a stone panel that also pages back through **every passage you have already found**, so the murals double as a codex you assemble on the way down. It does not pause the game.
- A mural can be mined with any pickaxe and **keeps its passage** — carry one home, place it, and it still carries the same text.
- Two advancements: **In Their Own Hand** and **The Whole Account**.
- Sounds for putting a hand to a mural and for turning a page.

## Fixed

- **Neither the Gilded Reliquary nor a mural dropped anything when mined, to any tool.** The mod shipped with no block tags at all, so the "requires the correct tool" gate could never be satisfied — the block broke at full speed and the drop was simply skipped. The Reliquary had been that way since it was added.
- **The sanctum's corner pillars generated stepped.** Their heights were drawn from a per-chunk random, and because of where the structure sites itself, three of the four corner pillars straddle a chunk border in *every* ruin. Heights now come from the world position, so a pillar is one height however many chunks it spans, and ruins still differ from one another.

## Notes

- Murals only appear in **Guarded Ruins generated after updating**. Existing ruins are already written to disk and will not gain them.

## Requires

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Fabric Language Kotlin
- Java 25
