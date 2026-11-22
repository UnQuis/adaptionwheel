# Binary model loading: replace OBJ text parsing at reload (design)

Date: 2026-11-22. Status: design — awaiting approval. No product code yet.

## Intent

A large `.obj` is parsed on the GL thread the first time the wheel is rendered, producing a 1–3 second freeze. Measurements supplied by another session for a 19.4 MB / 593k-line / 177,984-vertex OBJ: read+UTF-8 decode 14–22 ms, `split('\n')` 16–36 ms, parse loop 432–456 ms (`line.split('\s+')` alone 280 ms), PNG decode 177–194 ms plus a second decode in `Texture` 67–110 ms — 726–831 ms CPU plus GPU stalls. The fix: convert each authored OBJ once into a compact binary, ship only the binary, and read it with `ByteBuffer` in a few milliseconds.

We take this technique for our mod. The GLB experiment is cancelled; this is a separate, smaller project.

## Goals

- Replace `ObjMesh.parse(BufferedReader)` at the two reload listeners with a validated binary read.
- Convert all three shipped models after pulling PR #11: `dharma_chakra.obj`, `orbital_ring.obj` (5.5 MB / 30,548 verts / 59,328 tris), `soe.obj`.
- Bit-for-bit equivalent geometry and UVs (float drift only from ULP-level math).
- Keep the `.obj` files as editable sources in the repo, but exclude them from the jar; ship only `.bin`.
- Fail loudly on a corrupt binary **before** allocating large arrays.

## Non-goals / anti-goals

- No GLB/glTF work (cancelled).
- No change to the shader pipeline, materials, or skin selection beyond what PR #11 already has.
- No runtime OBJ fallback in the shipped jar: if a `.bin` is missing, log an error and draw the existing 25-cube fallback, not a slow text parse. (The OBJ path stays in `ObjMesh` for tests and for regeneration only.)
- No texture-decode optimisation in this phase; the measurements about `TextureData.prepare()` are a future task.

## Constraints (from the supplied format, adapted to our data)

- `FRRB v1`, little-endian. Header carries magic, version, layer count, vertex count, index/face count, group count, material count and byte count. Validate **all** of them plus `remaining()` before any allocation — a corrupt file otherwise yields an OOM up to 224 MB.
- Six layers and 14 floats per vertex in the suggested layout: pos3 + packed colour (float bits) + data4 (emissive/param/pattern/anim) + aux4 (normal.xyz/meta) + uv2. We adopt that layout verbatim for future compatibility; our current renderer consumes 11 floats (pos3, uv2, normal3, colour3), so the binary reader unpacks from the 14-float record into our existing `STRIDE = 11` corners.
- **Records are per triangle corner, not per unique OBJ position.** The text parser produces a flat normal per face and UVs per corner; a shared-vertex file cannot reproduce that without splitting on normal/UV boundaries. The supplied binary is 9,967,327 bytes for 177,984 corners × 14 floats × 4 — i.e. per-corner records. We do the same; the index layer is sequential (0..N−1) and kept for format compatibility. "Vertex count" means corner count.
- **Centring happens in the converter, once.** Vertex positions in the binary are already bbox-centred exactly as `ObjMesh.parse` produces them; metadata (centre/radius/height) is reporting-only. The runtime must NOT subtract the centre a second time — doing both shifts the model by −2×centre and breaks scale/spin.
- **Material id lives in `data4.x` at full float precision.** Packed RGBA bytes cannot represent 0.5 (decode ≈0.498/0.502), which would break the 1e-6 equivalence test; the shader buckets `int(vColor.r * 2 + 0.5)` would still work, but we refuse that fragility. `data4.x` = 1.0 for dharma/sword (white) and 0 / 0.5 / 1 for orbital; the runtime writes it into corner colour red and takes green/blue from the packed colour. `aux4.xyz` = normal, `aux4.w` = unused meta.
- Build: `sourceSets.main.resources.exclude '**/*.obj'` so the jar contains only `.bin` (plus the existing `.blend` files, which are also source art and should be excluded separately if already not shipped — builder check).
- Reload listeners: `WheelRenderer.java:715` and `SwordOfExterminationRenderer.java:318` call `ObjMesh.parse(reader)`; both become `ObjMesh.parseBinary(bytes)`.
- Branch: this worktree starts at `origin/1.21.1` (`503a683`). Step 0 is applying the 10 product commits of PR #11 (`d2ccbe8..0628fc8`); the cherry-pick has one conflict in `AdaptationWheelScreen.java` (menu changed on both branches) which must be resolved manually. Docs/plan commits stay out.

