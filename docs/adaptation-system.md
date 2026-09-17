# Adaptation System — Architecture

## 1. Core model (unchanged since the original port)

The wheel stores adaptation state **on the item** (`WheelData` data component) and mirrors it into a
world-persisted player attachment (`PlayerAdaption`) while worn. All state is keyed by **concept strings**:

| Family | Example | Semantics |
|---|---|---|
| `Type_<CATEGORY>` | `Type_FIRE` | Leveled 1–8 defense against a damage category |
| `Contact_<entity>` | `Contact_minecraft:zombie` | Leveled contact protection per mob |
| `Offense_NPC_<entity>` | | Leveled offense vs mob |
| `Drop_NPC_<entity>` | | Leveled loot bonus driven by kill counts |
| `Env_*`, `Move_*`, `Debuff_*`, `Existence_*`, `Mutation_*` | `Env_Lava`, `Move_Honey` | One-time flags |
| `Self_Damage`, `ADBERSITY` | | Injuries / adversity |

Progression always flows through **analysis tasks** (`AdaptionTask`: concept + countdown). A task is started by
triggers (damage taken, exposure to a hazard, kills, boss combat) and completes in the server tick loop, granting
either the next level or the one-time flag. This single mechanism is deliberately reused for every new domain.

Persistence: `PlayerAdaption.CODEC` (attachment, `copyOnDeath`) ⇄ `WheelData.CODEC` (item component), synced to
the owning client at 1 Hz via `AdaptionSyncPayload`. Concept strings are opaque keys everywhere — new concepts
never require save-format changes.

## 2. Extension framework (`ru.adaptionwheel.adapt`)

Added without altering storage or networking:

- **`AdaptationDomain`** — organizational grouping (DAMAGE, EFFECT, MOVEMENT, PHYSICS, MINING, ENVIRONMENT,
  COMBAT, PERCEPTION, ENTITY, EXISTENCE, SPECIAL). Purely presentational: GUI tabs, HUD sorting/colors,
  command filters.
- **`AdaptationDefinition`** — metadata record `(concept, domain, leveled, maxLevel)`.
- **`AdaptationRegistry`** — static registry with an open `register()` API. Built-ins are registered for every
  vanilla-facing concept; *dynamic* concepts (`Contact_minecraft:zombie`, modded debuffs, …) need no registration —
  `domainOf()` falls back to prefix rules.
- **`api/events/AdaptationCompleteEvent`** — posted on the NeoForge game bus from every completion path
  (`completeTask`, `grantOneTime` incl. mutations, existence grants).

### Adding a new adaptation (checklist)

1. Add the concept constant to `Concepts` (follow the family prefixes; extend `isOneTime`/`color`/display-name
   branches if introducing a new prefix).
2. Register a definition in `AdaptationRegistry.registerBuiltins()` (or at runtime through
   `AdaptionWheelAPI.registerDefinition`) so GUI/commands show it.
3. Add lang entries: `adaptionwheel.concept.<family>.<id>` and optional `adaptionwheel.desc.<id>`.
4. Write the **trigger**: exposure sampling in `MovementTriggers`-style tick code (4-tick cadence), or an event
   handler calling the public bridge `AdaptionEvents.startTask(player, data, concept, seconds*20)`.
5. Write the **effect**:
   - physics/movement restrictions → mixin guarded by `SurfaceAdaptations` (client mirror via `ClientChecks`,
     server via `AdaptionEvents.hasAdaptation`);
   - damage/effect immunity → hooks in `AdaptionEvents.onAttack/onDamage/onEffectApplicable`;
   - passive stats → permanent attribute modifiers in `applyStats` with a change-guard.
6. Register any mixins in `adaptionwheel.mixins.json`.
7. If it combines with others, unlock a `Mutation_*` in the mutation block of `onPlayerTick`.

## 3. Adaptation to Discomfort (movement domain)

New one-time adaptations triggered by sustained exposure, completing through the standard analysis timer:

| Concept | Vanilla restriction | Detection (server, every 4 t) | Full-adaptation effect |
|---|---|---|---|
| `Move_SoulSand` | soul sand speed ×0.4 | block under movement pos ∈ `soul_speed_blocks` tag | speed factor forced to 1.0 (`SpeedFactorMixin` wrap of `Block.getSpeedFactor()`) |
| `Move_Honey` | honey speed ×0.4, jump ×0.5 | honey at feet/below | both factors neutralized; slide-down behavior kept (useful, not discomfort) |
| `Move_PowderSnow` | sinking drag + freeze accumulation | `player.isInPowderSnow` | `canEntityWalkOnPowderSnow → true`; `entityInside` skipped entirely |
| `Move_BerryBush` | snag slowdown ×0.8/0.75/0.8 + thorn damage | AABB scan for sweet berry bush | bush `entityInside` skipped |
| `Move_BubbleColumn` | whirlpool drag / upward launch | bubble column at feet or eye | column `entityInside` skipped |

All effect hooks check the adaptation flag first and only then `instanceof Player`, keeping the cost for
non-wearers at one boolean; all run on both sides against consistent synced state.

## 4. Combo mutations

Unlocked automatically when parent adaptations are complete; grant a distinct ability rather than stacking stats:

- **Thermal Mastery** (`Mutation_Thermal` = maxed `Type_FIRE` + `Env_Lava`) — heat-scaled regeneration.
- **Aquatic Mastery** (`Mutation_Aquatic` = `Env_Liquid` + `Env_Drowning`) — large swim-speed attribute bonus
  (+ water-movement efficiency) and ×1.5 underwater mining even when grounded.
- **Impact Mastery** (`Mutation_Impact` = `Env_FallDamage` + `Env_Knockback`) — landing above the configured fall
  threshold detonates a shockwave damaging/hurling nearby living entities (wearer immune — they adapted to falls).

All three flow through one shared `grantComboMutation` path (message, voice, themed particle burst, event, sync)
and are included in the All-Adaption item's mass grant.

## 4b. Dimension Destroy (transcendence ultimate)

Port of the original mod's `SpatialRift.cs` ("Enable Dimension Destroy" config + Transcendence gate):

- **Unlock**: automatic once the wheel's adapt count exceeds
  `dimensionDestroy.adaptationsRequired` (default **450**, mirroring the user-facing "450 adaptations"
  requirement; the original used its Transcendence threshold). Granted through the shared combo path with a
  SONIC_BOOM burst.
