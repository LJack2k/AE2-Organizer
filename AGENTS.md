# AGENTS.md — guide for AI agents working on AE2Organizer

Orientation + hard-won knowledge so future sessions are fast and don't repeat mistakes.
Player docs: **[README.md](README.md)**. Architecture detail: **[DEVELOPMENT.md](DEVELOPMENT.md)**.
This file is the *how to work here* layer on top of those.

## What this is

A **client-side** Forge mod that adds user-defined **filter tabs** to Applied Energistics 2
terminals. It hooks AE2's *client-side* item view (a mixin into AE2's `Repo`) and draws a tab
panel on the terminal; there is **no server component**. It must keep working when joining a
vanilla-AE2 server that doesn't have this mod.

## Stack (don't guess — these are pinned)

This is the **1.20.1 (Forge)** line. (Sibling branches: `1.21.1` and `26.1`, both NeoForge.)

- Minecraft **1.20.1**, **MinecraftForge 47.4.20** (NOT NeoForge — AE2 15.4.10 ships only Forge/Fabric on
  1.20.1, and NeoForge 1.20.1 uses the same `net.minecraftforge` packages anyway). Java **17**.
- AE2 **[15.4,15.5)** — *required*, `side = "CLIENT"`. guideme **20.1.x** is a required runtime dep of AE2
  (standalone mod, not jar-in-jar — the dev client needs it explicitly). JEI **15.x** optional (dev-only).
- Gradle **8.10.2** + ModDevGradle **legacyforge** 2.0.141 (the `legacyForge { }` plugin; reobf to SRG +
  mixin refmap). Multi-project: minimal root + `neoforge/` subproject — the dir name is *historical*, it
  builds a Forge jar; kept so the shared default-branch CI's `:neoforge:build` keeps matching.
- Mod id `ae2organizer`, base package `nl.ljack2k.ae2organizer`. Versions live in `gradle.properties`.
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
- **No data components:** `AEItemKey` is NBT-based (`getTag()/copyTag()/hasTag()/isDamaged()`). The `COMPONENT`
  filter is reworked to NBT (`isEnchanted()` + `StoredEnchantments`, `hasCustomHoverName()`, `isDamaged()`,
  top-level NBT key); the `HAS_COMPONENT_TYPE` match is dropped (no 1.20.1 analog).
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

```bash
./gradlew :neoforge:build       # -> neoforge/build/libs/AE2Organizer-forge-1.20.1-<ver>.jar (reobf'd to SRG)
./gradlew :neoforge:runClient   # dev client with AE2 (+ JEI), opens a real window
```

- **Always `compileJava` after edits** — it's fast and catches AE2/Mojang API mismatches.
- After `runClient`, confirm a clean boot by grepping the log for `AE2Organizer ... Client loaded`,
  `Sound engine started`, and the *absence* of `exception` / `mixin ... fail`. You can't drive the
  GUI from here — the maintainer does interactive testing.
- **The dev client only has AE2 + JEI.** Addon terminals (e.g. the Wireless Crafting Grid) are
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
  (panel bounds → lets JEI draw its overlay beside a non-container screen).
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

## Working with the maintainer

- Wants it to look like a **proper AE2 addon** and respect AE2 dark-mode packs — prefer real AE2
  textures/widgets over hand-drawn approximations.
- Iterates on UI from screenshots; read the request precisely (e.g. "smaller icons" ≠ "bigger buttons").
- Verifies addon-terminal behavior in their **own modpack**, so ship a jar for those and don't claim a
  fix is confirmed until they say so.
