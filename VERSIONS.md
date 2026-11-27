# Changelog / version history

This file is the single place where changes are recorded **from one version to the next**.
Every commit that changes gameplay, UI, networking, data formats or resources should add a
bullet under `Unreleased`. On a release, rename that section to the new version and keep the
list verbatim. Commands used to verify a release go in `tools/README.md`, not here.

Format:

```
## <version> — <short date or "unreleased">
### Added / Changed / Fixed / Removed
- `short-hash` one-line description. (Optional: behaviour note, config key, migration note.)
```

---

## Unreleased

Changes made after the 0.2.0 section was written; fold them into the next release.

### Added

- Two separate top-level menu tabs, `PLAYER` and `TAMED`. `TAMED` is no longer an item in the
  vertical domain rail; selecting it shows a full companion list and a dedicated left card with
  that mob's wheel status. The player view is unchanged.

### Changed / Fixed

- Wheel-wearing mobs now receive **all passive buffs**, not just damage reduction:
  `applyStats` (HP, armor, movement speed, jump strength, swim modifiers) runs for mobs, and
  the ice/slime/honey/web/powder-snow/berry-bush/bubble-column mixins accept `LivingEntity`.
- Knockback denial and harmful-effect denial (Debuff_ adaptations) now work for mob wearers.
- **Adversity is player-only again.** It no longer resurrects wheel-wearing mobs mid-fight;
  a mob can die normally.
- **Untamed mobs cap at level 7.** Level 8 is full immunity/i-frames, so wild wearers stop one
  rung below it; tamed companions and players keep the full ladder. One-time phenomena
  (Env_/Debuff_/mutations) are unaffected. A wheel carrying level 8 is clamped on equip.
- **Mob killers earn their own loot progression.** A wheel-wearing mob that lands the kill gets
  kill counts, Drop_NPC_ analysis and extra loot-table rolls; an un-wheeled pet still credits its
  owner. Player-only luck/messages stay player-only.
- **Killing a wheel-wearing pet no longer drops two wheels.** Curios' equipped wheel is excluded
  from the fallback copies used when a loot table rolls empty.
- TAMED left card shows the companion's face texture (wolf/cat/parrot/horse/llama, with a fallback),
  and the domain rail is available in TAMED too so the mob's adaptations can be filtered by domain.
- `152a069` **Time Warp decimated the wrong ticks.** The server skipped the slow tick and ran the
  other four, so mobs moved and attacked at 4/5 speed while the client rendered them at 1/5 —
  frozen models that still hit. `pular` now skips the non-slow ticks, matching the client and the
  original design.
- `152a069` **R toggles Time Warp off** instead of being ignored while the zone is active. The
  cooldown starts when the zone ends (or when it is switched off early), not when it is cast.
- `3a89f70` **Time Warp clients no longer stay slowed after the zone ends.** The server removed
  the zone silently; the client mirror kept `active = true` and kept decimating rendering and
  entity ticks. `clear` now broadcasts an explicit inactive state, with a direct packet to the
  owner so it also works after walking away from the sphere.
