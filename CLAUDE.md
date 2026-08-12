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
- `unzip 'net/minecraft/client/*'` to grep the client jar for who calls a
  method. The glob did not recurse: 94 classes out of 3507, and the search came
  back empty — which reads exactly like *nothing calls it*, and the next step
  would have been building a mixin to do by hand what the engine already did.
  Caught only by also grepping for a string that was certainly present and
  getting zero for that too. **When a search over a corpus you extracted returns
  nothing, prove the corpus before believing the result** — the count of what
  you actually extracted is one line and settles it.

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
- **…but an `ItemCombinerMenu` can never be *backed* by one.** `inputSlots` is
  built in the constructor by a private `createContainer` returning an anonymous
  `SimpleContainer`, and the field is final — so there is no way to point the
  slots at a block entity's list, and a station that has to remember its inputs
  has to lend and reclaim around the base class instead. Two consequences.
  `removed` must empty the container **before** calling `super`, since the base's
  own `clearContainer` hands the inputs to the player. And the handover must move
  or lock, never copy: hand a copy to every menu that asks and two players at one
  block can each carry off the same item.
  Showing the block's contents while a screen is open then needs a third idea,
  because the stacks are in the menu and not in the block. Keep them in the block
  entity as a **mirror** the menu repaints on every change, and hold one rule:
  **a mirror is a picture, never a source.** Anything that could hand an item to
  a player — dropping on break, saving, lending to a second screen — has to check
  the loan first, or the picture prints.
- **`BlockEntity.preRemoveSideEffects` is what spills a block's contents**, not
  the block's `affectNeighborsAfterRemoval` — which reads like the right hook and
  is not: chest, hopper and dispenser all override it, and every one of them does
  nothing but `Containers.updateNeighboursAfterDestroy`. The base
  `preRemoveSideEffects` is three lines that check for a `Container` and call
  `Containers.dropContents`, so **implementing `Container` gets the drop for
  free** — at the price of the block becoming something a hopper can load and
  empty. Overriding it directly gets the behaviour without the plumbing. Either
  way `LevelChunk.setBlockState` calls it already guarded three ways: server side
  only, skipped for `UPDATE_SUPPRESS_DROPS`, and skipped when
  `shouldChangedStateKeepBlockEntity` says the entity survives — so a piston move
  or a `FACING` change cannot spill it, and no guard of your own is needed.
- **`setChanged()` does not reach clients.** It marks the chunk unsaved and
  nothing else, so a block entity whose contents are *rendered* keeps drawing
  whatever the client last heard — which looks exactly like the item failing to
  go in. The pattern is vanilla's `CampfireBlockEntity.markUpdated`, which is
  `setChanged()` then `level.sendBlockUpdated(pos, state, state, UPDATE_ALL)`,
  passing the same state twice. Pair it with
  `getUpdateTag(registries) = saveCustomOnly(registries)` and
  `getUpdatePacket() = ClientboundBlockEntityDataPacket.create(this)`.

- **Several names that read as obvious are simply not there.** Each cost a
  compile, and the pattern is worth more than the list — the item and block tag
  sets are not mirrors of each other, and convenience accessors come and go.
  - There is no `BlockTags.SAPLINGS`, only `ItemTags.SAPLINGS`. Flowers run the
    other way: `BlockTags.FLOWERS` and `SMALL_FLOWERS` exist and there is no
    vanilla item tag at all, so the item side has to come from Fabric's
    `ConventionalItemTags.FLOWERS`. Match a sapling *block* by `SaplingBlock`.
  - `BlockPos` has no `getCenter()`. `Vec3.atCenterOf(pos)` is the one; the
    similar-reading `Vec3i.distToCenterSqr` is a distance, not a point.
  - `Level` has no `getDayTime()`. `getSkyDarken()` — 0 in open daylight, 11 at
    midnight — is a better night test anyway, since it already answers for a
    thunderhead.
- **`EntityModel` has no `setupAnim`. It is `Model.setupAnim(S)`, and the first
  thing it does is `resetPose()`**, restoring every part to its `PartPose`. That
  is what makes `part.zRot +=` safe for a rest rotation baked into the model —
  and worth confirming rather than assuming in either direction, because if it
  did *not* reset, a `+=` would wind the arm further round every frame and
  present as a slow drift with no obvious cause.
- **`EntitySpawnReason.CHUNK_GENERATION` is not `NATURAL`, and for a
  `MobCategory.CREATURE` mob it is the one that matters.** Vanilla populates
  passive mobs when a chunk is first generated rather than on the running spawn
  cycle, so a `SpawnPlacements` predicate that rations only `NATURAL` — which
  reads exactly like the right check — never rations anything at all. The
  Nymph's one-in-two-thousand roll was dead code against the only path she ever
  arrived by, leaving the rarest mob in the mod gated by its biome weight alone.
  Ration `NATURAL || CHUNK_GENERATION` and let the deliberate reasons through.
  (The Mourner is correct as written because it is AMBIENT, and only creatures
  are placed at chunk generation. The same code is right in one file and wrong
  in the other.)
  Two further things this cost. The enum is bigger than it looks — nineteen
  values, and a spawn egg is `SPAWN_ITEM_USE`, not the `SPAWN_EGG` you will type
  first. And **it is invisible without an instrument**: a Nymph standing in a
  flower forest is the same mob however she got there, so the only way to see
  which door she came through is to log
  `EntitySpawnReason` from `finalizeSpawn` and read it.
