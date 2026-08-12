# AGENTS.md — guide for AI agents working on Storage Organizer

Orientation + hard-won knowledge so future sessions are fast and don't repeat mistakes.
Player docs: **[README.md](README.md)**. Architecture detail: **[DEVELOPMENT.md](DEVELOPMENT.md)**.
This file is the *how to work here* layer on top of those.

## What this is

A **client-side** Forge mod that adds user-defined **filter tabs** to **both** Applied Energistics 2
terminals **and** Refined Storage grids. It hooks each mod's *client-side* item view (a mixin per
backend) and draws a tab panel; there is **no server component**. The two systems are
**hard-separated** — independent stores/windows/settings, no shared filter. It must keep working
when joining a server that doesn't have this mod, and load cleanly with **either, both, or neither**
storage mod present. The separation seam is the backend SPI in `backend/`.

## Stack (don't guess — these are pinned)

This is the **1.20.1 (Forge)** line. (Sibling branches: `1.21.1` and `26.1`, both NeoForge.)

- Minecraft **1.20.1**, **MinecraftForge 47.4.20** (NOT NeoForge — AE2 15.4.10 ships only Forge/Fabric on
  1.20.1, and NeoForge 1.20.1 uses the same `net.minecraftforge` packages anyway). Java **17**.
- AE2 **[15.4,15.5)** and Refined Storage **[1.12,2.0)** — **both optional**, `side = "CLIENT"`. RS here is
  the **pre-rewrite 1.12** codebase (1.20.1 never got RS2), hooked by `backend/rslegacy`. guideme **20.1.x**
  is a required runtime dep of AE2 (standalone mod, not jar-in-jar — the dev client needs it explicitly).
  JEI **15.x** optional (dev-only).
- Gradle **8.10.2** + ModDevGradle **legacyforge** 2.0.141 (the `legacyForge { }` plugin; reobf to SRG +
  mixin refmap). Multi-project: minimal root + `neoforge/` subproject — the dir name is *historical*, it
  builds a Forge jar; kept so the shared default-branch CI's `:neoforge:build` keeps matching.
- Mod id `ae2organizer`, base package `nl.ljack2k.ae2organizer` — both deliberately **kept** through the
  rebrand so config paths (`config/ae2organizer/`), the clipboard export magic key and the published
  CurseForge/Modrinth projects stay intact. Only the *name* changed.
- **Two name properties** in `gradle.properties`: `mod_name=StorageOrganizer` is technical (it builds
  `archivesName`, so it must stay space-free) and `mod_display_name=Storage Organizer` is what players
  see (mods.toml `displayName`, `pack.mcmeta`, GUI strings, docs). Log prefixes use the technical form
  `[StorageOrganizer]`. Versions live in `gradle.properties` too.
- **Gradle launcher JDK:** Gradle 8.10.2 can't run on JDK 25 (this machine's default for the 26.1 line) —
  launch it on JDK 17/21, e.g. `JAVA_HOME="…/Eclipse Adoptium/jdk-21…" ./gradlew …`. The Java-17 toolchain
  (for the mod itself) is auto-provisioned via foojay regardless of the launcher JDK.

## MC 1.20.1 (Forge) deltas from the 1.21.1 source

Ported from `1.21.1`. The filter-core mixin targets are **UNCHANGED** on AE2 15.4
(`Repo.addEntriesToView`, `MEStorageScreen.repo`/`searchField`, `GridInventoryEntry.getWhat`), so the
mechanism ports 1:1. What differs (all verified via `javap` + a dev-client boot):

- **Loader API:** `net.neoforged.*` → `net.minecraftforge.*`. `@Mod` takes a **no-arg constructor**; get the
  mod bus via `FMLJavaModLoadingContext.get().getModEventBus()`, gate on `FMLEnvironment.dist`, use
  `MinecraftForge.EVENT_BUS`. `ScreenEvent.MouseScrolled.Pre#getScrollDelta()` (one delta, not `…Y()`).
