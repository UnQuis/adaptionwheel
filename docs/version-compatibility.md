# Version Compatibility Audit (1.12 → current)

Scope: what it would take to port each layer of the Adaption Wheel to other Minecraft versions,
which vanilla mechanics exist since when, and how old APIs map to the ones used here.
**The project targets NeoForge 1.21.1 and stays buildable there** — this document is an architectural
audit, not a promise of multi-version builds.

## 1. Layer separation in the codebase

| Layer | Packages | Portability |
|---|---|---|
| Core domain | `category.Concepts`, `adapt.*`, `data.AdaptionTask`, task/lifecycle logic inside `server.AdaptionEvents` | High. Concept strings, level tables, timers, combo rules are plain Java. Only needs a codec library equivalent on ancient versions (NBT instead of DFU). |
| Persistence schema | `data.PlayerAdaption.CODEC`, `WheelData.CODEC` | Medium. DFU Codecs exist since 1.14.4/1.15; before that the same records map to manual NBT read/write. Field names are the compatibility contract. |
| Platform events | `server.AdaptionEvents`, `MovementTriggers`, `AdaptionCommand` | Low per version. Event names/packages moved every few majors (see §3). Logic is thin glue over core helpers. |
| Physics mixins | `mixin.*`, `SurfaceAdaptations` | Low. Injection targets follow Mojang mappings and method moves; the guarded helper calls port unchanged. |
| Networking | `network.*` | Low. Transport changed completely across versions; payload *contents* are version-independent. |
| Rendering/GUI | `client.*` | Low. `GuiGraphics` (1.20+), `Screen`, `KeyMapping`, render types all shifted repeatedly. |

Rule already enforced by the codebase: **core logic never imports platform classes beyond its own adapter file**
(e.g. `SurfaceAdaptations` is the only entry mixins may call; client state is touched only through `ClientChecks`
so dedicated servers never load client classes).

## 2. Mechanism availability by version (what adaptations can target)

| Mechanic | Since | Detection/effect hook used today |
|---|---|---|
| Soul sand slowdown | 1.0 | `Block.getSpeedFactor()` (property-driven since ~1.14; before: hardcoded in `Entity.move`) |
| Ice/slime friction, cobweb | 1.0 | friction call + `SlimeBlock.stepOn/updateEntityAfterFallOn` |
| Water/lava swimming dynamics | 1.0 | travel math (lava constants stable since 1.13 fluid rewrite) |
| Bubble columns | 1.13 | `BubbleColumnBlock.entityInside` |
| Sweet berry bush | 1.14 | `SweetBerryBushBlock.entityInside` |
| Honey block | 1.15 | speed factor + jump factor + slide |
| Powder snow / freezing | 1.17 | `PowderSnowBlock.canEntityWalkOnPowderSnow/entityInside`, freeze ticks |
| Sculk darkness | 1.19 | `Debuff_darkness` via generic debuff system |
| `is_*` damage-type tags | 1.19.4 (data-driven damage types) | category matching |
| Mace smash | 1.21 | `PLAYER` category id list |
| `SUBMERGED_MINING_SPEED` attribute | 1.21 (Aqua-Affinity mechanism exposed) | Env_Liquid mining parity |
| Jump-strength/gravity attributes | 1.20.5 | used indirectly by lava-swim conversion |

Older targets simply drop those rows: e.g. a 1.12 port covers fall/fire/drown/starve/knockback/ice/slime/cactus
but not honey/powder-snow/berry/bubble columns.

## 3. API evolution map (old way → this project)

