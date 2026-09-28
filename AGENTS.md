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
- `build.gradle`, `gradle.properties`, `settings.gradle` — NeoGradle userdev 7.1.39 build; NeoForge `26.3.0.4-beta`, Minecraft `26.3`, Java toolchain 25, Gradle wrapper `9.2.1` (no Parchment). **Curios is OPTIONAL** on this branch: there is no Curios build for 26.3, so it is not a compile dependency at all — `compat/CuriosCompat.java` binds the Curios API reflectively when the `curios` mod is present, and `compat/WheelSlots.java` decides where the wheel counts as "worn" (Curios `wheel` slot if loaded, else off-hand / any main-inventory slot). mods.toml declares `curios` with `type="optional"`. Mixins are wired via top-level `[[mixins]]` in `neoforge.mods.toml` → `adaptionwheel.mixins.json` (`compatibilityLevel: JAVA_25`); compile-time deps are `org.spongepowered:mixin` + `io.github.llamalad7:mixinextras-common` (`compileOnly`, both provided by NeoForge at runtime). **No refmap** — NeoForge runs Mojang mappings at runtime, so mixin targets use the same names as dev. See `docs/port-26.3.md` for the API migration notes of this branch.
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
  - `server/AdaptionCommand.java` — `/adaptionwheel status|list|info|grant|analyze|reset|registry` (reads self-serve, mutations require perm 2); surgical grants go through `AdaptionEvents.debugGrant/debugReset` (no heal/voice/events).
  - `client/AdaptationScreen.java` — in-game adaptation browser (domain tabs incl. ALL, levels/ADAPTED/MAX states, live task progress bars, optional `adaptionwheel.desc.*` descriptions, scrollbar/scissor clipping, no pause); opened by `client/AdaptionKeybinds.java` (default K, MOD bus registration) via `client/AdaptionScreenOpener.java` (game-bus ClientTickEvent.Post polling `consumeClick()`); wheel tooltip shows the bound key behind an `FMLEnvironment.dist.isClient()` guard.
  - **Chaos Guardian compat (soft, string-ID based — no compile/runtime dependency on DE)**: `server/DraconicCompat.java` holds every DE identifier (`draconicevolution:draconic_guardian`, guardian wither/crystal, damage ids `guardian`, `guardian_laser`, `guardian_projectile`, `chaos_implosion`, `crystal_move`) and resolves guardian-linked sources (parts → body, projectile owner, withers/crystals inherit) to the canonical existence path. `server/GuardianDirectDamage.java` + `mixin/GuardianLaserMixin.java` handle the fully charged laser, which applies damage via a direct `setHealth` call in `LaserBeamPhase.serverTick`, bypassing ALL NeoForge damage events; the mixin is a `@Redirect` on that single callsite (handler receiver must be exactly `Player` — Mixin rejects supertypes at apply time), routed through `AdaptionEvents.handleDirectHealthReduction` (existence immunity / EXPLOSION+contact reductions / Lv5 heal / Adversity survival). Applied conditionally via `mixin/AdaptionMixinPlugin.java` (checks `FMLLoader.getLoadingModList()` for `draconicevolution`). Once `Existence_ChaosGuardian` completes, the wearer is immune to the whole arsenal and contact hits are reflected at `existenceReflection.reflectMultiplier` (default ×3, like the original) with knockback; grant also maxes Type_EXPLOSION + Type_PROJECTILE + Type_FIRE. Config: `modules.chaosGuardian`, `timing.existenceProximityBlocks`. Verified live: mixin applies cleanly when summoning the guardian on a dev server with DE installed.
  - `client/ClientAdaption.java` (client mirror), `client/AdaptionHud.java` (HUD panel with per-concept colored task bars + 2D wheel), `client/AdversityOverlay.java` (adversity HUD ported from the original: red vignette + cinematic bars + "ADAPTING TO ADVERSITY" title + [SS:CS] countdown + symmetric shrinking bar, cooldown note, totem-style wheel flying at the face on trigger then gray heartbeat wheel; **depends on live server sync — the adversity tick branch must keep syncing**, it early-returns otherwise), `client/WheelRenderer.java` (3D wheel above head via `RenderLivingEvent.Post`: fixed in **world space** — cancels body yaw, spins and bobs only; renders for any player wearing the wheel), `client/DharmaChakraModel.java` (wheel geometry **hardcoded in Java**, converted from the Bedrock Blockbench model; texture `textures/entity/dharma_chakra.png`), `client/ClientSurfaceHandler.java` (lava swim = water swim via tick-based dynamics conversion).
  - `mixin/FrictionMixin.java` (`LivingEntity.travel`: clamps ice/slime friction to 0.6 for adapted players → vanilla ground physics apply unchanged), `mixin/SlimeBlockMixin.java` (no stepOn slowdown / no bounce), `mixin/WebBlockMixin.java` (cobweb sticks at half vanilla strength), `mixin/SpeedFactorMixin.java`, `mixin/PowderSnowBlockMixin.java`, `mixin/BerryBushMixin.java`, `mixin/BubbleColumnMixin.java` (see Adaptation to Discomfort above), `mixin/GuardianLaserMixin.java` + `mixin/AdaptionMixinPlugin.java` (see Chaos Guardian compat above; the plugin is wired via `"plugin"` in `adaptionwheel.mixins.json` and skips the laser mixin when DE is absent). Shared checks live in `SurfaceAdaptations` (+ `ClientChecks`, lazily loaded client-only class). Env_Liquid also removes the underwater mining penalty via a permanent `SUBMERGED_MINING_SPEED` modifier (Aqua-Affinity mechanism) + a BreakSpeed ×5 compensation while swimming.
