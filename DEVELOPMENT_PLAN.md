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
- [ ] GameTest coverage for the analysis-task lifecycle

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

## Phase 15 — Porting main's 1.21.1 work to 26.3

`26.3` forked from `80bec4a` and had missed all eight commits since, so the Fist Mastery feature,
the grant-command rework, the enchanting-table particles and the gamma-based darkness adaptation were
ported across. The dedicated server boots clean and the mod loads with no adaptionwheel errors.

### API renames that had to be applied

| 1.21.1 | 26.3 | where it bit |
|---|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` | every id in the ported files; factory methods (`parse`, `withDefaultNamespace`, `fromNamespaceAndPath`) are unchanged |
| `CommandSourceStack.hasPermission(2)` | `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` | every `.requires(...)` in `AdaptionCommand` |
| `PacketDistributor.sendToServer` | `net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer` | the Instabreak stance payload |
| `Minecraft.setScreen` | `Minecraft.gui.setScreen` | closing the adaptation screen |
| `GLFW` keycodes | `InputConstants.KEY_*` SDL scancodes | the Instabreak keybind (already true of the screen keybind here) |
| `AdaptionEvents.syncAdaption` / `grantConceptLevel` | `sync` / `completeTask` | the fist's own grant and sync calls |

### The good news: the tool API survived

`Tiers` is gone, but `ToolMaterial` replaces it with the same ladder — `WOOD 2.0, STONE 4.0,
COPPER 5.0, IRON 6.0, DIAMOND 8.0, GOLD 12.0, NETHERITE 9.0` — and `Tool` is still
`record Tool(List<Tool.Rule> rules, float defaultMiningSpeed, int damagePerBlock, boolean
canDestroyBlocksInCreative)` with `Rule(HolderSet<Block>, Optional<Float> speed,
Optional<Boolean> correctForDrops)`. `Item.getDestroySpeed(ItemStack, BlockState)` is still there.
So `FistTiers`'s rule-speed reading ports essentially unchanged, which was the riskiest part of
the whole job.

**26.3 also gives copper a real tool band** (`BlockTags.INCORRECT_FOR_COPPER_TOOL`, speed 5.0),
which 1.21.1 did not — that is what a sixth tier would mean here. The ladder stays at five
(wood/stone/iron/diamond/netherite) so a player's progress means the same thing on both branches.

`PlayerEvent.HarvestCheck`, `PlayerEvent.BreakSpeed` and `BlockDropsEvent` all still exist with the
same accessors, so the fist's only hook and the luck multiplier needed no rework at all.

### Screens render through a different model

26.3 replaced `render(GuiGraphics, ...)` with `extractRenderState(GuiGraphicsExtractor, ...)` and
`GuiGraphicsExtractor.text(...)` for text. The vanilla-styled adaptation screen was ported to that;
its panel and bar drawing use `fill()` with sampled vanilla colours, so nothing depended on the
blit overloads whose parameter order could not be read reliably on 1.21.1.

### Known gap: the gametests did not come across

**The 38 gametests on `main` have no equivalent here and were deliberately not ported yet.** 26.3
replaced the annotation-driven framework outright: there is no `@GameTest`, no
`@GameTestHolder`, no `@PrefixGameTestTemplate`. Tests are now `GameTestInstance` objects
registered through the mod-bus `RegisterGameTestsEvent` with a `TestData` describing the structure,
and the runtime is built around `GameTestTicker`/`GlobalTestReporter`. That is a rewrite of the
harness, not a package rename, and porting product code first was the right order — the tests
verify the product, not the other way round.

Until that happens the 26.3 port has no automated coverage, so its behaviour is **verified only by
compilation and a clean server boot**. The data-driven parts the tests pinned on `main` — the six
material tags, the `fist_luck` tag, the cost table, the reach rule, the speed ladder — were ported
by copying, so they are identical to the tested versions rather than independently verified.

## Phase 16 — Env_Darkness on 26.3

Same fix as `main`, a different door. 26.3 split `LightTexture` into `Lightmap` +
`LightmapRenderStateExtractor` + `UiLightmap` and moved the lightmap arithmetic into
`shaders/core/lightmap.fsh`, so there are no CPU-side pixels to rewrite and the 1.21.1
`@ModifyArg(setPixelRGBA)` has no equivalent. `mixin/LightmapRenderStateExtractorMixin` instead
raises `LightmapRenderState.nightVisionEffectIntensity` to the configured floor, which makes the
shader do exactly what vanilla night vision does. Verified by compilation and a clean dedicated
server boot; like the rest of this branch there is no automated coverage, and client rendering
cannot be exercised from a headless server.
