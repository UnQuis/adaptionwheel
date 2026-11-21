# Orbital Ring Wheel Skin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add `orbital` as a second, item-stored wheel skin in 1.21.1 with its own mesh, fabric texture and animated emissive, switchable from the adaptation menu.

**Architecture:** A separate network-synced `WHEEL_SKIN` data component on the equipped wheel carries the choice to every client; a single client→server intent packet writes it. `WheelRenderer` selects one of two `ObjMesh`es from that component and draws through one extended `wheel_metal` shader, where a per-triangle material id rides in the previously unused vertex-colour channel.

**Tech Stack:** NeoForge 21.1.248 / Minecraft 1.21.1, Java 21, JOML, GLSL `#version 150`, Blender 5.3 headless export, ImageMagick `magick`.

**Spec:** `WHEEL_SKIN_DESIGN.md` (committed at `a36dfc1`). Read it alongside this plan.

## Global Constraints

- Skin ids are exactly `dharma` (default when the component is absent) and `orbital`.
- `WHEEL_SKIN` must be a **separate** `DataComponentType<String>` with `.persistent(Codec.STRING)` and `.networkSynchronized(ByteBufCodecs.STRING_UTF8)` — never a `WheelData` field (`saveToStack` rebuilds `WheelData` and would erase it).
- No new server→client payload and no `AdaptionSyncPayload` field in this phase.
- Orbital material ids in vertex colour: `Torus = 0`, `Torus.001 = 0.5`, `Torus.002 = 1.0`, exported as sRGB (write `srgb_to_linear(id)`, so 0.5 → ≈0.2139 linear).
- Ship only `Torus`, `Torus.001`, `Torus.002`; keep the active `automap` UV layer; triangulate; Blender Y-up export (`forward_axis='NEGATIVE_Z', up_axis='Y'`).
- Emissive is procedural GLSL, not an image. Game-space scales: F2 3.0, F1 12.0, speeds −0.67 / −0.5 per second, bands 0.88–0.90 and 0.20–0.25, strengths ≤1.2 / ≤2.5 before ACES.
- `ObjMesh.STRIDE` stays 11; `o`/`usemtl` stay ignored; the 16 named light uniforms and their names stay byte-identical.
- The skin applies to `mahoraga_wheel` and `mahoraga_wheel_wood`; the anvil upgrade copies `WHEEL_SKIN`.
- 1.21.1 only. 26.3 is out of scope.
- Verification commands: `./gradlew build`, `./tools/validate-shaders.sh`, `./tools/run-gametests.sh 4 150` (the suite is currently 198 tests; the runner handles the known spawn hang).

## Review Focus

1. **Material id silently discarded in the shader path.** `drawMeshShader` currently overwrites vertex colour with a constant tint. If the id does not reach the fragment shader, all three shells draw as gold/black with no emissive and no error. Pinned in Task 1 (`ObjMeshTests` asserts ids survive parsing) and Task 5 (shader validation + in-game check).
2. **Wrong vertex-colour values on export.** Blender encodes vertex colours as sRGB; writing 0.5 directly yields an id of ≈0.21 after round trip, collapsing the shell classification. Pinned in Task 1 by asserting the exported OBJ contains `v ... 0.5`, `v ... 1.0` (within 0.001).
3. **Skin lost on save or upgrade.** A `WheelData` field, the 1 Hz `saveToStack`, or the new-stack anvil upgrade can erase the choice with no message. Pinned in Task 2 (component round trip + whitelist) and Task 2's anvil copy step.
4. **CPU fallback paints the ring black/red.** Feeding raw orbital vertex colours to `drawMesh`'s `setColor` makes shells black, dark grey and red. Pinned in Task 5 by decoding ids into three explicit tints; verified in-game with `visual.wheelMetalShader=false`.
5. **Dharma skin regression.** Touching the shared shader must not change the original wheel's look (corrosion, f0, reflections, darkness glow). Pinned by unchanged existing `ObjMeshTests`, full gametest suite, and a side-by-side in-game look.

---

### Task 1: Export the orbital mesh and pin its layout

**Files:**
- Create: `src/main/resources/assets/adaptionwheel/models/entity/orbital_ring.obj`
- Create: `src/main/resources/assets/adaptionwheel/textures/item/orbital_fabric.png`
- Create scratch: `/mnt/Barracuda/tmp/opencode/orbital/export_final.py`
- Modify: `src/main/java/ru/adaptionwheel/test/ObjMeshTests.java`

