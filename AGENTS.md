# AGENTS.md — guide for AI agents working on TerminalOrganizer

Orientation + hard-won knowledge so future sessions are fast and don't repeat mistakes.
Player docs: **[README.md](README.md)**. Architecture detail: **[DEVELOPMENT.md](DEVELOPMENT.md)**.
This file is the *how to work here* layer on top of those.

## What this is

A **client-side** NeoForge mod that adds user-defined **filter tabs** to **both** Applied
Energistics 2 terminals **and** Refined Storage 2 grids. It hooks each mod's *client-side* item
view (a mixin per backend) and draws a tab panel; there is **no server component**. The two systems
are **hard-separated** — independent stores/windows/settings, no shared filter. It must keep working
when joining a server that doesn't have this mod, and load cleanly with **either, both, or neither**
storage mod present. The separation seam is the backend SPI in `backend/` — read DEVELOPMENT.md's
Architecture section before touching cross-backend code.

## Stack (don't guess — these are pinned)

- Minecraft **1.21.1**, NeoForge **21.1.242+** (RS 2.0.9 needs `.242`; AE2 is fine on it), Java **21**.
- AE2 **[19.2,19.3)** and RS **[2.0,3.0)** — **both optional**, declared `side = "CLIENT"`. JEI optional.
- Gradle **8.10.2** + ModDevGradle **1.0.20**. Multi-project: minimal root + `neoforge/` subproject.
- **Display name `TerminalOrganizer`; mod id stays `ae2organizer`, package `nl.ljack2k.ae2organizer`.**
  Never change the id/package — configs, modpack refs, and the published project depend on them.
  Versions live in `gradle.properties`.

## Build / run / test

```bash
./gradlew :neoforge:build          # -> neoforge/build/libs/TerminalOrganizer-neoforge-1.21.1-<ver>.jar
./gradlew :neoforge:runClient      # dev client with AE2 + RS (+ JEI), opens a real window
./gradlew :neoforge:runClientJoin  # dev client that quick-joins localhost:25565 (devHarness on)
./gradlew :neoforge:runServer      # dev server for the RCON/screenshot harness
```

- **Always `compileJava` after edits** — it's fast and catches AE2/RS/Mojang API mismatches.
- After `runClient`, confirm a clean boot by grepping the log for `TerminalOrganizer ... Client loaded`,
  `Sound engine started`, and the *absence* of `exception` / `mixin ... fail`.
- **RCON/screenshot harness** (`dev/`): gated on `-Dae2organizer.devHarness` (set on `runServer`/
  `runClientJoin`). RCON on `:25575` (password `rsorg`); `/rsorgtest build|open` places+opens an RS grid,
  `/rsorgshot` screenshots. **Only RS grids can be opened headlessly** — for AE2 terminals, start
  `runServer` + `runClientJoin` and let the maintainer look (that is how the AE2 styling and the
  autocraft/JEI fixes were signed off). Free ports 25565/25575 before relaunch (stale JVMs hold them).
- **The dev client has AE2 + RS + JEI.** Addon terminals (e.g. the Wireless Crafting Grid) are
  **not** here, so bugs specific to them can't be reproduced in dev — the maintainer tests those in
  their real modpack. To reproduce one here, add the addon as a dev-only `runtimeOnly` in
  `neoforge/build.gradle` (ask which mod first).
- `runClient` is long-running; launch it in the background and poll the log. Kill a stray client by
  PID (CIM filter on `CommandLine` containing `AE2-Organizer` and `forgeclientdev`/`forgeserverdev`,
  excluding `GradleDaemon`).

## THE GOLDEN RULE: verify AE2/RS/MC APIs with `javap` before writing code

AE2's terminal classes (`appeng.client.gui.style.*` / `widgets.*`) **and** RS's grid classes
(`com.refinedmods.refinedstorage.common.grid.*`) are **internal, not public API**. Method
names/locations differ from what you'd assume and shift between versions. Every time this session
guessed, it cost a build cycle; every time it `javap`'d first, it was right. The RS jar lives in the
Gradle cache under `curse.maven/refined-storage-243076/**` — Glob for the hashed path.

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
- **Backend-specific look lives in each `Theme`; neutral helpers in `RsStyle`.** `Ae2Theme` themes
  through AE2 so dark-mode packs apply — `BackgroundGenerator.draw(w,h,g,x,y)` for the panel,
  `StyleManager.loadStyleDoc(...).getColor(PaletteColor.*)` for text (reads `background.png`/`palette.json`,
  what AE2 dark-mode packs override), `Icon.COG` via `Icon#getBlitter()` for the settings icon. `RsTheme`
  uses the bundled `panel.png` + RS's wrench item. `RsStyle` holds the theme-neutral bevel buttons,
  checkboxes, scaled item/text, `DIM`, etc. shared by both (drop-in vanilla replacements for AE2 widgets,
  since AE2's widgets can't be used on an RS screen).