- **Metadata:** `META-INF/mods.toml` (not `neoforge.mods.toml`). Deps use **`mandatory = true`** — NOT
  NeoForge's `type = "required"` (FML throws `InvalidModFileException: Missing required field mandatory`
  and the *whole* mod scan aborts, which also cascades into bogus "Missing language javafml" errors).
  `pack_format` **15**.
- **Vanilla Screen API:** `Screen.renderBackground(GuiGraphics)` is **1-arg** AND `Screen.render` does
  **not** call it — override the 1-arg form *and* call `this.renderBackground(graphics)` yourself at the top
  of `render`. `mouseScrolled` is **3-arg** `(double,double,double)`. `EditBox.moveCursorToEnd()` takes **no**
  arg. (`ResourceLocation.fromNamespaceAndPath/parse/withDefaultNamespace` all exist on 1.20.1 — keep using
  the factories; the bare constructors are deprecated-for-removal.)
- **No data components:** the `COMPONENT` filter is reworked to NBT (`ItemStack.isEnchanted()` +
  `StoredEnchantments`, `hasCustomHoverName()`, `isDamaged()`, top-level NBT key). `HAS_COMPONENT_TYPE`
  needs the component registry, so it is **kept in the enum but marked unsupported**
  (`ComponentMatch#supported()`): it still parses — a config or clipboard export from a 1.21+ line
  would otherwise fail to load — but never matches and is skipped by the editor's cycle button.
- **JEI 15.x** names `IGuiProperties` getters `getGuiLeft()/getScreenWidth()/…`; JEI 19.x dropped the
  `get` prefix.
- **AE2 15.4's `Icon` sheet has no `COG`** — `Icon.WRENCH` is the settings glyph on this line
  (`appeng.client.gui.Icon`, drawn via `Icon#getBlitter()`).
- **Gradle 8.10.2 cannot run on JDK 25.** `JAVA_HOME` here points at JDK 25 for the newer lines, so every
  Gradle call on this branch needs
  `-Dorg.gradle.java.home="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"`
  (the Java **17** toolchain for compilation is resolved separately and is unaffected).

## Two backends, and why the RS one is different here

This line carries the same **backend SPI** as the newer ones (`backend/StorageBackend` + `ScreenAdapter` +
`Theme`, per-backend stores in `TabManager`), with two backends registered: `backend/ae2` and
**`backend/rslegacy`**.

Refined Storage on 1.20.1 is **RS 1.12** — pre-rewrite, and its client grid API has nothing in common with
the RS2 one the 1.21.1/26.1 backends hook. So `rslegacy` is a separate implementation, not a port:

- **Filter hook:** `GridViewImpl#getActiveFilters()` (private, returns `Predicate<IGridStack>`). `forceSort()`
  does `map.values().stream().filter(getActiveFilters()).sorted(getActiveSort())`, so AND-ing into that return
  value filters the whole view alongside RS's own search/craftable/view-type filters — the analogue of RS2's
  `createBaseFilter` and AE2's `Repo#addEntriesToView`. Verified by `javap -c`.
- **Plain `@Inject` + `setReturnValue`**, not MixinExtras' `@ModifyReturnValue`: MixinExtras is not on the
  1.20.1 userdev compile classpath.
- **Re-filter** with `screen.getView().forceSort()` — *not* `sort()`, which defers to `GridScreen#canSort()`
  and silently no-ops.
- **Item extraction:** `IGridStack` → `ItemGridStack#getStack()`; fluids test as `ItemStack.EMPTY`.
- **Search box:** `GridScreen#searchField` is private; read reflectively (it subclasses vanilla `EditBox`).
- **Companion screens** (suppress the reset-on-open after a round trip): `CraftingSettingsScreen`,
  `CraftingPreviewScreen`, `AlternativesScreen`.
- The backend id stays **`"rs"`**, so the store is `config/ae2organizer/rs.json` and filter exports move
  between lines unchanged.