**Interfaces:**
- Consumes: `ObjMesh.parse(BufferedReader)` → `ObjMesh` with `triangleCount()`, `radius()`, `quadCorners()` (`STRIDE = 11`, colour at offsets 8–10).
- Produces: `/assets/adaptionwheel/models/entity/orbital_ring.obj` on the classpath; fabric texture at `assets/adaptionwheel/textures/item/orbital_fabric.png`.

- [x] **Step 1: Write the failing test** in `ObjMeshTests` as `@GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel") public static void theOrbitalWheelParsesWithMaterialIds(GameTestHelper helper)` (class already has `@GameTestHolder`/`@PrefixGameTestTemplate(false)`). Assert: resource loads; `triangleCount() >= 9000`; `radius() > 0`; all corners have finite normals; the corner colour red channel contains values near **0.0, 0.5 and 1.0** (tolerance 0.01); every green/blue channel is 0. Also re-assert the shipped dharma OBJ still has all-white colours.

```java
private static final String ORBITAL_OBJ = "/assets/adaptionwheel/models/entity/orbital_ring.obj";
// helper.assertTrue(..., "...") — GameTestHelper has no assertEquals
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./tools/run-gametests.sh 4 150`; grep for `theOrbitalWheelParsesWithMaterialIds` in `/mnt/Barracuda/tmp/adaptionwheel-gametests/`.
Expected: the new test fails with "orbital OBJ is not readable from the classpath". (A run with a missing test result is a hang — the runner retries; do not mistake it for a pass.)

- [x] **Step 3: Write the export script and export**

In `/mnt/Barracuda/tmp/opencode/orbital/export_final.py`, operating on a **copy** of `~/Blender/Ready/OrbitalRing.blend`: select only `Torus`, `Torus.001`, `Torus.002`; add a `POINT` `FLOAT_COLOR` attribute `wheel_mat` to each with linear value `srgb_to_linear(id)` for ids 0 / 0.5 / 1; keep `automap` as active render UV; call `bpy.ops.wm.obj_export` with `export_selected_objects=True`, `export_materials=False`, `export_uv=True`, `export_normals=True`, `export_triangulated_mesh=True`, `export_colors=True`, `forward_axis='NEGATIVE_Z'`, `up_axis='Y'`. Copy the result to the path above with a header comment documenting the id encoding.

Run: `/home/alexquasar/.local/share/Steam/steamapps/common/Blender/blender --background <copy> --python export_final.py`
Expected: `v=4708`, `f=9416`, `vcol_lines=4708`, three distinct red values 0.0/0.5/1.0.

- [x] **Step 4: Extract the fabric texture**

`magick '<packed full_black_Fabric_019_Base_Color.jpg>' -resize 256x256 src/main/resources/assets/adaptionwheel/textures/item/orbital_fabric.png`
Expected: 256×256 PNG; do not use Pillow (0 decoder plugins on this machine).

- [x] **Step 5: Run the test to verify it passes**

Run: `./tools/run-gametests.sh 4 150`.
Expected: `All 198+ required tests passed :)` including `theOrbitalWheelParsesWithMaterialIds`.

- [x] **Step 6: Commit**

```bash
git add src/main/resources/assets/adaptionwheel/models/entity/orbital_ring.obj \
        src/main/resources/assets/adaptionwheel/textures/item/orbital_fabric.png \
        src/main/java/ru/adaptionwheel/test/ObjMeshTests.java
git commit -m "Wheel skins: export the orbital ring with per-material vertex ids"
```

---

### Task 2: Store the skin on the item

**Files:**
- Modify: `src/main/java/ru/adaptionwheel/data/ModDataComponents.java`
- Create: `src/main/java/ru/adaptionwheel/data/WheelSkins.java`
- Modify: `src/main/java/ru/adaptionwheel/server/AdaptionEvents.java:2770-2781` (anvil)
- Modify: `src/main/java/ru/adaptionwheel/test/ObjMeshTests.java` (or a new `WheelSkinTests.java` in the same package)

**Interfaces:**
- Consumes: `ModDataComponents.COMPONENTS` `DeferredRegister`; `Codec.STRING`, `ByteBufCodecs.STRING_UTF8`; `AdaptionEvents.getWheelStack` facade.
- Produces: `ModDataComponents.WHEEL_SKIN` (`DeferredHolder<DataComponentType<String>, DataComponentType<String>>`); `WheelSkins.DHARMA`, `WheelSkins.ORBITAL`, `WheelSkins.normalize(@Nullable String) -> String`, `WheelSkins.isValid(String) -> boolean`.

- [x] **Step 1: Write the failing test**

