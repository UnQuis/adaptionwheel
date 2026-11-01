package ru.adaptionwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.SurfaceAdaptations;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.data.WheelData;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.network.AdaptionSyncPayload;
import ru.adaptionwheel.network.TaskProgressPayload;
import ru.adaptionwheel.sound.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = AdaptionWheel.MODID)
public class AdaptionEvents {

    private static final String FLIGHT_MOB = "minecraft:phantom";
    private static final String FLIGHT_LEVITATION = "minecraft:levitation";

    private static final ResourceLocation HP_MODIFIER = ResourceLocation.fromNamespaceAndPath("adaptionwheel", "hp");
    private static final ResourceLocation ARMOR_MODIFIER = ResourceLocation.fromNamespaceAndPath("adaptionwheel", "armor");
    private static final ResourceLocation ADAPTATION_COUNT_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("adaptionwheel", "adaptation_count_speed");
    private static final ResourceLocation ADAPTATION_COUNT_JUMP_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("adaptionwheel", "adaptation_count_jump");
    private static final ResourceLocation SWIM_MODIFIER = ResourceLocation.fromNamespaceAndPath("adaptionwheel", "swim");
    private static final ResourceLocation LIQUID_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath("adaptionwheel", "liquid_speed");
    private static final ResourceLocation SUBMERGED_MINING_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("adaptionwheel", "submerged_mining");
    private static final ResourceLocation AQUATIC_SWIM_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("adaptionwheel", "aquatic_swim_speed");
    private static final ResourceLocation AQUATIC_SWIM_EFFICIENCY_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("adaptionwheel", "aquatic_swim_efficiency");

    private static final Map<UUID, Float> PENDING_RESPAWN_HEALTH = new HashMap<>();

    private static final Set<UUID> PENDING_RESPAWN_ARMED = new HashSet<>();

    private static final long WHEEL_GOLD_COST = 10L;

    private static final Map<UUID, long[]> WEARING_CACHE = new HashMap<>();

    private static boolean wearingWheel(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        long stamp = serverPlayer.tickCount;
        long[] cached = WEARING_CACHE.get(serverPlayer.getUUID());
        if (cached != null && cached[0] == stamp) {
            return cached[1] != 0;
        }
        boolean wearing = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(serverPlayer)
                .map(handler -> handler.isEquipped(ModItems.MAHORAGA_WHEEL.get()))
                .orElse(false);
        WEARING_CACHE.put(serverPlayer.getUUID(), new long[]{stamp, wearing ? 1 : 0});
        return wearing;
    }

    public static boolean isWearingWheel(Player player) {
        return wearingWheel(player);
    }

    public static PlayerAdaption dataOf(ServerPlayer player) {
        return data(player);
    }

    public static int conceptLevel(Player player, String concept) {
        if (!(player instanceof ServerPlayer serverPlayer) || !wearingWheel(serverPlayer)) {
            return 0;
        }
        // levelOrZero(), not level(). This feeds the break-speed and movement effects, which run on
        // BOTH sides, and the client already answers through ClientChecks.level() -> levelOrZero().
        // Reading the raw level here kept every switched-off adaptation working on the server while
        // the client stopped applying it: the two sides disagreed about the player's physics and
        // mining speed, which is the client-server split the switch exists to avoid.
        return data(serverPlayer).levelOrZero(concept);
    }

