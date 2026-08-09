# Release testing overview — Storage Organizer

Verification state of the **unified** mod (AE2 + Refined Storage in one client-side jar)
across the three Minecraft lines. Written **2026-08-04**; read it before tagging.

This is a record of *evidence*, not a quality claim. "No record" means no commit or session
documents a test — not that the thing is broken.

## Gate summary

| Line | Branch | Maintainer sign-off | Headless pass 2026-08-04 | Remaining gap | Tag now? |
|---|---|---|---|---|---|
| **1.21.1** | `unify-storage-backends` | ✅ 2026-07-29 | ✅ full | addon terminals | **yes** |
| **26.1** | `unify-26.1` | ✅ 2026-07-29 (full pass) | ✅ full (+1 bug found & fixed) | addon terminals | **yes** |
| **1.20.1** | `unify-1.20.1` | ✅ RS 1.12 filtering, 2026-08-04 | ✅ full | addon terminals | **yes** |

All three lines carry the *identical* unified core: `7574e0c` (the 1.21.1 tip at sign-off) is an
ancestor of all three, and no commit is missing from any line. They differ only in each line's
own platform port plus the shared rebrand + metadata commits.

**Everything that landed after the two sign-offs is non-gameplay:** the rebrand (class renames,
log prefixes, two clipboard-error strings), the publish dependency fix, and `pack_format`. No
filter, GUI-layout, persistence or input code changed.

---

## Headless pass of 2026-08-04

Run on **every** line via `runServer` + `runClientJoin` and the RCON harness. What each line
supports differs: AE2 terminals only open headlessly on **1.20.1** (that line's harness gained
`ae2build`/`ae2open`), and RS grids open on all three but are always **empty**, because no line's
harness can put items into an RS network.

### Verified on all three lines

- **Renamed mod loads** — `[StorageOrganizer] Client loaded` on client and server dist.
- **Editor renders opaque**, with JEI beside it, and **every lang string resolves** ("Filter Tabs",
  "Windows & Tabs", "Conditions", "Gear: shown", …) — the `pack_format` check.
- **An existing `config/ae2organizer/` survives the rename.** The store path and format are
  untouched. Incidentally proven **cross-line compatible**: the *same* `tabs.json`/`rs.json` pair
  loaded unchanged on 1.20.1, 1.21.1 and 26.1, which is the portability AGENTS.md claims for
  filter exports.
- **Load permutations** — AE2-only / RS-only / neither, via the new `-PnoAe2` / `-PnoRs` switches.
  Every case boots clean to the title screen, the mod loads, the absent mod's mixin config gates
  off without touching its target, and **only the present backend's store is created** (RS-only
  writes `rs.json` and no `tabs.json`). Zero mixin failures; the only mixin-ish lines are the
  expected `Reference map … could not be read` WARNs that AGENTS.md documents as normal in dev.
- **Joining a server without the mod** — a genuinely vanilla server (zero mod-loader references in
  its log) accepted the client on all three lines. **Caveat worth knowing:** this only holds for a
  client where *every* mod is vanilla-compatible. With AE2/RS/JEI also installed, NeoForge itself
  refuses the connection — *"you are trying to connect to a server that is not running NeoForge,
  but you have mods that require it"* — which is those mods' requirement, not something this mod
  can change. Tested properly by dropping them with `-PnoAe2 -PnoRs -PnoJei`. (Forge 1.20.1 is
  more permissive and joined even with all of them loaded.)

### 1.20.1 specifics — `unify-1.20.1`

