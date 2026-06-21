# AGENTS.md — guide for AI agents working on AE2Organizer

Orientation + hard-won knowledge so future sessions are fast and don't repeat mistakes.
Player docs: **[README.md](README.md)**. Architecture detail: **[DEVELOPMENT.md](DEVELOPMENT.md)**.
This file is the *how to work here* layer on top of those.

## What this is

A **client-side** NeoForge mod that adds user-defined **filter tabs** to Applied Energistics 2
terminals. It hooks AE2's *client-side* item view (a mixin into AE2's `Repo`) and draws a tab
panel on the terminal; there is **no server component**. It must keep working when joining a
vanilla-AE2 server that doesn't have this mod.

## Stack (don't guess — these are pinned)

- Minecraft **1.21.1**, NeoForge **21.1.x** (built vs 21.1.193), Java **21**.
- AE2 **[19.2,19.3)** — *required*, declared `side = "CLIENT"`. JEI optional.
- Gradle **8.10.2** + ModDevGradle **1.0.20**. Multi-project: minimal root + `neoforge/` subproject.
- Mod id `ae2organizer`, base package `nl.ljack2k.ae2organizer`. Versions live in `gradle.properties`.

## Build / run / test

```bash
./gradlew :neoforge:build       # -> neoforge/build/libs/AE2Organizer-neoforge-1.21.1-<ver>.jar
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

- **AE2**: `D:/Projects/JackItToMe/libs/applied-energistics-2-*.jar` (stable, in the sibling repo).
- **JEI**: `~/.gradle/caches/modules-2/files-2.1/curse.maven/jei-238222/**/**.jar`.
- **NeoForge sources** (read patched MC/NeoForge `.java`, e.g. `Screen`, `ScreenEvent`):
  `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<ver>/**/neoforge-<ver>-sources.jar`
  (extract a single entry with `System.IO.Compression.ZipFile` in PowerShell, then Read it).
- **Compiled MC+NeoForge** (Mojang-mapped, for `javap` on vanilla classes like `ItemStack`,
  `GuiGraphics`, `Screen`): `~/.gradle/caches/neoformruntime/intermediate_results/compiledWithNeoForge_*_output.jar`.

JackItToMe (`D:/Projects/JackItToMe`) is the reference AE2 addon — copy its gradle/toml patterns.

## Non-obvious gotchas (these are the robustness wins)

- **Plain `Screen`, never `AEBaseScreen`.** AEBaseScreen needs a server-side container menu, which
  breaks the client-only/any-server guarantee (and would desync inventory). All our screens are
  vanilla `Screen`s, themed manually.
- **Theme through AE2 so dark-mode packs apply.** Use `BackgroundGenerator.draw(w,h,g,x,y)` for the
  panel, `StyleManager.loadStyleDoc(...).getColor(PaletteColor.*)` for text, and AE2 widgets
  (`AE2Button`, `AECheckbox`, `Icon` via `Icon#getBlitter()`). These read `background.png`/`palette.json`,
  which is exactly what AE2 dark-mode resource packs override. All wrapped in `Ae2Style`.
- **AE2's `AETextField` renders border artifacts outside a container screen** — use a vanilla `EditBox`.
- **The menu blur is from `Screen.render` → `renderBackground` → `renderBlurredBackground`**, called
  every frame. A dim drawn in your own `render` gets overdrawn. Fix: **override `renderBackground`**
  to a plain dim (`Ae2Style.DIM`) + your panel.
- **AE2's terminal eats scroll/drag.** `MEStorageScreen` overrides `mouseScrolled`/`mouseDragged` and
  consumes them before added widgets get them (clicks *do* forward). Route the tab bar's wheel/drag
  through cancelable **`ScreenEvent.Mouse*.Pre`** events (see `ClientEvents`); the widget only renders.
- **Item icons render at a fixed 16px** (`GuiGraphics#renderItem`). To make them smaller, scale the
  pose (`Ae2Style.scaledItem`). Don't enlarge the buttons to "fit" — the maintainer means smaller icons.
- **Tab-bar offset:** anchor past the panel image *and* any real `menu.slots`, measured to the slot's
  **18px frame** (item is 16px + a 1px border). This clears terminals with extra card slots. Do **not**
  use `getExclusionZones()` — it includes the top-right help button and overshoots.
- **Tags use the `c:` namespace** on 1.21 NeoForge (`c:ingots`), not `forge:`.
- **The `Repo` filter funnel** is `addEntriesToView(Collection)` — both `updateView()` branches pass
  through it before sorting, so `@ModifyVariable` at HEAD there filters the whole view and AND-combines
  with AE2's search box. `updateView()` is public — call it to re-filter.
- **`GridInventoryEntry` is in `appeng.menu.me.common`**, not `client.gui`.
- Mixins: `required: true`, all under `client`, no refmap (AE2 ships official names).

## Verified API quick-reference (confirmed via javap this session)

- `appeng.client.gui.me.common.Repo`: `private void addEntriesToView(Collection<GridInventoryEntry>)`,
  `public final void updateView()`, `getSearchString/setSearchString`.
- `MEStorageScreen`: `protected final Repo repo` (→ `@Accessor`). Extends `AEBaseScreen` which has
  `public final int getGuiLeft()/getGuiTop()`.
- `AEKey`: `getModId()`, `getDisplayName()`, `isTagged(TagKey<?>)`, `<T> get(DataComponentType<T>)`,
  `hasComponents()`. `AEItemKey`: `getReadOnlyStack()`, `isDamaged()`, `getItem()`.
- `appeng.client.gui.style.BackgroundGenerator.draw(int width, int height, GuiGraphics, int x, int y)`.
- `appeng.client.gui.style.StyleManager.loadStyleDoc(String)` → `ScreenStyle.getColor(PaletteColor)` →
  `Color.toARGB()`. Palette paths: `/screens/common/common.json` (includes `palette.json`).
- `appeng.client.gui.style.Blitter`: `texture(...)/.src(...).dest(x,y,w,h).colorArgb(int).blit(GuiGraphics)`.
- Widgets: `AE2Button(x,y,w,h,Component,OnPress)` extends Button; `AECheckbox(x,y,w,h,ScreenStyle,Component)`
  with `isSelected/setSelected`; `appeng.client.gui.Icon.COG` + `Icon#getBlitter()`.
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