- **`Mob.serverAiStep` ticks the target selector, then the goal selector, then
  `customServerAiStep`.** So anything touching `target` in `customServerAiStep`
  gets the last word over the target goals *within that tick*. Clearing it
  indiscriminately fights `HurtByTargetGoal` tick by tick — the goal re-acquires,
  the step drops it, and the mob attacks in visible stutters. Clearing a target
  is fine; clearing *the mob currently hitting you* is not.
- **…but clearing `target` cannot end a fight, and it reads exactly as though it
  does.** `TargetGoal.canContinueToUse` keeps its own copy of the quarry in
  `targetMob`, falls back to it the moment `mob.getTarget()` is null, and ends
  with `mob.setTarget(livingEntity); return true`. So a target dropped in
  `customServerAiStep` is restored at the top of the next tick, *before* the
  goal selector runs — the mob never misses a swing. What ends it is
  `Mob.canAttack(LivingEntity)`, which the same method calls first and which
  makes the goal return false, stop, and null both the mob's target and its own
  copy. It is public and overridable, so "this mob will not fight right now" is
  one override; "set the target to null" is a no-op wearing a disguise.
  Worth stating as a general shape, because the sequel to it is in the
  Blockbench section too: **when something refuses to stay switched off, look
  for who switches it back on.** The Nymph shipped for a session telling players
  "Very well. Go carefully" and then killing them, and the line that was
  supposed to prevent it was running, every tick, doing exactly nothing.

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
- **`findNearestMapStructure`'s radius is in *grid rings*, not chunks and not
  blocks.** `ChunkGenerator.getNearestGeneratedStructure` reads
  `RandomSpreadStructurePlacement.spacing()` and walks candidates at
  `base + spacing * ring`, with `ring` running `-radius..+radius` on both axes —
  so the argument counts cells of the placement grid, each `spacing` chunks
  wide, and the work done is `(2 * radius + 1)` squared. For the Guarded Ruin's
  spacing of 24 that is a **384 block** step per ring, so a radius that reads
  like "64 chunks" is really a twenty four *thousand* block sweep. Four rings
  covers 1,536 blocks and is ample.
  **Narrowing it does not make the search faster**, which is worth knowing
  before spending a session on it: cold ground measured 443/192/194 ms at 64
  rings and 231/160 ms at 4, while explored ground was 0-1 ms at both. The loop
  keeps the nearest hit so far and only calls the expensive
  `getStructureGeneratingAt` on cells closer than it, so everything outside the
  first hit's ring is pruned by a distance compare — walking 16,641 cells rather
  than 81 is nearly free because 16,560 are never examined. The cost is
  examining the few candidates in the first ring or two of unassessed ground,
  which for us is `GuardedRuinsStructure`'s own column sampling. Set the radius
  for *reach* and for bounding the found-nothing case; do not expect throughput
  from it.
  The way the unit was caught is the transferable part. The constant had been
  written down as "1024 blocks", then tightened to "512 blocks", and a playtest
  returned a hit at **657 blocks** — an answer the documented radius made
  impossible. A number that is merely large is easy to rationalise; one that is
  *outside the stated bound* cannot be, and it is the only reason the unit got
  questioned at all. **When an instrument returns something the model forbids,
  that is the model failing, not the instrument** — and it is worth more than
  ten plausible readings. Both wrong figures had survived being written into a
  doc comment, twice, with confident arithmetic either side of them.
- **`skipKnownStructures` only skips a structure once something has asked about
  it.** The first `findNearestMapStructure` at a ruin returns *that ruin*, every
  time — measured at three separate sites, all about 11 blocks out — and a second
  call a moment later returns one hundreds of blocks away. So the flag cannot
  mean "somewhere you have not been" on a cold cache, and anything relying on it
  needs to ask twice and sanity check the answer against the origin. Note which
  half is expensive: finding the ruin under your feet is 0 ms, and the retry that
  has to travel past every known ruin is the one that costs 200–450 ms.
- **`ProtoChunk.setBlockState` does not create block entities**, so every copy of
  a structure already in a world was written without one. That matters when a
  block *gains* an entity in an update: nothing needs migrating, because
  `getBlockEntity` creates it on demand the first time anything asks — but until
  something asks, the block has none, so anything that walks chunks looking for
  them will find nothing and read as broken.

## Block entity rendering gotchas

