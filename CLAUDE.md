# Vanguard Spirits

Minecraft **Fabric 26.2** mod in **Kotlin**, built for a CurseForge modjam themed
**"Echoes of the Past"**. Mod id `vanguard-spirits`, group
`io.github.freshglitch.vanguardspirits`.

Core mechanic: **Guarded Ruins** yield **Fractured Memories**, which are crafted
into **charms** that grant passive auras.

A ruin is two pieces of one structure: the sanctum, buried in a cavern it
hollows for itself, and a **graveyard** on the surface directly above, whose
mausoleum holds the only stair down. Mourners circling overhead are what marks
it from a distance.

## Build and run

Always drive the game through **Gradle**:

```bash
./gradlew runClient
```

`runServer` and `runDatagen` likewise. `./gradlew build` for the jar.

**Never run the mod through a VS Code `"request": "launch"` config.** The Java
extension (JDT) does not compile `.kt` — it copies Kotlin sources into `bin/` as
plain resources, so the game starts with no entrypoint classes and Loader fails
the mod. `.vscode/launch.json` is therefore `"request": "attach"` configs backed
by background tasks running `--debug-jvm` (suspends on port 5005). **Re-running
`./gradlew vscode` overwrites `launch.json` with the broken launch configs** —
restore it from git if that happens.

Debugging Kotlin line-by-line is not possible in VS Code (no Kotlin debug adapter
plugs into JDT). Java mixin breakpoints work fine.

## Source sets

Split via `splitEnvironmentSourceSets()`. This is enforced, not stylistic:
calling client code on a dedicated server crashes it.

- `src/main/` — common. Registries, items, game logic, server ticking.
- `src/client/` — client only. Rendering, tooltips, anything touching
  `Minecraft.getInstance()`. **Datagen also lives here** (`configureDataGeneration { client = true }`).
- `src/main/generated/` — datagen output. **Committed**; its `.cache/` is not.

Assets (textures) go in `src/main/resources/assets/vanguard-spirits/`.

## Finding API: docs first, javap second

26.2 is **past the assistant knowledge cutoff** and its mappings are neither Yarn
nor quite the older official set. Do not write code against remembered API.

1. **Check the docs**: <https://docs.fabricmc.net/develop/> — the site is version
   selected and currently serves 26.2. Its examples match this version exactly.
2. **Then confirm with `javap`** for anything the docs do not cover.

Export the dev classpath once per session, then read real signatures:

```bash
./gradlew -q -I tools/cp.gradle printClientCp
```

`tools/cp.gradle` is a tracked init script that registers `printClientCp`. It
only ever runs when passed with `-I`, so it has no effect on an ordinary build.

Then: `javap -cp "<that classpath>" net.minecraft.world.item.Item`. Use the JDK 25
`javap` at `%JAVA_HOME%\bin\javap.exe`, not the JRE on `PATH`.

## Measure it; the guess is usually wrong

The rule above generalises, and it is the single biggest time saver in this
project. **When something behaves unexpectedly, the first plausible explanation
has been wrong nearly every time.** Not vaguely wrong either — wrong in a way
that still explains the symptom perfectly, which is what makes it so expensive.
A worked list from this repo:

- Arms vanished into the torso. The code said `-SHOULDER_ROLL` and the comment
  beside it said "roll out", so reading the code confirmed the wrong answer.
  One Blockbench export settled it: `z = +90` on `arm_right` emits
  `zRot = 1.5708`, and four shipped animations had been inverted for months.
- The Sentinel walked into walls. Obvious cause: bad pathfinding weights. Actual
  cause: `NodeEvaluator.prepare` sizes with `Mth.floor`, so a 2.6 block mob is
  planned for as 2.
- The Sentinel would not walk home. Obvious cause: one bug. Actual cause: two
  independent ones, and fixing either alone changed nothing.
- Datagen hung for ten minutes. Obvious cause: a bad provider. Actual cause: a
  stale daemon — 2.3 s of CPU over 13 minutes said *blocked*, not *computing*.
- The Bulwark would not trigger from a tower. Three mechanisms were theorised
  from bytecode, all plausible, none right. One temporary `LOGGER.info` printed
  `stalled=20` and the real cause was visible immediately.
- The graveyard's spiral stair was going to end part way up a wall, for every
  ruin whose depth was not a multiple of the ring. Nothing about the code looked
  wrong and a playthrough would have blamed the exit carving. Forty lines of
  Python re-implementing the ring arithmetic found it before the game was ever
  launched — and then found the *second* one, where carving the whole well left
  seven open cells to fall down at the entrance.
- A Sentinel woke through a wall the moment the player reached the bottom of that
  stair. The obvious reading is that the shaft is simply too close to the
  Sentinel. It is not: `watch()` measures from `wardPos` — the stairwell it
  guards, seven blocks from the altar it stands on — so what mattered was the
  distance to something the Sentinel was not standing on.
- The Fractured Memory's name was tied to its animated texture by counting client
  ticks, on the stated grounds that sprites tick once per client tick. They do
  not. `TextureManager.tick` is called from `Minecraft.**runTick**`, not
  `Minecraft.tick` — once per rendered *frame*, guarded by a game tick being due
  and the level running normally. This one is worth reading twice, because of
  *how* it was got wrong: the method-scoped `javap | awk` that was supposed to
  prove the caller returned nothing, and rather than fix the query the search was
  widened to the whole class, the call found, and **the expected enclosing method
  written down instead of the real one**. A failed instrument is not a licence to
  fall back on the guess; it is the point at which the instrument gets fixed.

So reach for the instrument early, not after the third theory:

| Question | Instrument |
| --- | --- |
| What does this API actually do? | `javap -c` on the dev classpath |
| Which way does this bone rotate? | pose it in Blockbench, read the Java export |
| What is the server actually seeing? | temporary `LOGGER.info`, ask the player to trigger it |
| Is this process working or stuck? | `jstack`, and process CPU time |
| Is this `.ogg` the right length? | `ffprobe`, or granule position over sample rate |
| Is this PNG intact? | walk the chunk list; `file` will happily bless a truncated one |
| Does this generated geometry close up? | re-implement the coordinate maths in a throwaway script and assert on it, before generating a world |
| Is this block set into a wall, or floating in one? | model the chamber's fills in the order `postProcess` writes them, then assert solid-behind and air-in-front |
| Which method contains this call? | disassemble to a file and walk back to the last signature line — a `grep` over the whole class finds the call and tells you nothing about its caller |

