# 1.10.1 — The altar, properly cut

A fix and a lot of stonework. Everything here refines what 1.10.0 introduced.

## Fixed

- **Deepening a charm was free if you shift-clicked it.** Taking the result with a shift-click did not consume the Fractured Memories, so charms could be bound to their deepest for nothing. Picking the result up by hand charged correctly, which is why it went unnoticed. **If you deepened charms this way, they cost you nothing** — worth knowing before you judge the balance.
- Both container panels drew their titles in a colour meant for vanilla's pale background, which on this mod's dark stone was very nearly invisible. The **Gilded Reliquary** had been doing this since it was added.

## Changed

- **The Binding Altar is a real altar now.** It was a plain slab with a ring on the lid; it is now cut from eleven stones — a stepped plinth, a waisted shaft, an overhanging table with a raised rim, and corner finials of three heights with the fourth broken clean off. Which corner is bare follows the way you place it.
- **New GUI readout: a binding chain.** The old arrow between the slots was meaningless — there is no cooking time at an altar, so it never moved. In its place is a chain of eight links that closes as you pay, so you can see how near you are to the price without reading the number. The working row now sits in an arched recess.
- **The altar has its own sounds** — one for a hand laid on it, one for a binding taking hold. Each binding rings a step brighter than the last.
- The altar's runes were redrawn. They read as rings cut into stone rather than as flat amber, and the working surface and the shaft now carry the same mark at two scales.

## Notes

- Nothing in this release changes worldgen, so existing ruins and existing charms are unaffected. Altars already placed in the world pick up the new model and sounds.

## Requires

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Fabric Language Kotlin
- Java 25
