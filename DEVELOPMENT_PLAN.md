# DEVELOPMENT PLAN — Adaption Wheel (Mahoraga) for Minecraft

Status legend: `[x]` done & verified · `[~]` partially done · `[ ]` planned

## Phase 0 — Audit of the existing mod
- [x] Project audit: full read-through of all sources (`src/main/java/ru/adaptionwheel/`)
- [x] Architecture map: concepts → tasks → levels/one-time flags → item-borne persistence → 1 Hz client sync
- [x] Identified extension pain points (hardcoded if-chains, no GUI, no commands, no API surface)
- [x] Baseline build verified (`./gradlew build` green before any change)

## Phase 1 — Research
- [x] Minecraft 1.21.1 decompiled-source research (NeoForm output in Gradle cache):
      speed/jump factors, powder snow collision+freeze path, berry bush, bubble columns,
      knockback event, BreakSpeed pipeline, attribute modifiers
- [x] Catalogue of vanilla discomfort mechanics and their detection/effect hooks
- [x] Version-compatibility research 1.12 → current (see docs/version-compatibility.md)

## Phase 2 — Extensible adaptation framework
- [x] `adapt/AdaptationDomain` — organizational domains (damage/effect/movement/physics/mining/
      environment/combat/perception/entity/existence/special)
- [x] `adapt/AdaptationDefinition` + `adapt/AdaptationRegistry` — metadata registry with open registration;
      dynamic concept keys keep working via prefix fallback
- [x] `Concepts` extended (new Move_* family, new mutations) without touching legacy keys/save format
- [x] Public task-start bridge on the server core (`AdaptionEvents.startTask/isWearingWheel/dataOf`)
- [x] `api/events/AdaptationCompleteEvent` fired on every completion path
- [x] `api/AdaptionWheelAPI` facade for third-party mods

## Phase 3 — Adaptation to Discomfort (vanilla mechanics)
- [x] Move_SoulSand — soul sand/soil speed factor neutralized (SpeedFactorMixin)
- [x] Move_Honey — honey speed factor + jump dampening removed
- [x] Move_PowderSnow — walk on top; no sink-drag, no freezing from the block
- [x] Move_BerryBush — no snag slowdown, no thorn damage
- [x] Move_BubbleColumn — no whirlpool drag / upward launch
- [x] MovementTriggers — 4-tick exposure sampling starting standard analysis tasks
- [x] Config module toggle `modules.movement`
- [x] Mine_Labor — leveled mining speed from breaking blocks (`modules.mining`)
- [x] Combat_Cooldown — leveled attack-cooldown penalty reduction, MAX ignores it (`modules.combat`)
- [x] Combat_ShieldLock — axes can never disable the shield (`modules.combat`)
- [x] Percep_SteadyGaze — hurt-camera shake removed (client-only mixin) (`modules.perception`)

## Phase 4 — Combo mutations
- [x] Mutation_Thermal (existing) generalized into shared combo-grant flow
- [x] Mutation_Aquatic — Env_Liquid + Env_Drowning → big swim-speed boost + faster underwater mining
- [x] Mutation_Impact — Env_FallDamage + Env_Knockback → hard landings trigger a damaging shockwave
- [x] Mutation_Fist — the adaptation to breaking and its six material tiers; see Phase 15
- [x] Config toggles + tuning values under `mutations`
- [x] All mutations included in All-Adaption item maxing

## Phase 5 — Tooling
- [x] `/adaptionwheel status|list|info|grant|analyze|reset|registry` debug commands
- [x] Adaptation browser screen (keybind K): domain tabs, levels, live progress, descriptions, scrollbar
- [x] Wheel tooltip hint showing the bound key

## Phase 6 — Performance & hygiene
- [x] HUD rainbow palette precomputation (no per-pixel HSB per frame)
- [x] New per-player transient state stored in attachment (no static maps to leak); logout cleanup audited
- [x] Tick-path additions gated at 4-tick cadence, adaptation-check-first ordering in mixins
- [x] Deprecated `EventBusSubscriber(bus = ...)` removed everywhere — FML auto-routes by `IModBusEvent`;
      zero deprecation warnings from the annotation remain