    public static Optional<ItemStack> getWheelStack(Player player) {
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(ModItems.MAHORAGA_WHEEL.get()))
                .map(result -> result.stack());
    }

    private static PlayerAdaption data(ServerPlayer player) {
        return player.getData(ru.adaptionwheel.data.AttachmentTypes.ADAPTION);
    }

    public static boolean hasAdaptation(Player player, String concept) {
        if (!(player instanceof ServerPlayer serverPlayer) || !wearingWheel(serverPlayer)) {
            return false;
        }
        // active(), not isAdapted(): the switch has to reach the server-side half of the movement
        // mixins too. The server previously answered raw and the client through ClientChecks.has()
        // -> active(), so a switched-off adaptation kept clamping friction / ignoring slime on the
        // server while the client ran vanilla physics. Every caller passes a one-time concept
        // (Move_*, Mutation_*, Combat_ShieldLock); leveled ones go through conceptLevel above.
        return data(serverPlayer).active(concept);
    }

    /**
     * Which environment adaptation makes a damage category harmless, or null for none.
     *
     * <p>This was a seven-line if-chain, and a chain is exactly the shape that produced the bug this
     * replaces: a chain of {@code category == X && data.active(...)} reads like a gate but is seven
     * independent gates, so one wrong accessor in one arm is invisible to review and to every other
     * test. As a single table the whole mapping can be enumerated from a test, which is the only way
     * to be sure a category was not simply forgotten.
     *
     * <p>Note the four categories that are NOT here: {@code DROWN} also has a second grant in
     * {@code SynergyEffects}, which refills air and is drowning immunity by another name. One effect
     * with two grants is the shape that hid behind the chain.
     */
    public static String envImmunityConcept(AdaptionCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case FALL -> Concepts.ENV_FALL;
            case STARVE -> Concepts.ENV_STARVE;
            case DROWN -> Concepts.ENV_DROWN;
            // FIRE is deliberately split below: this table is the *environmental* half only.
            case FIRE -> Concepts.ENV_LAVA;
            case SUFFOCATE -> Concepts.ENV_SUFFOCATE;
            case VOID -> Concepts.ENV_VOID;
            case CONTACT -> Concepts.ENV_THORNS;
            default -> null;
        };
    }

    /**
     * True when a one-time adaptation makes this whole damage category harmless.
     *
     * <p>Only the one-time {@code Env_*} concepts answer here. Leveled {@code Type_*} concepts are
     * the other half of the same idea and are handled by {@link #isMaxedImmunity}: {@code active()}
     * asks the {@code adapted} set, while a leveled concept lives in {@code levels}, so the old
     * {@code data.active(Type_FIRE)} arm was false by construction -- fire never became harmless no
     * matter the level. That is the accessor trap the data classes warn about, and it is why a maxed
     * fire adaptation still took 60 % damage.
     *
     * <p>FIRE is the one category with two separate adaptations, because lava and fire are
     * learned and should be lived through separately: {@link Concepts#ENV_LAVA} comes only from
     * actual immersion in lava, while {@code Type_FIRE} comes from every other fire hit. A fire arrow
     * must not grant lava immunity, and standing in lava must not train the fire adaptation. Lava is
     * therefore the only fire damage this method answers for; ordinary fire is a maxed Type_FIRE.
     */
    private static boolean hasFullImmunity(PlayerAdaption data, AdaptionCategory category, DamageSource source) {
        if (category == AdaptionCategory.FIRE) {
            return isLavaDamage(source) && data.active(Concepts.ENV_LAVA);
        }
        String immunity = envImmunityConcept(category);
        return immunity != null && data.active(immunity);
    }

    /**
     * True when a maxed leveled adaptation makes this incoming hit harmless outright.
     *
     * <p>This is the other half of {@link #hasFullImmunity}: that one covers the one-time
     * {@code Env_*} immunities, this one the last rung of a {@code Type_*} or {@code Contact_<mob>}
     * ladder. A maxed adaptation is the completed phenomenon adaptation, so the hit is nullified
     * instead of reduced -- no damage, no red flash, no knockback. Level 7 and below keep the
     * reduction curve.
     *
     * <p>Three deliberate exclusions:
     * <ul>
     *   <li>{@link DamageTypeTags#BYPASSES_INVULNERABILITY} -- that is the {@code /kill} command, not
     *       a phenomenon to adapt to; an adaptation must not make an operator's kill fail.</li>
     *   <li>Lava -- it belongs to the one-time {@code Env_Lava} adaptation, never to
     *       {@code Type_FIRE}; the fire/lava split is what keeps a fire arrow from granting lava
     *       immunity.</li>
     *   <li>The environmental categories -- fall, drowning, starvation, suffocation, void and
     *       thorns are one-time {@code Env_*} concepts with no leveled family, so there is no level
     *       eight to ask about.</li>
     * </ul>
     *
     * <p>Contact is checked before the category, because a mob's melee swing is matched as
     * {@code MOB} (or {@code PLAYER}) by {@link AdaptionCategory} even though the adaptation that
     * learned it is {@code Contact_<mob>}. Only a direct living attacker counts, exactly as in the
     * {@code contactDefense.immunityLevel} check: a projectile is the projectile family, not
     * contact.
     *
     * <p>Public so the boundaries can be pinned by a gametest without a mock player.
     */
    public static boolean isMaxedImmunity(PlayerAdaption data, AdaptionCategory category, DamageSource source) {
        if (data == null || category == null || source == null) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            String mobPath = entityPath(attacker.getType());
            if (data.levelOrZero(Concepts.contact(mobPath)) >= PlayerAdaption.MAX_LEVEL) {
                return true;
            }
        }
        switch (category) {
            case FALL, STARVE, DROWN, SUFFOCATE, VOID, CONTACT -> {
                return false;
            }
            case FIRE -> {
                if (isLavaDamage(source)) {
                    return false;
                }
            }
            default -> {
            }
        }
        return data.levelOrZero(Concepts.type(category)) >= PlayerAdaption.MAX_LEVEL;
    }

    /** The damage type vanilla uses for lava itself; fire ticks and fireballs are not lava. */
    private static boolean isLavaDamage(DamageSource source) {
        return source.typeHolder().is(DamageTypes.LAVA);
    }

    /**
     * Chorus-like rescue for {@code Env_Void}.
     *
     * <p>Cancelling the void damage without moving the player was a one-way trip: the immunity is
     * permanent, so the fall never ended and there was no way back except unequipping the wheel or a
     * command. The rescue first tries a chorus-fruit-style random spot near the player, but unlike
     * the fruit it reads the column's surface height instead of a small vertical offset -- a random
     * offset around Y = -100 would still be in the void.
     */
    private static void rescueFromVoid(ServerPlayer player, PlayerAdaption data) {
        long now = player.level().getGameTime();
        if (data.lastVoidRescueTick != Long.MIN_VALUE && now - data.lastVoidRescueTick < 20) {
            return;
        }
        ServerLevel level = player.serverLevel();
        var random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = player.getX() + (random.nextDouble() - 0.5D) * 32.0D;
            double z = player.getZ() + (random.nextDouble() - 0.5D) * 32.0D;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (y <= level.getMinBuildHeight() + 1) {
                continue;
            }
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
                continue;
            }
            if (level.getBlockState(pos.below()).isAir() || !level.getFluidState(pos).isEmpty()) {
                continue;
            }
            teleportRescue(player, level, x + 0.5D, y, z + 0.5D);
            data.lastVoidRescueTick = now;
            return;
        }

        // A void world has no surface to find. Fall back to the shared spawn, which is the one
        // position the game guarantees to be usable (the End's obsidian platform included).
        BlockPos spawn = level.getSharedSpawnPos();
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()),
                level.getMinBuildHeight() + 1);
        teleportRescue(player, level, spawn.getX() + 0.5D, y, spawn.getZ() + 0.5D);
        data.lastVoidRescueTick = now;
    }

    private static void teleportRescue(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setAirSupply(player.getMaxAirSupply());
        level.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void saveToItem(Player player, PlayerAdaption data) {
        getWheelStack(player).ifPresent(stack -> saveToStack(stack, data));
    }

    public static void saveToStack(ItemStack stack, PlayerAdaption data) {
        stack.set(ModDataComponents.WHEEL_DATA, WheelData.fromPlayer(data));
    }

    private static void loadFromItem(Player player, PlayerAdaption data) {
        getWheelStack(player).ifPresent(stack -> {
            WheelData wd = stack.get(ModDataComponents.WHEEL_DATA);
            if (wd != null) wd.loadInto(data);
        });
    }

    private static String entityPath(EntityType<?> type) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return key != null ? key.toString() : type.toShortString();
    }

    private static String pathOf(LivingEntity target) {
        LivingEntity root = BossHelper.resolveLiving(target);
        return root != null ? entityPath(root.getType()) : entityPath(target.getType());
    }

    private static String adaptedExistenceTarget(PlayerAdaption data, DamageSource source) {

        if (!AdaptionConfig.ENABLE_EXISTENCE.get()) {
            return null;
        }
        LivingEntity boss = BossHelper.resolveBossFromSource(source);
        if (boss != null) {
            String path = entityPath(boss.getType());
            if (data.isEnabled(Concepts.existence(path)) && data.existenceAdapted.contains(path)) {
                return path;
            }
            return null;
        }
        if (AdaptionConfig.ENABLE_CHAOS_GUARDIAN.get()) {
            String linked = DraconicCompat.guardianLinkedPath(source.getDirectEntity());
            if (linked == null) {
                linked = DraconicCompat.guardianLinkedPath(source.getEntity());
            }
            // The off switch reaches the DE path too; it used to be checked for the vanilla boss
            // path only, so a disabled Chaos Guardian adaptation still granted full immunity.
            return linked != null && data.isEnabled(Concepts.existence(linked))
                    && data.existenceAdapted.contains(linked) ? linked : null;
        }
        return null;
    }

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide
                && SynergyEffects.tryAbsorbVoid(player, event.getSource())) {

            event.setCanceled(true);
            return;
        }
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);
        DamageSource source = event.getSource();
        AdaptionCategory category = AdaptionCategory.match(source);

        if (hasFullImmunity(data, category, source)) {
            if (category == AdaptionCategory.VOID) {
                rescueFromVoid(player, data);
            }
            event.setCanceled(true); return;
        }

        int immunityLevel = AdaptionConfig.CONTACT_IMMUNITY_LEVEL.get();
        if (immunityLevel > 0) {
            Entity direct = source.getDirectEntity();
            if (direct instanceof LivingEntity livingDirect) {
                String mobPath = entityPath(livingDirect.getType());
                int contactLevel = data.levelOrZero(Concepts.contact(mobPath));
                if (contactLevel >= immunityLevel) {
                    event.setCanceled(true); return;
                }
            }
        }

        String adaptedBoss = adaptedExistenceTarget(data, source);
        if (adaptedBoss != null) {
            reflectAttack(player, source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity(),
                    event.getContainer().getNewDamage());
            event.setCanceled(true);
            return;
        }

        // Deliberately the last gate, behind the existence reflection: a maxed Type_MOB would
        // otherwise swallow a boss hit before existence could reflect it, and the reflection is the
        // only offence an existence adaptation has. A hit that was going to be reflected already
        // dealt the player no damage, so skipping the immunity check for it changes nothing for the
        // player and keeps the boss taking its own hit.
        if (AdaptionConfig.FULL_IMMUNITY_AT_MAX.get() && isMaxedImmunity(data, category, source)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        DamageSource source = event.getSource();
        float newDamage = event.getNewDamage();

        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof ServerPlayer attacker
                && event.getEntity() instanceof LivingEntity target
                && target != attacker
                && !REFLECTING.contains(target.getUUID())
                && wearingWheel(attacker)) {
            applyFistDamage(attacker, target, event);
            applyOffense(attacker, target, event);
        }

        if (!(event.getEntity() instanceof ServerPlayer player) || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);

        if (adaptedExistenceTarget(data, source) != null) {
            event.setNewDamage(0);
            return;
        }

        Entity direct = source.getDirectEntity();

        // Only Adversity itself switches the defences off. Its cooldown is a gate on starting
        // another Adversity (tryAdversitySurvival already checks it), not a second vulnerability
        // window: keeping the old check here meant 30 seconds of no reduction, healing, i-frames
        // or analysis immediately after surviving one.
        if (data.adversityActive) return;

        AdaptionCategory category = AdaptionCategory.match(source);

        if (hasFullImmunity(data, category, source)) {
            if (category == AdaptionCategory.VOID) {
                rescueFromVoid(player, data);
            }
            event.setNewDamage(0); return;
        }

        // The incoming handler already cancelled this in the ordinary path. Kept here as the same
        // second layer the one-time immunities above have, so a future damage route that reaches
        // Pre without passing Incoming cannot quietly keep hitting a fully adapted player.
        if (AdaptionConfig.FULL_IMMUNITY_AT_MAX.get() && isMaxedImmunity(data, category, source)) {
            event.setNewDamage(0); return;
        }

        boolean lavaDamage = category == AdaptionCategory.FIRE && isLavaDamage(source);

        List<String> concepts = new ArrayList<>();

        // These categories are covered by a one-time Env_* immunity and no longer have a leveled
        // Type_* counterpart. CONTACT joined them: Env_Thorns is its full-protection variant, and
        // Contact_<mob> still trains separately below.
        boolean envCategory = category == AdaptionCategory.FALL || category == AdaptionCategory.STARVE
                || category == AdaptionCategory.DROWN
                || category == AdaptionCategory.SUFFOCATE
                || category == AdaptionCategory.VOID
                || category == AdaptionCategory.CONTACT;
        if (!envCategory && !lavaDamage) {
            concepts.add(Concepts.type(category));
        }

        String mobPath = null;
        if (direct instanceof LivingEntity livingDirect) {

            mobPath = entityPath(BossHelper.resolveLiving(livingDirect).getType());
            concepts.add(Concepts.contact(mobPath));
        }
        if (AdaptionConfig.ENABLE_EXISTENCE.get()) {
            LivingEntity bossFromSource = BossHelper.resolveBossFromSource(source);
            if (bossFromSource != null) {
                String bossPath = entityPath(bossFromSource.getType());
                noteBossEncounter(data, bossPath);
                noteBossHit(player, data, bossPath);
            }
        }

        float reduction = 0f;
        for (String concept : concepts) {
            int level = data.levelOrZero(concept);
            if (level > 0) {
                if (concept.startsWith("Contact_")) {

                    reduction = Math.max(reduction, (float) (AdaptionConfig.contactProtection(level) / 100.0));
                } else {
                    reduction += (float) (AdaptionConfig.defenseReduction(level) / 100.0);
                }
            }
        }
        reduction = Math.min(1.0f, reduction);
        if (reduction > 0f) {
            event.setNewDamage(newDamage * (1f - reduction));
            newDamage = event.getNewDamage();
        }

        SynergyEffects.onHurtTaken(player, event.getSource().getEntity() instanceof LivingEntity attacker
                ? attacker : null, newDamage);

        int bestLevel = 0;
        for (String concept : concepts) {
            bestLevel = Math.max(bestLevel, data.levelOrZero(concept));
        }
        if (bestLevel >= 5) {
            double ratio = AdaptionConfig.defenseHealRatio(bestLevel) / 100.0;
            if (ratio > 0) {

                player.heal(newDamage * (float) ratio);
            }
        }
        if (bestLevel >= 8) {
            // 40 ticks, not 120. The original's immuneTime = 120 is 2 seconds because Terraria runs
            // at 60 tps; 120 Minecraft ticks is 6, and a mob in melee range swings every ~20 ticks, so
            // the window re-armed before it could ever lapse and the Lv8 capstone became permanent
            // immunity to that whole damage category.
            int iFrames = AdaptionConfig.DEFENSE_LV8_IFRAMES_TICKS.get();
            if (iFrames > 0) {
                player.invulnerableTime = Math.max(player.invulnerableTime, iFrames);
            }
        }

        if (AdaptionConfig.ENABLE_DEFENSE.get()) {
            if (!envCategory && !lavaDamage) {
                startOrAccelerate(player, data, Concepts.type(category),
                        (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);
            }
            if (mobPath != null) {
                startOrAccelerate(player, data, Concepts.contact(mobPath),
                        (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);
            }
        }

        if (AdaptionConfig.ENABLE_PERCEPTION.get()) {
            startOrAccelerate(player, data, Concepts.PERCEP_STEADY_GAZE,
                    (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);
        }

        if (AdaptionConfig.ENABLE_ENVIRONMENT.get()) {
            if (category == AdaptionCategory.FALL) {
                double fallTime = AdaptionConfig.RAPID_FALL_ANALYSIS.get()
                        ? AdaptionConfig.FALL_ANALYSIS_SECONDS.get()
                        : AdaptionConfig.ENV_ANALYSIS_SECONDS.get();
                startTask(player, data, Concepts.ENV_FALL, (int) (fallTime * 20));
            }
            // Lava is learned only by immersion, in the tick loop. A fire hit must not grant it:
            // that was the race that made Type_FIRE unreachable and Thermal impossible.
            if (category == AdaptionCategory.CONTACT) {
                startTask(player, data, Concepts.ENV_THORNS, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }
            if (category == AdaptionCategory.SUFFOCATE) {
                startTask(player, data, Concepts.ENV_SUFFOCATE, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }
            if (category == AdaptionCategory.VOID) {
                startTask(player, data, Concepts.ENV_VOID, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }
            if (category == AdaptionCategory.DROWN) {
                startTask(player, data, Concepts.ENV_DROWN, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }
            if (category == AdaptionCategory.STARVE) {
                if (AdaptionConfig.INSTANT_STARVE.get()) {
                    grantOneTime(player, data, Concepts.ENV_STARVE);
                    player.getFoodData().eat(20, 20f);
                    playAdaptVoice(player, 0.7f, 1f);
                    player.sendSystemMessage(Component.translatable("adaptionwheel.msg.adapted_once",
                                    Concepts.chatName(Concepts.ENV_STARVE))
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Concepts.COLOR_ENV))));
                    event.setNewDamage(0);
                    return;
                }
                startTask(player, data, Concepts.ENV_STARVE, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }
        }

        if (tryAdversitySurvival(player, data, newDamage)) {

            event.setNewDamage(0f);
        }
    }

    private static boolean tryAdversitySurvival(ServerPlayer player, PlayerAdaption data, float damage) {
        if (!AdaptionConfig.ENABLE_ADVERSITY.get()
                || player.isCreative()
                || data.adversityCooldownTimer > 0 || data.adversityActive
                || damage < player.getHealth()) {
            return false;
        }

        player.setHealth(Math.max(1f, player.getHealth() - 30f));
        data.adversityActive = true;
        data.adversityTimer = ru.adaptionwheel.AdaptionTimings.ADVERSITY_TICKS;

        player.invulnerableTime = Math.max(player.invulnerableTime, 60);

        if (player.level() instanceof ServerLevel serverLevel) {
            var random = player.getRandom();
            double ex = player.getX(), ey = player.getEyeY(), ez = player.getZ();
            for (int i = 0; i < 16; i++) {
                double ang = i / 16.0 * Math.PI * 2;
                double dx = Math.cos(ang), dz = Math.sin(ang);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        ex + dx * 0.4, ey + (random.nextDouble() - 0.5) * 0.4, ez + dz * 0.4,
                        0, dx * 0.35, (random.nextDouble() - 0.5) * 0.1, dz * 0.35, 1.0);
            }
        }

        sync(player, data, true);
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.adversity")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        return true;
    }

    public static void handleDirectHealthReduction(LivingEntity target, float newHealth) {
        float current = target.getHealth();
        if (!(target instanceof ServerPlayer player) || player.level().isClientSide || newHealth >= current) {
            target.setHealth(newHealth);
            return;
        }
        if (!wearingWheel(player)) {
            target.setHealth(newHealth);
            return;
        }

        PlayerAdaption data = data(player);
        boolean existenceEnabled = AdaptionConfig.ENABLE_EXISTENCE.get();

        if (existenceEnabled && data.isEnabled(Concepts.existence(DraconicCompat.GUARDIAN_ID))
                && data.existenceAdapted.contains(DraconicCompat.GUARDIAN_ID)) {
            return;
        }
        if (!AdaptionConfig.ENABLE_CHAOS_GUARDIAN.get()) {
            target.setHealth(newHealth);
            return;
        }

        // The laser writes health directly, so it never reaches onAttack; a maxed explosion or
        // Chaos Guardian adaptation has to nullify it here, or "fully adapted" has a hole exactly
        // where the hardest-hitting attack in the game is.
        if (AdaptionConfig.FULL_IMMUNITY_AT_MAX.get()
                && (data.levelOrZero(Concepts.type(AdaptionCategory.EXPLOSION)) >= PlayerAdaption.MAX_LEVEL
                || data.levelOrZero(Concepts.contact(DraconicCompat.GUARDIAN_ID)) >= PlayerAdaption.MAX_LEVEL)) {
            return;
        }

        float raw = current - newHealth;

        if (existenceEnabled) {
            noteBossEncounter(data, DraconicCompat.GUARDIAN_ID);
        }
        int defenseTicks = (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20);
        startOrAccelerate(player, data, Concepts.type(AdaptionCategory.EXPLOSION), defenseTicks, true);
        startOrAccelerate(player, data, Concepts.contact(DraconicCompat.GUARDIAN_ID), defenseTicks, true);

        if (data.adversityActive) {
            target.setHealth(Math.max(current - raw, 0f));
            return;
        }

        float reduction = 0f;
        int bestLevel = 0;
        int explosionLevel = data.levelOrZero(Concepts.type(AdaptionCategory.EXPLOSION));
        if (explosionLevel > 0) {
            reduction += (float) (AdaptionConfig.defenseReduction(explosionLevel) / 100.0);
            bestLevel = explosionLevel;
        }
        int contactLevel = data.levelOrZero(Concepts.contact(DraconicCompat.GUARDIAN_ID));
        if (contactLevel > 0) {
            reduction = Math.max(reduction, (float) (AdaptionConfig.contactProtection(contactLevel) / 100.0));
            bestLevel = Math.max(bestLevel, contactLevel);
        }
        reduction = Math.min(1f, reduction);

        float applied = raw * (1f - reduction);
        if (bestLevel >= 5) {
            double healRatio = AdaptionConfig.defenseHealRatio(bestLevel) / 100.0;
            if (healRatio > 0) {
                player.heal(raw * (float) healRatio);
            }
        }

        float resulting = current - applied;
        if (resulting <= 0f && tryAdversitySurvival(player, data, applied)) {
            return;
        }
        target.setHealth(Math.max(resulting, 0f));
    }

    private static void reflectAttack(ServerPlayer player, Entity direct, float damage) {

        Entity reflectTarget = direct;
        if (direct instanceof Projectile projectile && projectile.getOwner() != null) {
            reflectTarget = projectile.getOwner();
        }
        if (!(reflectTarget instanceof LivingEntity living) || reflectTarget == player) {
            return;
        }
        float multiplier = (float) (double) AdaptionConfig.EXISTENCE_REFLECT_MULTIPLIER.get();
        if (multiplier <= 0f) {
            return;
        }

        REFLECTING.add(living.getUUID());
        try {
            living.hurt(player.damageSources().playerAttack(player), damage * multiplier);
        } finally {
            REFLECTING.remove(living.getUUID());
        }

        living.knockback(1.2, player.getX(), player.getZ());
        // 1 tick, not 10. The original grants max(immuneTime, 2) here -- two Terraria ticks, about
        // 33ms. Reflection is supposed to deny the boss its attack, not to give the player a shield;
        // the half-second this used to grant was standing in for a cooldown it never had.
        int reflectIFrames = AdaptionConfig.EXISTENCE_REFLECT_IFRAMES_TICKS.get();
        if (reflectIFrames > 0) {
            player.invulnerableTime = Math.max(player.invulnerableTime, reflectIFrames);
        }
    }

    private static final Set<UUID> REFLECTING = new HashSet<>();

    private static final Map<UUID, Long> DIMENSION_SLASH_LAST = new HashMap<>();
    private static final int DIMENSION_SLASH_COOLDOWN_TICKS = 20;

    private static boolean claimDimensionSlash(ServerPlayer attacker) {
        long now = attacker.level().getGameTime();
        Long last = DIMENSION_SLASH_LAST.get(attacker.getUUID());
        if (last != null && now - last < DIMENSION_SLASH_COOLDOWN_TICKS) {
            return false;
        }
        DIMENSION_SLASH_LAST.put(attacker.getUUID(), now);
        return true;
    }

    private static void applyFistDamage(ServerPlayer attacker, LivingEntity target, LivingDamageEvent.Pre event) {
        if (!wearingWheel(attacker)) {
            return;
        }
        float bonus = MartialArts.bonus(data(attacker));
        if (bonus > 0f) {
            event.setNewDamage(event.getNewDamage() + bonus);
        }
    }

    private static void applyOffense(ServerPlayer attacker, LivingEntity target, LivingDamageEvent.Pre event) {
        PlayerAdaption data = data(attacker);

        String path = pathOf(target);
        String concept = Concepts.offense(path);
        int level = data.levelOrZero(concept);
        float damage = event.getNewDamage();

        if (level > 0) {

            float base = damage + (float) AdaptionConfig.offenseDamageBonus(level);
            double armor = target.getArmorValue();
            base += (float) (armor * (AdaptionConfig.offenseArmorPen(level) / 100.0));
            double crit = AdaptionConfig.offenseCrit(level) + data.getAdaptCount() * AdaptionConfig.BONUS_CRIT_PCT.get();
            if (attacker.getRandom().nextFloat() * 100f < crit) {
                base *= 1.5f;
            }
            base *= (float) (1.0 + data.getAdaptCount() * AdaptionConfig.BONUS_DAMAGE_PCT.get() / 100.0);
            event.setNewDamage(base);

            SynergyEffects.onHit(attacker, target, event.getSource());

            if (level >= 8 && attacker.getRandom().nextFloat() * 100f < AdaptionConfig.DIMENSION_SLASH_CHANCE.get()
                    && claimDimensionSlash(attacker)) {
                float slashDamage = Math.max(1f, (float) (target.getMaxHealth()
                        * AdaptionConfig.DIMENSION_SLASH_HP_PERCENT.get() / 100.0));
                attacker.server.execute(() -> {
                    if (target.isAlive() && !target.isRemoved()) {
                        target.hurt(attacker.damageSources().playerAttack(attacker), slashDamage);
                    }
                });
                attacker.sendSystemMessage(Component.translatable("adaptionwheel.msg.dimension_slash")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
                attacker.level().playSound(null, target.blockPosition(), ModSounds.DIMENSION_CUT.get(),
                        SoundSource.PLAYERS, 1f, 1f);
            }
        }

        if (level < PlayerAdaption.MAX_LEVEL && AdaptionConfig.ENABLE_OFFENSE.get()) {
            int baseTicks = (int) (AdaptionConfig.OFFENSE_ANALYSIS_SECONDS.get() * 20);
            int timer = (int) (baseTicks * Math.pow(1.55, level));

            startOrAccelerate(attacker, data, concept, timer, true,
                    (int) (AdaptionConfig.OFFENSE_ACCELERATION_SECONDS.get() * 20));
        }

        if (BossHelper.isBoss(target) && AdaptionConfig.ENABLE_EXISTENCE.get()) {
            noteBossEncounter(data, path);
        }
    }

    private static ServerPlayer killerOf(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer sp) return sp;
        if (attacker instanceof net.minecraft.world.entity.OwnableEntity owned
                && owned.getOwner() instanceof ServerPlayer sp) return sp;
        return null;
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) return;
        ServerPlayer player = killerOf(event.getSource());
        if (player == null || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);
        if (dead instanceof net.minecraft.world.entity.monster.Enemy) {
            MartialArts.onFistKill(player, data, dead, event.getSource());
        }
        if (data.adversityActive || !AdaptionConfig.ENABLE_LOOT.get() || BossHelper.isBoss(dead)) return;

        String concept = Concepts.drop(pathOf(dead));
        data.killCounts.merge(concept, 1, Integer::sum);
        int newLevel = AdaptionConfig.dropLevelFromKills(data.kills(concept));
        if (newLevel > data.level(concept) && newLevel <= PlayerAdaption.MAX_LEVEL) {
            startDropTask(player, data, concept);
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getEntity().level().isClientSide) return;
        ServerPlayer player = event.getAttackingPlayer() instanceof ServerPlayer sp ? sp : null;
        if (player == null || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);

        if (data.adversityActive || !AdaptionConfig.ENABLE_LOOT.get()
                || BossHelper.isBoss(event.getEntity())) return;

        String concept = Concepts.drop(pathOf(event.getEntity()));
        int level = data.levelOrZero(concept);
        if (level <= 0) return;

        int base = event.getDroppedExperience();
        if (base <= 0) return;
        double bonusPct = AdaptionConfig.lootBonus(level);
        event.setDroppedExperience((int) Math.min(base * (1.0 + bonusPct / 100.0), Integer.MAX_VALUE / 4));
    }

    private static void startDropTask(ServerPlayer player, PlayerAdaption data, String concept) {
        if (data.level(concept) >= PlayerAdaption.MAX_LEVEL) return;

        for (AdaptionTask task : data.tasks) {
            if (task.concept.equals(concept)) return;
        }
        data.tasks.add(new AdaptionTask(concept, 20, 20));
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.analyzing", Concepts.chatName(concept))
                .withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onMobDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity dead = event.getEntity();
        ServerPlayer player = killerOf(event.getSource());
        if (player == null || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);
        if (data.adversityActive || !AdaptionConfig.ENABLE_LOOT.get() || BossHelper.isBoss(dead)) return;

        String concept = Concepts.drop(pathOf(dead));
        int level = data.levelOrZero(concept);
        if (level <= 0) return;

        double increase = AdaptionConfig.lootBonus(level);
        int extraRolls = (int) (increase / 100.0);
        if (player.getRandom().nextDouble() < increase / 100.0 - extraRolls) extraRolls++;
        if (extraRolls <= 0) return;
        extraRolls = Math.min(extraRolls, 200);

        ServerLevel serverLevel = (ServerLevel) dead.level();
        DamageSource source = event.getSource();
        LootParams.Builder builder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, dead)
                .withParameter(LootContextParams.ORIGIN, dead.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withLuck(player.getLuck());
        if (source.getEntity() instanceof LivingEntity killer) {
            builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer);
        }
        if (source.getDirectEntity() instanceof LivingEntity directEntity) {
            builder.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, directEntity);
        }
        builder.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        LootParams params = builder.create(LootContextParamSets.ENTITY);

        java.util.List<ItemStack> baseDrops = new java.util.ArrayList<>();
        for (ItemEntity ie : event.getDrops()) {
            if (!ie.getItem().isEmpty()) baseDrops.add(ie.getItem().copy());
        }

        boolean usedFallback = false;
        for (int i = 0; i < extraRolls; i++) {
            java.util.List<ItemStack> lootRoll = serverLevel.getServer().reloadableRegistries()
                    .getLootTable(dead.getLootTable()).getRandomItems(params);
            if (lootRoll.isEmpty()) {
                if (!usedFallback && !baseDrops.isEmpty()) {
                    for (ItemStack fallbackStack : baseDrops) {
                        event.getDrops().add(new ItemEntity(serverLevel, dead.getX(), dead.getY(), dead.getZ(), fallbackStack.copy()));
                    }
                    usedFallback = true;
                }
            } else {
                for (ItemStack stack : lootRoll) {
                    if (!stack.isEmpty()) {
                        event.getDrops().add(new ItemEntity(serverLevel, dead.getX(), dead.getY(), dead.getZ(), stack));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        PlayerAdaption data = data(player);
        boolean wearing = wearingWheel(player);
        if (wearing) {
            data.wasWearing = true;
            // A login is not an equip, but it has to behave like one for the data's sake. The
            // attachment is the live copy and the wheel item is the travelling one, and loadInto
            // only ever raises a level or adds to a set, so merging on every join can repair an
            // attachment that came back poorer than its own wheel and can never walk anything
            // backwards. Without it, logging in was visibly worse than the wheel in the slot
            // whenever the attachment had missed a write.
            loadFromItem(player, data);
            data.equippedStack = getWheelStack(player).orElse(null);
            applyStats(player, data);
        }
        // The ability transition is replayed on every join, but if the saved abilities already had
        // `mayfly` set, FlightAbility sees no transition and cannot repair a `flying` flag left over
        // from the previous session. Clear it here so logging in never resumes a hover.
        if (!player.isCreative() && !player.isSpectator()
                && data.isAdapted(Concepts.MUTATION_FLIGHT)
                && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        sync(player, data, wearing);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        PENDING_RESPAWN_HEALTH.remove(id);
        PENDING_RESPAWN_ARMED.remove(id);
        PROXIMITY_HEAT_CACHE.remove(id);
        WEARING_CACHE.remove(id);
        NEARBY_BOSS_CACHE.remove(id);
        LAST_VOICE_TICK.remove(id);
        REFLECTING.clear();
        DIMENSION_SLASH_LAST.remove(id);
        MartialArts.forget(id);
        SynergyEffects.forget(player);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (wearingWheel(player)) {
            float maxHealth = player.getMaxHealth();
            if (maxHealth > 0f) {
                PENDING_RESPAWN_HEALTH.put(player.getUUID(), player.getHealth() / maxHealth);
            }
        }
        if (!AdaptionConfig.RESET_ADAPTATIONS_ON_DEATH.get()) return;

        PlayerAdaption data = data(player);
        data.reset();
        data.wasWearing = wearingWheel(player);
        MartialArts.forget(player.getUUID());
        PENDING_RESPAWN_HEALTH.remove(player.getUUID());
        PENDING_RESPAWN_ARMED.remove(player.getUUID());
        applyStats(player, data);

        wipeWheelItem(player, data);
    }

    private static void wipeWheelItem(ServerPlayer player, PlayerAdaption data) {
        ItemStack equipped = data.equippedStack;
        if (equipped != null) {
            equipped.set(ModDataComponents.WHEEL_DATA, WheelData.EMPTY);
        } else {
            getWheelStack(player).ifPresent(stack -> stack.set(ModDataComponents.WHEEL_DATA, WheelData.EMPTY));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {

        if (PENDING_RESPAWN_HEALTH.containsKey(event.getEntity().getUUID())) {
            PENDING_RESPAWN_ARMED.add(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        PlayerAdaption data = data(player);
        boolean wearing = wearingWheel(player);

        if (wearing && !data.wasWearing) {
            // Deliberately only a merge. This branch used to clear the running analyses, the boss
            // counters and the Adversity state first, on the theory that an equip starts a fresh
            // wheel — but it also runs on the first tick after a login whenever Curios reported the
            // slot a moment late, so joining a world silently cancelled every analysis that was
            // running. Unequipping already owns that reset; an equip has nothing to clear.
            loadFromItem(player, data);
            applyStats(player, data);
        }
        if (!wearing && data.wasWearing) {

            data.tasks.clear();
            data.bossCombatTicks.clear();

            ItemStack unequipped = data.equippedStack;
            data.equippedStack = null;
            if (unequipped != null) {
                if (AdaptionConfig.KEEP_DATA_ON_UNEQUIP.get()) {
                    saveToStack(unequipped, data);
                } else {
                    unequipped.set(ModDataComponents.WHEEL_DATA, WheelData.EMPTY);
                }
            }
            // The cache, the per-concept switches, both fist counters and the Instabreak stance
            // are player-bound and are not stored in WheelData, so the full reset here used to
            // destroy them with no copy on the item. Keep them across the unequip.
            data.resetForUnequip();
            revokeWearableState(player);
            MartialArts.forget(player.getUUID());
            PENDING_RESPAWN_HEALTH.remove(player.getUUID());
            PENDING_RESPAWN_ARMED.remove(player.getUUID());
            applyStats(player, data);
            sync(player, data, false);
        }

        if (wearing && !player.isDeadOrDying()) {
            ItemStack equipped = getWheelStack(player).orElse(null);
            if (equipped != null && data.equippedStack != null && equipped != data.equippedStack) {
                saveToStack(data.equippedStack, data);
                // A swap is still a wheel transition, not a player wipe: the cache and the switch
                // states belong to the player and survive it.
                data.resetForUnequip();
                // The new wheel may grant neither flight nor the synergies the old one did, so the
                // state that lives outside the attachment has to be taken back here too. Whatever
                // the new wheel does grant is re-applied by the tick loop on its next pass.
                revokeWearableState(player);
                loadFromItem(player, data);
                applyStats(player, data);
                sync(player, data, true);
            }
            data.equippedStack = equipped;
        }
        data.wasWearing = wearing;
        if (!wearing) return;

        if (player.isDeadOrDying()) {
            if (data.adversityActive) {
                data.adversityActive = false;
                data.adversityTimer = 0;
            }
            return;
        }

        if (data.adversityCooldownTimer > 0) data.adversityCooldownTimer--;
        if (data.adversityActive) {
            data.adversityTimer--;
            boolean finished = false;
            if (data.adversityTimer <= 0) {
                data.adversityActive = false;
                data.adversityCooldownTimer = (int) (AdaptionConfig.ADVERSITY_COOLDOWN_SECONDS.get() * 20);
                player.heal(player.getMaxHealth());

                completeAllTasks(player, data);
                grantOneTime(player, data, Concepts.ADVERSITY);

                applyStats(player, data);
                playAdaptVoice(player, 1f, 1f);
                player.sendSystemMessage(Component.translatable("adaptionwheel.msg.adversity_done")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                finished = true;
            }
            data.wheelRotation += 0.2f;
            applyEnvEffects(player, data);
            if (!player.isDeadOrDying()) {

                if (finished || player.tickCount % 20 == 0) sync(player, data, true);
            }
            return;
        }

        Iterator<AdaptionTask> it = data.tasks.iterator();
        while (it.hasNext()) {
            AdaptionTask task = it.next();
            task.timer--;
            if (task.timer <= 0) {
                it.remove();
                completeTask(player, data, task.concept);
            }
        }

        // The analyses do not wait for the 1 Hz sync to be believed. Their countdown is already
        // smooth on the client, because it extrapolates between syncs, but a task's timer also moves
        // in jumps -- startOrAccelerate's accelerate branch subtracts from it on every point of damage,
        // and a completed task vanishes -- and extrapolation cannot invent a jump it was never told
        // about. So the running tasks are pushed on a short cadence while any are running, which is
        // what makes taking damage move the bar straight away instead of a beat later.
        if (!data.tasks.isEmpty() && player.tickCount % TaskProgressPayload.PUSH_EVERY_TICKS == 0) {
            // The same filtered view the full sync sends, not the raw map. Sending the raw counters
            // here is what made the existence row blink: this arrived at 4 Hz with every boss in it
            // while the 1 Hz sync arrived with only the nearby ones.
            TaskProgressPayload.send(player, data.tasks, visibleBossProgress(player, data));
        }

        if (AdaptionConfig.ENABLE_ENVIRONMENT.get()) {
            if (player.isInLava()) {
                startTask(player, data, Concepts.ENV_LAVA, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }
            if (player.isInWater()) {
                startTask(player, data, Concepts.ENV_LIQUID, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }
            if (player.getAirSupply() <= 0) {
                startTask(player, data, Concepts.ENV_DROWN, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }

            if (player.tickCount % 4 == 0) {
                if (isInDarkness(player)) {
                    startTask(player, data, Concepts.ENV_DARKNESS, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
                }
                if (isOnIce(player)) {
                    startTask(player, data, Concepts.ENV_ICE, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
                }
                if (isStandingOnSlime(player)) {
                    startTask(player, data, Concepts.ENV_SLIME, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
                }
                if (isInCobweb(player)) {
                    startTask(player, data, Concepts.ENV_COBWEB, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
                }
            }
        }

        applyEnvEffects(player, data);

        if (!data.active(Concepts.MUTATION_THERMAL)
                && data.active(Concepts.type(AdaptionCategory.FIRE))
                && data.active(Concepts.ENV_LAVA)) {
            grantComboMutation(player, data, Concepts.MUTATION_THERMAL);
        } else if (data.active(Concepts.MUTATION_THERMAL)) {
            tickThermalRegeneration(player, data);
        }
        if (AdaptionConfig.ENABLE_MUTATION_AQUATIC.get()) {
            if (!data.active(Concepts.MUTATION_AQUATIC)
                    && data.active(Concepts.ENV_LIQUID) && data.active(Concepts.ENV_DROWN)) {
                grantComboMutation(player, data, Concepts.MUTATION_AQUATIC);
            }
        }
        if (AdaptionConfig.ENABLE_MUTATION_IMPACT.get()) {
            if (!data.active(Concepts.MUTATION_IMPACT)
                    && data.active(Concepts.ENV_FALL) && data.active(Concepts.ENV_KNOCKBACK)) {
                grantComboMutation(player, data, Concepts.MUTATION_IMPACT);
            } else if (data.active(Concepts.MUTATION_IMPACT)) {
                tickImpactStomp(player, data);
            }
        }

        if (AdaptionConfig.ENABLE_MUTATION_SEA_EYE.get()
                && !data.active(Concepts.MUTATION_SEA_EYE)
                && data.active(Concepts.ENV_LIQUID)
                && data.active(Concepts.ENV_DROWN)
                && data.active(Concepts.ENV_LAVA)) {
            grantComboMutation(player, data, Concepts.MUTATION_SEA_EYE);
        }
        if (AdaptionConfig.ENABLE_MUTATION_FLIGHT.get()) {
            tickFlight(player, data);
        }

        if (AdaptionConfig.ENABLE_INVENTORY_ADAPTATION.get()
                && !data.active(Concepts.ENV_INVENTORY)
                && CacheService.inventoryIsFull(player)) {
            startTask(player, data, Concepts.ENV_INVENTORY,
                    (int) (AdaptionConfig.INVENTORY_ADAPTATION_SECONDS.get() * 20));
        }

        if (AdaptionConfig.DIMENSION_DESTROY_ENABLED.get()
                && !data.active(Concepts.DIMENSION_DESTROY)
                && reachesDimensionDestroy(data.getAdaptCount(),
                        adaptableConcepts(player.serverLevel()).total(),
                        AdaptionConfig.DIMENSION_DESTROY_PERCENT.get())) {
            grantComboMutation(player, data, Concepts.DIMENSION_DESTROY);
        }

        if (AdaptionConfig.ENABLE_WHEEL_PARTICLES.get()) {
            spawnWheelParticles(player, data);
        }

        if (AdaptionConfig.ENABLE_DEBUFF.get() && !player.getActiveEffects().isEmpty()) {
            for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
                MobEffect mobEffect = effect.getEffect().value();
                if (mobEffect.isBeneficial()) continue;
                String path = effectKey(effect);
                String concept = Concepts.debuff(path);
                if (data.active(concept)) {
                    player.removeEffect(effect.getEffect());
                } else {
                    startTask(player, data, concept, (int) (AdaptionConfig.DEBUFF_ANALYSIS_SECONDS.get() * 20));
                }
            }
        }

        double hpPct = AdaptionConfig.REGEN_HP_THRESHOLD.get() / 100.0;
        int injureLevel = data.levelOrZero(Concepts.SELF_DAMAGE);
        if (player.getHealth() <= player.getMaxHealth() * hpPct) {
            if (injureLevel < PlayerAdaption.MAX_LEVEL) {
                startTask(player, data, Concepts.SELF_DAMAGE, (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20));
            }
        }
        if (injureLevel > 0 && player.getHealth() < player.getMaxHealth()) {
            data.healingTimer++;

            if (data.healingTimer >= 20) {
                data.healingTimer = 0;
                player.heal(Math.max(1f, (float) AdaptionConfig.regenSpeed(injureLevel)));
            }
        }

        if (AdaptionConfig.ENABLE_EXISTENCE.get()) {
            accumulateBossCombat(player, data);
        }

        if (data.tasks.isEmpty() && !data.adversityActive) {
            float diff = data.targetRotation - data.wheelRotation;
            data.wheelRotation += diff * 0.08f;
            if (Math.abs(diff) < 0.01f) data.wheelRotation = data.targetRotation;
        } else {
            data.wheelRotation += 0.2f;
        }

        if (player.tickCount % 20 == 0) {
            applyStats(player, data);
        }
        restorePendingRespawnHealth(player);

        SynergyEffects.refresh(player, data);
        SynergyEffects.tickPassive(player);

        if (player.tickCount % 20 == 0) {

            RitualAuras.Auras auras = RitualAuras.scan(player);
            if (auras.any()) {
                RitualAuras.apply(player, data, auras);
            }
            Resonance.tick(player, auras);

            ru.adaptionwheel.advancement.AdaptationTrigger.evaluate(player, data);

            saveToItem(player, data);
            sync(player, data, wearing);
        }
    }

    private static void restorePendingRespawnHealth(ServerPlayer player) {
        if (!PENDING_RESPAWN_ARMED.remove(player.getUUID())) {
            return;
        }
        Float fraction = PENDING_RESPAWN_HEALTH.remove(player.getUUID());
        if (fraction == null) {
            return;
        }

        applyStats(player, data(player));
        float max = player.getMaxHealth();
        if (max <= 0f) {
            return;
        }
        float target = Mth.clamp(max * fraction, 1f, max);
        if (player.getHealth() < target - 0.01f) {
            player.setHealth(target);
        }
    }

    private static void accumulateBossCombat(ServerPlayer player, PlayerAdaption data) {
        if (data.bossCombatTicks.isEmpty()) return;
        int threshold = (int) (AdaptionConfig.EXISTENCE_REQUIRED_SECONDS.get() * 20);
        Set<String> nearby = nearbyBossPaths(player);
        Iterator<Map.Entry<String, Integer>> it = data.bossCombatTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            String path = entry.getKey();
            if (data.existenceAdapted.contains(path)) {
                it.remove();
                continue;
            }
            if (!nearby.contains(path)) {
                continue;
            }
            int ticks = entry.getValue() + 1;
            entry.setValue(ticks);
            if (ticks >= threshold) {
                it.remove();
                grantExistenceAdaptation(player, data, path);
            }
        }
    }

    /**
     * The boss counters the client is allowed to see, and therefore the ones the HUD may draw a row
     * for. One definition, used by every sender.
     *
     * <p>This had to be extracted because two senders disagreed and the row flickered between them.
     * The counters themselves persist: {@code accumulateBossCombat} stops incrementing a boss that
     * has left the proximity radius but deliberately does not drop it, because walking away from a
     * fight should not throw the analysis away. What this filters is only what is *shown* -- an
     * adapted boss, and a boss that is not here right now, are not displayed.
     *
     * <p>The full sync filtered that way while the shorter task push sent the raw map, so at 1 Hz
     * against 4 Hz the client alternated between a map with the boss in it and one without, and the
     * existence row blinked on and off. Nothing about the analysis changed; only the two views of it
     * did. Same failure mode as {@link #envImmunityConcept} being written out twice.
     */
    private static Map<String, Integer> visibleBossProgress(ServerPlayer player, PlayerAdaption data) {
        if (!AdaptionConfig.ENABLE_EXISTENCE.get() || data.bossCombatTicks.isEmpty()) {
            return java.util.Map.of();
        }
        Set<String> nearby = nearbyBossPaths(player);
        Map<String, Integer> visible = new HashMap<>();
        for (Map.Entry<String, Integer> entry : data.bossCombatTicks.entrySet()) {
            String path = entry.getKey();
            if (data.existenceAdapted.contains(path) || !nearby.contains(path)) continue;
            int ticks = entry.getValue();
            if (ticks > 0) {
                visible.put(path, ticks);
            }
        }
        return visible;
    }

    /**
     * A hit on the boss banks a little progress, on top of the passive per-tick accumulation.
     *
     * <p>Ported from the original's {@code ReduceCombatTime}, which is the reason a boss bar there
     * visibly moves when you swing at the boss rather than only drifting on its own:
     *
     * <pre>
     * float scale = (float)requiredFrames / 108000f;
     * int reduction = (int)((float)Main.rand.Next(6, 61) * scale);
     * if (reduction &lt; 1) reduction = 1;
     * </pre>
     *
     * <p>108000 frames is 1800 seconds at the original's 60 ticks a second, so at any sane
     * {@code existenceRequiredSeconds} the multiplier lands below one and every hit banks the floor
     * of one tick. That is deliberate: the original's own arithmetic makes a hit a nudge, not a
     * shortcut, and it is kept as a floor rather than "simplified" to a bigger number so the pacing
     * still matches. Randomised per hit, as there, so the bar does not read as a deterministic
     * counter, and clamped so a hit can never carry a boss past its total.
     *
     * <p>Gated on {@code active}, like every other effect site, so switching the adaptation off stops
     * banking as well as stopping the denial.
     */
    private static void noteBossHit(ServerPlayer player, PlayerAdaption data, String bossPath) {
        if (!data.active(Concepts.existence(bossPath)) || data.existenceAdapted.contains(bossPath)) {
            return;
        }
        int required = (int) (AdaptionConfig.EXISTENCE_REQUIRED_SECONDS.get() * 20);
        if (required <= 0) {
            return;
        }
        double scale = (double) required / 108000.0;
        int reduction = (int) ((float) (6 + player.getRandom().nextInt(55)) * (float) scale);
        if (reduction < 1) {
            reduction = 1;
        }
        int banked = Math.min(required, data.bossCombatTicks.getOrDefault(bossPath, 0) + reduction);
        data.bossCombatTicks.put(bossPath, banked);
    }

    private static void noteBossEncounter(PlayerAdaption data, String bossPath) {
        data.bossCombatTicks.putIfAbsent(bossPath, 0);
    }

    private static final class NearbyBossCache {
        long stamp = -1;
        Set<String> paths = Set.of();
    }

    private static final Map<UUID, NearbyBossCache> NEARBY_BOSS_CACHE = new HashMap<>();

    private static Set<String> nearbyBossPaths(ServerPlayer player) {
        NearbyBossCache cache = NEARBY_BOSS_CACHE.computeIfAbsent(player.getUUID(), k -> new NearbyBossCache());
        if (cache.stamp == player.tickCount) {
            return cache.paths;
        }
        cache.stamp = player.tickCount;
        Set<String> found = null;
        double range = AdaptionConfig.EXISTENCE_PROXIMITY_BLOCKS.get();
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(range))) {
            LivingEntity boss = BossHelper.resolveBoss(entity);
            if (boss == null) {
                continue;
            }
            if (found == null) {
                found = new HashSet<>();
            }
            found.add(entityPath(boss.getType()));
        }
        cache.paths = found != null ? found : Set.of();
        return cache.paths;
    }

    /**
     * Starts the reward sequence on the client, now that {@code bossPath}'s bar is full.
     *
     * <p>Sent to this player alone, and <b>after</b> the grant rather than in place of it. The
     * original granted at tick 540 of its own cinematic, which means a disconnect at second eight
     * loses the adaptation outright; here the grant has already happened by the time the client is
     * told to play anything, so the sequence is presentation and nothing rides on it finishing.
     *
     * <p>Silent when the boss has already despawned, which is exactly the case where the original's
     * "is the framing still right" check would have refused to play.
     */
    private static void startExistenceCinematic(ServerPlayer player, String bossPath) {
        if (!AdaptionConfig.ENABLE_EXISTENCE.get() || !AdaptionConfig.EXISTENCE_CINEMATIC_ENABLED.get()) {
            return;
        }
        LivingEntity boss = findNearbyBoss(player, bossPath);
        if (boss == null) {
            // The boss despawned between filling the bar and being granted. Silent, which is exactly
            // the case where the original's "is the framing still right" check refused to play.
            return;
        }
        ru.adaptionwheel.network.ExistenceCinematicPayload.send(player, boss.getId(), boss.getName().getString());
    }

    /** The boss this bar was filling for, if it is still within the analysis radius. */
    private static LivingEntity findNearbyBoss(ServerPlayer player, String bossPath) {
        float range = AdaptionConfig.EXISTENCE_PROXIMITY_BLOCKS.get().floatValue();
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(range))) {
            if (!entityPath(entity.getType()).equals(bossPath)) {
                continue;
            }
            LivingEntity boss = BossHelper.resolveBoss(entity);
            if (boss != null) {
                return boss;
            }
        }
        return null;
    }

    private static void grantExistenceAdaptation(ServerPlayer player, PlayerAdaption data, String bossPath) {
        // Deliberately LAST. It used to be the first statement, before a single mutation, so anything
        // that went wrong in a purely cosmetic send took the entire reward with it -- no
        // existenceAdapted, no maxed contact or offence, no history, no completion event, and no
        // error the player could act on. The reward is the point; the cinematic is a hat on it.
        String existenceConcept = Concepts.existence(bossPath);
        String contactConcept = Concepts.contact(bossPath);
        String offenseConcept = Concepts.offense(bossPath);

        data.tasks.removeIf(t -> t.concept.equals(contactConcept) || t.concept.equals(offenseConcept));

        data.levels.put(contactConcept, PlayerAdaption.MAX_LEVEL);
        data.levels.put(offenseConcept, PlayerAdaption.MAX_LEVEL);

        if (bossPath.contains("wither")) {
            data.levels.put(Concepts.type(AdaptionCategory.WITHER), PlayerAdaption.MAX_LEVEL);
        } else if (bossPath.contains("ender_dragon")) {
            data.levels.put(Concepts.type(AdaptionCategory.MAGIC), PlayerAdaption.MAX_LEVEL);
            data.levels.put(Concepts.type(AdaptionCategory.EXPLOSION), PlayerAdaption.MAX_LEVEL);
        } else if (bossPath.contains("warden")) {
            data.levels.put(Concepts.type(AdaptionCategory.MAGIC), PlayerAdaption.MAX_LEVEL);
        } else if (DraconicCompat.GUARDIAN_ID.equals(bossPath)) {
            data.levels.put(Concepts.type(AdaptionCategory.EXPLOSION), PlayerAdaption.MAX_LEVEL);
            data.levels.put(Concepts.type(AdaptionCategory.PROJECTILE), PlayerAdaption.MAX_LEVEL);
            data.levels.put(Concepts.type(AdaptionCategory.FIRE), PlayerAdaption.MAX_LEVEL);
        } else {
            data.levels.put(Concepts.type(AdaptionCategory.MOB), PlayerAdaption.MAX_LEVEL);
        }

        data.adapted.add(existenceConcept);
        data.existenceAdapted.add(bossPath);

        data.addHistory(existenceConcept);
        data.targetRotation += (float) (Math.PI / 2);
        data.invalidateAdaptCount();

        player.heal(AdaptionConfig.ADAPTATION_HEAL_AMOUNT.get());
        playMaxVoice(player);
        saveToItem(player, data);

        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.existence",
                        Concepts.displayName(existenceConcept))
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.existence_details")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        ru.adaptionwheel.api.events.AdaptationCompleteEvent.post(player, existenceConcept, -1);

        startExistenceCinematic(player, bossPath);
    }

    private static boolean isInDarkness(Player player) {
        return player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 4;
    }

    private static final int THERMAL_REGEN_INTERVAL_TICKS = 40;

    private static final Map<UUID, Float> PROXIMITY_HEAT_CACHE = new HashMap<>();

    public static void grantComboMutation(ServerPlayer player, PlayerAdaption data, String concept) {
        if (data.isAdapted(concept)) {
            return;
        }
        grantOneTime(player, data, concept);
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.mutation_unlocked",
                        Concepts.chatName(concept))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Concepts.color(concept)))
                        .withBold(true)));
        playMaxVoice(player);
        spawnMutationBurst(player, concept);
        sync(player, data, true);
    }

    private static void tickFlight(ServerPlayer player, PlayerAdaption data) {
        if (!data.active(Concepts.MUTATION_FLIGHT)) {
            // Having the mutation but switching it off must also take the permission back; the old
            // early return left `mayfly` granted after the panel switch was turned off.
            if (data.isAdapted(Concepts.MUTATION_FLIGHT)
                    && !player.isCreative() && !player.isSpectator()) {
                if (FlightAbility.apply(player.getAbilities(), false)) {
                    player.onUpdateAbilities();
                }
                return;
            }
            if (player.getY() >= AdaptionConfig.FLIGHT_ALTITUDE.get()
                    && data.level(Concepts.contact(FLIGHT_MOB)) >= PlayerAdaption.MAX_LEVEL
                    && data.isAdapted(Concepts.debuff(FLIGHT_LEVITATION))) {
                grantComboMutation(player, data, Concepts.MUTATION_FLIGHT);
            }
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (FlightAbility.apply(player.getAbilities(), true)) {
            player.onUpdateAbilities();
        }
    }

    private static void spawnMutationBurst(ServerPlayer player, String concept) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        var particle = switch (concept) {
            case Concepts.MUTATION_THERMAL -> net.minecraft.core.particles.ParticleTypes.FLAME;
            case Concepts.MUTATION_AQUATIC -> net.minecraft.core.particles.ParticleTypes.SPLASH;
            case Concepts.MUTATION_IMPACT -> net.minecraft.core.particles.ParticleTypes.POOF;
            case Concepts.MUTATION_SEA_EYE -> net.minecraft.core.particles.ParticleTypes.BUBBLE;
            case Concepts.MUTATION_FLIGHT -> net.minecraft.core.particles.ParticleTypes.CLOUD;
            case Concepts.MUTATION_FIST -> net.minecraft.core.particles.ParticleTypes.CRIT;
            case Concepts.DIMENSION_DESTROY -> net.minecraft.core.particles.ParticleTypes.SONIC_BOOM;
            default -> net.minecraft.core.particles.ParticleTypes.END_ROD;
        };
        double ex = player.getX(), ey = player.getEyeY() - 0.3, ez = player.getZ();
        for (int i = 0; i < 24; i++) {
            double ang = i / 24.0 * Math.PI * 2;
            serverLevel.sendParticles(particle,
                    ex + Math.cos(ang), ey, ez + Math.sin(ang),
                    0, Math.cos(ang) * 0.15, 0.06, Math.sin(ang) * 0.15, 1.0);
        }
    }

    private static void tickThermalRegeneration(ServerPlayer player, PlayerAdaption data) {
        if (!AdaptionConfig.THERMAL_REGEN_ENABLED.get()) return;
        float factor = thermalFactor(player);
        if (factor > 0f) {
            spawnThermalParticles(player, factor);
        }
        if (factor <= 0f || player.getHealth() >= player.getMaxHealth()) {
            data.thermalHealingTimer = 0;
            return;
        }
        if (++data.thermalHealingTimer < THERMAL_REGEN_INTERVAL_TICKS) return;
        data.thermalHealingTimer = 0;
        player.heal((float) (AdaptionConfig.THERMAL_REGEN_HP_PER_SECOND.get()
                * factor * (THERMAL_REGEN_INTERVAL_TICKS / 20.0)));
    }

    private static float thermalFactor(Player player) {
        if (player.isInLava()) return 1.0f;

        float factor = 0f;
        if (player.isOnFire()) {
            factor = 0.7f;
        } else {
            boolean recompute = player.tickCount % 15 == 0 || !PROXIMITY_HEAT_CACHE.containsKey(player.getUUID());
            if (recompute) {
                PROXIMITY_HEAT_CACHE.put(player.getUUID(), scanProximityHeat(player));
            }

            float proximity = PROXIMITY_HEAT_CACHE.getOrDefault(player.getUUID(), 0f);
            factor = proximity > 0f ? 0.35f : 0f;
            if (factor <= 0f && player.level().getBiome(player.blockPosition()).value().getBaseTemperature() >= 1.5f) {
                factor = 0.15f;
            }
        }
        return factor;
    }

    private static void tickImpactStomp(ServerPlayer player, PlayerAdaption data) {
        boolean grounded = player.onGround();
        float lastFall = data.impactLastFallDistance;
        double minFall = AdaptionConfig.IMPACT_STOMP_MIN_FALL.get();
        if (grounded && !data.impactWasOnGround && !player.isInWater() && !player.isInLava()
                && lastFall >= minFall) {
            triggerImpactShockwave(player, lastFall);
        }
        data.impactLastFallDistance = grounded ? 0f : player.fallDistance;
        data.impactWasOnGround = grounded || player.isInWater() || player.isInLava() || player.onClimbable();
    }

    private static void triggerImpactShockwave(ServerPlayer player, float fallDistance) {
        if (!Float.isFinite(fallDistance) || fallDistance <= 0f) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        double radius = SynergyEffects.impactRadius(player, AdaptionConfig.IMPACT_STOMP_RADIUS.get());
        double rawDamage = (fallDistance - AdaptionConfig.IMPACT_STOMP_MIN_FALL.get() * 0.5)
                * AdaptionConfig.IMPACT_STOMP_DAMAGE_PER_BLOCK.get();
        if (!Double.isFinite(radius) || radius <= 0.0
                || !Double.isFinite(rawDamage) || rawDamage > Float.MAX_VALUE) {
            return;
        }
        float damage = Math.max(2f, (float) rawDamage);
        var source = player.damageSources().playerAttack(player);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player));
        for (LivingEntity target : targets) {
            // The list is a snapshot: an earlier hit (or another listener it invokes) may have
            // killed a later target before we reach it. Never begin a second hurt pipeline for a
            // dead/removed entity; NeoForge rejects entities killed from inside LivingDamageEvent.Pre.
            if (target == player || target.isRemoved() || !target.isAlive()) {
                continue;
            }
            if (!target.hurt(source, damage) || target.isRemoved() || !target.isAlive()) {
                continue;
            }
            Vec3 away = target.position().subtract(player.position());
            double horizontal = Math.max(0.25, Math.sqrt(away.x * away.x + away.z * away.z));
            target.push(away.x / horizontal * 1.2, 0.5, away.z / horizontal * 1.2);
        }

        double ex = player.getX(), ey = player.getY() + 0.1, ez = player.getZ();
        int steps = Math.max(10, (int) (radius * 6));
        for (int i = 0; i < steps; i++) {
            double ang = i / (double) steps * Math.PI * 2;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    ex + Math.cos(ang) * radius * 0.7, ey, ez + Math.sin(ang) * radius * 0.7,
                    1, Math.cos(ang) * 0.2, 0.05, Math.sin(ang) * 0.2, 0.02);
        }
        level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.7f, 0.6f);
    }

    private static float scanProximityHeat(Player player) {
        BlockPos base = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 2, 2))) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(BlockTags.FIRE) || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                    || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                return 1f;
            }
        }
        return 0f;
    }

    private static void spawnThermalParticles(ServerPlayer player, float factor) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        var random = player.getRandom();
        int interval = factor >= 1f ? 4 : factor >= 0.7f ? 8 : 25;
        if (player.tickCount % interval != 0) return;
        var particle = factor >= 1f ? net.minecraft.core.particles.ParticleTypes.FLAME
                : net.minecraft.core.particles.ParticleTypes.SMALL_FLAME;
        int count = factor >= 1f ? 2 : 1;
        serverLevel.sendParticles(particle,
                player.getX() + (random.nextDouble() - 0.5) * 0.6,
                player.getY() + random.nextDouble() * player.getBbHeight(),
                player.getZ() + (random.nextDouble() - 0.5) * 0.6,
                count, 0, 0.02, 0, 0);
    }

    private static void spawnWheelParticles(ServerPlayer player, PlayerAdaption data) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        boolean analyzing = !data.tasks.isEmpty();
        var random = player.getRandom();

        double cx = player.getX();
        double cy = player.getY() + player.getBbHeight() + 0.45;
        double cz = player.getZ();

        int adaptBonus = Math.min(data.getAdaptCount(), 35) / 5;
        int denominator = analyzing ? 3 : Math.max(3, 10 - adaptBonus);
        if (random.nextInt(denominator) == 0) {
            int count = analyzing ? 1 : 1 + adaptBonus;
            for (int i = 0; i < count; i++) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                        cx + (random.nextDouble() - 0.5) * 2.1,
                        cy + (random.nextDouble() - 0.5) * 1.4,
                        cz + (random.nextDouble() - 0.5) * 2.1,
                        1, 0.04, 0.04, 0.04, 0.0);
            }
        }

        if (analyzing && random.nextInt(3) == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 2.3 + random.nextDouble() * 2.3;
            double px = cx + Math.cos(angle) * dist;
            double pz = cz + Math.sin(angle) * dist;
            double py = cy + (random.nextDouble() - 0.5) * dist;

            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    px, py, pz, 0,
                    (cx - px) * 0.12, (cy - py) * 0.12, (cz - pz) * 0.12,
                    1.0);
        }
    }

    private static boolean isOnIce(Player player) {
        var blockState = player.level().getBlockState(player.blockPosition().below());
        return blockState.is(net.minecraft.tags.BlockTags.ICE);
    }

    private static boolean isStandingOnSlime(Player player) {
        return player.onGround()
                && player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement()).is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK);
    }

    private static boolean isInCobweb(Player player) {
        net.minecraft.world.phys.AABB bb = player.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(bb.minX + 1.0E-7), Mth.floor(bb.minY + 1.0E-7), Mth.floor(bb.minZ + 1.0E-7),
                Mth.floor(bb.maxX - 1.0E-7), Mth.floor(bb.maxY - 1.0E-7), Mth.floor(bb.maxZ - 1.0E-7))) {
            if (player.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.COBWEB)) {
                return true;
            }
        }
        return false;
    }

    private static void applyEnvEffects(ServerPlayer player, PlayerAdaption data) {
        if (data.isEnabled(Concepts.ENV_DROWN) && data.active(Concepts.ENV_DROWN)) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        // Only while actually in lava. Extinguishing every fire tick made Env_Lava a second,
        // silent immunity to fire arrows and burning, which is exactly what the fire/lava split
        // is meant to prevent.
        if (data.active(Concepts.ENV_LAVA) && player.isInLava()) {
            player.clearFire();
        }
        if (data.isEnabled(Concepts.ENV_STARVE) && data.active(Concepts.ENV_STARVE)
                && player.tickCount % 20 == 0) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20f);
            resetExhaustion(player.getFoodData());
        }
    }

    private static final java.lang.reflect.Field EXHAUSTION_FIELD;
    static {
        java.lang.reflect.Field f = null;
        try {
            f = net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
            f.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
        }
        EXHAUSTION_FIELD = f;
    }

    private static void resetExhaustion(net.minecraft.world.food.FoodData foodData) {
        if (EXHAUSTION_FIELD != null) {
            try {
                EXHAUSTION_FIELD.setFloat(foodData, 0f);
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static String effectKey(MobEffectInstance effect) {
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
        return key != null ? key.toString() : "minecraft:unknown";
    }

    private static int levelPenaltyTicks(String concept) {
        return concept.startsWith("Mine_") || concept.startsWith("Combat_") ? 60 : 180;
    }

    public static void startTask(ServerPlayer player, PlayerAdaption data, String concept, int timer) {

        if (data.adversityActive || !data.isEnabled(concept) || data.isAdapted(concept)
                || data.level(concept) >= PlayerAdaption.MAX_LEVEL) return;

        if (data.tasks.size() >= AdaptionConfig.MAX_SIMULTANEOUS_ADAPTATIONS.get()) return;
        for (AdaptionTask task : data.tasks) {
            if (task.concept.equals(concept)) return;
        }
        if (Concepts.isOffense(concept)) {
            timer = (int) (timer * Math.pow(1.55, data.level(concept)));
        } else {
            timer = timer + data.level(concept) * levelPenaltyTicks(concept);
        }
        data.tasks.add(new AdaptionTask(concept, Math.max(1, timer), Math.max(1, timer)));
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.analyzing", Concepts.chatName(concept))
                .withStyle(ChatFormatting.GOLD));
        sync(player, data, true);
    }

    public static void startOrAccelerate(ServerPlayer player, PlayerAdaption data, String concept, int baseTicks, boolean accelerate) {
        startOrAccelerate(player, data, concept, baseTicks, accelerate,
                (int) (AdaptionConfig.DEFENSE_ACCELERATION_SECONDS.get() * 20));
    }

    private static void startOrAccelerate(ServerPlayer player, PlayerAdaption data, String concept, int baseTicks,
                                          boolean accelerate, int accelerationTicks) {

        if (data.adversityActive || !data.isEnabled(concept) || data.isAdapted(concept)
                || data.level(concept) >= PlayerAdaption.MAX_LEVEL) return;
        AdaptionTask existing = null;
        for (AdaptionTask task : data.tasks) {
            if (task.concept.equals(concept)) { existing = task; break; }
        }
        if (existing == null) {
            if (data.tasks.size() >= AdaptionConfig.MAX_SIMULTANEOUS_ADAPTATIONS.get()) return;

            int timer = (int) Math.max(1, Math.round(
                    (baseTicks + data.level(concept) * levelPenaltyTicks(concept))
                            * Shedding.reattachFactor(data, concept)));
            data.tasks.add(new AdaptionTask(concept, Math.max(1, timer), Math.max(1, timer)));
            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.analyzing", Concepts.chatName(concept))
                    .withStyle(ChatFormatting.GOLD));
            sync(player, data, true);
        } else if (accelerate && accelerationTicks > 0) {
            existing.timer = Math.max(1, existing.timer - accelerationTicks);
        }
    }

    private static void completeTask(ServerPlayer player, PlayerAdaption data, String concept) {

        if (data.isAdapted(concept) && !Concepts.isLevelBased(concept)) {
            return;
        }
        grantConceptLevel(player, data, concept);
    }

    public static void grantConceptLevel(ServerPlayer player, PlayerAdaption data, String concept) {
        grantConceptUpTo(player, data, concept, -1);
    }

    public static void grantConceptUpTo(ServerPlayer player, PlayerAdaption data, String concept,
                                        int targetLevel) {
        applyGrant(player, data, concept, targetLevel);
        applyStats(player, data);

        applyEnvEffects(player, data);
        saveToItem(player, data);
        sync(player, data, true);
    }

    public static void grantToWheel(ServerPlayer player, PlayerAdaption fed, ItemStack wheel,
                                    String concept, int targetLevel) {
        if (Concepts.isDrop(concept)) {
            int wanted = (int) Math.ceil(AdaptionConfig.lootKills(targetLevel));
            int have = fed.kills(concept);
            if (wanted > have) {
                fed.killCounts.merge(concept, wanted - have, Integer::sum);
            }
        }
        applyGrant(player, fed, concept, targetLevel);
        saveToStack(wheel, fed);
    }

    public static PlayerAdaption readFrom(ItemStack stack) {
        PlayerAdaption data = new PlayerAdaption();
        if (stack != null && !stack.isEmpty()) {
            WheelData wheelData = stack.get(ModDataComponents.WHEEL_DATA);
            if (wheelData != null) {
                wheelData.loadInto(data);
            }
        }
        return data;
    }

    private static void applyGrant(ServerPlayer player, PlayerAdaption data, String concept,
                                   int targetLevel) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(Concepts.color(concept)));
        boolean isLevelBased = Concepts.isLevelBased(concept);
        if (isLevelBased) {
            int level;
            if (Concepts.isDrop(concept)) {
                level = Math.min(AdaptionConfig.dropLevelFromKills(data.kills(concept)), PlayerAdaption.MAX_LEVEL);
            } else {
                level = Math.min(targetLevel < 0 ? data.level(concept) + 1 : targetLevel,
                        PlayerAdaption.MAX_LEVEL);
            }
            data.levels.put(concept, level);
            MutableComponent message = level >= PlayerAdaption.MAX_LEVEL
                    ? Component.translatable("adaptionwheel.msg.adapted_max", Concepts.chatName(concept))
                    : Component.translatable("adaptionwheel.msg.adapted", Concepts.chatName(concept), level);
            player.sendSystemMessage(message.withStyle(style));
        } else {
            data.adapted.add(concept);
            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.adapted_once", Concepts.chatName(concept))
                    .withStyle(style));
        }
        player.heal(AdaptionConfig.ADAPTATION_HEAL_AMOUNT.get());
        data.addHistory(concept);
        data.targetRotation += (float) (Math.PI / 2);
        data.invalidateAdaptCount();
        boolean maxedOut = isLevelBased && data.level(concept) >= PlayerAdaption.MAX_LEVEL;
        if (maxedOut) {
            playMaxVoice(player);
        } else {
            playAdaptVoice(player, 1f, 1f);
        }
        ru.adaptionwheel.api.events.AdaptationCompleteEvent.post(player, concept,
                isLevelBased ? data.level(concept) : -1);
    }

    public static void grantAllAdaptations(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!wearingWheel(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.translatable("adaptionwheel.msg.all_adaption_no_wheel")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        PlayerAdaption data = data(serverPlayer);
        data.disabled.clear();
        data.tasks.clear();
        data.bossCombatTicks.clear();
        data.adversityActive = false;
        data.adversityTimer = 0;
        data.adversityCooldownTimer = 0;

        // One description of "everything", built from the live registries rather than a copy
        // written out here.
        //
        // A hand-written copy of the core concepts is the whole bug: it silently went stale the
        // moment Env_Inventory, Mutation_SeaEye, Mutation_Flight and the five Combat_Fist* stages
        // were added, and "eats the All Adaptation item and still is not adapted to it" is exactly
        // what a stale copy looks like from the outside. The same list is the denominator of the
        // Dimension Destroy gate, so the two can never disagree about what "all" means.
        AdaptableConcepts everything = adaptableConcepts(serverPlayer.serverLevel());
        for (Map.Entry<String, Integer> entry : everything.leveled().entrySet()) {
            data.levels.put(entry.getKey(), entry.getValue());
            data.addHistory(entry.getKey());
        }
        for (String concept : everything.oneTime()) {
            data.adapted.add(concept);
            data.addHistory(concept);
        }
        // Existence is two things at once: the one-time Existence_ concept, which is in `oneTime`
        // above, and the boss path the reflection gate reads.
        data.existenceAdapted.addAll(everything.existence());

        data.targetRotation += (float) (Math.PI * 30);
        data.invalidateAdaptCount();
        applyStats(serverPlayer, data);
        saveToItem(serverPlayer, data);

        LAST_VOICE_TICK.remove(serverPlayer.getUUID());
        playMaxVoice(serverPlayer);

        serverPlayer.sendSystemMessage(Component.translatable("adaptionwheel.msg.all_adaption_maxed")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sync(serverPlayer, data, true);
    }

    /**
     * Whether this entity type produces a {@link LivingEntity}.
     *
     * <p>This deliberately does not ask {@code EntityType#getBaseClass()}. In 1.21.1 that method is
     * {@code EntityTypeTest}'s default implementation and returns {@code Entity.class} for every
     * registered type, modded ones included, so the check the entity pass used to make --
     * {@code LivingEntity.class.isAssignableFrom(type.getBaseClass())} -- was false for all of
     * them. The pass therefore granted nothing at all, which is exactly how All Adaptation ended up
     * looking like it only knew the registry concepts: no {@code Contact_}, {@code Offense_NPC_} or
     * {@code Drop_NPC_} rows for any mob, vanilla or modded, and no warning anywhere.
     *
     * <p>The spawn category answers the question without constructing anything, because every
     * category other than {@link MobCategory#MISC} is a mob. MISC is the mixed bag -- the player,
     * every projectile, item and vehicle, and also living things such as the armour stand -- so it
     * is probed by creating one. That probe is what keeps the answer right for a modded mob that
     * registered itself as MISC instead of picking a real category.
     */
    public static boolean isLivingType(EntityType<?> type, Level level) {
        if (type == EntityType.PLAYER) {
            return true;
        }
        if (type.getCategory() != MobCategory.MISC) {
            return true;
        }
        try {
            return type.create(level) instanceof LivingEntity;
        } catch (RuntimeException e) {
            // A modded entity whose constructor throws must not take the whole grant down with it:
            // skipping one type is better than granting none.
            return false;
        }
    }

    /**
     * Everything the All Adaptation item grants, and therefore also the denominator of the
     * Dimension Destroy gate.
     *
     * <p>{@code leveled} holds one level per concept that stores a level, {@code oneTime} the
     * concepts that are simply present, and {@code existence} the boss paths that the existence
     * reflection gate reads (the matching {@code Existence_} concept is in {@code oneTime}).
     */
    public record AdaptableConcepts(Map<String, Integer> leveled, Set<String> oneTime,
                                    Set<String> existence) {

        public int total() {
            return leveled.size() + oneTime.size();
        }
    }

    private static volatile AdaptableConcepts cachedAdaptableConcepts;

    /**
     * The live "everything" list, built once per server and rebuilt when tags change.
     *
     * <p>Rebuilding it is not free: the {@link MobCategory#MISC} probe creates an entity. That is
     * fine for a one-off grant, but the Dimension Destroy check runs every tick, so the result is
     * cached. The cache is dropped on {@link TagsUpdatedEvent}, because the boss tag decides which
     * {@code Existence_} concepts exist.
     */
    public static AdaptableConcepts adaptableConcepts(Level level) {
        AdaptableConcepts cached = cachedAdaptableConcepts;
        if (cached != null) {
            return cached;
        }
        synchronized (AdaptionEvents.class) {
            if (cachedAdaptableConcepts == null) {
                cachedAdaptableConcepts = buildAdaptableConcepts(level);
            }
            return cachedAdaptableConcepts;
        }
    }

    private static AdaptableConcepts buildAdaptableConcepts(Level level) {
        Map<String, Integer> leveled = new HashMap<>();
        Set<String> oneTime = new HashSet<>();
        Set<String> existence = new HashSet<>();

        for (AdaptationDefinition definition : AdaptationRegistry.allDefinitions()) {
            if (definition.leveled()) {
                leveled.put(definition.concept(), definition.maxLevel());
            } else {
                oneTime.add(definition.concept());
            }
        }

        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key == null || effect.isBeneficial()) {
                continue;
            }
            // Debuffs are one-time. Putting them in levels too made adaptCount count the same
            // adaptation twice and kept WheelData.adaptCount() disagreeing with getAdaptCount().
            oneTime.add(Concepts.debuff(key.toString()));
        }

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null || !isLivingType(type, level)) {
                continue;
            }
            String path = key.toString();
            leveled.put(Concepts.contact(path), PlayerAdaption.MAX_LEVEL);
            leveled.put(Concepts.offense(path), PlayerAdaption.MAX_LEVEL);
            if (type != EntityType.PLAYER) {
                leveled.put(Concepts.drop(path), PlayerAdaption.MAX_LEVEL);
            }
            if (isBossType(type, path)) {
                oneTime.add(Concepts.existence(path));
                existence.add(path);
            }
        }

        return new AdaptableConcepts(leveled, oneTime, existence);
    }

    /**
     * The boss set All Adaptation treats as having an existence adaptation.
     *
     * <p>Kept in one place so growing the boss list is one edit; the path heuristics cover the
     * vanilla bosses on the versions where the boss tag is thin, and Draconic Evolution's guardian
     * is not in any tag at all.
     */
    public static boolean isBossType(EntityType<?> type, String path) {
        return type.is(Tags.EntityTypes.BOSSES)
                || DraconicCompat.GUARDIAN_ID.equals(path)
                || path.contains("wither")
                || path.contains("ender_dragon")
                || path.contains("warden");
    }

    /**
     * The Dimension Destroy gate: a share of everything the All Adaptation item can grant.
     *
     * <p>A fixed count was the bug. The old default was 450 and vanilla 1.21.1 has 331 concepts in
     * total, so the requirement sat above the ceiling and the feature could never unlock by playing
     * — while looking like an ordinary endgame threshold in the config. A percentage follows the
     * content: the same fraction of vanilla, and still "almost everything" in a modpack.
     *
     * @param adaptCount    the player's distinct adaptations
     * @param totalConcepts {@link AdaptableConcepts#total()}
     * @param percent       1..100
     */
    public static boolean reachesDimensionDestroy(int adaptCount, int totalConcepts, int percent) {
        if (totalConcepts <= 0) {
            // An empty registry must not read as "nothing left to do".
            return false;
        }
        return (long) adaptCount * 100L >= (long) totalConcepts * percent;
    }

    @SubscribeEvent
    public static void onServerStoppedResetAdaptableCache(ServerStoppedEvent event) {
        cachedAdaptableConcepts = null;
    }

    @SubscribeEvent
    public static void onTagsUpdatedResetAdaptableCache(TagsUpdatedEvent event) {
        cachedAdaptableConcepts = null;
    }

    private static final Map<UUID, Long> LAST_VOICE_TICK = new HashMap<>();
    private static final int VOICE_COOLDOWN_TICKS = 15;

    private static void playAdaptVoice(ServerPlayer player, float pitch, float volume) {
        playSoundThrottled(player, ModSounds.ADAPT_VOICE.get(), pitch, volume);
    }

    private static void playMaxVoice(ServerPlayer player) {
        playSoundThrottled(player, ModSounds.ADAPT_VOICE.get(), 0.68f, 1.2f);
    }

    private static void playSoundThrottled(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        float vol = (float) (double) AdaptionConfig.VOICE_VOLUME.get() / 100f;
        if (vol <= 0f) return;
        long now = player.level().getGameTime();
        Long last = LAST_VOICE_TICK.get(player.getUUID());
        if (last != null && now - last < VOICE_COOLDOWN_TICKS) return;
        LAST_VOICE_TICK.put(player.getUUID(), now);
        player.level().playSound(null, player.blockPosition(), sound,
                SoundSource.PLAYERS, volume * vol, pitch);
    }

    private static void completeAllTasks(ServerPlayer player, PlayerAdaption data) {
        List<AdaptionTask> tasks = new ArrayList<>(data.tasks);
        data.tasks.clear();
        for (AdaptionTask task : tasks) {
            completeTask(player, data, task.concept);
        }
    }

    private static void grantOneTime(ServerPlayer player, PlayerAdaption data, String concept) {

        if (data.isAdapted(concept)) {
            return;
        }
        data.adapted.add(concept);
        player.heal(AdaptionConfig.ADAPTATION_HEAL_AMOUNT.get());
        data.addHistory(concept);
        data.targetRotation += (float) (Math.PI / 2);
        data.invalidateAdaptCount();
        ru.adaptionwheel.api.events.AdaptationCompleteEvent.post(player, concept, -1);
        saveToItem(player, data);
    }

    public static void debugGrant(ServerPlayer player, String concept, int level) {
        PlayerAdaption d = data(player);
        if (Concepts.isOneTime(concept)) {
            d.adapted.add(concept);
        } else {
            d.levels.put(concept, Mth.clamp(level, 0, PlayerAdaption.MAX_LEVEL));
        }
        d.addHistory(concept);
        d.invalidateAdaptCount();
        applyStats(player, d);
        applyEnvEffects(player, d);
        saveToItem(player, d);
        sync(player, d, true);
    }

    public static boolean debugUngrant(ServerPlayer player, String concept) {
        PlayerAdaption d = data(player);
        boolean had = d.adapted.remove(concept);
        Integer level = d.levels.remove(concept);
        if (!had && level == null) {
            return false;
        }
        d.invalidateAdaptCount();
        applyStats(player, d);

        applyEnvEffects(player, d);

        if (ru.adaptionwheel.category.Concepts.MUTATION_FIST.equals(concept)) {
            d.instabreak = false;
            MartialArts.forget(player.getUUID());
        }
        saveToItem(player, d);
        sync(player, d, true);
        return true;
    }

    public static void debugReset(ServerPlayer player) {
        PlayerAdaption d = data(player);
        d.reset();
        applyStats(player, d);
        applyEnvEffects(player, d);
        MartialArts.forget(player.getUUID());
        wipeWheelItem(player, d);
        sync(player, d, false);
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (!wearingWheel(player)) {
            return;
        }
        PlayerAdaption data = data(player);
        if (data.active(Concepts.ENV_KNOCKBACK)) {

            event.setCanceled(true);
        } else if (AdaptionConfig.ENABLE_ENVIRONMENT.get() && !data.adversityActive) {

            startOrAccelerate(player, data, Concepts.ENV_KNOCKBACK,
                    (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        float base = event.getNewSpeed();

        float speed = FistMastery.breakSpeed(player, event.getNewSpeed(), event.getState());

        int laborLevel = SurfaceAdaptations.conceptLevel(player, Concepts.MINE_LABOR);
        if (laborLevel > 0 && AdaptionConfig.ENABLE_MINING.get()) {
            speed *= (float) (1.0 + AdaptionConfig.miningSpeedBonus(laborLevel) / 100.0);
        }
        if (speed != event.getNewSpeed()) {
            event.setNewSpeed(speed);
        }
        if (!player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) return;
        if (SurfaceAdaptations.hasLiquidAdaptation(player) && !player.onGround()) {
            event.setNewSpeed(event.getNewSpeed() * 5f);
        }

        if (SurfaceAdaptations.hasAquaticMastery(player)) {
            event.setNewSpeed(event.getNewSpeed() * 1.5f);
        }

        // TEMPORARY diagnostics for the block-revert investigation: enable with
        // -Daw.debugBreakSpeed on the run. Logs every input that feeds the destroy
        // speed on whichever side the event fired, once per second while breaking,
        // so a client/server divergence is visible as two lines with different numbers.
        if (Boolean.getBoolean("aw.debugBreakSpeed") && player.tickCount % 20 == 0) {
            String side = player.level().isClientSide ? "CLIENT" : "SERVER";
            String state = String.valueOf(event.getState());
            int idx = state.indexOf('[');
            if (idx >= 0) state = state.substring(0, idx);
            System.out.println("[aw-break] " + side
                    + " state=" + state
                    + " base=" + base
                    + " final=" + event.getNewSpeed()
                    + " fistTier=" + SurfaceAdaptations.fistTier(player)
                    + " instaUnlocked=" + SurfaceAdaptations.instabreakUnlocked(player)
                    + " instaActive=" + SurfaceAdaptations.instabreakActive(player)
                    + " labor=" + laborLevel
                    + " onGround=" + player.onGround()
                    + " eyeInWater=" + player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                    + " wearing=" + SurfaceAdaptations.wearingWheel(player));
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !player.level().isClientSide
                && wearingWheel(player)) {
            MobEffectInstance instance = event.getEffectInstance();
            if (instance != null && !instance.getEffect().value().isBeneficial()) {
                String concept = Concepts.debuff(effectKey(instance));
                // active(), NOT isAdapted(): the panel switch sets `disabled`, and isAdapted only
                // answers "do you have it", so the denial kept firing after the adaptation was
                // switched off and the debuff could still never be received. isAdapted is still
                // right for the altar, shedding and the panel itself, which must show what is owned.
                if (data(player).active(concept)) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                }
            }
        }
    }

    /**
     * Takes back every grant that does not live in {@link PlayerAdaption}, so losing the wheel
     * loses the effects too.
     *
     * <p>{@code data.reset()} cannot do this, and that is the trap: the attachment is only half of
     * what a worn wheel grants. Two grants sit outside it and both outlived the wheel.
     *
     * <ul>
     *   <li><b>Flight</b> is {@code Abilities.mayfly}, and the loop that grants it
     *       ({@code tickFlight}) only runs while the wheel is worn. So an unequip left nothing able
     *       to take it away, and the player kept flying, mid-air, indefinitely. Unequipping mid-air
     *       is also the case that strands them highest, which is why it is the one that gets
     *       reported.</li>
     *   <li><b>Synergy stat bonuses</b> are attribute modifiers installed by
     *       {@code SynergyEffects}. Its {@code forget} dropped the bookkeeping maps but not the
     *       modifier, so the bonus survived an unequip and lasted until logout.</li>
     * </ul>
     *
     * <p>Both are revoked unconditionally rather than "if the concept is still granted", because
     * right after {@code reset()} nothing is: the concepts may just as well live on a different
     * wheel, in which case the tick loop re-applies them a tick later. Revoking first is therefore
     * both correct and cheap — {@link FlightAbility#apply} reports whether anything actually
     * changed, so an abilities packet goes out only on a real transition.
     */
    private static void revokeWearableState(ServerPlayer player) {
        if (FlightAbility.apply(player.getAbilities(), false)) {
            player.onUpdateAbilities();
        }
        SynergyEffects.forget(player);
    }

    private static void applyStats(ServerPlayer player, PlayerAdaption data) {
        int count = data.getAdaptCount();

        applyStat(player.getAttribute(Attributes.MAX_HEALTH), HP_MODIFIER,
                count * AdaptionConfig.BONUS_HP_PCT.get() / 100.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyStat(player.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER,
                count * AdaptionConfig.BONUS_ARMOR_FLAT.get(),
                AttributeModifier.Operation.ADD_VALUE);

        // Apply the requested movement bonuses directly as attributes (no potion effects); both
        // scale linearly and stop increasing after the 400-adaptation progression cap.
        applyStat(player.getAttribute(Attributes.MOVEMENT_SPEED), ADAPTATION_COUNT_SPEED_MODIFIER,
                AdaptationProgression.movementSpeedBonus(count), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        applyStat(player.getAttribute(Attributes.JUMP_STRENGTH), ADAPTATION_COUNT_JUMP_MODIFIER,
                AdaptationProgression.jumpStrengthBonus(count), AttributeModifier.Operation.ADD_VALUE);

        boolean liquid = data.isEnabled(Concepts.ENV_LIQUID) && data.active(Concepts.ENV_LIQUID);
        boolean aquatic = AdaptionConfig.ENABLE_MUTATION_AQUATIC.get()
                && data.active(Concepts.MUTATION_AQUATIC);

        // These two adaptations occupy the same tier, and Aquatic Mastery replaces the Liquid
        // bonus rather than stacking on it.
        //
        // The numbers are small on purpose, because all three of these knobs multiply through
        // vanilla's water formula rather than adding to it: efficiency raises the swim accel AND
        // the fraction of velocity the water keeps (LivingEntity.travel: f4 += (0.546 - f4) * eff,
        // f5 += (getSpeed() - f5) * eff), SWIM_SPEED then multiplies that accel again, and
        // getSpeed() already carries the wheel's +80% movement-speed progression at the cap. At
        // the old values (efficiency 0.75, swim +1.0) the steady state worked out to ~8.7 blocks/s
        // — 4x vanilla swimming and well past a land sprint — which is what "insanely fast
        // underwater" was. These settle at ~3.2 (Liquid) and ~4.8 (Aquatic) at a maxed wheel.
        double swim = aquatic
                ? Math.clamp(AdaptionConfig.AQUATIC_SWIM_SPEED_BONUS.get(), 0.0, 0.35)
                : 0.0;
        double efficiency = aquatic ? 0.45 : liquid ? 0.22 : 0.0;
        applyStat(player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY), SWIM_MODIFIER,
                efficiency, AttributeModifier.Operation.ADD_VALUE);
        applyStat(player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), LIQUID_SPEED_MODIFIER,
                swim, AttributeModifier.Operation.ADD_VALUE);
        // The two names are kept as one modifier each for config compatibility, but only one of
        // them is ever non-zero now; the old saved modifiers are removed by the amount check.
        applyStat(player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), AQUATIC_SWIM_SPEED_MODIFIER,
                0.0, AttributeModifier.Operation.ADD_VALUE);
        applyStat(player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY), AQUATIC_SWIM_EFFICIENCY_MODIFIER,
                0.0, AttributeModifier.Operation.ADD_VALUE);

        applyStat(player.getAttribute(Attributes.SUBMERGED_MINING_SPEED), SUBMERGED_MINING_MODIFIER,
                liquid ? 0.8 : 0.0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void applyStat(AttributeInstance attribute, ResourceLocation id,
                                  double amount, AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(id);
        if (existing != null && existing.amount() == amount && existing.operation() == operation) {
            return;
        }
        attribute.removeModifier(id);
        if (amount != 0) {
            attribute.addPermanentModifier(new AttributeModifier(id, amount, operation));
        }
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        boolean forward = left.is(ModItems.MAHORAGA_WHEEL_WOOD.get()) && right.is(Items.GOLD_INGOT);
        boolean backward = left.is(Items.GOLD_INGOT) && right.is(ModItems.MAHORAGA_WHEEL_WOOD.get());
        if (!forward && !backward) {
            return;
        }
        event.setOutput(new ItemStack(ModItems.MAHORAGA_WHEEL.get()));
        event.setCost(WHEEL_GOLD_COST);
        event.setMaterialCost(1);
    }

    private static void sync(ServerPlayer player, PlayerAdaption data, boolean wearing) {
        syncAdaption(player, data, wearing);
    }

    public static void syncAdaption(ServerPlayer player, PlayerAdaption data, boolean wearing) {

        Map<String, Integer> existenceProgress = visibleBossProgress(player, data);
        // The ability list is decided here, not on the client. The client's mirror is a copy of the
        // attachment that arrives once a second and can lag a grant, a switch or a wheel change, and
        // a HUD card drawn from a stale copy promises a key that the server will refuse. One list
        // per sync means the card and the gate cannot disagree.
        List<String> abilities = new ArrayList<>();
        if (DiveAbility.unlocked(data)) {
            abilities.add(DiveAbility.ID);
        }
        AdaptionSyncPayload payload = new AdaptionSyncPayload(
                wearing,
                data.adversityActive,
                data.getAdaptCount(),
                data.adversityTimer,
                data.adversityCooldownTimer,
                data.wheelRotation,
                new ArrayList<>(data.tasks),
                new java.util.HashMap<>(data.levels),
                new ArrayList<>(data.adapted),
                new ArrayList<>(data.history),
                existenceProgress != null ? existenceProgress : java.util.Map.of(),
                (int) (AdaptionConfig.EXISTENCE_REQUIRED_SECONDS.get() * 20),
                data.instabreak,
                SurfaceAdaptations.instabreakUnlocked(player),
                FistMastery.tierProgress(player.getUUID(), data),
                fistProgressTotal(data),
                combatFistDone(data),
                combatFistTotal(data),
                new ArrayList<>(data.disabled),
                abilities
        );
        AdaptionSyncPayload.sendTo(player, payload);
    }

    /**
     * Kills banked toward the punching fist's next level, for the 1 Hz sync.
     *
     * <p>These used to travel only on {@code CombatFistProgressPayload}, which is sent when a kill
     * lands. So on relogin the mirror held zero and the row came back as a full bar until the player
     * killed something. The per-kill payload stays, because it is what makes a kill show up
     * immediately; this is the recovery path, and it is why the row no longer depends on it.
     */
    private static int combatFistDone(PlayerAdaption data) {
        int tier = ru.adaptionwheel.server.MartialArts.trainingTier(data);
        if (tier < 0) {
            return 0;
        }
        return data.progress.getOrDefault(ru.adaptionwheel.data.Extras.punchingKey(tier), 0);
    }

    /**
     * What the player still has to bank, as "done of total".
     *
     * <p>This used to return 0 for a maxed stage, and 0 is what the HUD reads as "MAX", so the row
     * said {@code 100.0% MAX} and stopped there -- which is exactly the row that needs to say most:
     * the ladder has just handed over and the player has no idea what the next material costs. It
     * now reports the <i>next</i> stage's first level instead, so the row reads {@code 0/1 kills}
     * against the stage it is about to train.
     *
     * <p>Only a finished ladder still reports 0, because then there genuinely is nothing left.
     */
    private static int combatFistTotal(PlayerAdaption data) {
        int tier = ru.adaptionwheel.server.MartialArts.trainingTier(data);
        if (tier < 0) {
            return 0;
        }
        int level = data.level(ru.adaptionwheel.category.CombatFistTiers.concept(tier));
        if (level >= PlayerAdaption.MAX_LEVEL) {
            return 0;
        }
        return AdaptionConfig.fistKillsForNextLevel(tier, level);
    }

    private static int fistProgressTotal(PlayerAdaption data) {
        int tier = FistMastery.currentTier(data);
        if (tier < 0) {
            return 0;
        }
        int level = data.level(ru.adaptionwheel.category.FistTiers.concept(tier));
        if (level >= PlayerAdaption.MAX_LEVEL) {
            return 0;
        }
        return AdaptionConfig.fistBlocksForNextLevel(tier, level);
    }
}
