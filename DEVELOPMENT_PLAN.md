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
- [x] Mutation_Fist — the adaptation to breaking and its five material tiers; see Phase 15
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

### Fixes from the first playtest
- [x] **Tier deadlock.** `currentTier` was "highest tier with a level above zero", so maxing Wood
      announced the Stone fist while `Fist_Stone` was still 0 — and levelling it required stone
      blocks that would not drop. Reach is now derived from the *previous* tier being maxed, so
      stone starts dropping the moment Wood caps. Levels granted out of order via `/grant` still count.
- [x] **"Bare hand" was "empty hand".** Holding a block, food or any other non-tool item disabled
      the fist entirely. `FistTiers.usableWith` now treats "not a tool or weapon" as bare-handed:
      the `DataComponents.TOOL` component catches every implement (vanilla and modded), a positive
      main-hand attack-damage modifier catches weapons, and a mining speed above 1.0 on the targeted
      block is the catch-all.
- [x] **No guidance.** The unlock and every tier-up now name the material that trains the tier and
      how many blocks the next level costs.

### Second playtest: tool parity
- [x] **Fist speed now equals the equivalent pickaxe** (wooden 2x, stone 4x, iron 6x, diamond 8x,
      netherite 9x in 1.21.1) instead of a flat +1..+6 bonus, and the value is **read from
      `DataComponents.TOOL` of the vanilla pickaxe at runtime** rather than hardcoded — nothing to
      keep in sync, and a retuned tool speed moves the fist with it. A missing tool makes the tier
      inherit the speed below it so the ladder never develops a hole.
- [x] The fist speed is applied as an **absolute** base before Mine_Labor's multiplier, so the two
      compose instead of the fist being added on top of the bare hand's 1.0.
- [x] **Harvest bands rebuilt on vanilla's own tool tiers** via the `needs_*_tool` block tags, so
      Stone = everything a stone pickaxe mines, Iron = `needs_iron_tool`, Diamond =
      `needs_diamond_tool`. Vanilla leaves a few blocks untiered (any pickaxe works: coal ore,
      redstone blocks, most metal blocks) and those follow vanilla rather than intuition.
- [~] **Superseded by the fourth playtest:** copper is gone entirely rather than being the Nether
      band. Kept for the record — the reasoning about the missing tool band was right, the fix was
      the wrong shape. Netherite is what no pickaxe can harvest; its level 8 remains Instabreak.
- [x] 14 gametests, including one that asserts each tier's speed equals its vanilla tool's and
      that the speed ladder never decreases.

### Third playtest: the fist still mined at bare-hand speed
Three separate bugs stacked, none of them visible server-side:
- [x] **The client never granted the harvest check.** `FistMastery.onHarvestCheck` bailed on
      `!isClientSide`, but that check also picks the `/30` vs `/100` destroy-speed divisor, and
      `MultiPlayerGameMode.continueDestroyBlock` accumulates break progress *client-side* and
      destroys the block itself. The server thought stone took 12 ticks; the player spent ~38.
      Now answered on both sides through `SurfaceAdaptations`.
- [x] **Client and server disagreed on the reachable tier.** `FistMastery.currentTier` had been
      changed to "the previous tier being maxed opens this one" but `ClientAdaption.fistTier` was
      a separate copy still using "highest tier with a level". After maxing Wood the client kept
      mining at wooden-fist speed for a whole tier. The rule now lives in one place,
      `FistTiers.reachTier`, called by both.
- [x] **`DataComponents.TOOL.defaultMiningSpeed` is 1.0 for every vanilla tool.** A tool's real
      speed lives in the matching `Tool.Rule`, resolved by `Tool.getMiningSpeed(state)`; the
      component field is only the fallback for blocks no rule covers. Reading it collapsed the
      whole ladder to bare-hand speed — and the "speed equals tool" test *passed*, because it
      compared against the same wrong field. Now measured with `Item.getDestroySpeed` on the block
      in front of the player, and the ladder is pinned to 2/4/4/6/8/9 by an explicit test
      (now 2/4/6/8/9 — the fourth playtest removed the copper rung).
