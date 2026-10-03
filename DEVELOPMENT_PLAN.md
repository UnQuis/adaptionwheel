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

### Seventh playtest: GUI reskin, gamma instead of night vision, branch sync
- [x] **The adaptation screen is drawn in vanilla's GUI style.** It was entirely `g.fill()`
      rectangles with gold-on-black text, which read as a debug overlay. Now a container panel using
      the exact pixel values read out of `textures/gui/container/generic_54.png` (1 px `#000000`
      outline, 2 px `#FFFFFF` highlight, flat `#C6C6C6` body), domain tabs raised 2 px when
      selected the way vanilla's own tabs are, dark `#404040` text on the light panel, and progress
      bars in vanilla's XP-bar colours. Concept colours are scaled to 52 % for the light panel so the
      per-domain grouping still reads; the HUD's own colours are untouched.
- [x] **The bars and scrollbar are drawn with `fill()`, deliberately.** The first attempt blitted
      `experience_bar_*` through `GuiGraphics.blitSprite` and produced a bar split in half with a gap
      plus a magenta tint. `blitSprite`'s overloads cannot be read reliably from decompiled sources
      — the parameter names are meaningless and two overloads differ only in arity — so a wrong guess
      compiled, ran, and looked plausible enough to be worth two screenshot rounds. Sampled colours
      are deterministic. Only `blit(rl, x, y, u, v, w, h)` (7-arg, 1:1) is confirmed from a vanilla
      callsite (`BeaconScreen.renderBg`); that is the one plain-texture form worth trusting.
- [x] **Real bug the screenshot exposed: the list was built once in `init()`.** The client mirror
      arrives on the 1 Hz sync, so a screen opened in the first second after joining rendered an empty
      list under a header reading "Adaptations: 51". Now rebuilt per frame when the concept set
      changes (`refreshIfStale`). The eager build was pre-existing, not something the reskin caused.
- [x] **`Env_Darkness` is a gamma lift, not the night vision effect** (`client/DarknessGamma.java`).
      A status effect has a duration and must be refreshed as it runs down, so the screen blinked out
      and back once per window — the report. Gamma has no duration, no packets, and
      `LightTexture.tick()` rebuilds the light map every client tick, so it simply cannot flicker and
      lands on the next frame. The player's own Brightness is remembered and restored on loss and on
      `ClientPlayerNetworkEvent.LoggingOut`; NeoForge 21.1 has no client-stopping event, and leaving a
      world always goes through a disconnect, so that is what keeps `options.txt` clean. Config
      `visual.darkness.darknessGammaEnabled` / `darknessGamma`.
- [x] **`grant <concept> <player>` existed only as `<concept> <level> <player>`.** Naming another
      player meant inventing a level, and omitting it produced "Expected integer" pointing at the
      player's name. The target branch is a **sibling** of the `level` node; nested under `level` it
      only ever matched the form that already existed, which is exactly what the first fix attempt
      did and why it appeared to do nothing.
- [x] **A gametest whose body throws does not fail, it hangs the batch.** The new command-tree tests
      dereferenced nodes directly; a missing one threw a `NullPointerException` out of the test body
      and the 38-test batch sat there printing `+` forever instead of reporting one failure. They now
      assert on child *names*, and every node is null-checked. Worth remembering: a hang here is a
      test bug, not an engine problem, and the 500k-line log is the tell.
- [x] Branches: the unpushed `26.3` commit is pushed and its stale worktree registration pruned. The
      `arena/*` branches are deleted after verifying by patch-id that every one of their commits is
      already in `26.3`, and that the only content they had beyond it was the *older* version of what
      `26.3` improved.

## Phase 17 — Adaptations leaked between worlds

Reported as "I made a new world and the diamond fist bar was already there". Two independent leaks
had to line up, and neither was visible from the symptom alone.