From building the Gilded Reliquary and the Binding Altar. None of these are
visible from logs — each needed a screenshot to diagnose.

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
- **`submit` does not draw. It queues, and a `ModelPart` is passed by
  reference.** `SubmitNodeCollection.submitModel` copies the *pose* —
  `poseStack.last().copy()` — and then stores the **model itself unwrapped** in a
  node drawn later in the frame. So a `ModelPart` held as a field on the renderer
  is shared by every block entity of that type on screen, and posing it per
  entity before submitting means they all draw with whatever the *last* one
  wrote. `submitModelPart` is not a way out: it wraps the part in a
  `Model.Simple` that holds the same reference.
  The symptom is unmistakable once two are in view and impossible before:
  animating the one submitted last moves them all, animating any other moves
  none — while the sound plays once, because the server was never confused. The
  Reliquary shipped like this from the day it was added and nobody had two.
  **Vanilla's answer is the type parameter on `Model<S>`.** `ChestModel` is
  `Model<Float>`; the openness travels as the `S` handed to `submitModel`, the
  node keeps that value beside the model, and `setupAnim(S)` runs against it
  immediately before *that* node draws. Anything that varies per instance goes
  through the state, never into a field. Render *states* are safe — one is
  extracted per block entity — but the renderer's own fields are not.
- Lid state reaches clients as a **block event** (`Level.blockEvent` →
  `Block.triggerEvent` → `BlockEntity.triggerEvent`), because the opener count
  only exists server-side.
- 26.2's renderer is **render-state based**: `createRenderState` /
  `extractRenderState` / `submit`. Entity render types moved to `RenderTypes`,
  and Fabric's `BlockEntityRendererRegistry` is deprecated in favour of vanilla
  `BlockEntityRenderers.register`.
- **`sounds.json` treats every top-level key as a sound event.** A `_comment`
  string there fails the whole file and silences the mod.
- **To draw an `ItemStack` from a renderer**, take `context.itemModelResolver()`,
  call `updateForTopItem(state, stack, ItemDisplayContext.FIXED, level, null,
  seed)` in `extractRenderState`, and `ItemStackRenderState.submit(pose,
  collector, lightCoords, NO_OVERLAY, 0)` in `submit`. Two things vanilla's
  `CampfireRenderer` obscures. `updateForTopItem` **clears the state first** —
  that is the first line of its body — so one `ItemStackRenderState` can be a
  field and reused every frame, rather than allocated per item per frame as the
  campfire does; and the same resolved state can be submitted many times under
  different poses, which is how one memory becomes a ring of them. Second, **the
  model arrives centred on its own origin**: the campfire's
  `translate(-0.3125, -0.3125, 0)` is putting food in a quadrant, not centring
  it, so copying that line into a renderer that wants one centred item shifts it
  off by five pixels.
  **Which way up the sprite lands is not worth deriving.** Three transforms
  compose here — the yaw from `FACING`, the quarter turn that lays it flat, and
  the model's own `fixed` display transform, which for `item/generated` carries
  an undocumented `rotation: [0, 180, 0]` — and the altar's charm came out
  exactly backwards from a derivation that checked out line by line. Same trap as
  the Blockbench bone signs, same answer: put it in the world and look at it.
- **Drawing text on a block: the call is `SubmitNodeCollector.submitText(pose,
  x, y, FormattedCharSequence, dropShadow, Font.DisplayMode, light, colour,
  backdrop, outline)`**, with the `Font` taken from
  `BlockEntityRendererProvider.Context.font()`. The mode to use is
  `Font.DisplayMode.POLYGON_OFFSET`, which is what stops glyphs z-fighting the
  face behind them. The class to copy is **`AbstractSignRenderer`** — there is no
  `SignRenderer` in 26.2, and javap on the guessed name returns nothing, which
  reads exactly like *signs do not render text*.
  Written down because the first attempt at this **drew nothing at all**, and
  the call shape was verified against vanilla's bytecode afterwards and found
  identical — so if this happens again the API usage is *not* the suspect. Look
  at whether the render state actually reached the client, and at the pose. Note
  the diagnostic trap: a `submit` that early-returns on absent state is
  indistinguishable from a renderer that was never registered, since both draw
  nothing, so probe with a log line rather than another look at the screen.
  And size the surface first. A player name needs about a plank and a half,
  which is why signs are that size; six pixels of a grave marker's face will
  never hold one, and no amount of scaling fixes that.
- **A `BlockEntityRenderer` composes with the block model; it does not replace
  it.** Only `BaseEntityBlock` forces `RenderShape.INVISIBLE`. Implementing
  `EntityBlock` on a plain `Block` leaves the shape MODEL, so the JSON model
  still draws and the renderer adds to it — which is what you want for anything
  that is a static block *plus* something lying on it.