Four corollaries worth their own line. **A comment is not evidence** — several in
this repo confidently described the opposite of what the code did. **Silence
is not confirmation**: when the Bulwark worked, the log went quiet, which looks
identical to the feature never running. Decide in advance what success will
*emit*.

Third, and the most expensive: **a passing playtest can confirm a wrong
mechanism.** The name-to-texture sync was checked in game, looked perfect, and
was built on a false premise — because above twenty frames a second the wrong
clock and the right clock tick at the same rate. The test and the bug shared a
condition, so the test could only ever agree. Before treating a green run as
proof, ask what the test would have to look like *if the mechanism were wrong*;
if the answer is "the same", it has confirmed nothing. Here the distinguishing
conditions were a pause and a frame rate under twenty, and both had to be
provoked deliberately.

Fourth, and the one that keeps recurring: **an instrument can fail in a way that
looks like a result.** Every case below happened in a single session, and in
every one the broken tool produced output that read as an answer:

- A method-scoped `javap | awk` matched nothing, so the search was widened to the
  whole class. It found the call and said nothing about the caller — and the
  expected method got written down instead of the real one.
- A probe logging `bulwark up` tested `bulwarkTick == 1`, but `beginBulwark`
  already sets it to 1 and `advanceBulwark` increments before the check. It could
  never fire, so a working shell looked like a shell that never went up.
- A filter for "spawns outside the allowed biomes" anchored on `$`, which the
  log's ANSI reset codes broke. It matched nothing and printed everything, under
  a heading that said finding nothing meant the tag held.
- A `cmd1 && cmd2` chain silently skipped the compile, because `grep -c` exits 1
  when it finds zero matches — and zero matches was the *good* outcome.
- `./gradlew clientClasses | tail -30 && echo COMPILE-OK` printed **COMPILE-OK
  under a failing build**. In a pipeline the exit status is the *last* command's,
  and `tail` always succeeds. Use `set -o pipefail`, or read `${PIPESTATUS[0]}`.
- A log watcher filtering for player chat with `<[A-Za-z0-9_]+>` matched
  `CrashReport.<init>` and buried the real messages under stack frames. Anchor
  on `\[CHAT\]`.
- A watcher was armed to catch missing block models, and its pattern listed
  `missing` but not `Unknown` — which is the word Minecraft actually uses for a
  blockstate variant nobody generated. It would have sat silent through exactly
  the failure it was put there for.
- Two greps in a pipeline, and only the *first* had `--line-buffered`. The
  second buffered in 4 KB blocks, so a watch that was working perfectly reported
  nothing for a whole seventeen-minute play session.

So: check what the instrument said before believing what it meant. A filter that
matches nothing and a filter that is broken are the same empty output; a probe
that never fires and a condition that never happened are the same silence. The
cheap guard is to make the instrument prove itself — run it once against a case
you know is positive, or have it print totals that must add up, which is what
finally settled the biome question.

## 26.2 API gotchas

Each of these cost a compile cycle or a wrong guess:

- `net.minecraft.resources.**Identifier**` replaces `ResourceLocation`.
  `ResourceKey` kept its name. Everything else is Mojang-official layout.
- Items need their id **before** construction:
  `Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))`, then
  `Registry.register(BuiltInRegistries.ITEM, key, item)`. There are **no**
  `Items.register*` helpers.
- Creative tabs: `net.fabricmc.fabric.api.creativetab.v1.**FabricCreativeModeTab**.builder()`
  for a custom tab. `FabricItemGroup` (in most tutorials) does not exist here.
  To also place items in *vanilla* tabs, use
  `CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)`.
  This mod does both — see `ModItemGroups`.
- `Item.appendHoverText` is **deprecated** — prefer a default `DataComponents.LORE`
  / `ItemLore` component. Vanilla renders LORE purple italic, which suits the theme.
- `AttachmentRegistry.builder()` is **deprecated** — use
  `AttachmentRegistry.create(id) { builder -> ... }`.
- `Player.displayClientMessage` does **not** exist. Use `sendOverlayMessage`
  (action bar) or `sendSystemMessage` (chat).
- `MobEffects` constants are `SPEED` / `HASTE`, not `MOVEMENT_SPEED` / `DIG_SPEED`,
  and are `Holder<MobEffect>`, not raw `MobEffect`.
- **Vanilla bounces a projectile that failed to damage its target, *after* the
  damage hook returns.** `AbstractArrow.onHitEntity` scales its velocity by
  `-0.1` and flips the yaw, so any velocity or owner set from inside the hook is
  overwritten a moment later. Queue the projectile and act on the next tick,
  where you get the last word. The same trap hides a second bug: read the speed
  inside the hook too, because by the next tick the only speed left is a tenth
  of what arrived.
- **Deflect a hurting projectile through `Projectile.deflect`, not by assigning
  a velocity.** Fireballs carry an `accelerationPower` and re-derive motion from
  it, so one given only a new delta turns and then accelerates itself straight
  back round. `deflect` runs `onDeflection`, which rescales it.
- **Spawn eggs are no longer a tinted template.** Every vanilla egg in 26.2 is
  its own 16x16 painting under `textures/item/`, referenced by a plain
  `item/generated` model with no tints, and they are illustrated with the mob's
  features — bat ears, parrot crest, ravager horns. There is nothing to tint;
  draw one.
- **`ItemEntity.setUnlimitedLifetime()` freezes `age` at -32768**, because
  `tick` stops incrementing once it holds that value. Anything timed off `age`
  — a bob, a repeating sound — stops dead the moment it is called. Use
  `level.gameTime`, which also survives a reload.
- **`Item.getName(stack)` is not a hook for naming an item — it is the *reader*
  of `DataComponents.ITEM_NAME`.** Vanilla's body is one line:
  `stack.getComponents().getOrDefault(ITEM_NAME, EMPTY)`. Overriding it to
  return a fresh `translatable(descriptionId)` therefore throws away every
  per-stack rename — `/give …[item_name=…]`, a loot table's `set_components`,
  anything a data pack set — and silently shows the default. Build on `super`
  instead. Worth knowing what routes through it: `ItemStack.getItemName` calls
  it, `getHoverName` calls that, so one override reaches the tooltip title *and*
  the hotbar popup. And `getStyledHoverName` appends the result as a **child** of
  a parent styled with the rarity colour, so a colour set on the child survives —
  rarity does not overwrite it.