- [x] **The client mirror was never cleared, and nothing else ever cleared it.**
      `ClientAdaption` is all static; the only writes are the per-payload field assignments in
      `onSync`. On disconnect it kept the last world's `wearingWheel`, `ADAPTED`, `LEVELS` and the
      fist counters indefinitely. `AdaptionHud` gates on `wearingWheel`, which was stale-**true**,
      so a brand new world opened showing the previous world's HUD — a fist progress bar for a
      material the new player had never touched. Fixed by making `ClientAdaption` an
      `@EventBusSubscriber` that calls a new `clear()` on `LoggingOut`, listing every mutable field
      explicitly: a field added later and not listed is the same bug again, one field narrower.
      (Deliberately *not* put in `DarknessLightmap`, which also has a `LoggingOut` handler — the
      mirror clearing itself is the right ownership, and a hidden cross-class wipe is how a
      disconnect fix turns into a maintenance trap.)
- [x] **The server only pushed a sync while the wheel was worn.** `onPlayerLogin` was
      `if (wearingWheel(player)) { ...; sync(...); }` and the 1 Hz tick sync is likewise gated on
      `wearing`. So the second half of the bug was self-inflicted: even with a wiped mirror, a new
      world would show nothing until the first time the player put the wheel on. Login now syncs
      unconditionally, which makes "this world starts empty" true by construction rather than
      depending on a later wheel equip to overwrite stale data.
- [x] **The server side was already clean** — `onPlayerLogout` calls `FistMastery.forget(id)` and
      clears the UUID-keyed runtime caches, and `PlayerAdaption` is a `Player` attachment with a
      Codec, so it lands in that world's `player.dat`. No adaptation levels live in any static map;
      all of those are runtime caches (slash cooldowns, respawn health, instabreak stance, fist
      block counter). Worth having checked, because "static maps keyed by UUID" looked like the
      obvious culprit and there are thirteen of them.
- [x] **Verified by actually switching worlds**, not by reading the code: populated a wheel in
      world A, killed the client, moved world A aside, started a fresh world B and rejoined. World
      A's mirror read `wearing=true adapted=1 levels=2`; the disconnect read
      `cleared wearing=false adapted=0 levels=0`; world B received exactly one sync,
      `wearing=false adapted=0 levels=0` — the login sync that makes the empty state explicit,
      and the reason there is nothing to inherit.

## Phase 16 — Env_Darkness: the lightmap, not gamma

Reported as "gamma does not work". It genuinely did not, and the reason is worth recording because
all three obvious fixes are traps.

- [x] **The gamma option cannot be used.** The brightness slider is a bounded `OptionInstance`, and
      `OptionInstance.set` runs the value through `values.validateValue` first: out of range, it
      logs only `Illegal option value` and reverts to `initialValue` (0.5). So a lift capped by
      the slider is at the mercy of a cap the mod does not control, and the symptom is that
      nothing happens at all. It also overwrites a setting the player owns, which is why the
      first attempt had to remember and restore their Brightness. Removed.