- `src/main/resources/` — `META-INF/neoforge.mods.toml` (incl. top-level `[[mixins]]`), `adaptionwheel.mixins.json`, `pack.mcmeta`, `assets/adaptionwheel/` (lang `en_us.json`/`ru_ru.json`, textures `textures/entity/wheel.png` + `textures/entity/dharma_chakra.png` + `textures/item/*.png` + `textures/slot/empty_wheel_slot.png` + `icon.png` — wheel textures/icon converted from the original Terraria `.rawimg`, `sounds/*.ogg` — converted from the original `.wav` files). **Crafting chain**: `data/adaptionwheel/recipe/mahoraga_wheel_wood.json` (shaped: any log center + 8 sticks → `mahoraga_wheel_wood`, a plain stackable item) then an anvil upgrade in `AdaptionEvents.onAnvilUpdate` (`AnvilUpdateEvent`: wooden wheel + gold ingot, either slot order, 1 ingot, 10 XP levels → `mahoraga_wheel`). The old gold+clock shaped recipe was removed. Item textures `mahoraga_wheel*.png` are author-provided 32×32 sprites (not Terraria conversions).
- **Curios wheel slot**: dedicated slot `wheel` that only accepts the Mahoraga Wheel — `data/adaptionwheel/curios/slots/wheel.json` (validator `curios:tag`), item tag `data/curios/tags/item/wheel.json`, player assignment `data/adaptionwheel/curios/entities/player.json`. **Entity-file format is flat**: `{"entities": ["minecraft:player"], "slots": ["wheel"]}` — a nested `"minecraft:player": {...}` object parses fine but silently assigns nothing (no log error). The wheel itself only equips in this slot (`MahoragaWheelItem.canEquip`).
- Repo root also holds Blockbench authoring sources for the 3D wheel: `dharma_chakra.bbmodel` / `dharma_chakra.json` / `dharma_chakra.png`. Editing them does **not** change the mod — the runtime model is Java code in `DharmaChakraModel`; re-export geometry there and copy the texture into `assets`.

## Commands

- Build: `./gradlew build --no-daemon` (first run downloads Minecraft + runs neoForm; long). Runs/tests: `./gradlew runClient`, `./gradlew runServer`, `./gradlew runData`. No lint/test framework configured.
- Headless server smoke test: enable RCON in `runs/server/server.properties` (`enable-rcon=true`, `rcon.port=25575`, `rcon.password=...`), start `./gradlew runServer --no-daemon &`, poll the log for `Done (`, then drive it with a minimal RCON client — Gradle does NOT forward stdin to the server console, piped commands are lost.
- Decompiled Minecraft sources for mixin/API research: `build/neoForm/neoFormJoined*/steps/decompile/output.jar` (unzip selected classes; Mojang mappings = runtime names).
- Docs live in `DEVELOPMENT_PLAN.md` and `docs/` (adaptation-system, mod-api, version-compatibility); keep them current when changing the framework.
- `runData` writes generated resources to `src/generated/resources`; delete that folder after use.

