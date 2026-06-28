# AE2Organizer — Development

Technical reference: building, the config-file format, and how the mod works. For player-facing usage see **[README.md](README.md)**.

## Requirements / toolchain

- Minecraft **1.20.1**, **MinecraftForge 47.4.20**, Java **17**. (NeoForge 1.20.1 uses the same `net.minecraftforge` packages and would also work, but AE2 15.4.10 ships only Forge/Fabric — so this line targets Forge.)
- Applied Energistics 2 **[15.4,15.5)** — required at runtime, client side. guideme **20.1.x** is a required runtime dep of AE2 (a standalone mod, not jar-in-jar).
- JEI **15.x** — optional; compiled against for the drag integration, never bundled.
- Gradle **8.10.2** + ModDevGradle **legacyforge** 2.0.141 (the `legacyForge { }` plugin: reobfuscates to SRG and generates a Mixin refmap). Multi-project: minimal root + the `neoforge/` subproject (dir name carried over from the NeoForge lines; it builds a Forge jar).
- Build Gradle on a **JDK 17 or 21 launcher** (Gradle 8.10.2 predates JDK 25): `JAVA_HOME=…/jdk-21… ./gradlew …`. The Java-17 toolchain that compiles the mod is auto-provisioned (foojay).

## Building

```bash
./gradlew :neoforge:build       # -> neoforge/build/libs/AE2Organizer-forge-1.20.1-<ver>.jar (reobf'd to SRG)
./gradlew :neoforge:runClient   # dev client with AE2 (+ JEI) for testing
```

The output jar contains only this mod's classes/resources — AE2, guideme and JEI are `compileOnly`/`runtimeOnly` and are not shaded in.

### Versions

Set in `gradle.properties` (`mod_version`, `minecraft_version`, `forge_version`, …). To target a new AE2 build, update `ae2_curse_file_id` (and `ae2_version` / `ae2_version_range`) from the CurseForge file page's "Curse Maven Snippet"; JEI is `jei_curse_file_id`. Both resolve via the CurseMaven repo.

## Publishing (CurseForge + Modrinth)