- [x] **The night vision effect flickers, and the source is exact.**
      `GameRenderer.getNightVisionScale` returns `1.0F` only when the effect does *not* end within
      200 ticks, and otherwise returns `0.7F + sin((duration - partialTick) * PI * 0.2F) * 0.3F` —
      a full 0.4..1.0 swing on every window. The ProjectE-style rolling top-up therefore cannot
      avoid it: there is always a window to cross. The original report ("blinks when less than 10
      seconds are left") is this, to the tick.
- [x] **Shipped: a lightmap lift** (`client/DarknessLightmap.java` + `mixin/LightTextureMixin`).
      One `@ModifyArg` on the single `NativeImage.setPixelRGBA(III)V` call inside
      `LightTexture.updateLightTexture`, raising every pixel darker than `darknessLightmapFloor`
      (default 0.72) up to it and leaving brighter pixels untouched. That is the same normalisation
      vanilla night vision applies to those pixels, with the target below 1.0 so it is not blown
      out; relative shading and the day/night cycle both survive. Rejected a flat white overwrite,
      which would throw both away. `LightTexture.tick()` sets the dirty flag every client tick, so
      the change lands on the next frame and cannot drift.
- [x] **Injection point chosen for what will not break.** A redirect of `player.hasEffect` would
      have to declare the receiver as exactly `LocalPlayer` — the declared type of
      `Minecraft.player` — or Mixin rejects a supertype at apply time, the same trap as the DE
      laser mixin. `NativeImage` is a stable non-Minecraft type, so the handler signature is
      certain. The colour is argument index **2** of `setPixelRGBA(x, y, rgba)`, not 0.
- [x] **Verified in-game, not just compiled.** Carved a large sealed chamber, granted
      `Env_Darkness` through RCON, captured the same view twice: 500k of 921k pixels changed and
      the cave walls, ores and lava became readable. Instrumenting first was necessary, because the
      default dev spawn puts the camera *inside solid stone*, where nothing renders and the screen
      is black no matter what the lightmap says — two captures came back byte-identical and looked
      like a dead mixin. Counting `lift()` calls showed 256 per tick (the whole 16x16 map) and ~45
      actually lifted, i.e. the mixin had been working the entire time against an unrenderable
      view. `Math.round(r * scale)` uses a float multiply on purpose: an `int` product of two
      0..255 values overflows and wraps to a wrong colour on exactly the dimmest pixels the lift
      exists to fix.

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

## Phase 20 — Content: the mod stops being one-dimensional

### The diagnosis that started it

Audited against the code rather than from memory, and it is not a matter of taste:

- **zero world content** — no blocks, no structures, no mobs, no dimension, no biomes
- **zero goals** — no advancements, nothing that says what to do next
- **no choice** — every adaptation accumulates and applies at once, no loadout, no opportunity cost
- **no social layer** — nothing to do with another player
- **purely reactive** — nothing a player does changes *what* they can adapt to, only how long they wait
- **adversity is one single event**, and the wheel itself has no progression and no visual change

Thirty concepts, one shape: *hit by this → the number goes up → one less thing can hurt you.*

### The design constraint the poll set

Asked what to build, and the answer on cost was decisive: **"Не нужно. Прикол именно во всесильности"**
— no upkeep, no strain, no backlash, no currency. The point of the wheel is omnipotence, so every new
system had to only ever give. That is now a pinned test (`laterTiersAreNeverWorse`) rather than a
convention, because it is the one rule a future change is most likely to break by accident.

### What went in

| | |
|---|---|
| **Wheel awakening** | Six tiers derived from the adaptation count. Each *reveals* a family — Contact, Offense, Plunder, Existence — so the pool of what the wheel can adapt to grows instead of the pool of what it has survived. Derived, so no migration and a handed-over wheel arrives pre-awakened. |
| **Synergies** | Ten named combinations. Thirty independent adaptations are a list, not a build; these make the pool interact. Each is a behaviour, not another flat stat. |
| **Shedding** | Give an adaptation up deliberately for a burst. The one thing that changes *what* you can adapt to. The concept re-analyses at a third of its timer, so it is a swap rather than a price. |
| **Resonance** | Adapted players standing near each other strengthen each other, and an altar covers the player alone. |
| **Transfer** | Right-click a player to hand over your most developed adaptation they lack. |
| **Ritual blocks** | Brazier, Totem, Altar, Domain Stone — all auras, all only give, none with a block entity. |
| **Disciple of the Domain** | The first mob, and the answer to a wheel whose whole trajectory is upward in a world that is not. It scales to the nearest wearer, and it adapts too. |
| **Advancement tree** | 34 advancements on one custom criterion with seven optional conditions. |

### Things that turned out to be load-bearing

- **A tier that *reveals* rather than *gates*.** The first instinct was to gate the deep families
  behind tiers, which is a nerf to a player who already has them. Revealing keeps the promise:
  nothing is ever taken away, and the wheel's growth is visible as a widening of what is possible.
- **A missing parent drops an advancement's whole subtree.** 33 of 34 failed to insert because
  `"parent": "root"` resolves to `minecraft:root`. One `ERROR` line, no per-file reason, and a
  count that quietly read as "one loaded".
- **Polling beats eventing for criteria.** Completion, a tier crossing, a shed, a transfer, logging
  in already deep, and picking the wheel back up are six events. Firing at a moment means firing at
  six moments and the seventh is the bug.
- **Auras by scanning players, not ticking blocks.** A brazier in a storage chest should cost
  nothing; a player standing in a fully furnished room should cost a few lookups.
- **One copy of the synergy rules.** The client browser answers the same question from its mirror
  through the same `satisfied`, because a second copy would simply mean the synergy never appears.

### Test suite: 38 → 59

`WheelTierTests` (6), `SynergyTests` (5), `SheddingTests` (6), `DiscipleTests` (4). Three failures
found along the way were all mine: an unbounded `while` over the *cached* `getAdaptCount()` that
froze the server thread with nothing in the log, six tests written without the trailing
`helper.succeed()` (which the framework reports as a 100-tick timeout, not a failure), and one
asserting on a fixture the setup had stopped creating.

**A stale `runs/gameTestServer/config/adaptionwheel-*.toml` can loop the tracker forever** and hang
a run with no exception. Delete the config file first whenever a run hangs for no visible reason.

## Phase 21 — The Domain Stone trades

The stone handed an adaptation over for free, on a 45-second cooldown, drawn at random from whatever
the wheel had not finished. That made it the one place in the mod where progress cost nothing, and a
place where progress costs nothing is a place where every other cost is optional: the other
fifty-odd adaptations are each bought with suffering and time, and one counterweight that says
"or just stand here" undercuts all of them.

It is now an exchange. An item goes in, an adaptation comes out, and the item is the price.

| decision | what was chosen | why |
|---|---|---|
| what the item does | narrows the pool | the block's whole point is that the item *is* the question; naming one adaptation would make it a menu of 16 buttons |
| price | the item, consumed, **plus the player's own experience levels** | two prices rather than one, so a rich player with a feather still cannot buy a boss |
| cooldown | removed | the item is already the limiter; two limiters is one too many |
| old behaviour | removed outright | a second, free path is how a price stops being a price |

### The price ladder

`DomainExchange.priceFor` — **1** for a one-time adaptation, **2** for a leveled one, **3** for
`Drop_NPC_`, **4** for `Existence_` / `Mutation_` / `Dimension_Destroy`. The shape is the design: the
price tracks how much of the wheel's progression the thing is worth, so an adaptation the wheel would
have spent minutes earning costs more than one it grants outright.

**The floor of one level is load-bearing.** A price that could reach zero turns the stone into a place
to stand rather than a trade, which is precisely what it stopped being. It is the rounded-up form of
the half-level minimum that was asked for: vanilla experience is an integer and part of a level has
nowhere to live, so the honest reading of "at least 0.5 levels" is "at least one, and make the
ladder do the rest".

Charging is `giveExperienceLevels(-price)`, the same call vanilla uses for an enchanting table, so the
client's experience bar updates through the ordinary path and there is no second currency to sync.

An exchange grants the adaptation **whole** — `grantConceptUpTo(player, data, concept, MAX_LEVEL)` —
because what is being bought is an adaptation, not a step of one, and the ladder is what says how big
a thing that is. The level count that the slider used to choose is gone entirely, along with its
payload field and its action.

### What each item buys

A recipe is a list of **selectors**, each either an exact concept or a family written with a
trailing `*`:

    FEATHER      -> Debuff_levitation      one adaptation, always
    ENDER_EYE    -> Env_Void                idem
    NETHER_STAR  -> Type_*                  sixteen damage types, pick one

Families rather than a fixed enumeration, because the pool is then *derived* rather than declared: a
recipe does not need editing when the registry grows, and a missing entry cannot silently make a
recipe offer nothing.

Which surfaces immediately, and is not obvious:

- **`Debuff_*` and `Drop_NPC_*` are not in `AdaptationRegistry`.** Debuff keys are minted on the fly
  from whichever effect a player happens to be under (`Concepts.debuff(effectPath)`), and the per-mob
  families are minted per entity. The pool is walked out of `allDefinitions()`, so a recipe naming
  either would offer nothing at all — silently, with no error and no log line. Hence an **exact
  selector is resolved directly** rather than looked up. The cost is that such a concept sorts last
  by domain name, because `AdaptationRegistry.get` returns null for it; that is a far cheaper bill
  than a recipe that quietly trades nothing.
- **No mutation, `Dimension_Destroy` or `Self_Damage` is sold.** Those are milestones with unlock
  conditions of their own — a combo mutation exists *because* the wheel has learned both halves —
  and selling one for an item would replace that condition with a shopping list.

### Why the tier does not filter

The original preferred a family the wheel had **not** revealed yet, and that is why the block
existed: somewhere to go that was a step ahead rather than behind. It also keeps the subsystem honest
with the rest of the mod. Wheel tiers *reveal*, they never restrict — the wheel is omnipotent, so a
later tier is only ever a larger one — and a block that refused to sell a concept until the wheel
could analyse it would impose the one rule the tier system does not have, and would make the price
of the item irrelevant to what it buys.

### The container

The mod's first container GUI, and the first place `main` touches the container API at all.

- **No block entity.** The stone stays a plain `Block` and the position travels as menu-open data
  through `IMenuProviderExtension.writeClientSideData`. That extra data is **mandatory**: with none,
  the server sends a plain open-screen packet, the client factory is handed an empty buffer, and
  reading a `BlockPos` off it throws — at the moment a player opens the block, not at boot.
- **Its own container, not the player's.** The two slots wrap a `SimpleContainer`. Player-inventory
  indices are already spoken for by `addStandardInventorySlots` (hotbar over 0-8), and a second
  `Slot` on the same index is how a menu renders one item twice and moves it twice.
- **The server owns every number.** Candidates, selected row and level count live on the menu; the
  client sends *intent* and draws whatever the server last said. This is not defensiveness for its
  own sake — a row is chosen by **index**, and the pool is rebuilt whenever anything in the menu
  changes, so an index is potentially stale the moment it is sent. Re-deciding legality at the moment
  of the exchange is what makes a stale index a refusal rather than a wrong grant, and paying for an
  adaptation already held is the worst available outcome because the item would be gone and nothing
  would have changed.
- **The pool order is total** — revealed first, then domain, then concept name. The first clause is
  the original shortcut; the other two exist only because the client picks a row by index.
- **`Adaptersity` is never sold.** It is a survival challenge, not an adaptation: the price would be
  an item and what it buys would be a fight the player did not ask for.

### The screen

Minecraft-style from vanilla's own parts rather than an imitation:

- slot wells are `generic_54.png`'s own pixels, **sampled** (`#373737` frame, `#8B8B8B` body, white
  bottom-and-right inner shadow) — the same way `AdaptationScreen`'s panel palette was obtained;
