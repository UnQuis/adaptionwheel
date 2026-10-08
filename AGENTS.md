# AGENTS.md

## What this repo is

- **Not a source repo for the Terraria mod.** The `3670588280/` folder mirrors the Steam Workshop item **ADAPTIONWHEEL** (Workshop ID `3670588280`): compiled `ADAPTIONWHEEL.tmod` builds under `3670588280/<tmodLoader-version>/`, plus `workshop.json` metadata. The C# source lives elsewhere.
- **Also the source repo for a Minecraft port**: a NeoForge mod `adaptionwheel` (this branch targets **Minecraft 26.3 / NeoForge 26.3.0.x-beta / Java 25**; `main` stays on 1.21.1) (Adaption Wheel, Mahoraga from Jujutsu Kaisen) built from the decompiled Terraria mod for maximum fidelity. The Gradle project lives at the repo root.

## Layout

### Terraria artifacts (`3670588280/`)
- `3670588280/<tmodLoader-version>/ADAPTIONWHEEL.tmod` — compiled mod builds.
- Subdirectory names (`2025.12`, `2026.1`, `2026.2`) are **tModLoader versions**, not mod versions (match the `TMOD.*` header inside each `.tmod`).
- `workshop.json` — Steam Workshop metadata. Edit only when republishing.
- `3670588280/test/` — stale scratch Gradle project holding an **outdated copy** of the mod resources (missing newer assets). Ignore it; never edit it expecting changes to ship.

