# Wheel skin: Orbital Ring as a selectable second skin (1.21.1)

Date: 2026-11-21. Status: **design — awaiting approval**. No product code is written until this is approved.

## Intent

`~/Blender/Ready/OrbitalRing.blend` is to become a real second skin of the Mahoraga Wheel, selectable from the adaptation menu. "Full skin" means: the ring's own mesh, its own textures, its animated emissive and its own material responses, drawn through a real shader — not a recoloured Dharma Chakra. The choice must live **on the wheel item** so every player sees the same skin on the same wheel and it travels when the wheel is given away.

Why: the player wants visual customization of the wheel that survives trading and is visible to other players, not a private client-side preference.

## Goals

- Two skins: `dharma` (current, default) and `orbital`.
- The choice is stored on the item, synced by the vanilla data-component mechanism, and read for **every** wearer, local or remote.
- The orbital skin has a metal shell, a fabric/rubber shell and an animated cyan emissive shell, all drawn in **one** draw call through the existing `wheel_metal` program extended for the purpose.
- All existing wheel behaviour (detent spin, growth, bob, world lightmap, sky/torch reflections, darkness glow) keeps working for both skins.
- The CPU-lit fallback (`visual.wheelMetalShader = false`) still draws the orbital mesh, degraded but present.

## Non-goals / anti-goals

- No 26.3 port in this phase. Its renderer uses `RenderPipelines`+bind groups (no JSON shaders, no env/torch uniforms) and needs a separate design and implementation.
- No light emitted into the world by the emissive ring. It is an emissive *surface*, not a light source.
- No `Earth_*_6K` / `stars` images: all five have `users = 0` in this file and belong to the unused Planet variant.
- No decimation: the ring costs slightly fewer triangles than the shipped wheel.
- No config toggle for the skin. Selection is per item, from the menu.
- No third skin, no skin colour picker, no resource-pack API.

## Constraints / measured facts

- `WheelData` has 7 codec fields and is rebuilt from `PlayerAdaption` by `AdaptionEvents.saveToStack` on equip/unequip/swap and on a 1 Hz tick. **A skin field inside `WheelData` would be silently reset to default** (`server/AdaptionEvents.java:430-436,1540-1568`). Therefore: separate component.
- `ModDataComponents` pattern: `SWORD_MODE` = `.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL)` (`data/ModDataComponents.java:25-31`). Network-synced components on equipped Curios stacks are propagated by Curios' own stack sync — the project already relies on exactly this for `WHEEL_DATA` (`client/AdaptationWheelScreen.java:500-507`: "Curios 9.5.1 copies the stack after comparing it in the same tick, so writing WHEEL_DATA onto an equipped stack is picked up and synced without a custom packet"). **Decision: no new server→client payload and no echo in `AdaptionSyncPayload` in this phase**; if Curios changes, a follow-up adds the echo at the single read site.
- `MahoragaWheelItem.equippedWheel(LivingEntity)` (`item/MahoragaWheelItem.java:30-48`) reads the dedicated Curios slot directly and is already used by `WheelRenderer` for remote players (`adaptCountFor`, `darknessGlowFor`). Server writes must go through the `AdaptionEvents.getWheelStack` facade.
- `ObjMesh.STRIDE = 11` (`x,y,z,u,v,nx,ny,nz,r,g,b`); `o`/`usemtl`/`mtllib` are ignored. The shipped `dharma_chakra.obj` has **no** 7-token `v` lines, so the colour slots are all white and free for reuse (`client/ObjMesh.java:40-44,152-159,175-177`).
- The shader path currently *discards* mesh vertex colour and writes a constant tint (`client/WheelRenderer.java:542`); the CPU path uses it (`:645-647`). This must be changed together.
- `wheel_metal.json` declares one sampler (`Sampler0`); `GameTime` works with no Java setter if and only if declared in JSON (verified against `slash.json`). `Uniform.uploadAsFloat()` accepts counts 1–4 only, so new vec4 uniforms must be separate named uniforms.
- The ring's emissive is **procedural**, not an image: `Sci Fi Flowing Square Fx` uses Voronoi F2 scale 5 / speed −2 cells/s and F1 scale 20 / speed −6 cells/s, bands 0.88–0.90 and 0.20–0.25, strengths 5 and 10, colours blue→cyan and violet→cyan. There is **no** emissive texture in the file; `Earth_Illumination_6K` has `users = 0`.
- Geometry (headless measurement): three visible toruses only — `Torus` 2500 quads (Hammered Dark Metal, metallic 1 / rough 0.4, no images), `Torus.001` 768 quads (emissive), `Torus.002` 1440 quads (fabric: 4 packed 1024² JPEGs). Export with material ids: 4708 `v`, 9416 triangles, ~910 KB. `BézierCircle` + four `Cube*` are GeometryNodes instance sources (~43k realised tris) and must **not** be exported.
- Blender's OBJ exporter applies **sRGB encoding** to vertex colours: write `srgb_to_linear(id)` to get exact exported ids. The active UV layer is `automap` (range about −9..10, 19× tiling) — keep it; `UVMap` is a 0..1 unwrap that would stretch the fabric.
- The ring's in-game size needs no adjustment: `WheelRenderer` scales by `mesh.radius()`, and the ring's XZ radius (~9.46) is close to the shipped wheel's (~0.66) only in *shape*, not in number — the scale divisor handles either.