- **There is no coloured light in this game, and there is nowhere to put one.**
  `LightLayer` has exactly two values, SKY and BLOCK; `DataLayer` stores each as
  a **nibble**; and `Lightmap` is a 16x16 lookup indexed by the pair. Every lit
  surface in the world is decided by two four-bit integers, so "emit a colour"
  is not a hard feature but an impossible one — it is why shader packs replace
  the lighting pipeline outright. What is achievable is (a) drawing a thing at
  its own brightness so its *sprite's* colour reads in the dark, and (b) a
  scalar block light, which is white. Together they pass for the request.
  For (a), `LightCoordsUtil` is the packing helper: `pack(block, sky)` is
  `(block << 4) | (sky << 20)`, `FULL_BRIGHT` is `0xF000F0`, and
  `lightCoordsWithEmission(packed, n)` raises *both* channels to at least `n` —
  a floor, not an override, so daylight still wins outdoors.
- **A model element's `light_emission` is honoured for items, not just blocks —
  and it is the whole feature.** `FaceBakery` bakes it into the quad's
  `BakedQuad.MaterialInfo`, and `VertexConsumer.putBakedQuad` — the *`Pose`
  taking* overload, which is where item and entity rendering both end up —
  reads it back as the quad is drawn. (`putBlockBakedQuad` is the chunk path.)
  So one line of data lights a detail **everywhere the item can appear**: held,
  dropped, in a frame, in a `BlockEntityRenderer`. Reaching for a renderer or a
  mixin instead buys a worse version of this, one context at a time.
  Three things it costs. `light_emission` belongs to an **element**, and
  `item/generated` builds its elements itself out of the sprite, so there is
  nowhere to hang it — the glowing part has to be a **second model**, joined on
  with the `minecraft:composite` item-model type. That second model needs its
  own `display` block, because each composited model applies its own; copy it
  out of the client jar's `assets/minecraft/models/item/generated.json` rather
  than from memory, since the halves have to line up exactly and a few units out
  shows in the hand and not in the inventory. And give it a `particle` texture,
  or every load logs `Missing texture references` for it.
- **`SubmitNodeCollector.submitCustomGeometry(pose, renderType, renderer)`** is
  the way to hand-build a quad in a renderer: the callback gets a
  `PoseStack.Pose` and a `VertexConsumer`, and `addVertex(pose, x, y, z)` plus
  `setNormal(pose, …)` apply the transform for you. Reach for it over a
  `ModelPart` when the geometry is a single flat face — a part means a *box*,
  which means laying the sheet out to match the cube net and wasting three faces
  of texture on something that will never be seen.
- **Animate off `level.gameTime`, and reduce it modulo the period before it
  becomes a float.** `gameTime` is a long, and a world a few weeks old has passed
  the point where a float can hold it to tick precision — a spin driven by the
  raw value visibly steps instead of turning. `((time % PERIOD) + partialTick) /
  PERIOD` is exact forever and wraps seamlessly, since a whole period is a whole
  revolution.

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
- **The stack handed to `Slot.onTake` is already empty on a shift-click.**
  `quickMoveStack` calls `moveItemStackTo` on the slot's *live* stack, which
  shrinks it in place, and then passes that same stack to `onTake`. So anything
  a menu charges, consumes or records must be read from the **input slots**,
  never from the result being carried off — which is why vanilla's anvil prices
  from `inputSlots` and its own stored cost.
  This shipped in the Binding Altar as a free deepening: the price was read off
  the result's depth component, an empty stack has no components, `depthOf`
  answered its default of 1, and the price of reaching depth 1 is zero. Note how
  well it hid — an ordinary click passes a stack that still has its components,
  so it only failed for players who shift-click, and it failed *silently*, by
  charging nothing rather than by throwing. The same read was in the sound's
  pitch and would have rung every shift-clicked binding at the wrong note.
  The general form is worth more than the instance: **an argument is not a
  fact about the past.** It was chosen deliberately, over the payment slot, on
  the grounds that the result could not be changed underneath us. The payment
  slot was the stable one.
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
- **`place_cube` with explicit face UVs does not bind the texture**, even when
  the call names one. The cube renders in the untextured pink, while other cubes
  placed in the *same call* with `faces: true` come out correctly — so a mixed
  model looks half broken and the obvious suspect is the UV numbers rather than
  the binding. Follow up with `apply_texture` on that cube; the UVs survive it,
  which is worth reading back rather than assuming, since an applier that reset
  them would say nothing.
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
- **Reading a texture in is the same trick backwards**, and it beats
  `create_texture` from a path: `new Texture({name}).fromDataURL("data:image/png;base64," + fs.readFileSync(p).toString("base64")).add()`.
  Decoding is **async**, so the texture reports `width` and `height` of 0 for a
  moment after it is added — that zero is not a failed load, and building UVs
  on it would be. Read the size back before placing anything.
- **The `java_block` export drops `parent`, and auto UV puts every face at
  `[0, 0]`.** Both matter and neither is visible in Blockbench. Without
  `minecraft:block/block` the block's *item* form has no display transforms at
  all; and thirty faces all sampling the same corner makes a speckled sheet
  repeat visibly up a stack. Re-add the parent, the namespaced texture refs and
  a `particle` entry, and respread the UVs, after every export.
