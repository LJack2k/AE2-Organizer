# Unify AE2 + RS into one mod (branch `unify-storage-backends`)

## Status — ✅ COMPLETE (stages 1–8), on branch `unify-storage-backends`
Unified jar builds and boots clean with **both** AE2 and RS present. Verified in a dev
server+client: mixins gate per-mod, each backend keeps its own store (`tabs.json` / `rs.json`),
filters never cross, viewer sync + JEI + clipboard work, and each backend renders in its **own
mod's look** — AE2 terminals use AE2's `BackgroundGenerator`/palette (inherits AE2 dark-mode
packs) + AE2's `Icon.COG`; RS grids use the nine-slice panel + RS's own wrench item icon.
The RS side is screenshot-verified; the AE2 terminal side was **signed off by the maintainer in a
dev server+client on 2026-07-29** ("styling is good on all places") — the last verification gap on
this branch is closed. Stage 9 (display-name rebrand to TerminalOrganizer) is done.

**This branch (`unify-26.1`) is the 26.1 port of that work** — same code on MC 26.1.2 / NeoForge
26.1.2.76 / Java 25 against AE2 26.1.10-beta and RS 3.2.1. It was branched from
`unify-storage-backends` and forward-ported, *not* merged from the old pre-unify `26.1` branch
(that fork predates the backend SPI and conflicts in ~20 files); the old branch is superseded and
should be replaced by this one when the port is signed off.

**Not done (needs maintainer go-ahead):** replacing `26.1` with this branch, the merge of
`unify-storage-backends` → `1.21.1`, and a release on either line.

Goal: one client-side mod (kept mod id **`ae2organizer`**) that adds filter tabs to **both**
Applied Energistics 2 terminals **and** Refined Storage 2 grids, with a **hard separation** between the
two — each storage system has its own independent tabs/windows/settings/active-tab, never mixed. Manual
import/export bridges them. (A future "share one set" toggle is trivial to add later.)

Both AE2 and RS are **optional** deps: the jar loads cleanly with either, both, or neither present.

Decisions (from the maintainer):
- **Keep mod id `ae2organizer`** — no config-path or modpack-reference breakage. Rebrand display name only.
- **Merge into this repo** (the published AE2-Organizer project) — reuse CurseForge/Modrinth history.
- **Seamless AE2 continuity:** the AE2 backend keeps reading the existing `config/ae2organizer/tabs.json`
  as its store; the RS backend gets a new `config/ae2organizer/rs.json`. No migration needed.

## Why it's mostly restructuring, not rewriting
The RS-Organizer codebase (`D:\Projects\RS-Organizer`) is already the cleaner, `ItemStack`-based version of
the same mod. Strategy: **adopt RS-Organizer's common code as the shared core**, and fold AE2's specifics in
as one backend. AE2's only mod-specific pieces are its mixin (`Repo#addEntriesToView`) and screen
(`MEStorageScreen`); RS's are its mixin (`AbstractGridContainerMenu#createBaseFilter`) and screen
(`AbstractGridScreen`). Everything else (model, GUI, persistence, viewer sync, import/export) is shared.

## Target package layout (`nl.ljack2k.ae2organizer`)
- `filter/` — shared model over `Predicate<ItemStack>` (from RS-Organizer verbatim).
- `client/` — shared: `TabManager` (now **per-backend stores**), `ClientEvents` (backend-dispatching),
  `ClientBootstrap`, `ClientSetup`, `ViewerSync`.
- `client/gui/` — shared: `Style` (neutral; see note), `TabBarWidget`, editor/picker/settings screens, `RsButton`.
- `persist/` — shared `TabStorage` (parameterised by filename) + `TabShare`.
- `backend/` — **NEW SPI**: `StorageBackend` (interface) + `BackendRegistry`.
- `backend/ae2/` — `Ae2Backend`, AE2 screen adapter, AE2 `RepoMixin` (+ accessors).
- `backend/rs/` — `RsBackend`, RS screen adapter, RS `AbstractGridContainerMenuMixin` (+ search accessor).
- `jei/`, (later) `rei/` — viewer integration, shared.
- `dev/` — the RCON/screenshot harness (from RS-Organizer).