- **Sprite animations do not advance once per client tick.** `TextureManager.tick`
  runs from `Minecraft.runTick`, once per rendered *frame*, and only when a game
  tick is due and `isLevelRunningNormally()`. So anything that has to stay in
  step with an animated texture cannot count `ClientTickEvents` — below twenty
  frames a second several ticks land in one frame, and a pause stops the sprite
  while client ticks keep arriving. Ride `TextureManager.tick` itself with a
  mixin and the guards come for free. Reading the live frame is not an option:
  `SpriteContents.AnimationState.frame` is private, and the states live in a
  private list on `TextureAtlas` rather than on the sprite, so there is nothing
  to reach from a `TextureAtlasSprite`.
- **A sound cannot be stopped by the level.** `ClientboundStopSoundPacket` is
  per listener, so everyone in earshot has to be told individually.
- **Sound volume above 1 is also its reach**: a variable-range event carries
  `16 * volume` blocks. There is no way to make something loud and near-field,
  or quiet and far-carrying.
- **A dropped item's behaviour needs a mixin.** Fabric can say when an entity
  enters the world but not when one ticks, and the behaviour belongs to the
  `ItemEntity` rather than the `Item`. See `ItemEntityMixin`, which is kept to
  an injection point and calls straight out to Kotlin.
- **`requiresCorrectToolForDrops()` without a `mineable/*` tag means the block
  drops nothing, to anything, ever.** `ServerPlayerGameMode.destroyBlock` only
  calls `playerDestroy` — the method that rolls the loot table — when
  `Player.hasCorrectToolForDrops` passes, and that is two lines:
  `!state.requiresCorrectToolForDrops() || held.isCorrectToolForDrops(state)`.
  The second resolves through the held item's `TOOL` component, whose rules are
  all written against **block tags**, so a block in no tag matches no rule and a
  diamond pickaxe is worth exactly as much as a bare hand. Nothing reports it:
  the loot table is valid, the block breaks at the right speed, the drop is just
  skipped. The Gilded Reliquary shipped like this from the day it was added and
  it was only noticed when a second block did the same thing.
- **`CustomPacketPayload.createType(String)` claims the `minecraft` namespace.**
  Its body is `new Type<>(Identifier.withDefaultNamespace(s))`, so the
  convenient-looking `createType("mural_open")` registers `minecraft:mural_open`.
  Use the `Type` constructor with our own `Identifier`.
- **`DirectionProperty` no longer exists** — `HorizontalDirectionalBlock.FACING`
  is an `EnumProperty<Direction>`.
- **A block's light level is a function of `BlockState` and nothing else.**
  `Properties.lightLevel` is handed a `ToIntFunction<BlockState>`, so anything
  dynamic — a glow that answers to a nearby player — has to live in the
  blockstate, not on the block entity. That multiplies the state count, which is
  the real cost: the mural's four glow steps took it from 32 blockstate variants
  to 128, and a variant nobody generated renders as a missing model.
- **Implementing `EntityBlock` directly is usually better than extending
  `BaseEntityBlock`.** The base class overrides `getRenderShape` to `INVISIBLE`,
  which has to be undone for any block you still want drawn. The only thing lost
  is `createTickerHelper`, which is four lines: check `type == ours`, then cast.
- **Goals can require attributes, and say so only by crashing.** `TemptGoal`
  reads `Attributes.TEMPT_RANGE` inside `canUse`, so a mob built on
  `createMobAttributes` rather than `createAnimalAttributes` dies with
  `Can't find attribute minecraft:tempt_range` on the first tick the goal is
  evaluated — nothing in the constructor hints at it. Checking a goal's
  signature is not checking a goal; `javap -c` its `canUse` too.
- **Night vision has exactly one strength.** `GameRenderer.nightVisionScale`
  reads `MobEffectInstance.getDuration` and nothing else — there is no
  `getAmplifier` call anywhere in the method, only the `endsWith(200)` check
  that drives the expiry flicker. So an amplifier on night vision is inert, and
  anything built to "level up" an effect has to check that the effect *has* a
  level before charging for one. This is worth generalising: **an amplifier is a
  number the effect may or may not read**, so before designing a tier ladder on
  one, disassemble whatever consumes it. The charm depths were designed around
  this — the Leaper and the Wanderer deepen on amplifier, the Delver gains a
  second effect instead.
- **A crafting station does not need a block entity.** `ItemCombinerMenu` — the
  base under the anvil, grindstone and smithing table — owns the input container
  and a `ResultContainer`, drops the inputs when the screen closes, routes
  shift-clicks, and answers `stillValid` through an abstract `isValidBlock`. All
  that is left to implement is `createResult`, `onTake` and `isValidBlock`, and
  the block itself is a plain `Block` with a `SimpleMenuProvider`. Its slots come
  from `ItemCombinerMenuSlotDefinition.create().withSlot(i, x, y, predicate)…
  .withResultSlot(i, x, y).build()`, and **the constructor adds the player
  inventory itself** at `addStandardInventorySlots(inventory, 8, 84)` — which is
  already this mod's panel convention, so the artwork lines up for free.
  `mayPlace` on an input slot is the right place to refuse an item outright,
  rather than accepting it and silently producing no result.

## Worldgen gotchas

Each of these produced a structure that looked broken in a way the logs could
not explain. They are listed in the order they will bite.

- **`StructurePiece` orientation defaults to null**, and `getWorldX`/`getWorldZ`
  return the *relative* coordinate unchanged in that case instead of offsetting
  by the bounding box. Every block is then aimed near world origin and dropped by
  `placeBlock`'s chunk-box check — the piece runs and places **nothing**, which is
  indistinguishable from never running. Always `setOrientation(...)` in the
  constructors. `Direction.SOUTH` is the identity transform; NORTH mirrors Z.
  (`getWorldY` has no null branch, so Y looks correct and only X/Z are wrong.)
- **`postProcess` runs once per chunk the piece touches**, each call with its own
  random. Geometry that must agree across a chunk border cannot be shaped by
  random draws — make it a pure function of position (a positional hash gives
  variation without the disagreement).