- [x] Env_Liquid swim bonuses tuned below land pace per feedback (efficiency +0.6, swim speed +1.0);
      Aquatic Mastery remains the dolphin-grade tier

## Phase 7 — Documentation
- [x] docs/adaptation-system.md — architecture of the adaptation framework
- [x] docs/mod-api.md — integration guide for third-party mods
- [x] docs/version-compatibility.md — 1.12 → 26.x portability audit
- [x] AGENTS.md updated with the new package layout

## Phase 8 — Verification
- [x] Full Gradle build green after each phase
- [x] Dedicated-server boot smoke test (RCON-driven): clean boot with pre-existing world save,
      `adaptionwheel registry` lists all definitions with correct domains,
      console-target errors handled gracefully, config generated with all new sections
- [x] Client boot smoke test on a live display: all new mixins apply cleanly
      (SpeedFactor/PowderSnow/BerryBush/BubbleColumn/ShieldDisable/AttackCooldown/HurtCam confirmed in mixin log),
      keybinds registered, client reaches title screen and exits cleanly
- [~] Interactive playthrough of movement adaptations/mutations in-game (manual checklist below)

## Phase 9 — Feedback iteration 2
- [x] Adversity fix: survive floor clamped to 1 HP — the old `max(0, health − 30)` math meant guaranteed death
      on the first lethal hit for anyone below 30 max HP (pre-existing port bug, now actually "survive ONE blow")
- [x] Mine_Labor / Combat_Cooldown rebalanced to be perceptible: mining +15→240 %, cooldown recovery 25→100 %,
      action-fed retraining penalty cut from 9 s to 3 s per level
- [x] Lang root cause fixed: concept NAME keys were missing entirely (only desc.* had been added) — raw
      translation keys showed instead of names; both languages verified complete by script
- [x] GUI state labels localized (ADAPTED/ANALYZING/MAX/Lv used hardcoded English); command output no longer
      resolves names server-side
- [x] Deprecated `EventBusSubscriber(bus=...)` removed everywhere; FML auto-routing verified via boot logs

## Phase 10 — Feedback iteration 3
- [x] Combat_Cooldown reworked to true 1.7-style: charge itself now recovers faster (ticker boost in
      `Player.tick`, @Accessor for the LivingEntity field) instead of patching the damage formula —
      visible on the crosshair indicator, MAX = no cooldown at all
- [x] Fixed a build-system trap discovered during verification: a package typo (`adaptionwall`) silently
      compiled classes into the wrong package while masked grep pipelines reported fake BUILD=0;
      all verification commands now run without output-masking pipes
- [x] Command discoverability: `/adaptionwheel` bare prints usage + concept examples; concept/domain arguments
      have tab-completion suggestion providers (registered concepts + dynamic family prefixes / domain keys)

## Phase 11 — Feedback iteration 4
- [x] Env_Lava presented as "Heat / Тепло" (covers fire contact too); concept ID unchanged for save compat
- [x] Combat_SkillIssue ("Skill Issue"): one-time ranged adaptation — arrows home in only onto entities near
      the flight path; shots into empty space stay vanilla (`server/SkillIssueHandler.java`, config `skillIssue`)
- [x] Bug fix: Env_Knockback had no analysis trigger (unobtainable in normal play) — now trained by every
      knockback taken

## Phase 12 — Feedback iteration 5
- [x] Skill Issue display: the adaptation browser's fallback branch showed "Ур.0/8" for pending ONE-TIME
      concepts; one-time now render as NOT YET ADAPTED → ADAPTED (binary, no levels). Mechanically it was
      already single-completion with full effect from the start
- [x] `/adaptionwheel info` works from console (metadata-only view) and shows explicit one-time vs leveled
      semantics, verified live

## Phase 13 — Feedback iteration 6: Dimension Destroy
- [x] Researched the original Terraria mod (`ADAPTIONWHEEL/SpatialRift.cs`, `MahoragaPlayer.PostItemCheck`,
      `MahoragaConfig.EnableSpatialRift`): 3-rift ±5° fan on Sword of Extermination swings, 60-tick timer,
      line-collision blade, max 5 victims, `life=1`+HitEffect sever, stasis/particles/"DESTROY THE DIMENSION"