## Approaches considered

1. **One shader, material id in vertex colour, emissive in GLSL (chosen).** One OBJ with ids 0/0.5/1 in the red channel; one extra fabric sampler; animated Voronoi in the shader. Keeps all sky/torch reflections and needs no atlas or UV rewrite. Cost: shader edit + custom export.
2. **Albedo atlas with remapped UVs.** Bake all three shells into one texture atlas. Simpler shader, but two shells lose texture detail at this scale, and the emissive becomes either a static scroll or a flipbook — less faithful and more asset work.
3. **Three OBJs / three shaders, one per material.** No parser or shader change but three draw calls and three uniform setups per wheel; the 24 existing light uniforms would be duplicated. Rejected.

## Design

### Storage and intent

- Add `ModDataComponents.WHEEL_SKIN`: `DataComponentType<String>`, `.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING)`. Absent = `dharma`. Accepted values exactly `dharma` and `orbital`.
- Add `network/SetSkinPayload.java`: `record SetSkinPayload(String skin) implements CustomPacketPayload`; `TYPE` id `adaptionwheel:set_skin`; `STREAM_CODEC = StreamCodec.of(buf -> buf.writeUtf(...), buf -> new SetSkinPayload(buf.readUtf()))`; `send()` via `PacketDistributor.sendToServer`.
- Handler: reject ids outside the whitelist; `AdaptionEvents.getWheelStack(player).ifPresent(stack -> stack.set(WHEEL_SKIN.get(), skin))`; no reply packet. Register in `AdaptionNetworking` `playToServer` beside `ToggleAdaptationPayload`.
- Both `mahoraga_wheel` and `mahoraga_wheel_wood` are valid targets (`MahoragaWheelItem.isWheel`), and both already render the 3D wheel (`WheelRenderer.isWearingWheel` uses `equippedWheel != null`). **Decision:** the skin is active on the wooden wheel too, so the menu row is not disabled for it. `AdaptionEvents.onAnvilUpdate` builds a **new** `ItemStack` for the upgrade (`:2770-2781`), so it must copy `WHEEL_SKIN` from whichever input is the wheel — otherwise a chosen skin silently reverts on upgrade.

### Menu