- the cross on the empty wheel slot is `container/beacon/cancel`, the sprite vanilla uses to say
  *this is missing*;
- panel, bars and scroller reuse `AdaptationScreen`'s primitives, so the two screens read as one mod;
- **nothing else is blitted.** `blitSprite` blits a whole sprite with no way to take an 18×18 window
  out of a 108×19 strip, and the 7-argument `blit` hardcodes a 256×256 sheet. A sprite is reachable
  only at its own size, which is exactly the cross and nothing else here.

### Three faults found by drawing it rather than reasoning about it

None would have shown in a build, and the first two would have been obvious on screen:

1. slots placed at x=26 while the screen drew their wells at x=8 — every item outside its square;
2. shift-clicking a wheel into a full wheel slot indexed slots 38..46 in a 38-slot list;
3. a 112-wide levels bar under a list panel starting at x=40, so the bar and both its captions were
   drawn over;
4. and, found only by a screenshot of the running screen — **the player inventory was invisible**.
   Vanilla does not draw slot wells at all: `renderSlot` renders the contents only, and the wells are
   baked into a background texture. This screen draws its own panel, so it has to draw every well
   itself, and the first version drew two of them for its own slots. The inventory existed, was
   clickable, and could not be seen.

All four are layout arithmetic, so all four now live in `DomainStoneMenu` as the single declaration
the screen reads, and `theMenuIsLaidOutWhereTheScreenExpects` pins them. The fourth is the one worth
remembering: **a container screen draws no slot backgrounds**, so a screen that draws its own panel
must draw its own wells for every slot, including the thirty-six the player owns.

