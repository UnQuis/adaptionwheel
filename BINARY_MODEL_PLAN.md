# Binary Model Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** replace OBJ text parsing on the GL thread with a validated FRRB-v1 binary read for every shipped 3D model.

**Architecture:** a Python stdlib converter turns each authored OBJ into one `.bin` with per-corner 14-float records, sequential uint32 indices, group/material layers and metadata; `ObjMesh.parseBinary(byte[])` validates the whole header before allocating, unpacks into the existing 11-float corner format, and both reload listeners read only the binary. The `.obj` stays in git as source but is excluded from the jar.

**Tech Stack:** Python 3 stdlib (`struct`), Java 21, NeoForge 21.1.248, `ByteBuffer`/`FloatBuffer`, Gson not needed.

**Spec:** `BINARY_MODEL_DESIGN.md` in this worktree (committed `07f1697`). Read it alongside this plan.

## Global Constraints

- `FRRB v1`, little-endian; magic `FRRB`, version 1, exactly 6 layers.
- Per-corner records: 14 floats = pos3 + packed colour (float bits, RGBA bytes) + data4 (material id in x, rest 0) + aux4 (normal xyz, meta w) + uv2. Corner count means triangle corners, not unique OBJ positions.
- Positions are bbox-centred **in the converter**; runtime must not centre again. Metadata is reporting-only.
- Material id in `data4.x` (float precision): 1.0 for dharma/sword, 0/0.5/1 for orbital. Packed colour never carries the id.
- Validate magic/version/layer count/corner count/index count/group count/material count/byte count and `remaining()` **before** allocating any array.
- Ship `.bin` only: `sourceSets.main.resources.exclude '**/*.obj'`; keep `.obj` in git.
- No runtime OBJ fallback in the jar; on a missing/corrupt binary log the reason and use the existing fallback.
- Step 0 applies PR #11 product commits (`d2ccbe8..0628fc8`) with the one `AdaptationWheelScreen` conflict resolved manually; docs/plan commits stay out.
- Verification: `./gradlew build`, `./tools/run-gametests.sh 4 150` (known spawn flake; wrapper retries), `./tools/validate-shaders.sh`.

## Review Focus

1. Corrupt header (bad magic, version, counts, truncated bytes) must throw before a 224 MB allocation — Task 3 test `corruptHeadersAreRejectedBeforeAllocation`.
2. Double centring (converter + runtime) would shift models and break spin/scale — Task 2/3 test `binaryMatchesTextParserOnFixture` compares positions exactly.
3. Flat normals and UV seams must survive the shared-vertex trap — Task 3 test asserts binary corner count equals text parser's `corners().length / STRIDE` on the fixture and orbital model.
4. Material id precision: 0.5 packed as RGBA would decode ≈0.498/0.502 — Task 3 test `materialIdSurvivesAsExactFloat` asserts `corners[o+8]` equals 0.5 exactly.
5. Excluding `.obj` from main resources breaks tests that load shipped OBJs from the classpath — Task 4 updates `ObjMeshTests` to read `.bin` and keeps a small OBJ fixture in `src/test/resources`.

---

### Task 1: Apply the wheel-skin branch as the base

**Files:**
- Modify: `src/main/java/ru/adaptionwheel/client/AdaptationWheelScreen.java` (conflict resolution)
- No other hand edits; this task only lands the 10 commits.

**Interfaces:**
- Consumes: commits `d2ccbe8..0628fc8` from `feature/orbital-wheel-skin`.
- Produces: `WheelSkins`, `ModDataComponents.WHEEL_SKIN`, `ObjMesh` with orbital ids, both wheel meshes and both reload listeners.

- [ ] **Step 1: Cherry-pick the range**

Run from this worktree:
```bash
git cherry-pick d2ccbe8..0628fc8
```
Expected: stops with a conflict in `AdaptationWheelScreen.java`; all other commits apply.

- [ ] **Step 2: Resolve the menu conflict**

Keep both sides' changes: the existing menu layout/tabs from this branch plus the skin row and hit-rect handling from the cherry-picked commit. Do not drop either drawing or click path.

- [ ] **Step 3: Verify the landed base**

Run: `./gradlew build --no-daemon` and `./tools/run-gametests.sh 4 150`.
Expected: clean build; all tests pass (count should be 212+ as on the feature branch).