Add `WheelSkinTests` with `@GameTestHolder(AdaptionWheel.MODID)` `@PrefixGameTestTemplate(false)` and `@GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel") public static void theSkinComponentRoundTrips(GameTestHelper helper)`. Assert on a `new ItemStack(ModItems.MAHORAGA_WHEEL.get())`: default `WheelSkins.normalize(stack.get(ModDataComponents.WHEEL_SKIN.get()))` is `dharma`; after `stack.set(ModDataComponents.WHEEL_SKIN.get(), "orbital")` the read-back is `orbital`; `WheelSkins.isValid("nope")` is false and `isValid("orbital")` is true.

- [x] **Step 2: Run the test to verify it fails**

Run: `./tools/run-gametests.sh 4 150`.
Expected: compile failure — `WHEEL_SKIN`/`WheelSkins` do not exist.

- [x] **Step 3: Implement the component and whitelist**

In `ModDataComponents`, register `WHEEL_SKIN` next to `SWORD_MODE` using the same cast pattern, `.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)`. Create `WheelSkins` with the two ids, `normalize(null) == DHARMA` and invalid ids also normalising to `DHARMA`.

- [x] **Step 4: Copy the component on anvil upgrade**

In `AdaptionEvents.onAnvilUpdate`, before `event.setOutput(...)`, capture `WHEEL_SKIN` from whichever input `isWheel(...)` and set it on the output stack. Use `WheelSkins.normalize` so an absent component does not write a redundant value.

- [x] **Step 5: Run the test to verify it passes**

Run: `./tools/run-gametests.sh 4 150`.
Expected: all tests pass, including `theSkinComponentRoundTrips`.

- [x] **Step 6: Commit**

```bash
git add src/main/java/ru/adaptionwheel/data/ModDataComponents.java \
        src/main/java/ru/adaptionwheel/data/WheelSkins.java \
        src/main/java/ru/adaptionwheel/server/AdaptionEvents.java \
        src/main/java/ru/adaptionwheel/test/WheelSkinTests.java
git commit -m "Wheel skins: item component, whitelist and anvil migration"
```

---

### Task 3: Client→server skin intent

**Files:**
- Create: `src/main/java/ru/adaptionwheel/network/SetSkinPayload.java`
- Modify: `src/main/java/ru/adaptionwheel/network/AdaptionNetworking.java`

**Interfaces:**
- Consumes: `WheelSkins.isValid` / `normalize`, `AdaptionEvents.getWheelStack(Player)`, `ModDataComponents.WHEEL_SKIN`.
- Produces: `SetSkinPayload.record SetSkinPayload(String skin)`, `TYPE`, `STREAM_CODEC`, `send(String skin)`, `handle(SetSkinPayload, ServerPlayer)`.

- [x] **Step 1: Write the failing check**

Extend `WheelSkinTests` with `@GameTest ... public static void theSkinWhitelistRejectsUnknownIds(GameTestHelper helper)` asserting `WheelSkins.isValid("dharma")`, `isValid("orbital")`, `!isValid("orbital:else")`, `!isValid("")`. This is the only part of the packet that can be tested without a mock server player.

- [x] **Step 2: Run the test to verify it fails for the missing cases**

Run: `./tools/run-gametests.sh 4 150`.
Expected: fails on `isValid("")`.

- [x] **Step 3: Implement the payload and handler**

Mirror `network/ToggleAdaptationPayload.java`: `record SetSkinPayload(String skin) implements CustomPacketPayload`; `TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "set_skin"))`; `StreamCodec.of` with `writeUtf`/`readUtf`; `send()` via `PacketDistributor.sendToServer`; `handle` returns immediately unless `WheelSkins.isValid(payload.skin())`, then `AdaptionEvents.getWheelStack(player).ifPresent(stack -> stack.set(WHEEL_SKIN.get(), payload.skin()))`. No reply packet.

- [x] **Step 4: Register it**

Add a `registrar.playToServer(SetSkinPayload.TYPE, SetSkinPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> SetSkinPayload.handle(payload, (ServerPlayer) context.player())))` line beside `ToggleAdaptationPayload` in `AdaptionNetworking`.

- [x] **Step 5: Verify compile and tests**

Run: `./gradlew build --no-daemon` then `./tools/run-gametests.sh 4 150`.
Expected: clean build; all tests pass.

- [x] **Step 6: Commit**

```bash
git add src/main/java/ru/adaptionwheel/network/SetSkinPayload.java \
        src/main/java/ru/adaptionwheel/network/AdaptionNetworking.java \
        src/main/java/ru/adaptionwheel/test/WheelSkinTests.java
git commit -m "Wheel skins: server-authoritative set_skin packet"
```