Three tag-triggered workflows under `.github/workflows/` (modelled on JackItToMe), using
[mc-publish](https://github.com/Kir-Antipov/mc-publish):

- **`release.yml`** — on a pushed `v*` tag, builds the jar and creates the GitHub Release with it attached.
- **`publish-modrinth.yml`** / **`publish-curseforge.yml`** — run after `release.yml` succeeds (via
  `workflow_run`), download the jar from the Release, and upload to each platform. Each can also be
  re-run alone (Actions → the workflow → Run workflow → enter the tag) to retry one platform.

`workflow_run` uses the workflow file from the repo's **default branch**, so the default branch must
always carry these (version-aware) workflows. This project keeps one long-lived branch per Minecraft
line — `1.21.1` (currently the default), `26.1`, and `1.20.1` (Forge) — with the "featured" line chosen
via the default-branch setting (not by renaming branches). The workflows read `minecraft_version` /
`java_version` / `mod_version` from the published line's `gradle.properties`, so one set of files
serves every branch.

One-time setup — repo → **Settings → Secrets and variables → Actions**:

- **Secrets:** `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`. (`GITHUB_TOKEN` is automatic.)
- Project ids are set in the workflows: Modrinth project id `xugd56Pv` in `publish-modrinth.yml`
  (the base62 id, **not** the slug `ae2-organizer` — Modrinth ids are base62, so the slug's hyphen
  is rejected by the API), CurseForge id `1581862` in `publish-curseforge.yml`.
- A platform is skipped if its token is missing, so you can enable them one at a time.

To release: bump `mod_version` in `gradle.properties` on the line's branch, commit, then tag with the
Minecraft line appended so tags stay unique across branches —
`git tag v1.2.0-mc1.21.1 && git push origin v1.2.0-mc1.21.1` (or `…-mc26.1` from the `26.1` branch).
Only `v*` tag pushes publish; the workflow derives the game version, Java version and the `<ver>+<mc>`
platform version string from `gradle.properties`. AE2 (required) and JEI (optional) dependency links
are declared in the publish workflows.

> **The 1.20.1 (Forge) line is not release-wired yet.** The publish workflows run from the **default
> branch** and hardcode `loaders: neoforge` (and a `neoforge` token in the asset name). Before tagging
> `v…-mc1.20.1`, make `loaders` version-aware — read a `mod_loaders` property from the tagged commit's
> `gradle.properties` (set `mod_loaders=forge` here, `=neoforge` on `1.21.1`/`26.1`) — on the default
> branch. Until then, build this line's jar locally with `:neoforge:build`.

## Config file

Per client, at `config/ae2organizer/tabs.json`:

```json
{
  "version": 1,
  "settings": { "resetFilterOnOpen": false, "showTabLabels": false, "tabScale": 1.15, "clearSearchOnTabSelect": false, "syncJeiOnTabSelect": false },
  "tabs": [
    { "id": "ingots", "name": "Ingots", "icon": "minecraft:iron_ingot", "mode": "any",
      "conditions": [ { "type": "tag", "tag": "c:ingots" } ] }
  ]
}
```

- Loaded on `FMLClientSetupEvent`. Missing or invalid pieces fall back to defaults; a few example tabs are seeded on first run.
- Tabs are written on editor **Done**, settings on change. Writes are atomic (temp file + move).
- A condition is `{ "type": "mod"|"tag"|"text"|"component", ... }` — e.g. `{"type":"mod","modId":"create"}`, `{"type":"text","text":"sword"}`, `{"type":"component","match":"enchanted"}`. (De)serialized with Mojang Codecs.

## Architecture

Everything is client-side; nothing registers on a dedicated server (the AE2 dependency is declared `side = "CLIENT"`).

### Filtering — mixins into AE2

- **`mixin/RepoMixin`** — `@ModifyVariable` at `HEAD` of `appeng.client.gui.me.common.Repo#addEntriesToView(Collection)`. Both the full-rebuild and paused-incremental code paths funnel through this one method before sorting, so shrinking its input filters the entire view, AND-combined with AE2's own search box. The active predicate is attached to the live `Repo` instance via the `TabFilterHolder` duck-type interface (a `@Unique` field).
- **`mixin/MEStorageScreenAccessor`** — `@Accessor`s for AE2's `protected final Repo repo` and its `AETextField searchField`. The `searchField` accessor backs the *Clear search bar when selecting a tab* setting: it empties the visible search box, while `repo.setSearchString("")` clears the underlying filter string (both are needed — clearing only one leaves the box and the filter out of sync).
- **`mixin/AbstractContainerScreenAccessor`** — `@Accessor` for `imageWidth`/`imageHeight`, used to position the tab bar.

Mixins are configured in `ae2organizer.mixins.json` (referenced from `mods.toml`), `required: true`, all under `client`. A **refmap is generated** (production reobfuscates to SRG) for the vanilla `AbstractContainerScreenAccessor` (`imageWidth`/`imageHeight` → SRG); the AE2-targeting mixins carry `@Mixin(…, remap = false)` because AE2 ships un-obfuscated. The Mixin annotation processor is declared explicitly in `build.gradle` (the legacyforge `mixin { }` block only wires the refmap config).

### Filter model — `filter/`

`Tab` + `Condition` (`ModCondition`, `TagCondition`, `TextCondition`, `ComponentCondition`) with Codecs. `Tab#toPredicate()` / `Condition#toPredicate()` build a `Predicate<AEKey>`. Conditions guard on `AEItemKey`, so fluid/other keys never match item conditions, and precompute expensive bits (resolved `TagKey`s, lowercased text, registry lookups) because the predicate runs over every key on each terminal view refresh.

### UI — `client/`, `client/gui/`

- **`ClientEvents`** — on `ScreenEvent.Init.Post` for an `MEStorageScreen`, attaches `TabBarWidget` and re-applies the active tab. The bar's mouse input (click/drag/scroll) is routed through the cancelable `ScreenEvent.Mouse*` pre-events, because AE2's terminal overrides `mouseScrolled`/`mouseDragged` and consumes them before added widgets receive them. Registered (client-only) from the `@Mod` constructor.
- **`TabManager`** — client singleton holding tabs, the active selection, and settings; **`TabStorage`** does the JSON persistence (see above).
- **`TabBarWidget`** — the right-hand "Filters" panel: one `BackgroundGenerator` panel sized to the terminal, a title bar (name + gear), bevelled rows (active = sunken), a scrollbar when the tabs overflow, and a `tabScale`-driven size. Its X anchors past the panel image **and** any real menu slot that sticks out (measured to the slot's 18px frame), so it clears terminals with extra card slots (e.g. the Wireless Crafting Grid).
- The screens (`TabEditorScreen`, `ItemPickerScreen`, `TagChooserScreen`, `SettingsScreen`) are plain client `Screen`s — deliberately **not** AE2 `AEBaseScreen`s, which would need a server-side container menu and break the client-only/any-server guarantee.
- **`Ae2Style`** themes those screens through AE2's own pipeline (so AE2 dark-mode packs apply automatically) and replaces vanilla's blurred menu background with a plain dim via a `renderBackground` override:
  - `BackgroundGenerator` draws the nine-sliced `background.png` panel; `StyleManager` + `PaletteColor` supply text colours from `palette.json` (with fallbacks).
  - AE2 widgets used directly: `AECheckbox` and `Icon.WRENCH` (tinted to the palette colour). Text buttons are a local `Ae2Button` (extends vanilla `Button`, drawn via `Ae2Style.bevelButton`) — AE2 15.4 has no generic text button. Text fields are plain `EditBox`es — AE2's `AETextField` rendered border artifacts outside a container screen.
  - Helpers render item icons and text at an arbitrary scale, driving the `tabScale` setting.
- **Inventory drag** is custom: the editor renders the player inventory read-only (never mutating it) and drops resolve against the same `GhostTarget` rects the JEI handler uses.

### JEI integration — `jei/` (optional)

A `@JeiPlugin` registers two GUI handlers and stays dormant if JEI is absent:
- a **ghost-ingredient handler** (`EditorGhostHandler`) — accepts items dragged from JEI onto the editor's `GhostTarget`s;
- a **screen handler** (`EditorGuiProperties`) — reports the editor's panel bounds so JEI draws its item-list overlay beside this (non-container) screen, which is what makes dragging from JEI possible at all.

It also backs the optional **"Sync JEI search bar"** setting. `onRuntimeAvailable` captures JEI's `IIngredientFilter` and registers a callback on the JEI-free `client/JeiSync` bridge; when a tab is selected, `TabBarWidget` calls `JeiSync.apply(tab)`, which translates the tab's conditions to a JEI query — `@mod` / `$tag` (JEI 15.x's tag prefix is `$`, not `#`; tag *path* only, no namespace) / the item name — joined by `|` for **Match ANY** or spaces for **Match ALL**, then calls `setFilterText`. `component` conditions have no JEI equivalent and are dropped. Routing through `JeiSync` keeps this plugin the only class that imports JEI, so the core stays JEI-optional.

## Notes / limitations

- AE2's `Repo` / `MEStorageScreen` and the `appeng.client.gui.style.*` / `widgets.*` classes are **internal, not public API**. The AE2 version range is pinned tight and mixins are `required: true`, so the mod fails loudly rather than silently mis-filtering or mis-rendering if AE2's internals change between versions; the style layer falls back to a default colour if it can't load.
- Component matching is **presence-based** — no value matching (e.g. "enchant level ≥ 3").
- The tab bar and the editor's tab list both scroll; the editor's per-tab **condition** rows don't, so a tab with very many conditions can overflow the window. Typical counts fit comfortably.