## Approaches

1. **FRRB-style binary with 14-float vertex records (chosen).** One-time Python converter, runtime direct `ByteBuffer`, unpack to our 11-float corners. Follows the supplied layout so a future geometry pipeline can reuse the file.
2. **Minimal 11-float binary.** Smaller code, but throw away the suggested layout and any future extension. Rejected: we were explicitly asked to adopt the suggestion.
3. **Serialize `ObjMesh` object via Java codec.** Fewer custom bytes but tied to our class layout and version-fragile. Rejected.

## Design

### Converter `tools/gen_model_bin.py` (stdlib only)

- Input: one or more OBJ paths, output path. Parse `v` (with optional 7-token colour), `vt`, `vn`, `f` (fan triangulate, negative indices), preserving the exact same maths as `ObjMesh.parse` (centre on bbox, radius, flat vs smooth normals per `f` line, winding `(v0,v1,v2)`).
- Write header + six layers: corner records (14 floats each), face indices (uint32), group names, material names, face→group/material mapping, and a small metadata layer (bbox centre/radius/height).
- Pack colour RGBA bytes into one float via `struct.unpack('<f', struct.pack('<I', rgba))`; decode with the reverse. Document byte order in the file header comment. The material id is NOT in those bytes — it goes into `data4.x` so 0.5 stays exact.
- Print input/output sizes and vertex/triangle counts for the log.

### Runtime `ObjMesh.parseBinary(byte[])`

- Validate header fields and `buffer.remaining() == byteCount` before allocating; throw `IOException` with the exact failed field in the message.
- Read vertex/face layers with `asFloatBuffer()` / `asIntBuffer()`; build the same `corners` array as the text parser (11 floats per corner), same centring and radius; `faceCount` from the face layer.
- Unit-test equivalence on a **small OBJ fixture kept in `src/test/resources`** (the main-resource `**/*.obj` exclusion removes shipped OBJs from the gametest classpath). Keep the existing shipped-model tests but point them at the `.bin` via `parseBinary`; add a text-vs-binary equivalence test on the fixture with 1e-6 tolerance for positions/UVs/normals and exact equality for the material id from `data4.x`. Assert counts equal.

### Build and reload wiring

- `build.gradle`: exclude `**/*.obj` from `sourceSets.main.resources`. Keep `.obj` in git as the source.
- Reload listeners try `models/entity/<name>.bin` first; on a missing/corrupt file log the path and reason, then use the existing 25-cube fallback (sword: draw nothing, as today).
- Resource-pack override: a pack can ship its own `.bin` under the same path, preserving current behaviour where a pack replaces the OBJ.

### Verification

- Python self-test in the converter: round-trip a tiny OBJ and compare floats with the existing Java parser via a gametest fixture.
- `ObjMeshTests` additions: binary parses; corrupt header (bad magic / vertex count / truncated) throws with the field named; text vs binary equivalence on a small shipped fixture.
- `./tools/validate-shaders.sh` (unchanged shaders), `./gradlew build`, `./tools/run-gametests.sh 4 150`.
- In-game: measure first-render stall before/after (log timestamps around the reload listener) and confirm the wheel/sword still look identical.

## Decisions already made

- Adopt the supplied FRRB-style 14-float layout, unpacking into our existing `STRIDE = 11`.
- Convert all three models; ship `.bin`, exclude `.obj` from the jar.
- No runtime OBJ fallback in the jar; OBJ stays only as source/test input.
- Step 0: cherry-pick PR #11 product commits and resolve the one menu conflict.

## Left to the builder

- Exact header field order and layer table (as long as all validation fields from Constraints are present).
- Whether the converter emits one `.bin` per OBJ or a single archive (choose one file per OBJ — simpler).
- How to expose the conversion command (plain `python3 tools/gen_model_bin.py <obj> <bin>`).

## Risks

- Divergence between the Python converter and the Java text parser: pinned by a float-equivalence test on a fixture and on the shipped models.
- `float` colour packing endianness must match on both sides; documented and tested with non-white colours (orbital ids 0/0.5/1).
- Excluding `.obj` from the jar means a resource pack that only overrides `.obj` stops working; document the new `.bin` override path in AGENTS.md.
- PR #11 cherry-pick conflict could hide a semantic change in the menu; review the resolved file against both sides.
