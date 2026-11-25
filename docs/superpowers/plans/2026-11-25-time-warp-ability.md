# Time Warp Ability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `time_warp` as a selectable active ability that slows every entity in a radius while the wearer moves normally, using tick decimation and a substituted partialTick.

**Architecture:** Server owns a logical millisecond clock and cancels `tickNonPassenger` for entities inside the sphere except on "slow ticks"; a state packet drives the same decimator on the client. Damage and explosions are buffered and applied when the zone ends. No `DimensionType` game-speed override.

**Tech Stack:** NeoForge 21.1.248, Java 21, Mojang mappings, `PacketDistributor`, `FriendlyByteBuf`/`StreamCodec`, Mixin + MixinExtras.

**Spec:** `docs/superpowers/specs/2026-11-25-cursed-slash-slicing-and-time-warp-design.md` and `docs/time-slowdown.md`.

## Global Constraints

- 1.21.1 / NeoForge 21.1.248 only; no 26.3 APIs (`Identifier`, `isClientSide()` field, etc.).
- Ability selection stays server-authoritative via `AbilityDispatch`; no new keybind (activation is the existing R key).
- Unlocked by default: `unlocked(PlayerAdaption) -> true`. A later adaptation gate replaces that body only.
- Defaults: speed `0.2`, radius `64`, duration `120 s`, cooldown `10 s`, owner excluded.
- Do not touch the orbital wheel skin work (`WHEEL_SKIN_DESIGN.md` / `WHEEL_SKIN_PLAN.md`) or the TAMED UI.
- Every task ends with `./gradlew build` and `./tools/run-gametests.sh` green; use `timeout`, as documented.

## Review Focus

- A player outside the radius and the owner must never be decimated: test `pular` for owner/outside.
- `dayTime` must not be skipped when `doDaylightCycle=true`: test fractional compensation.
- Buffered damage must not recurse or double-apply: test `aplicarVolta` twice.
- Client `partialTick` must stay in `[0,1]` with a frozen clock: test `parcial()` bounds.
- Logout/dimension change must clear per-player state: test cleanup.

---

### Task 1: Ability registration and default unlock

**Files:** Create `src/main/java/ru/adaptionwheel/server/TimeWarpAbility.java`; modify `src/main/java/ru/adaptionwheel/AdaptionWheel.java`; modify both lang files.

**Interfaces:**
- Produces: `TimeWarpAbility.ID = "time_warp"`, `ActiveAbility definition()`, `boolean unlocked(PlayerAdaption)`, `boolean start(ServerPlayer)`.

- [ ] **Step 1: Write the failing test** in `src/main/java/ru/adaptionwheel/test/TimeWarpAbilityTests.java`: assert `ActiveAbilities.all()` contains id `time_warp` and `AbilityDispatch.isUnlocked("time_warp", new PlayerAdaption())` is true.
- [ ] **Step 2:** Run `./tools/run-gametests.sh 1 150`; expect FAIL (no such id).
- [ ] **Step 3:** Implement `definition()` with icon/color/cooldown and `unlocked()` returning `true`; register in `commonSetup` beside `DiveAbility`.
- [ ] **Step 4:** Run the suite; expect PASS.
- [ ] **Step 5:** Add `ability.adaptionwheel.time_warp.name/.desc` to both lang files, keeping key parity.
- [ ] **Step 6:** Commit.

### Task 2: Logical clock and decimation math

**Files:** Modify `TimeWarpAbility.java`; test in `TimeWarpAbilityTests.java`.

**Interfaces:**
- Produces: `static long advanceClock(long clock, float speed)`, `static boolean slowTick(long clock)`, `static float partial(long clock, float speed, float partialTick)`, `static boolean pular(Entity e)`, `static int estadoEnvio(Entity e)`.

- [ ] **Step 1:** Write failing tests: at speed `0.2`, `advanceClock(0,0.2f)==200` and `slowTick(1000)` true while `slowTick(999)` false; `partial(...)` always within `[0,1]`; `pular` false for the owner and for an entity outside radius.
- [ ] **Step 2:** Run suite; expect FAIL (methods absent).
- [ ] **Step 3:** Implement the four methods exactly as in `docs/time-slowdown.md` §1.1–1.2.
- [ ] **Step 4:** Run suite; expect PASS.
- [ ] **Step 5:** Commit.

