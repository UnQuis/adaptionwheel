# Port notes: NeoForge 1.21.1 → Minecraft 26.3 / NeoForge 26.3.0.x-beta

This branch (`26.3`) is the same mod as `main`, reworked for Minecraft 26.3.
Toolchain: Java 25, NeoGradle 7.1.39, Gradle 9.2.1, NeoForge `26.3.0.4-beta`
(mixin 0.8.7 / MixinExtras 0.5.4 provided by NeoForge). CI builds with Temurin 25.

## Dependencies

* **Curios is optional.** Curios has no 26.3 release, so it is no longer a compile
  dependency. `compat/CuriosCompat` binds the Curios API through `MethodHandle`s /
  a `Proxy` `ICurioItem` only when the `curios` mod is loaded (API shape of the Curios
  `26.x` branch: `CuriosApi.registerCurio`, `CuriosApi.getCuriosInventory`,
  `ICuriosItemHandler.findFirstCurio`, `SlotResult.stack`, `SlotContext.identifier`).
  `compat/WheelSlots` is the single place that decides where the wheel is "worn":
  Curios `wheel` slot when Curios is present, otherwise off-hand or any main-inventory
  slot. The Curios datapack files (`data/adaptionwheel/curios/**`, `data/curios/tags/**`)
  are kept and are simply ignored without Curios.
* `neoforge.mods.toml`: no `modLoader`/`loaderVersion` (FML 12), explicit `minecraft`
  dependency, `curios` is `type="optional"`.
* `pack.mcmeta` uses `min_format`/`max_format` (97 resources … 121 data).

## Vanilla / NeoForge API changes handled

