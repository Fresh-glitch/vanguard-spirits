# 1.11.1 — The ward holds

**Update if you are on 1.11.0.** The Gilded Reliquary could be mined for its Fractured Memories without fighting the Stone Sentinel at all, which is the one gate the whole mod is built around.

## Fixed

- **A guarded Reliquary could simply be mined.** Tunnel to the vault, break the chest, and its memories dropped on the floor with the Sentinel still standing over you. The ward asked before the chest *opened* and nothing asked before it was *broken*. A sealed Reliquary now cannot be broken at all — hold the button as long as you like — and gives nothing up even if something else removes it. Punching one answers with the same runes it always gave a bare hand, so the refusal is something you can see rather than a block that quietly refuses to break.
- **Taking the whole chest skipped the hollowing.** Mining a Reliquary rather than emptying it left the ruin unchanged, so the graveyard above never woke and no Remnants ever rose — quietly switching off the only renewable source of memories in the game, for a player who thought they were saving a right-click. Carrying the chest away now hollows the ruin exactly as emptying it does.
- **A spare Reliquary could hollow a ruin on its own.** Setting one of your own down inside a ruin and opening it counted as stripping the place, while its real Reliquary still stood full. Only a ruin's own vault can hollow it now.
- **Two Reliquaries side by side shared one lid.** Opening one swung both; opening the other swung neither, though the sound played correctly. Each chest now animates itself. This has been true since the Reliquary was added and only shows when two are in view.
- The Reliquary now shows the usual cracking overlay while it is being mined.

## Changed

- **The Gilded Reliquary needs an iron pickaxe** to harvest. You will not meet this rule until the ward has lifted, by which point iron is long behind you — it marks the vault as worth more than the stone around it rather than gating anything.

## Notes

- Ruins in existing worlds are unaffected and need nothing done to them. A Reliquary you already emptied stays emptied; one still standing guarded is now properly guarded.
- A Reliquary you placed yourself, outside any ruin, behaves as it always did: it is nobody's vault, so nothing refuses you.

## Requires

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Fabric Language Kotlin
- Java 25