Consequences to remember: there are **two** mixin configs, `ae2organizer.ae2.mixins.json` and
`ae2organizer.rslegacy.mixins.json` (each gated by its own `IMixinConfigPlugin` on `LoadingModList`), and each
name appears in **three** places that must agree — the file, `mods.toml`, and `mixin { config … }` in
`neoforge/build.gradle`. A stale name there fails at launch with
`MixinInitialisationError: … was invalid or could not be read`, because the dev run passes them as
`--mixin.config`.
- **Codec dispatch:** DFU 6.0.8's `Codec.dispatch("type", …, fn)` wants the fn to return a **Codec** (1.21.1's
  wants a MapCodec) — `Condition.CODEC` adapts with `t -> t.codec().codec()`.
- **AE2 widgets:** AE2 15.4 has **no `AE2Button`** → local `client/gui/Ae2Button` (extends vanilla `Button`,
  draws via `Ae2Style.bevelButton`). Gear icon `Icon.COG` → **`Icon.WRENCH`** (15.4 has no COG). `AECheckbox`,
  `BackgroundGenerator`, `StyleManager`, `Icon#getBlitter()`/`Blitter` are unchanged.
- **Mixins need a refmap** (reobf to SRG). Top-level `mixin { add sourceSets.main, 'ae2organizer.refmap.json';
  config 'ae2organizer.mixins.json' }` **plus** `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`
  (the plugin does NOT add the AP). Only the **vanilla** `AbstractContainerScreenAccessor` (imageWidth/Height
  → SRG) is in the refmap; the **AE2**-targeting mixins use **`@Mixin(…, remap = false)`** (AE2 ships
  un-obfuscated — otherwise the AP fails with *"Unable to locate obfuscation mapping"*). `compatibilityLevel`
  **JAVA_17**. The dev-runtime `Reference map … could not be read` WARN is expected (the refmap is for the
  reobf production jar).
- **JEI tag-search prefix is `$`** on JEI 15.x (1.20.1), not `#` (which is `#` on JEI 19.x / the 1.21.1
  line — JEI changed it). The JEI-sync `conditionToJei` emits `$<tagpath>` for TAG conditions; `@mod` and
  name search are unchanged. (JEI indexes tags by `ResourceLocation.getPath()`, so the namespace is dropped.)
  Verified in the dev client 2026-06-28.

## Build / run / test

All Gradle calls on this branch need `-Dorg.gradle.java.home=<JDK 21>` (see the toolchain note above).

```bash
./gradlew :neoforge:build          # -> neoforge/build/libs/StorageOrganizer-forge-1.20.1-<ver>.jar (reobf'd to SRG)
./gradlew :neoforge:runClient      # dev client with AE2 + RS (+ JEI), opens a real window
./gradlew :neoforge:runClientJoin  # dev client that quick-joins 127.0.0.1:25565 (devHarness on)
./gradlew :neoforge:runServer      # dev server for the RCON/screenshot harness
```

- **Always `compileJava` after edits** — it's fast and catches AE2/RS/Mojang API mismatches.
- After `runClient`, confirm a clean boot by grepping the log for `StorageOrganizer ... Client loaded`,
  `Sound engine started`, and the *absence* of `exception` / `mixin ... fail`.