### Verification

Nine gametests, `DomainExchangeTests`. The one that matters most is **`everyRecipeSellsSomething`**:
a selector naming nothing produces an empty list, which is completely silent — the screen opens, the
item goes in, and the list is blank. That is precisely the "present, registered, and connected to
nothing" shape this repo has been bitten by before, and nothing else would catch it.

70 tests total, up from 60. Verified by compilation, a clean dedicated-server boot and the suite.
**The screen itself has not been seen running** — a dedicated server has no GUI — so the layout was
checked against a mock drawn from the same constants, which is how faults 1-3 above were found, and
the screen is otherwise unplaytested. 26.3's nine tests are not ported, because that branch has no
test framework at all.

---

## Phase 22 — The Resonance Altar takes a mob's own loot

The mod was almost entirely defensive. Every one of its hundred-odd adaptations asks *what is being
done to me*: fall damage, fire, drowning, a warden's sonic boom. `Contact_<mob>` is a reaction. There
was nothing in it that paid for **offence**, and nothing in it that a player could spend on rather
than suffer.

So the altar was given a second face. Feed it a mob's own drop and it sells that mob's adaptations:
the ability to hurt it, and the ability to take more from it.

| decision | what was chosen | why |
|---|---|---|
| which block | the **Resonance Altar**, keeping its aura | one block, two questions: the aura is passive and about who is standing near you, the trade is active and about what you will spend. And the altar was already the mod's "this is a rite, not a machine" block |
| what one offering grants | a **choice**: offense *or* drop rate | a player who wants a mob's drops is not asking for the ability to fight it. Splitting them is the whole point |
| price | the item, consumed, **plus experience**, as on the stone | two prices, so a rich player with a bone still cannot buy a Warden |
| which mobs | **every mob that drops anything** — 58 of them | see below |