| Concern | 1.12.x (Forge) | 1.16.x | 1.19.x | 1.20.x | 1.21.x (NeoForge, this project) |
|---|---|---|---|---|---|
| Damage source identity | `DamageSource` subclass + `isProjectile()` etc. | same | **registry `minecraft:damage_type`** (1.19.4), JSON + tags | tags stable | `source.typeHolder().getRegisteredName()` + `TagKey<DamageType>` matching (memoized) |
| Incoming-damage interception | `LivingHurtEvent`/`LivingAttackEvent` | same | same | `LivingIncomingDamageEvent` splits attack/hurt | `LivingIncomingDamageEvent` (cancel ⇒ no red flash) + `LivingDamageEvent.Pre` (modify) |
| Knockback | velocity hacks | `LivingKnockbackEvent` (Forge) | same | rename | `LivingKnockBackEvent.setCanceled` |
| Potion immunity | `PotionEvent.PotionApplicableEvent` | same | same | `MobEffectEvent.Applicable` → `Result.DO_NOT_APPLY` | same as 1.20 |
| Effect iteration | `PotionEffect` | `MobEffectInstance` | same | same | `player.getActiveEffects()` snapshot copy |
| Item-borne data | capability + NBT | same | same | **1.20.5 Data Components** | `WHEEL_DATA` component with `Codec` |
| Per-player persisted data | `CapabilityICapabilitySerializable` | same | same | NeoForge **attachments** (1.20.4+) | attachment + `copyOnDeath` |
| Networking | `SimpleNetworkWrapper` | same | SimpleChannel | **payloads** `playToClient/playToServer` + `StreamCodec` | same as 1.20.5+ |
| Attribute modifiers | UUID-based `AttributeModifier` | UUID | UUID | **ResourceLocation-id modifiers**, `ADD_VALUE/ADD_MULTIPLIED_BASE` ops, `removeModifier(RL)` | same (permanent modifiers survive login — required by our HP handling) |
| Underwater mining penalty | hardcoded ÷5 + Aqua Affinity check | same | same | same | ÷5 still hardcoded while swimming → `BreakSpeed ×5` compensation; base penalty now also an attribute |
| Block speed/jump factors | hardcoded in `Entity.move` | property-driven | same | same | `WrapOperation` on `Block.getSpeedFactor()/getJumpFactor()` inside `Entity.getBlockSpeedFactor()/getBlockJumpFactor()` |
| Commands | `CommandBase` | Brigadier | Brigadier | same | `RegisterCommandsEvent`, brigadier tree |
| Client HUD | custom `RenderGameOverlayEvent` drawing | same | same | `GuiGraphics` | `RenderGuiEvent.Post` + `GuiGraphics.fill/drawString/scissor` |
| Keybinds | `ClientRegistry.registerKeyBinding` | same | `RegisterKeyMappingsEvent` | same | same |

## 4. Porting recipe per era (if ever attempted)

- **1.12–1.16**: replace Codecs with manual NBT; replace payloads with `SimpleNetworkWrapper`; damage categories
  become `instanceof`/id-string matchers (no tags); drop powder-snow/honey/berry/bubble rows; mixins retarget
  SRG names — expect every mixin signature to change; GUI rewritten without `GuiGraphics`.
- **1.17–1.19**: Codecs OK (1.15+); damage-type registry only ≥1.19.4 — keep fallback matcher below that;
  networking still SimpleChannel; attachments must be re-expressed as capabilities (<1.20.4).
- **1.20.x**: closest siblings. 1.20.1 (legacy Forge-style NeoForge) differs mainly in payload/attachment APIs;
  1.20.4 adds attachments; 1.20.5 adds data components + RL attribute modifiers.
- **1.21.x**: this project. Minor bumps within 1.21 need mostly event-name/mapping attention
  (e.g. 21.1 renamed `LivingHurtEvent` → pipeline split).
- **Future ("26.x")**: unknown by construction. The mitigation is the layer table in §1: keep concept model,
  tables, task lifecycle and trigger predicates in pure Java so a port is glue-code replacement, not redesign.

## 5. Deliberate single-version decision

Maintaining true multiloader/multiversion builds requires separate platform modules (shared `api`/`core`
sourcesets + per-version `platform` projects, MultiLoader-template style). For a content mod of this size the
maintenance cost outweighs the benefit; instead the repo keeps strict package boundaries documented above so a
future extraction is mechanical. Nothing in `category`/`adapt`/`data` depends on NeoForge-specific behavior.