- In `AdaptationWheelScreen.drawLeftCard`, between the stats (y+174..200) and the hairline (y+216), add a `SKIN` row: two small labelled buttons `DHARMA` / `ORBITAL`, using `WheelUi.fillRounded` + `WheelUi.text` like the mode tabs; record their `Rect`s in a new `List<Rect>` cleared on each draw; handle them in the same `mouseClicked` path as the mode tabs (`AdaptationWheelScreen.java:2468`).
- Current value is read from `MahoragaWheelItem.equippedWheel(Minecraft.getInstance().player)` (component may be absent ⇒ `dharma`). If the player is not wearing a wheel the row is drawn disabled and clicking it does nothing.
- Lang keys in both `en_us.json` and `ru_ru.json`: `adaptionwheel.gui.menu_skin`, `adaptionwheel.gui.menu_skin_dharma`, `adaptionwheel.gui.menu_skin_orbital`.
- Compact/mini left-card layouts: the row is omitted when there is no room; no new layout branch.

### Asset export (scratch, reproducible script)

- Work on a **copy** of the `.blend` in `/mnt/Barracuda/tmp/opencode/orbital/`; never modify the user's live scene.
- Select only `Torus`, `Torus.001`, `Torus.002`; transforms are identity. Add a `POINT` `FLOAT_COLOR` attribute `wheel_mat` to each with the pre-compensated value `srgb_to_linear(id)`: `Torus = 0`, `Torus.001 = 0.5`, `Torus.002 = 1.0`.
- Export with `bpy.ops.wm.obj_export`: `export_selected_objects=True`, `export_materials=False`, `export_uv=True`, `export_normals=True`, `export_triangulated_mesh=True`, `export_colors=True`, `forward_axis='NEGATIVE_Z'`, `up_axis='Y'`. Keep `automap` as the active render UV.
- Ship `assets/adaptionwheel/models/entity/orbital_ring.obj` (~910 KB) and `assets/adaptionwheel/textures/item/orbital_fabric.png` (256², extracted from the packed `full_black_Fabric_019_Base_Color.jpg` with `magick`; Pillow is broken on this machine).
- Header inside the OBJ documents the id encoding, so a future re-export cannot silently drop it.

### Mesh + renderer

- `WheelRenderer`: replace the single `wheelMesh` with an immutable holder of both meshes; one `WheelMeshReloadListener` loads both in `prepare` and publishes the holder atomically in `apply`; log each failure separately (the current one-shot flags cannot report the second skin).
- Add `skinFor(entity)`: read `WHEEL_SKIN` from `MahoragaWheelItem.equippedWheel(entity)` for **both** local and remote players, default `dharma`. Selecting a mesh happens before `scale = wheelSize / mesh.radius()`; growth, bob, spin and tint stay skin-agnostic.
- `drawMeshShader(mesh, …, skinId)`: bind `Sampler0` to the skin's albedo (`DharmaChakraModel.TEXTURE` for dharma; a 1×1 dark fallback or the fabric texture for orbital) and `Sampler1` to `orbital_fabric`. Set `WheelSkin.x` (0/1) and `WheelTint.rgb`. Pass `corners[o+8..10]` through as `vColor` instead of the constant red/green/blue; move the adaptation tint into `WheelTint`.
- CPU fallback `drawMesh`: select the same mesh and bind the skin's texture; keep the existing Blinn-Phong shading. **Decision:** do not feed the orbital mesh's vertex colour straight into `setColor` (`client/WheelRenderer.java:645-647`) — red carries the material id, so the three shells would draw black, dark grey and red. Decode it instead: `id 0 → vec3(0.35)`, `id 0.5 (emissive) → vec3(0.05,0.80,1.00)`, `id 1 → vec3(1.0)`. No animated emissive in this path by design; the ring must still be visibly present when the shader is off.
- Keep `WheelLights` unchanged. Keep `disableCull()` in `finally`, the `TRIANGLE_CORNERS` re-triangulation, the `Position - WheelCamera` convention and `WheelProj = projection × modelView` unchanged.

### Shader (`wheel_metal.json/.vsh/.fsh`)