- [x] **The speed is applied as a multiplier, not an absolute value.** Vanilla builds a bare hand's
      speed as `1.0 × BLOCK_BREAK_SPEED × haste ÷ 5 airborne × submerged`, all of which is already
      in `newSpeed` when the event fires. Multiplying swaps the 1.0 and keeps every modifier;
      overwriting with an absolute value discarded haste and dropped the airborne penalty.
- [x] 20 gametests, two of which assert the player-facing **break duration** in ticks rather than
      the speed number — a stone fist must break stone in the same 12 ticks as a stone pickaxe, and
      a netherite fist must break obsidian as fast as a netherite pickaxe. Comparing speeds alone
      cannot catch a client/server disagreement; comparing durations can.

### Fourth playtest: the ladder was too long, and the HUD was late
- [x] **The copper tier is gone, folded into Iron.** The player was right that Iron was the wall:
      eight levels at 3.0x the base cost, needing two ore types that sit in two different
      biomes/altitudes. Vanilla's own ladder is six materials but copper owns *no* tool band — a
      copper pickaxe is a stone-band tool, and `copper_pickaxe` is absent from this registry
      entirely — so a literal six-rung ladder has a rung that grants nothing and costs a whole
      eight-level climb. Iron now trains on the **union** of the old copper band (the Nether
      material band) and `needs_iron_tool`, and the tier-cost table went from six entries to five:
      `1.0 / 1.5 / 2.5 / 4.0 / 5.0` (Iron dropped from 3.0 to 2.5, since it now absorbs two
      bands' worth of blocks). Five tiers, each one an honest climb.
- [x] **Existing wheels keep their progress.** `WheelData.migrateLegacyConcepts` folds a carried
      `Fist_Copper` level into `Fist_Iron` on load, clamped to the cap so a wheel deep into both
      tiers cannot land past the top and skip a tier-up. A no-op on any current save.
- [x] **The HUD bar no longer lags by up to a second.** The fist's block counter only reached the
      client on the 1 Hz full sync, so a break sat invisible for 100-700 ms depending on where in
      the tick the sync landed. `network/FistProgressPayload` pushes the two ints (four bytes) the
      moment a block is actually counted, so the bar moves with the break. The 1 Hz copy stays as
      the recovery path for unequip/equip and for a dropped packet.
- [x] **Progress bars freeze during Adversity.** The server already stops decrementing task timers,
      but the client extrapolated them between syncs, so the bars crept forward and then snapped
      back on the next packet. `ClientAdaption.taskProgress` now uses `progressElapsedTicks()`,
      which is the same zero-during-adversity clock `existenceProgress` already used. The
      adversity *overlay's* own countdown deliberately keeps running — that is the challenge.
- [x] **The fist is an analysis, so Adversity freezes it too.** `FistMastery.onHandBreak` now
      bails while `adversityActive`, matching every other analysis start.
- [x] 22 gametests, including the copper→iron migration (merge, clamp, and no-op on current
      saves) and an assertion that the copper blocks all landed in the iron tag.

### Fifth playtest: luck, the deep family, and the tool-class prototypes
Three requests, and the middle one turned out to rest on a wrong premise that was mine, not the
player's.

- [x] **Luck, 1/2/3/5/10x by tier, on ore drops and ore XP.** `server/FistLuck.java` hooks
      `BlockDropsEvent`, reached by `Block.playerDestroy -> Block.dropResources ->
      CommonHooks.handleBlockDrops`. That event sits after the loot table has produced its list
      but before anything enters the world, and it exposes the items *and* the XP on one object,
      so fortune and silk touch are already baked in and the two cannot drift apart. Stacks are
      merged up to their stack limit instead of spawning N item entities. Scope is the
      `adaptionwheel:fist_luck` block tag, stated on the HUD row so the multiplier is not invisible.
- [x] **The speed prototype is now chosen per block across pickaxe/axe/shovel/hoe.** The report
      was "my diamond fist should break dirt instantly" — the arithmetic was right and the code
      was wrong. The speed was measured with a *pickaxe* against whatever block was in front of
      the player, and 1.21.1 puts dirt in `mineable/shovel` only, so `Item.getDestroySpeed` answers
      1.0 there: bare-hand speed. A diamond fist took 15 ticks on soil where a diamond shovel
      takes 2. The fix reads each tool's declared `Tool.Rule` speed, which is block-independent,
      and asks only which class claims the block. A side effect worth knowing: all four classes of
      a tier declare the *same* speed in vanilla (2/4/6/8/9), so the per-class probe is belt and
      braces rather than a correction — but it makes the property true by construction instead of
      by coincidence.
- [x] **The diamond tier was ungrindable and now trains on the whole deepslate family.** Its list
      was obsidian, crying obsidian, ancient debris, netherite blocks, respawn anchors, lodestone
      and diamond ore — ancient debris barely spawns, and a respawn anchor is a one-time item.
      Added deepslate and every cut variant, infested deepslate, tuff, calcite and dripstone, plus
      `#minecraft:emerald_ores`. This required changing the levelling test from
      `tierOf(state) == tier` to `state.is(FistTiers.tag(tier))`: the deepslate family is
      stone-band, so `tierOf` reports all of it as Stone and a diamond fist could never be trained
      on any of it. A material may now be reachable from the lower tier and trainable at the
      higher one, which is what makes an under-stocked tier fixable at all.
- [x] **Data bug found by the tests:** `fist_iron.json` carried a `{value: value}` mapping at the
      top level next to its real `values` list, left behind by the earlier tag merge. Harmless to
      the tag loader, but it was junk in a shipped resource.
- [x] **There is no `minecraft:ores` block tag in 1.21.1.** The luck tag was written against it
      and silently resolved to nothing — the gametest asserting coal ore is covered is what
      caught it. The tag now lists the eight vanilla `*_ores` tags explicitly; `nether_gold_ore`
      is inside `gold_ores`, but `nether_quartz_ore` has no tag of its own and is named outright.
- [x] 31 gametests, including a matrix pinning that all four tool classes yield the tier's speed
      on their own blocks, and the player's own arithmetic as a duration: a diamond fist breaks
      dirt in at most 2 ticks against 15 for a bare hand.

### Sixth playtest: particles, granting, and instant effects
- [x] **Wheel particles are enchanting-table glyphs** (`ParticleTypes.ENCHANT`), idle and converging
      alike, replacing END_ROD/CRIT. The runic sprite reads as arcane; an end rod read as a generic
      white streak that could have come off anything.
- [x] **The adaptation granting system was rebuilt, and the blocker was worse than a syntax error.**
      The concept argument was `StringArgumentType.word()`, and word() does not *reject* a colon —
      it stops reading at one. So `Existence_draconicevolution:draconic_guardian` was parsed as
      `Existence_draconicevolution`, `Contact_minecraft:zombie` as `Contact_minecraft`, the command
      cheerfully reported a successful grant, and nothing happened. No error, no symptom. That is
      precisely "you simply cannot grant the adaptation I want", and it silently killed every
      concept built from a namespaced id: the entire `Contact_<mob>` / `Offense_NPC_<mob>` /
      `Drop_NPC_<mob>` families and every modded boss, the Chaos Guardian included.
      `server/ConceptArgument.java` now reads word()'s charset plus `:` and `/`, stopping at
      whitespace so a trailing `<target>` still parses. `ConceptArgumentTests` pins the whole
      registry through the real parser and asserts, against word() itself, why it cannot be used.
- [x] `grant` resolves the level per concept kind instead of demanding a number that means nothing
      for a one-time adaptation (and now says so rather than discarding it silently); the level's
      upper bound tracks `PlayerAdaption.MAX_LEVEL` rather than a literal 8.
- [x] `grant all [domain] [target]` — dumps a whole domain, the only practical way to set up a
      late-game state for testing.
- [x] `ungrant <concept> [target]` — removes one adaptation. Previously the only undo was a full
      `reset`, which throws away everything else; iterating on the fist needed all-or-nothing. It
      also clears the fist's runtime stance/counter when the mutation is what was removed.
- [x] An unknown concept now answers with near-matches by substring. With a registry this size a
      flat "unknown concept" is a dead end.
- [x] **Effects apply on the tick they are earned.** `Env_Darkness` night vision was refreshed on
      `tickCount % 40 == 0`, so standing in a cave after adapting to the dark, nothing happened for
      up to two seconds. `applyEnvEffects` is now called directly from `grantConceptLevel`,
      `debugGrant`, `debugUngrant` and `debugReset`, so a completion takes hold immediately.
- [x] **Night vision is maintained as a mirror of the adaptation, following ProjectE's gem helmet**
      (`moze_intel.projecte.handlers.InternalAbilities.tick`: a central tick owns the effect, tops
      it up on a rolling window, and removes it when the source is gone). Two details kept over a
      straight copy: the effect is `ambient`, so no potion icon appears and it reads as part of the
      wheel; and it is removed *only when the live instance is ambient*, which is exactly the shape
      this code writes, so losing the adaptation never deletes a real night vision potion from a
      brewing stand. ProjectE removes unconditionally and does delete it. The window is 200 ticks
      refreshed below 100 — unconditional re-adding re-sends an effect packet to every client
      every tick, because a differing duration is not equal to the live one.

### Immediate regression, caught in play: custom command argument types block login
The fix for the silent truncation above (a custom `ArgumentType` accepting `:`) **made the game
unplayable**, and it is worth recording because the failure mode is not obvious:

    java.lang.IllegalArgumentException: Unrecognized argument type
        ConceptArgument$ConceptType@... (class ConceptArgument$ConceptType)
        at ArgumentTypeInfos.byClass(ArgumentTypeInfos.java:174)
        at ClientboundCommandsPacket$ArgumentNodeStub.<init>(...:221)
    Dev lost connection: Invalid player data

The command tree is mirrored to the client, and the client rebuilds each node from a serializer
looked up **by the argument type's class** in `ArgumentTypeInfos.BY_CLASS` — a private static map
filled only from a hardcoded `bootstrap` list, registered into a registry that `byClass` never
reads back. NeoForge 21.1 exposes no hook. So an unregistered argument type is simply
**impossible** to use here, and it fails at player login, not at command execution: a far worse
outcome than the mangled argument it was meant to fix. `Commands.validate()` does the same
`findUsedArgumentTypes` + `isClassRecognized` check and throws `Unregistered argument types`.

- [x] Reverted to `StringArgumentType.string()`, which carries a colon when quoted. Vanilla's own
      way of passing a value that needs one.
- [x] `truncationHint` detects the fingerprint of the old silent truncation — a key ending in a known
      mod namespace, i.e. a string that was cut at its colon — and answers with the quoted form
      instead of granting a dead key and reporting success.
- [x] `test/CommandTreeSyncTests` builds the real registered tree and asserts every argument type in
      it is client-serializable, using the same predicate the packet builder throws on. **Verified
      the test actually fails** by temporarily reinstating a custom type, then reverted.
- [x] Verified end to end: a real dev client joins a dedicated dev server, no
      `Invalid player data`, zero errors, player stays in the world.

  A dedicated-server boot and a unit test both miss this class of bug, because both are
  server-side. Any future command-argument change needs a client in the loop, or the
  serializability assertion.

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
