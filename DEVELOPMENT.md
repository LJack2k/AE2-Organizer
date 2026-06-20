# AE2Organizer — Development

Technical reference: building, the config-file format, and how the mod works. For player-facing usage see **[README.md](README.md)**.

## Requirements / toolchain

- Minecraft **1.21.1**, NeoForge **21.1.x** (built against 21.1.193), Java **21**.
- Applied Energistics 2 **[19.2,19.3)** — required at runtime, client side.
- JEI — optional; compiled against for the drag integration, never bundled.
- Gradle **8.10.2** + ModDevGradle **1.0.20**. Multi-project: minimal root + the `neoforge/` subproject (so a `fabric/` module could be added later).

## Building

```bash
./gradlew :neoforge:build       # -> neoforge/build/libs/AE2Organizer-neoforge-1.21.1-<ver>.jar
./gradlew :neoforge:runClient   # dev client with AE2 (+ JEI) for testing
```

The output jar contains only this mod's classes/resources — AE2, guideme and JEI are `compileOnly`/`runtimeOnly` and are not shaded in.

### Versions

Set in `gradle.properties` (`mod_version`, `minecraft_version`, `neo_version`, …). To target a new AE2 build, update `ae2_curse_file_id` (and `ae2_version` / `ae2_version_range`) from the CurseForge file page's "Curse Maven Snippet"; JEI is `jei_curse_file_id`. Both resolve via the CurseMaven repo.

## Config file

Per client, at `config/ae2organizer/tabs.json`:

```json
{
  "version": 1,
  "settings": { "resetFilterOnOpen": false, "showTabLabels": false, "tabScale": 1.15 },
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
- **`mixin/MEStorageScreenAccessor`** — `@Accessor` for AE2's `protected final Repo repo`.
- **`mixin/AbstractContainerScreenAccessor`** — `@Accessor` for `imageWidth`/`imageHeight`, used to position the tab bar.

Mixins are configured in `ae2organizer.mixins.json` (referenced from `neoforge.mods.toml`), `required: true`, all under `client`. No refmap (AE2 ships official names).

### Filter model — `filter/`

`Tab` + `Condition` (`ModCondition`, `TagCondition`, `TextCondition`, `ComponentCondition`) with Codecs. `Tab#toPredicate()` / `Condition#toPredicate()` build a `Predicate<AEKey>`. Conditions guard on `AEItemKey`, so fluid/other keys never match item conditions, and precompute expensive bits (resolved `TagKey`s, lowercased text, registry lookups) because the predicate runs over every key on each terminal view refresh.

### UI — `client/`, `client/gui/`

- **`ClientEvents`** — on `ScreenEvent.Init.Post` for an `MEStorageScreen`, attaches `TabBarWidget` and re-applies the active tab. The bar's mouse input (click/drag/scroll) is routed through the cancelable `ScreenEvent.Mouse*` pre-events, because AE2's terminal overrides `mouseScrolled`/`mouseDragged` and consumes them before added widgets receive them. Registered (client-only) from the `@Mod` constructor.
- **`TabManager`** — client singleton holding tabs, the active selection, and settings; **`TabStorage`** does the JSON persistence (see above).
- **`TabBarWidget`** — the right-hand "Filters" panel: one `BackgroundGenerator` panel sized to the terminal, a title bar (name + gear), bevelled rows (active = sunken), a scrollbar when the tabs overflow, and a `tabScale`-driven size. Its X anchors past the panel image **and** any real menu slot that sticks out (measured to the slot's 18px frame), so it clears terminals with extra card slots (e.g. the Wireless Crafting Grid).
- The screens (`TabEditorScreen`, `ItemPickerScreen`, `TagChooserScreen`, `SettingsScreen`) are plain client `Screen`s — deliberately **not** AE2 `AEBaseScreen`s, which would need a server-side container menu and break the client-only/any-server guarantee.
- **`Ae2Style`** themes those screens through AE2's own pipeline (so AE2 dark-mode packs apply automatically) and replaces vanilla's blurred menu background with a plain dim via a `renderBackground` override:
  - `BackgroundGenerator` draws the nine-sliced `background.png` panel; `StyleManager` + `PaletteColor` supply text colours from `palette.json` (with fallbacks).
  - AE2 widgets used directly: `AE2Button`, `AECheckbox`, and `Icon.COG` (tinted to the palette colour). Text fields are plain `EditBox`es — AE2's `AETextField` rendered border artifacts outside a container screen.
  - Helpers render item icons and text at an arbitrary scale, driving the `tabScale` setting.
- **Inventory drag** is custom: the editor renders the player inventory read-only (never mutating it) and drops resolve against the same `GhostTarget` rects the JEI handler uses.

### JEI integration — `jei/` (optional)

A `@JeiPlugin` registers two things and stays dormant if JEI is absent:
- a **ghost-ingredient handler** (`EditorGhostHandler`) — accepts items dragged from JEI onto the editor's `GhostTarget`s;
- a **screen handler** (`EditorGuiProperties`) — reports the editor's panel bounds so JEI draws its item-list overlay beside this (non-container) screen, which is what makes dragging from JEI possible at all.

## Notes / limitations

- AE2's `Repo` / `MEStorageScreen` and the `appeng.client.gui.style.*` / `widgets.*` classes are **internal, not public API**. The AE2 version range is pinned tight and mixins are `required: true`, so the mod fails loudly rather than silently mis-filtering or mis-rendering if AE2's internals change between versions; the style layer falls back to a default colour if it can't load.
- Component matching is **presence-based** — no value matching (e.g. "enchant level ≥ 3").
- The tab bar and the editor's tab list both scroll; the editor's per-tab **condition** rows don't, so a tab with very many conditions can overflow the window. Typical counts fit comfortably.