- **Where a chunk border falls inside a piece is fixed, not luck.** A structure
  that sites itself on `chunkPos.middleBlockX` puts its box at
  `chunkX * 16 + 8 - WIDTH / 2`, so local coordinate *n* lands on
  `chunkX * 16 + (n - WIDTH / 2 + 8)` — the same residue mod 16 in every
  instance in the world. For the Guarded Ruin that works out at local 9 always
  on a chunk edge and local 24 always one short of one, which is why three of
  the four corner pillars straddle a border in *every* ruin and the fourth never
  does. Worth computing before hunting for a case in game: it says which part to
  look at, and it turns "some of them are broken" into a number.
  It also gives a free landmark. `/locate` reports
  `StructurePlacement.getLocatePos`, which is the structure chunk's *min* corner
  (`chunkX * 16`, not the centre — the centre would end in 8), and that is
  exactly where the local-9 corner pillar stands. So the located coordinate is
  the chunk-clean control pillar, and the one with four chunks meeting inside it
  is always **+15, +15** from it. No flying about looking for a case.
  This is also a trap for the checking script. A first pass over the colonnade
  bug sampled arbitrary box positions and reported 23% of ruins affected; the
  real figure was 100%, because arbitrary centres were not the population the
  structure actually generates. **Sample the sites the code really produces, not
  a convenient stand-in** — a plausible percentage is much harder to distrust
  than an obviously wrong one.
- **A piece may only *read* the chunk it is writing.** 26.2 checks this and logs
  `Detected unsafe terrain read during worldgen … (distance: 2, write radius: 1)`,
  so the mistake announces itself — but only if the log is being watched, and it
  does not stop generation. Anything wider than a chunk therefore has to fence
  its own reads: compare `getWorldX`/`getWorldZ` against the `box` argument, which
  is the chunk currently being written, and skip columns it does not cover.
  `placeBlock` already drops out-of-box *writes*, which is what makes this easy
  to miss — `level.getHeight` and `getBlock` have no such guard. The same guard
  is a large saving, since a plot spanning four chunks otherwise runs every
  heightmap lookup four times.
- **Anything a piece cannot recompute from its bounding box has to be persisted.**
  The box and the piece random are saved by the base class, so a layout derived
  purely from those needs no `addAdditionalSaveData` at all — which is the reason
  to derive from them wherever possible. A fact about the *world* rather than the
  geometry (which biome the site is in, and so which palette to build from) has
  nowhere to be recovered from and must go in the tag.
- **Pieces `postProcess` in the order they were added.** Two pieces that overlap
  resolve last-writer-wins, so a shaft that has to cut through another piece's
  work is added after it.
- **Surface structures anchor to the noise-predicted terrain height**, which
  excludes snow, topsoil and everything else the surface rules add afterwards. A
  solid platform placed at that height ends up sealed under the finished ground.
  Clear your own footprint before building.
- **Caves are carved before features but after structures pick their position**,
  so a structure cannot reliably detect an existing cavern. Vanilla's underground
  structures do not try; carve your own space and sample the column to reject
  spots that are already hollow.
- **Face a shaft before cutting it, not after.** Patching the walls of a cut
  never covers the back wall or the ground under the treads. Cast the volume as
  solid masonry, then carve the passage out of it.
- **A structure-placed connecting block does not connect.** `placeBlock` writes
  with flag 2 — clients only, no neighbour update — so nothing recalculates
  shape afterwards *except* for the blocks in `StructurePiece.SHAPE_CHECK_BLOCKS`,
  which get `markPosForPostProcessing`. That set is fences, iron bars, ladders and
  torches. **Walls are not in it**, so a cobblestone wall generates as a row of
  loose posts and a fence generates correctly — the opposite way round to what
  the two blocks feel like. Iron bars are the connecting block to reach for.
  Stairs are fine because only their `shape` is derived, and `straight` is the
  usual look anyway.
- **`TagAppender.addTag` validates that the target is defined by the same
  provider.** Referencing a vanilla tag needs `addOptionalTag`, or datagen fails
  outright.
- **Anything set into a wall needs stone *behind* it, not just air in front.**
  The obvious home for the sanctum's murals was its own perimeter, and it is
  wrong: the colonnade is a ruined wall one block thick standing in an open
  cavern, so a block in it has a room on one face and the cavern on the other
  and reads as a loose slab somebody propped up. The masses that actually work
  are the ones cast solid and then carved — the stairwell, the crypt and deeps
  shells, the vault. Note that *untouched world rock* counts: the test is
  whether there is material behind the face, not whether the structure wrote it.
  A one-block building wall backing onto the outdoors is the honest exception.

## Block entity rendering gotchas

From building the Gilded Reliquary. None of these are visible from logs — each
needed a screenshot to diagnose.

- **`ModelPart` divides by 16 internally.** Geometry arrives block-scaled, so an
  extra `scale(1/16)` renders at 1/256 size.
- **Block entities get no automatic Y inversion**, unlike entities. Boxes
  authored Y-up render upright as they stand. `scale(1,-1,1)` then
  `translate(0,-1,0)` composes to `y -> 1-y`, mirroring the model about the
  middle of its own block.
- **`ModelPart.Cube` emits `DOWN` before `UP`.** In an unwrapped net the first
  region of the top row is the *underside*. Getting this backwards paints detail
  on the face nobody sees and leaves the visible top blank.
- **A block model can only reference textures stitched into the block atlas.**
  A renderer loads any path directly, so a sheet under `textures/entity/` works
  for the renderer but fails for the item's model — keep shared sheets in
  `textures/block/`.
- **An animated lid needs a `BlockEntityRenderer`**; static block models cannot
  move. Render shape must then be `INVISIBLE` or the JSON model draws through it.
- Lid state reaches clients as a **block event** (`Level.blockEvent` →
  `Block.triggerEvent` → `BlockEntity.triggerEvent`), because the opener count
  only exists server-side.
- 26.2's renderer is **render-state based**: `createRenderState` /
  `extractRenderState` / `submit`. Entity render types moved to `RenderTypes`,
  and Fabric's `BlockEntityRendererRegistry` is deprecated in favour of vanilla
  `BlockEntityRenderers.register`.
- **`sounds.json` treats every top-level key as a sound event.** A `_comment`
  string there fails the whole file and silences the mod.

## Blocks, menus and screens

- Blocks register through `Blocks.register(key, factory, properties)`, which
  assigns the id internally — `BlockBehaviour.Properties` has no `setId`. The
  matching `BlockItem` is registered separately, like any other item.