- **Matching the numbers is not matching the shape.** The Epitaph's placed
  block was rebuilt to carry the item sprite's profile exactly — widths read
  row by row off the drawing, verified box for box against the `VoxelShape`,
  every row proven equal — and in game it looked **identical** to the version
  it replaced. The sprite spends only two of its fourteen rows on the dome, and
  two sixteenths of a block is a step however many steps are cut into it. The
  arch only read as one after it was built in Blockbench, rendered, and looked
  at, with the taper spread over three steps and a half-pixel inset. Verifying
  geometry against geometry can be perfect and prove nothing about how it
  looks; a render is the instrument, and it costs one tool call.
- **Sweep for coplanar faces; it finds z-fighting that looking never will.** Two
  boxes that overlap *and* share a face plane flicker, and at a glance that is
  indistinguishable from a texture fault or from nothing at all. Walking every
  pair — overlapping on all three axes, equal on any one bound — took the Nymph
  from seven real hits to zero. Three of them were faces created by inflating an
  overlay by a round `0.5` onto geometry already sitting at `3.0`, so **take an
  inflation off the round number** and the whole class disappears. Skip rotated
  cubes: their pre-rotation boxes overlap while the real geometry radiates
  apart, which is fifteen false positives from a six-petal coronet alone.
- **An outer layer is the same boxes again, inflated, with alpha.** That is all a
  player skin's hat, jacket and sleeves are, and it works for any entity: a
  second `addBox` at a `CubeDeformation`, mapped elsewhere on the sheet, with
  everything the garment does not cover left transparent. An entity model's
  render type is a cutout, so alpha is a straight keep-or-discard and there is
  no draw order to get wrong. Two rules learned by breaking them: an overlay
  covering most of its rectangle is not a garment but a fatter limb, and **a band
  must never span a joint**, because it tears in half the first time the joint
  bends.
- **Splitting a limb into two bones removes overlaps rather than adding them.**
  Parts joined end to end meet at a pivot; one long box beside another long box
  interpenetrates. Giving the Nymph elbows, knees and ankles took her coplanar
  count to zero by itself.
- **Writing `keyframe.data_points[0].x` directly does not refresh the
  viewport.** The value is stored, `Codecs.project.compile()` writes it out
  correctly, and the animation on screen keeps playing the *old* motion. So a
  file that has already been fixed still looks broken, which is worth more than
  it sounds: acting on that reading, I "fixed" a correct animation by negating
  all seventy keyframes and genuinely broke it, then had to undo that. Follow a
  direct write with `animation.setLength(animation.length)` and
  `Timeline.setTime(Timeline.time)`, and when the question is which way a joint
  bends, reload the project rather than trusting what is on screen.
- **A knee and an elbow fold in opposite directions, so no global sign flip can
  ever fix both.** This is the trap that turned one wrong sign into four rounds
  of whack-a-mole: negating every keyframe fixed the legs and broke the arms,
  negating back fixed the arms and broke the legs, and each round looked like
  progress because one complaint really had gone away. A knee carries the ankle
  *behind* the joint and an elbow carries the hand *in front* of it, so their
  keyframes need opposite signs and each has to be measured on its own.
- **Check a joint's phase as well as its sign.** The Nymph's knees had both
  wrong: they folded during *stance*, half a cycle from the swing they belong
  to, which reads as a march. Find when the thigh is travelling forward — that
  is the swing — and put the deepest fold in the middle of it. A knee bending at
  the wrong moment looks almost as wrong as one bending the wrong way, and the
  two faults disguise each other.
- **Settle a direction by measuring the scene graph, never by looking at a
  render.** Blockbench's front is `-z`, and a model normally proves that itself
  — the Nymph's toes run from z -3 to +1 and her eyes are on the *north* face.
  Then rotate the bone and transform a local point through `mesh.matrixWorld`:
  `forearm_right` at Blockbench x +30 puts the hand at z -3.5, so positive
  swings a limb forward, full stop. Three separate attempts to read this off a
  screenshot produced two different answers and a flip-flop; the arithmetic took
  one call.
- **The MCP animation tools do not agree with each other about sign, so never
  trust a round trip through them.** `create_animation` negated the x it was
  given; `manage_keyframes` edit did not, and its readback showed the old value
  twice running while cheerfully reporting success. An elbow bending backwards
  survived two "fixes" that way, because writing a value and reading the same
  value back looks exactly like a correction that worked. Set `data_points[0].x`
  through `risky_eval`, which has no convention of its own, and read it straight
  back. Then convert once, on the way out to Kotlin, with the documented rule.
- **`createKeyframe` snaps the time to `Timeline.getStep()`, which is read off
  the *selected* animation and not the one being written to.** Ask for keys at
  0.1 intervals while the walk (`snapping: 12`) happens to be selected and they
  land on `0, 0.0833, 0.1667, 0.3333, …` — eleven keys, uneven spacing, two
  segments silently twice as wide as the rest, which changes the speed profile
  because Catmull-Rom parameterises by normalised segment time. Set
  `anim.snapping` **and** `anim.select()` before creating anything, then read
  the times back.
  This is the sharpest instance yet of an instrument failing in a way that
  looks like a result: every one of the 165 *values* read back exactly as
  written, which is precisely the confirmation one goes looking for, and the
  field that had been mangled was a different one. **Read back the thing you
  did not touch**, not just the thing you set.