The only line never maintainer-tested, and the only one with a different RS implementation
(`backend/rslegacy` against RS **1.12**'s `GridScreen`/`IGridView`, not a port).

- ✅ **AE2 filtering end-to-end** — an ME Chest showing 7 items on *All* filtered to exactly the 4
  ingots.
- ✅ **`forge:ingots` vs `c:ingots`, side by side.** The dev config happened to hold both, giving a
  direct A/B: the `forge:` tab matched the 4 ingots, the `c:` tab matched **nothing** — reproducing
  the original bug exactly and confirming `873eb0a` was necessary and correct. Note JEI's sidebar
  still showed 6 ingots for the `c:` tab, because JEI matches tag **paths** and ignores the
  namespace — precisely what hid the bug in the first place.
- ✅ **Fresh install** — with no config, both stores seeded, the seeded *Ingots* tab wrote
  `forge:ingots`, and it filtered correctly. The new-user path is good.
- ✅ Editor/pickers draw opaque (the 1.20.1-only `renderBackground` fix).
- ✅ JEI search sync emits `$ingots` — JEI 15.x's `$` tag prefix.
- ✅ **RS 1.12 filtering of real grid contents — VERIFIED by the maintainer, 2026-08-04.** Hand-built
  Creative Controller + Grid + Disk Drive + Creative Storage Disk in a creative dev world; the
  *Ingots* tab filtered the grid down to just the ingots and cleared back to *All*. **This was the
  last release gate on this line.**
  It needed a human because `setblock` cannot build a *working* RS 1.12 network — RS 1.12 registers
  network nodes on real placement, so a headlessly-placed storage never feeds the grid. Everything
  around it (panel, tab selection, JEI sync) was already verified headlessly; only this step wasn't.

### 1.21.1 specifics — `unify-storage-backends`

- ✅ RS grid opens, panel renders docked, tab selection works.
- ✅ JEI search sync emits `#ingots` — JEI 19.x's `#` prefix, versus `$` on 1.20.1. Both confirmed.
- ❌ RS content filtering not observable (empty grid, as above). AE2 terminals do not open
  headlessly on this line, so AE2 rests on the maintainer sign-off.

### 26.1 specifics — `unify-26.1`

- ✅ RS grid opens, panel renders, tab selection and `#ingots` sync work.
- ✅ **Found and fixed a real bug** (`eeb22f5`): the earlier `pack_format` 34 → 84 correction broke
  the mod's resource pack. MC 26.1 added a minor-version pack scheme — `PackFormat`'s
  `lastPreMinorVersion` is **64** for client resources and **81** for server data, and a bare
  `pack_format` above that is rejected with *"missing mandatory fields min_format and max_format"*.
  84 tripped both thresholds, so NeoForge fell back to the description-only codec on every load.
  Now declares `min_format: 84` / `max_format: 84`; boot logs zero pack errors. This is also why
  AE2 (8), RS (34) and JEI (46) all ship "stale" values on 26.1 — staying under the threshold is
  what keeps a bare `pack_format` legal. The published `26.1` branch has 34 and was never affected.
- **Known cosmetic regression** (pre-existing): 26.1's `StringWidget` dropped `setColor()`, so the
  tag chooser's "no tags" label is untinted.
- ⚠️ **Harness contract broken on this line only:** 26.1's `Screenshot.grab` lost its filename
  argument, so `/rsorgshot` writes a **timestamped** file instead of the fixed
  `screenshots/rsorgshot.png` that AGENTS.md documents. Read the newest `*.png` on 26.1. Cost this
  session a false "the payloads aren't arriving" conclusion — they were arriving fine.

---

## Only the maintainer can do these

1. ~~**RS 1.12 content filtering on 1.20.1**~~ — **done 2026-08-04**, see above. No gaps remain that
   block a release.
2. **Addon terminals** (Wireless Crafting Grid and friends) — *not a release blocker, but the best
   remaining use of 5 minutes.* Not present in any dev client, so they cannot be reproduced there.
   Relevant because the tab-bar offset logic and the *global* JEI GUI handler were both written
   specifically to cover terminals we never name.
3. **Export → Import between AE2 and RS** — the clipboard bridge between the two stores, and the
   only *feature* with no test coverage at all, since it needs real clicks.
4. **`/ae2organizer resetwindows`** — the recovery command; client-side, so RCON can't reach it.

## Not tested, and why not

- **AE2 filtering on 1.21.1 / 26.1 headlessly.** Only the 1.20.1 harness has `ae2build`/`ae2open`
  (AE2 forms its grid from block entities, so `setBlockAndUpdate` suffices). Porting those two
  commands to the newer lines would close it — worth doing only if the AE2 sign-off is ever in
  doubt, since both lines are already signed off for AE2.
- **RS content filtering anywhere.** Would need harness code: place a network storage block, then
  drive `AbstractContainerMenu#quickMoveStack` server-side to shift items in from the player's
  inventory. Feasible on RS2/RS3; likely not on RS 1.12, where the node-registration-on-placement
  behaviour is the blocker.
- **Clipboard export/import round-trip** and the `/ae2organizer resetwindows` client command — both
  need real clicks / a client-side command, neither of which the RCON harness can drive.

## Non-test blockers

1. **`mod_version` must be bumped.** All three unify branches still read `1.3.0`, and
   `v1.3.0-mc1.20.1`, `v1.3.0-mc1.21.1` and `v1.3.0-mc26.1` are **already tagged and published**
   (as AE2Organizer). Tagging again collides — bump each branch first.
2. **Merge into the default branch first.** `publish-curseforge.yml` and `publish-modrinth.yml` are
   triggered by `workflow_run`, which always uses the workflow file from the repo's **default
   branch** — currently `1.21.1`, which still declares `applied-energistics-2(required)` and omits
   Refined Storage. Tag any line before that merge lands and the unified jar publishes with the
   **old** dependency metadata. (`release.yml` is a `push` trigger and does come from the tag, so
   the jar filename is fine.)
3. **CurseForge "Client" environment tag is manual.** mc-publish can't set it; set it on the file
   page after upload. Modrinth's is project-level and already set.
4. **First release confirms the Modrinth RS dependency.** Refined Storage is referenced as base62
   id `KDvYkUg3` rather than the slug `refined-storage`, because this project already hit a Modrinth
   API rejection on a hyphenated slug. Check it appears on the Modrinth version page.