### Why the mapping is shipped rather than scraped

Which mobs drop what vanilla answers nowhere. It could be read out of the loot tables at runtime, and
that was the obvious implementation. It was rejected for three reasons:

- `LootPool`'s entry list is a `private final` field behind `LootPoolEntryContainer` on 1.21.1, so
  reaching the items means an unwrap that differs between branches;
- the loot table *shape* changed on 26.3 (`functions` → `modifier`, `item` → `name`), so the same walk
  would be two different parsers;
- and a mob's loot is static data, so scraping it at runtime re-derives a constant fifty-eight times.

So it is generated once from the vanilla data files and lives in
`data/adaptionwheel/domain_altar/<mob_path>.json`, one per mob. `server/AltarOfferings` reads the
directory on first use and builds the reverse index — item → mobs — in memory.

**The twenty-six mobs that drop nothing are simply absent.** allay, bat, fox, ocelot, wolf, villager,
the player. That is the correct answer rather than an empty entry, and a test pins it: an empty entry
would make the altar answer questions about a mob that cannot be paid for.

**The Warden needed no special case.** `loot_tables/entities/warden.json` already lists the sculk
catalyst, so the generation found it unaided — and there is a test that says so, because a hand-written
entry would look identical from the outside.

**The loot tables turned out to be byte-identical between 1.21.1 and 26.3** — `diff -rq` over all 84
found no difference. That is worth knowing rather than assuming: the loot table *shape* did change on
26.3, but only for tables the mod itself authors; vanilla's mob tables kept the fields the generator
reads. So the data set is one set of files for both branches, not two that can drift.

A mod that changes what its mobs drop ships its own file and is covered without a code change.

### A drop-rate adaptation is paid in kills, not in levels

`Drop_NPC_<mob>`'s level is **derived** from a kill count — everywhere else in the mod it is
`dropLevelFromKills(kills)` and nothing else. An altar that assigned the level directly would leave
that counter lying: a player holding an eighth level of loot-luck having killed one chicken.

`AdaptionEvents.grantKillsToward` tops the kill count up to whatever the existing table says that
level costs, and the existing rule turns that into the level. One rule, one table, one number, and no
second way to reach the same value.

### The stone and the altar share their mechanics

`menu/TradeMenu` holds both slots, the pool, the ordering, the two prices, the legality re-check and
the server-side authority; a subclass only says **what it sells**. `client/TradeScreen` does the same
for the drawing.

Copying them instead would be the trap this mod has walked into three times already: two copies of a
rule, one of them fixed later, and nothing that notices.