- **RCON/screenshot harness** (`dev/`), gated on `-Dae2organizer.devHarness` (set by `runServer` /
  `runClientJoin`). RCON on `:25575`, password `rsorg`. `/rsorgtest build|open` places and opens an RS
  grid, `/rsorgtest tab <id>` selects a tab, `/rsorgtest editor` opens the editor, `/rsorgshot` writes
  `run-client/screenshots/rsorgshot.png` (fixed name — Read it directly). Hard-won details:
  - **Server→client signalling is a chat marker** (`DevSignal`, prefix `[TO-DEV]`), *not* a
    `SimpleChannel`. A custom channel is fine on the server but wedges the dev client's login.
  - **Never register a `RegisterCommandsEvent` listener on the client dist** — commands are
    server-side, and registering it client-side was one of the login-stall suspects.
  - **Join over `127.0.0.1`, not `localhost`** — `localhost` resolves to `::1` first and the dev login
    stalls intermittently (client logs "Starting new vanilla impl connection", then sits until the
    server's 30s login timeout). If a join stalls anyway, just relaunch the client; it is flaky.
  - **`run-server/world` and `run-client/saves` are shared across branch checkouts.** A world written
    by the 1.21.1/26.1 lines crashes this server on load (`No key dimensions in MapLike[{}]`) — delete
    them when switching lines.
  - **`setblock` cannot build a *working* RS network.** The blocks appear and the grid opens, but RS
    1.12 registers network nodes on real placement, so an external storage placed this way never feeds
    the grid. Headless verification therefore covers the panel, tab selection and JEI sync — but not
    filtering of real grid contents. That one needs the maintainer.
- **The dev client has AE2 + RS + JEI.** Addon terminals (e.g. the Wireless Crafting Grid) are
  **not** here, so bugs specific to them can't be reproduced in dev — the maintainer tests those in
  their real modpack. To reproduce one here, add the addon as a dev-only `runtimeOnly` in
  `neoforge/build.gradle` (ask which mod first).
- `runClient` is long-running; launch it in the background and poll the log. Kill a stray client by
  PID (CIM filter on `CommandLine` containing `AE2-Organizer` and `minecraft|fml|bootstraplauncher`,
  excluding `GradleDaemon`).

## THE GOLDEN RULE: verify AE2/MC APIs with `javap` before writing code

AE2's terminal classes and the `appeng.client.gui.style.*` / `widgets.*` classes are **internal,
not public API**. Method names/locations differ from what you'd assume and shift between versions.
Every time this session guessed, it cost a build cycle; every time it `javap`'d first, it was right.

JDK 21 (`javap`) is on PATH (Adoptium). Recipe:

```bash
javap -p -classpath "<jar>" appeng.client.gui.me.common.Repo            # member list
javap -c -p -classpath "<jar>" appeng.client.gui.style.BackgroundGenerator   # bytecode (arg order, etc.)
```

Jars to inspect (find the hashed ones with Glob — paths drift):

- **AE2 15.4 + JEI 15.x**: the *real modpack* jars (the maintainer's pack) —
  `C:/Users/ljack/AppData/Local/.ftba/instances/ftb presents architects exodus/mods/appliedenergistics2-forge-15.4.10.jar`
  and `…/mods/jei-1.20.1-forge-15.20.0.132.jar`. (AE2 internals shift between versions — javap *this* AE2, not 19.x.)
- **Compiled MC+Forge** (Mojang-mapped, for `javap` on vanilla `Screen`/`EditBox`/`ResourceLocation`/`GuiGraphics`):
  `neoforge/build/moddev/artifacts/forge-1.20.1-47.4.20.jar` (exists after a build). Forge sources for reading
  patched `.java`: `forge-1.20.1-47.4.20-sources.jar` alongside it.

JackItToMe (`D:/Projects/JackItToMe`) is the reference AE2 addon — copy its gradle/toml patterns.

## Non-obvious gotchas (these are the robustness wins)

- **Plain `Screen`, never `AEBaseScreen`.** AEBaseScreen needs a server-side container menu, which
  breaks the client-only/any-server guarantee (and would desync inventory). All our screens are
  vanilla `Screen`s, themed manually.
- **Theme through AE2 so dark-mode packs apply.** Use `BackgroundGenerator.draw(w,h,g,x,y)` for the
  panel, `StyleManager.loadStyleDoc(...).getColor(PaletteColor.*)` for text, AE2 widgets
  (`AECheckbox`, `Icon.WRENCH` via `Icon#getBlitter()`), and a local `Ae2Button` (AE2 15.4 has no text button).
  These read `background.png`/`palette.json`,
  which is exactly what AE2 dark-mode resource packs override. All wrapped in `Ae2Style`.
- **AE2's `AETextField` renders border artifacts outside a container screen** — use a vanilla `EditBox`.
- **Replace the vanilla menu background** by overriding `renderBackground` to a plain dim (`Ae2Style.DIM`)
  + your panel. On 1.20.1 it's the **1-arg** `renderBackground(GuiGraphics)`, and `Screen.render` does *not*
  call it — so invoke `this.renderBackground(graphics)` yourself at the top of `render` (see 1.20.1 deltas).
- **AE2's terminal eats scroll/drag.** `MEStorageScreen` overrides `mouseScrolled`/`mouseDragged` and
  consumes them before added widgets get them (clicks *do* forward). Route the tab bar's wheel/drag
  through cancelable **`ScreenEvent.Mouse*.Pre`** events (see `ClientEvents`); the widget only renders.
- **Item icons render at a fixed 16px** (`GuiGraphics#renderItem`). To make them smaller, scale the
  pose (`Ae2Style.scaledItem`). Don't enlarge the buttons to "fit" — the maintainer means smaller icons.
- **Tab-bar offset:** anchor past the panel image *and* any real `menu.slots`, measured to the slot's
  **18px frame** (item is 16px + a 1px border). This clears terminals with extra card slots. Do **not**
  use `getExclusionZones()` — it includes the top-right help button and overshoots.
- **Container screens throw the carried stack on the mouse RELEASE, not the press.** Cancelling
  `MouseButtonPressed.Pre` over an overlay panel is not enough: the paired
  `MouseButtonReleased.Pre` still reaches `AbstractContainerScreen`, which treats a release outside
  its own bounds as click-outside → throw. Swallow the release too (see the tab bar's
  `handleMouseRelease` / `ClientEvents.swallowNextRelease`).
- **Tags use the `forge:` namespace** on Forge 1.20.1 (`forge:ingots`), not NeoForge's `c:`.
- **The `Repo` filter funnel** is `addEntriesToView(Collection)` — both `updateView()` branches pass
  through it before sorting, so `@ModifyVariable` at HEAD there filters the whole view and AND-combines
  with AE2's search box. `updateView()` is public — call it to re-filter.
- **`GridInventoryEntry` is in `appeng.menu.me.common`**, not `client.gui`.
- Mixins: `required: true`, all under `client`. **Refmap required** (reobf to SRG) for the vanilla
  `AbstractContainerScreenAccessor`; AE2-targeting mixins use `@Mixin(…, remap = false)`. See the 1.20.1 deltas.

## Verified API quick-reference (confirmed via javap this session)

- `appeng.client.gui.me.common.Repo`: `private void addEntriesToView(Collection<GridInventoryEntry>)`,
  `public final void updateView()`, `getSearchString/setSearchString`.
- `MEStorageScreen`: `protected final Repo repo` (→ `@Accessor`). Extends `AEBaseScreen` which has
  `public final int getGuiLeft()/getGuiTop()`.
- `AEKey` (AE2 15.4): `getModId()`, `getDisplayName()`, `isTagged(TagKey<?>)`, `getId()`. **No** data-component
  methods on 1.20.1. `AEItemKey`: `getReadOnlyStack()`, `toStack()`, `getItem()`, `isDamaged()`, `getTag()`,
  `copyTag()`, `hasTag()` (NBT-based, not components).
- `appeng.client.gui.style.BackgroundGenerator.draw(int width, int height, GuiGraphics, int x, int y)`.
- `appeng.client.gui.style.StyleManager.loadStyleDoc(String)` → `ScreenStyle.getColor(PaletteColor)` →
  `Color.toARGB()`. Palette paths: `/screens/common/common.json` (includes `palette.json`).
- `appeng.client.gui.style.Blitter`: `texture(...)/.src(...).dest(x,y,w,h).colorArgb(int).blit(GuiGraphics)`.
- Widgets (AE2 15.4): **no `AE2Button`** — use local `client/gui/Ae2Button` (extends `Button`).
  `AECheckbox(x,y,w,h,ScreenStyle,Component)` with `isSelected/setSelected`; `appeng.client.gui.Icon.WRENCH`
  (no `COG`) + `Icon#getBlitter()` → `Blitter`.
- JEI: `IGhostIngredientHandler#getTargetsTyped(T, ITypedIngredient<I>, boolean)`,
  `ITypedIngredient#getItemStack(): Optional<ItemStack>`; `IGuiHandlerRegistration#addGhostIngredientHandler`
  and `#addGuiScreenHandler(Class<T>, IScreenHandler<T>)` where `IScreenHandler` returns an `IGuiProperties`
  (panel bounds → lets JEI draw its overlay beside a non-container screen);
  ghost handlers are a `ListMultiMap` and lookup **combines every handler whose registered class
  `isInstance`s the screen** — so registering for `MEStorageScreen` coexists with AE2's own
  `AEBaseScreen` handler (pattern-slot drags unaffected). `Screen.class` itself is blacklisted for
  registration. `getTargetsTyped(..., doStart=false)` is the hover-hint query (fires while merely
  hovering a JEI ingredient); return `List.of()` there to highlight targets only during a real drag —
  mid-drag highlights draw from the `doStart=true` snapshot, so they survive. (Verified on this
  line's pinned JEI file 8292131 as well as 1.21.1's 7420587 and 26.1's 8014757.)
- NeoForge: `Screen.render` calls `renderBackground`; `ScreenEvent.Init.Post#addListener` adds a
  renderable widget; `ScreenEvent.Mouse{ButtonPressed,ButtonReleased,Dragged,Scrolled}.Pre` are cancelable.

## Release & git workflow

- **Branch per Minecraft line:** `1.21.1` (default) and `26.1` — long-lived and parallel; there is no
  `develop`/`main`. Work on the branch for the MC line you're targeting. Origin is
  `https://LJack2k@github.com/LJack2k/AE2-Organizer.git`.
- Bump `mod_version` in `gradle.properties`; the jar name and `neoforge.mods.toml` version expand from it.
- **Release tags carry the MC line:** `v<mod_version>-mc<mcline>` (e.g. `v1.2.0-mc1.21.1`,
  `v1.2.0-mc26.1`) so tags stay unique across branches. Only `v*` tag pushes publish; the version-aware
  workflows read `minecraft_version`/`java_version`/`mod_version` from `gradle.properties` (configure a
  new MC line there, not in the workflow files).
- Commit style: small, logical, conventional-ish (`feat:`/`fix:`/`docs:`/`chore:`). End commit messages
  with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. **Commit/push only when asked.**
- When grouping a big change into several commits, order them so each compiles (foundation → features
  → release bump); note that a `Settings` record change drags its constructor call sites with it.
- `.gitignore` already excludes `build/`, `.gradle/`, `**/run/`, `*.log`, `libs/*.jar`. The maintainer
  sometimes commits assets (e.g. `branding/`) themselves between turns — `git fetch`/check before pushing.

## Release gotchas (learned the hard way shipping 2.0.0)

- **Merge/push the DEFAULT branch first.** `publish-curseforge.yml` and `publish-modrinth.yml` are
  `workflow_run`-triggered, so GitHub always runs the copy sitting on the repo's **default branch**
  (`1.21.1`) — regardless of which line you tagged. Tag any other line before that merge lands and
  it publishes with the default branch's *stale* metadata. This nearly shipped "AE2 required, no
  Refined Storage" on all three lines. (`release.yml` is a plain `push` trigger and does come from
  the tag, so the jar itself is fine — only the platform metadata is affected.)
- **`pack_format` is per MC line; never copy it forward.** 15 on 1.20.1, 34 on 1.21.1, **84 on
  26.1**. From 26.1 the pack scheme gained minor versions: `PackFormat.lastPreMinorVersion` is
  **64** (client resources) / **81** (data), and a bare `pack_format` above that is *rejected* —
  `"missing mandatory fields min_format and max_format"` — after which NeoForge silently falls back
  to a description-only pack. Declare `min_format`/`max_format` alongside it. This is exactly why
  AE2 (8), RS (34) and JEI (46) all ship "stale" low values on 26.1: staying under the threshold is
  what keeps a bare `pack_format` legal.
- **Bump `mod_version` before tagging.** Every line shares the version number and tags are
  `v<ver>-mc<line>`, so a forgotten bump collides with an existing tag.
- **CurseForge's "Client" environment tag is manual** per uploaded file — mc-publish cannot set it.
- The dev harness (`dev/`, `client/ClientScreenshot`, `client/DevClientActions`) is **excluded from
  the published jar** by `tasks.named('jar')`. Dev runs are unaffected — they run from the compiled
  classes, not the jar. If you add a dev-only class, put it where that exclusion catches it.

## Exercising the load-safety guarantee

`-PnoAe2` / `-PnoRs` / `-PnoJei` drop a mod from the **dev runtime only** (`compileOnly` untouched,
so the build is unaffected). This is the only way to actually test "loads with either, both, or
neither" — every test before 2.0.0 had silently run with both present.

```bash
./gradlew :neoforge:runClient -PnoRs             # AE2 only
./gradlew :neoforge:runClient -PnoAe2            # RS only
./gradlew :neoforge:runClient -PnoAe2 -PnoRs     # neither
```

- **Use `guideme` as the tell for "is AE2 loaded"** when grepping a log. Grepping `AE2` matches our
  own strings (`ae2organizer.ae2.mixins.json`, the "AE2 terminals" log line) and yields false
  positives; `guideme` is gated together with AE2 and appears only when it is really there.
- **A loader-less-server join only proves anything with every other mod dropped.** NeoForge refuses
  the connection when *any* loaded mod requires it ("you are trying to connect to a server that is
  not running NeoForge, but you have mods that require it") — that is AE2/RS/JEI, not this mod. Test
  with `-PnoAe2 -PnoRs -PnoJei`. Forge 1.20.1 is more permissive and joins even with all of them on.

## Harness traps that cost real time

- **On 26.1, `/rsorgshot` writes a TIMESTAMPED file**, not the fixed `rsorgshot.png` documented
  above — 26.1's `Screenshot.grab` lost its filename argument. Read the newest `*.png` instead.
  Assuming the fixed name produced a completely false "the dev payloads aren't reaching the client"
  conclusion; they were arriving the whole time.
- **`/rsorgtest tab <id>` targets the backend of whichever screen is open.** Passing an id from the
  AE2 store while an RS grid is open is a silent no-op (and vice versa). Read the ids from the right
  `config/ae2organizer/<backend>.json` — `tabs.json` is AE2, `rs.json` is RS.
- **`DevClientActions.selectTab` syncs the ingredient viewer unconditionally**, unlike the real
  click path which honours `syncViewerOnTabSelect`. Harness JEI-sync observations therefore
  overstate what a player would actually see.
- **RS content filtering is not reachable headlessly on any line.** `setblock` cannot wire a working
  RS network (RS 1.12 registers nodes on real placement; RS 2/3 need storage the harness can't
  insert into), so a harness grid is always empty. The AE2 side *is* reachable on 1.20.1 via
  `/rsorgtest ae2build|ae2open`, which fills an ME Chest with a creative cell.
- **Kill the dev client before building.** A running client holds
  `build/moddev/artifacts/intermediateToNamed.zip` and Gradle dies with "Unable to delete file".
  Match the process by **window title** (`Minecraft`), never by command line — the *server*'s
  classpath contains `client-extra-*.jar`, so a command-line match on "client" kills the wrong JVM.

## Other things worth knowing

- **The UI is effectively English-only.** Only 4 translation keys are actually used; roughly 100
  user-facing strings are hardcoded `Component.literal(...)`, ~69 of them in `TabEditorScreen`.
  Adding a language means extracting those first. (`ae2organizer.panel.title` is defined but dead.)
- **The recovery command is `/storageorganizer resetwindows`** with **no alias** — the old
  `/ae2organizer` keyword was deliberately removed at 2.0.0.
- **Rewriting history must be scoped.** `git filter-branch` over whole branches also rewrites the
  SHAs of commits shared with published branches, which silently destroys a fast-forward. Scope it
  (`-- <branches> --not origin/1.21.1`) so published SHAs are preserved.
- Repo files check out **CRLF**. Patterns anchored with `$`, or multi-line `\n` matches in
  sed/perl one-liners, will silently fail to match — normalize line endings first.

## Working with the maintainer

- Wants it to look like a **proper AE2 addon** and respect AE2 dark-mode packs — prefer real AE2
  textures/widgets over hand-drawn approximations.
- Iterates on UI from screenshots; read the request precisely (e.g. "smaller icons" ≠ "bigger buttons").
- Verifies addon-terminal behavior in their **own modpack**, so ship a jar for those and don't claim a
  fix is confirmed until they say so.
