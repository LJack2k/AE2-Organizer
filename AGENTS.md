# AGENTS.md — guide for AI agents working on Storage Organizer

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
- **Two name properties:** `mod_name=StorageOrganizer` is technical (it builds `archivesName`, so it
  must stay space-free); `mod_display_name=Storage Organizer` is what players see (mods.toml
  `displayName`, `pack.mcmeta`, GUI strings, docs). Log prefixes use `[StorageOrganizer]`.
- **Mod id stays `ae2organizer`, package `nl.ljack2k.ae2organizer`.** Never change the id/package —
  configs (`config/ae2organizer/`), the clipboard-export magic key, modpack refs, and the published
  project depend on them. Only the *name* changed in the rebrand. Versions live in `gradle.properties`.

## Build / run / test

```bash
./gradlew :neoforge:build          # -> neoforge/build/libs/StorageOrganizer-neoforge-1.21.1-<ver>.jar
./gradlew :neoforge:runClient      # dev client with AE2 + RS (+ JEI), opens a real window
./gradlew :neoforge:runClientJoin  # dev client that quick-joins localhost:25565 (devHarness on)
./gradlew :neoforge:runServer      # dev server for the RCON/screenshot harness
```

- **Always `compileJava` after edits** — it's fast and catches AE2/RS/Mojang API mismatches.
- After `runClient`, confirm a clean boot by grepping the log for `StorageOrganizer ... Client loaded`,
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
- **An overlay drawn in `Render.Post` is NOT automatically on top — depth testing is on**, so it must
  also be *nearer* in z than what the screen already drew, or items punch through it. Verified z
  ceiling: slot items ≈150, their count/damage decorations 200, vanilla tooltips 400, and the highest
  — the carried stack, which `AbstractContainerScreen#renderFloatingItem` draws at **232** with
  `renderItemDecorations` nested inside that pose, i.e. **432**. So a modal must sit above 432
  (`AddToTabDialog` uses 500). Symptom of getting this half-right: item *sprites* hide correctly but
  their *count numbers* still float over the panel. `graphics.flush()` does **not** fix it — the
  cause is depth, not text batching (that theory cost a cycle).
- **Tab-bar offset:** anchor past the panel image *and* any real `menu.slots`, measured to the slot's
  **18px frame** (item is 16px + a 1px border). This clears terminals with extra card slots. Do **not**
  use `getExclusionZones()` — it includes the top-right help button and overshoots.
- **Container screens throw the carried stack on the mouse RELEASE, not the press.** Cancelling
  `MouseButtonPressed.Pre` over an overlay panel is not enough: the paired
  `MouseButtonReleased.Pre` still reaches `AbstractContainerScreen`, which treats a release outside
  its own bounds as click-outside → throw. Swallow the release too (see the tab bar's
  `handleMouseRelease` / `ClientEvents.swallowNextRelease`).
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
  ghost handlers are a `ListMultiMap` and lookup **combines every handler whose registered class
  `isInstance`s the screen** — so registering for `MEStorageScreen` coexists with AE2's own
  `AEBaseScreen` handler (pattern-slot drags unaffected). `Screen.class` itself is blacklisted for
  registration. `getTargetsTyped(..., doStart=false)` is the hover-hint query (fires while merely
  hovering a JEI ingredient); return `List.of()` there to highlight targets only during a real drag —
  mid-drag highlights draw from the `doStart=true` snapshot, so they survive. (Verified on JEI file
  7420587 / 1.21.1; re-verify the multimap on other lines' JEI before porting the bar ghost drop.);
  `#addGlobalGuiHandler(IGlobalGuiHandler)` → `getGuiExtraAreas(): Collection<Rect2i>` are the exclusion
  rects JEI's grid wraps around (all its methods are `default`, so it can't be a lambda target).
- NeoForge: `Screen.render` calls `renderBackground`; `ScreenEvent.Init.Post#addListener` adds a
  renderable widget; `ScreenEvent.Mouse{ButtonPressed,ButtonReleased,Dragged,Scrolled}.Pre` are cancelable.

## Release & git workflow

- **Branch per Minecraft line:** `1.21.1` (default) and `26.1` — long-lived and parallel; there is no
  `develop`/`main`. Work on the branch for the MC line you're targeting. Origin is
  `https://LJack2k@github.com/LJack2k/AE2-Organizer.git`.
- Bump `mod_version` in `gradle.properties`; the jar name and `neoforge.mods.toml` version expand from it.
- **Write `changelogs/<mod_version>.md` before tagging — it is part of the release, not an afterthought.**
  All three workflows feed it to mc-publish as `changelog-file`, so that one file *is* the GitHub Release
  body **and** the changelog shown on the CurseForge file page and the Modrinth version page. Keep it
  player-facing and MC-line-neutral (the release title already carries the line and loader); the same
  file ships on every branch. If it is missing, mc-publish silently falls back to an auto-generated
  commit list — which is how a release ends up with a developer changelog on the storefront.
  **Style rule, no exceptions: never use an em dash (—) in a changelog file — use a plain hyphen `-`.**
  (Maintainer's standing instruction. It applies to the changelog files themselves, not to this file
  or the README.)
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

- Wants each backend to look **native to its own mod** — AE2 terminals like a proper AE2 addon
  (respect AE2 dark-mode packs; real AE2 textures/widgets over hand-drawn), RS grids like RS. Prefer
  each mod's own art/icons over bundled approximations.
- Iterates on UI from screenshots; read the request precisely (e.g. "smaller icons" ≠ "bigger buttons").
- Verifies addon-terminal behavior in their **own modpack**, so ship a jar for those and don't claim a
  fix is confirmed until they say so.