- **Verify the Kotlin by parsing it back and diffing against the `.bbmodel` —
  and make the *already-shipped* animation the control.** A verifier run only
  against the definition it just generated proves nothing; both halves share
  whatever the generator got wrong. Running the same diff over the walk, which
  was hand-transcribed in an earlier session, is what proves the conversion
  rule and the parser are right. 75 keyframes and 165 keyframes, both exact.
- **A limb hangs +y from its pivot, so a positive `xRot` swings the far end to
  +z — which is *behind* her, because the model faces −z.** Elbows and knees
  therefore bend forward on a *negative* model xRot. Deriving this is possible
  and gets got wrong anyway; the one-tool-call check is to set a bone to a large
  unmistakable angle in Blockbench and look at which way the hand goes. A subtle
  value at a mid-cycle frame is unreadable, which is why the first two attempts
  proved nothing.
- **`Animator.preview()` is not reliable enough to verify a pose.** It silently
  stopped applying — model at rest, animation selected, timeline at the right
  moment — and a render of that is indistinguishable from an animation with no
  motion in it. Check the *stored numbers*, and prove direction separately with
  a manual group rotation, which does apply.
- **Hair cannot lag behind a head it is nested inside.** A counter-rotation on
  the hair bone made it trail a look, which looked lovely and was unshippable:
  the locks hang about 3.2 from the head's axis while the head is a *square* of
  half-width 3.0 whose corners reach 4.24, so anything sweeping round at a fixed
  radius passes through them. There is no lag small enough to be safe, only one
  small enough to hide. Hair attached to a scalp turns with the scalp; put the
  life somewhere that cannot intersect, such as a pitch on a part that hangs
  clear behind.

## Item art

- **A sprite is not its texture.** Every charm's drawing occupies 12x14 of its
  16x16 sheet — the rest is the transparent margin the outline convention leaves
  — so anything that sizes an item against the *sheet* silently loses a quarter
  of what it asked for. Sizing the charm on the altar at 0.40 of a block put 4.8
  block pixels inside an 8-pixel ring, and it read as a charm floating in a
  setting made for something bigger; the arithmetic said 80% and the screenshot
  said 60%. Measure the alpha bounding box (`Image.getchannel("A").getbbox()`)
  and scale against *that*. The same correction applies to anything else drawn at
  a size relative to its surroundings — item frames, held models, particles cut
  from item sheets.
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

### Ramps, from <http://rjanes.com/tutorials/introduction_to_pixel_art.php>

Rules that earned their place by fixing the Unfinished Epitaph. Its ramp ran
23, 38, 54, 70, 84, 107 at a flat hue, and every fault below was in it.

- **Adjacent shades must differ by enough to be picked out — about 25
  luminance.** The Epitaph's steps were **14 to 16 apart**, which is the
  guide's own example of seven shades where "it's near impossible to identify
  between them". Widening them to 34-40 was the single change that did the most
  work. This is one line to assert and worth asserting every time.
- **Five shades is the ceiling at sixteen pixels.** A sixth is a step nobody
  can see.
- **A ramp needs a temperature direction, and saturation can carry it instead
  of hue.** The guide's letter is a warm saturated highlight against a cold
  desaturated shadow; applied literally to deepslate that produced an **amber**
  marker, correct by the tutorial and wrong for the mod, because the same sheet
  drives a Blockbench model that has to keep looking like rock. Holding the hue
  inside the original blue-grey band and making the *shadow* the most saturated
  blue against a nearly neutral highlight reads as cold-against-warm without
  leaving grey — and it is the same "lower the saturation down to grey" move
  the guide describes, simply stopped at grey rather than continued through it.
  What is never acceptable is the shipped arrangement: hue flat to three
  decimals **and** saturation running backwards, so there is no temperature at
  all.
- **Pillow shading — progressive shades running parallel to the outline — is
  the one thing the guide calls a crime.** Shade from a direction instead. It
  is testable without eyes: under pillow shading a pixel and its reflection
  through the shape's centre carry the same tone, since both sit the same
  distance from the edge. Assert that fewer than half of mirrored pairs match.
- **Take the flat black outline out and outline selectively.** Vanilla does
  ring solid shapes (see `resistance`), but the guide's last step is to replace
  the edge with *another dark shade from the palette* wherever a lighter shade
  meets it. Two edge tones — the darker on the shadow side, a lighter dark on
  the lit upper left — keeps this mod's unbroken ring, which is what lets an
  item hold a slot at either inventory grey, while giving the ring a light
  source.
- **A shade that exists only in the palette is not in the sprite.** The Epitaph
  defined a `STONE_FRESH` of `#939CAD` for an abandoned carving pass and never
  drew it, so the item topped out at 107 with its own highlight sitting unused.
  A later draft did it again from the other end: a five-shade ramp whose
  darkest tone was masked everywhere by the edge pass. Print a colour histogram
  over the finished sprite; a shade with a count of zero is a shade to delete
  or a bug to fix.