- **AE2's `AETextField` renders border artifacts outside a container screen** — use a vanilla `EditBox`
  (`RsStyle.textField`).
- **The menu blur is from `Screen.render` → `renderBackground` → `renderBlurredBackground`**, called
  every frame. A dim drawn in your own `render` gets overdrawn. Fix: **override `renderBackground`**
  to a plain dim (`RsStyle.DIM`) + your panel.
- **AE2's terminal eats scroll/drag.** `MEStorageScreen` overrides `mouseScrolled`/`mouseDragged` and
  consumes them before added widgets get them (clicks *do* forward). Route the tab bar's wheel/drag
  through cancelable **`ScreenEvent.Mouse*.Pre`** events (see `ClientEvents`); the widget only renders.
- **Item icons render at a fixed 16px** (`GuiGraphics#renderItem`). To make them smaller, scale the
  pose (`RsStyle.scaledItem`). Don't enlarge the buttons to "fit" — the maintainer means smaller icons.
- **Tab-bar offset:** anchor past the panel image *and* any real `menu.slots`, measured to the slot's
  **18px frame** (item is 16px + a 1px border). This clears terminals with extra card slots. Do **not**
  use `getExclusionZones()` — it includes the top-right help button and overshoots.
- **Tags use the `c:` namespace** on 1.21 NeoForge (`c:ingots`), not `forge:`.
- **The `Repo` filter funnel** is `addEntriesToView(Collection)` — both `updateView()` branches pass
  through it before sorting, so `@ModifyVariable` at HEAD there filters the whole view and AND-combines
  with AE2's search box. `updateView()` is public — call it to re-filter.
- **`GridInventoryEntry` is in `appeng.menu.me.common`**, not `client.gui`.
- **RS filter hook** is `AbstractGridContainerMenu#createBaseFilter()` (MixinExtras `@ModifyReturnValue`,
  wrap RS's `ResourceRepositoryFilter`); re-filter via `menu.getRepository().sort()`; item extraction via
  `ItemGridResource#getItemStack()`.
- **Two mixin configs**, one per backend (`ae2organizer.ae2.mixins.json` / `.rs.mixins.json`), each with
  an `IMixinConfigPlugin` gating `shouldApplyMixin` on the target mod's presence — so a missing mod's
  mixins are skipped, not crashed. `required: true`, no refmap (both mods ship official names).
- **Mixins/accessors MUST live in the `…mixin` subpackage** (`backend.ae2.mixin` / `backend.rs.mixin`),
  never beside plain classes — a class in a declared mixin package can't be referenced directly
  (`IllegalClassLoadError`). Plain backend classes (`Ae2Backend`, themes, bridges) sit one level up.
- **Backend classes only load when their mod is present** — `BackendRegistry.init()` gates on
  `ModList.isLoaded`. So `Ae2Theme`/`RsTheme`/backends may freely reference `appeng.*`/`com.refinedmods.*`;
  **never** reference those from common/`client/` code.
- **Per-backend theming via `Theme`**: AE2 → AE2's `BackgroundGenerator`/palette + `Icon.COG`; RS → bundled
  `panel.png` nine-slice + `refinedstorage:wrench` item (`g.renderItem`). Each backend uses its mod's
  **native** icon — don't reintroduce a bundled gear/wrench sprite (that road was a dead end: the 16px GUI
  pipeline renders partial-alpha edges as an opaque cutout, not a blend).

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
  (panel bounds → lets JEI draw its overlay beside a non-container screen);
  `#addGlobalGuiHandler(IGlobalGuiHandler)` → `getGuiExtraAreas(): Collection<Rect2i>` are the exclusion
  rects JEI's grid wraps around (all its methods are `default`, so it can't be a lambda target).
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

- Wants each backend to look **native to its own mod** — AE2 terminals like a proper AE2 addon
  (respect AE2 dark-mode packs; real AE2 textures/widgets over hand-drawn), RS grids like RS. Prefer
  each mod's own art/icons over bundled approximations.
- Iterates on UI from screenshots; read the request precisely (e.g. "smaller icons" ≠ "bigger buttons").
- Verifies addon-terminal behavior in their **own modpack**, so ship a jar for those and don't claim a
  fix is confirmed until they say so.