---

### Task 4: Skin switcher in the adaptation menu

**Files:**
- Modify: `src/main/java/ru/adaptionwheel/client/AdaptationWheelScreen.java` (`drawLeftCard` near y+174..216, `mouseClicked` near `:2468`)
- Modify: `src/main/resources/assets/adaptionwheel/lang/en_us.json`, `ru_ru.json`

**Interfaces:**
- Consumes: `SetSkinPayload.send(String)`, `WheelSkins`, `MahoragaWheelItem.equippedWheel(Entity)`, `WheelUi.fillRounded`/`text`, `Rect.contains`.
- Produces: two clickable `Rect`s whose list is cleared on each draw; no new public API.

- [x] **Step 1: Add the lang keys**

Add `adaptionwheel.gui.menu_skin`, `...skin_dharma`, `...skin_orbital` to both language files (English "SKIN"/"DHARMA"/"ORBITAL"; Russian "СКИН"/"ДХАРМА"/"ОРБИТАЛЬНОЕ КОЛЬЦО"). Verify with `python3 tools/check-lang.py`.

- [x] **Step 2: Draw the row**

In `drawLeftCard`'s non-compact branch, between the stats and the y+216 hairline, draw a `SKIN` label and two small rounded buttons using the mode-tab idiom (`WheelUi.fillRounded` + `WheelUi.text`, active button outlined in `GOLD`). The current value comes from `MahoragaWheelItem.equippedWheel(Minecraft.getInstance().player)` → `WheelSkins.normalize(stack == null ? null : stack.get(WHEEL_SKIN))`. If no wheel is worn, draw the row with muted colours. Store the two `Rect`s in a field cleared at the start of `drawLeftCard`; skip the row in `drawMiniLeftCard`/`drawCompactLeftCard`.

- [x] **Step 3: Handle clicks**

In `mouseClicked`, before the mode-tab loop, check the two skin rects with `contains(mouseX, mouseY)`; when a wheel is worn, call `SetSkinPayload.send(WheelSkins.ORBITAL)` or `...DHARMA` and return true. No click with no wheel.

- [x] **Step 4: Verify**

Run: `./gradlew build --no-daemon` and `python3 tools/check-lang.py`.
Expected: clean build; both lang files have identical key sets. In-game check by the user after Task 5: clicking switches the visible wheel within the same tick.

- [x] **Step 5: Commit**

```bash
git add src/main/java/ru/adaptionwheel/client/AdaptationWheelScreen.java \
        src/main/resources/assets/adaptionwheel/lang/en_us.json \
        src/main/resources/assets/adaptionwheel/lang/ru_ru.json
git commit -m "Wheel skins: switcher in the adaptation menu"
```

---

### Task 5: Render both skins through one shader

**Files:**
- Modify: `src/main/java/ru/adaptionwheel/client/WheelRenderer.java` (`wheelMesh` field `:72`, reload listener `:722-748`, `render` `:249-338`, `drawMeshShader` `:435-551`, `drawMesh` `:569-707`)
- Modify: `src/main/resources/assets/adaptionwheel/shaders/core/wheel_metal.json`, `wheel_metal.vsh`, `wheel_metal.fsh`

**Interfaces:**
- Consumes: `ObjMesh` for both models, `WheelSkins`, `ModDataComponents.WHEEL_SKIN`, `MahoragaWheelItem.equippedWheel(LivingEntity)`, `WheelLights` (unchanged).
- Produces: `private static String skinFor(LivingEntity entity)`; `drawMeshShader(ObjMesh mesh, PoseStack pose, int tint, int packedLight, LivingEntity entity, float partialTick, String skin)`; `WheelSkin` / `WheelTint` / `GameTime` shader uniforms.

- [x] **Step 1: Write the failing tint test**

Add to `WheelSkinTests` a `@GameTest ... public static void materialIdsDecodeToFallbackTints(GameTestHelper helper)`. Assert `WheelSkins.tintForMaterial(0f)` is `(0.35, 0.35, 0.35)`, `tintForMaterial(0.5f)` is `(0.05, 0.80, 1.00)` and `tintForMaterial(1f)` is `(1, 1, 1)`, with a 0.001 tolerance. These are the colours the CPU-lit fallback must use instead of feeding raw orbital vertex colours to `setColor`.

- [x] **Step 2: Run it to verify it fails**

Run: `./tools/run-gametests.sh 4 150`.
Expected: compile failure — `WheelSkins.tintForMaterial` does not exist.

- [x] **Step 3: Add the tint helper and the quad-packing regression pin**

