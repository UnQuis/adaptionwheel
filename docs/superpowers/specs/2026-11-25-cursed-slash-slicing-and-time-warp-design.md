# Cursed-slash slicing + Time Warp ability — design (1.21.1)

Date: 2026-11-25. Branch: `1.21.1` (Minecraft 1.21.1 / NeoForge 21.1.248).

## Intent

Two independent features, both described in the untracked specs under `docs/`:

1. **Slicing is not an ability.** When a cursed-energy slash from the Sword of Extermination hits a
   mob, the mob is cut into two halves: the server decides the cut authoritatively and removes the
   original; every client builds the two halves locally from the still-living entity's mesh. This
   is the `mob-slicing.md` port, adapted from a laser sweep to a projectile slash.
2. **Time Warp is an ability.** It joins the existing one-slot active-ability system
   (`ActiveAbilities` + `AbilityDispatch`), chosen in the menu and activated with the existing R
   key. No new keybind, no new selection packet. It is unlocked by default for now; a real
   adaptation gate will be added later. This is the `CameraLenta` part of `time-slowdown.md`.

## Goals

- A cursed slash visibly bisects a mob, with loot/XP and no duplicated entity.
- Time Warp slows the world inside a radius while the wearer moves normally; movement is smooth
  (partialTick substitution), damage and explosions are buffered until the zone ends.
- Player behavior outside these two features is unchanged. No conflict with the orbital wheel skin
  work already committed in `a36dfc1`/`2a83789`.

## Non-goals / anti-goals

- No telekinesis dismemberment, no moon fragments, no acceleration (`H`) and no rewind (`G`).
- No new server entities for the halves; no NBT cloning of the cut mob.
- No gameplay immortality: slicing must not grant the target any wheel adaptation effects.
- Time Warp does not change `DimensionType` game speed, input replay, or chunk ticking.

## Constraints / decisions already made

- Server is authoritative for the cut; clients only render halves. `Player` entities are never cut,
  only damaged as today. Multipart entities are excluded.
- The cutting plane comes from the slash's swept segment (previous position → current position)
  and its flight direction, following the document's triangle/frac math rather than the mob's yaw.
- `frac` is computed from the 8 AABB corners and gated by a configurable window (default 0.15–0.85).
- A cut mob drops loot and XP **before** being discarded; the equipped wheel is not duplicated.
- Time Warp defaults: speed 0.2×, radius 64 blocks, duration 120 s, cooldown 10 s. Owner excluded.
- Time Warp uses the existing R activation path, not the document's X key.

## Approaches (approved)

### Slicing — approach A (full port)

Server: on `CursedSlashProjectile.onHitEntity`, build the plane and `frac`, send a `SlashCutPayload`
(point/normal/sweep/entity id), then drop loot/XP and `discard()` the mob.

Client: capture the mob mesh with access mixins (ModelPart / EntityRenderDispatcher / RenderType
state), clip quads against the plane, interpolate cut edges, build cut caps as a convex hull,
simulate two halves with gravity/collision/fade as a client-only effect, hide the original for a
short time. This is the faithful port; the cheaper "two slash visuals + delete" option was rejected
because it is not actually slicing.

### Time Warp — approach A (full port)

Server: logical millisecond clock, tick decimation on `ServerLevel.tickNonPassenger`, delayed
`ServerEntity.sendChanges`, fractional `dayTime` compensation, buffered `LivingAttackEvent` and
explosions applied when the zone ends.

Client: same decimator from state packets, `partialTick` override, entity/particle render hooks,
sound pitch/volume plus low-pass filter. The simpler "give nearby mobs Slowness" option was
rejected because it would not decimate AI/ticks and would not feel like time slowdown.

## Builder scope

- Slicing: new payload + networking registration, slash hit handler changes, capture/clip/cap/sim
  client package, 3–5 mixins, config keys and lang keys, gametests for server-side cut/drop.
- Time Warp: `ActiveAbility` definition + `AbilityDispatch.register`, server state machine and
  packets, mixins, client interpolation/sound, config keys, HUD/menu text via the existing ability
  card, tests for unlock/activation/tick decimation math.

Order: Time Warp first (self-contained, no new render substrate), then slicing (needs the mesh
capture work). Each lands as its own commit with build + 207+ gametests green.

## Open for the builder

- Exact config key names and defaults, unless already implied above.
- Which of the document's 15 slicing gotchas to port in the first pass (target: all that affect
  visible correctness).
- Test placement and any public test-only accessors needed for the clock math.