- [x] Researched Re-Avaritia's Infinity Sword jar bytecode for the MC kill idiom:
      `setHealth(0)` → `die(source)` → `killedEntity` credit
- [x] Implemented `Dimension_Destroy` (one-time SPECIAL): auto-unlock at adaptCount > 450 (config),
      3 rifts per swing via `SpatialRiftProjectile` (noPhysics, swept-line blade, ≤5 severs each,
      NPC-only), procedural additive tear renderer, DIMENSION_CUT SFX, action-bar announcement
- [x] Verified: 45 definitions in live registry, client boots with the new entity/renderer

### Manual in-game checklist (requires a player at the keyboard)
1. Wear wheel → walk on soul sand ~3 s → "ANALYZING SOUL SAND" HUD bar appears; after completion walking speed restored.
2. Repeat for honey (speed + jump), powder snow (walk on top), berry bush (no damage/slowdown), bubble columns.
3. Adapt Env_Liquid + drown underwater → "MUTATION UNLOCKED: AQUATIC MASTERY" → swim speed visibly higher.
4. Adapt fall + knockback → Impact Mastery → jump off ≥6 blocks near mobs → shockwave damages/knocks them back.
5. Press K → adaptation browser opens; tabs filter by domain; ESC/K closes.
6. `/adaptionwheel grant Type_FIRE 8` then max Env_Lava → Thermal Mastery unlocks.

## Backlog / ideas (not started deliberately)
- [ ] Existential adaptation to `/kill` — analyzed, deliberately NOT implemented as auto-immunity
      (see docs/adaptation-system.md § Existential threats)
- [ ] Datapack-driven custom discomfort definitions (JSON → registry)
- [ ] GameTest coverage for the analysis-task lifecycle (blocked on the Curios/gametest clash
      described in Phase 15 — any test that calls `makeMockServerPlayerInLevel` dies before its body runs)

## Phase 14 — Feedback iteration 7: visual overhaul (ldlib2 research)
- [x] Explored `ldlib2/` (LDLib2/Photon editor assets): decoded `slash_trail`/`slash`/`fresnel`/`lightning`
      material NBTs — design cues adopted: HDR bright cores, soft cross-falloff, discard thresholds
- [x] Rewrote `CursedSlashRenderer`: crescent ACROSS the flight path in the vertical plane, sine width
      profile (needle tips), dark `debugQuads` body + additive glow/core with fake-gaussian falloff,
      echo copies, unfold easing; fixed edge-on invisibility (was lying in the horizontal plane)
- [x] Rewrote `SpatialRiftRenderer`: vertical jagged wall ACROSS the path (original's second collision
      line), dark body + violet haze + pulsing white core + flickering crack shards + thin tail
- [x] Root-caused "invisible/terrible" renders: horizontal-plane blade seen edge-on; lightning
      back-face culling (now double-winding quads); frustum culling on 0.5-block hitboxes
      (both projectiles now override getBoundingBoxForCulling().inflate(8))
- [x] Wheel particles: FIREWORK (flat untextured squares) → CRIT sparks
- [x] Verified visually via automated in-game screenshots (QuickPlay + xdotool F2 harness,
      AW_QUICKPLAY env hook in build.gradle)
- [x] 3D pass: slash = three crescents crossed at 0/+60/-60 deg around the flight axis
      (never fully edge-on); rift = crossed walls (across + along the path) with 3D bursting
      crack shards; verified by screenshots

## Phase 15 — Adaptation to breaking: Fist Mastery

The adaptation to breaking itself, plus the material ladder that grows out of it.

- [x] `Mutation_Fist` — unlocked by maxing `Mine_Labor` **and** breaking a Stone-class block
      bare-handed. The break is the analysis: vanilla already lets a bare hand mine stone, it is
      just slow (7.5 s, or ~2.2 s with Labor 8) and yields nothing, which is the adversity being decoded.
- [x] Six material tiers, 8 levels each: Wood → Stone → Copper → Iron → Diamond → Netherite,
      stored as six leveled concepts `Fist_Wood` … `Fist_Netherite` in the MINING domain.
      Maxing a tier opens the next one.
