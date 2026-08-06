# Vanguard Spirits

Under the deepslate lie ruins that were never abandoned. Something still keeps them.

Vanguard Spirits adds a two-part structure to the Overworld, three mobs that live in it, and a charm system built on a budget you have to spend carefully. The ruins are guarded, the guardian does not bluff, and everything you carry out of one is worth more than the last thing you carried out.

## In short

- **Guarded Ruins** — a graveyard on the surface and a sanctum buried thirty blocks beneath it, joined by one spiral stair. Built in earth, sand or snow to match where it generates.
- **Three mobs** — a stone guardian that holds the vault, the dead who rise out of the graves, and a bird that circles overhead and tells you a ruin is there at all.
- **Four charms** granting Jump Boost, Speed, Night Vision, and a Deflection effect that turns projectiles away before they reach you.
- **Attunement** — charms work from anywhere in your inventory, but you can only keep so many live at once. The cap starts at 1 and reaches 4, and one charm costs three of it on its own.
- Nine advancements, custom sounds, and a sealed container the guardian is the key to.

Everything below is the detail, including the boss fight and the loot. Open it if you would rather know before you go.

<details>
<summary><b>Full details — structures, mobs, stats and loot (spoilers)</b></summary>

## Finding a ruin

A **Guarded Ruin** is two structures joined by one hole in the ground.

On the surface there is a **graveyard** — a walled plot of grave mounds and a mausoleum, laid in stone that matches its surroundings. It builds in earth, sand or snow depending on where it generates, so a ruin in a desert is sandstone and a ruin in a taiga is under snow.

Thirty blocks below sits the **sanctum**, in a cavern the structure hollows out for itself. A spiral stair inside the mausoleum is the only way down.

You find these by looking up. **Mourners** — large dark birds — circle over a ruin and nowhere else, holding a slow orbit well above the treeline. A shape turning over a hillside means something is under it. They will drop to take a closer look at whoever is standing on the graves, and they roost in nearby trees between passes.

Very rarely a Mourner spawns wild in forest and dark forest, walking on the ground rather than circling. That one is just a bird, so a Mourner on foot is not proof of anything.

## The graveyard

Grave mounds are not loot. Opening one wakes what is under it, and a **Remnant** climbs out — fast, brittle, and already moving by the time you see it. There are twenty-eight graves in a plot, so the surface half of a ruin is a fight you choose the pace of.

Graves are a placeable block. The ones worldgen lays are occupied; the ones you place are not, so carrying a stack home does not give you a portable spawner.

## Below

The sanctum holds spawners, a soul-lit altar, and a **Stone Sentinel** standing on it. It sleeps until you come down the stair.

The Sentinel is the mod's boss and is built to be fought at close range:

- **150 health, 15 armour, 8 armour toughness, immune to knockback**
- **Slam** — 14 damage in a 5-block radius, throws you up and back
- **Sweep** — 9 damage in a 4-block arc, on a shorter cooldown
- **Reckoning** — 20 damage, and hauls you in from up to 16 blocks away
- **Sunder** — breaks through what you put between you and it
- **Gyre** — a spin that absorbs half the damage of incoming arrows
- **Bulwark** — if you shoot it from somewhere it genuinely cannot reach, it shuts itself into a shell instead. Arrows come off it and the stone knits back together faster than a bow can open it. It is a refusal, not an attack: you are told the tower will not work, and left to come down or leave. When you go, it walks back to its altar and waits.

The **Gilded Reliquary** in the vault holds the Fractured Memories, and it **will not open while its Sentinel is alive**. Tunnelling straight to the vault gets you a sealed chest.

## Charms and attunement

**Fractured Memories** are the raw material, and a Reliquary holds four to eight of them. A charm costs four Memories in a cross, four Deepslate Bricks at the corners, and a core in the middle that matches the spirit being bound:

```
[Deepslate Brick]  [Fractured Memory]  [Deepslate Brick]
[Fractured Memory]      [ core ]       [Fractured Memory]
[Deepslate Brick]  [Fractured Memory]  [Deepslate Brick]
```

| Charm | Effect | Core ingredient | Attunement cost |
|---|---|---|---|
| Charm of the Leaper | Jump Boost | Rabbit's Foot | 1 |
| Charm of the Wanderer | Speed | Mourner's Feather | 1 |
| Charm of the Delver | Night Vision | Golden Carrot | 1 |
| Charm of the Returned | Deflection | Shield | **3** |

Charms work **from anywhere in your inventory** — no equip slot, no curios dependency. You do not wear them, you carry them.

What limits you is **attunement**. You start with **1** and it caps at **4**. Charms are applied in inventory order until the budget runs out, and vanilla numbers the hotbar first — so you set priority by moving charms to your hotbar. A charm that will not fit is skipped rather than stopping the ones below it.

**Deflection** is why the Charm of the Returned costs three of your four. It turns away projectiles heading for you — arrows, tridents, ghast fireballs — by looking a tick ahead at what is about to cross your path, so nothing has to hit you first for it to work. At the cap it leaves room for exactly one other charm, which makes taking it a decision about what you give up.

**Echoes of Kinship** raise the cap by one each. One drops from a Stone Sentinel half the time, so deeper attunement means more ruins.

Any charm can be **hushed** — sneak and use — which switches it off and gives up its place in the queue, so a charm further down takes the attunement instead. The setting lives on the charm, not on you.

Hovering a charm tells you whether it is Attuned or Dormant and what it costs.

## Items

- **Fractured Memory** — the ruins' currency. Its light drifts through the four charms' colours because it has not decided yet what it will become; the name in the tooltip shifts with it.
- **Mourner's Feather** — shed when a Mourner is startled off a perch or shot at without being killed. Killing one ends its ruin's vigil permanently and the birds do not come back, so flushing the same bird repeatedly is the better trade. It also works anywhere vanilla wants a feather: arrows, books and quills, brushes, firework stars.
- **Echo of Kinship** — deep-ruin loot, consumed to deepen attunement.
- **Gilded Reliquary** — a ward-sealed container with its own interface.
- **Grave** — a mound with an occupant, or without one if you placed it.

Remnants drop rotten flesh, and rarely a handful of nuggets, flint, an amethyst shard, or a worn iron tool.

## Also included

- Nine advancements covering the whole progression, from finding a ruin to reaching full attunement
- Three spawn eggs
- Custom sounds for the Mourner, the Remnant, the graves and the charms
- Works on servers; attunement is saved, survives death, and is synced only to its owner

</details>

---

**Requires Fabric API and Fabric Language Kotlin.**

Built for the *Echoes of the Past* modjam.
