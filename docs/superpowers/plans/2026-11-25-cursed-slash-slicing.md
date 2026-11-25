# Cursed Slash Slicing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a cursed-energy slash from the Sword of Extermination hits a mob, the server decides a cutting plane and removes the mob; each client re-renders the still-living mesh into two independently simulated halves.

**Architecture:** Server-side geometry (`frac` over the target AABB) and loot/XP happen before `discard()`. A packet carries point/normal/sweep; the client captures the mob mesh through access mixins, clips quads, builds cut caps and simulates halves as a pure client effect. No server entities or NBT cloning.

**Tech Stack:** NeoForge 21.1.248, Java 21, Mixin + MixinExtras, `StreamCodec`, `PoseStack`/`GuiGraphics`, `RenderType`.

**Spec:** `docs/superpowers/specs/2026-11-25-cursed-slash-slicing-and-time-warp-design.md` and `docs/mob-slicing.md`.

## Global Constraints

- Only `Mob` targets; never `Player`, never multipart (`PartEntity`), never an excluded id.
- Loot and XP must be produced **before** `discard()`, and the equipped wheel must not be duplicated.
- The cut plane comes from the slash sweep (`lastPosition` → current position), not from target yaw.
- `frac` window default `0.15..0.85`; out-of-window is a scratch (damage + fire), not a cut.
- Halves are client-only FX with a hard cap (64 groups / scaled budget); no new saved entities.
- Do not touch the Time Warp ability files or the orbital wheel skin work.
- Every task ends with `./gradlew build` and `./tools/run-gametests.sh` green under `timeout`.

## Review Focus

- A slash that does not move must still produce a valid plane: test fallback `dir × up` and straight-up `dir`.
- A cut through a tangent mesh must stay visible: test the 15% plane-nudge step.
- Killing a wheel-wearing mob must drop exactly one wheel: test against the existing loot fallback.
- Players and multipart entities must never be cut: test `frac` gate excludes them.
- Mesh capture must happen while the entity still exists; zero quads must degrade silently.

---

### Task 1: Server cut geometry and packet

**Files:** Create `src/main/java/ru/adaptionwheel/server/SlashCut.java`; create `src/main/java/ru/adaptionwheel/network/SlashCutPayload.java`; modify `AdaptionNetworking.java` and `AdaptionConfig.java`.

**Interfaces:**
- Produces: `SlashCutPayload(int entityId, Vec3 point, Vec3 normal, Vec3 sweep, int ownerId)` with `STREAM_CODEC`; `static boolean SlashCut.cut(ServerLevel level, LivingEntity target, LivingEntity owner, Vec3 from, Vec3 to)`; `static float SlashCut.fraction(LivingEntity target, Vec3 eye, Vec3 normal)`.

- [ ] **Step 1:** Write failing tests in `src/main/java/ru/adaptionwheel/test/SlashCutTests.java`: `fraction` is in `[0,1]` for a normal through a zombie's AABB; `cut` returns false for a `Player` and for `frac` outside the window; cutting a mob that wears a wheel yields exactly one wheel in the drops, never two.
- [ ] **Step 2:** Run `./tools/run-gametests.sh 1 150`; expect FAIL.
- [ ] **Step 3:** Implement Sutherland–Hodgman AABB clipping, the 8-corner `d_i` projection, `frac` gate, scratch path, and `discard()` with loot/XP before removal. Add config keys for min/max/scratch damage/fire.
- [ ] **Step 4:** Run suite; expect PASS.
- [ ] **Step 5:** Register `SlashCutPayload` as `playToClient`; commit.

### Task 2: Hook cursed slashes into the cut

**Files:** Modify `src/main/java/ru/adaptionwheel/entity/CursedSlashProjectile.java`; modify lang files.

**Interfaces:**
- Consumes: `SlashCut.cut(...)` from Task 1.
- Produces: cursed slashes call `cut` before `target.hurt(...)`; normal slashes keep today's behavior.

- [ ] **Step 1:** Write failing gametest: a cursed slash crossing a zombie triggers `SlashCut` (assert by hook counter/visible effect) while a positive-mode slash does not.
- [ ] **Step 2:** Run suite; expect FAIL.
- [ ] **Step 3:** In `onHitEntity`, pass `lastPosition`/`position()` and the cursed flag into the cut path; keep `maxHits` semantics intact.
- [ ] **Step 4:** Run suite; expect PASS.
- [ ] **Step 5:** Add lang strings for scratch/cut feedback if any; commit.

### Task 3: Mesh capture and access mixins

**Files:** Create `src/main/java/ru/adaptionwheel/client/slashcut/Corpo.java`, `Fonte.java`, `CapturaCorpo.java`; create `mixin/ModelPartMixin.java`, `mixin/EntityRenderDispatcherMixin.java`; modify `adaptionwheel.mixins.json`.

**Interfaces:**
- Produces: `static Corpo CapturaCorpo.capturar(LivingEntity e, float partial)`; `Corpo` exposes parallel position/UV/normal/color/light arrays grouped into quads.

- [ ] **Step 1:** Write a JUnit-free static test in `SlashCutTests` that a `Corpo` with zero quads returns null from `capturar` on a removed entity.
- [ ] **Step 2:** Run suite; expect FAIL.
- [ ] **Step 3:** Implement consumer/filter/access-chain capture as in `docs/mob-slicing.md` §5. Keep the render-type filter list exact.
- [ ] **Step 4:** Run suite; expect PASS.
- [ ] **Step 5:** Commit.

### Task 4: Clipping, caps and two-half simulation

**Files:** Create `src/main/java/ru/adaptionwheel/client/slashcut/Metades.java`; create `client/SlashCutEffectRenderer.java`.

**Interfaces:**
- Consumes: `Corpo` from Task 3 and `SlashCutPayload`.
- Produces: `static void Metades.criar(SlashCutPayload p)`, `static void Metades.desenhar(...)`, `static void Metades.limpar()`.

- [ ] **Step 1:** Write failing tests for quad side classification, cap polygon area guard (`<1e-4` skipped), and the tangent-mesh case where the 15% plane nudge must move the plane inside the mesh.
- [ ] **Step 2:** Run suite; expect FAIL.
- [ ] **Step 3:** Implement plane nudge, quad clipping with interpolation, boundary edges, union-find shells, convex hull caps, kinematics, gravity/collision, fade, and `ESCONDIDAS`-style original hiding.
- [ ] **Step 4:** Run suite; expect PASS.
- [ ] **Step 5:** Commit.

### Task 5: Final verification and docs

- [ ] **Step 1:** Add slicing to `VERSIONS.md` under `Unreleased`.
- [ ] **Step 2:** Run `./gradlew build --no-daemon`, `python3 tools/check-config.py`, `python3 tools/check-lang.py`, `./tools/run-gametests.sh 3 150`; all PASS.
- [ ] **Step 3:** Commit.