- [x] A fist at tier *n* harvests every block of tiers `0..n`; a level is earned **only** by
      breaking blocks of that tier's own material, so the ladder cannot be side-stepped by farming
      the softest blocks. Difficulty rises three ways at once: rarer blocks, slower bare-hand
      mining, and a per-tier cost multiplier.
- [x] Material classes are plain block tags (`data/adaptionwheel/tags/block/fist_*.json`) built
      from vanilla tags, so modpacks can retarget or extend any tier without touching code.
- [x] **No mixin needed.** `PlayerEvent.HarvestCheck` is the single hook: vanilla gates both the
      drops (`ServerPlayerGameMode.destroyBlock` → `canHarvestBlock`) and the destroy-speed divisor
      (`/30` instead of `/100`) behind that one check, so answering true grants the drops *and*
      the "correct tool" speed bonus in one place. Verified in the NeoForge 21.1.248 patches —
      `Player.hasCorrectToolForDrops` is *not* the live gate any more.
- [x] Mining speed per tier/level folded into the existing Mine_Labor `BreakSpeed` handler so the
      two compose instead of overwriting each other.
- [x] Instabreak: Netherite 8 removes any breakable block in one tick, toggled with a keybind
      (default G). Stance is server-authoritative — the request is re-validated and a rejected
      toggle is answered with a fresh sync. Blocks with destroy speed `-1` (bedrock, end portal)
      stay unbreakable, as in vanilla.
- [x] HUD row for the fist showing `Material [Lv.n > n+1] : done/total blocks`, plus `instabreakActive`
      and the two progress counters added to the sync payload.
- [x] 8 gametests in `test/FistGameTests` pinning the tag contents, the harvest gate, the cost curve
      and the registry wiring. `structure/aw_empty5x5x5.nbt` is a hand-written gzipped binary NBT
      (`data/<ns>/structure/`, **not** `structures/`, and binary NBT, not SNBT text).
- [ ] **Still needs an in-game pass**: the unlock, the level-ups and the Instabreak stance all need
      a real player. NeoForge's `makeMockServerPlayerInLevel()` logs the mock player in, Curios then
      throws `Payload curios:sync_data may not be sent to the client`, and the test fails before its
      body runs — so those paths cannot be covered headlessly until that is worked around.

### Also fixed in this phase (from the code audit)
- [x] Existence reflection was dead code: the immunity cancelled the hit in `LivingIncomingDamageEvent`,
      which fires at the top of `LivingEntity.hurt()`, so the `LivingDamageEvent.Pre` branch that
      called `reflectAttack` could never execute. Reflection moved into the incoming handler;
      `existenceReflection.reflectMultiplier` is live again. The reflected hit is now excluded from
      the wearer's own offense stacking.
- [x] `Type_FIRE`, `Type_CONTACT`, `Type_MOB` and `Type_WITHER` could never be trained (the
      `envCategory` list suppressed `Type_*` analysis), which also made Thermal Mastery unobtainable.
- [x] `offenseScaling.flatDamageBonus` was applied as `damage * max(1, bonus/10)`, a no-op for
      levels 1-6 and a multiplier for 7-8; it is now the flat add the config documents.
- [x] Unequip / death-wipe wrote to the wheel through `getWheelStack()`, which is guaranteed empty
      when the wheel is not equipped (`isEquipped(Item)` delegates to `findFirstCurio(...).isPresent()`).
      Those paths now use the stack captured while worn, so `persistOnItem=false` and
      `clearOnDeath` actually do something and cancelled analyses stay cancelled.
- [x] `PENDING_RESPAWN_HEALTH` was written for every death, letting a non-wearer bank a free
      top-up; it is now gated on wearing the wheel and only consumed on the first tick after a respawn.
- [x] Lifesteal healed a share of the *pre*-reduction hit; adversity lowered the Lv8 i-frames it had
      just granted; `ENABLE_EXISTENCE` was not honoured when consuming an existing immunity;
      `Contact_*` used the raw multi-part entity type while offense/drop unwrapped the body;
      `offenseHitAcceleration` was read by nobody; short config lists were silently ignored;
      `hpPerSecond` was applied every 3 s; `onExperienceDrop` lacked the boss guard its siblings have;
      Dimension Slash could chain without bound.