### Task 3: Server state machine and packets

**Files:** Create `src/main/java/ru/adaptionwheel/network/TimeWarpStatePayload.java`; modify `AdaptionNetworking.java`, `TimeWarpAbility.java`, `AdaptionConfig.java`; create `mixin/TimeWarpServerLevelMixin.java` and `mixin/TimeWarpServerEntityMixin.java`; modify `adaptionwheel.mixins.json`.

**Interfaces:**
- Consumes: `advanceClock`, `slowTick`, `pular`, `estadoEnvio` from Task 2.
- Produces: `TimeWarpStatePayload(UUID owner, float speed, long clock, boolean tick, BlockPos centre, float radius, boolean active)` with `STREAM_CODEC`; `static void TimeWarpAbility.onServerTick(ServerTickEvent.Start)`; `static void clear(UUID owner)`.

- [ ] **Step 1:** Write failing tests: after `start(player)`, a second `start` inside cooldown returns false; `clear(uuid)` resets state; with `doDaylightCycle=true` and `speed=0.2`, ten server ticks advance `dayTime` by exactly 2, not 0 or 10.
- [ ] **Step 2:** Run suite; expect FAIL.
- [ ] **Step 3:** Implement `start`, per-UUID `State`, `onServerTick`, `clear`, config values, payload + registration, and the two mixins (`tickNonPassenger` HEAD cancel; `sendChanges` delay/force).
- [ ] **Step 4:** Run suite; expect PASS.
- [ ] **Step 5:** Commit.

### Task 4: Client decimator, partialTick and render hooks

**Files:** Create `src/main/java/ru/adaptionwheel/client/TimeWarpClient.java`; create `client/TimeWarpClientLevelMixin.java`, `client/TimeWarpPartialTickMixin.java`, `client/TimeWarpParticleMixin.java`, `client/TimeWarpSoundMixin.java`; modify mixin config.

**Interfaces:**
- Consumes: `TimeWarpStatePayload` from Task 3.
- Produces: `static void TimeWarpClient.onState(TimeWarpStatePayload)`, `static float parcial(float p)`, `static boolean pular(Entity e)`.

- [ ] **Step 1:** Write failing test for `parcial`: with a frozen clock it returns `clock%1000/1000f`; with `speed=1` it equals the input.
- [ ] **Step 2:** Run suite; expect FAIL.
- [ ] **Step 3:** Implement client queue (`pendentes`, max 4), `parcial`, `pular`, and the four mixins.
- [ ] **Step 4:** Run suite; expect PASS.
- [ ] **Step 5:** Commit.

### Task 5: Buffered damage and explosions, sound

**Files:** Modify `TimeWarpAbility.java`, `TimeWarpClient.java`; create `network/TimeWarpHitPayload.java`; modify `AdaptionNetworking.java`.

**Interfaces:**
- Produces: `static void applyBuffered(UUID owner)`, `static void clear(UUID owner)` (extends Task 3).

- [ ] **Step 1:** Write failing test: two buffered hits apply once on `applyBuffered`, second call applies nothing.
- [ ] **Step 2:** Run suite; expect FAIL.
- [ ] **Step 3:** Implement `LivingAttackEvent` cancel/accumulate, `ExplosionEvent.Start` cancel/accumulate, and sound low-pass; reuse `clear` to drop buffers on end/logout.
- [ ] **Step 4:** Run suite; expect PASS.
- [ ] **Step 5:** Commit.

### Task 6: Documentation and final verification

- [ ] **Step 1:** Add the ability and config keys to `VERSIONS.md` under `Unreleased`.
- [ ] **Step 2:** Run `./gradlew build --no-daemon`, `python3 tools/check-config.py`, `python3 tools/check-lang.py`, `./tools/run-gametests.sh 3 150`; all PASS.
- [ ] **Step 3:** Commit.
