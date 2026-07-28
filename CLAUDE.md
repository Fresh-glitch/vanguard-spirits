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
./gradlew -q -I cp.gradle printClientCp
```

where `cp.gradle` is an init script containing:

```groovy
allprojects {
    tasks.register("printClientCp") {
        doLast { println(project.configurations.getByName("clientRuntimeClasspath").files.join(File.pathSeparator)) }
    }
}
```

Then: `javap -cp "<that classpath>" net.minecraft.world.item.Item`. Use the JDK 25
`javap` at `%JAVA_HOME%\bin\javap.exe`, not the JRE on `PATH`.

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