- JSON: add `Sampler1`, `GameTime` (float, count 1), `WheelSkin` (float vec4, count 4) and `WheelTint` (float vec4, count 4). Keep all 16 light uniforms and their names byte-identical.
- Fsh: introduce `int mat = int(vColor.r * 2.0 + 0.5)`; for `WheelSkin.x < 0.5` keep the existing albedo/corrosion/f0 code exactly as-is (with `WheelTint` replacing the old constant tint). For `WheelSkin.x > 0.5`: `albedo = mat == 2 ? fabric.rgb : vec3(0.035)`; skip the corrosion perturbation (the ring is clean sci-fi metal); use `f0 = albedo` and `rough` from a material-specific uniform/branch (metal ~0.4, fabric ~0.8).
- Emissive only on `mat == 1`: 2D Voronoi on wheel-local `q = (worldPos - WheelCentre) / wheelRadius`, F2 scale 3, F1 scale 12, time offsets −0.67 and −0.5 per second, bands 0.88–0.90 and 0.20–0.25, colours blue→cyan and violet→cyan, strengths clamped to ~1.2 and ~2.5 before ACES. Reuse the existing `hash13` for cell hashes; a 2D 9-neighbour Voronoi is enough. Gate the whole term by the shell id so it cannot leak onto metal/fabric.
- The existing darkness `WheelGlow` term stays separate and applies to both skins. Do not conflate the two emissions.
- `glslangValidator` must compile both stages (copy `.vsh`/`.fsh` to `.vert`/`.frag` first) before any in-game test.

### Verification and tests

- Extend `ObjMeshTests` with the orbital OBJ: parses, ≥9000 triangles, material ids present at 0/0.5/1, positive radius, no NaN normals; keep the existing wheel test unchanged.
- Add a small gametest for the component: absent ⇒ `dharma`, `set("orbital")` round-trips, an unknown id is rejected by the **handler** (test the whitelist helper, not the packet).
- `./gradlew build`, `tools/validate-shaders.sh`, and `tools/run-gametests.sh` (with its timeout/flake handling) must pass; note the suite is currently 198 tests.
- In-game check by the user: switch skins in the menu, relog/trade the wheel to another player, toggling `visual.wheelMetalShader` off, viewing another player's wheel.

## Decisions already made (builder may treat as fixed)

- Separate component, not a `WheelData` field.
- One shader program, ids in vertex colour, GLSL emissive (no baked emissive texture, no atlas, no third OBJ).
- Menu row in the left card, two buttons, server-authoritative intent packet.
- 1.21.1 only in this phase.
- No server→client echo in this phase (Curios stack sync is the transport, as it already is for `WHEEL_DATA`).
- CPU fallback decodes the id into three flat tints instead of using the raw vertex colour.
- The skin works on the wooden wheel and is copied through the anvil upgrade.

## Left to the builder

- Exact shader code for the Voronoi bands and material branch; exact pixel geometry of the two buttons.
- Whether to bind a 1×1 dark texture or the fabric as `Sampler0` for the orbital skin in the shader path (choose whichever is shorter; the fabric is unused on non-fabric shells either way).
- Whether the whitelist helper lives in `network/SetSkinPayload` or a small `WheelSkins` utility — keep it in one place.

## Risks

- Vertex-colour ids are sRGB-encoded by Blender at export; an id of 0.5 must be written as ~0.2139 linear or the shell ids collapse.
- `drawMeshShader` still writing the constant tint would silently destroy the ids — pinned by a test on the shipped OBJ only; the orbital test must assert the ids survive parsing.
- A second mesh loaded with two sequential volatile writes could mix old/new assets for one frame; publish one holder atomically.
- If Curios ever stops propagating data-component changes on equipped stacks, the menu would appear to do nothing until re-equip; the fallback (explicitly out of scope now) is a small skin echo in `AdaptionSyncPayload`, added at the single read site.
- 26.3 remains behind; a port is a separate project with a different renderer architecture.