- [ ] **Step 4: Commit the conflict resolution**

```bash
git add src/main/java/ru/adaptionwheel/client/AdaptationWheelScreen.java
git cherry-pick --continue
```

---

### Task 2: Converter and generated binaries

**Files:**
- Create: `tools/gen_model_bin.py`
- Create: `src/test/resources/models/fixture.obj` (tiny OBJ with a 7-token `v` colour line, one quad, two UVs)
- Create: `src/main/resources/assets/adaptionwheel/models/entity/dharma_chakra.bin`, `orbital_ring.bin`, `src/main/resources/assets/adaptionwheel/models/entity/soe.bin`

**Interfaces:**
- Consumes: the three shipped OBJs.
- Produces: FRRB-v1 files; `tools/gen_model_bin.py OBJECT OUTPUT` CLI; byte layout pinned below.

Byte layout (pin exactly):
- Header 32 bytes: `<4sIIIIIII` = magic `FRRB`, version 1, layerCount 6, cornerCount, indexCount, groupCount, materialCount, byteCount (total file bytes).
- Layer 0: cornerCount × 14 floats.
- Layer 1: indexCount uint32.
- Layer 2: groupCount × (`<H` length + UTF-8 bytes).
- Layer 3: materialCount × (`<H` length + UTF-8 bytes).
- Layer 4: indexCount/3 × `<II` face→group/material.
- Layer 5: metadata `<Iffffff` = faceCount, centreX, centreY, centreZ, radius, height, reserved.

- [ ] **Step 1: Write the failing converter test**

Create `tools/test_gen_model_bin.py` (stdlib) that invokes `python3 tools/gen_model_bin.py src/test/resources/models/fixture.obj /mnt/Barracuda/tmp/opencode/fixture.bin`, reads the output and asserts magic `FRRB`, version 1, layerCount 6, byteCount == file length and one decoded corner's first three floats. Run it before the converter exists.
Expected: FAIL — `tools/gen_model_bin.py` not found.

- [ ] **Step 2: Implement `tools/gen_model_bin.py`**

Stdlib only. Parse `v` (7-token colour), `vt`, `vn`, `f` with fan triangulation and negative indices; apply the exact same centring, winding `(v0,v1,v2)`, flat-vs-smooth normal rule and radius as `ObjMesh.parse`; emit one 14-float record per triangle corner with `data4.x` material id from the colour's red channel or 1.0 when absent; sequential indices 0..N−1. Print input/output bytes and corner counts.

- [ ] **Step 3: Run the test and convert the three models**

Run: `python3 tools/test_gen_model_bin.py`, then three `gen_model_bin.py` invocations.
Expected: test PASS; orbital bin ≈10 MB, dharma ≈0.6–1 MB, soe tiny; no OBJ parsing on the runtime path yet.

- [ ] **Step 4: Commit**

```bash
git add tools/gen_model_bin.py src/test/resources/models/fixture.obj \
        src/main/resources/assets/adaptionwheel/models/entity/*.bin
git commit -m "Tools: FRRB converter and generated model binaries"
```

---

### Task 3: Runtime binary reader

**Files:**
- Modify: `src/main/java/ru/adaptionwheel/client/ObjMesh.java` (add `parseBinary`)
- Modify: `src/main/java/ru/adaptionwheel/test/ObjMeshTests.java` (new tests)

**Interfaces:**
- Consumes: FRRB bytes from Task 2.
- Produces: `public static ObjMesh parseBinary(byte[] bytes) throws IOException` filling the same `corners` array (`STRIDE = 11`), `faceCount`, `radius`, `height` as `parse`.

- [ ] **Step 1: Write failing tests**