## Gotchas

- `.tmod` files are **not** plain ZIPs: `TMOD.<version>` header + .NET length-prefixed version + hash + signature + 4-byte length + file table + raw-DEFLATE blobs (`zlib.decompress(blob, -15)`). Extract with `/tmp/opencode/tmod_extract.py`; decompile with `ilspycmd` (`.NET` tool) — the decompiled reference source lives at the repo root in `ADAPTIONWHEEL/` (incl. original `Assets/Sounds/*.wav`, textures, shaders, localization).
- `.rawimg` Terraria textures = XNB TiledImage: int32 tile count, then per tile (int32 w, int32 h, raw BGRA pixels). Convert with Python/PIL.
- Surface physics: ice/slime/cobweb are handled by **mixins** (`mixin/` package) so vanilla physics apply unchanged on both sides; lava swimming is tick-based velocity conversion in `ClientSurfaceHandler` (+ server mirror `AdaptionEvents.applySurfaceEffects`) because NeoForge 21.1 has no fluid-movement hook. Don't replace the mixin approach with post-tick velocity multipliers — that halved walk speed (the original bug).
- **Important:** `gradle.properties` is read by Gradle as ISO-8859-1 — keep `mod_description` and any `${...}`-expanded values in `neoforge.mods.toml` ASCII-only or the text will be mojibake'd.
- **NeoForge 21+ dependency format:** `[[dependencies.<modid>]]` uses the `type` field (`required|optional|incompatible|discouraged`); the legacy Forge `mandatory=true/false` key is silently IGNORED and every parsed dependency defaults to REQUIRED. The DE soft-compat entry once crashed every install without Draconic Evolution because of this.
- NeoForge 21.1 API quirks hit during development: `LivingDamageEvent.Pre` (no `LivingIncomingDamageEvent`/`AttackEvent`); `DamageSource.type()` returns `DamageType` (use `typeHolder().getRegisteredName()` for the id); `AttributeModifier(ResourceLocation, double, Operation)` with `Operation.ADD_VALUE/ADD_MULTIPLIED_BASE`; `AttributeInstance.removeModifier(ResourceLocation)`; `MobEffectEvent.Applicable.Result.DO_NOT_APPLY`; `PlayerTickEvent` lives in `net.neoforged.neoforge.event.tick`; `StreamCodec.composite` supports at most 6 components (use `StreamCodec.of` for more); `Mob` has no `isBoss()` — boss detection lives in `BossHelper` (hardcoded Wither/EnderDragon/Warden + Chaos Guardian + boss tag); loot params in 1.21.1 use `ATTACKING_ENTITY`/`DIRECT_ATTACKING_ENTITY`/`LAST_DAMAGE_PLAYER` (renamed from `KILLER_ENTITY`/`DIRECT_KILLER_ENTITY`/`LOOTING_ENTITY`); `LootParams.Builder` takes a `ServerLevel`; `FoodData` has no `getMaxFoodLevel()` — max food is the constant 20.
- Mixin quirks hit with the DE laser mixin: an `@Redirect` handler parameter must match the **exact** receiver type in the target bytecode (`Player`, not `LivingEntity` — supertypes are rejected at apply time with `InvalidInjectionException`); `@At(target = "setHealth(F)V")` without an owner matches any receiver, which keeps the injection resilient; optional mixins into another mod's classes must be gated through an `IMixinConfigPlugin` (`shouldApplyMixin` → `FMLLoader.getLoadingModList().getModFileById(...)`) or Mixin logs errors for the missing target class.
- Draconic Evolution facts (from the jar at the repo root): the Chaos Guardian (`draconicevolution:draconic_guardian`) is NOT in any boss tag — recognition is hardcoded via string id; its parts are NeoForge `PartEntity`s, so generic part unwrapping covers them; ALL guardian damage types bypass armor/resistance/effects/shields/cooldowns and most are tagged `is_explosion`/`is_projectile` (so vanilla-tag categories already partially apply); the charged twin laser writes health directly (handled by mixin). DE needs CodeChickenLib + BrandonsCore at runtime for dev-server testing (fetchable from Modrinth).
- Not a git repo; no commits or branches to manage here.
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
