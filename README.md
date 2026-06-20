# AE2Organizer

A client-side [NeoForge](https://neoforged.net/) mod that adds user-defined **filter tabs** to Applied Energistics 2 terminals. Create tabs that narrow the ME/Crafting terminal to just the items you want — by mod, item tag, name, or per-stack data component (NBT) — and switch between them with one click.

- **Minecraft:** 1.21.1
- **NeoForge:** 21.1.x
- **Applied Energistics 2:** 19.2.x (required, client-side)

## What it does

AE2 filters and sorts the terminal item list entirely on the client. AE2Organizer hooks into that client-side view, so tabs are a pure client feature with **zero server load** — they even work when connecting to a server that doesn't have the mod installed.

A vertical tab bar appears on the **right edge** of every ME/Crafting/Pattern/Wireless terminal (AE2's own button toolbar is on the left):

- **Compass (top)** — *All*: clears the filter.
- **Your tabs (middle)** — click to filter to that tab's criteria. Scroll with the mouse wheel if you have more tabs than fit.
- **Comparator (bottom)** — opens the tab editor.

Your tab's filter combines with AE2's own search box (AND), so you can pick a broad tab and then type to narrow further.

### Editing tabs

The editor is a centered window. Pick a tab's icon and the items for `mod`/`tag`/`text` conditions either way:

- **Built-in picker** (no JEI needed): click the icon slot or a condition's **`…`** button → searchable item grid → click an item.
- **JEI drag**: drag an item from JEI onto the icon slot or a condition's value box.

What the picked/dropped item does: `mod` → its mod id; `text` → its display name; `tag` → opens a chooser of *that item's* tags so you don't have to know tag ids; the icon slot → that item.

### Settings

Open the editor and click **Settings…**:

- **Reset filter when opening a terminal** — on: every terminal opens on *All*; off (default): your last active tab is remembered.
- **Show tab names as labels** — on: the bar shows wide labelled buttons; off (default): icon-only cells with the name on hover.

## Tab criteria

Each tab has a list of conditions combined with **Match ANY** (OR) or **Match ALL** (AND):

| Type | Matches | Example value |
|------|---------|---------------|
| `mod` | items from a mod id | `create` |
| `tag` | items in an item tag | `c:ingots` |
| `text` | display name contains text (case-insensitive) | `sword` |
| `component` | a per-stack data component (see below) | — |

Component checks (presence-based in v1):

- `enchanted` — has enchantments (or stored enchantments, for books)
- `named` — has a custom name
- `damaged` — has taken damage
- `custom_data_key` — its custom data contains a given NBT key (the *arg*)
- `component_type` — has a given component type id, e.g. `minecraft:potion_contents` (the *arg*)

> **Tag namespace note (1.21):** common tags use the `c:` namespace on NeoForge — e.g. `c:ingots`, `c:nuggets`, `c:ores`. The old `forge:` namespace no longer exists.

## Where tabs are stored

Per client, at `config/ae2organizer/tabs.json` (`{"version":1,"settings":{...},"tabs":[...]}`). On first run, a few example tabs (Enchanted / Ingots / Named) are seeded. Edits made in-game are written on **Done** (tabs) or immediately (settings).

## Building

```bash
./gradlew :neoforge:build          # produces neoforge/build/libs/AE2Organizer-neoforge-1.21.1-<ver>.jar
./gradlew :neoforge:runClient      # launch a dev client with AE2 to test
```

To bump AE2: update `ae2_curse_file_id` (and `ae2_version` / `ae2_version_range`) in `gradle.properties` from the CurseForge file page's "Curse Maven Snippet".

## How it works (for maintainers)

- **`mixin/RepoMixin`** — `@ModifyVariable` at the HEAD of AE2's `Repo.addEntriesToView(Collection)`. Both the full-rebuild and paused-incremental code paths funnel through this single method before sorting, so shrinking its input is the one place that filters the whole view. The active predicate is stored on the `Repo` instance via the `TabFilterHolder` duck-type interface.
- **`mixin/MEStorageScreenAccessor`** — reads AE2's `protected final Repo repo`.
- **`mixin/AbstractContainerScreenAccessor`** — reads `imageHeight`/`imageWidth` to align the bar.
- **`client/ClientEvents`** — on `ScreenEvent.Init.Post`, attaches the `TabBarWidget` and re-applies the active tab.
- **`filter/*`** — the `Tab` / `Condition` model with Mojang Codecs; conditions evaluate against `AEItemKey` (non-item keys never match).

### Known limitations / risks

- AE2's `Repo`/`MEStorageScreen` are **internal classes, not public API**. The AE2 dependency version range is pinned tight (`[19.2,19.3)`) and the mixins are `required: true`, so the mod fails loudly rather than silently mis-filtering if AE2's internals shift.
- Component matching is **presence-based** (no "enchant level ≥ 3" style value matching).
- The editor lays conditions out top-down without scrolling; a tab with very many conditions can run past the buttons. Typical tabs fit fine.