| Area | 1.21.1 | 26.3 |
|---|---|---|
| Ids | `ResourceLocation` | `Identifier` (`fromNamespaceAndPath`, `withDefaultNamespace`) |
| `Level.isClientSide` | field | `isClientSide()` |
| Items | `SwordItem`/`Tiers`, `Unbreakable` component, `UseAnim` | `Item.Properties.sword(ToolMaterial…)`, `DataComponents.UNBREAKABLE`+`Unit`, `ItemUseAnimation`; `DeferredRegister.Items.registerItem(name, ctor, Supplier<Properties>)`; `appendHoverText(stack, ctx, TooltipDisplay, Consumer, flag)`; `use` returns `InteractionResult` |
| Creative tabs | `CreativeModeTabs.COMBAT` | keys are private → `ResourceKey.create(Registries.CREATIVE_MODE_TAB, "minecraft:combat")`; custom tab via `CreativeModeTab.builder(Row, int)` |
| Entities | `EntityType.Builder.build(String)`, `CompoundTag` save data, `getBoundingBoxForCulling` on the entity | `build(ResourceKey)`, `ValueInput`/`ValueOutput` (`getFloatOr`, …), culling box override moved to `EntityRenderer.getBoundingBoxForCulling(entity, partialTicks)` |
| Damage | `hurt(source, dmg)`, `knockback(str, x, z)` | `hurtServer(level, source, dmg)`, `knockback(str, x, z, source, dmg)`; `killedEntity(level, target, source)`; `Entity.hasImpulse` → `syncVelocity`; `invulnerableTime` is private (`setInvulnerableTime`); `fallDistance` is `double` |
| Loot | `Entity.getLootTable()` key | `Optional<ResourceKey<LootTable>>` |
| Registries | `BuiltInRegistries.X.get(id)` value | `getValue(id)` (`get` now returns `Optional<Holder>`) ; `EntityType.is(tag)` → `builtInRegistryHolder().is(tag)` |
| Attachments | `AttachmentType.builder().serialize(Codec)` | `serialize(MapCodec)` |
| Player | `displayClientMessage(c, true/false)` | `sendOverlayMessage` / `sendSystemMessage`; `ServerPlayer.server` private → `level().getServer()` |
| Commands | `s.hasPermission(2)` | `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` |
| Events | `BlockEvent.BreakEvent`, `AnvilUpdateEvent.setCost` | `event.level.block.BreakBlockEvent`, `setXpCost(int)`; `PacketDistributor.sendToServer` → `ClientPacketDistributor.sendToServer` |
| Keybinds | string category, GLFW key codes | `KeyMapping.Category` record + `RegisterKeyMappingsEvent.registerCategory`; lang key `key.category.<ns>.<path>`; **26.3 uses SDL3 scancodes** (`InputConstants.Type.KEYBOARD`, `InputConstants.KEY_K`) — GLFW is gone |
| FML | `FMLLoader.getLoadingModList()` static, `FMLEnvironment.dist` | `FMLLoader.getCurrent().getLoadingModList()`, `FMLEnvironment.getDist()` |
| GUI | `GuiGraphics` (`drawString`, `renderOutline`, `pose()` = `PoseStack`), `Screen.render`, `mouseClicked(x, y, button)`, `Minecraft.screen/setScreen` | `GuiGraphicsExtractor` (`text`, `outline`, `pose()` = `Matrix3x2fStack`), `Screen.extractRenderState`, `mouseClicked(MouseButtonEvent, doubleClick)`, `Minecraft.gui.screen()/setScreen()`, `isInGameUi()` for transparent background; immediate-mode `Tesselator`/`RenderSystem` quads → `blit(RenderPipelines.GUI_TEXTURED, …, color)` |
| Mouse buttons | GLFW numbering: **0** left, 1 right, 2 middle | **`MouseButtonEvent.button()` is 1 left, 2 middle, 3 right** (matches `InputConstants.Type.MOUSE`: `key.mouse.left` = 1, `key.mouse.right` = 3). A ported `event.button() == 0` left-click test never fires, and for a container screen the click then falls into `super.mouseClicked`, which returns `true` unconditionally — silent. Use `ru.adaptionwheel.client.MouseButtons.isLeft(event)` |
| Entity rendering | `EntityRenderer<T>.render(entity, …, MultiBufferSource)` | `EntityRenderer<T, S extends EntityRenderState>` with `createRenderState`/`extractRenderState`/`submit(state, pose, SubmitNodeCollector, camera)`; custom quads via `submitCustomGeometry`; `RenderTypes.entityTranslucent/entityCutout`; `LightCoordsUtil.FULL_BRIGHT`; `PoseStack.rotate(Quaternionfc)` / `rotate(Axis, float)` replace `mulPose`; `camera.orientation` instead of `entityRenderDispatcher.camera.rotation()` |
| Wheel above head | `RenderLivingEvent.Post` had the entity | render-state based: `RegisterRenderStateModifiersEvent.registerAvatarEntityModifier` stores "wearing" + world time on the `AvatarRenderState`, `RenderLivingEvent.Post<?,?,?>` reads them and `submitModelPart`s the model |

## Mixins

| Mixin | Change |
|---|---|
| `*BlockMixin.entityInside` | new signature `(BlockState, Level, BlockPos, Entity, InsideBlockEffectApplier, boolean)` |
| `FrictionMixin` | friction call moved from `travel` to `travelInAir`; still wraps NeoForge's `BlockState.getFriction(LevelReader, BlockPos, Entity)` |
| `SlimeBlockMixin` | `updateEntityAfterFallOn` no longer exists; bounce is generic restitution → new `SlimeBounceMixin` wraps `Entity.getBlockBounciness(BlockPos, BlockState)` inside `restituteMovementAfterCollisions` and returns 0 for adapted players on slime |
| `ShieldDisableMixin` | `Player.disableShield()` is gone; injects into `Player.blockUsingItem(ServerLevel, LivingEntity, DamageSource, float, boolean fullyBlocked)` and keeps only the `LivingEntity` knockback part |
| `HurtCamMixin` | `GameRenderer.bobHurt(CameraRenderState, PoseStack)` |
| `SpeedFactorMixin`, `AttackCooldownMixin`, `LivingEntityTickerAccessor`, `GuardianLaserMixin` | unchanged (targets verified against 26.3) |

## Verification

The port was checked by compiling every source file against the real 26.3 server jar
plus NeoForge/FML/Mixin built from the `26.3` branches, and by verifying each mixin
target in the patched 26.3 decompile. The Gradle build itself runs in CI.