- **Declaring a `FACING` property does nothing on its own.** Without
  `getStateForPlacement`, the state keeps its default forever and every block
  faces the same way no matter how it was placed.
- **To answer a block being destroyed, override
  `affectNeighborsAfterRemoval`, not `playerWillDestroy`.** `LevelChunk.setBlockState`
  is what calls it, so every route to removal arrives there — pick, explosion,
  piston, `/setblock` — and it is handed a `ServerLevel`, so there is no side to
  check. `playerWillDestroy` misses everything that is not a player *and* fires
  client-side for prediction. Its `movedByPiston` flag is worth honouring: a
  pushed block has not been destroyed, only moved, and it takes its blockstate
  with it. (Found by disassembling to see who calls it; the name suggests
  neighbour bookkeeping and reads like the wrong hook.)
- **A trap in a block belongs in its blockstate, not in an assumption about
  where the block came from.** The Grave carries `OCCUPIED`: worldgen lays
  occupied ones, the `BlockItem` in a player's hand places empty ones, so a
  stack carried home is not a portable spawner and an emptied grave can still be
  left standing as a plain mound.
- **Screens register per `MenuType`.** Styling a screen for a *vanilla* menu type
  restyles every vanilla block using it — reusing `GENERIC_3x3` would have given
  the Reliquary's panel to every dispenser in the game. Custom interface means a
  custom menu type, menu and screen.
- Owning the menu also lets `ContainerOpenersCounter.isOwnContainer` ask which
  container is open; vanilla's `DispenserMenu` exposes no accessor for its own.
- 26.2 screens draw in **`extractBackground(GuiGraphicsExtractor, ...)`** with
  `extractor.blit(RenderPipelines.GUI_TEXTURED, ...)`. There is no `renderBg` and
  no `GuiGraphics.blit`.
- **`GuiGraphics` does not exist at all** — the class is `GuiGraphicsExtractor`,
  and `Screen` implements `Renderable`, whose one method is
  **`extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick)`**.
  That is the `render` replacement. The extractor carries `text`,
  `centeredText`, `textWithWordWrap`, `fill` and `blit`, so a hand-laid panel
  needs nothing else. Input moved to record types too: `keyPressed(KeyEvent)`
  and **`mouseClicked(MouseButtonEvent, Boolean)`**, not the old int triples.
  And the screen is opened with **`Minecraft.setScreenAndShow`**; `setScreen` is
  gone.
- **A screen that pauses the game pauses the sound engine with it.** Anything
  the server played on the same tick the screen opened is queued and only
  arrives when the player closes it — which reads exactly like the block failing
  to play its sound, and sent us looking at the block. Vanilla's read-only book
  screen returns `false` from `isPauseScreen`; any screen that expects a sound
  alongside it must do the same.
- **A read-only screen wants a payload, not a menu.** `AbstractContainerMenu`
  buys nothing when there is nothing to hold: you get a slotless menu, a
  `stillValid` that always says yes, and a data slot smuggling the one number
  across. A `CustomPacketPayload` from the server, received in `src/client`,
  says what it means — and respects the source-set split, which is the real
  reason common code cannot just open a screen itself.
- Container panels are **176x166 in the top-left of a 256x256 sheet**. Player
  inventory at `(8, 84)`, hotbar at `(8, 142)`. Match those or the artwork and
  the real slots disagree.
- Ornament competes with text: a centred title lands on a centred keystone, and
  a left title lands on a top-left corner boss. Decide which owns the space.
- Slot bevels belong on the 18x18 outer ring, not inside the 16x16 well. Inside,
  every slot's weight shifts up-left and the grid reads as if it were misaligned.

## Blockbench

The Blockbench MCP server drives the desktop app directly. Model sources live in
`blockbench/` and are the authority; the Kotlin `LayerDefinition` is a
transcription of Blockbench's Java export, never hand-authored.

- **`risky_eval` rejects `//` and `/* */`.** Comments in the payload fail
  validation outright, so scripts have to go in bare.
- **The export negates X.** A bone placed at Blockbench x = +9 exports to model
  x = -9, which after the renderer's `scale(-1, -1, 1)` lands on the entity's
  own right — matching vanilla, whose `rightArm` is at model x = -5. So *right*
  parts go at *positive* Blockbench X. Y is likewise flipped and offset:
  Blockbench y (0 at the feet) exports as pivot `24 - y`. Blockbench previews
  the result, so what is on screen is what renders.
- **Rotations export as `xRot = -bb.x`, `yRot = -bb.y`, `zRot = +bb.z`.** Two of
  the three are negated and the third is not, so no single mental rule covers
  them. Verified by setting a bone to `[30, 20, 10]` and reading what
  `Codecs.modded_entity.compile()` emitted: `-0.5236F, -0.3491F, 0.1745F`. To
  preview a Java pose in Blockbench, set the bone to `[-deg(xRot), -deg(yRot),
  +deg(zRot)]` — cheaper than a game session, and it catches poses that fold
  through the body.
- **Which way a limb opens is not guessable — derive it.** `ModelPart` composes
  with `Quaternionf.rotationZYX`, so for an arm whose bone sits at model x = -9
  with cubes hanging down +y, *positive* `zRot` carries the hand further into
  -x, which is its own side. Away from the body is therefore **positive on the
  right arm, negative on the left**. Getting it backwards folds both arms
  through the torso and they vanish — which reads as the model breaking, not as
  a wrong sign. Four animations in this mod shipped inverted for exactly this
  reason, all of them commented "out" or "wide" while rotating in.
- **`create_texture` with width/height does not resize the bitmap.** The texture
  stays 16x16 while `Project.texture_width` is whatever was asked for, so UVs
  spanning 128 fall off a 16px canvas and almost every face renders transparent
  — which looks like the *model* has come apart, not the texture. Set
  `tex.canvas.width/height`, `tex.width/height` and `tex.uv_width/uv_height`
  together.
- **Auto-UV does not lay out a sheet.** `autouv: 1` leaves every cube at
  `texOffs(0, 0)`, all overlapping. `TextureGenerator.generateTemplate` is a
  minified dialog API; a shelf packer over `cube.uv_offset` is less trouble and
  gives a layout that can be reproduced.