- **A highlight on five pixels of a hundred and twelve is not a highlight.**
  Brightness that peaks at a single coordinate lands nowhere. Give it a
  plateau.
- **Luminance in the file is not brightness in the world.** The block face was
  weighted to 66 mean so it would sit with the graves at 64 and 69 — checked
  against the texture files and never against the render. Minecraft shades
  vertical faces (about x0.8 north/south, x0.6 east/west), and the Epitaph is a
  thin slab showing mostly those, where a grave is a full block lit on top. The
  placed marker came out at roughly *half* the brightness of the same marker in
  the hand. Compare a screenshot of the placed block, not two histograms.

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

## Keyframe animation

Everything below is 26.2's `net.minecraft.client.animation` package, taken from
bytecode. The Blockbench half of the story is in the Blockbench section above;
this is what the game does with the result.

- **Blockbench cannot export a Java animation** — there is no such codec in the
  list, only the model ones. So a keyframe animation is read out of the project
  (`Animation.all[n].animators[uuid].rotation[i].data_points[0]`) and written
  into an `AnimationDefinition` by hand, exactly as the `LayerDefinition` is.
- The shape is `AnimationDefinition.Builder.withLength(seconds).looping()
  .addAnimation("bone", AnimationChannel(Targets.ROTATION, Keyframe(t,
  KeyframeAnimations.degreeVec(...), interpolation), ...)).build()`. Bake it
  **once** per model instance with `definition.bake(root)` — the public entry is
  on `AnimationDefinition`; `KeyframeAnimation.bake` itself is package-private —
  and drive it with `applyWalk`, `apply(AnimationState, ageInTicks)` or
  `applyStatic`.
- **`applyWalk`'s third argument is a time rate, not an angle**, whatever the
  usual parameter name suggests. The body is
  `time = (long)(limbSwing * 50 * arg3)` in milliseconds and
  `blend = min(limbSwingAmount * arg4, 1)`, so a one second animation completes
  a cycle every `20 / arg3` of `walkAnimationPos`. That third number is the
  one to reach for when a walk looks like a scurry.
- **There are exactly two interpolations**: `AnimationChannel.Interpolations`
  holds `LINEAR` and `CATMULLROM` and nothing else. Blockbench's graph editor
  will cheerfully author bezier easing, which cannot be represented — turn it
  back into a smooth curve *before* transcribing, or the motion silently stops
  matching the preview it was approved from.
- **CATMULLROM clamps its keyframe indices at the ends instead of wrapping.**
  `p0` is `keyframes[max(0, i-1)]` and `p3` is `keyframes[min(len-1, j+1)]`, so
  a looping animation gets a *velocity* kink at the seam — position is
  continuous, the tangent is not. Two things follow. The two sides of a
  symmetric pair are not exact mirrors near t=0 (the Nymph's legs are 4.4°
  apart there; her already-shipped walk is 3.1° apart in the same place on the
  same bone), so **measure the shipped animation as the control** before
  treating your own as broken. And clamping is sometimes the *better*
  behaviour: at her toe-off it holds the knee still through late stance and
  then snaps it, where a correctly wrapped tangent would start the heel
  flicking up while the foot was still pushing.
- **`applyWalk` comes apart, and that is how you crossfade two gaits.** Its
  whole body is `timeMs = limbSwing * 50 * rate`, `weight =
  min(limbSwingAmount * blend, 1)`, then `apply(timeMs, weight)` — and
  `apply(long, float)` is public. The weight is handed to
  `Interpolation.apply` as a plain scale on the output vector, so two
  definitions applied at complementary weights sum to a genuine linear blend of
  the two poses (see the previous bullet: the targets add). Two conditions.
  Both animations must be driven from **one shared `timeMs`** and be the same
  length, or the cycles drift and the blend cancels the limbs it is mixing; and
  they must be authored **in phase** — the Nymph's walk and run both put the
  right thigh at its rearmost at t=0, and disagree in sign for under 3% of the
  cycle. The rate cannot be part of the blend: it multiplies an ever-growing
  `walkAnimationPos`, so changing it jumps the phase rather than easing it.
- **`walkAnimationSpeed` cannot tell a walk from a run.**
  `LivingEntity.updateWalkAnimation` is `min(blocksMovedThisTick * 4, 1)`, so
  it saturates at a quarter of a block per tick — which every mob worth calling
  fast is already past. Do not infer a gait from it. The Nymph syncs one bit
  saying whether she has a target (`MeleeAttackGoal` is the only goal that
  moves her at chase speed, and it only runs when one is set), eases it on the
  *entity* the way `wrathAmount` is eased, and lets `walkAnimationSpeed` go on
  doing the one job it is good at: scaling both gaits by whether she is moving
  at all. So a Nymph swinging from a standstill is at full run weight and shows
  no stride.