Implement `public static float[] tintForMaterial(float id) -> float[]` in `WheelSkins`, returning the three test values by nearest id (0 / 0.5 / 1) with a white fallback. Also add `@GameTest ... theOrbitalIdsSurviveQuadPacking` to `ObjMeshTests`: parse the orbital OBJ and assert that `quadCorners()` keeps red ids 0 / 0.5 / 1 at the same corners as `corners()` (4th duplicates 3rd) — this catches a future `STRIDE` change silently dropping the ids.

- [x] **Step 4: Change the mesh holder and selector**

Replace `private static volatile ObjMesh wheelMesh` with `private static volatile WheelMeshes wheelMeshes` (a private record with `dharma()` and `orbital()`). The reload listener loads both resources in `prepare` and publishes the holder once in `apply`; each failed load logs its own path. Add `skinFor(LivingEntity)` reading `WHEEL_SKIN` off `MahoragaWheelItem.equippedWheel(entity)` (default `dharma`) — same source for local and remote players. In `render`, select `skinFor(entity)` before computing `scale = wheelSize / mesh.radius()`; keep growth, bob, spin, tint and light sampling unchanged.

- [x] **Step 5: Parameterize the draw**

In `drawMeshShader`, bind `Sampler0` to `DharmaChakraModel.TEXTURE` for dharma and to the fabric texture for orbital; always bind `Sampler1` to the fabric texture. Set `WheelSkin` to `(1,0,0,0)` or `(0,0,0,0)` and `WheelTint` from the existing adaptation tint rgb. Emit `corners[o+8..10]` through `.setColor(...)` instead of the constant red/green/blue (the original dharma OBJ is all-white, so its output is unchanged). In `drawMesh`, select the same mesh and texture; for orbital, decode red with `WheelSkins.tintForMaterial(corners[o+8])`, and use white for dharma.

- [x] **Step 6: Extend the shader**

JSON: add `Sampler1`, `GameTime` (float, count 1), `WheelSkin` (vec4, count 4), `WheelTint` (vec4, count 4); leave all 16 light uniforms untouched. Vsh: no change except passing `Color` through (it already does). Fsh: for dharma keep the existing albedo/corrosion/f0/reflection/glow code exactly as-is, substituting `WheelTint.rgb` for the previous constant tint. For orbital: `int mat = int(vColor.r * 2.0 + 0.5)`, albedo = fabric texture for mat 2 and a dark constant otherwise, skip the corrosion perturbation, roughness ≈0.4 for metal / ≈0.8 for fabric, and add the two animated Voronoi bands only on mat 1 using the numbers in Global Constraints. Keep the darkness `WheelGlow` term separate and applying to both skins; clamp emission before ACES.

- [x] **Step 7: Validate the shaders and build**

Run: `./tools/validate-shaders.sh` (copy `.vsh`/`.fsh` to `.vert`/`.frag` for `glslangValidator` as the script does) then `./gradlew build --no-daemon`.
Expected: 6 stages, 0 failed; clean build.

- [x] **Step 8: Run the full suite**

Run: `./tools/run-gametests.sh 4 150`.
Expected: `All 198+ required tests passed :)`.

- [x] **Step 9: Commit**

```bash
git add src/main/java/ru/adaptionwheel/client/WheelRenderer.java \
        src/main/java/ru/adaptionwheel/test/ObjMeshTests.java \
        src/main/resources/assets/adaptionwheel/shaders/core/
git commit -m "Wheel skins: draw the orbital ring with its own materials and emissive"
```

---

### Task 6: Document and hand off for in-game verification

**Files:**
- Modify: `AGENTS.md` (add a short bullet under the wheel-shader block)
- Modify: `WHEEL_SKIN_PLAN.md` (tick completed tasks)

**Interfaces:**
- Consumes: all of the above.
- Produces: accurate repo documentation; no code API.

- [x] **Step 1: Write the doc bullet**

In `AGENTS.md`, next to the `wheel_metal` shader bullet, record: the separate `WHEEL_SKIN` component and why; the vertex-colour material ids and the sRGB pre-compensation trap; `GameTime` + `Sampler1`; that emissive is procedural with the measured scales; that the CPU fallback decodes the ids; and that 26.3 is not ported.

- [x] **Step 2: Commit and report**

```bash
git add AGENTS.md WHEEL_SKIN_PLAN.md
git commit -m "Docs: orbital wheel skin implementation notes"
```

Report to the user exactly what to check in game: switch skins in the menu; relog; give the wheel to another player; trade upgrade from the wooden wheel; toggle `visual.wheelMetalShader` off; look at another player's wheel.