- Ask Blockbench for `cube.faces[k].uv` rather than deriving the box-UV net.
  The verified layout is: top row `v..v+d` holds UP at `u+d` then DOWN at
  `u+d+w`; bottom row `v+d..v+d+h` holds EAST, NORTH, WEST, SOUTH left to right.
  Painting from the live rectangles skips the question entirely.
- **A block model face stretches whatever rectangle its `uv` names over the
  whole face.** So a mark drawn in the middle of a 16x16 texture, put on a face
  eight wide and five tall, is *squashed* — and box-UV instead crops some
  arbitrary corner of it. Neither looks like a bug in the texture; both look
  like a bug in the drawing. Draw the mark into a window whose aspect matches
  the face and name that window as the `uv` (`RUNE_WINDOW` in
  `make_binding_altar.py`), and it renders 1:1.
  Two more things that only bite at this size. A one-pixel outline does not
  survive a five-pixel-tall face — Bresenham breaks it and the groove closes
  the middle, so it reads as a smudge; go solid. And a mark reaching the edges
  of its window stops being a mark and becomes a band, so leave stone around it.
  The altar's shaft went through all three before it read as anything.
- **Blockbench caches a texture loaded from a path.** Rewriting the PNG on disk
  and re-rendering shows the *old* image, and `reloadTexture()` did not help —
  which reads as the change having no effect, and sent us looking at the model.
  Push the bytes in instead: read the file in `risky_eval` and call
  `texture.fromDataURL('data:image/png;base64,' + b64)`. And when a render
  looks unchanged, move the camera before believing it: two identical captures
  are also what a stale screenshot looks like.
- **Never hand-transcribe a data URL.** Base64 for a 128x128 PNG runs to
  thousands of characters and dropping twelve bytes yields a file that still
  reports `PNG image data, 128 x 128` to `file` and still parses its IHDR —
  it fails much later, in game, as `unknown PNG chunk type`. Blockbench is
  Electron: `require("fs").writeFileSync(path, Buffer.from(b64, "base64"))`
  inside `risky_eval` writes the real bytes. Verify by walking the chunk list;
  a good PNG ends IHDR … IDAT … IEND.

## Item art

- **An item that came off a mob must be drawn in that mob's palette.** Skin,
  bone, feather, shell, scale — anything the fiction says was part of the
  creature. Sample the entity texture and build the ramp out of what is actually
  in it; do not pick values that merely look good in isolation. The player sees
  the two together — a dropped item lands beside the thing that dropped it — and
  a mismatch reads instantly as belonging to a different animal, no matter how
  well the sprite works on its own. The Mourner's Feather shipped a draft whose
  lightest tone was `0xBF` against a bird topping out at `0x44`, and it was
  spotted in one screenshot.
  **This is worth a hard check in the generator**, because hex does not look
  wrong when read: `make_mourner_feather.py` asserts no vane tone exceeds the
  bird's lightest and prints the two numbers side by side on every run.
- **Do not generalise a legibility rule from an item that is part of nothing.**
  The above was got wrong by reasoning from the Fractured Memory, which vanished
  into the inventory slot when drawn dark — so the rule written down was "lift
  the hue until it reads at sixteen pixels". The Memory is a shard of a ruin and
  answers to no other texture; it is free to be whatever value reads best. A
  body part is not. And the diagnosis was wrong anyway: what killed the Memory
  was darkness *with no internal spread*, not darkness. The silhouette was never
  at risk, since both inventory greys are `0x8B` and `0xC6` and anything dark
  clears them easily. Spend the range on contrast *within* the sprite.
- **The mod's outline convention is a plate technique, not a house style.** Every
  charm is a solid mass inside an unbroken near-black ring, and that ring is
  what lets them hold a slot at either grey — but it is a *proportion*, not a
  fixed cost. Around a shape five pixels across it leaves three pixels of
  interior and there is nowhere to draw anything. Three drafts of the feather
  tried to keep it and read as a lozenge on a stick, a spruce tree, and a knife.
  Vanilla's own `feather.png` is **sixty-five pixels, four greys and no outline
  anywhere**, laid on a diagonal and full of holes. For a thin organic subject,
  follow that idiom instead: the gaps in the silhouette are what say *barbs*, and
  without them a tapered blob is a leaf.
- **Check a value structure against vanilla's before inverting it by instinct.**
  A dark vane with a pale spine down the middle is a *knife* at this scale, and
  an amber calamus at the base finishes it as a pommel. Vanilla has it the other
  way round — light vane, rachis as the darkest line — because a spine is a
  shadow between two banks of barbs, not a highlight along a blade.
- **A hole one pixel off a shaft reads as a missing pixel, not as detail.** The
  shaft is the one line the eye follows end to end, so a gap touching it is
  damage to the spine. Keep splits two or more pixels out. Also caught in game
  rather than in a diff, and now asserted.
- Render candidates at **1:1 on both inventory greys** (`0x8B8B8B` and
  `0xC6C6C6`) *and* beside the related art before shipping. Every failure above
  looked fine at 40x. A contact sheet of two or three candidate ramps against
  the mob texture settles in one look what argument does not.

## Particles

26.2 rebuilt this area, so almost nothing remembered about particles is true.

- **`TextureSheetParticle` no longer exists.** The base is `SingleQuadParticle`,
  whose only abstract member is `getLayer(): SingleQuadParticle.Layer`.
  `Layer.TRANSLUCENT` is the particle atlas paired with the translucent
  pipeline — the one to use for anything glowing. `getGroup()` is already
  implemented, so it does not need overriding.
- **`Mth.cos`/`Mth.sin` take a `double` and return a `float`.** Passing a float
  is a compile error, and `Mth.TWO_PI` *is* a float, so the obvious
  `random.nextFloat() * Mth.TWO_PI` has to be widened before it can be used.
- **The six-double `Particle` constructor does not set velocity.** It adds a
  random vector, normalises, scales by 0.4 and adds 0.1 to `yd` — vanilla's
  scatter behaviour. For controlled movement use the three-double constructor
  and assign `xd`/`yd`/`zd` directly.
- **`Particle.tick` applies friction after moving**, so a sway added *before*
  `super.tick()` is damped flat within a second or two. Assign velocity after
  the super call instead, which also makes gravity and friction irrelevant.