## The SPI (the separation seam)
```
interface StorageBackend {
  String id();                       // "ae2" / "rs" — also the config filename + store key
  boolean isPresent();               // ModList.isLoaded(...)
  boolean handles(Screen s);         // instanceof MEStorageScreen / AbstractGridScreen
  ScreenAdapter adapt(Screen s);     // per-open adapter
}
interface ScreenAdapter {
  int guiLeft(); int guiTop(); int xSize(); int ySize();
  List<Slot> slots();                // for dock-past-protruding-slots
  String terminalKey();              // menu-type id (per-terminal placement key)
  String title();
  void setActiveFilter(Predicate<ItemStack> p);  // push to that backend's mixin bridge
  void refilter();                   // AE2: repo.updateView(); RS: getRepository().sort()
}
```
- **Per-backend filter bridge**: each backend has its own static `Predicate<ItemStack>` holder its mixin
  reads (AE2 `RepoFilterBridge`, RS `GridFilterBridge`). No cross-talk.
- **TabManager** holds `Map<String backendId, Store>`. A `Store` = windows+tabs+activeId+settings+
  terminalNames, persisted to `config/ae2organizer/<backendId>.json` (ae2→`tabs.json`, rs→`rs.json`).
  `ClientEvents` resolves the backend from the open screen and drives that backend's store only.

## Conditional mixins (the load-safety enabler)
- Two mixin configs: `ae2organizer.ae2.mixins.json`, `ae2organizer.rs.mixins.json`.
- A `MixinConfigPlugin` per config gates `shouldApplyMixin` on `ModList.isLoaded("ae2")` /
  `"refinedstorage")` so a missing target mod's mixins are skipped (no crash).
- `neoforge.mods.toml`: both `ae2` and `refinedstorage` become `type="optional"`, `side="CLIENT"`.
- `neo_version` → **21.1.242** (RS 2.0.9 requires it; AE2 19.2.17 is fine on it).

## Build
- `build.gradle`: add RS (`curse.maven:refined-storage-243076:8211701`) compileOnly+runtimeOnly alongside
  AE2 + JEI. Dev runs: keep AE2's `runClient`; add RS to a second run (or same) for testing both.
- The harness (`runServer`/`runClientJoin` + RCON + screenshot payloads) comes from RS-Organizer.

## Staged execution (each stage compiles)
1. Bump neo_version; add RS dep; both deps optional in mods.toml. Baseline still builds (AE2-only).
2. Drop in shared model (`filter/` from RS-Organizer, ItemStack-based).
3. SPI (`backend/`) + `TabManager` refactor to per-backend stores + `TabStorage` filename param.
4. RS backend (mixin+adapter+bridge) folded in; RS mixin config + plugin.
5. AE2 backend: rewrite `RepoMixin` to push `Predicate<ItemStack>` (via `AEItemKey#getReadOnlyStack`);
   AE2 screen adapter; AE2 mixin config + plugin.
6. GUI + ClientEvents made backend-dispatching; wire both.
7. JEI shared; (optional) REI backend-agnostic.
8. Build; test AE2 dev client AND RS dev client via harness; screenshot report.
9. Rebrand display name; docs; merge to `1.21.1` when verified.

## Reference
- Shared/RS code to lift: `D:\Projects\RS-Organizer` (branch 1.21.1).
- AE2 specifics to keep: this repo's current `mixin/RepoMixin`, `MEStorageScreenAccessor`, `client/gui/Ae2Style`
  (AE2's themed style — decide whether the unified GUI themes per-backend or uses one neutral style).
- Open sub-decision: **GUI theming** — AE2 screens themed via AE2's `BackgroundGenerator`/palette (dark-mode
  packs), RS via the sampled nine-slice sprite. Simplest: theme the tab panel per active backend (AE2 look on
  AE2 terminals, RS look on RS grids). The editor/popups can use one neutral style.