### Minecraft port (repo root)
- `build.gradle`, `gradle.properties`, `settings.gradle` — NeoGradle userdev 7.1.39 build; NeoForge `26.3.0.26-beta`, Minecraft `26.3`, Java toolchain 25, Gradle wrapper `9.2.1` (no Parchment). **Curios is OPTIONAL** on this branch: its 26.3 build (17.0.0-beta.2) is not a compile dependency at all — `compat/CuriosCompat.java` binds the Curios API reflectively when the `curios` mod is present, and `compat/WheelSlots.java` decides where the wheel counts as "worn": **with Curios bound the `wheel` slot is authoritative and nothing else counts**, and without it (or if its API could not be bound) the off-hand or any main-inventory slot counts. mods.toml declares `curios` with `type="optional"`. Mixins are wired via top-level `[[mixins]]` in `neoforge.mods.toml` → `adaptionwheel.mixins.json` (`compatibilityLevel: JAVA_25`); compile-time deps are `org.spongepowered:mixin` + `io.github.llamalad7:mixinextras-common` (`compileOnly`, both provided by NeoForge at runtime). **No refmap** — NeoForge runs Mojang mappings at runtime, so mixin targets use the same names as dev. See `docs/port-26.3.md` for the API migration notes of this branch.
- `src/main/java/ru/adaptionwheel/` — package layout:
  - `adapt/` — **extensible adaptation framework**: `AdaptationDomain` (organizational grouping: damage/effect/movement/physics/mining/environment/combat/perception/entity/existence/special), `AdaptationDefinition` + `AdaptationRegistry` (metadata registry with open `register()`; dynamic concept keys like `Contact_<entity>` need no registration — prefix fallback covers them). Purely descriptive: adding definitions never changes storage/sync.
  - `api/` — public mod API: `AdaptionWheelAPI` (server-side queries: isWearingWheel/getLevel/isAdapted/getAdaptCount/getActiveTasks + definition registration) and `events/AdaptationCompleteEvent` (game-bus event fired from every completion path: leveled tasks, one-time grants incl. mutations, existence).
  - `category/AdaptionCategory.java` — 16 damage categories (`FALL, WITHER, FIRE, DROWN, FREEZE, LIGHTNING, EXPLOSION, PROJECTILE, MAGIC, MOB, PLAYER, CONTACT, STARVE, SUFFOCATE, VOID, OTHER`) with `match(DamageSource)` using vanilla damage-type tags (`is_fall`, `is_fire`, ...).
  - `category/Concepts.java` — adaptation concept keys (`Type_*`, `Env_*`, `Debuff_*`, `Contact_<mob>`, `Offense_NPC_<mob>`, `Drop_NPC_<mob>`, `Existence_*`, `Mutation_*` combo mutations, `Self_Damage`, `ADBERSITY`) + display-name resolution + per-concept HUD colors (Contact green, Offense red, Drop purple, Mutation orange, matching the original).
  - `config/AdaptionConfig.java` — server + client `ModConfigSpec` mirroring the Terraria mod's `MahoragaConfig` defaults (reduction 5/10/15/20/25/30/45/60%, heal ratios, offense tables, timers, adversity/existence, drop-rate tables 100–10000% / kills 1–1000, instant starve/fall flags, voice volume, stat scaling, HUD options).
  - `data/PlayerAdaption.java` — world-persisted attachment (Codec): permanent levels, one-time adaptations, active tasks, history, adversity state, kill counts (`killCounts`, drives Drop_NPC_ levels), wheel rotation.
  - `data/WheelData.java` + `data/ModDataComponents.java` — `wheel_data` Data Component on the wheel item (levels/adapted/existence/killCounts/history/tasks); adaptations travel with the item when given to another player. The player attachment uses `.copyOnDeath()` and tasks are serialized, so running analyses survive death.
  - `data/AdaptionTask.java` — active analysis task (concept, timer, maxTimer) with Codec.
  - `data/AttachmentTypes.java` — `DeferredRegister` for the player attachment.
  - `item/MahoragaWheelItem.java` — the curio item (`ICurioItem`); `item/AllAdaptionItem.java` — edible item that grants all adaptations when eaten (requires the wheel equipped); `item/SwordOfExterminationItem.java` — two energy modes toggled by right-click (`sword_mode` Data Component: false = positive, true = cursed); `item/ModItems.java` — DeferredRegister.
  - `entity/ModEntities.java` + `entity/CursedSlashProjectile.java` — straight-line cursed slash: flies **until it hits a block** (`onHitBlock` discards), pierces up to `maxHits` targets (each once via UUID set); fired on EVERY swing including empty air — client sends `FireSlashPayload` on `LeftClickEmpty`, server also hooks AttackEntityEvent/LeftClickBlock; count/damage/speed/pierce scale with adaptCount. Records a client-side position ring buffer for the renderer's trail.
  - **Dimension Destroy** (`Dimension_Destroy`, one-time SPECIAL; port of the original mod's SpatialRift): auto-unlocks when `adaptCount > dimensionDestroy.adaptationsRequired` (default **450**, original gates it behind Transcendence) via `grantComboMutation` (SONIC_BOOM burst). Effect: while wearing the wheel and holding the Sword of Extermination, every cursed swing additionally fires **three rifts in a ±5° fan** (60-tick swing cooldown, original's fan/timer). `entity/SpatialRiftProjectile.java`: noPhysics straight flight (240t), swept-segment blade (`AABB(prev,pos).inflate(bladeHalfWidth)`), max **5 lives per rift**, players never targeted (original is NPC-only). The sever is the Re-Avaritia Infinity-Sword idiom seen in its jar bytecode: `setHealth(0)` → `die(playerAttack)` → `killedEntity` credit, bypassing all mitigation. SFX DIMENSION_CUT + SQUID_INK/SONIC_BOOM burst; wearer gets an action-bar "DESTROY THE DIMENSION" once per volley. Client visual: `client/SpatialRiftRenderer.java` — a vertical jagged wall ACROSS the flight path (the original's second collision line) with dark body + violet haze + white pulsing core, crack shards flickering alongside, roll-tilted; thin bright tail behind. Config section `dimensionDestroy`.
  - `client/CursedSlashRenderer.java` — **procedural anime blade arc (v3)**: THREE crescents crossed at 0/+60/-60 degrees around the flight axis (from any camera angle at least two planes are face-on — a single plane collapses edge-on), each crescent sweeps ACROSS the flight path with a vertical bow, width follows a sharpened sine profile (needle tips), dark blade body via `RenderType.debugQuads()` (POSITION_COLOR translucent — visible against bright sky, like the original's black slash) + additive `lightning()` glow/white-hot core with fake-gaussian cross-falloff (5 nested strips), one faded echo trails behind, static roll-tilt (no per-tick spin), unfold-on-spawn easing, far-fade. **Gotchas baked in:** `lightning()` has back-face culling (emit every quad in both windings), `lightning` targets the weather framebuffer, entity culling uses the 0.5-block hitbox (both projectiles override `getBoundingBoxForCulling().inflate(8)`), and mixing `getBuffer()` types mid-layer finalizes the previous buffer (fetch per layer).
  - `server/SwordOfExterminationHandler.java` — sword combat: cursed mode fires slashes with a 10-tick cooldown (`tryFireSlashes`, public — payload entry point); positive mode adds +1% of target's current HP as bonus damage (+ END_ROD sparkles) in `LivingDamageEvent.Pre`. The sword is UNBREAKABLE (`DataComponents.UNBREAKABLE`).
  - `sound/ModSounds.java` — DeferredRegister of sound events (`adapt_voice`, `dimension_cut`, `swing`, `soe_hit1/2`, `ref`) + `assets/adaptionwheel/sounds.json` mapping; the `.ogg`s are converted from the original `.wav`s (ffmpeg, libvorbis).
  - `network/AdaptionSyncPayload.java` + `AdaptionNetworking.java` — server→client sync via `StreamCodec.of` + `playToClient` (registry friendly); `FireSlashPayload` — client→server empty-air swing notice (`playToServer`).
  - `server/AdaptionEvents.java` — core logic: damage reduction, task start/acceleration, Lv5+ heal, Lv8 i-frames, one-time env/debuff adaptations, instant starvation adaptation (first starve damage → adapt + max hunger/saturation), fast fall adaptation → full fall immunity, injuries regeneration, Adversity survival, per-mob offense (damage/crit/armor pen/Dimension Slash + dimension_cut sound), kill-count drop adaptation (`LivingDeathEvent` level-ups + `LivingDropsEvent` extra loot-table rolls via `LootParams`, plus `LivingExperienceDropEvent` XP multiplier from the same drop table), boss existence reflection, knockback immunity, debuff denial (`MobEffectEvent.Applicable`), passive stat modifiers (`applyStats` uses **permanent** attribute modifiers with a change-guard — transient ones let vanilla clamp saved HP to the vanilla max after re-login; death-time health fraction is stored in `PENDING_RESPAWN_HEALTH` and restored once the wheel is worn again, because respawn full-heals into the vanilla max before modifiers return), adapt_voice on completion (throttled per player, `REF` for MAX-level/existence), combo mutation **Thermal Mastery** (`Mutation_Thermal`, one-time: auto-unlocks at maxed Type_FIRE + Env_Lava; heat-scaled regen via `tickThermalRegeneration` — factor 0.15 hot biome / 0.35 near fire-lava-magma (15-tick cached 5×4×5 scan) / 0.7 on fire / 1.0 in lava, heals every 40 ticks, config `thermalRegen*`), 1 Hz sync + item save, wheel sparkle particles (`spawnWheelParticles`: END_ROD shimmer scaled by adaptCount + FIREWORK sparks converging into the wheel while analyzing — port of the original `MahoragaWheelLayer.cs` dust). Unequip cancels running tasks (cleared before the item save) and sends a final sync so the client hides HUD/model; death keeps tasks (attachment `copyOnDeath` + 1 Hz item save). Wheel swaps while worn are detected via the transient `PlayerAdaption.equippedStack` reference — old data is saved back onto the old stack object, then the newly equipped wheel's own data loads.
  - `server/BossHelper.java` — boss resolution for existence reflection (Wither/EnderDragon/Warden + Chaos Guardian + NeoForge boss tag; unwraps vanilla `EnderDragonPart`s, any NeoForge `PartEntity` subclass (used by DE's `DraconicGuardianPartEntity`) and projectile owners via `resolveLiving`).
  - **Adaptation to Discomfort (movement domain)**: `server/MovementTriggers.java` samples the wearer's surroundings every 4 ticks and starts standard analysis tasks for one-time `Move_*` concepts; effects are head-cancel/skip mixins guarded by `SurfaceAdaptations` (adaptation check FIRST, then `instanceof Player`). `mixin/SpeedFactorMixin.java` (@WrapOperation on `Block.getSpeedFactor()/getJumpFactor()` inside `Entity.getBlockSpeedFactor/getBlockJumpFactor` — receiver block captured, so soul sand → `Move_SoulSand`, honey speed+jump → `Move_Honey`; honey slide-down is deliberately KEPT), `mixin/PowderSnowBlockMixin.java` (entityInside skipped + static `canEntityWalkOnPowderSnow` → true), `mixin/BerryBushMixin.java`, `mixin/BubbleColumnMixin.java`. Config toggle `modules.movement`.
  - **Adaptation to Discomfort — mining/combat/perception domains**: `server/DomainTriggers.java` (game-bus events: `BlockEvent.BreakEvent` → `Mine_Labor`, `AttackEntityEvent` → `Combat_Cooldown`, `LivingShieldBlockEvent` + `#minecraft:axes` attacker → `Combat_ShieldLock`; `Percep_SteadyGaze` triggers from the damage pipeline). Effects: `Mine_Labor` = BreakSpeed % bonus per level (`discomfortScaling.miningSpeedPct`), `Combat_Cooldown` = `mixin/AttackCooldownMixin.java` (@Inject TAIL of `Player.tick`: closes `cooldownRecoveryPct` of the missing charge gap per tick via `mixin/LivingEntityTickerAccessor.java` (@Accessor for LivingEntity's protected `attackStrengthTicker` — @Shadow can't see superclass fields), MAX = instant full recharge, 1.7 feel; full-charge cap is weapon-speed-aware through getCurrentItemAttackStrengthDelay()), `Combat_ShieldLock` = `mixin/ShieldDisableMixin.java` (cancels `Player.disableShield()` — vanilla's single choke point for all shield disables), `Percep_SteadyGaze` = `mixin/HurtCamMixin.java` (client-only, lives in the `"client"` array of the mixin config; cancels `GameRenderer.bobHurt`). Leveled-vs-one-time resolution in `Concepts.isLevelBased/isOneTime` is registry-first with prefix fallback. Config toggles `modules.mining/combat/perception`.
  - **Skill Issue** (`Combat_SkillIssue`, one-time, combat domain): `server/SkillIssueHandler.java`. Trigger: every bow/crossbow shot (`ArrowLooseEvent`) accelerates the analysis. Effect: server-tick homing for own `AbstractArrow`s — a candidate must lie AHEAD along the velocity ray within `skillIssue.maxDistance` and within `skillIssue.radius` of the center line AFTER subtracting half its body width; steering blends direction by `skillIssue.strength` per tick. Shots into empty space find no corridor candidate and fly vanilla ballistics; entities already passed are skipped (`along < -0.5`), so no U-turns. Own pets (`OwnableEntity.getOwner()`), allies and spectators excluded. Stuck arrows self-exclude via the zero-motion early-out (no public inGround getter). Config section `skillIssue`.
  - **Env_Knockback trigger**: was previously unobtainable (effect existed, no analysis start) — `AdaptionEvents.onKnockback` now starts/accelerates its task on every knockback while not yet adapted.
  - **Swim tuning**: Env_Liquid bonuses are intentionally BELOW land pace (WATER_MOVEMENT_EFFICIENCY +0.6, SWIM_SPEED +1.0); Aquatic Mastery stacks on top (+2.5/+1.0) as the dolphin-grade tier.
  - **Combo mutations** share `grantComboMutation` (message + max voice + themed burst + event + sync): Thermal Mastery (maxed Type_FIRE + Env_Lava, heat-scaled regen), Aquatic Mastery (`Mutation_Aquatic` = Env_Liquid + Env_Drowning; big SWIM_SPEED + WATER_MOVEMENT_EFFICIENCY modifiers + BreakSpeed ×1.5 underwater even grounded; config `mutations.aquatic*`), Impact Mastery (`Mutation_Impact` = Env_FallDamage + Env_Knockback; landing above `impactMinFallDistance` triggers a damaging shockwave via transient landing-edge tracking on the attachment).
  - **Deprecated-API policy**: no `bus = EventBusSubscriber.Bus.MOD` anywhere — FML auto-routes each `@SubscribeEvent` by whether the event implements `IModBusEvent` (verified in loader bytecode); keep it that way when adding new subscribers.
  - **The Resonance Altar's trade** (Phase 22, and now the mod's only trade): right-clicking it opens a container (`menu/ResonanceAltarMenu`, `TradeScreen`) that sells a mob's adaptations for that mob's own drop **plus experience levels** — the mod's first *offensive* content, and its resonance aura in `RitualAuras` is untouched, because the two answer different questions (who is standing near you vs. what you will spend). Two halves: `server/AltarOfferings` reads `data/adaptionwheel/domain_altar/<mob_path>.json` (58 mobs, generated from vanilla's loot tables) into an item→mobs index on first use, and a bone offers **ten** things because it drops from five kinds of skeleton — each offering *either* `Offense_NPC_<mob>` *or* `Drop_NPC_<mob>`, mob-major so a mob's two rows sit together. A bone is on the price list too, so it now buys eleven rows rather than ten. A `Drop_NPC_` concept is **paid in kills, not levels** (inside `AdaptionEvents.grantToWheel`) because its level is derived from a kill count everywhere else in the mod and assigning it directly would leave that counter lying — eighth-level loot-luck having killed one chicken. The Warden needed no special case: vanilla's `warden.json` already lists the sculk catalyst. The 26 mobs that drop nothing are simply **absent** rather than present-and-empty. Config `altarTradeEnabled`, separate from `altarEnabled` so the trade can be off while the aura stays.
  - **The altar's item→mob index now says what it loaded, and forgets itself when the world ends.**
  `AltarOfferings` is a lazily built static map, which is the worst kind: a data file naming an item
  this build lacks is skipped, a file that fails to parse is logged and skipped, and a mob with no
  file at all is simply absent — so "the altar opens and offers me nothing" is indistinguishable
  from "this item is not an offering" and from "my wheel already knows everything it opens". It
  therefore logs one INFO line on server start (`ServerStartedEvent` → `allMobs`, which also moves a
  directory walk out of the first player's first click), warns per item it could not resolve, and
  clears on `ServerStoppedEvent` — which its javadoc had claimed was happening for a long time and
  nothing actually called.
- **The mapping is generated and shipped, not scraped at runtime**, and that is a decision rather than an accident. Reading "which mobs drop this item" out of the loot tables is the obvious implementation and it was rejected for three reasons: `LootPool`'s entry list is a `private final` field behind `LootPoolEntryContainer`, so reaching the items needs an unwrap that differs between branches; the loot table *shape* differs between versions, so the same walk is two parsers; and a mob's loot is static data being re-derived 58 times. **Vanilla's 84 mob loot tables turned out to be byte-identical between 1.21.1 and 26.3** (`diff -rq`, no differences) — the `functions` → `modifier` / `item` → `name` rename applies to tables *the mod authors*, not to vanilla's — so the data set is one set of files for both branches. A mod that changes its mobs' drops ships its own file.
  - **There is one trading block and one menu, and the mechanics live in the parent rather than in each subclass**: `menu/TradeMenu` (both slots, the pool, the ordering, the item price, the XP price, the legality re-check, server authority) and `TradeScreen` (all drawing, in `extractLabels`). A subclass only says **what it sells**. Two copies of a trade rule is the exact trap this mod has walked into three times already. **`TradeScreen` is deliberately NOT generic any more**: it was `TradeScreen<T extends TradeMenu>` while two blocks shared it, because `AbstractContainerScreen` implements the *invariant* `MenuAccess<T>` (`T getMenu()`) and one screen over two menu types has to be `MenuAccess` in both; with one menu there is no type argument to supply, so it names `ResonanceAltarMenu` outright. (It also cannot be registered as `TradeScreen::new` while generic — the constructor reference's parameter is the type variable and NeoForge's `register(MenuType<? extends M>, ScreenConstructor<M,U>)` cannot infer from it.) `TradeMenu`'s constructor takes the `MenuType` as a parameter rather than asking an abstract method for it, because an abstract method cannot be called from a super constructor.
  - `server/AdaptionCommand.java` — `/adaptionwheel status|list|info|grant|analyze|reset|registry` (reads self-serve, mutations require perm 2); surgical grants go through `AdaptionEvents.debugGrant/debugReset` (no heal/voice/events). Plus **`/adaptionwheel debug aggro` and `/adaptionwheel debug altar [item]`**, which exist because two reports turned out to be answered by the game rather than by the mod: `aggro` prints each answer `TargetingConditions.test` consults (difficulty, gamemode and `abilities.invulnerable`, `canBeSeenByAnyone`, `canBeSeenAsEnemy`, effects, and the nearest mobs' current targets), and `altar` names the mobs an item is mapped to. A diagnostic that has to be reasoned about from source is a diagnostic that gets argued with; one that prints the answer ends the argument.
    **The aggro one exists because the mod was innocent**: `Player.canBeSeenAsEnemy()` is `!abilities.invulnerable && super`, and `GameType.updatePlayerAbilities` sets that flag for **creative and spectator** — so a player testing in creative is untargetable *by design*, and every symptom (zombies ignoring you, an enderman losing interest, a golem not retaliating) follows from that one boolean. Worth knowing before changing any targeting code.
  - `client/AdaptationScreen.java` — in-game adaptation browser (domain tabs incl. ALL, levels/ADAPTED/MAX states, live task progress bars, optional `adaptionwheel.desc.*` descriptions, scrollbar/scissor clipping, no pause); opened by `client/AdaptionKeybinds.java` (default K, MOD bus registration) via `client/AdaptionScreenOpener.java` (game-bus ClientTickEvent.Post polling `consumeClick()`); wheel tooltip shows the bound key behind an `FMLEnvironment.dist.isClient()` guard.
  - **Chaos Guardian compat (soft, string-ID based — no compile/runtime dependency on DE)**: `server/DraconicCompat.java` holds every DE identifier (`draconicevolution:draconic_guardian`, guardian wither/crystal, damage ids `guardian`, `guardian_laser`, `guardian_projectile`, `chaos_implosion`, `crystal_move`) and resolves guardian-linked sources (parts → body, projectile owner, withers/crystals inherit) to the canonical existence path. `server/GuardianDirectDamage.java` + `mixin/GuardianLaserMixin.java` handle the fully charged laser, which applies damage via a direct `setHealth` call in `LaserBeamPhase.serverTick`, bypassing ALL NeoForge damage events; the mixin is a `@Redirect` on that single callsite (handler receiver must be exactly `Player` — Mixin rejects supertypes at apply time), routed through `AdaptionEvents.handleDirectHealthReduction` (existence immunity / EXPLOSION+contact reductions / Lv5 heal / Adversity survival). Applied conditionally via `mixin/AdaptionMixinPlugin.java` (checks `FMLLoader.getLoadingModList()` for `draconicevolution`). Once `Existence_ChaosGuardian` completes, the wearer is immune to the whole arsenal and contact hits are reflected at `existenceReflection.reflectMultiplier` (default ×3, like the original) with knockback; grant also maxes Type_EXPLOSION + Type_PROJECTILE + Type_FIRE. Config: `modules.chaosGuardian`, `timing.existenceProximityBlocks`. Verified live: mixin applies cleanly when summoning the guardian on a dev server with DE installed.
  - `client/ClientAdaption.java` (client mirror), `client/AdaptionHud.java` (HUD panel with per-concept colored task bars + 2D wheel), `client/AdversityOverlay.java` (adversity HUD ported from the original: red vignette + cinematic bars + "ADAPTING TO ADVERSITY" title + [SS:CS] countdown + symmetric shrinking bar, cooldown note, totem-style wheel flying at the face on trigger then gray heartbeat wheel; **depends on live server sync — the adversity tick branch must keep syncing**, it early-returns otherwise), `client/WheelRenderer.java` (3D wheel above head via `RenderLivingEvent.Post`: fixed in **world space** — cancels body yaw, spins and bobs only; renders for any player wearing the wheel), `client/DharmaChakraModel.java` (wheel geometry **hardcoded in Java**, converted from the Bedrock Blockbench model; texture `textures/entity/dharma_chakra.png`), `client/ClientSurfaceHandler.java` (lava swim = water swim via tick-based dynamics conversion).
  - `mixin/FrictionMixin.java` (`LivingEntity.travel`: clamps ice/slime friction to 0.6 for adapted players → vanilla ground physics apply unchanged), `mixin/SlimeBlockMixin.java` (no stepOn slowdown / no bounce), `mixin/WebBlockMixin.java` (cobweb sticks at half vanilla strength), `mixin/SpeedFactorMixin.java`, `mixin/PowderSnowBlockMixin.java`, `mixin/BerryBushMixin.java`, `mixin/BubbleColumnMixin.java` (see Adaptation to Discomfort above), `mixin/GuardianLaserMixin.java` + `mixin/AdaptionMixinPlugin.java` (see Chaos Guardian compat above; the plugin is wired via `"plugin"` in `adaptionwheel.mixins.json` and skips the laser mixin when DE is absent). Shared checks live in `SurfaceAdaptations` (+ `ClientChecks`, lazily loaded client-only class). Env_Liquid also removes the underwater mining penalty via a permanent `SUBMERGED_MINING_SPEED` modifier (Aqua-Affinity mechanism) + a BreakSpeed ×5 compensation while swimming.
- `src/main/resources/` — `META-INF/neoforge.mods.toml` (incl. top-level `[[mixins]]`), `adaptionwheel.mixins.json`, `pack.mcmeta`, `assets/adaptionwheel/` (lang `en_us.json`/`ru_ru.json`, textures `textures/entity/wheel.png` + `textures/entity/dharma_chakra.png` + `textures/item/*.png` + `textures/slot/empty_wheel_slot.png` + `icon.png` — wheel textures/icon converted from the original Terraria `.rawimg`, `sounds/*.ogg` — converted from the original `.wav` files). **Crafting chain**: `data/adaptionwheel/recipe/mahoraga_wheel_wood.json` (shaped: any log center + 8 sticks → `mahoraga_wheel_wood`, a plain stackable item) then an anvil upgrade in `AdaptionEvents.onAnvilUpdate` (`AnvilUpdateEvent`: wooden wheel + gold ingot, either slot order, 1 ingot, 10 XP levels → `mahoraga_wheel`). The old gold+clock shaped recipe was removed. Item textures `mahoraga_wheel*.png` are author-provided 32×32 sprites (not Terraria conversions).
- **The Adaptation Temple is a generated structure, and it is the altar's home in the world.** A hand-built 21x12x22 ruin is shipped as `data/adaptionwheel/structure/adaptation_temple.nbt` and placed by a **jigsaw** structure, because that is the only vanilla mechanism that runs a *processor list* over a single template — which is where the variety comes from. The building (one `adaptionwheel:resonance_altar`, mossy/cracked/chiseled stone bricks and cobblestone in blocks/slabs/stairs/walls, a `stone_button` floor inlay, iron + oxidized copper chains, 3 soul lanterns, an oxidized lightning rod) **carries its own ground layer**: template `y = 0` is a full 21x22 sheet of grass, dirt and the ruin's floor bricks, which is what makes it sit *in* the landscape instead of on it — the piece is seated so that layer *is* the terrain's top block layer. 795 of the saved 5544 blocks survive import; the other 4749 are air.
  - **`tags/worldgen/biome/has_structure/adaptation_temple.json` decides where that grass is allowed to land.** The ruin paints its own grass and dirt now, so a temple in a desert or badlands is a green rectangle on sand — biome list and template are one decision split across two files. Shrink the tag (or make the ground layer `coarse_dirt`/`podzol`) if that ever shows up.
  - `worldgen/structure_set/adaptation_temple.json` — `random_spread`, `spacing: 32`, `separation: 16`, `salt: 2611`. One number to change if it is too rare or too common.
  - `worldgen/structure/adaptation_temple.json` — `minecraft:jigsaw`, `step: surface_structures`, `terrain_adaptation: beard_thin`, `project_start_to_heightmap: WORLD_SURFACE_WG`, `start_height {absolute: 0}`. **`size` must be at least 1**: the piece is only handed to the builder inside `if (maxDepth > 0)` (`JigsawPlacement.addPieces`), so `size: 0` produces a start with zero pieces and `/place structure` answers "Failed to place structure" with no log line anywhere. There are no jigsaw blocks in the template, so `size: 1` never expands — it just gets the piece placed. The piece is randomly **rotated** per temple and lands with its floor layer on the terrain's top block layer (`getFirstFreeHeight` at the piece's centre, minus `groundLevelDelta = 1`), which is what makes it read as a ruin sitting on the ground. **`beard_thin` is the second half of "seamless"**: it is a terrain-*density* bump centred on the piece's ground level (a Gaussian in `Beardifier.getBeardContribution`, ±11 blocks), so a floor that lands on a slope gets the gap beneath it filled and the joint bedded in instead of hovering — the same setting villages use. `none` is one line away if the ruin ever wants to float on a cliff.
  - `worldgen/template_pool/adaptation_temple.json` — one `minecraft:single_pool_element` with `processors: "adaptionwheel:adaptation_temple_variety"`.
  - `tags/worldgen/biome/has_structure/adaptation_temple.json` — **land biomes only**, assembled from vanilla tags (`is_forest`, `is_taiga`, `is_hill`, `is_mountain`, `is_jungle`, `is_savanna`, `is_badlands`) plus a few explicit ones; oceans, rivers, beaches and cave biomes are deliberately absent. **A structure set whose structures' biome sets are empty is silently dropped**: `ChunkGeneratorStructureState.hasBiomesForStructureSet` filters the whole set out and then `/locate structure` answers "Could not find a structure of type ..." with no error in the log.
  - `worldgen/processor_list/adaptation_temple_variety.json` — **generated, not hand-written**: 56 `minecraft:rule` rules that swap stone bricks ↔ mossy/cracked/chiseled/infested, cobblestone ↔ mossy, `iron_chain` → the four copper-weathering stages, and demossify in the other direction so a temple can be *less* mossy than the original. **The altar, the ground layer (grass and dirt), the `stone_button` inlay, the lanterns and the lightning rod are never touched** — the temple's whole point is that it holds a working altar, and the ground is the seam rather than the weathering. `RuleProcessor` seeds its randomness from the block's world position, so a given temple looks the same every time you load the world while two temples in one world differ.
  - **The variety has to be spelled out per state, because the data format cannot carry properties.** `ProcessorRule.outputState` is one fixed `BlockState`, so `random_block_match` on `stone_brick_stairs` would flatten every stair in the ruin into a default-state north-facing stair; vanilla solves the same problem in Java (`BlackstoneReplaceProcessor` copies FACING/HALF/TYPE by hand). Hence one `minecraft:random_blockstate_match` rule per *exact* input state with its properties repeated in the output, derived from the template's own palette by `tools/temple_variation.py`.
  - **Every `minecraft:air` in the template is DROPPED, not turned into `minecraft:structure_void`.** Placement writes **every** block in the palette: `StructureTemplate.placeInWorld` loops the whole block list and `setBlock`s each one, skipping nothing but the chunk box. `structure_void` is stripped when a template is *saved* (`StructureBlockEntity` adds it to `ignoreBlocks`), which is why no vanilla template contains one and why converting air to it looks like the safe answer — it is not. It is an invisible **real** block (`StructureVoidBlock`, `replaceable`, no collision, no loot), so a "seamless" conversion buries a 13x13 ring of grass and terrain in nothing at all, and the damage is invisible too. Dropping the air means the position is simply not in the template and nothing outside the ruin is ever written. `tools/temple_variation.py check` fails if air or `structure_void` comes back. (`--keep-air` / `--allow-air` exist for a sealed building whose interior should be carved into a hillside — not for an open ruin.)
  - `tools/temple_variation.py` — three offline modes: `template <world-save nbt>` (copy a vanilla `/structure save` output into the mod, dropping its air and rebuilding the palette; prints a note when the saved window is bigger than the ruin, since a footprint that straddles a slope is the other half of "does not look like it belongs here"), `rules` (regenerate the processor list from the shipped template), and `check --jar <decompiled outputs.jar>` (fails if the template carries air or `structure_void` again, if a template state has no rule, if a rule names a block that does not exist in this Minecraft version, if a rule moves a block into a family with different properties, or if a rule touches the keep-unchanged list). Run `check` after any edit to the `.nbt` or the swap table.
  - To test it: `/locate structure adaptionwheel:adaptation_temple` on a **fresh** world (it reports the chunk of the nearest candidate), then look — or `/place structure adaptionwheel:adaptation_temple <x> 0 <z>`, which places one immediately in a loaded chunk. Do not judge the biome tag from a world that already has a structure start saved near the search origin: `/locate` answers from an existing start, so a retry after a failure can look like a fix. **Structures never change in already-generated chunks** — a fixed template shows up only in new terrain, so test with `/place structure` or a new world.
- **Curios wheel slot**: dedicated slot `wheel` that only accepts the Mahoraga Wheel — `data/adaptionwheel/curios/slots/wheel.json` (validator `curios:tag`), item tag `data/curios/tags/item/wheel.json`, player assignment `data/adaptionwheel/curios/entities/player.json`. **Entity-file format is flat**: `{"entities": ["minecraft:player"], "slots": ["wheel"]}` — a nested `"minecraft:player": {...}` object parses fine but silently assigns nothing (no log error). The wheel itself only equips in this slot (`MahoragaWheelItem.canEquip`).
- The 3D wheel's model is Java code in `DharmaChakraModel`, converted from the Blockbench source. The `.bbmodel`/`.json`/`.png` sources themselves are **no longer in the repository**; to change the wheel's geometry, edit `DharmaChakraModel` and the texture in `assets/`.

## The six features added after the wheel awakening work

All six exist on **both** branches (`1.21.1` and `26.3`) and are configured, not hard-coded.

- **Adaptations can be switched off from the panel.** `PlayerAdaption.disabled` (a `Set<String>`) is the whole
  mechanism, and it rides inside `Extras` — see the codec trap below. The client's mirror is
  `ClientAdaption.DISABLED`. The switch is a small `fill()`-drawn switch on every row of
  `client/AdaptationScreen.java`, clicked through `ToggleAdaptationPayload` (client→server intent only; the
  server rejects a concept the player does not have and is the only thing that mutates the set).
  **The deliberate split is the whole design**: `isAdapted`/`level` keep reporting what is *stored*, and only
  `active`/`levelOrZero` (both on `PlayerAdaption`) are what effects read. Anything else would make turning an
  adaptation off look like never having had it — and `DomainExchange.isFinished` would then sell it to the
  altar again, which is the exact thing a switch is supposed to prevent. Every effect site asks
  `data.active(...)`/`data.levelOrZero(...)`: the `Env_*` immunity blocks in `onAttack`/`onHurt`, the reduction
  loop, `applyOffense`, `applyEnvEffects`, `applyStats`, the debuff-denial loop, `HardFist.bonus`,
  `adaptedExistenceTarget`, and `tickFlight` (which **revokes** `mayfly` again). `startTask` and
  `startOrAccelerate` both refuse to start or accelerate a disabled concept, so a disabled adaptation does not
  re-analyse either. `AdaptionHud` greys the row and prints `[OFF]`.
  **The client mirror needs the same two accessors or the switch is half a switch.** This shipped broken and the
  server half hid it: the switch worked, showed, and stopped the damage reduction — while the darkness lightmap
  kept lifting the lightmap anyway. `ClientAdaption` now carries `active`/`levelOrZero` identical to
  `PlayerAdaption`, and every client-side effect gate reads those: `client/DarknessLightmap` (`Env_Darkness` —
  the lightmap path is different on this branch, see below, but the gate is the same one), `client/SeaEyeFog`,
  `client/ClientSurfaceHandler` (`Env_Lava`), `mixin/HurtCamMixin` (`Percep_SteadyGaze`), the cache key in
  `client/AdaptionScreenOpener` (`Env_Inventory`), and `ClientChecks.has`/`level`, which is what every mixin
  reaches the movement adaptations through. **A new client-side effect must ask `active`/`levelOrZero`, never
  `isAdapted`/`ADAPTED`.** The one deliberate exception is `client/AdaptationScreen`, which reads the raw sets
  on purpose: the panel has to *display* a disabled adaptation, greyed and marked `[OFF]`.
- **The panel's domain tabs are a vertical column**, because twelve domains plus `ALL` never fit in one row at
  any panel width. The column is `tabX`/`tabW` wide, has its own `tabScroll` (the mouse wheel over the column
  scrolls it, and a 1 px track is drawn when it overflows), and the list shifts right of it. `panelH` was raised
  to 200–340 to give it room. Both the tabs and the row switches are plain `int[]` rects tested through the same
  click path, so they needed no widget. One trap worth keeping: a row switch is **rejected when its box falls
  outside the scissor viewport**, or a half-scrolled row would be clickable where nothing is drawn.
- **Sea Eye** (`Mutation_SeaEye`, one-time SPECIAL): `Env_Liquid` + `Env_Drowning` + `Env_Lava`. It removes
  liquid fog. The rule lives once in `client/SeaEyeFog.suppresses(FogType)`; the mixins only translate the
  answer. **1.21.1 asks the camera for fog in two places** — `FogRenderer.setupColor` (colour) and
  `FogRenderer.setupFog` (distances) — so there are two `@Redirect`s on
  `Camera#getFluidInCamera()Lnet/minecraft/world/level/material/FogType;`, returning `FogType.NONE`.
  **26.3 has exactly one private `FogRenderer.getFogType(Camera)`** feeding both `setupFog` and
  `computeFogColor`, so there is a single `@Inject` at its HEAD returning `FogType.ATMOSPHERIC` (which is also
  what that method itself synthesises from `NONE`). Neither branch touches `Camera#getFluidInCamera` itself:
  `Camera.modifyFovBasedOnDeathOrFluid` and `GameRenderer.getFov` read it too, and "no fog" must not become "no
  underwater FOV narrowing". `POWDER_SNOW` is passed through — the mutation is about liquids.
  Returning the game's own "no fog" value is also the mod-compatible answer: whatever a mod did to make its
  fluid foggy, it did it by making the camera report it.
- **Flight** (`Mutation_Flight`, one-time SPECIAL): Y ≥ `flightAltitude` (310) **and** `Contact_minecraft:phantom`
  at max level **and** an adaptation to `Debuff_minecraft:levitation`. `tickFlight` sets
  `abilities.mayfly` and calls **`player.onUpdateAbilities()`** — not a hand-built
  `ClientboundPlayerAbilitiesPacket`, because that is vanilla's own choke point and survives API churn. It runs
  every tick and only acts when `!mayfly`, because abilities are rebuilt on respawn and on every gamemode
  change; it never touches a creative or spectator player.
  **`mayfly` is not "flight" — `mayfly` is permission to flight, and the second flag is the flight.**
  The grant used to set `mayfly = true` and `flying = false`, which reads correct in a save file and does
  nothing in the game: `mayfly` only *arms* vanilla's double-tap-to-fly, and the client then does
  `if (onGround && flying && !isAlwaysFlying) flying = false`, so a player standing anywhere never leaves
  the ground. **Both flags must be set, which is what vanilla's creative mode does and what IMDS's
  `WaaProcedures.godMode` does** (`mayfly`, `flying`, `instabuild`, `onUpdateAbilities()`) — that mod was the
  reference for how to turn flight on at all. The rule now lives in `server/FlightAbility.apply(Abilities,
  boolean)`, which sets both and **returns whether anything changed** so `tickFlight` only sends
  `ClientboundPlayerAbilitiesPacket` on a real transition rather than every tick; the repair also covers the
  half-armed state (`mayfly` true, `flying` false), which is exactly what the old code left behind.
  **Flight was unreachable for a while because equipping the wheel ERASED it, and the erasure was invisible.**
  `WheelData.loadInto` used to `clear()` every collection before copying the wheel item's `wheel_data` over
  them, so `loadFromItem` on the equip transition was a **replace, not a load**: anything living only in the
  player attachment was destroyed with no message. An adaptation granted while the wheel was off therefore
  survived right up to the equip and was then gone — so `Mutation_Flight` was granted, reported success, wiped
  on the tick that filled the slot, and `tickFlight` then found it unadapted **with all three of its
  preconditions wiped alongside it**, which is unrecoverable: nothing left to re-grant from. That is the shape
  to recognise — a feature that "was granted" and then does nothing. `loadInto` is now a **merge**: levels and
  kill counts take the higher value (a hand-over must not walk a concept backwards), sets union, history
  dedupes, and a running analysis is kept rather than added twice, because two tasks for one concept would
  complete the same adaptation and pay out twice. **This is the one place where Curios being OPTIONAL changes
  the story**: `wearingWheel` goes through `CuriosCompat` reflection here and is a hard dependency on 1.21.1, so
  a bug in `loadInto` presents as "the mutation is granted but nothing happens" on both branches but is only
  reachable by a player who actually put the wheel in the slot. **`/adaptionwheel debug flight [player]`
  prints every gate `tickFlight` reads** — worn, adapted, enabled, gamemode, `mayfly`/`flying`, all three
  preconditions — plus whether the attachment and the wheel item disagree, and ends with the one line naming
  the blocking condition. The two regression tests are on `1.21.1` only; this branch has no gametest framework,
  so the merge semantics here are covered by nothing automated.
- **Hard Fist** (`Combat_FistDamage`, **leveled**, COMBAT domain — deliberately *not* under `Fist_`, which belongs
  to the mining tiers and is walked by index): trained by hits with a bare hand or with any item that adds no
  attack damage, and by kills. `server/HardFist.java` holds the formula
  `(base + perLevel × level) × (1 + perAdaptation × adaptCount)`. Two decisions: the bonus is **added** in
  `LivingDamageEvent.Pre` (`applyFistDamage`) **before** `applyOffense`, so the per-mob offense bonus, the crit
  and the adapt-count multiplier all scale the punch instead of the punch being computed outside them — and
  with a weapon in hand the sum lands on top of the weapon, which is what was asked for. "Adds no attack
  damage" is **`FistTiers.dealsExtraAttackDamage`**, which was made `public` for this: the definition of "bare
  hand" must not exist twice.
- **Inventory adaptation** (`Env_Inventory`, one-time ENVIRONMENT): all 36 slots *and* the offhand filled starts
  the analysis (`CacheService.inventoryIsFull`); the reward is a **50-slot personal cache** of its own, opened
  with `V` (`AdaptionKeybinds.OPEN_CACHE_KEY`) through `OpenCachePayload`, which re-checks server-side.
  **Why it is a separate container and not a longer `Inventory`**: the two reference mods settle this by their
  source. `Funwayguy/InfiniteInvo`'s `BigInventoryPlayer extends InventoryPlayer` allocates
  `mainInventory = new ItemStack[invoSize + 9]` and copies only the first 36 — the four armour slots and the
  offhand live at indices ≥ 36 and get overwritten by ordinary items, and the whole thing only works by
  *replacing* `player.inventory` with the subclass. `Lothrazar/OverpoweredInventory` (a fork of it) fixed exactly
  that by **not** subclassing: the vanilla inventory is untouched and the extra slots are a separate
  player-persisted `IInventory` with its own container and GUI. Ours follows OverpoweredInventory.
  `CacheMenu` therefore holds a `SimpleContainer` view and writes it back into `PlayerAdaption.cache` on
  **every `broadcastChanges` and on `removed`**, so an open container cannot lose a stack to a crash or a
  logout. `pad()` normalises the list to exactly 50 without touching the contents (pinned by a test).
- **Synergies now do something that scales.** Every one of the ten already had an effect, but all of them were
  flat, and the two whose requirements are terminal (GOLIATH, ASTRAL_MINE) had nothing left to scale with.
  `SynergyEffects.refresh` now also stores a per-player `strength` per synergy: `1 + 3 × progress`, where
  `progress` is the mean completion of that synergy's own *leveled* requirements, falling back to
  `min(1, adaptCount / 60)` when it has none. Every effect multiplies by it. `Unseen` scaled by distance
  (`36 × strength`, so "mobs lose track of you" gets truer), and Goliath's "cooks what it touches" is an
  on-hit ignite that shares one `ignite()` helper with Ashwalker — `Math.max` of the two, never an overwrite.

### Two traps from this work that will bite again

- **`RecordCodecBuilder.group` accepts at most 16 components.** `PlayerAdaption` was already at 16, so
  `disabled` and `cache` went into a nested `data/Extras.java` record and the attachment gained exactly one
  component. That record is a `Codec` on 1.21.1 (`RecordCodecBuilder.create`) but on 26.3
  `PlayerAdaption` is built with `mapCodec`, whose `group()` only accepts `MapCodec` components — so `Extras`
  exposes a plain `Codec` on both branches and the attachment uses `Extras.CODEC.optionalFieldOf(...)`.
- **26.3 spells `FogType`'s "no fog" answer `ATMOSPHERIC`, 1.21.1 spells it `NONE`,** and only 1.21.1 has the
  two-call-site fog shape. Copying a mixin between the branches compiles nowhere; `SeaEyeFog` is the shared part
  and nothing else is.

### The three follow-ups: All Adaptation, the inventory files, and the punching fist

All three are on **both** branches and configured, not hard-coded.

- **The All Adaptation item granted everything except the things added most recently.** It reported
  success and left the player unadapted, which is the worst shape a bug can have: no error, no
  message, and the only evidence is noticing a concept is absent. The cause was structural rather
  than a typo — `grantAllAdaptations` carried a **hand-written copy** of the core concept list, so
  `Env_Inventory`, `Mutation_SeaEye`, `Mutation_Flight` and the five fist stages were simply not in
  it. **It now iterates `AdaptationRegistry.allDefinitions()`**, which already answers "which concepts
  exist and does this one have levels", so a concept registered anywhere is granted by construction
  and a future one needs no edit here at all. Only the two families that are minted at runtime and so
  cannot be registered — debuffs and the per-entity `Contact_`/`Offense_`/`Drop_NPC_`/`Existence_`
  keys — are still walked explicitly, over the live registries. The registry is descriptive and
  adding a definition still changes nothing about storage or sync; this just means the item reads it
  rather than duplicating it.
- **The same silence hid a second half of that bug: debuff keys had two live spellings.** The
  runtime minted `Debuff_<path>` from `Identifier.getPath()`, which **drops the namespace**, while
  flight's gate and the altar named `Debuff_minecraft:levitation`. So the item granted levitation
  denial under a key nothing ever asked about — eating it did not enable flight — and both spellings
  sat in real save data. `getPath()` is wrong for a third reason: `othermod:poison` and
  `minecraft:poison` both mint `Debuff_poison`, so two mods shipping the same effect path silently
  shared one adaptation, and `Identifier.tryParse` rejects a string with no colon, so the bare key
  could not resolve a display name either and fell through to a raw lowercase literal.
  **`Concepts.debuff` now normalises once** — always namespaced, resolving a bare path against the
  effect registry (`minecraft` wins a tie) — so every call site is correct by construction rather than
  by being remembered. `debuffId`/`debuff(Holder<MobEffect>)` are the runtime entry points.
  `data/LegacyConcepts` renames the old spelling on disk, and it must run on **both** load paths:
  the attachment *and* the wheel item's component, because `loadInto` merges the item's stored keys
  back in on every equip and would otherwise undo the attachment's migration on the next one. One
  rule, two call sites. **On this branch the migration is new rather than moved** — 1.21.1 had a
  `Fist_Copper` rename inside `WheelData` that never existed here, so `LegacyConcepts` is the only
  migration this branch has had, and `LegacyConcepts` is the right place for it.
- **The punching fist is now a material ladder, wood → stone → iron → diamond → netherite**, eight
  levels a stage, a stage opening only when the previous one is maxed — deliberately the shape of
  `FistTiers`, because that is the shape the player already learned. `category/CombatFistTiers.java`
  is the table; `server/HardFist.java` is the ladder. **Unlock and every level are earned the same
  way: kill a hostile mob bare-handed to death** — a KILL, last blow landed by the hand, nothing in
  it that adds attack damage (`!FistTiers.dealsExtraAttackDamage`, the same single definition of
  "bare hand" the breaking fist uses). That is the mining fist's gate applied to something no
  normal player does, and a kill rather than a hit so the ladder cannot be farmed on cows.
- **The fist's bonus sums every trained stage rather than reading the highest one, and that is not a
  style choice.** A stage's own `(base + perLevel x level)` times its material multiplier is *smaller
  at level 1 than the material below it is at max* — wooden Lv.8 is 7.0 while stone Lv.1 is 3.5 — so
  keying off the highest stage pays out **less** the moment a player advances, which is backwards for
  a reward. Summing is monotonic in both level and stage: training can never lose damage, and the
  later materials still dominate because they carry the larger multipliers. Measured with the default
  tables before writing the code, not tuned by feel.
- **`CombatFistTiers` deliberately has no `reachTier`, and `HardFist.currentTier` is hand-written.**
  1.21.1's version of this ladder reached for `FistTiers.reachTier`, which answers "which stage is
  *unlocked*", so a maxed wooden fist reported **stone**, and stone at level 0 pays nothing — two
  tests failed on exactly that before it was removed. The breaking fist wants that semantic (maxing
  wood *is* why you get stone speed) but the punching fist pays for what was trained, so the tier is
  the highest stage with levels, and `ClientAdaption.combatFistTier()` mirrors it. Two helpers with
  different meanings is the shape to expect whenever a ladder is read both as capability and as
  progress.
- **Two fists, two HUD rows and two payloads.** `FistProgressPayload` feeds a row gated on and named
  after the *breaking* fist's tier, so sending the punching fist's counters through it drew the wrong
  fist's row and drew it at all for a player who never unlocked that one. `CombatFistProgressPayload`
  carries `done/total/tier`; the tier is in the packet because maxing a stage hands over to the next,
  and leaving the finished stage's numbers up would freeze the bar at its last fill. Kill progress is
  pushed **per kill**, not on the 1 Hz sync, or a kill is invisible for up to a second.
- **Config `fistDamage`** replaced `analysisSeconds` (a wall-clock timer fit for "hits or kills") with
  `firstLevelKills`, `levelCostGrowth` and two lists, `tierKillCost {1, 2, 3.5, 6, 10}` and
  `tierDamageMultiplier {1, 2, 4, 8, 16}`. **A `defineList` validator must be
  `AdaptionConfig::isDouble`, never a raw lambda** — the predicate's argument arrives as `Object`, so
  `o -> o >= 1.0` does not compile (`bad operand types for binary operator`). **This branch also had
  no `listValue` helper**, so one was added, and it falls back to the shipped defaults on a
  wrong-length list rather than clamping: clamping would silently give netherite the diamond entry.
  Note the existing `fistTierCost` on this branch *does* clamp, so the two now differ on purpose —
  the breaking fist self-repairs its table, the punching fist is the newer rule.
- **Four places do not copy between the branches** and each one failed to compile rather than to
  behave: `Identifier` not `ResourceLocation`; `PacketDistributor.sendToPlayer` not
  `ClientPacketDistributor`; `ResourceKey.identifier()` not `Holder`'s `location()`; and there is no
  `grantConceptLevel` here — the level bump is `AdaptionEvents.completeTask(player, data, concept)`,
  whose `applyGrant` with `targetLevel = -1` does `data.level(concept) + 1` clamped to max, which is
  exactly what 1.21.1's `grantConceptLevel` did.
- **`inventory_files/`** at the repo root holds read-only copies of the 50-slot cache's five files
  (`CacheMenu`, `CacheMenuProvider`, `CacheScreen`, `CacheService`, `OpenCachePayload`) for review.
  The live sources are the ones under `src/main/java/ru/adaptionwheel/`; these are a copy and will
  drift, so edit there.
- **1.21.1 has the matching tests and this branch does not** (`AllAdaptationTests`, 6 tests, plus the
  tier-aware rewrite of `HardFistAndSynergyTests`). This branch has no gametest framework, so the
  guarantees here rest on `./gradlew build` plus the 1.21.1 suite covering the same logic.

### Flight could not be left, and the cache layout the player redrew

- **"Из режима полета нельзя выйти" — and the cause was the tick loop writing `flying = true`
  every tick.** Four vanilla sites, each individually reasonable, add up to the trap, and the fix is
  worth recording because the *obvious* reading of the code is wrong:
  - `LocalPlayer.aiStep` — double-tapping jump toggles `abilities.flying = !abilities.flying`, and
    the whole branch is guarded by `if (abilities.mayfly)`, so **permission alone is enough for the
    toggle**;
  - `LocalPlayer.aiStep` again — `if (onGround() && abilities.flying && !gameMode.isAlwaysFlying())
    abilities.flying = false;` clears it on landing;
  - `ServerGamePacketListenerImpl` — `player.getAbilities().flying = packet.isFlying() &&
    player.getAbilities().mayfly`, so the server **adopts** whichever the client sent;
  - `tickFlight` — which put it straight back on the next tick, authoritatively.
  So both ways out lasted exactly one tick. `FlightAbility.apply` now writes `flying` **once, on the
  permission transition**, and leaves it alone after — which is also why the `if (apply(...))` early
  return was so harmful: it fired *precisely* when the player had just turned flight off, and that
  is the corrective packet that put them back in the air.
  **`MultiPlayerGameMode.isAlwaysFlying()` means SPECTATOR** (`return localPlayerMode ==
  GameType.SPECTATOR`), not "the `flying` flag is set". Reading it the other way produces a confident
  and completely wrong diagnosis — that is where the earlier `flying = true` "fix" came from, and its
  javadoc asserted the false claim, which is what made the bug repeatable. **A wrong javadoc is a
  bug that reproduces itself; when a fix and its reasoning disagree, re-read the vanilla source
  rather than the comment you just wrote.**
- **The cache GUI's layout now comes from the player's own rewrite** (`inventory_files/` → live
  sources), and it fixed two real defects that nothing had caught. **Only the layout crosses
  verbatim**: `CacheService` keeps this branch's `player.getOffhandItem()` instead of 1.21.1's
  `inventory.offhand.get(0)`, and `CacheScreen` had to be edited by hand because this branch draws
  in `extractLabels(GuiGraphicsExtractor)` with relative coordinates rather than in
  `renderBg(GuiGraphics)` with `leftPos`/`topPos` — copying the file puts a `GuiGraphics` where a
  `GuiGraphicsExtractor` belongs.
  - **The offhand slot was removed, and it was never an offhand slot.**
    `new Slot(player.getInventory(), Inventory.getSelectionSize(), ...)` — `getSelectionSize()` is
    **45**, a *hotbar* slot; the offhand is `getSelectionSize() + getArmorSize()` = 49. With the new
    `playerSlotY` it also sat one row *below* the panel. Most container GUIs do not show the offhand,
    so dropping it is right rather than a regression.
  - **`moveItemStackTo`'s range is half-open `[index, end)`.** The call passed
    `slots.size() - playerBase` as `end`, so the upper bound was wrong by exactly the number of
    cache slots; it is now `slots.size()`.
  - `PANEL_HEIGHT` is **derived** (`HOTBAR_Y + SLOT + 7` = 223, was a literal 200) and
    `CacheScreen.inventoryLabelY` is `PLAYER_INV_Y - 11` instead of `imageHeight - 94`. The literal
    was correct only for the old height, so the two had to move together — which is the argument for
    deriving it.
  - **`CacheLayoutTests` (3 tests)** pins the arithmetic: every cache and player row inside the
    panel, and the hotbar separated by exactly `HOTBAR_GAP`. A slot drawn one row below the panel
    throws nothing and reports nothing, which is the whole reason the offhand slot survived; pure
    layout maths in a drawing routine is where a test earns its keep.
- **`inventory_files/` is now a mirror, not a source.** It was how the player handed over a rewrite;
  it is committed and kept identical to the live sources, but the live ones are authoritative and
  will move on.

---
## Commands

- **The jar's name says which game it is for**: `build/libs/adaptionwheel-0.1.5+mc26.3.jar`, from
  `version = "${mod_version}+mc${minecraft_version}"` in `build.gradle`. The manifest repeats it
  (`Minecraft-Version`, `NeoForge-Version`, `FMLLoader-Type`) for anything that reads a jar without
  unpacking. Both come from `minecraft_version` in `gradle.properties` — the same property
  `neoforge.mods.toml` is expanded with — so the name cannot drift from what the mod declares.
  `main` produces `adaptionwheel-0.1.4+mc1.21.1.jar`, and the two are deliberately distinguishable
  at a glance because the same mod has to be built twice against two incompatible APIs.
- Build: `./gradlew build --no-daemon` (first run downloads Minecraft + runs neoForm; long). Runs/tests: `./gradlew runClient`, `./gradlew runServer`, `./gradlew runData`. No lint/test framework configured.
- Headless server smoke test: enable RCON in `run/server/server.properties` (`enable-rcon=true`, `rcon.port=25575`, `rcon.password=...`), also set `pause-when-empty-seconds=0` (a paused server accepts the RCON connection and then silently drops every command), start `./gradlew runServer --no-daemon &`, poll the log for `Done (`, then drive it with a minimal RCON client — Gradle does NOT forward stdin to the server console, piped commands are lost. The RCON packet is `length, id, type, payload, 2 nulls`; **omit the length field and the server closes the socket right after your AUTH**, which looks exactly like a wrong password. A bare `execute if …` answers `Test passed` / `Test failed` in 26.3, and **there is no `/say` command any more**, so probe with a bare `execute if block <x> <y> <z> <block>`. To find out where a `/place structure` actually put its blocks, remember the piece is rotated at random (the altar lands 6 blocks from the chunk corner in one of four directions) — or read the region files: chunk `block_states.palette` is a plain list of block-id strings in 26.3.
- Decompiled Minecraft sources for mixin/API research: `build/neoForm/neoFormJoined*/steps/decompile/output.jar` (unzip selected classes; Mojang mappings = runtime names).
- `docs/`, `tools/`, `DEVELOPMENT_PLAN.md`, `REVIEW.md` and the `dharma_chakra.*` Blockbench sources were **removed from version control** on request; they are still in git history if a tool is ever wanted back. Everything they said is now here.
- `runData` writes generated resources to `src/generated/resources`; delete that folder after use.

## Gotchas

- `.tmod` files are **not** plain ZIPs: `TMOD.<version>` header + .NET length-prefixed version + hash + signature + 4-byte length + file table + raw-DEFLATE blobs (`zlib.decompress(blob, -15)`). Extract with `/tmp/opencode/tmod_extract.py`; decompile with `ilspycmd` (`.NET` tool) — the decompiled reference source lives at the repo root in `ADAPTIONWHEEL/` (incl. original `Assets/Sounds/*.wav`, textures, shaders, localization).
- `.rawimg` Terraria textures = XNB TiledImage: int32 tile count, then per tile (int32 w, int32 h, raw BGRA pixels). Convert with Python/PIL.
- Surface physics: ice/slime/cobweb are handled by **mixins** (`mixin/` package) so vanilla physics apply unchanged on both sides; lava swimming is tick-based velocity conversion in `ClientSurfaceHandler` (+ server mirror `AdaptionEvents.applySurfaceEffects`) because NeoForge 21.1 has no fluid-movement hook. Don't replace the mixin approach with post-tick velocity multipliers — that halved walk speed (the original bug).
- **Important:** `gradle.properties` is read by Gradle as ISO-8859-1 — keep `mod_description` and any `${...}`-expanded values in `neoforge.mods.toml` ASCII-only or the text will be mojibake'd.
- **NeoForge 21+ dependency format:** `[[dependencies.<modid>]]` uses the `type` field (`required|optional|incompatible|discouraged`); the legacy Forge `mandatory=true/false` key is silently IGNORED and every parsed dependency defaults to REQUIRED. The DE soft-compat entry once crashed every install without Draconic Evolution because of this.
- **The `ModConfig.Type` enum was renamed wholesale between NeoForge 26.3 builds, and the repo deliberately sits on the older end of that.** NeoForge `26.3.0.26-beta` (`FancyModLoader 12.0.1`) is `COMMON / CLIENT / SERVER / STARTUP` and is what this repo builds against and what the **user's own instance runs**; `26.3.0.43-beta` (FML 12.0.8) is `LOCAL / CLIENT / SYNCED / STARTUP`. The user asked for the older target on purpose: **Curios' 26.3 build only works there**, so building against the newer NeoForge would mean the Curios path could never be run, let alone tested, in dev. So `SERVER` → `SYNCED` *and* `COMMON` → `LOCAL` in one step. A renamed enum constant is a **binary** break, so a jar built against one end dies with `NoSuchFieldError` inside `AdaptionWheel.<init>` before any mod code runs — the launcher never recompiles anything. This is not hypothetical: the 16:45 crash on the user's pack showed our mod asking for `SERVER` **and Curios asking for `COMMON`**, and the fix was to downgrade the pack to 26.3.0.26-beta. `AdaptionWheel.configType(String...)` therefore looks every config type up by **name** over `ModConfig.Type.values()` (`"SYNCED", "SERVER"` for the server spec, `"CLIENT"` for the client one) — the compiled class contains **zero** `getstatic` references to `ModConfig$Type` constants (check with `javap -c`), which is the actual guarantee: the jar cannot die of this at all, on either loader.
- **A reflective proxy must never invent an answer to a question that reads as a permission.** The Curios proxy overrode `canUnequip` with `defaultAnswer`, whose synthesized value for a `boolean` is `false` — the honest reading of "no opinion" — and Curios reads `false` as *this item may never be removed*, because the removal path is `CuriosStacksResourceHandler.extract → ICurio.canUnequip(SlotContext)`. The wheel sat in its slot looking perfectly equipped and could not be taken off by any means, silently, and a proxy answering `false` is indistinguishable from one answering correctly about a slot the item does not belong in. Two rules, both now load-bearing in `WheelCurioHandler`: **a permission is answered with the permission's real default** (`canUnequip → true`, which is Curios' own), and **a method with no answer here is asked of the interface** (`InvocationHandler.invokeDefault`) instead of guessed at. Match override names by name and not by arity — `ICurio.canEquip(SlotContext)` and `ICurioItem.canEquip(SlotContext, ItemStack)` are the same question twice, and Curios calls both. And because Curios never logs the answers it gets, `Impl.selfCheck` asks the fresh proxy the three questions that matter and logs what it said at boot (`wheel bound — accepts the wheel slot: true, accepts the head slot: false, can be taken off again: true`), which is where this class's behaviour becomes visible instead of inferred.
- **Version ranges stay wide, on purpose.** Pinning `dependencies.neoforge` to exclude the old spelling does not fix a binary break — it only moves the failure from the dependency check into the constructor, and it makes the mod refuse to load on a perfectly good NeoForge. `neo_version_range` is `[26.3.0,)` and `mod_id`'s `minecraft_version_range` is `[26.3]`; a version mismatch should not be able to stop the game from starting, so binary breaks get handled in code.
- **In Maven version ordering a qualifier sorts *before* the release: `26.3.0.43-beta` < `26.3.0.43`.** So a lower bound of `[26.3.0.43,)` rejects the very build it was written for — the launcher refuses to load with `Mod ID: 'neoforge', Requested by: 'adaptionwheel', Expected range: '[26.3.0.43,)', Actual version: '26.3.0.43-beta'`. If a tight bound is ever really wanted, the spec has to carry the qualifier: `[26.3.0.43-beta,)`. Verified with `VersionRange`/`DefaultArtifactVersion` from `maven-artifact`, which is what FML uses.
- **Curios on 26.3 only exists for the OLD loader half, which is why `neo_version` is `26.3.0.26-beta`.** `curios-neoforge-17.0.0-beta.2+26.3.jar` references `ModConfig$Type.COMMON`, so it loads on FML 12.0.1 and **cannot** load on FML 12.0.8 — it dies with `NoSuchFieldError: ModConfig$Type.COMMON` before any of its own code runs, exactly like our mod did with `SERVER`. To exercise the Curios path in dev, drop that jar into `run/client/mods/`; without it the reflective binding is skipped and the wheel uses the off-hand/inventory rule instead.
- `gradle.properties` has one range, `neo_version_range`, and `neoforge.mods.toml` expands `${neo_version_range}`. There used to be a second property, `dependencies_neoforge_version_range`, that nothing referenced and that AGENTS.md told future sessions to "keep in sync" — deleted, because a duplicate source of truth for a version bound is a trap.
- **An unbalanced `push`/`pop` in a `ModConfigSpec.Builder` is invisible by construction, and it does not fail the build or the boot.** `pop()` on an empty stack is a silent no-op, not an exception, so one `pop` too many just leaves the current section closed: every key defined afterwards is written one level too shallow and reads back from that same wrong path, so the mod behaves identically and nothing reports anything. The damage is to anyone who already has the file. This mod shipped one on **this** branch, and the mirror-image one on `1.21.1` (there `mutations` was never closed, so eight sections landed as `[mutations.X]` and were silently reset to defaults on load). Neither is caught by any check, so the way to see it is to count: walk the builder counting `push`/`pop` **per builder variable** — there are two, `s` and `c`, and one counter across both reads as a false positive — and require both to end at **zero**. Then confirm against the generated file rather than the code: `grep -n "^[[:space:]]*\["` over a freshly written `adaptionwheel-server.toml` lists every section *including nested ones*, and the nesting is the thing under test.
- NeoForge 21.1 API quirks hit during development: `LivingDamageEvent.Pre` (no `LivingIncomingDamageEvent`/`AttackEvent`); `DamageSource.type()` returns `DamageType` (use `typeHolder().getRegisteredName()` for the id); `AttributeModifier(ResourceLocation, double, Operation)` with `Operation.ADD_VALUE/ADD_MULTIPLIED_BASE`; `AttributeInstance.removeModifier(ResourceLocation)`; `MobEffectEvent.Applicable.Result.DO_NOT_APPLY`; `PlayerTickEvent` lives in `net.neoforged.neoforge.event.tick`; `StreamCodec.composite` supports at most 6 components (use `StreamCodec.of` for more); `Mob` has no `isBoss()` — boss detection lives in `BossHelper` (hardcoded Wither/EnderDragon/Warden + Chaos Guardian + boss tag); loot params in 1.21.1 use `ATTACKING_ENTITY`/`DIRECT_ATTACKING_ENTITY`/`LAST_DAMAGE_PLAYER` (renamed from `KILLER_ENTITY`/`DIRECT_KILLER_ENTITY`/`LOOTING_ENTITY`); `LootParams.Builder` takes a `ServerLevel`; `FoodData` has no `getMaxFoodLevel()` — max food is the constant 20.
- Mixin quirks hit with the DE laser mixin: an `@Redirect` handler parameter must match the **exact** receiver type in the target bytecode (`Player`, not `LivingEntity` — supertypes are rejected at apply time with `InvalidInjectionException`); `@At(target = "setHealth(F)V")` without an owner matches any receiver, which keeps the injection resilient; optional mixins into another mod's classes must be gated through an `IMixinConfigPlugin` (`shouldApplyMixin` → `FMLLoader.getLoadingModList().getModFileById(...)`) or Mixin logs errors for the missing target class.
- Draconic Evolution facts (from the jar at the repo root): the Chaos Guardian (`draconicevolution:draconic_guardian`) is NOT in any boss tag — recognition is hardcoded via string id; its parts are NeoForge `PartEntity`s, so generic part unwrapping covers them; ALL guardian damage types bypass armor/resistance/effects/shields/cooldowns and most are tagged `is_explosion`/`is_projectile` (so vanilla-tag categories already partially apply); the charged twin laser writes health directly (handled by mixin). DE needs CodeChickenLib + BrandonsCore at runtime for dev-server testing (fetchable from Modrinth).
- **Wheel awakening, synergies, shedding, resonance, transfer, ritual blocks, the Disciple and the
  advancement tree are ported here** (Phase 20, commit `34f7d20`). Same design as `main`; see the
  corresponding bullets in the package-layout section and `DEVELOPMENT_PLAN.md` Phase 20 on `main`
  (this branch has no Phase 20 section of its own). Verified by a clean dedicated-server boot:
  0 errors, 1900 advancements loaded, and every new config section present in the generated toml.
- **Wheel awakening reveals per-mob families only; `Env_` is core.** `category/WheelTier.java`:
  six tiers (Dormant → Infinite) derived from `getAdaptCount()`, so there is no stored state and no
  migration, and a wheel handed to another player arrives pre-awakened. The gate
  (`requiredTierFor`) opens `Contact_` at 1, `Offense_` at 2, `Drop_NPC_` at 3, `Existence_` at 4,
  and one `if` in `AdaptionEvents.startOrAccelerate` — the single choke point every analysis
  funnels through. **`Env_` used to be gated behind tier 1 and that was wrong**, on both branches:
  the gated families are the ones that scale with the world (one concept per mob and per boss,
  hundreds of them) while there are thirteen environments and they are as basic as damage types, so
  gating them meant a player could not begin adapting to water at all until they held twelve
  adaptations. It reads as a feature being switched off, not as progression. The environments are
  core here and stay core. Revealing and never restricting is the point: the wheel is omnipotent,
  so a later tier is only ever a larger one.
- **There is ONE trading block: the Resonance Altar.** The Domain Stone is **deleted**, not merged in
  spirit only — no block, item, menu, screen, menu type, recipe, advancement, blockstate, model,
  loot table, texture, pickaxe-tag entry or lang key survives it. Right-clicking the altar asks
  **both** questions of the same offering item — `DomainExchange`'s price list and `AltarOfferings`'
  item→mob index — and shows the union, so a bone (on the price list *and* dropped by five
  skeletons) buys eleven rows. What survives is `menu/TradeMenu.java` (all the arithmetic),
  `menu/ResonanceAltarMenu.java` (the one subclass, which says what it sells),
  `menu/ModMenus.java` (one `MenuType`), `TradeScreen.java` — now **not generic**, because with one
  menu there is no type argument to supply and the subclass that existed only to provide one was
  deleted rather than kept as ceremony — and the two payloads. The two blocks had identical
  mechanics; two copies of a trade rule is the trap this mod has walked into three times already.
  The altar also keeps the resonance aura, so its two jobs have two config switches
  (`altarEnabled` for the aura, `altarTradeEnabled` for the trade) and the menu opens on the second
  alone. It used to hand an adaptation over **free** on a cooldown; it trades.
  **An item narrows the pool rather than naming an adaptation**: a recipe is a list of selectors,
  each an exact concept or a family ending in `*` (feather → `Debuff_levitation`, ender eye →
  `Env_Void`, nether star → `Type_*`). Families rather than a fixed list so the pool is *derived*
  and a recipe needs no edit when the registry grows — **and because `Debuff_*` and `Drop_NPC_*` are
  not in `AdaptationRegistry` at all** (their keys are minted at runtime), so a recipe naming either
  would offer nothing, silently. An **exact** selector is therefore resolved directly rather than
  looked up. **The tier does not filter the pool**: the altar is a shortcut towards what the wheel
  has not reached, and tiers reveal rather than restrict. `ADBERSITY` is never sold — it is a
  challenge, and the price would be an item for a fight.
- **The pool is the item's offerings MINUS what the wheel in the slot has finished — and it is empty
  without that wheel.** Both halves are the rule, and an earlier pass here got the first half wrong
  in the other direction: an adaptation already bought *or already adapted to by suffering* must
  **disappear** from the list. It is not a defect that the list changes as the wheel fills — it is
  the list. Paying levels for something the wheel already has is the worst outcome available, so the
  row is gone before it can be clicked rather than refused after. `DomainExchange.isFinished(fed,
  concept)` is the one definition of "finished" (`isAdapted || level > 0`) and both halves of the pool
  — the price list and the mob pairs — go through it, so the filter and the exchange's own re-check
  cannot disagree. Both kinds of "finished" live in the fed stack's `wheel_data`, which is why reading
  it off the fed stack covers bought and suffered alike.
  **The wheel slot is therefore not optional**: the purchase is written onto that stack and there is
  nothing to subtract from without it, so `recompute()` returns an empty pool and `gui.need_wheel` is
  the empty state.
  **Which makes a stale index dangerous, and `exchange()` resolves the clicked row by NAME**:
  `candidates.get(selectedIndex)` — what the client saw — then `pool.contains(concept)` against a
  freshly built pool, else `msg.trade_stale`. An index read out of the live pool instead would silently
  buy the row that shifted up, which is the only outcome here the player cannot undo.
- **The item price is server-computed and synced; the client cannot work it out.** The mob half's price
  is "which mobs drop this item", read out of the *server's* resources
  (`AltarOfferings.mobsFor(stack, server)`), so a client asking the same question answers zero —
  and `TradeMenu.itemCost()` used to be exactly that client-side question, returning 0, which made
  `canAfford` permanently false and left the EXCHANGE button grey and inert. It rides in
  `TradeSyncPayload` beside the pool and the selection.
- **A purchase is ONE level, and its price grows with the level.** `grantToWheel` is handed
  `heldLevel + 1`, not `MAX_LEVEL`: an adaptation that climbs to 8 is bought eight times. The price of
  each level is the first level's price multiplied by `ritual.tradeCostGrowth` (2.0) once per level
  already held, and capped by `ritual.tradeMaxItems` (32) and `ritual.tradeMaxXp` (30) — so a
  level-based adaptation costs 1‑2–4–8–16–32–32–32 items and 1‑2–4–8–16–30–30–30 experience from
  empty to max, while a one-time `Env_`/`Move_`/`Debuff_` still has no levels to grow. **`DomainExchange.grow`
  applies the cap inside the loop**, because a growth factor and a cap otherwise multiply unchecked.
  A base of zero stays zero: the recipe price doubles as "the altar takes this item", and growth must
  never turn *not for sale* into *very expensive*.
  **The prices are per row and synced, never recomputed on the client**: a level's price depends on
  how many levels the fed wheel holds, and the client has no fed wheel. `TradeSyncPayload` therefore
  carries one `(items, xp)` per candidate, plus a flag saying whether the altar takes the offering
  at all — because an empty list has two causes that want opposite answers ("wrong item" vs "your
  wheel already learned everything this item opens"), and only the server knows which.
- **The player pays twice: the item is consumed, and the altar charges their own experience
  levels.** `DomainExchange.priceFor` is the price of the FIRST level, by kind — **1** for a one-time
  adaptation, **2** for a leveled one, **3** for `Drop_NPC_`, **4** for
  `Existence_`/`Mutation_`/`Dimension_Destroy` — and every level after it costs more, per the rule
  above.
  **The floor of one level is load-bearing**: a price that could reach zero would make the altar a
  place to stand rather than a trade, which is what it stopped being. Whole levels, not fractions —
  vanilla experience is an integer and part of a level has nowhere to live. Charging is
  `giveExperienceLevels(-price)`, the same call vanilla uses for an enchanting table, so the client's
  bar updates through the ordinary path. Experience is deliberately **not** synced: the client
  already has it, and a second copy would be a second source of truth. `grantToWheel` grants
  `MAX_LEVEL` into the fed wheel — an exchange sells the adaptation whole, and the ladder says how big a
  thing that is.
  **The server owns every number**: candidates and the selected row live on the menu, and the client
  sends *intent* ("row 3", "exchange") — the row travels as an index and the pool is rebuilt
  whenever anything in the menu changes, so `exchange()` turns the index into a **name** and refuses
  rather than granting whatever the shrunken pool shifted into that slot.
  **No block entity**: the position travels as menu-open data, and that extra data is
  **mandatory** — with none, the client factory gets an empty buffer and reading a `BlockPos` off it
  throws when a player opens the block. **All layout constants live in `TradeMenu`, not the
  screen**, because a slot's position and the well drawn behind it are two numbers that must agree.
- **Vanilla draws no slot wells.** `extractSlot` renders the contents only; the wells are baked into
  a background texture, and 26.3 no longer blits one for a container screen. **A screen that draws
  its own panel must therefore draw every well itself**, or its items sit on bare grey — which is how
  the player inventory came out invisible: existing, clickable, and unseen. Loop `menu.slots`, not
  just your own slots. On 26.3 that loop belongs in **`extractLabels`**, the only hook that runs
  after the background and before the slots; it sits inside the `translate(leftPos, topPos)` block,
  so coordinates there are relative and the mouse needs offsetting by hand, and
  `super.extractLabels` is called **last** so the title lands on top of the panel rather than under
  it. On 1.21.1 the equivalent hook is `renderBg`, called from `renderBackground`, in absolute
  coordinates.
- Screen style is vanilla's own parts: slot wells from `generic_54.png`'s sampled pixels, and the
  wheel icon in the empty wheel slot is `textures/slot/empty_wheel_slot.png` — the same picture the
  player already sees in their Curios wheel slot, so it says "the wheel goes here" in the one place
  the mod has taught them to look. 26.3 reaches a 32×32 file in a 16×16 well through
  `blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1)` with normalised UVs; 1.21.1 needs the
  9-argument `blit` with the real texture size, because the 7-argument one hardcodes a 256×256 sheet.
  The experience bar is a **readout, not an input**: the price is a property of the adaptation, not a
  choice, so a draggable control over it would be a control that lies.
- 26.3 deltas this cost, none of which the 1.21.1 version shares: the whole drawing pipeline is
  `extract*(GuiGraphicsExtractor, ...)` with `g.text`/`g.centeredText`, `blitSprite` takes the
  RenderPipeline first, input is event objects where **`mouseClicked`'s second argument is "double
  click" and not a button index**, `imageWidth`/`imageHeight` are `final` and go to `super`,
  `openMenu(provider, BlockPos)` is gone, `Player.getBlockReach()` is replaced by
  `isWithinBlockInteractionRange(pos, 4.0)`, and `AbstractContainerMenu` grew
  `addStandardInventorySlots` with the same layout the hand-rolled 1.21.1 helper produced.
- **The mouse buttons were renumbered, and nothing in the API shape says so.** 1.21.1 used GLFW's
  numbers (left 0, right 1, middle 2); here **`MouseButtonEvent.button()` is 1 for left, 2 for
  middle, 3 for right** — which is what vanilla's own `InputConstants.Type.MOUSE` table says
  (`key.mouse.left` = 1, `key.mouse.right` = 3) and what `AbstractContainerScreen` assumes when it
  maps a click back to a container button (`getContainerClickButton`: `case 1 -> 0`, `case 3 -> 1`;
  and its pick-all double-click test is `event.button() == 1`). A `mouseClicked` override ported from
  `main` asking "is this button 0" therefore **never fires on a left click**, silently, and the click
  falls through to `super`, which for a container screen returns `true` unconditionally — so the game
  looks like it ate the click. Both of this mod's screens were dead that way: the adaptation
  browser's tabs and the trading blocks' rows. Ask `client/MouseButtons.isLeft(event)`; never write
  the literal.
- **`ResourceKey` has identity equality on this branch — it overrides neither `equals` nor
  `hashCode`,** and the only accessor is `identifier()` (1.21.1 spells it `location()`). So
  `event.getTabKey() == SomeVanillaTab` works only because the registry hands back the very same
  canonical object, and a **hand-built `ResourceKey.create(...)` is never equal to the registry's
  key for the same tab** — which is why `AdaptionWheel`'s long-standing `COMBAT_TAB` branch had been
  dead code from the day it was written, and why the new blocks and spawn egg were invisible in
  every creative tab. Matching is now by identifier via `isTab(key, id)`, and the new content is
  also listed in the mod's own tab's `displayItems`, which is the one place guaranteed to run.
- **Datapack formats are stricter here than on 1.21.1 and none of them compile-check.** Each of
  these fails at world load with a message that names neither the field nor the mod:
  - `BlockBehaviour.Properties` and `Item.Properties` need `setId(...)`; it is read from inside the
    constructor (`effectiveDrops()` for a block, `effectiveDescriptionId()` for a `BlockItem` and a
    `SpawnEggItem`), so a missing id is a bare `Block id not set` / `Item id not set` and the mod
    does not load. A plain `new Item.Properties()` is still fine for an ordinary item.
  - Only advancement **roots** may carry a `background`. `display.icon` is an item stack, so an
    entity type is rejected as an unknown `minecraft:item` key.
  - Entity predicates are wrapped: `{"type": "minecraft:entity_properties", "entity": "this",
    "predicate": {"minecraft:entity_type": ...}}`. A bare id resolves in `minecraft:predicate`,
    where an entity type does not live.
  - Loot: `functions` → `modifier`, `item` → `name`, and each modifier is keyed by `type`.
  - Recipes: ingredients are a bare id or tag string; `{"item": ...}` is rejected.
  - `neoforge:add_spawns`: `spawners` must be an array, each entry's fields sit **inline** next to
    `weight` (no `data` wrapper, because `Weighted.codec(MapCodec)` is a RecordCodecBuilder), and
    the pack size is one `count` IntProvider. The javadoc above it still documents the 1.21.1 shape.
- **26.3 renderers are render-state based.** `MobRenderer<T, S extends LivingEntityRenderState, M>`,
  a `createRenderState()` to make the state, and `getTextureLocation(S)` — the renderer never sees
  the entity. `LivingEntityRenderer` fills the humanoid fields itself in `extractRenderState`, so a
  mob needs only its own state class (see `client/DiscipleRenderState.java`).
- **`Entity.hurt` is `final` on 26.3.** The override point is
  `LivingEntity.hurtServer(ServerLevel, DamageSource, float)`, and `isInvulnerableTo` takes the
  level as its first argument. A `hurt` override compiles nowhere and a port that guesses will not
  build, which is at least a loud failure.
- **`DeferredSpawnEggItem` is gone.** `SpawnEggItem` resolves the mob from the `ENTITY_DATA`
  component, written by `Item.Properties.spawnEgg(type)`, so the entity register must be attached
  to the mod bus **before** the spawn-egg register.
- **The criterion API lives in `net.minecraft.advancements.triggers`** (`CriterionTrigger`,
  `CriteriaTriggers`, `SimpleCriterionTrigger`) and `SimpleInstance.player()` returns
  `Optional<Holder<LootItemCondition>>` — the advancement predicates were folded into the
  loot-condition system.
- **`Commands.hasPermission(int)` is a `PermissionProviderCheck` factory, not a `Predicate`** — pass
  it as a method reference. `src -> Commands.hasPermission(...)` compiles to the wrong type.
- **`Level.isClientSide` is a private field with a public `isClientSide()` method** here, so a
  1.21.1 `isClientSide` *field* read does not compile and the same expression with `()` does.

## 26.3-specific notes (added by the Phase 15 port)

- **`ResourceLocation` is `net.minecraft.resources.Identifier` here.** Factory methods are unchanged.
  A global rename is the first thing any ported file needs.
- **Permission checks are `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`**, a
  `Predicate<CommandSourceStack>`; `CommandSourceStack.hasPermission(int)` no longer exists.
- **Screens render via `extractRenderState(GuiGraphicsExtractor, ...)`** and draw text with
  `g.text(...)`. There is no `render(GuiGraphics, ...)` and no `GuiGraphics.drawString`.
- **`Minecraft.setScreen` is `Minecraft.gui.setScreen`.**
- **Client-to-server payloads go through
  `net.neoforged.neoforge.client.network.ClientPacketDistributor`**, not `PacketDistributor`.
- **`ToolMaterial` replaced `Tiers`** with the same speed ladder, and `Tool`/`Tool.Rule`/
  `Item.getDestroySpeed` are unchanged, so `FistTiers` ports as-is. `PlayerEvent.HarvestCheck`,
  `PlayerEvent.BreakSpeed` and `BlockDropsEvent` also survived with the same accessors.
- **`AdaptionEvents` names differ from 1.21.1**: `sync` (not `syncAdaption`) and `completeTask`
  (not `grantConceptLevel`). Both are public for the fist.
- **The gametest framework was rewritten and the 38 tests from `main` are NOT ported.** No
  `@GameTest`/`@GameTestHolder` exists. Tests are now `GameTestInstance`s registered on the mod bus
  via `net.neoforged.neoforge.event.RegisterGameTestsEvent` — note the package: it is
  `neoforge.event`, **not** `neoforged.neoforge.gametest`, where only `GameTestHooks` and
  `BlockPosValueConverter` remain. Grepping the old package looks like the feature was deleted.
  See DEVELOPMENT_PLAN.md Phase 15.
- **No gametest/lint task exists on this branch**, unlike `main`. `./gradlew build` only compiles, so
  a green build is still not a green suite.
- **The ported schedule and payload assertions therefore run under plain JUnit**
  (`src/test/java`, `./gradlew test`), not as gametests. That is a deliberate departure from the
  gametest convention above, for one reason: with no task to run gametests on this branch, a
  gametest port could not be verified to execute at all, whereas the JUnit suite runs and reports
  8 passing tests. Revisit if a gametest run task is ever added.

### The lightmap is not a `LightTexture` any more

`Env_Darkness` is a lightmap lift, but **26.3 rewrote the whole lightmap path**: `LightTexture`
became `Lightmap` + `LightmapRenderStateExtractor` + `UiLightmap`, the map is a `GpuTexture` rather
than a `NativeImage`, and the arithmetic now lives in `assets/minecraft/shaders/core/lightmap.fsh`.
There are no CPU-side per-pixel colours to rewrite, so the 1.21.1 `@ModifyArg` on `setPixelRGBA`
has no equivalent here.

What 26.3 does have is `net.minecraft.client.renderer.state.LightmapRenderState` — a plain mutable
object with public `brightness`, `nightVisionEffectIntensity`, `darknessEffectScale` and tint
fields that the shader reads. `mixin/LightmapRenderStateExtractorMixin` therefore `@Inject`s into
`LightmapRenderStateExtractor.extract` and raises `nightVisionEffectIntensity` to
`DarknessLightmap.intensity()` (default floor 0.72). The shader then does exactly what vanilla
night vision does — scale toward `nightVisionColor`, which is white — so relative shading and the
day/night cycle survive, same intent as the 1.21.1 branch.

Two traps when touching that injection:

- **Anchor on the last field assigned, not on `needsUpdate`.** `needsUpdate` is assigned at the
  very top of `extract` (into the *state* object) and read on the *extractor*, so anchoring there
  runs before any value is computed and the vanilla assignment overwrites the lift on the same
  call. `bossOverlayWorldDarkening` is the final one written and sits inside the `needsUpdate`
  branch, which is what is wanted.
- **`Math.max` against the existing value, not `=`.** A real night vision potion must still be
  able to win; an unconditional assignment would stomp it.

The gamma approach is dead on this branch for the same reason as on 1.21.1: `Options.gamma()` is a
bounded `OptionInstance` and an out-of-range `set` reverts silently. See DEVELOPMENT_PLAN.md
Phase 15/16 on `main` for the full reasoning.
- **The client mirror must be wiped on disconnect here too.** `ClientAdaption` is an
  `@EventBusSubscriber` whose `ClientPlayerNetworkEvent.LoggingOut` handler calls `clear()`,
  listing every mutable field explicitly. Without it the static mirror outlives the world it
  described and a freshly created world opens showing the previous world's HUD.
- **`onPlayerLogin` syncs unconditionally**, not only while wearing the wheel. The 1 Hz sync is
  gated on `wearing`, so a world where the wheel is not worn would never send anything at all.
- **`Level.isClientSide` is a private final field on 26.3 with no accessor**, and
  `Entity.level()` returns `Level` — so the 1.21.1 guard `player.level().isClientSide` does not
  compile here. `instanceof ServerPlayer` already guarantees the server side; use that alone.

## Ported from `main`: the Existence cinematic

`client/ExistenceCinematicFX`, `cinematic/ExistenceCinematicTiming`,
`network/ExistenceCinematicPayload`, `textures/entity/gakon.png`, plus the send at the **end** of
`AdaptionEvents.grantExistenceAdaptation`. The schedule class is pure Java and copied verbatim;
everything else moved. What the move actually cost, beyond `Identifier`:

- **`MultiBufferSource` is gone**; `RenderLivingEvent.Post` hands over a
  `net.minecraft.client.renderer.SubmitNodeCollector` and there is no way to push raw vertices. The
  Gakon sprites are now a **baked `ModelPart`** submitted per sprite. Follow `WheelRenderer`: it
  already does exactly this on this branch.
- **`RenderLivingEvent.Post` has no `getEntity()` at all**, and render states are pure data with no
  back-reference. "Is this the local player" therefore *cannot* be answered at render time. It is
  answered where the entity still exists — an `AvatarRenderStateModifier` during state extraction —
  and carried across on a `ContextKey<Boolean>`. Do not try to recover the entity from the state.
- **The baked quad's depth is `0.001`, not `0`.** A cube's UVs are laid out per face from the tex
  offset and the depth, so at `texOffs(0,0)` with `64x66` the viewer-facing face covers the whole
  texture and the opposite face lands on `u 64..128`, which wraps onto the same pixels — both sides
  show the sprite. Depth exactly `0` collapses the four side faces and the renderer discards
  zero-area faces.
- `MeshDefinition.bakeRoot()` is `LayerDefinition.create(mesh, 64, 66).bakeRoot()`.
- `PoseStack` rotations are instance methods: `poseStack.rotate(Axis.ZP, radians)`, not
  `mulPose(Axis.ZP.rotation(r))`.
- `Minecraft.screen`/`setScreen(null)` is `mc.gui.screen()`/`mc.gui.setScreen(null)`.
  `Minecraft.setScreenAndShow` exists but forces a `renderFrame`, which is far too much from a
  payload handler.
- **`RenderGuiEvent.getPartialTick()` returns a `DeltaTracker`, not a float** — see
  `AdversityOverlay`. The world-space half reads `state.partialTick` off the render state instead.

## The custom-shader substrate does not exist here — read this before porting any shader

This is the single biggest difference from `main`, and it is not a rename. Verified against the
26.3 decompile and the NeoForge sources:

| `main` (1.21.1) | 26.3 |
|---|---|
| `net.minecraft.client.renderer.ShaderInstance` | **gone** |
| `RegisterShadersEvent` / `RenderSystem` `registerShader` | **gone** |
| `ShaderInstance.apply/setSampler/safeGetUniform` | **gone** |
| old `blaze3d` shader pipeline | `com.mojang.renderpearl` + `net.minecraft.client.renderer.ShaderManager` |

`grep -r registerShader` over the NeoForge 26.3 sources returns **nothing**, and `ShaderInstance`
is absent from the Minecraft jar. A mod that registers a core shader on `main` cannot be ported by
adjusting names — the whole registration mechanism is gone.

What replaced it, and what it will and will not do:

- Post effects are **JSON chains** at `assets/<ns>/post_effect/<name>.json`
  (`targets` + `passes`), loaded by `ShaderManager.getPostChain`, and run inside the frame graph via
  `PostChain.addToFrame`. Vanilla ships only `assets/minecraft/post_effect/{invert,creeper,blur,
  spider,entity_outline}.json`. The vertex shader is normally the stock
  `minecraft:core/screenquad`; the fragment shader is `#version 330` with
  `#extension GL_ARB_separate_shader_objects : require`, a `InSampler` sampler (not `Sampler0`), a
  `layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; }` block supplied for free, and
  its own uniforms in a `layout(std140)` block named to match the JSON's `uniforms` key.
- **Uniform values are frozen.** `PostPass`'s constructor builds each uniform group's GPU buffer
  once from the JSON; `customUniforms` is private with no setter, and the only per-frame buffer it
  rewrites is `SamplerInfo` (the two sizes). So a JSON chain can do a **static** full-screen effect
  and nothing more.
- **There is no way to animate them, including by reflection.** `GpuBuffer` exposes only
  `size/usage/isClosed/slice/map`; there is no write or upload method, and `map` needs
  `USAGE_MAP_WRITE`, which `PostPass` does not request (it creates the buffer with usage `128`,
  `USAGE_UNIFORM` only).
- `PostChain.process(RenderTarget, GraphicsResourceAllocator)` **is public but `@Deprecated`**;
  the live path is `addToFrame`, which needs the `FrameGraphBuilder` from world rendering.
- `com.mojang.renderpearl.backend.opengl.GlProgram` is public (`link`, `getProgramId`,
  `uniformCount`, `getUniform(int)`, `pushConstant()`), but its uniforms are addressed **by index,
  not by name**, and bypassing the frame graph that way would be a rewrite.

### The working route: `RenderGuiEvent.Pre` straight into `mainRenderTarget`

**Do not use `FrameGraphSetupEvent` for a full-screen pass. It cannot work, and the reason is not
obvious enough to guess.** That event hands you the `FrameGraphBuilder` (method spelled
`getFrameGrapBuilder()`, missing an "h") plus the `LevelTargetBundle`, and it fires *before* vanilla
adds its own passes — `LevelRenderer.render` adds "clear" at 249, "sky" at 377, "main" at 391, and
executes at 283. `FrameGraphBuilder.execute` then runs passes **in creation order** (only
`resolvePassOrder` pulls explicit `requires()`/reader deps earlier). So a pass added from that event
executes *first*, samples a `main` target that nothing has written yet, and reads as pure black.
You cannot express "run after vanilla" through handles either: pass handles carry only data
dependencies, and aliasing a handle resets them.

`RenderGuiEvent.Pre` has neither problem:

- `GameRenderer.render` calls `renderLevel()` at **497** and `guiRenderer.render()` at **511**, and
  `RenderGuiEvent.Pre` is posted from the NeoForge-patched `GuiLayerManager.render`. So by then the
  whole level frame graph has run and `main` holds the composited frame.
- `Minecraft` blits `gameRenderer.mainRenderTarget().getColorTextureView()` to the window surface
  after `render()`, so anything written to `main` beforehand is visible.
- Writing into `main` outside the frame graph is **not a hack**: vanilla does exactly this a few
  lines earlier, in `GameRenderer.render3dHud` (703-712, "Screen effects"; 719, debug crosshair):

  ```java
  try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
          .createRenderPass(() -> "Screen effects", mainRenderTarget.getColorTextureView(),
                            Optional.empty(), depthTextureView, OptionalDouble.empty())) {
      RenderSystem.bindDefaultUniforms(pass);
      pass.draw(3, 1, 0, 0);      // full-screen triangle, no vertex buffer
  }
  ```

  A full-screen triangle is `draw(3, 1, 0, 0)`; see the stock `core/screenquad.vsh`, which derives
  `texCoord` from `gl_VertexIndex`. `RenderTarget.blitAndBlendToTexture` (112-123) is the reference
  idiom for binding a sampler.

Immediate-mode GL is gone: `RenderSystem` no longer has `drawElements`, `setShader`,
`disableBlend`, `enableDepthTest` or anything else from 1.21.1's `ShaderInstance.apply()` toolkit.

**Uniforms: do not use push constants, and do not use a JSON chain.** Push constants are a trap
because the two backends disagree — `VulkanRenderPass` calls `vkCmdPushConstants` for real, while
`GlCommandEncoder` *emulates* them by binding a buffer as a UBO, and `GlProgram` only finds that UBO
if the block is named **exactly `_push_constants`**. No single GLSL declaration satisfies both. The
portable route is what vanilla itself does for its per-frame `SamplerInfo`: allocate your own
`MappableRingBuffer` with **`GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE`** (that is 130, and
it is why vanilla's buffers use 130 too), `map(false, true)` it, write your floats, and hand it to
`setUniform`. `MappableRingBuffer` keeps three buffers and fences on `rotate()`, so you never
overwrite bytes the GPU is still reading — and you can call `rotate()` between two passes in one
frame so they can carry **different** uniform values, which is how the impact frame gets its copy
pass and its panel pass out of one program.

Write std140 blocks by **absolute index** into the mapped `ByteBuffer`, not sequentially: the mapped
buffer's position is not guaranteed to be zero, and each field's byte offset has to match the shader
declaration exactly.

#### Reading a target you are drawing into — use a scratch `TextureTarget`

The two-tone threshold samples neighbouring pixels, and sampling the framebuffer you are drawing into
is undefined. So copy first: draw `main` into a `TextureTarget` with `strength = 0`, which the shader
early-outs to a single texture fetch, then draw that target back over `main` with the real strength.
`TextureTarget(label, width, height, colorFormat, depthFormat)` resizes in its constructor; pass
`null` for the depth format, since a flat overlay needs no depth attachment. Resize it when `main`
resizes and destroy the old buffers.

Because this route never aliases a handle, the whole class of crash documented for
`FramePass.readsAndWrites` (use of the stale handle, or `bundle.replace(MAIN_TARGET_ID, ...)`) simply
does not apply.

Consequence for the two effects `main` ships: the **Dimension Destroy impact frame** is ported this
way. The **flying slash shader** is not — a world-space translucent quad needs a vertex format and a
real projection matrix, which is a different problem from a full-screen pass. Everything else (the
payload trigger, the camera shake on `ViewportEvent.ComputeCameraAngles` with `setRoll`, the anime
flash via `graphics.fill`, the config) ports unchanged.

## Branch parity: run `python3 tools/branch_parity.py`

**Do not assume the branches are in sync.** The Phase 15/16 port was signed off as
"synchronised" while **seven** main-side fixes had never landed — see DEVELOPMENT_PLAN.md Phase 18
for the list. The worst of them was invisible: `progressElapsedTicks()` *had* been copied to 26.3,
but `taskProgress` still called the pre-fix `elapsedTicksSinceSync()`, so every grep for the helper
found it present.

`tools/branch_parity.py` walks every java file the two branches share and diffs the **method
names**, which is the cheap signal. Two deliberate shapes of result:

- **Expected differences, do not "fix" them**: API renames (`syncAdaption` -> `sync`,
  `grantConceptLevel` -> `completeTask`, `render` -> `extractRenderState`, `getTextureLocation` ->
  `SlashRenderState`/`submit`), 26.3-only files (`CuriosCompat`, `WheelSlots`, `SlashRenderState`,
  `SlimeBounceMixin`, `boundKeyName`), and code that is main-only *by design*
  (`WheelData.migrateLegacyConcepts` — 26.3 forked before the fist existed, so no 26.3 save can hold
  a `Fist_Copper` to migrate; `SurfaceAdaptations.fistLevel` and `AdaptionConfig.fistTierSpeed` —
  dead code on main, zero callers).
- **The trading block is one here and two on `main`, and that is a real gap, not a port artefact.**
  `branch_bodies.py` will report the missing `DomainStoneMenu`, `DomainStoneScreen`,
  `DomainStoneBlock`, `ModMenus.DOMAIN_STONE`, `ModBlocks.DOMAIN_STONE`, the old
  `candidates(tier, recipe)` signature, the `return player==null?0:itemPrice(...)` body in
  `itemCost()`, and the whole of `ritual.json`/`pickaxe.json`. All of it is deliberate here: the two
  blocks had identical mechanics, the user asked for one, and the price list moved into the altar.
  **`main` still has two blocks with two recipes and two advancements**, and its `TradeSyncPayload`
  has no `itemCost` — harmless there only because main's screen sends the exchange unconditionally
  instead of gating the click on `canAfford`, so just the button's *colour* is wrong. Port the merge
  when `main` is next touched; do not "restore parity" by splitting the block again.
  Two things about the pool are **not** divergences and must stay identical on both branches: the
  list subtracts what the fed wheel has finished, and `exchange()` resolves the clicked row by name.
  An earlier pass here removed the subtraction on the theory that a shrinking list was a defect; it
  is not, it is the list.
- **Real gaps**: a method on main with a 26.3 counterpart that was *supposed* to exist. Every one
  found this way was a behaviour, not a refactor.

### The three parity tools, and what each one cannot see

Run all three after touching anything that exists on both branches. Each found gaps the other
two structurally cannot:

- `tools/branch_parity.py` — diffs **method names** on shared files. Found the seven Phase 18 gaps.
- `tools/branch_audit.py` — **call sites** (a mod method with N references on main and 0 on 26.3),
  **config defaults** (same key, different value), **lang key→value** (not just key presence), and
  **block tag contents**. Found `darknessLightmapFloor` defaulting to 1.0 instead of 0.72.
- `tools/branch_bodies.py` — **statement-level** diff with comments and formatting stripped. This
  is the one that finds the nastiest class: a method present on *both* branches whose body is still
  the pre-fix version.

**What none of them can see, and what bit twice:**

- **A pre-fix body behind a present method.** `PlayerEvent.BreakSpeed` was subscribed on 26.3 and
  non-empty — it was just the version from *before* the fist existed, so it never called
  `FistMastery.breakSpeed`. Instabreak, and the fist's whole mining-speed multiplier, were dead and
  both a method diff and a call-site diff called the file identical. Only reading the body found it.
- **A copied-but-uncalled helper.** `progressElapsedTicks()` was present and unused while the caller
  still used the old helper.
- **An early return that omits a newly relevant clause.** The HUD bailed out when tasks, adversity
  and existence progress were all empty — which is exactly the state where the fist row is the only
  thing left to draw, so the bar never appeared. Adding a row means auditing the *conditions* that
  decide whether drawing happens at all, not just the drawing code.

So: a green build, a clean boot, and three green audits still do not mean the feature works on this
branch. It has no test task; runtime behaviour needs a playtest.