- Fabric's `ParticleFactoryRegistry` is gone. Use
  `net.fabricmc.fabric.api.client.particle.v1.**ParticleProviderRegistry**.getInstance()`.
  Register with the *pending* overload, which hands back a `FabricSpriteSet` —
  the sprite set does not exist until the atlas is stitched, long after mod
  init. Types themselves come from `FabricParticleTypes.simple(alwaysShow)`;
  `alwaysShow` overrides the limiter, which is worth setting for rare particles
  that would otherwise be culled on low particle settings.
- Sprites live in `assets/<ns>/textures/particle/`, listed by a definition at
  `assets/<ns>/particles/<name>.json`. **No atlas file of our own is needed** —
  vanilla's `particles.json` atlas has a directory source with an empty prefix,
  which sweeps every namespace.
- One sheet, two idioms: `setSpriteFromAge(sprites)` each tick animates a single
  particle through the frames, while `sprites.get(random)` picks one sprite for
  the particle's whole life. The first gives an envelope, the second gives
  variety — using the wrong one makes six distinct glyphs read as one glyph
  flickering between shapes.
- **An even-sized sprite has no pixel at its centre.** The four innermost are
  0.707 away, so a radial falloff measured from the true centre never reaches
  full brightness and the sprite renders as a dim smudge. Subtract that offset
  before the falloff. Related: at 8x8 a four-pointed glint has three pixels of
  arm to work with and collapses into a blob — 16x16 is what makes the shape read.
- `StructureManager.getStructureWithPieceAt` returns `StructureStart.INVALID_START`,
  never null. Check `isValid`. The predicate overload takes
  `Predicate<Holder<Structure>>`, so a structure that only exists in a dynamic
  registry can be matched by `ResourceKey` without resolving it first.

## Animation and sound

Match the **audible movement**, not the file's length. `block/vault/open_shutter`
runs 2.041 s but most of that is decay tail, so a lid stretched to span it crawls
long after anything sounds like it is moving. Measure a `.ogg` by dividing its
last page's granule position by the sample rate when the duration matters.

## Design invariant: charms and attunement

Charms apply their aura **from anywhere in the inventory**, in slot order, until
the player's **attunement** runs out (starts 1, caps 4). Vanilla numbers the
hotbar first, so players set priority by moving charms to the hotbar. Attunement
is a Fabric data attachment: persistent, `copyOnDeath`, synced `targetOnly`.
**Echo of Kinship** is consumed to raise it.

`CharmScan.activeSlots()` is the **single source of truth** and lives in the
common source set, because the server tick applies auras and the client tooltip
labels them Attuned/Dormant. If the rule changes, change `CharmScan` — never
duplicate it in the ticker or the tooltip, or the UI will lie.

Adding a charm should only require a new `CharmAura`. No per-charm slot logic.

**A charm also has a depth**, one-based, stored as `ModComponents.CHARM_DEPTH`
on the stack and raised at a Binding Altar. `CharmItem.depths` is the list of
what it grants at each, so `cost` and the effects are both properties of the
*stack* rather than the item — which is why `CharmScan` and `CharmTicker` read
through `CharmItem.auraOf(stack)` and never `charm.cost`. Two rules that are
load-bearing:

- **Deepening is not always an amplifier.** Night vision has no level and
  Deflection is ours, so the Delver widens instead (night vision, then + haste)
  and the Returned does not deepen at all. Anything iterating effects must
  therefore flatten *every* depth: `CharmTicker.grantable` does, and if it did
  not, haste would be granted at depth II and never withdrawn, because it does
  not appear at depth I.
- **The supply and the sink were built together.** Deepening costs 8 memories
  then 16, which is more than a ruin holds — so it only works because emptying a
  Reliquary hollows that ruin (`RuinHollow`) and its graveyard starts giving up
  Remnants that carry memories. Changing either number without the other turns
  the mechanic back into a reason to fly to the next structure.

Four rules that are load-bearing, each of which cost something to arrive at:

- **Attunement is a budget, not a count.** `CharmItem.cost` is 1 for almost
  everything and 3 for the Returned, so at the cap it leaves room for exactly
  one more. A charm that will not fit is **skipped, not stopping the walk** —
  otherwise one expensive charm high in the inventory silently strands every
  cheap one below it.
- **A hushed charm gives up its place**, rather than merely going dormant. It
  drops out of the queue so a charm further down takes the attunement, which is
  the whole point of being able to switch one off. The off switch is a synced
  component on the *stack*, so the setting travels with the charm rather than
  with the player.
- **Auras are granted infinite and withdrawn**, never topped up. A short effect
  re-applied on a timer looks fine for most things and is wrong for night
  vision, which vanilla deliberately makes flicker under ten seconds remaining
  as an expiry warning — so a charm meant to be permanent strobes underground.
  That inverts the sweep's job, and it runs **every tick** so putting a charm
  down cancels at once.
- **Only withdraw what is infinite *and* ambient.** That pair is a signature
  nothing a player can drink or stand in produces: potions are finite, and a
  beacon is ambient but keeps refreshing a finite duration (checked in the
  bytecode, not assumed). The list of effects to consider is read off the item
  registry rather than hand-kept, so a new charm is withdrawn correctly without
  anyone remembering to come back here.

**Reacting to damage is the wrong shape for anything that intercepts a
projectile.** The Returned's deflection tried it first and could never have
worked for a ghast fireball, which calls `Level.explode` and then `discard` —
by the time anything is hurt there is no projectile left to send anywhere. It
also makes the feature depend on whether each projectile type names itself as
the `directEntity` of its damage source, which varies, and is what tridents fell
through. **Look a tick ahead instead**: clip the segment each nearby projectile
is about to travel against the guard's box and turn what would cross. One rule
for everything, and nothing has to be hurt for it to fire. The Sentinel's gyre
still answers the damage hook because it only ever catches arrows.

## Testing in-game with the player

**The player is an available instrument — use them.** They are happy to be asked
to test things, look things up, and report back.

Launch the client as a **background** task, then attach a `Monitor` tailing that
task's output file. The log is a live two-way channel:

- `(vanguard-spirits)` — our own logger.
- `<PlayerName> message` — **in-game chat appears in the log**, so the player can
  talk to you without leaving the game. Ask them to report findings in chat.
- Stack traces and crashes.

Filter out the dev-environment noise, which is constant and harmless: Realms auth
failures, `Failed to retrieve profile key pair` (401), oshi /
`HkeyPerformanceData` Windows perf-counter warnings, and `Preparing spawn area`
progress spam.

