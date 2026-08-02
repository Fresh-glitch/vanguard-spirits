# Vanguard Spirits

Minecraft **Fabric 26.2** mod in **Kotlin**, built for a CurseForge modjam themed
**"Echoes of the Past"**. Mod id `vanguard-spirits`, group
`io.github.freshglitch.vanguardspirits`.

Core mechanic: **Guarded Ruins** yield **Fractured Memories**, which are crafted
into **charms** that grant passive auras.

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

So reach for the instrument early, not after the third theory:

| Question | Instrument |
| --- | --- |
| What does this API actually do? | `javap -c` on the dev classpath |
| Which way does this bone rotate? | pose it in Blockbench, read the Java export |
| What is the server actually seeing? | temporary `LOGGER.info`, ask the player to trigger it |
| Is this process working or stuck? | `jstack`, and process CPU time |
| Is this `.ogg` the right length? | `ffprobe`, or granule position over sample rate |
| Is this PNG intact? | walk the chunk list; `file` will happily bless a truncated one |

Two corollaries worth their own line. **A comment is not evidence** — several in
this repo confidently described the opposite of what the code did. And **silence
is not confirmation**: when the Bulwark worked, the log went quiet, which looks
identical to the feature never running. Decide in advance what success will
*emit*.

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
- **`TagAppender.addTag` validates that the target is defined by the same
  provider.** Referencing a vanilla tag needs `addOptionalTag`, or datagen fails
  outright.

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
- **Screens register per `MenuType`.** Styling a screen for a *vanilla* menu type
  restyles every vanilla block using it — reusing `GENERIC_3x3` would have given
  the Reliquary's panel to every dispenser in the game. Custom interface means a
  custom menu type, menu and screen.
- Owning the menu also lets `ContainerOpenersCounter.isOwnContainer` ask which
  container is open; vanilla's `DispenserMenu` exposes no accessor for its own.
- 26.2 screens draw in **`extractBackground(GuiGraphicsExtractor, ...)`** with
  `extractor.blit(RenderPipelines.GUI_TEXTURED, ...)`. There is no `renderBg` and
  no `GuiGraphics.blit`.
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
- **Never hand-transcribe a data URL.** Base64 for a 128x128 PNG runs to
  thousands of characters and dropping twelve bytes yields a file that still
  reports `PNG image data, 128 x 128` to `file` and still parses its IHDR —
  it fails much later, in game, as `unknown PNG chunk type`. Blockbench is
  Electron: `require("fs").writeFileSync(path, Buffer.from(b64, "base64"))`
  inside `risky_eval` writes the real bytes. Verify by walking the chunk list;
  a good PNG ends IHDR … IDAT … IEND.

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

Charms apply their aura **from anywhere in the inventory**, but only the first N
in slot order, where N is the player's **attunement** (starts 1, caps 4). Vanilla
numbers the hotbar first, so players set priority by moving charms to the hotbar.
Attunement is a Fabric data attachment: persistent, `copyOnDeath`, synced
`targetOnly`. **Echo of Kinship** is consumed to raise it.

`CharmScan.activeSlots()` is the **single source of truth** and lives in the
common source set, because the server tick applies auras and the client tooltip
labels them Attuned/Dormant. If the rule changes, change `CharmScan` — never
duplicate it in the ticker or the tooltip, or the UI will lie.

Adding a charm should only require a new `CharmAura`. No per-charm slot logic.

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