- 214 gametests now pass (new tests pin the server's tick decimation and the toggle).
- `6182b38` **Slash Cut now cuts visibly.** The client packet handler only cached the payload and
  never built the halves; it now calls the effect renderer directly. The captured body uses the
  mob's real model polygons and skin, and the halves are drawn camera-relative with that texture
  instead of untextured world-space triangles. The `slash_cut_*` config keys finally drive the
  cut band, scratch damage/fire/throttle and the packet radius.
- `6182b38` **Time Warp leaves afterimages.** Slowed mobs periodically snapshot their captured
  body and leave a short translucent trail rendered at `AFTER_ENTITIES`.
- `6182b38` **Stopped-time jerking fixed.** Move packets queued vanilla's three-tick interpolation,
  but the client skips most ticks of a warped entity, so it moved in visible steps. The new client
  mixin applies the packet position at once and sets the old position to the previous packet's,
  letting the existing partial-tick interpolation spread the step across the warped interval.

---

## 0.2.0 — in development (Minecraft 1.21.1 / NeoForge 21.1.248)

The version was bumped in `503a683`. All entries below are on branch `1.21.1` and have not
been published to `origin/1.21.1` yet.

### Added

- `0e0b06d` **Mobs can wear the Mahoraga Wheel.** Curios `wheel` slot assigned to every
  vanilla entity type plus `adaptionwheel:disciple`; `/aw mobwheel equip|unequip|status <target>`;
  `Wearers` facade; shared `tickWearer` drives mobs from `EntityTickEvent.Post`; damage
  reduction, immunities, healing, analysis tasks and mutations work for mob wearers. Player-only
  features (HUD, abilities, inventory, flight, achievements) stay player-only.
- `0e0b06d` **3D wheel renders above any `LivingEntity` wearer**, not just players. Curios already
  syncs the wheel stack to clients, so no extra packet was needed.
- `d26bad0` **Shift + right-click equips the wheel on a mob** with a wheel in the main hand.
  Owner gate for owned mobs; occupied slots are not overwritten; `wheel_data` travels with the
  stack; creative mode does not consume the item.
- `0d5b82b` **TAMED tab in the adaptation menu** (read-only): lists the player's own tamed/owned
  mobs, shows their adaptations and task progress from `wheel_data` in the Curios slot, with an
  explicit empty state for a mob that wears no wheel.
- `0d5b82b` **Shift + right-click with an empty hand unequips** a mob's wheel, saving its
  `wheel_data` back onto the stack before returning it to the player.

### Changed / Fixed

- `0d5b82b` **Adaptation voice plays for mobs too** (`ADAPT_VOICE`), with per-UUID and global
  throttles and the vanilla `Players` sound category so the player's sound slider still applies.
- `0d5b82b` **Wheel data survives mob death**: written in `LivingDeathEvent` before Curios drops
  the stack (the previous 1 Hz write could be up to a second stale).
- `0d5b82b` **Friendly-fire gate**: a wheel-wearing mob cannot apply offense/crit or reflect
  damage onto its owner, a fellow pet with the same owner, or an ally.
- `0d5b82b` **All per-wearer UUID caches** (voice, weapon cooldowns, wearing cache, boss caches,
  etc.) are cleared on `EntityLeaveLevelEvent`, so unloaded chunks and despawns do not leak.
- `0d5b82b` **Curios per-tick lookup cache avoided**: wearer checks read the dedicated slot
  directly instead of `findFirstCurio`/`isEquipped`, so equip→unequip in one tick is immediate.
- `0d5b82b` New UI and message strings localized in `en_us.json` and `ru_ru.json` (392 keys, 1:1).
- Post-review fixes on top of `0d5b82b`: global voice cooldown sentinel no longer overflows;
  owner gate compares `getOwnerUUID()` (works for offline owners); new-wearer scan interval
  actually throttles; tamed-tab counters and companion filtering corrected.
- `5fb308f`/`12a2f83` **Time Warp** — a default-unlocked active ability: 10 s slow-time sphere
  (120 s duration, 10 s cooldown), server-authoritative clock, buffered hits/explosions replayed
  on expiry, and a client decimator that halves model/particle/sound playback rate. Config section
  `timeWarp`, 397 lang keys.
- `5fb308f` **Slash Cut** — cursed sword sweeps now cut tamed mobs during the swing: swept-depth
  band determines full cut vs scratch+fire, with a client-side clip/capture pipeline for half-body
  geometry. Config section `slashCut`; design docs in `docs/mob-slicing.md` and
  `docs/time-slowdown.md`.
- `TimeWarpAbility.definition()` no longer reads the server config during common setup (that
  crashed the dedicated server with `Cannot get config value before config is loaded`); the
  authoritative cooldown is read when the ability starts.

### Tests

- 7 new gametests in `MobWearerTests`: Curios slot on a vanilla mob, equip/unequip, wheel-data
  read-back and merge, and reduced damage for a mob wearer. Suite: **212/212 passing** after
  adding Time Warp and Slash Cut (the ability fallback test was updated for the default-unlocked
  Time Warp).
- `python3 tools/check-lang.py` and `python3 tools/check-config.py` both pass.

---

## 0.1.5+mc26.3 — Minecraft 26.3 port branch (separate branch `26.3`)

Kept here because the same mod is built twice against incompatible APIs. The 26.3 port includes
the render pipeline rewrite, Curios reflective optional binding, mixins, UI/content/network/menu
zones, branch-parity batch, lava-swim and starve-floor config parity, dive key and wheel shader
fixes. See branch `26.3` / `integration/26.3-all-port` for its own commits. It is **not** the
source of the 0.2.0 entries above.

---

## 0.1.4+mc1.21.1 — previous 1.21.1 baseline

The mod version before `503a683`. See git history before that commit for the individual changes
(wheel awakening, synergies, altar, ritual blocks, fist ladders, cinematics, configs and tools).