**What you cannot see is the screen.** Anything visual or audible — whether a
texture reads well at 16x16, whether a tooltip line is the right colour, whether
a sound fired, whether an effect icon appears — must be *asked*. The other option
is a temporary `LOGGER.info` the player can trigger.

Good things to hand the player: `/give @s vanguard-spirits:<item>`, checking a
creative tab, confirming a recipe shows in the recipe book.

## Advancements

**Flavour goes in the title. The description is a plain instruction.** This is
the rule the first tree broke, and it is worth stating as a measurement rather
than a taste: vanilla ships **127 advancement descriptions, median seven words
and thirty-four characters, and not one of them adds a second sentence.** "Enter
a Bastion Remnant." "Kill any hostile monster." "Upgrade your Pickaxe." All the
wit is in names like *Isn't It Iron Pick* and *Those Were the Days*, and the line
underneath stays flat. Our first draft ran half again as long because every
description carried an editorial clause the title had already earned — "Stand
inside a Guarded Ruin. **The birds were the sign.**" A description may name the
route when that is genuinely useful, the way "Brush a Suspicious block to obtain
a Pottery Sherd" does, but it stops there.

The general form is worth keeping, because it is the same lesson as the item-art
palette: **when authoring content that sits inside a vanilla surface, measure
vanilla's own corpus before writing to taste.** The lang file is right there and
`json.load` plus a median takes a minute. A sentence full stop in a description
is the cheap tell that a second clause has crept back in — assert on it.

Mechanics, each of which cost a lookup:

- **Backgrounds are just block textures.** `backgrounds/stone.png` is
  byte-for-byte the stone block — same 16x16, same `0x68`-`0x8F` range, same four
  colours — and the panel darkens it at draw time. So `minecraft:block/<whatever>`
  works directly as a `background`, which means no asset to draw and, for a jam
  entry, no copy of a Mojang texture in the jar.
- **The root is named after the tab, and its description is a tagline.** Two
  separate conventions, both off vanilla: `story.root` is titled "Minecraft", not
  after its criterion, so a mod's root takes the mod's name. And roots are the
  one exception to the instruction rule above — every vanilla root carries a
  mood line instead ("Bring summer clothes", "Or the beginning?", "The heart and
  story of the game"). An evocative title displaced from a root has somewhere to
  go.
- **Parents are display only.** A child can complete before its parent and the
  tab will happily show it, so the parent must be the *actual* prerequisite, not
  the narratively tidy one. Ours got this wrong: Two Who Never Met hung off the
  Reliquary when the Echo of Kinship in fact drops from the Sentinel at 50%, and
  a playthrough duly earned the child six seconds before the parent.
- **Vanilla suppresses `show_toast` and `announce_to_chat` on roots** because its
  roots are trivial — everyone gets a crafting table. A modded root usually fires
  on something worth noticing, and silence there reads as the feature not working.
- **State the game cannot see needs a custom `CriterionTrigger`**, registered into
  `BuiltInRegistries.TRIGGER_TYPES` at mod init. Attunement is a data attachment,
  so nothing vanilla can watch it. Have the instance carry a *minimum* rather than
  an exact value and one trigger serves every threshold. Fire it from the state
  change itself (`Attunement.raise`), not from the item that happens to cause it.
- **A criterion naming an unregistered trigger generates perfectly happily** and
  then never fires. Build the criterion through the registry object so datagen
  and the firing site cannot name different things.
- Two free instruments. `Loaded N advancements` reconciles exactly against
  vanilla's file count plus ours, so a rejected advancement shows up as a number
  that is one short — adding two took it from 1704 to 1706. **But that line is
  logged many times a session and only the first is the total.** Everything
  after it is the *client* being sent the player's earned progress, so the same
  message reads 1706, then 2, then 10, then 17, climbing as they play. Twenty-five
  of them in one session, and reading any but the first as the datapack total
  looks exactly like the mod's tree having failed to load. Take the first, from
  the server's datapack load, and ignore the rest.
  And the **announcement wording reveals the frame** — "has
  made the advancement" / "has reached the goal" / "has completed the challenge"
  — which is the only confirmation from a log that `AdvancementType` took.
- Generate the tree and its lang strings from **one shared list**
  (`ModAdvancements`). An untranslated key is a perfectly valid key, so a
  mismatch cannot fail datagen and surfaces only as raw
  `advancements.vanguard-spirits.foo.title` in game.

## Datagen

Providers are registered in `VanguardSpiritsDataGenerator`. After changing items,
lang, or recipes:

```bash
./gradlew runDatagen
```

Commit the resulting `src/main/generated/` changes. Translation keys are added
through `ModItems.loreKey(path)` and the provider, never hand-edited into JSON.

## Tooling status

Both language servers run from the local `fresh-local` marketplace, because the
official plugins' installed `plugin.json` loses its `lspServers` block in transit.
The marketplace listing has the config; the installed manifest does not.

- **Java** (`jdtls-win@fresh-local`) — working for `.java` (mixins). Needs a
  native `.exe` shim because Claude Code refuses to spawn `.cmd`.
- **Kotlin** (`kotlin-lsp-win@fresh-local`) — working for `.kt`/`.kts`. Points
  straight at JetBrains' `intellij-server.exe --stdio`; **no shim needed**, since
  the shipped `kotlin-lsp.cmd` is deprecated and only forwards to that exe.
  Installed at `%LOCALAPPDATA%\kotlin-lsp\262.9593.0`, with `--system-path` set
  so indexes persist instead of being rebuilt in `%TEMP%` every run.

Both advertise hover, definition, references and documentSymbol, and both resolve
Minecraft types from the dev classpath. **Neither replaces `javap`**:
`workspaceSymbol` does not reach library types, so they are for navigating *our*
code, not discovering Minecraft/Fabric API.

On a cold start the Kotlin server runs a Gradle import before semantics work —
`documentSymbol` answers immediately, but `hover` and `goToDefinition` return
nothing for the first minute or so. That is import lag, not breakage; retry.

The fastest correctness check for Kotlin is still the compiler — `./gradlew
clientClasses` takes a few seconds and catches every wrong signature.

Changing a plugin manifest requires **restarting Claude Code**; LSP configs are
read at session start.

## Conventions

- Tabs for indentation in Kotlin, matching the template.
- Commit messages: imperative subject, body explaining *why*, and
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Remote is a private repo: <https://github.com/Fresh-glitch/vanguard-spirits>
