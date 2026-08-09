# Storage Organizer — Development

Technical reference: building, the config-file format, and how the mod works. For player-facing usage see **[README.md](README.md)**.

## Requirements / toolchain

- Minecraft **1.21.1**, NeoForge **21.1.242+**, Java **21**.
- Applied Energistics 2 **[19.2,19.3)** and Refined Storage 2 **[2.0,3.0)** — both **optional** backends, client side. The mod hooks whichever is present.
- JEI — optional; compiled against for the drag integration, never bundled.
- Gradle **8.10.2** + ModDevGradle **1.0.20**. Multi-project: minimal root + the `neoforge/` subproject (so a `fabric/` module could be added later).

> **NeoForge floor is 21.1.242.** RS 2.0.9 calls `AttachmentType.Builder.sync(StreamCodec)`, which is absent in earlier 21.1.x. AE2 19.2.x runs fine on it.

## Building

```bash
./gradlew :neoforge:build         # -> neoforge/build/libs/StorageOrganizer-neoforge-1.21.1-<ver>.jar
./gradlew :neoforge:runClient     # dev client with AE2 + RS (+ JEI) for testing
./gradlew :neoforge:runClientJoin # dev client that quick-joins localhost:25565 (pairs with runServer)
./gradlew :neoforge:runServer     # dev server for the RCON/screenshot harness (see dev/)
```

The output jar contains only this mod's classes/resources — AE2, RS, guideme and JEI are `compileOnly`/`runtimeOnly` and are not shaded in.

### Versions

Set in `gradle.properties` (`mod_version`, `minecraft_version`, `neo_version`, …). To target a new AE2 build, update `ae2_curse_file_id` (and `ae2_version` / `ae2_version_range`) from the CurseForge file page's "Curse Maven Snippet"; RS is `rs_curse_file_id` (project 243076) with `rs_version` / `rs_version_range`; JEI is `jei_curse_file_id`. All resolve via the CurseMaven repo.

Identity note: the name is split in two — **`mod_name = StorageOrganizer`** is the technical name that builds the jar filename (`archivesName`), and **`mod_display_name = Storage Organizer`** is the player-facing name used for the mods.toml `displayName` and `pack.mcmeta`; never build a filename from it. The **mod id stays `ae2organizer`** and the Java package stays `nl.ljack2k.ae2organizer` — so existing configs (`config/ae2organizer/…`), filter exports, modpack references, and the published CurseForge/Modrinth project are unaffected by the rebrand.

## Publishing (CurseForge + Modrinth)

