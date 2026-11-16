package ru.adaptionwheel.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.SurfaceAdaptations;
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
import java.util.EnumSet;
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

    private static final Identifier HP_MODIFIER = Identifier.fromNamespaceAndPath("adaptionwheel", "hp");
    private static final Identifier ARMOR_MODIFIER = Identifier.fromNamespaceAndPath("adaptionwheel", "armor");
    private static final Identifier ADAPTATION_COUNT_SPEED_MODIFIER =
            Identifier.fromNamespaceAndPath("adaptionwheel", "adaptation_count_speed");
    private static final Identifier ADAPTATION_COUNT_JUMP_MODIFIER =
            Identifier.fromNamespaceAndPath("adaptionwheel", "adaptation_count_jump");
    private static final Identifier SWIM_MODIFIER = Identifier.fromNamespaceAndPath("adaptionwheel", "swim");
    private static final Identifier LIQUID_SPEED_MODIFIER = Identifier.fromNamespaceAndPath("adaptionwheel", "liquid_speed");
    private static final Identifier SUBMERGED_MINING_MODIFIER =
            Identifier.fromNamespaceAndPath("adaptionwheel", "submerged_mining");
    private static final Identifier AQUATIC_SWIM_SPEED_MODIFIER =
            Identifier.fromNamespaceAndPath("adaptionwheel", "aquatic_swim_speed");
    private static final Identifier AQUATIC_SWIM_EFFICIENCY_MODIFIER =
            Identifier.fromNamespaceAndPath("adaptionwheel", "aquatic_swim_efficiency");

    private static final Map<UUID, Float> PENDING_RESPAWN_HEALTH = new HashMap<>();
    private static final Set<UUID> PENDING_RESPAWN_ARMED = new HashSet<>();

    /**
     * The categories where a maxed ladder may be made absolute without stalling another ladder:
     * a projectile is not a direct attacker, and {@code OTHER} is the bucket for everything no
     * category claims.
     */
    private static final Set<AdaptionCategory> TOTAL_IMMUNITY_CATEGORIES =
            EnumSet.of(AdaptionCategory.PROJECTILE, AdaptionCategory.OTHER);

    private static final long WHEEL_GOLD_COST = 10L;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("AdaptionWheel/Events");

    private static final Map<UUID, Long> DIMENSION_SLASH_LAST = new HashMap<>();
    private static final int DIMENSION_SLASH_COOLDOWN_TICKS = 20;
    private static final Map<UUID, long[]> WEARING_CACHE = new HashMap<>();

    private static final int WHEEL_SWAP_CONFIRM_TICKS = 5;

    private static final int WHEEL_SWAP_LOG_INTERVAL = 100;
    private static int lastWheelSwapLog = -WHEEL_SWAP_LOG_INTERVAL;

    private static boolean wearingWheel(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        long stamp = serverPlayer.tickCount;
        long[] cached = WEARING_CACHE.get(serverPlayer.getUUID());
        if (cached != null && cached[0] == stamp) {
            return cached[1] != 0;
        }
        boolean wearing = ru.adaptionwheel.compat.WheelSlots.isWorn(serverPlayer);
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
        return data(serverPlayer).level(concept);
    }

    public static Optional<ItemStack> getWheelStack(Player player) {
        return ru.adaptionwheel.compat.WheelSlots.findWorn(player);
    }

    private static PlayerAdaption data(ServerPlayer player) {
        return player.getData(ru.adaptionwheel.data.AttachmentTypes.ADAPTION);
    }

    public static boolean hasAdaptation(Player player, String concept) {
        if (!(player instanceof ServerPlayer serverPlayer) || !wearingWheel(serverPlayer)) {
            return false;
        }
        return data(serverPlayer).isAdapted(concept);
    }

    private static void saveToItem(Player player, PlayerAdaption data) {
        getWheelStack(player).ifPresent(stack -> saveToStack(stack, data));
    }

    public static void saveToStack(ItemStack stack, PlayerAdaption data) {
        stack.set(ModDataComponents.WHEEL_DATA.get(), WheelData.fromPlayer(data));
    }

    private static void loadFromItem(Player player, PlayerAdaption data) {
        getWheelStack(player).ifPresent(stack -> {
            WheelData wd = stack.get(ModDataComponents.WHEEL_DATA);
            if (wd != null) wd.loadInto(data);
        });
    }

    /**
     * The one-time environment immunity for a damage category, or null when the category has none.
     *
     * <p>This was a seven-arm if-chain, and on this branch it is written out **twice** -- once in
     * the incoming-damage handler (which cancels) and once in the pre-damage handler (which zeroes
     * the new damage). Two copies of one rule is how the two halves end up disagreeing, so both now
     * ask this table. It is public and static so a gametest can enumerate it: the handlers cannot be
     * tested directly, because a gametest cannot create a player while Curios is installed.
     */
    public static String envImmunityConcept(AdaptionCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case FALL -> Concepts.ENV_FALL;
            case STARVE -> Concepts.ENV_STARVE;
            case DROWN -> Concepts.ENV_DROWN;
            // Fire is an environment adaptation now, not a damage ladder. Lava is the
            // source-specific override in hasFullImmunity, not a second category concept.
            case FIRE -> Concepts.ENV_FIRE;
            case SUFFOCATE -> Concepts.ENV_SUFFOCATE;
            case VOID -> Concepts.ENV_VOID;
            case CONTACT -> Concepts.ENV_THORNS;
            default -> null;
        };
    }

    /**
     * True when a one-time adaptation makes this whole damage category harmless.
     *
     * <p>Only the one-time {@code Env_*} concepts answer here. Fire belongs to that family too:
     * {@link Concepts#ENV_FIRE} is a one-time adaptation stored in {@code adapted}, not a leveled
     * key in {@code levels}, so {@code active()} is exactly the right accessor for it. (The damage
     * type is still called {@code FIRE}; the concept is not {@code Type_FIRE}.) Leveled
     * {@code Type_*} concepts are the other half of the same idea and are handled by
     * {@link #isMaxedImmunity}.
     *
     * <p>FIRE is the one category with two adaptations, because lava and fire are learned and
     * should be lived through separately: {@link Concepts#ENV_LAVA} comes only from actual
     * immersion in lava, while {@link Concepts#ENV_FIRE} comes from every other fire hit. A fire
     * arrow must not grant lava immunity, and standing in lava must not train the fire adaptation.
     */
    public static boolean hasFullImmunity(PlayerAdaption data, AdaptionCategory category, DamageSource source) {
        if (category == AdaptionCategory.FIRE && isLavaDamage(source)) {
            return data.active(Concepts.ENV_LAVA);
        }
        String immunity = envImmunityConcept(category);
        return immunity != null && data.active(immunity);
    }

    /**
     * True when a maxed leveled adaptation makes this incoming hit harmless outright.
     *
     * <p>The other half of {@link #hasFullImmunity}: that one covers the one-time {@code Env_*}
     * immunities, this one the last rung of a {@code Type_*} or {@code Contact_<mob>} ladder.
     *
     * <p>Three deliberate exclusions: {@code BYPASSES_INVULNERABILITY} is the {@code /kill}
     * command, not a phenomenon to adapt to; lava belongs to the one-time {@code Env_Lava}; and the
     * one-time families have no level eight to ask about.
     */
    public static boolean isMaxedImmunity(PlayerAdaption data, AdaptionCategory category, DamageSource source) {
        if (data == null || category == null || source == null) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (contactImmunityApplies(data, source)) {
            return true;
        }
        switch (category) {
            case FALL, STARVE, DROWN, FIRE, SUFFOCATE, VOID, CONTACT -> {
                return false;
            }
            default -> {
            }
        }
        return data.levelOrZero(Concepts.type(category)) >= PlayerAdaption.MAX_LEVEL;
    }

    /**
     * True when a completed {@code Contact_<mob>} adaptation makes this hit harmless.
     *
     * <p>A completed contact ladder is ALWAYS total immunity, independent of
     * {@code fullImmunityAtMax}. The {@code contactDefense.immunityLevel} knob still offers an
     * EARLIER cutoff; 0 means "only the completed ladder".
     */
    public static boolean contactImmunityApplies(PlayerAdaption data, DamageSource source) {
        if (data == null || source == null) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (!(source.getDirectEntity() instanceof LivingEntity attacker)) {
            return false;
        }
        int contactLevel = data.levelOrZero(Concepts.contact(entityPath(attacker.getType())));
        if (contactLevel >= PlayerAdaption.MAX_LEVEL) {
            return true;
        }
        int cutoff = AdaptionConfig.CONTACT_IMMUNITY_LEVEL.get();
        return cutoff > 0 && contactLevel >= cutoff;
    }

    /**
     * True when a completed adaptation of a category that is safe to make absolute is maxed.
     *
     * <p>Only {@link #TOTAL_IMMUNITY_CATEGORIES} answer here. Possible stalls only exist in the
     * categories that are trained by being hit; a projectile is not a direct attacker, and
     * {@code OTHER} is the residue no category claims, so their level eight stays total immunity
     * even with {@code fullImmunityAtMax} off.
     */
    public static boolean categoryCapstoneApplies(PlayerAdaption data, AdaptionCategory category, DamageSource source) {
        if (data == null || category == null || source == null) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (!TOTAL_IMMUNITY_CATEGORIES.contains(category)) {
            return false;
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
        ServerLevel level = player.level();
        var random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = player.getX() + (random.nextDouble() - 0.5D) * 32.0D;
            double z = player.getZ() + (random.nextDouble() - 0.5D) * 32.0D;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (y <= level.getMinY() + 1) {
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
        BlockPos spawn = level.getRespawnData().pos();
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()),
                level.getMinY() + 1);
        teleportRescue(player, level, spawn.getX() + 0.5D, y, spawn.getZ() + 0.5D);
        data.lastVoidRescueTick = now;
    }

    private static void teleportRescue(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot(), false);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        player.setAirSupply(player.getMaxAirSupply());
        level.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static String entityPath(EntityType<?> type) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
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
            return linked != null && data.existenceAdapted.contains(linked) ? linked : null;
        }
        return null;
    }

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SynergyEffects.tryAbsorbVoid(player, event.getSource())) {

            event.setCanceled(true);
            return;
        }
        if (event.getEntity().level().isClientSide()) return;
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

        String adaptedBoss = adaptedExistenceTarget(data, source);
        if (adaptedBoss != null) {
            reflectAttack(player, source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity(),
                    event.getContainer().getNewDamage());
            event.setCanceled(true);
            return;
        }

        // Both of these come AFTER the existence reflection, for the same reason the config-gated
        // check below is last: a hit that is about to be reflected already deals the player no
        // damage, and cancelling it first would silently remove the only offence an existence
        // adaptation has.
        if (contactImmunityApplies(data, source)) {
            event.setCanceled(true);
            return;
        }
        if (categoryCapstoneApplies(data, category, source)) {
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
        if (event.getEntity().level().isClientSide()) return;
        DamageSource source = event.getSource();
        float newDamage = event.getNewDamage();

        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof ServerPlayer attacker
                && event.getEntity() instanceof LivingEntity target
                && target != attacker
                && wearingWheel(attacker)) {
            applyFistDamage(attacker, target, event);
            applyOffense(attacker, target, event);
        }

        if (!(event.getEntity() instanceof ServerPlayer player) || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);

        String reflectedBoss = adaptedExistenceTarget(data, source);
        if (reflectedBoss != null) {
            Entity reflectSource = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
            reflectAttack(player, reflectSource, newDamage);
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
            mobPath = entityPath(livingDirect.getType());
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
            // levelOrZero, not level(): this loop grants the Lv5 heal and the Lv8 i-frames, both of
            // which are effects, so a switched-off adaptation must not keep paying out.
            bestLevel = Math.max(bestLevel, data.levelOrZero(concept));
        }
        if (bestLevel >= 5) {
            double ratio = AdaptionConfig.defenseHealRatio(bestLevel) / 100.0;
            if (ratio > 0) {
                player.heal(newDamage * (float) ratio);
            }
        }
        if (bestLevel >= 8) {
            // Configurable, and 10 rather than the original's 2 seconds on purpose: a window that
            // outlasts a melee swing re-arms before it lapses, which turns an Lv8 capstone into
            // permanent immunity to that damage category rather than a reduction.
            int lv8IFrames = AdaptionConfig.DEFENSE_LV8_IFRAMES_TICKS.get();
            if (lv8IFrames > 0) {
                player.setInvulnerableTime(Math.max(player.getInvulnerableTime(), lv8IFrames));
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
            // the fire/lava split is what keeps a fire arrow from making lava harmless.
            if (category == AdaptionCategory.FIRE && !lavaDamage) {
                startTask(player, data, Concepts.ENV_FIRE, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
            }
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

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !target.isAlive() || target.isRemoved()) {
            return;
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || target == attacker
                || !wearingWheel(attacker)) {
            return;
        }

        PlayerAdaption data = data(attacker);
        if (data.levelOrZero(Concepts.offense(pathOf(target))) < 8
                || attacker.getRandom().nextFloat() * 100f >= AdaptionConfig.DIMENSION_SLASH_CHANCE.get()
                || !claimDimensionSlash(attacker)) {
            return;
        }

        // This extra hit must happen after the original Pre event has returned. Server.execute()
        // runs inline when called on the server thread, so using it from Pre re-entered hurtServer
        // and could kill this same target before NeoForge's Pre lifecycle check.
        float slashDamage = Math.max(1f, (float) (target.getMaxHealth()
                * AdaptionConfig.DIMENSION_SLASH_HP_PERCENT.get() / 100.0));
        target.hurt(attacker.damageSources().playerAttack(attacker), slashDamage);
        attacker.sendSystemMessage(Component.translatable("adaptionwheel.msg.dimension_slash")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
        attacker.level().playSound(null, target.blockPosition(), ModSounds.DIMENSION_CUT.get(),
                SoundSource.PLAYERS, 1f, 1f);
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
        player.setInvulnerableTime(60);

        if (player.level() instanceof ServerLevel serverLevel) {
            var random = player.getRandom();
            double ex = player.getX(), ey = player.getEyeY(), ez = player.getZ();
            for (int i = 0; i < 16; i++) {
                double ang = i / 16.0 * Math.PI * 2;
                double dx = Math.cos(ang), dz = Math.sin(ang);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
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
        if (!(target instanceof ServerPlayer player) || player.level().isClientSide() || newHealth >= current) {
            target.setHealth(newHealth);
            return;
        }
        if (!wearingWheel(player)) {
            target.setHealth(newHealth);
            return;
        }

        PlayerAdaption data = data(player);
        boolean existenceEnabled = AdaptionConfig.ENABLE_EXISTENCE.get();

        if (existenceEnabled && data.existenceAdapted.contains(DraconicCompat.GUARDIAN_ID)) {
            return;
        }
        if (!AdaptionConfig.ENABLE_CHAOS_GUARDIAN.get()) {
            target.setHealth(newHealth);
            return;
        }

        float raw = current - newHealth;

        if (existenceEnabled) {
            noteBossEncounter(data, DraconicCompat.GUARDIAN_ID);
        }
        int defenseTicks = (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20);
        startOrAccelerate(player, data, Concepts.type(AdaptionCategory.EXPLOSION), defenseTicks, true);
        startOrAccelerate(player, data, Concepts.contact(DraconicCompat.GUARDIAN_ID), defenseTicks, true);

        if (data.adversityActive || data.adversityCooldownTimer > 0) {
            target.setHealth(Math.max(current - raw, 0f));
            return;
        }

        float reduction = 0f;
        int bestLevel = 0;
        int explosionLevel = data.level(Concepts.type(AdaptionCategory.EXPLOSION));
        if (explosionLevel > 0) {
            reduction += (float) (AdaptionConfig.defenseReduction(explosionLevel) / 100.0);
            bestLevel = explosionLevel;
        }
        int contactLevel = data.level(Concepts.contact(DraconicCompat.GUARDIAN_ID));
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
        if (reflectTarget instanceof LivingEntity living && reflectTarget != player) {
            float multiplier = (float) (double) AdaptionConfig.EXISTENCE_REFLECT_MULTIPLIER.get();
            living.hurt(player.damageSources().playerAttack(player), damage * multiplier);

            living.knockback(1.2, player.getX() - living.getX(), player.getZ() - living.getZ(),
                    player.damageSources().playerAttack(player), damage * multiplier);
            // 1 tick, not 10. The original grants max(immuneTime, 2) here -- two Terraria ticks,
            // about 33ms. Reflection denies the boss its attack; it does not shield the player, and
            // half a second of invulnerability per reflected hit was standing in for a cooldown.
            int reflectIFrames = AdaptionConfig.EXISTENCE_REFLECT_IFRAMES_TICKS.get();
            if (reflectIFrames > 0) {
                player.setInvulnerableTime(Math.max(player.getInvulnerableTime(), reflectIFrames));
            }
        }
    }

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
        PlayerAdaption data = data(attacker);
        float bonus = MartialArts.bonus(attacker, data);
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
        }

        if (level < PlayerAdaption.MAX_LEVEL && AdaptionConfig.ENABLE_OFFENSE.get()) {
            int baseTicks = (int) (AdaptionConfig.OFFENSE_ANALYSIS_SECONDS.get() * 20);
            int timer = (int) (baseTicks * Math.pow(1.55, level));
            startOrAccelerate(attacker, data, concept, timer, false,
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
        if (event.getEntity().level().isClientSide()) return;
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
        if (event.getEntity().level().isClientSide()) return;
        ServerPlayer player = event.getAttackingPlayer() instanceof ServerPlayer sp ? sp : null;
        if (player == null || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);
        if (data.adversityActive || !AdaptionConfig.ENABLE_LOOT.get()) return;

        String concept = Concepts.drop(pathOf(event.getEntity()));
        int level = data.levelOrZero(concept);
        if (level <= 0) return;

        int base = event.getDroppedExperience();
        if (base <= 0) return;
        double bonusPct = AdaptionConfig.lootBonus(level);
        event.setDroppedExperience((int) Math.min(base * (1.0 + bonusPct / 100.0), Integer.MAX_VALUE / 4));
    }

    private static void startDropTask(ServerPlayer player, PlayerAdaption data, String concept) {
        if (data.level(concept) >= PlayerAdaption.MAX_LEVEL
                || data.tasks.size() >= AdaptionConfig.MAX_SIMULTANEOUS_ADAPTATIONS.get()) return;
        for (AdaptionTask task : data.tasks) {
            if (task.concept.equals(concept)) return;
        }
        data.tasks.add(new AdaptionTask(concept, 20, 20));
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.analyzing", Concepts.chatName(concept))
                .withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onMobDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide()) return;
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

        java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> lootTable = dead.getLootTable();
        if (lootTable.isEmpty()) return;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTableKey = lootTable.get();
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
                    .getLootTable(lootTableKey).getRandomItems(params);
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
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        ru.adaptionwheel.data.PlayerAdaption data = data(player);
        boolean wearing = wearingWheel(player);
        if (wearing) {
            data.wasWearing = true;
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

        SynergyEffects.forget(player);
        FistMastery.forget(id);
        MartialArts.forget(id);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        float maxHealth = player.getMaxHealth();
        if (maxHealth > 0f) {
            PENDING_RESPAWN_HEALTH.put(player.getUUID(), player.getHealth() / maxHealth);
        }
        if (!AdaptionConfig.RESET_ADAPTATIONS_ON_DEATH.get()) return;

        PlayerAdaption data = data(player);
        data.reset();
        data.wasWearing = wearingWheel(player);
        applyStats(player, data);
        wipeWheelItem(player, data);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (PENDING_RESPAWN_HEALTH.containsKey(event.getEntity().getUUID())) {
            PENDING_RESPAWN_ARMED.add(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
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
            // The cache, the per-concept switches and both fist counters are player-bound and are
            // not stored in WheelData, so the full reset here used to destroy them with no copy
            // on the item. Keep them across the unequip.
            data.resetForUnequip();
            revokeWearableState(player);
            applyStats(player, data);
            sync(player, data, false);
        }

        if (wearing && !player.isDeadOrDying()) {
            ItemStack equipped = getWheelStack(player).orElse(null);
            if (equipped != null && data.equippedStack != null) {

                if (ItemStack.isSameItemSameComponents(equipped, data.equippedStack)) {
                    data.wheelSwapMismatchTicks = 0;
                } else if (++data.wheelSwapMismatchTicks >= WHEEL_SWAP_CONFIRM_TICKS) {
                    if (player.tickCount - lastWheelSwapLog >= WHEEL_SWAP_LOG_INTERVAL) {
                        lastWheelSwapLog = player.tickCount;
                        LOGGER.info("{} swapped the equipped Adaption Wheel, "
                                + "adopting the data of the new one", player.getName().getString());
                    }
                    saveToStack(data.equippedStack, data);
                    // A swap is still a wheel transition, not a player wipe: the cache and the
                    // switch states belong to the player and survive it.
                    data.resetForUnequip();
                    // The new wheel may grant neither flight nor the synergies the old one did,
                    // so state living outside the attachment has to be taken back here too.
                    // Whatever the new wheel does grant is re-applied by the tick loop after.
                    revokeWearableState(player);
                    loadFromItem(player, data);
                    applyStats(player, data);
                    sync(player, data, true);
                    data.wheelSwapMismatchTicks = 0;
                }
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

        // Push timer jumps and completions at 5 Hz instead of making the HUD wait for the 1 Hz full sync.
        // Include the full sync's filtered existence counters so the HUD cannot blink between the two.
        if (!data.tasks.isEmpty() && player.tickCount % TaskProgressPayload.PUSH_EVERY_TICKS == 0) {
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
        applySurfaceEffects(player, data);

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
                && data.isAdapted(Concepts.ENV_LIQUID)
                && data.isAdapted(Concepts.ENV_DROWN)
                && data.isAdapted(Concepts.ENV_LAVA)) {
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
                        adaptableConcepts(player.level()).total(),
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
        int injureLevel = data.level(Concepts.SELF_DAMAGE);
        if (player.getHealth() <= player.getMaxHealth() * hpPct) {
            if (injureLevel < PlayerAdaption.MAX_LEVEL) {
                startTask(player, data, Concepts.SELF_DAMAGE, (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20));
            }
        }
        if (injureLevel > 0 && player.getHealth() < player.getMaxHealth()) {
            data.healingTimer++;
            if (data.healingTimer >= 60) {
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
        Set<String> nearby = nearbyBossPaths(player, data);
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
     * The boss counters the client is allowed to see, and so the ones the HUD may draw a row for.
     *
     * <p>Extracted because two senders once disagreed about this same map and the row flickered
     * between them: the full sync filtered to bosses that are nearby right now, while a shorter push
     * sent the raw counters, and {@code accumulateBossCombat} deliberately keeps an entry for a boss
     * that has walked away. The counters persisting is correct -- walking away from a fight should
     * not throw the analysis away. What is filtered here is only what is <i>shown</i>. Same lesson as
     * {@code envImmunityConcept} having been written out twice.
     */
    private static Map<String, Integer> visibleBossProgress(ServerPlayer player, PlayerAdaption data) {
        if (!AdaptionConfig.ENABLE_EXISTENCE.get() || data.bossCombatTicks.isEmpty()) {
            return java.util.Map.of();
        }
        Set<String> nearby = nearbyBossPaths(player, data);
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
     * <p>Ported from the original's {@code ReduceCombatTime}, which is why a boss bar there visibly
     * moves when you swing at the boss rather than only drifting on its own:
     *
     * <pre>
     * float scale = (float)requiredFrames / 108000f;
     * int reduction = (int)((float)Main.rand.Next(6, 61) * scale);
     * if (reduction &lt; 1) reduction = 1;
     * </pre>
     *
     * <p>108000 frames is 1800 seconds at the original's 60 ticks a second, so at any sane
     * {@code existenceRequiredSeconds} the multiplier lands below one and every hit banks the floor of
     * one tick. That is deliberate: a hit is a nudge, not a shortcut, and it is kept as a floor rather
     * than rounded up so the pacing still matches. Randomised per hit so the bar does not read as a
     * deterministic counter, and clamped so a hit can never carry a boss past its total.
     *
     * <p>Gated on {@code active}, so switching the adaptation off stops banking as well as denying.
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
        int reduction = (int) ((6 + player.getRandom().nextInt(55)) * scale);
        if (reduction < 1) {
            reduction = 1;
        }
        data.bossCombatTicks.put(bossPath,
                Math.min(required, data.bossCombatTicks.getOrDefault(bossPath, 0) + reduction));
    }

    private static void noteBossEncounter(PlayerAdaption data, String bossPath) {
        data.bossCombatTicks.putIfAbsent(bossPath, 0);
    }

    private static final class NearbyBossCache {
        long stamp = -1;
        Set<String> paths = Set.of();
    }

    private static final Map<UUID, NearbyBossCache> NEARBY_BOSS_CACHE = new HashMap<>();

    private static Set<String> nearbyBossPaths(ServerPlayer player, PlayerAdaption data) {
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

    private static void grantExistenceAdaptation(ServerPlayer player, PlayerAdaption data, String bossPath) {
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
            // Fire is one-time now, so it lives in `adapted`, not `levels`. Writing a level
            // here would leave the guardian reward short of the fire immunity it promises.
            data.adapted.add(Concepts.ENV_FIRE);
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

        // Deliberately LAST, after every mutation and every message. It used to run first, before
        // anything at all, so anything that went wrong in a purely cosmetic send took the entire
        // reward with it -- no existenceAdapted, no maxed levels, no history, no completion event,
        // and no error the player could act on. The reward is the point; the cinematic is a hat on
        // it. Also silent when the boss has already despawned, which is exactly the case where the
        // original's "is the framing still right" check would have refused to play.
        startExistenceCinematic(player, bossPath);
    }

    /** Plays the reward cinematic, if the boss it is addressed to is still here to be framed. */
    private static void startExistenceCinematic(ServerPlayer player, String bossPath) {
        if (!AdaptionConfig.EXISTENCE_CINEMATIC_ENABLED.get()) {
            return;
        }
        LivingEntity boss = findNearbyBoss(player, bossPath);
        if (boss == null) {
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

    private static boolean isInDarkness(Player player) {
        return player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 4;
    }

    private static final int THERMAL_REGEN_INTERVAL_TICKS = 40;

    private static final Map<UUID, Float> PROXIMITY_HEAT_CACHE = new HashMap<>();

    public static void grantComboMutation(ServerPlayer player, PlayerAdaption data, String concept) {
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
        if (!data.active(Concepts.MUTATION_FLIGHT)
                && player.getY() >= AdaptionConfig.FLIGHT_ALTITUDE.get()
                && data.level(Concepts.contact(FLIGHT_MOB)) >= PlayerAdaption.MAX_LEVEL
                && data.isAdapted(Concepts.debuff(FLIGHT_LEVITATION))) {
            grantComboMutation(player, data, Concepts.MUTATION_FLIGHT);
            return;
        }
        if (!data.active(Concepts.MUTATION_FLIGHT) || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (data.isEnabled(Concepts.MUTATION_FLIGHT)) {
            if (FlightAbility.apply(player.getAbilities(), true)) {
                player.onUpdateAbilities();
            }
            return;
        }
        if (FlightAbility.apply(player.getAbilities(), false)) {
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
            case Concepts.DIMENSION_DESTROY -> net.minecraft.core.particles.ParticleTypes.SONIC_BOOM;
            default -> net.minecraft.core.particles.ParticleTypes.ENCHANT;
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
        data.impactLastFallDistance = grounded ? 0f : (float) player.fallDistance;
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
            target.hurt(source, damage);
            if (target.isRemoved() || !target.isAlive()) {
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
                        1, 0, 0.02, 0, 0);
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

    private static void applySurfaceEffects(ServerPlayer player, PlayerAdaption data) {
        if (!data.active(Concepts.ENV_LAVA) || !player.isInLava()) return;
        double waterEff = player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY)
                * (player.onGround() ? 1.0 : 0.5);
        double f4 = player.isSprinting() ? 0.9 : 0.8;
        double waterDecay = f4 + (0.54600006F - f4) * waterEff;
        double decayRatio = waterDecay / 0.5;
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(
                v.x * decayRatio,
                v.y + player.getGravity() * 3.0 / 16.0,
                v.z * decayRatio);
    }

    private static void applyEnvEffects(ServerPlayer player, PlayerAdaption data) {
        if (data.active(Concepts.ENV_DROWN)) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        if (data.active(Concepts.ENV_LAVA)) {
            player.clearFire();
        }

        if (data.active(Concepts.ENV_DARKNESS)) {
            if (player.tickCount % 40 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0, false, false));
            }
        } else {
            removeAdaptationNightVision(player);
        }
        if (data.active(Concepts.ENV_STARVE)
                && player.tickCount % 20 == 0) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20f);
            resetExhaustion(player.getFoodData());
        }
    }

    /**
     * Removes the long, hidden night vision granted by Env_Darkness when the adaptation is off.
     * Leaves other sources (potions, beacons) alone by matching the signature we apply.
     */
    private static void removeAdaptationNightVision(ServerPlayer player) {
        var effect = player.getEffect(MobEffects.NIGHT_VISION);
        if (effect == null) {
            return;
        }
        if (!effect.isVisible() && !effect.isAmbient() && effect.getAmplifier() == 0
                && effect.getDuration() > 20 * 30) {
            player.removeEffect(MobEffects.NIGHT_VISION);
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
        Identifier key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
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

    public static void completeTask(ServerPlayer player, PlayerAdaption data, String concept) {
        completeTaskUpTo(player, data, concept, -1);
    }

    public static void completeTaskUpTo(ServerPlayer player, PlayerAdaption data, String concept,
                                        int targetLevel) {
        applyGrant(player, data, concept, targetLevel);
        applyStats(player, data);

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
        data.tasks.clear();
        data.bossCombatTicks.clear();
        data.adversityActive = false;
        data.adversityTimer = 0;
        data.adversityCooldownTimer = 0;

        // One description of "everything", built from the live registries rather than a copy
        // written out here. The same list is the denominator of the Dimension Destroy gate, so the
        // two can never disagree about what "all" means.
        AdaptableConcepts everything = adaptableConcepts(serverPlayer.level());
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
     * <p>Rebuilding it is not free: the {@link net.minecraft.world.entity.MobCategory#MISC} probe
     * creates an entity. That is fine for a one-off grant, but the Dimension Destroy check runs
     * every tick, so the result is cached. The cache is dropped on {@link TagsUpdatedEvent}, because
     * the boss tag decides which {@code Existence_} concepts exist.
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

        for (ru.adaptionwheel.adapt.AdaptationDefinition definition
                : ru.adaptionwheel.adapt.AdaptationRegistry.allDefinitions()) {
            if (definition.leveled()) {
                leveled.put(definition.concept(), definition.maxLevel());
            } else {
                oneTime.add(definition.concept());
            }
        }

        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            Identifier key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key == null || effect.isBeneficial()) {
                continue;
            }
            // Debuffs are one-time. Putting them in levels too made adaptCount count the same
            // adaptation twice and kept WheelData.adaptCount() disagreeing with getAdaptCount().
            oneTime.add(Concepts.debuff(key.toString()));
        }

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null || !isLivingType(type, level)) {
                continue;
            }
            String path = key.toString();
            leveled.put(Concepts.contact(path), PlayerAdaption.MAX_LEVEL);
            leveled.put(Concepts.offense(path), PlayerAdaption.MAX_LEVEL);
            if (type != EntityTypes.PLAYER) {
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
     * category other than {@link net.minecraft.world.entity.MobCategory#MISC} is a mob. MISC is the
     * mixed bag -- the player, every projectile, item and vehicle, and also living things such as the
     * armour stand -- so it is probed by creating one. That probe is what keeps the answer right for
     * a modded mob that registered itself as MISC instead of picking a real category.
     */
    public static boolean isLivingType(EntityType<?> type, Level level) {
        if (type == EntityTypes.PLAYER) {
            return true;
        }
        if (type.getCategory() != net.minecraft.world.entity.MobCategory.MISC) {
            return true;
        }
        try {
            return type.create(level, EntitySpawnReason.COMMAND) instanceof LivingEntity;
        } catch (RuntimeException e) {
            // A modded entity whose constructor throws must not take the whole grant down with it:
            // skipping one type is better than granting none.
            return false;
        }
    }

    /**
     * Whether this entity type is a boss for the existence family.
     *
     * <p>The boss tag plus the three vanilla names, because vanilla's own boss tag is incomplete
     * for the mobs this mod reflects. Draconic Evolution's Chaos Guardian is hardcoded here because
     * it is not in any tag at all.
     */
    public static boolean isBossType(EntityType<?> type, String path) {
        return type.builtInRegistryHolder().is(Tags.EntityTypes.BOSSES)
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
     * -- while looking like an ordinary endgame threshold in the config. A percentage follows the
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
    public static void onServerStoppedResetAdaptableCache(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        cachedAdaptableConcepts = null;
    }

    @SubscribeEvent
    public static void onTagsUpdatedResetAdaptableCache(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
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
            FistMastery.forget(player.getUUID());
        MartialArts.forget(player.getUUID());
        }
        saveToItem(player, d);
        sync(player, d, true);
        return true;
    }

    private static void wipeWheelItem(ServerPlayer player, PlayerAdaption data) {
        ItemStack equipped = data.equippedStack;
        if (equipped != null) {
            equipped.set(ModDataComponents.WHEEL_DATA.get(), WheelData.EMPTY);
        } else {
            getWheelStack(player)
                    .ifPresent(stack -> stack.set(ModDataComponents.WHEEL_DATA.get(), WheelData.EMPTY));
        }
    }

    public static void debugReset(ServerPlayer player) {
        PlayerAdaption d = data(player);
        d.reset();
        applyStats(player, d);
        wipeWheelItem(player, d);
        sync(player, d, false);
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
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
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !player.level().isClientSide()
                && wearingWheel(player)) {
            MobEffectInstance instance = event.getEffectInstance();
            if (instance != null && !instance.getEffect().value().isBeneficial()) {
                String concept = Concepts.debuff(effectKey(instance));
                // active(), NOT isAdapted(): the panel switch sets `disabled`, and isAdapted only
                // answers "do you have it", so the denial kept firing after the adaptation was
                // switched off and the debuff could still never be received.
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
     *       to take it away, and the player kept flying, mid-air, indefinitely. Unequipping
     *       mid-air is also the case that strands them highest, which is why it is the one that
     *       gets reported.</li>
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

    /**
     * Kills banked toward the punching fist's next level, for the 1 Hz sync.
     *
     * <p>These used to travel only on {@code CombatFistProgressPayload}, which is sent when a kill
     * lands, so on relogin the mirror held zero and the row came back as an empty bar until the
     * player killed something. The per-kill payload stays -- that is what makes a kill appear
     * immediately -- and this is the recovery path.
     */
    private static int combatFistDone(PlayerAdaption data) {
        int tier = ru.adaptionwheel.server.MartialArts.trainingTier(data);
        if (tier < 0) {
            return 0;
        }
        return data.progress.getOrDefault(ru.adaptionwheel.data.Extras.punchingKey(tier), 0);
    }

    /** 0 means "maxed" for a stage, which is also how it reads for a stage that was never started. */
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

    private static void applyStats(ServerPlayer player, PlayerAdaption data) {
        int count = data.getAdaptCount();

        applyStat(player.getAttribute(Attributes.MAX_HEALTH), HP_MODIFIER,
                count * AdaptionConfig.BONUS_HP_PCT.get() / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyStat(player.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER,
                count * AdaptionConfig.BONUS_ARMOR_FLAT.get(), AttributeModifier.Operation.ADD_VALUE);

        // Apply the requested movement bonuses directly as attributes (no potion effects); both
        // scale linearly and stop increasing after the 400-adaptation progression cap.
        applyStat(player.getAttribute(Attributes.MOVEMENT_SPEED), ADAPTATION_COUNT_SPEED_MODIFIER,
                AdaptationProgression.movementSpeedBonus(count), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        applyStat(player.getAttribute(Attributes.JUMP_STRENGTH), ADAPTATION_COUNT_JUMP_MODIFIER,
                AdaptationProgression.jumpStrengthBonus(count), AttributeModifier.Operation.ADD_VALUE);

        boolean liquid = data.active(Concepts.ENV_LIQUID);
        applyStat(player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY), SWIM_MODIFIER,
                liquid ? 0.6 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        applyStat(player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), LIQUID_SPEED_MODIFIER,
                liquid ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);

        applyStat(player.getAttribute(Attributes.SUBMERGED_MINING_SPEED), SUBMERGED_MINING_MODIFIER,
                liquid ? 0.8 : 0.0, AttributeModifier.Operation.ADD_VALUE);

        boolean aquatic = AdaptionConfig.ENABLE_MUTATION_AQUATIC.get()
                && data.isEnabled(Concepts.MUTATION_AQUATIC)
                && data.active(Concepts.MUTATION_AQUATIC);
        applyStat(player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), AQUATIC_SWIM_SPEED_MODIFIER,
                aquatic ? AdaptionConfig.AQUATIC_SWIM_SPEED_BONUS.get() : 0.0, AttributeModifier.Operation.ADD_VALUE);
        applyStat(player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY), AQUATIC_SWIM_EFFICIENCY_MODIFIER,
                aquatic ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void applyStat(AttributeInstance attribute, Identifier id,
                                  double amount, AttributeModifier.Operation operation) {
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
        event.setXpCost((int) WHEEL_GOLD_COST);
        event.setMaterialCost(1);
    }



    private static java.util.List<String> buildAbilities(PlayerAdaption data) {
        java.util.List<String> abilities = new java.util.ArrayList<>();
        if (DiveAbility.unlocked(data)) {
            abilities.add(DiveAbility.ID);
        }
        return abilities;
    }

    public static void sync(ServerPlayer player, PlayerAdaption data, boolean wearing) {

        Map<String, Integer> existenceProgress = visibleBossProgress(player, data);
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
                ru.adaptionwheel.server.FistMastery.instabreakStance(player),
                ru.adaptionwheel.server.FistMastery.instabreakUnlocked(data),
                ru.adaptionwheel.server.FistMastery.tierProgress(player.getUUID(), data),
                ru.adaptionwheel.server.FistMastery.fistProgressTotal(data),
                combatFistDone(data),
                combatFistTotal(data),
                new ArrayList<>(data.disabled),
                buildAbilities(data)
        );
        AdaptionSyncPayload.sendTo(player, payload);
    }
}
