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
- Creative tabs: `net.fabricmc.fabric.api.creativetab.v1.**FabricCreativeModeTab**.builder()`.
  `FabricItemGroup` (in most tutorials) does not exist here.
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

## Datagen

Providers are registered in `VanguardSpiritsDataGenerator`. After changing items,
lang, or recipes:

```bash
./gradlew runDatagen
```

Commit the resulting `src/main/generated/` changes. Translation keys are added
through `ModItems.loreKey(path)` and the provider, never hand-edited into JSON.

## Tooling status

- **Java LSP** (`jdtls-win@fresh-local`) — **working** for `.java` (mixins).
  `documentSymbol` and `hover` both resolve, and hover reaches into
  `minecraft-common-*.jar`, so the server has the full dev classpath.
  `workspaceSymbol` returns nothing for library types, so it does **not** replace
  `javap` for discovering Minecraft/Fabric API.
- **Kotlin LSP** — **not working.** Two independent causes: the official plugin's
  manifest ships without its `lspServers` block (the same "config lost in transit"
  bug worked around locally for jdtls), and no server binary is installed — its
  README documents only `brew install`, which is macOS/Linux. Fixing it needs a
  local marketplace entry plus a downloaded standalone release.

Until Kotlin LSP works, the fastest correctness check for Kotlin is the compiler
itself — `./gradlew clientClasses` takes a few seconds and catches every wrong
signature.

## Conventions

- Tabs for indentation in Kotlin, matching the template.
- Commit messages: imperative subject, body explaining *why*, and
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Remote is a private repo: <https://github.com/Fresh-glitch/vanguard-spirits>