Three tag-triggered workflows under `.github/workflows/` (modelled on JackItToMe), using
[mc-publish](https://github.com/Kir-Antipov/mc-publish):

- **`release.yml`** — on a pushed `v*` tag, builds the jar and creates the GitHub Release with it attached.
- **`publish-modrinth.yml`** / **`publish-curseforge.yml`** — run after `release.yml` succeeds (via
  `workflow_run`), download the jar from the Release, and upload to each platform. Each can also be
  re-run alone (Actions → the workflow → Run workflow → enter the tag) to retry one platform.

`workflow_run` uses the workflow file from the repo's **default branch**, so the default branch must
always carry these (version-aware) workflows. This project keeps one long-lived branch per Minecraft
line — `1.21.1` (currently the default) and `26.1` — with the "featured" line chosen via the
default-branch setting (not by renaming branches). The workflows read `minecraft_version` /
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
platform version string from `gradle.properties`. AE2, RS and JEI dependency links (all optional)
are declared in the publish workflows.

## Config files

**One file per backend**, per client, under `config/ae2organizer/` — the data-side half of the hard separation. AE2 keeps the legacy filename so existing users' tabs carry over untouched:

- `config/ae2organizer/tabs.json` — the **AE2** store.
- `config/ae2organizer/rs.json` — the **Refined Storage** store.

Both use the identical format below; each holds its own windows, tabs, settings, and active selection, and the two never merge. Example (`tabs.json`):

```json
{
  "version": 2,
  "settings": { "resetFilterOnOpen": false, "clearSearchOnTabSelect": false, "syncJeiOnTabSelect": false },
  "windows": [
    { "id": "main", "name": "Filters", "orientation": "vertical", "showLabels": true, "scale": 1.15,
      "position": "dock", "x": 0, "y": 0, "showGear": true, "showAll": true,
      "placements": { "ae2:craftingterm": { "mode": "free", "x": 120, "y": 40 } },
      "baseTerminal": "ae2:craftingterm", "hiddenOn": [ "ae2:wirelessterm" ], "collapsed": false }
  ],
  "tabs": [
    { "id": "ingots", "name": "Ingots", "icon": "minecraft:iron_ingot", "mode": "any",
      "conditions": [ { "type": "tag", "tag": "c:ingots" } ], "window": "main" }
  ],
  "terminalNames": { "ae2:craftingterm": "" }
}
```

- Loaded on `FMLClientSetupEvent`. Missing or invalid pieces fall back to defaults; a few example tabs are seeded on first run.
- Written on editor **Save**, on settings change, and on live window drags. Writes are atomic (temp file + move).
- A condition is `{ "type": "mod"|"tag"|"text"|"component", ... }` — e.g. `{"type":"mod","modId":"create"}`, `{"type":"text","text":"sword"}`, `{"type":"component","match":"enchanted"}`. (De)serialized with Mojang Codecs. Any condition may carry `"negate": true` to make it an **exclusion** (optional, defaults `false`); `Tab#toPredicate()` combines positives by `mode` and AND-excludes the negated ones: *(positives) and not (any exclusion)*.
- **Windows** own presentation only; the active tab is global (one at a time). Each `Tab` names its `window`; every window property is optional with a sane default. `position` is `dock`/`center`/`free`; per-terminal overrides live in `placements` (keyed by menu-type id), with `baseTerminal` as the inherited fallback for terminals not yet placed. `hiddenOn` lists terminal types where the window is hidden; `collapsed` is the editor tree state.
- **v1 → v2 migration:** a file without `windows` is upgraded on load — one `main` window is synthesised carrying the legacy `settings.showTabLabels`/`tabScale`, and every tab keeps its default `window: "main"`.
- `terminalNames` remembers a friendly name per terminal type as they're opened (AE2 screens often have a blank vanilla title, so the visibility UI falls back to a known-id map / prettified id).
- **Clipboard import/export** (`persist/TabShare`) uses the same Codecs to (de)serialize plain JSON to/from the system clipboard, wrapped with a magic key so foreign text is rejected: `{"ae2organizer":"tabs",…}` for a single window's tabs (conditions only; `id`/`window` stripped, regenerated on import) and `{"ae2organizer":"windows",…}` for a full windows+tabs snapshot. Import always **replaces** (per window, or the whole config) behind a confirm.

## Architecture

Everything is client-side; nothing registers on a dedicated server (both storage deps are declared `side = "CLIENT"`, `type = "optional"`).

The unifying idea is a **storage-backend SPI** (`backend/`) that both isolates AE2 and RS from each other and keeps a missing mod's code from ever classloading. Everything above the SPI — the filter model, the tab UI, persistence, JEI, the clipboard — is shared and backend-agnostic; everything mod-specific (the mixin, the screen adapter, the theme) lives under `backend/ae2/` or `backend/rs/`.

### The backend SPI — `backend/`

- **`StorageBackend`** — one storage system: `id()` (`"ae2"` / `"rs"`, also the store key), `handles(Screen)`, `adapt(Screen) → ScreenAdapter`, `theme() → Theme`, and `setActiveFilter(Predicate<ItemStack>)` (pushes the active tab's predicate into that backend's own client-side filter bridge; `null` = the *All* tab).
- **`ScreenAdapter`** — a per-open view of a terminal/grid abstracting what the shared UI needs: `guiLeft/guiTop/xSize/ySize`, `slots()`, `terminalKey()` (menu-type id), `title()`, and `refilter()` (AE2 `repo.updateView()` / RS `getRepository().sort()`).
- **`Theme`** — the backend's look: `panel(...)`, `textColor()`, `selectionColor()`, `settingsIcon(...)`. This is what makes an AE2 terminal render in AE2's style and an RS grid in RS's.
- **`BackendRegistry`** — `init()` instantiates a backend **only if `ModList.isLoaded(...)`** for its mod. Those gates are the load-safety boundary: `Ae2Backend`/`RsBackend` (and their transitive `appeng.*` / `com.refinedmods.*` references) are never classloaded when that mod is absent. `forScreen(Screen)` resolves the backend for an open screen; `byId(...)` for the stores.
- **`SearchClearable`** — small capability interface for the *clear search bar* setting.

### Filtering — one mixin per backend

Filters operate on a **`Predicate<ItemStack>`** (the common denominator across both mods). Two mixin configs, each with its own `IMixinConfigPlugin` that gates `shouldApplyMixin` on the target mod being present, so a missing mod's mixins are simply skipped:

- **`ae2organizer.ae2.mixins.json`** (package `backend.ae2.mixin`, plugin `Ae2MixinPlugin`):
  - **`RepoMixin`** — `@ModifyVariable` at `HEAD` of `appeng.client.gui.me.common.Repo#addEntriesToView(Collection)`. Both the full-rebuild and paused-incremental paths funnel through it before sorting, so shrinking its input filters the whole view (AND-combined with AE2's search). Each `GridInventoryEntry`'s `AEItemKey` is tested via `getReadOnlyStack()`; a non-item `AEKey` is tested as `ItemStack.EMPTY`. The active predicate lives in **`Ae2Backend`'s `RepoFilterBridge`**.
  - **`MEStorageScreenAccessor`** — `@Accessor`s for AE2's `Repo` and its `AETextField searchField` (backs the *clear search* setting).
  - **`AbstractContainerScreenAccessor`** — `@Accessor` for `imageWidth`/`imageHeight`.
- **`ae2organizer.rs.mixins.json`** (package `backend.rs.mixin`, plugin `RsMixinPlugin`):
  - **`AbstractGridContainerMenuMixin`** — MixinExtras `@ModifyReturnValue` on `com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu#createBaseFilter()`, wrapping RS's `ResourceRepositoryFilter<GridResource>` so an `ItemGridResource` is tested by its `getItemStack()` against the active predicate held in **`RsBackend`'s `GridFilterBridge`**.

Mixins **must** live in the `…mixin` subpackage, away from the plain backend classes — a class in a declared mixin package can't be referenced directly, so `Ae2Backend`/`RsBackend` etc. sit one level up. Both configs are `required: true`; no refmap (both mods ship official names).

### Filter model — `filter/` (shared)

`Tab` + `Condition` (`ModCondition`, `TagCondition`, `TextCondition`, `ComponentCondition`) with Codecs. `Tab#toPredicate()` / `Condition#toPredicate()` build a **`Predicate<ItemStack>`**, precomputing expensive bits (resolved `TagKey`s, lowercased text, registry lookups) because the predicate runs over every item on each view refresh. Being `ItemStack`-based, the model is identical for both backends — each backend just feeds its own items in.

`FilterWindow` is the presentation layer: `Orientation` (vertical/horizontal), `PositionMode` (dock/center/free) + coords, per-window scale/labels/gear/all/collapsed, a `Map<String, Placement>` of per-terminal overrides keyed by menu-type id, a `baseTerminal` (the first-placed terminal others inherit), and `hiddenOn`. It affects only where/how tabs are drawn — exactly one tab is active per store.

### UI — `client/`, `client/gui/`

- **`ClientEvents`** — on `ScreenEvent.Init.Post`, asks `BackendRegistry.forScreen(...)` for the backend; if one handles the screen it takes that backend's `Store` + `ScreenAdapter` + `Theme` and builds one `TabBarWidget` **per visible window**, re-applying the active tab. The bar list rebuilds when the screen instance or the visible-window signature changes. **"Reset filter on open" fires only on a genuine open:** `Init.Post` also runs on a window resize (same screen instance) and on the terminal that comes back from a craft preview / amount / status / settings page, which both storage mods serve from their own menu — so the reset is suppressed for a re-init of the same instance, and for ~10 ticks after a `StorageBackend#isCompanionScreen` screen was on top (AE2: any `ISubMenu`; RS: `AbstractAmountScreen`). Without that, every autocraft request threw you back to *All*. Mouse input (click/drag/scroll) routes through the cancelable `ScreenEvent.Mouse*` pre-events (the storage screen consumes scroll/drag before added widgets see them) and fans out to every bar. Renders the move-mode banner and registers `/storageorganizer resetwindows`, which resets **every** backend's store so recovery works whatever screen is open.
- **`TabManager`** — holds a `Map<backendId, Store>`; each **`Store`** is one backend's independent windows + tabs + active selection + settings + terminal names, persisted via **`TabStorage`** to `config/ae2organizer/<file>.json` (`ae2` → `tabs.json`, others → `<id>.json`). `setActive`/`replaceAll`/`load` call `pushFilter()`, which routes the predicate to `BackendRegistry.byId(backendId).setActiveFilter(...)` — so a tab change only ever touches its own backend. `visibleWindows(terminalKey)` applies `hiddenOn` with a lockout safeguard (always ≥1 window so a settings icon stays reachable).
- **`TabBarWidget`** — one window's panel, drawn through the backend's `Theme`: `theme.panel(...)`, an optional "All" entry + tabs as bevelled rows/cells (active = sunken), the settings icon (`theme.settingsIcon` — AE2's `Icon.COG` / RS's wrench item), vertical **or** horizontal layout, per-window scale, scrollbar on overflow. Its dock X clears the panel image **and** any protruding menu slot (18px). In move-mode (toggle or **Alt** held — not Shift, to avoid shift-click clashes) the panel drags to a `free` position saved per terminal; a window dragged off-screen snaps back to center.
- The screens (`TabEditorScreen` tree, `WindowVisibilityScreen`, `WindowPickerScreen`, `ItemPickerScreen`, `TagChooserScreen`, `SettingsScreen`) are plain client `Screen`s — deliberately **not** container-bound screens, which would need a server menu and break the client-only/any-server guarantee. They draw via the active backend's `Theme` plus **`RsStyle`** — the theme-neutral shared helpers (bevelled buttons, checkboxes, insets, dividers, slots, scaled item/text, vanilla text fields). Backend-specific look (panel, text/selection colour, settings icon) is the `Theme`'s job; `RsStyle` is the part that's the same either way.
- **Inventory drag** is custom: the editor renders the player inventory read-only and drops resolve against the same `GhostTarget` rects the JEI handler uses.

Per-backend theming detail: **`Ae2Theme`** renders through AE2's own pipeline (`BackgroundGenerator` + `StyleManager`/`PaletteColor`, with fallbacks) so AE2 dark-mode packs apply automatically, and uses `Icon.COG`. **`RsTheme`** uses the bundled nine-slice `panel.png` sprite, a fixed RS-grey/RS-blue palette, and renders `refinedstorage:wrench` (RS's own item) as the settings icon. Each theme references its mod's classes, so it only loads when that backend does.

### Viewer sync + JEI — `client/ViewerSync`, `jei/` (optional)

**`ViewerSync`** is the JEI-free bridge the UI talks to (so the core never imports JEI). The **`@JeiPlugin`** (`StorageOrganizerJeiPlugin`) registers three GUI handlers and stays dormant if JEI is absent:
- **`EditorGhostHandler`** — accepts items dragged from JEI onto the editor's `GhostTarget`s;
- **`EditorGuiProperties`** — reports the editor's panel bounds so JEI draws its item-list overlay beside this (non-container) screen;
- **an `IGlobalGuiHandler`** — reports the filter panels' rects (`ClientEvents#activeBarBounds`, recomputed per query) as *extra areas*, so JEI drops the item slots they cover and its list wraps around a panel instead of being hidden under it. Global rather than per-screen so it also covers addon terminals we never name; the rects are only reported while a bar is really drawn on the open screen, since a stale rect would blank out JEI slots elsewhere.

It also backs the **"Sync JEI search bar"** setting: `onRuntimeAvailable` captures JEI's `IIngredientFilter` and registers a callback on `ViewerSync`; selecting a tab translates its conditions to a JEI query — `@mod` / `#tag` (path only) / the item name (quoted if spaced) — joined by `|` for **Match ANY** or spaces for **Match ALL**. `Not` conditions become `-` exclusions distributed into every OR branch (`p1 -n | p2 -n`) so it mirrors `Tab#toPredicate()`; `component` conditions have no JEI equivalent and are dropped.

## Notes / limitations

- The hooked classes on both sides are **internal, not public API** — AE2's `Repo` / `MEStorageScreen` / `appeng.client.gui.style.*`, and RS's `AbstractGridContainerMenu` / grid-resource types. Both version ranges are pinned tight and the mixins are `required: true`, so the mod fails loudly (only for the backend whose internals changed) rather than silently mis-filtering; the AE2 style layer falls back to default colours if it can't load. Because each backend's mixin config self-gates on `ModList`, a change in one mod never affects loading the other.
- **Separation is by construction:** each backend has its own store file, its own filter bridge, and its own mixin. There is no shared active-tab state — moving a filter set between AE2 and RS is only possible via the clipboard export/import.
- Component matching is **presence-based** — no value matching (e.g. "enchant level ≥ 3").
- The filter windows and the editor's tree both scroll; the editor's per-tab **condition** rows and the per-window **Terminals…** visibility list don't, so very many conditions / terminal types can overflow. Typical counts fit comfortably.
