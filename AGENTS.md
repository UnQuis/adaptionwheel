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
- `build.gradle`, `gradle.properties`, `settings.gradle` — NeoGradle userdev 7.1.39 build; NeoForge `26.3.0.43-beta`, Minecraft `26.3`, Java toolchain 25, Gradle wrapper `9.2.1` (no Parchment). **Curios is OPTIONAL** on this branch: there is no Curios build for 26.3, so it is not a compile dependency at all — `compat/CuriosCompat.java` binds the Curios API reflectively when the `curios` mod is present, and `compat/WheelSlots.java` decides where the wheel counts as "worn" (Curios `wheel` slot if loaded, else off-hand / any main-inventory slot). mods.toml declares `curios` with `type="optional"`. Mixins are wired via top-level `[[mixins]]` in `neoforge.mods.toml` → `adaptionwheel.mixins.json` (`compatibilityLevel: JAVA_25`); compile-time deps are `org.spongepowered:mixin` + `io.github.llamalad7:mixinextras-common` (`compileOnly`, both provided by NeoForge at runtime). **No refmap** — NeoForge runs Mojang mappings at runtime, so mixin targets use the same names as dev. See `docs/port-26.3.md` for the API migration notes of this branch.
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
- Repo root also holds Blockbench authoring sources for the 3D wheel: `dharma_chakra.bbmodel` / `dharma_chakra.json` / `dharma_chakra.png`. Editing them does **not** change the mod — the runtime model is Java code in `DharmaChakraModel`; re-export geometry there and copy the texture into `assets`.

## Commands

- Build: `./gradlew build --no-daemon` (first run downloads Minecraft + runs neoForm; long). Runs/tests: `./gradlew runClient`, `./gradlew runServer`, `./gradlew runData`. No lint/test framework configured.
- Headless server smoke test: enable RCON in `run/server/server.properties` (`enable-rcon=true`, `rcon.port=25575`, `rcon.password=...`), also set `pause-when-empty-seconds=0` (a paused server accepts the RCON connection and then silently drops every command), start `./gradlew runServer --no-daemon &`, poll the log for `Done (`, then drive it with a minimal RCON client — Gradle does NOT forward stdin to the server console, piped commands are lost. The RCON packet is `length, id, type, payload, 2 nulls`; **omit the length field and the server closes the socket right after your AUTH**, which looks exactly like a wrong password. A bare `execute if …` answers `Test passed` / `Test failed` in 26.3, and **there is no `/say` command any more**, so probe with a bare `execute if block <x> <y> <z> <block>`. To find out where a `/place structure` actually put its blocks, remember the piece is rotated at random (the altar lands 6 blocks from the chunk corner in one of four directions) — or read the region files: chunk `block_states.palette` is a plain list of block-id strings in 26.3.
- Decompiled Minecraft sources for mixin/API research: `build/neoForm/neoFormJoined*/steps/decompile/output.jar` (unzip selected classes; Mojang mappings = runtime names).
- Docs live in `DEVELOPMENT_PLAN.md` and `docs/` (adaptation-system, mod-api, version-compatibility); keep them current when changing the framework.
- `runData` writes generated resources to `src/generated/resources`; delete that folder after use.

## Gotchas

- `.tmod` files are **not** plain ZIPs: `TMOD.<version>` header + .NET length-prefixed version + hash + signature + 4-byte length + file table + raw-DEFLATE blobs (`zlib.decompress(blob, -15)`). Extract with `/tmp/opencode/tmod_extract.py`; decompile with `ilspycmd` (`.NET` tool) — the decompiled reference source lives at the repo root in `ADAPTIONWHEEL/` (incl. original `Assets/Sounds/*.wav`, textures, shaders, localization).
- `.rawimg` Terraria textures = XNB TiledImage: int32 tile count, then per tile (int32 w, int32 h, raw BGRA pixels). Convert with Python/PIL.
- Surface physics: ice/slime/cobweb are handled by **mixins** (`mixin/` package) so vanilla physics apply unchanged on both sides; lava swimming is tick-based velocity conversion in `ClientSurfaceHandler` (+ server mirror `AdaptionEvents.applySurfaceEffects`) because NeoForge 21.1 has no fluid-movement hook. Don't replace the mixin approach with post-tick velocity multipliers — that halved walk speed (the original bug).
- **Important:** `gradle.properties` is read by Gradle as ISO-8859-1 — keep `mod_description` and any `${...}`-expanded values in `neoforge.mods.toml` ASCII-only or the text will be mojibake'd.
- **NeoForge 21+ dependency format:** `[[dependencies.<modid>]]` uses the `type` field (`required|optional|incompatible|discouraged`); the legacy Forge `mandatory=true/false` key is silently IGNORED and every parsed dependency defaults to REQUIRED. The DE soft-compat entry once crashed every install without Draconic Evolution because of this.
- **This branch builds against NeoForge `26.3.0.43-beta`, and `ModConfig.Type.SERVER` no longer exists there.** The enum is `LOCAL / CLIENT / SYNCED / STARTUP`; `SERVER` was renamed to `SYNCED` (same meaning: a server config synced to clients). A renamed enum constant is a **binary** break, so a jar built against the older NeoForge dies with `NoSuchFieldError` inside `AdaptionWheel.<init>` before any mod code runs — the launcher never recompiles anything. `AdaptionWheel.configType(String...)` therefore looks every config type up by **name** over `ModConfig.Type.values()` (`"SYNCED", "SERVER"` for the server spec, `"CLIENT"` for the client one) — the compiled class contains **zero** `getstatic` references to `ModConfig$Type` constants (check with `javap -c`), which is the actual guarantee: the jar cannot die of this at all, on either loader.
- **Version ranges stay wide, on purpose.** Pinning `dependencies.neoforge` to exclude the old spelling does not fix a binary break — it only moves the failure from the dependency check into the constructor, and it makes the mod refuse to load on a perfectly good NeoForge. `neo_version_range` is `[26.3.0,)` and `mod_id`'s `minecraft_version_range` is `[26.3]`; a version mismatch should not be able to stop the game from starting, so binary breaks get handled in code.
- **In Maven version ordering a qualifier sorts *before* the release: `26.3.0.43-beta` < `26.3.0.43`.** So a lower bound of `[26.3.0.43,)` rejects the very build it was written for — the launcher refuses to load with `Mod ID: 'neoforge', Requested by: 'adaptionwheel', Expected range: '[26.3.0.43,)', Actual version: '26.3.0.43-beta'`. If a tight bound is ever really wanted, the spec has to carry the qualifier: `[26.3.0.43-beta,)`. Verified with `VersionRange`/`DefaultArtifactVersion` from `maven-artifact`, which is what FML uses.
- `gradle.properties` has one range, `neo_version_range`, and `neoforge.mods.toml` expands `${neo_version_range}`. There used to be a second property, `dependencies_neoforge_version_range`, that nothing referenced and that AGENTS.md told future sessions to "keep in sync" — deleted, because a duplicate source of truth for a version bound is a trap.
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
  `@GameTest`/`@GameTestHolder` exists; tests are `GameTestInstance`s registered via the mod-bus
  `RegisterGameTestsEvent`. See DEVELOPMENT_PLAN.md Phase 15. This branch therefore has no
  automated coverage — do not treat a green build as a green suite.
- **No gametest/lint task exists on this branch**, unlike `main`. `./gradlew build` only compiles.

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