**`TradeScreen` is generic in its menu type, and that is forced rather than stylistic.**
`AbstractContainerScreen` implements `MenuAccess<T>`, which declares `T getMenu()` and is *invariant*,
so a screen shared by two menu types has to be `MenuAccess` in both of them or `RegisterMenuScreensEvent`
rejects the registration with a type error. One type parameter is what satisfies both.

### What this branch cannot show

`main` has 75 tests, five of them new, and they cover the index — the part that fails silently, since a
data file naming a missing item, an unreadable file, or an item no file mentions all produce "the altar
opens and shows nothing" with no error anywhere. 26.3 has no test task, so the index is unverified
there; the data files are the same files.

Neither screen has been seen running.

## Phase 23 — Everything 26.3 had and 1.21.1 did not

Ported back from the `26.3` branch, in one pass, with the version differences spelled out where
they were unavoidable. Three tools (`tools/branch_parity.py`, `branch_audit.py`,
`branch_bodies.py`) drove the audit: method names, then call sites / config defaults / lang values
/ block tags, then statement-level bodies. What they found and what it turned out to be:

- **One trading block.** The Domain Stone is deleted outright — block, item, menu, screen, menu
  type, recipe, advancement, blockstate, model, loot table, texture, pickaxe-tag entry, config key,
  lang key. The altar keeps the aura and takes the trade behind `altarTradeEnabled`.
  `TradeScreen` stops being generic, because `MenuAccess<T>` is invariant and with one menu there is
  no type argument to supply.
- **A purchase is one level and its price climbs** (`tradeCostGrowth` 2.0, capped by
  `tradeMaxItems` 32 and `tradeMaxXp` 30), with the price computed on the server and synced per
  row, because it depends on how many levels *that wheel* holds and the client has no fed wheel.
- **The polished screens**: sunken list wells, synergy chips, a real button, an XP readout with an
  orb, a breathing empty wheel slot, a draggable scrollbar, hit areas matching what is drawn.
- **`/adaptionwheel debug aggro` and `debug altar`**, and an altar index that logs what it loaded
  and forgets itself when the server stops.
- **The altar's Blockbench model and textures**, byte-for-byte as exported, textures bound by a
  child model because the file's own names (`block1`, `block`) cannot resolve through the block
  atlas.
- **The Adaptation Temple** as a jigsaw structure with a generated processor list, which needed two
  version-specific fixes the game answered and the source would not have: the `BlockState` codec is
  spelled `Name`/`Properties` on 1.21.1 and `id`/`properties` on 26.3, and the copper chain family
  does not exist before 1.21.2.

Verified by `./gradlew runGameTestServer`: **all 79 gametests pass**, which is what caught both
datapack problems.

### What deliberately still differs

The remaining parity output is API shape, not behaviour: `renderBg`/`renderTooltip` against
`extractLabels`/`extractTooltip` (the coordinate space is inverted between the versions),
`GuiGraphics.drawString` against `GuiGraphicsExtractor.text`, a button index against a
`MouseButtonEvent`, `ResourceLocation` against `Identifier`, `Identifier.parse` against
`Registry.get(...).map(Holder.Reference::value)`, `AbstractContainerMenu.addStandardInventorySlots`
against the hand-rolled helper 1.21.1 lacks, `Entity.hurt` against `hurtServer`, and the
`getBoundingBoxForCulling` override that 26.x moved onto the renderer. `WheelData.migrateLegacyConcepts`,
`SurfaceAdaptations.fistLevel` and `AdaptionConfig.fistTierSpeed` remain main-only by design: 26.3
forked before the fist existed, so no 26.3 save can hold a `Fist_Copper` to migrate.

### The altar model on 1.21.1 — open

`models/block/resonance_altar.json` is the author's export, byte-for-byte, and it loads on 26.3. On
1.21.1 it does not: **1.21.1's element rotation is single-axis** (`{"origin","axis","angle"}`), and
216 of the 336 elements carry a two-axis Euler rotation (`x=90,z=90` and friends) that the newer
loader accepts and this one cannot express at all. The client answers with
`JsonSyntaxException: Missing axis, expected to find a string`. Either the rotations get baked into
axis-aligned geometry (every angle here is a multiple of 90°, so the silhouette survives; the UVs do
not) or the model is re-exported from Blockbench for a Java target.