Add to `ObjMeshTests` (class already has `@GameTestHolder`/`@PrefixGameTestTemplate(false)`):
- `binaryMatchesTextParserOnFixture`: load `fixture.obj` from test resources and `fixture.bin` from generated bytes; assert equal triangle counts and each corner's floats within 1e-6 (positions/uv/normals), and `corners[o+8] == 1.0f` exactly.
- `materialIdSurvivesAsExactFloat`: feed a hand-built FRRB byte array with `data4.x = 0.5f`; assert `corners[8] == 0.5f`.
- `corruptHeadersAreRejectedBeforeAllocation`: bad magic, version 2, cornerCount = 0xFFFFFFFF and a truncated byte array each throw `IOException` whose message names the failed field.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./tools/run-gametests.sh 4 150`.
Expected: compile failure — `parseBinary` does not exist.

- [ ] **Step 3: Implement `parseBinary`**

Validate every header field and `buffer.remaining() == byteCount` **before** allocating `corners`; use `ByteBuffer.order(LITTLE_ENDIAN).asFloatBuffer()/asIntBuffer()`; unpack 14→11 floats (pos, uv, normal, colour from `data4.x` red and packed RGBA green/blue); build `quadCorners` lazily as today. Do not centre or recompute radius from positions — read metadata.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./tools/run-gametests.sh 4 150`.
Expected: all tests pass, including the three new ones.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ru/adaptionwheel/client/ObjMesh.java \
        src/main/java/ru/adaptionwheel/test/ObjMeshTests.java
git commit -m "Client: validated FRRB binary model reader"
```

---

### Task 4: Wire the reload listeners and exclude OBJs from the jar

**Files:**
- Modify: `src/main/java/ru/adaptionwheel/client/WheelRenderer.java` (reload listener ~`:715`)
- Modify: `src/main/java/ru/adaptionwheel/client/SwordOfExterminationRenderer.java` (reload listener ~`:318`)
- Modify: `build.gradle` (`:72`)
- Modify: `src/main/java/ru/adaptionwheel/test/ObjMeshTests.java` (switch shipped-model helpers to `.bin`)

**Interfaces:**
- Consumes: `ObjMesh.parseBinary(byte[])`.
- Produces: both reload listeners read `<model>.bin`; jar contains no `.obj`.

- [ ] **Step 1: Write the failing test update**

Change `ObjMeshTests.shipped()`/`load()` to load `/assets/adaptionwheel/models/entity/dharma_chakra.bin` as bytes and call `parseBinary`; keep the parsing assertions unchanged. Run before wiring.
Expected: tests fail because the binary is not yet on the test path through the changed helper (or because main resources still expose the OBJ). Use this as the RED step.

- [ ] **Step 2: Switch both listeners**

In each `prepare`, request the `.bin` resource; read all bytes with `resource.get().openAsStream().readAllBytes()`; call `ObjMesh.parseBinary`; keep the same per-model log line and the same error fallback. Name the exact ResourceLocation in the log so a missing binary is obvious.

- [ ] **Step 3: Exclude OBJs from the jar**

At `build.gradle:72`, add `exclude '**/*.obj'` inside `sourceSets.main.resources` (keep the generated resources srcDir). Verify with `unzip -l build/libs/*.jar | grep '\.obj'` → no matches, and `grep '\.bin'` → three matches.

- [ ] **Step 4: Verify everything**

Run: `./gradlew build --no-daemon`, `./tools/validate-shaders.sh`, `./tools/run-gametests.sh 4 150`, `./tools/check-jar.sh`.
Expected: clean build; 6 shader stages OK; all gametests pass; jar check PASS.

- [ ] **Step 5: Commit**

```bash
git add build.gradle src/main/java/ru/adaptionwheel/client/WheelRenderer.java \
        src/main/java/ru/adaptionwheel/client/SwordOfExterminationRenderer.java \
        src/main/java/ru/adaptionwheel/test/ObjMeshTests.java
git commit -m "Client: load models from binaries, ship no OBJs in the jar"
```

---

### Task 5: Document and hand off for in-game measurement

**Files:**
- Modify: `AGENTS.md`
- Modify: `BINARY_MODEL_PLAN.md` (tick checkboxes)

**Interfaces:**
- Consumes: all of the above.
- Produces: accurate documentation; no code API.

- [ ] **Step 1: Write the doc bullet**

In the wheel/OBJ section, record: FRRB v1 layout and per-corner records, converter command, why centring is converter-side, why material id is in `data4.x`, that `.obj` is excluded from the jar, and that a resource pack overrides `.bin`. Note the supplied 177,984-corner / 4–11 ms measurement as rationale.

- [ ] **Step 2: Commit and report**

```bash
git add AGENTS.md BINARY_MODEL_PLAN.md
git commit -m "Docs: binary model loading notes"
```

Report to the user: launch `runClient`, compare first-render stall before/after from the log, and confirm the wheel/sword look unchanged.