- **Activation**: while wearing the wheel and holding the **Sword of Extermination** (original's requirement),
  every cursed swing additionally fires **three rifts in a ±5° fan**, 60-tick swing cooldown — the original's
  exact fan spread and `DimensionSlashTimer`.
- **The rift** (`SpatialRiftProjectile`): flies straight *through terrain* (`noPhysics`, 240-tick life), and its
  hit test is the swept line of travel inflated by the blade half-width — a screen-wide rending tear.
- **The sever**: at most **5 lives per rift**; each victim is erased via Re-Avaritia's Infinity-Sword idiom
  (verified in its jar bytecode): `setHealth(0)` → `die(playerAttack)` → `killedEntity` credit. This bypasses
  armor, resistance, damage caps and immunity frames. **Players are never targeted** — the original severs NPCs
  only.
- **Presentation**: DIMENSION_CUT sound on first victim, SONIC_BOOM + SQUID_INK burst over each severed target,
  black action-bar "DESTROY THE DIMENSION" once per volley (the original flashes it over every victim), and a
  procedural additive renderer (`SpatialRiftRenderer`) drawing violet glow / dark body / white core strips along
  the trail, roll-tilted like the original's giant slash sprite.

Config: `[dimensionDestroy] enabled / adaptationsRequired`.

## 3b. Mining / Combat / Perception domains

Second wave of Adaptation to Discomfort, exercising the same task pipeline from new trigger sources
(`server/DomainTriggers.java` + the damage pipeline):

| Concept | Domain | Type | Trigger | Full effect |
|---|---|---|---|---|
| `Mine_Labor` | MINING | Leveled 1–8 | `BlockEvent.BreakEvent` accelerates analysis | +mining speed % per level (`discomfortScaling.miningSpeedPct`, default 15→240 %) via `PlayerEvent.BreakSpeed` |
| `Combat_Cooldown` | COMBAT | Leveled 1–8 | `AttackEntityEvent` accelerates analysis | 1.7-style recovery: each tick after a hit closes `cooldownRecoveryPct` (default 25→100 %) of the remaining charge gap, so MAX recharges within one tick — no cooldown at all. Implemented as `AttackCooldownMixin` (@Inject TAIL of `Player.tick` + `LivingEntityTickerAccessor` for the protected ticker), weapon-speed-aware and visible on the crosshair charge indicator; both sides stay consistent because vanilla's own math consumes the boosted ticker |
| `Combat_ShieldLock` | COMBAT | One-time | `LivingShieldBlockEvent` where the attacker wields an item tagged `#minecraft:axes` | `ShieldDisableMixin` cancels `Player.disableShield()` — the single choke point vanilla uses for every shield disable |
| `Combat_SkillIssue` ("Skill Issue") | COMBAT | One-time | every bow/crossbow shot (`ArrowLooseEvent`) accelerates analysis | own arrows/tridents home in ONLY onto entities whose bodies intersect the flight corridor (ahead, within `skillIssue.maxDistance`, within `skillIssue.radius` of the center line minus half body width; steering blends by `skillIssue.strength`). Shots into empty space find no candidate → vanilla ballistics; already-passed entities are skipped, so no U-turns and no invented hits |
| `Percep_SteadyGaze` | PERCEPTION | One-time | every hit taken (damage pipeline) | `HurtCamMixin` (client-only mixin section) cancels `GameRenderer.bobHurt` — no hurt-camera shake |

Leveled-vs-one-time resolution is **registry-first** in `Concepts.isLevelBased/isOneTime`, so mixed families
(`Combat_*` contains both) work without special cases; prefix rules remain as fallback for dynamic keys.

**Retraining speed**: action-fed discomforts (`Mine_*`, `Combat_*`) carry only a 3-second per-level analysis
penalty instead of the usual 9 — every hit/break already feeds the task directly, so progression stays responsive.

**Knockback** (`Env_Knockback`) was originally shipped without any analysis trigger — unobtainable in normal play.
It now starts/accelerates from `AdaptionEvents.onKnockback` on every knockback taken while not yet adapted;
completion keeps cancelling knockback entirely.

**Display note**: `Env_Lava` is *presented* as "Heat / Тепло" since it covers fire contact as well as lava;
the concept ID is unchanged for save compatibility.

## 5. Existential threats (design analysis)

The prompt asks whether `/kill`-class forced death should be adaptable. Decision: **no automatic immunity**.

- `/kill` uses the `minecraft:generic_kill` damage type which bypasses invulnerability checks; hard-cancelling it
  would break map/admin tooling expectations and trivialize challenge content.
- The mod already implements the *interesting part* as a bounded mechanic: **Adversity** — once per cooldown a
  lethal blow leaves the wearer at 30 HP below it (never below **1 HP** — with the vanilla 20-point pool an
  unclamped `20 − 30` meant instant death, so the floor is load-bearing) and starts a 24 s mass-analysis window.
  That is "a last chance", not immortality.
- Void (`out_of_world`) remains covered by the existing `Env_Void` one-time adaptation, matching the original
  Terraria mod's semantics.
- Third-party mods that want deeper integration can listen to `AdaptationCompleteEvent` or implement their own
  survival logic on top of the API; the framework does not force `/kill` immunity on anyone.

## 6. Performance contract

- Tick-path block/state sampling: every 4 ticks, adaptation-check-first, no allocation beyond iterator reuse
  patterns already used by `AdaptionEvents`.
- Curios slot scan: memoized per tick (`WEARING_CACHE`); entity proximity scan: memoized per tick
  (`NEARBY_BOSS_CACHE`); heat scan: cached 15 ticks.
- Mixins return early unless the factor/block actually differs from vanilla (≥ 1.0 fast path).
- HUD rainbow bar uses a precomputed 256-entry palette instead of per-pixel `HSBtoRGB`.
- New transient state lives on the attachment instance (GC'd with the player) — no static maps added;
  existing static maps are cleaned in `onPlayerLogout`.

## 7. Known limitations

- Client rendering code (screen/keybind) is verified by compilation + shared-code review; interactive playthrough
  requires a display and is left as the manual checklist in DEVELOPMENT_PLAN.md Phase 8.
- `@EventBusSubscriber` no longer carries an explicit `bus=` argument anywhere: FML auto-routes each
  `@SubscribeEvent` method by whether its event implements `IModBusEvent` (verified in the loader bytecode and by
  boot logs), which is the non-deprecated pattern on NeoForge 21.1.
- Mixin gotchas hit in this repo: @Shadow resolves ONLY members declared in the exact target class (inherited
  LivingEntity fields need an @Accessor mixin — see `LivingEntityTickerAccessor`); WrapOperation handlers on
  virtual calls must declare the receiver as their first parameter.
