# 1.13.0 — Somebody who was there

## Added

- **The Nymph.** Everything else in this mod is evidence. Murals are what people wrote down, Remnants are what is left of them, a Sentinel is a machine still obeying an order nobody ever rescinded, and the graves are the people themselves. All of it is past tense. The Nymph is not — she watched the ruin get built, she watched it fail, and she is standing in a flower forest several centuries later with opinions about it.

  She is **uncommon** and she lives only in flower forests — which are uncommon themselves, so most of the work of making her a find is done by having to locate one at all. Once you have found her she never despawns, because a mob you can lose by walking home for supplies is not a mob you can have a relationship with.

- **She will talk to you, and she is paying attention.** Use an empty hand on a Nymph who has nothing against you and she says something. It is not a bag of fortune cookies: half of what she says is a read of the world in front of her, so she remarks on the rain when it is raining, on the dark when it is dark, on the axe in your hand, on the fact that you are bleeding, and on the Fractured Memory you are carrying — *"you are carrying a piece of somebody. I knew the hands that made it."*

  The other half is a thread that advances, one step per conversation, and it is the mod's story told by the only witness still alive to tell it. She adds no facts you cannot find on a wall thirty blocks underground. What she adds is that somebody is saying them out loud.

  Two players talking to the same Nymph each get the whole of it from the beginning.

- **She keeps accounts, and she is watching the grove.** Kill an animal, fell a tree or pick a flower near a Nymph and she notices — she does not need line of sight, because she is not a guard with eyes, she is the thing the wood belongs to. What she minds most is the killing, because it is the only one of the three that does not grow back.

  Four flowers earns a warning. Two logs earns a warning. One dead sheep earns a warning on its own, and two earns a **fight** — she roots you where you stand with heavy slowness rather than hitting harder, so the trouble with angering her is not the damage, it is that leaving is a decision instead of a reflex.

  Her grievance is hers, not the world's. Walk to the next flower forest and the Nymph there has no opinion of you yet, because she has not seen anything.

- **You can make it right.** She cools off on her own — about eight minutes from the top of her temper to nothing — or you can hand her a flower or a sapling, which is worth a great deal more. Offer one to a Nymph who has nothing against you and she gives something back instead.

  Amends genuinely end it. A Nymph you have brought all the way back down stops fighting the moment she says so, rather than finishing the swing she was already on. Hit her again and you are back where you started.

  **She drops nothing when killed.** The same rule the ruin's Mourners follow, for the same reason: the moment the rare thing pays out, shooting it becomes the correct play and the encounter it was built for never happens.

- **She runs when she comes for you.** She has two gaits and they are two
  separate hand-built animations, not one played faster. The walk is unhurried
  and deliberate — she rises rather than bobs, and places each foot rather than
  dropping it. The run is a hunt: she pitches forward into it, her stride more
  than doubles, her knees fold twice as far, her elbows lock into a right angle
  and pump instead of swinging, her hair and the torn hem of her sash stream
  behind her, and her hips drop under her at each stride instead of only
  rising.

  The two are mixed rather than switched, so there is no frame where one gait
  ends and the other begins — over about a fifth of a second she gathers
  herself up out of the walk and into the run. What decides it is whether she
  has actually settled on somebody, so a Nymph who is merely angry still walks,
  and one standing over you swinging is in the run without taking a stride.

- **A Nymph Spawn Egg**, in the creative tabs alongside the other three.

## Changed

- **The Unfinished Epitaph reads as an arch now.** It was a shaft with a block
  on top, and a squared shoulder at that size looks like a mistake rather than a
  monument. It is cut as a proper arch instead, tapering over three steps from a
  ten-wide plinth to a four-wide crown.

  Getting there meant building it rather than calculating it. A version that
  copied the item sprite's profile row for row was checked box by box against
  the collision shape, every row proven equal — and in the world it looked
  *identical* to the one it replaced, because that sprite spends only two of its
  fourteen rows on the dome, and two sixteenths of a block is a step however
  many steps you cut into it.

- **And it is drawn in a ramp you can see.** Its six shades sat 14 to 16
  luminance apart, close enough that the eye reads them as a single flat tone,
  and every one of them was the same hue with the saturation running *backwards*
  — so the stone had no light direction at all. It now carries four shades at
  least 25 apart, with the cold travelling through saturation rather than hue: a
  saturated blue in the shadow, near-neutral at the highlight.

  The block and the item are weighted differently out of that one ramp, because
  a face in the world is shaded by the game and a sprite in a slot is not. The
  placed marker used to come out at 110 mean brightness — pale limestone among
  grave mounds that measure 64 and 69. It now sits at 66, with them.

## Notes on how she is built

She carries a second model layer, the same way a player skin carries a hat, a jacket and sleeves — the same boxes drawn again slightly larger, from their own region of her sheet, with everything the garment does not cover left transparent. That gives her a hood over the back of her hair, a mantle across her shoulders, vine wraps at her forearms, a torn fringe below the sash, and moss growing up her ankles.

She is properly jointed underneath it: elbows, knees, ankles, a waist and a neck, sixteen bones in all. Her knees only fold one way, her ankles keep her soles level with the ground as she strides, her torso counter-twists against the leading leg, and a look is shared between her neck and her head so she leans into it rather than swivelling a skull on a post.

## Notes for the curious

Every colour she is drawn in was measured out of the biome rather than chosen. Her limbs are birch bark, her crown and hair are allium, and her sash is oak leaves multiplied by the flower forest's own foliage tint of `#59AE30` — leaves ship as a near-grey sheet and are coloured at draw time, so sampling the texture on its own gives an olive that appears nowhere in the world.

## Requires

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API
- Fabric Language Kotlin
- Java 25