- **There is no airborne phase to measure, so do not build a check on one.**
  A Minecraft model's root never translates: the feet are decorative with
  respect to the ground and every gait reads as "off the ground" for most of
  its cycle. A check written to prove the Nymph's run had a flight phase duly
  failed its own control by declaring the shipped walk 42% airborne. What
  actually separates the gaits is the size of the excursions, measured on both
  with the same instrument — foot lift 1.27 → 6.45 units, stride 40° → 87°,
  knee −48° → −105°, arm swing 26° → 82°.
- **A baked animation *adds*; it does not overwrite.**
  `AnimationChannel.Targets.ROTATION` is a method reference to
  `ModelPart::offsetRotation`, whose entire body is three `+=`, and POSITION is
  `offsetPos`. So every keyframe is a **delta on the rest pose**, not a
  replacement for it — which is why a `PartPose` rotation baked into the model
  (the Nymph's arms carry a 7° outward roll) survives the animation instead of
  being thrown away by it. This entry previously said the opposite, on no
  evidence; the conclusion it drew — compose the animation first and add
  everything else with `+=` — happens to be right anyway, but for a different
  reason. Head tracking needs one more step: head, neck and torso
  compose, so a look applied on top of a walk that already turns all three
  overshoots. Add the *residual* — the target minus what the animation has
  already contributed — and the total lands exactly on the target with the
  walk's sway intact underneath.

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

## Design: a mob that keeps accounts

From the Nymph, and all three were found by watching a log rather than by
reading the code.

- **A threshold compared against a decaying number has to be a latch.** Her
  temper asked `grievance >= WRATH_AT` every tick while the grievance fell a
  point every five seconds, so one that crossed at 58 dropped back under 55
  about ten seconds later — the log shows "Enough." and then a *wary* line
  twelve seconds after it. At the boundary she flickered between hunting and
  not, and the target was dropped and re-taken with her. Entering a state must
  be harder than staying in one; twelve points of hysteresis turned three temper
  changes in two minutes into one.
- **Do not derive which line a character says from the parity of a counter.**
  Hers alternated — odd turns took an observation about the world, even turns
  advanced a story. In plain daylight with an empty hand there is *nothing* to
  observe, so half of every first conversation was filler, and turn seven
  produced "I have nothing else for you today" with three lines still unsaid.
  Give the thread its own counter that moves only when the thread is used. Then
  the fallback lines appear only when there is genuinely nothing left, which is
  what they were written for.
- **A message sent by UUID arrives from anywhere in the world.** A Nymph
  forgiving somebody four minutes after a fight messaged them mid-conversation
  with a *different* Nymph, which reads as a bug because there is no speaker on
  a chat line. Anything a mob says needs a distance check as well as a
  recipient.

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

## concept/ — where an idea goes first

`concept/` in the project root is untracked scratch space (it is in
`.gitignore`, and so is everything under it). **Anything new gets worked out
there before it is built**: sprite drafts, a mechanic written up in prose, a
contact sheet, a script that exists to answer one question and then dies.

Untracked on purpose. The point is to be free to be wrong in there, and a
half-finished design sitting in the history reads like a decision that was made.

It is not a detour — it is where the cheap version of every expensive mistake in
this file would have been caught. The Mourner's Feather drafts that read as a
lozenge on a stick, the charm depths that had to be checked against what an
amplifier actually does, the stair arithmetic that found two bugs before a world
existed: all of that is concept work, and all of it is cheaper than a playtest.

When a draft survives it **moves out** and is committed where it belongs —
generators to `tools/`, art to `src/main/resources/assets/vanguard-spirits/`,
code to `src/`. Nothing tracked may reference a path under `concept/`, since no
other checkout has one.

## Releases

**Write the changelog entry as each change lands, not the whole release at the
end.** A release is usually the last thing in a long session, which is exactly
when the session has been compacted and the early work is no longer in context —
so the entry gets reconstructed from a summary rather than recalled. That is
where invented details come from: a number that was measured at one value and is
written down as another, a bug described by the first theory about it rather than
the cause that was actually found, a fix credited to the wrong file.

The failure is quiet, too. A changelog is prose, so nothing type checks it and
nothing fails to compile. It ships, and it is the one artefact players read.

An entry written the moment a change is finished costs nothing extra — the
reasoning is already in the working memory that produced the code — and by the
end of the session the changelog is a matter of ordering entries rather than
remembering a day's work.

The same argument applies to anything else written from memory at the end of a
long session: **the CLAUDE.md entry for a lesson goes in when the lesson is
learned.** This session moved a number from 64 to 32 on the strength of five
measured search distances; a day later those five numbers are gone and only the
conclusion is left, which is the half that cannot be checked.

Concretely: append to `branding/changelog-<version>-modrinth.md` and its
CurseForge twin as the work happens, and only do the version bump, the jar pair
and the commit at the end.

## Conventions

- Tabs for indentation in Kotlin, matching the template.
- Commit messages: imperative subject, body explaining *why*, and
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Remote is a private repo: <https://github.com/Fresh-glitch/vanguard-spirits>
