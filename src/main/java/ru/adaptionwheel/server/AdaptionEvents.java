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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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

/**
 * Server-side adaptation logic.
 * Adaptations are stored on the Wheel ITEM (via Data Component), not on the player.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public class AdaptionEvents {

    private static final ResourceLocation HP_MODIFIER = ResourceLocation.fromNamespaceAndPath("adaptionwheel", "hp");
    private static final ResourceLocation ARMOR_MODIFIER = ResourceLocation.fromNamespaceAndPath("adaptionwheel", "armor");
    private static final ResourceLocation SWIM_MODIFIER = ResourceLocation.fromNamespaceAndPath("adaptionwheel", "swim");
    private static final ResourceLocation LIQUID_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath("adaptionwheel", "liquid_speed");
    private static final ResourceLocation SUBMERGED_MINING_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("adaptionwheel", "submerged_mining");
    private static final ResourceLocation AQUATIC_SWIM_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("adaptionwheel", "aquatic_swim_speed");
    private static final ResourceLocation AQUATIC_SWIM_EFFICIENCY_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("adaptionwheel", "aquatic_swim_efficiency");

    /** Health fraction at the moment of death; restored (scaled) once the wheel is worn again. */
    private static final Map<UUID, Float> PENDING_RESPAWN_HEALTH = new HashMap<>();
    /**
     * Players whose respawn has not been reconciled yet. The restore must only ever fire on the
     * first tick after a respawn — otherwise a stale death fraction could top the player up
     * again much later in the session.
     */
    private static final Set<UUID> PENDING_RESPAWN_ARMED = new HashSet<>();

    /** XP levels charged for the anvil upgrade wooden wheel + gold ingot -> Mahoraga Wheel. */
    private static final long WHEEL_GOLD_COST = 10L;

    /**
     * Per-tick memo for the Curios slot scan. The curios inventory query walks the
     * player's slot inventories and is by far the most expensive check in the hot
     * tick path; entries are valid only for the exact tick they were computed in,
     * so equipment changes are still noticed on the very next tick.
     */
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

    /** Public per-tick-cached check for other server subsystems (movement triggers, commands, API). */
    public static boolean isWearingWheel(Player player) {
        return wearingWheel(player);
    }

    /** Public access to a player's runtime adaptation data (server side only). */
    public static PlayerAdaption dataOf(ServerPlayer player) {
        return data(player);
    }

    /** Leveled-concept lookup for cross-side helpers; 0 unless the wheel is worn. */
    public static int conceptLevel(Player player, String concept) {
        if (!(player instanceof ServerPlayer serverPlayer) || !wearingWheel(serverPlayer)) {
            return 0;
        }
        return data(serverPlayer).level(concept);
    }

    private static Optional<ItemStack> getWheelStack(Player player) {
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(ModItems.MAHORAGA_WHEEL.get()))
                .map(result -> result.stack());
    }

    private static PlayerAdaption data(ServerPlayer player) {
        return player.getData(ru.adaptionwheel.data.AttachmentTypes.ADAPTION);
    }

    /** Server-side adaptation check for common code (mixins). */
    public static boolean hasAdaptation(Player player, String concept) {
        if (!(player instanceof ServerPlayer serverPlayer) || !wearingWheel(serverPlayer)) {
            return false;
        }
        return data(serverPlayer).isAdapted(concept);
    }

    private static void saveToItem(Player player, PlayerAdaption data) {
        getWheelStack(player).ifPresent(stack -> saveToStack(stack, data));
    }

    /**
     * Writes the player's adaptations onto a wheel stack.
     *
     * <p>Public because the Domain Stone hands the wheel to the player through a menu slot rather
     * than through the Curios slot, so at the moment of a purchase {@code getWheelStack} is
     * answering about a different item than the one being fed. The attachment stays the source of
     * truth — this only makes the stack the player is holding reflect it immediately instead of at
     * the next one-second tick.</p>
     */
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

    /**
     * Entity path for concept keys, unwrapping multi-part bodies (Ender Dragon,
     * Chaos Guardian) and projectile owners so hits on a part count for the boss.
     */
    private static String pathOf(LivingEntity target) {
        LivingEntity root = BossHelper.resolveLiving(target);
        return root != null ? entityPath(root.getType()) : entityPath(target.getType());
    }

    /**
     * Returns the adapted existence path matching this damage source, or null.
     * Covers vanilla bosses resolved through {@link BossHelper} and — when the
     * Chaos Guardian compat is enabled — its withers/crystals/projectiles which
     * all inherit from the guardian's existence adaptation.
     */
    private static String adaptedExistenceTarget(PlayerAdaption data, DamageSource source) {
        // Honour the module toggle at the point of use, not only where existence is EARNED:
        // otherwise turning `modules.existence` off left every already-granted boss immunity
        // fully active.
        if (!AdaptionConfig.ENABLE_EXISTENCE.get()) {
            return null;
        }
        LivingEntity boss = BossHelper.resolveBossFromSource(source);
        if (boss != null) {
            String path = entityPath(boss.getType());
            return data.existenceAdapted.contains(path) ? path : null;
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

    // ================= FULL IMMUNITY (prevents red flash) =================

    /**
     * LivingAttackEvent fires BEFORE hurtTime is set (no red flash).
     * We cancel the attack entirely for fully-immune sources so the player
     * never visually flinches or turns red.
     */
    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide
                && SynergyEffects.tryAbsorbVoid(player, event.getSource())) {
            // Unmaker: the void heals instead of hurting. Cancelled at the very top of hurt(),
            // because anything cancelled later is undone by the reduction that follows it.
            event.setCanceled(true);
            return;
        }
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);
        DamageSource source = event.getSource();
        AdaptionCategory category = AdaptionCategory.match(source);

        // Environment immunities (no red flash)
        if (category == AdaptionCategory.FALL && data.isAdapted(Concepts.ENV_FALL)) {
            event.setCanceled(true); return;
        }
        if (category == AdaptionCategory.STARVE && data.isAdapted(Concepts.ENV_STARVE)) {
            event.setCanceled(true); return;
        }
        if (category == AdaptionCategory.DROWN && data.isAdapted(Concepts.ENV_DROWN)) {
            event.setCanceled(true); return;
        }
        if (category == AdaptionCategory.FIRE && data.isAdapted(Concepts.ENV_LAVA)) {
            event.setCanceled(true); return;
        }
        if (category == AdaptionCategory.SUFFOCATE && data.isAdapted(Concepts.ENV_SUFFOCATE)) {
            event.setCanceled(true); return;
        }
        if (category == AdaptionCategory.VOID && data.isAdapted(Concepts.ENV_VOID)) {
            event.setCanceled(true); return;
        }
        if (category == AdaptionCategory.CONTACT && data.isAdapted(Concepts.ENV_THORNS)) {
            event.setCanceled(true); return;
        }

        // Contact immunity (configurable level, default 0 = off)
        int immunityLevel = AdaptionConfig.CONTACT_IMMUNITY_LEVEL.get();
        if (immunityLevel > 0) {
            Entity direct = source.getDirectEntity();
            if (direct instanceof LivingEntity livingDirect) {
                String mobPath = entityPath(livingDirect.getType());
                int contactLevel = data.level(Concepts.contact(mobPath));
                if (contactLevel >= immunityLevel) {
                    event.setCanceled(true); return;
                }
            }
        }

        // Existence adaptation: full immunity to the boss AND its projectiles/attacks (no red flash).
        // Chaos Guardian minions (withers/crystals) inherit the guardian's existence adaptation.
        //
        // The reflection has to happen HERE, in the incoming handler. LivingDamageEvent.Pre
        // fires from actuallyHurt(), which is never reached once this event is cancelled — so
        // a reflection branch down there could not execute.
        String adaptedBoss = adaptedExistenceTarget(data, source);
        if (adaptedBoss != null) {
            reflectAttack(player, source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity(),
                    event.getContainer().getNewDamage());
            event.setCanceled(true);
        }
    }

    // ================= DAMAGE TAKEN =================

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        DamageSource source = event.getSource();
        float newDamage = event.getNewDamage();

        // ---- Offense: the player is the attacker ----
        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof ServerPlayer attacker
                && event.getEntity() instanceof LivingEntity target
                && target != attacker
                && !REFLECTING.contains(target.getUUID())
                && wearingWheel(attacker)) {
            applyOffense(attacker, target, event);
        }

        // ---- Defense: the player is the victim ----
        if (!(event.getEntity() instanceof ServerPlayer player) || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);

        // Existence was already fully handled (reflected + cancelled) in onAttack, which runs
        // before actuallyHurt(). Reaching here with an adapted boss means the incoming stage
        // let it through, e.g. a mod that re-posts the event.
        if (adaptedExistenceTarget(data, source) != null) {
            event.setNewDamage(0);
            return;
        }

        Entity direct = source.getDirectEntity();

        if (data.adversityActive || data.adversityCooldownTimer > 0) return;

        AdaptionCategory category = AdaptionCategory.match(source);

        // ---- Full immunity once the hazard is adapted ----
        if (category == AdaptionCategory.FALL && data.isAdapted(Concepts.ENV_FALL)) {
            event.setNewDamage(0); return;
        }
        if (category == AdaptionCategory.STARVE && data.isAdapted(Concepts.ENV_STARVE)) {
            event.setNewDamage(0); return;
        }
        if (category == AdaptionCategory.DROWN && data.isAdapted(Concepts.ENV_DROWN)) {
            event.setNewDamage(0); return;
        }
        if (category == AdaptionCategory.FIRE && data.isAdapted(Concepts.ENV_LAVA)) {
            event.setNewDamage(0); return;
        }
        if (category == AdaptionCategory.CONTACT && data.isAdapted(Concepts.ENV_THORNS)) {
            event.setNewDamage(0); return;
        }
        if (category == AdaptionCategory.SUFFOCATE && data.isAdapted(Concepts.ENV_SUFFOCATE)) {
            event.setNewDamage(0); return;
        }
        if (category == AdaptionCategory.VOID && data.isAdapted(Concepts.ENV_VOID)) {
            event.setNewDamage(0); return;
        }

        List<String> concepts = new ArrayList<>();
        // Categories that have a dedicated Env_* adaptation train that instead, so the player
        // is not decoding two overlapping concepts for the same hazard.
        //
        // FIRE used to be in this list, which made Type_FIRE permanently 0 and therefore made
        // Thermal Mastery (gated on maxed Type_FIRE) unobtainable outside of the Chaos Guardian
        // and the All-Adaptation item. CONTACT / MOB / WITHER have no Env_ counterpart at all,
        // so excluding them left three more dead concepts. Both are now trainable.
        boolean envCategory = category == AdaptionCategory.FALL || category == AdaptionCategory.STARVE
                || category == AdaptionCategory.DROWN
                || category == AdaptionCategory.SUFFOCATE
                || category == AdaptionCategory.VOID;
        if (!envCategory) {
            concepts.add(Concepts.type(category));
        }

        String mobPath = null;
        if (direct instanceof LivingEntity livingDirect) {
            // Unwrap multi-part bodies, exactly like the offense/drop keys do. Using the raw part
            // type meant a hit on the Ender Dragon's neck trained Contact_minecraft:ender_dragon_part,
            // a concept nothing ever reads back.
            mobPath = entityPath(BossHelper.resolveLiving(livingDirect).getType());
            concepts.add(Concepts.contact(mobPath));
        }
        if (AdaptionConfig.ENABLE_EXISTENCE.get()) {
            LivingEntity bossFromSource = BossHelper.resolveBossFromSource(source);
            if (bossFromSource != null) {
                String bossPath = entityPath(bossFromSource.getType());
                noteBossEncounter(data, bossPath);
                data.bossCombatTicks.merge(bossPath, 1, Integer::sum);
            }
        }

        // ---- Damage reduction from every applicable adapted concept ----
        float reduction = 0f;
        for (String concept : concepts) {
            int level = data.level(concept);
            if (level > 0) {
                if (concept.startsWith("Contact_")) {
                    // Contact: use the dedicated contact protection table
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

        // Gravebloom: a quarter of what actually landed goes back into whatever did it. Placed
        // after the reduction, so the returned share is of real damage rather than of the
        // pre-mitigation number.
        SynergyEffects.onHurtTaken(player, event.getSource().getEntity() instanceof LivingEntity attacker
                ? attacker : null, newDamage);

        // ---- Healing on hit at level 5+ ----
        int bestLevel = 0;
        for (String concept : concepts) {
            bestLevel = Math.max(bestLevel, data.level(concept));
        }
        if (bestLevel >= 5) {
            double ratio = AdaptionConfig.defenseHealRatio(bestLevel) / 100.0;
            if (ratio > 0) {
                // Heal a share of what was actually absorbed, not of the raw incoming hit:
                // the config documents this as "% of damage absorbed".
                player.heal(newDamage * (float) ratio);
            }
        }
        if (bestLevel >= 8) {
            player.invulnerableTime = Math.max(player.invulnerableTime, 120);
        }

        // ---- Start / accelerate analysis tasks ----
        if (AdaptionConfig.ENABLE_DEFENSE.get()) {
            if (!envCategory) {
                startOrAccelerate(player, data, Concepts.type(category),
                        (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);
            }
            if (mobPath != null) {
                startOrAccelerate(player, data, Concepts.contact(mobPath),
                        (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);
            }
        }
        // Perception: every hit on the wearer trains a steady gaze (no hurt-cam shake).
        if (AdaptionConfig.ENABLE_PERCEPTION.get()) {
            startOrAccelerate(player, data, Concepts.PERCEP_STEADY_GAZE,
                    (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);
        }

        // ---- Environment tasks triggered by damage ----
        if (AdaptionConfig.ENABLE_ENVIRONMENT.get()) {
            if (category == AdaptionCategory.FALL) {
                double fallTime = AdaptionConfig.RAPID_FALL_ANALYSIS.get()
                        ? AdaptionConfig.FALL_ANALYSIS_SECONDS.get()
                        : AdaptionConfig.ENV_ANALYSIS_SECONDS.get();
                startTask(player, data, Concepts.ENV_FALL, (int) (fallTime * 20));
            }
            if (category == AdaptionCategory.FIRE && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
                startTask(player, data, Concepts.ENV_LAVA, (int) (AdaptionConfig.ENV_ANALYSIS_SECONDS.get() * 20));
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

        // ---- Adversity: survive a lethal hit ----
        if (tryAdversitySurvival(player, data, newDamage)) {
            // The helper already wrote the reduced health directly;
            // cancel what's left of this hit so vanilla doesn't apply it twice.
            event.setNewDamage(0f);
        }
    }

    /**
     * Adversity: when a hit would kill the wearer and the mechanism is off cooldown,
     * survive at 30 HP below the blow and start a 24 second mass analysis.
     *
     * @return true when the adversity trigger fired (damage was rewritten).
     */
    private static boolean tryAdversitySurvival(ServerPlayer player, PlayerAdaption data, float damage) {
        if (!AdaptionConfig.ENABLE_ADVERSITY.get()
                || player.isCreative()
                || data.adversityCooldownTimer > 0 || data.adversityActive
                || damage < player.getHealth()) {
            return false;
        }
        // Survive "30 HP below the blow", but never below 1 HP — with the vanilla
        // 20-point pool the old unclamped math (20 - 30 -> 0) meant guaranteed death
        // on the very first lethal hit, so the mechanic never fired for most wearers.
        player.setHealth(Math.max(1f, player.getHealth() - 30f));
        data.adversityActive = true;
        data.adversityTimer = 480; // 24 seconds analysis
        // Max, not assign: a Lv8 wearer was just granted 120 ticks of i-frames on this very hit.
        player.invulnerableTime = Math.max(player.invulnerableTime, 60);
        // Totem-style burst in front of the player's face.
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
        // Immediate sync so the client's face-flash/overlay starts on the trigger tick.
        sync(player, data, true);
        player.sendSystemMessage(Component.translatable("adaptionwheel.msg.adversity")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        return true;
    }

    // ================= DIRECT HEALTH WRITES (Chaos Guardian laser) =================

    /**
     * Handles the Chaos Guardian's fully charged laser, which applies damage by
     * calling {@code setHealth} directly — no NeoForge damage event fires for it.
     * Invoked from {@code GuardianLaserMixin} via {@link GuardianDirectDamage}.
     *
     * Everything that is not a health REDUCTION on a wheel-wearing player passes
     * through untouched. For an adapted player the hit is nullified entirely; for
     * everyone else the same reduction tables as the normal pipeline apply
     * (the laser damage type is tagged {@code is_explosion}, plus the guardian's
     * contact protection), including Adversity survival.
     */
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

        // Adapted to the guardian's existence: the beam does nothing at all.
        if (existenceEnabled && data.existenceAdapted.contains(DraconicCompat.GUARDIAN_ID)) {
            return;
        }
        if (!AdaptionConfig.ENABLE_CHAOS_GUARDIAN.get()) {
            target.setHealth(newHealth);
            return;
        }

        float raw = current - newHealth;

        // The bypassing hit still counts as guardian combat and feeds analysis tasks.
        if (existenceEnabled) {
            noteBossEncounter(data, DraconicCompat.GUARDIAN_ID);
        }
        int defenseTicks = (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20);
        startOrAccelerate(player, data, Concepts.type(AdaptionCategory.EXPLOSION), defenseTicks, true);
        startOrAccelerate(player, data, Concepts.contact(DraconicCompat.GUARDIAN_ID), defenseTicks, true);

        // During adversity (or its cooldown) the wearer is deliberately vulnerable.
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
            return; // survived through adversity — health already written by the helper
        }
        target.setHealth(Math.max(resulting, 0f));
    }

    private static void reflectAttack(ServerPlayer player, Entity direct, float damage) {
        // Find the actual source entity to reflect damage back to
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
        // The reflected hit is a playerAttack, so it re-enters onDamage and would pick up the
        // wearer's whole offense package (crit, armour pen, adaptCount multiplier) on top of
        // the reflect multiplier. Suppress it for exactly this one call.
        REFLECTING.add(living.getUUID());
        try {
            living.hurt(player.damageSources().playerAttack(player), damage * multiplier);
        } finally {
            REFLECTING.remove(living.getUUID());
        }
        // Push the attacker away from the wearer, like the original mod's contact reflection.
        living.knockback(1.2, player.getX(), player.getZ());
        player.invulnerableTime = Math.max(player.invulnerableTime, 10);
    }

    /** Targets currently receiving a reflected hit, so offense stacking does not re-trigger. */
    private static final Set<UUID> REFLECTING = new HashSet<>();

    // ================= OFFENSE =================

    /** Last game time each attacker was granted a Dimension Slash, to bound the re-roll chain. */
    private static final Map<UUID, Long> DIMENSION_SLASH_LAST = new HashMap<>();
    private static final int DIMENSION_SLASH_COOLDOWN_TICKS = 20;

    /**
     * A Dimension Slash is itself a playerAttack, so it re-enters {@code applyOffense} and
     * re-rolls the same chance. Deferred via {@code server.execute}, so a durable boss could
     * chain slashes without bound. One slash per second per attacker closes that off.
     */
    private static boolean claimDimensionSlash(ServerPlayer attacker) {
        long now = attacker.level().getGameTime();
        Long last = DIMENSION_SLASH_LAST.get(attacker.getUUID());
        if (last != null && now - last < DIMENSION_SLASH_COOLDOWN_TICKS) {
            return false;
        }
        DIMENSION_SLASH_LAST.put(attacker.getUUID(), now);
        return true;
    }

    private static void applyOffense(ServerPlayer attacker, LivingEntity target, LivingDamageEvent.Pre event) {
        PlayerAdaption data = data(attacker);
        // Unwrap multi-part bodies so hits on a Chaos Guardian part count for the guardian.
        String path = pathOf(target);
        String concept = Concepts.offense(path);
        int level = data.level(concept);
        float damage = event.getNewDamage();

        if (level > 0) {
            // flatDamageBonus is documented as a FLAT add, so add it. It used to be
            // `damage * max(1, bonus/10)`, which clamped every level below 7 to a x1.0
            // multiplier (no bonus at all) and turned 7-8 into a multiplier instead.
            float base = damage + (float) AdaptionConfig.offenseDamageBonus(level);
            double armor = target.getArmorValue();
            base += (float) (armor * (AdaptionConfig.offenseArmorPen(level) / 100.0));
            double crit = AdaptionConfig.offenseCrit(level) + data.getAdaptCount() * AdaptionConfig.BONUS_CRIT_PCT.get();
            if (attacker.getRandom().nextFloat() * 100f < crit) {
                base *= 1.5f;
            }
            base *= (float) (1.0 + data.getAdaptCount() * AdaptionConfig.BONUS_DAMAGE_PCT.get() / 100.0);
            event.setNewDamage(base);

            // Ashwalker / Glacierblood / Stormcall. After the offence maths has settled, so the
            // bonuses ride the final damage instead of a pre-reduction estimate.
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
            // accelerate = true: every landed hit should shave time off the analysis, which is
            // exactly what offenseHitAcceleration configures. Passing false left that option
            // read by nobody.
            startOrAccelerate(attacker, data, concept, timer, true,
                    (int) (AdaptionConfig.OFFENSE_ACCELERATION_SECONDS.get() * 20));
        }

        if (BossHelper.isBoss(target) && AdaptionConfig.ENABLE_EXISTENCE.get()) {
            noteBossEncounter(data, path);
            data.bossCombatTicks.merge(path, 1, Integer::sum);
        }
    }

    // ================= DROP ADAPTATION =================

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
        if (data.adversityActive || !AdaptionConfig.ENABLE_LOOT.get() || BossHelper.isBoss(dead)) return;

        String concept = Concepts.drop(pathOf(dead));
        data.killCounts.merge(concept, 1, Integer::sum);
        int newLevel = AdaptionConfig.dropLevelFromKills(data.kills(concept));
        if (newLevel > data.level(concept) && newLevel <= PlayerAdaption.MAX_LEVEL) {
            startDropTask(player, data, concept);
        }
    }

    /** Drop-rate adaptation also multiplies the experience a killed mob grants. */
    @SubscribeEvent
    public static void onExperienceDrop(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (event.getEntity().level().isClientSide) return;
        ServerPlayer player = event.getAttackingPlayer() instanceof ServerPlayer sp ? sp : null;
        if (player == null || !wearingWheel(player)) return;
        PlayerAdaption data = data(player);
        // Same boss guard as onMobDeath / onMobDrops: a boss drop adaptation must not hand out
        // bonus experience on top of its loot rolls.
        if (data.adversityActive || !AdaptionConfig.ENABLE_LOOT.get()
                || BossHelper.isBoss(event.getEntity())) return;

        String concept = Concepts.drop(pathOf(event.getEntity()));
        int level = data.level(concept);
        if (level <= 0) return;

        int base = event.getDroppedExperience();
        if (base <= 0) return;
        double bonusPct = AdaptionConfig.lootBonus(level);
        event.setDroppedExperience((int) Math.min(base * (1.0 + bonusPct / 100.0), Integer.MAX_VALUE / 4));
    }

    private static void startDropTask(ServerPlayer player, PlayerAdaption data, String concept) {
        if (data.level(concept) >= PlayerAdaption.MAX_LEVEL) return;
        // Deliberately ignores MAX_SIMULTANEOUS_ADAPTATIONS: the kill that crossed a drop
        // threshold has already been counted, and the task is only 1 s long. Bailing out here
        // used to silently swallow the level-up until the player killed that mob again.
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
        int level = data.level(concept);
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

        // Collect already-dropped items as fallback for mobs with empty/no loot tables
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

    // ================= LOGIN / DEATH =================

    /**
     * Avoid treating re-login while wearing the wheel as a fresh equip (clears tasks / reloads
     * stale item data).
     *
     * <p>Syncs unconditionally rather than only while wearing. The client mirror is static and the
     * periodic sync is gated on {@code wearing}, so a world where the player is not wearing the
     * wheel would otherwise never send anything and the client would keep the previous world's
     * levels indefinitely — which showed up as a freshly created world opening with the last
     * world's fist progress bar still on the HUD. One packet on login makes "this world starts
     * empty" true by construction instead of by the next world happening to wipe it.</p>
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        PlayerAdaption data = data(player);
        boolean wearing = wearingWheel(player);
        if (wearing) {
            data.wasWearing = true;
        }
        sync(player, data, wearing);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        PENDING_RESPAWN_HEALTH.remove(id);
        PENDING_RESPAWN_ARMED.remove(id);
        PROXIMITY_HEAT_CACHE.remove(id);
        WEARING_CACHE.remove(id);
        NEARBY_BOSS_CACHE.remove(id);
        LAST_VOICE_TICK.remove(id);
        REFLECTING.clear();
        DIMENSION_SLASH_LAST.remove(id);
        FistMastery.forget(id);
        SynergyEffects.forget(id);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Remember the health fraction so a respawn that full-heals into the vanilla
        // max (before our modifier is re-applied) can be restored once the wheel is worn.
        // Only wearers: a non-wearer has no adapted max to scale into, and recording them
        // used to hand a free top-up to anyone who later equipped the wheel.
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
        FistMastery.forget(player.getUUID());
        PENDING_RESPAWN_HEALTH.remove(player.getUUID());
        PENDING_RESPAWN_ARMED.remove(player.getUUID());
        applyStats(player, data);
        // Wipe the item through the stack captured while the wheel was worn. getWheelStack()
        // searches the Curios slot, so inside a !wearing branch it is always empty.
        wipeWheelItem(player, data);
    }

    /** Clears the wheel's stored data, preferring the stack reference seen while worn. */
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
        // Arm the pending health restore for exactly the first tick of the new life.
        if (PENDING_RESPAWN_HEALTH.containsKey(event.getEntity().getUUID())) {
            PENDING_RESPAWN_ARMED.add(event.getEntity().getUUID());
        }
    }

    // ================= TICK =================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) return;
        PlayerAdaption data = data(player);
        boolean wearing = wearingWheel(player);

        // ---- Item-bound adaptation: equip/unequip transitions ----
        if (wearing && !data.wasWearing) {
            data.tasks.clear();
            data.bossCombatTicks.clear();
            data.healingTimer = 0;
            data.adversityTimer = 0;
            data.adversityActive = false;
            data.adversityCooldownTimer = 0;
            loadFromItem(player, data);
        }
        if (!wearing && data.wasWearing) {
            // Running analyses are canceled on unequip and must NOT persist to the item.
            data.tasks.clear();
            data.bossCombatTicks.clear();
            // Capture the stack BEFORE nulling it: once the wheel left the slot,
            // getWheelStack() resolves to empty, so it can no longer write anything.
            ItemStack unequipped = data.equippedStack;
            data.equippedStack = null;
            if (unequipped != null) {
                if (AdaptionConfig.KEEP_DATA_ON_UNEQUIP.get()) {
                    saveToStack(unequipped, data);
                } else {
                    unequipped.set(ModDataComponents.WHEEL_DATA, WheelData.EMPTY);
                }
            }
            data.reset();
            FistMastery.forget(player.getUUID());
            PENDING_RESPAWN_HEALTH.remove(player.getUUID());
            PENDING_RESPAWN_ARMED.remove(player.getUUID());
            applyStats(player, data);
            sync(player, data, false);
        }

        // ---- Detect the equipped wheel being SWAPPED while worn (e.g., cursor-swapping
        // two wheels in the curios slot): persist onto the old wheel, adopt the new one's data.
        if (wearing && !player.isDeadOrDying()) {
            ItemStack equipped = getWheelStack(player).orElse(null);
            if (equipped != null && data.equippedStack != null && equipped != data.equippedStack) {
                saveToStack(data.equippedStack, data);
                data.reset();
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

        // ---- Adversity state ----
        if (data.adversityCooldownTimer > 0) data.adversityCooldownTimer--;
        if (data.adversityActive) {
            data.adversityTimer--;
            boolean finished = false;
            if (data.adversityTimer <= 0) {
                data.adversityActive = false;
                data.adversityCooldownTimer = (int) (AdaptionConfig.ADVERSITY_COOLDOWN_SECONDS.get() * 20);
                player.heal(player.getMaxHealth());
                // Tasks present when Adversity started were deliberately frozen for
                // the whole challenge. Resolve that snapshot now, regardless of how
                // much time remained on each progress bar.
                completeAllTasks(player, data);
                grantOneTime(player, data, Concepts.ADVERSITY);
                // Apply attribute changes immediately instead of waiting for the
                // next one-second passive-stat refresh.
                applyStats(player, data);
                playAdaptVoice(player, 1f, 1f);
                player.sendSystemMessage(Component.translatable("adaptionwheel.msg.adversity_done")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                finished = true;
            }
            data.wheelRotation += 0.2f;
            applyEnvEffects(player, data);
            if (!player.isDeadOrDying()) {
                // The early return below must not starve the client of live adversity state
                // (active flag + timer drive the overlay's bar and face-wheel).
                if (finished || player.tickCount % 20 == 0) sync(player, data, true);
            }
            return;
        }

        // ---- Analysis tasks ----
        Iterator<AdaptionTask> it = data.tasks.iterator();
        while (it.hasNext()) {
            AdaptionTask task = it.next();
            task.timer--;
            if (task.timer <= 0) {
                it.remove();
                completeTask(player, data, task.concept);
            }
        }

        // ---- Environment triggers by state ----
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
            // Block/light based checks run every 4 ticks: analysis timers are seconds long,
            // so a 200 ms detection delay costs nothing while saving three scans per tick.
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

        // ---- One-time env adaptation effects ----
        applyEnvEffects(player, data);
        applySurfaceEffects(player, data);

        // ---- Mutations: combos of completed adaptations ----
        if (!data.isAdapted(Concepts.MUTATION_THERMAL)
                && data.level(Concepts.type(AdaptionCategory.FIRE)) >= PlayerAdaption.MAX_LEVEL
                && data.isAdapted(Concepts.ENV_LAVA)) {
            grantComboMutation(player, data, Concepts.MUTATION_THERMAL);
        } else if (data.isAdapted(Concepts.MUTATION_THERMAL)) {
            tickThermalRegeneration(player, data);
        }
        if (AdaptionConfig.ENABLE_MUTATION_AQUATIC.get()) {
            if (!data.isAdapted(Concepts.MUTATION_AQUATIC)
                    && data.isAdapted(Concepts.ENV_LIQUID) && data.isAdapted(Concepts.ENV_DROWN)) {
                grantComboMutation(player, data, Concepts.MUTATION_AQUATIC);
            }
        }
        if (AdaptionConfig.ENABLE_MUTATION_IMPACT.get()) {
            if (!data.isAdapted(Concepts.MUTATION_IMPACT)
                    && data.isAdapted(Concepts.ENV_FALL) && data.isAdapted(Concepts.ENV_KNOCKBACK)) {
                grantComboMutation(player, data, Concepts.MUTATION_IMPACT);
            } else if (data.isAdapted(Concepts.MUTATION_IMPACT)) {
                tickImpactStomp(player, data);
            }
        }

        // ---- Transcendence: Dimension Destroy (original mod's ultimate) ----
        if (AdaptionConfig.DIMENSION_DESTROY_ENABLED.get()
                && !data.isAdapted(Concepts.DIMENSION_DESTROY)
                && data.getAdaptCount() > AdaptionConfig.DIMENSION_DESTROY_REQUIRED.get()) {
            grantComboMutation(player, data, Concepts.DIMENSION_DESTROY);
        }

        // ---- Wheel particles (port of the original mod's MahoragaWheelLayer dust) ----
        if (AdaptionConfig.ENABLE_WHEEL_PARTICLES.get()) {
            spawnWheelParticles(player, data);
        }

        // ---- Debuffs ----
        if (AdaptionConfig.ENABLE_DEBUFF.get() && !player.getActiveEffects().isEmpty()) {
            for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
                MobEffect mobEffect = effect.getEffect().value();
                if (mobEffect.isBeneficial()) continue;
                String path = effectKey(effect);
                String concept = Concepts.debuff(path);
                if (data.isAdapted(concept)) {
                    player.removeEffect(effect.getEffect());
                } else {
                    startTask(player, data, concept, (int) (AdaptionConfig.DEBUFF_ANALYSIS_SECONDS.get() * 20));
                }
            }
        }

        // ---- Injuries: analysis below HP threshold, then regeneration ----
        double hpPct = AdaptionConfig.REGEN_HP_THRESHOLD.get() / 100.0;
        int injureLevel = data.level(Concepts.SELF_DAMAGE);
        if (player.getHealth() <= player.getMaxHealth() * hpPct) {
            if (injureLevel < PlayerAdaption.MAX_LEVEL) {
                startTask(player, data, Concepts.SELF_DAMAGE, (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20));
            }
        }
        if (injureLevel > 0 && player.getHealth() < player.getMaxHealth()) {
            data.healingTimer++;
            // The table is documented as "HP per second", so tick it every second. It fired
            // once every 3 s, quietly dividing every configured value by three.
            if (data.healingTimer >= 20) {
                data.healingTimer = 0;
                player.heal(Math.max(1f, (float) AdaptionConfig.regenSpeed(injureLevel)));
            }
        }

        // ---- Existence: boss combat accumulation ----
        if (AdaptionConfig.ENABLE_EXISTENCE.get()) {
            accumulateBossCombat(player, data);
        }

        // ---- Wheel rotation animation ----
        if (data.tasks.isEmpty() && !data.adversityActive) {
            float diff = data.targetRotation - data.wheelRotation;
            data.wheelRotation += diff * 0.08f;
            if (Math.abs(diff) < 0.01f) data.wheelRotation = data.targetRotation;
        } else {
            data.wheelRotation += 0.2f;
        }

        // ---- Passive stats (guarded no-ops most ticks; a 1 s refresh is plenty) ----
        if (player.tickCount % 20 == 0) {
            applyStats(player, data);
        }
        restorePendingRespawnHealth(player);

        announceWheelTier(player, data);
        SynergyEffects.refresh(player, data);
        SynergyEffects.tickPassive(player);


        // ---- Sync every second (also persists tasks so a dropped wheel keeps running analyses) ----
        if (player.tickCount % 20 == 0) {
            // One cube scan, three consumers. The ritual auras, the Resonance rung and the
            // neighbouring-player count all want the same volume around this player, and
            // scanning it once per second per wearer is cheap; scanning it three times is not.
            RitualAuras.Auras auras = RitualAuras.scan(player);
            if (auras.any()) {
                RitualAuras.apply(player, data, auras);
            }
            Resonance.tick(player, auras);
            // Advancement criteria, evaluated against the same state as everything else above.
            // Polling rather than event-driven, so a login with a deep wheel, a shed, a transfer
            // and a tier crossing all light up without four separate call sites.
            ru.adaptionwheel.advancement.AdaptationTrigger.evaluate(player, data);
            // saveToItem after the totem acceleration, so an accelerated timer is the one that
            // gets persisted rather than being overwritten a tick later by the pre-acceleration
            // value the item still holds.
            saveToItem(player, data);
            sync(player, data, wearing);
        }
    }

    /**
     * Vanilla full-heals the respawned player into the VANILLA max health before our
     * modifier is back, clamping current HP. Restore the death-time fraction (scaled
     * to the adapted max) once, while the wheel is worn.
     */
    private static void restorePendingRespawnHealth(ServerPlayer player) {
        if (!PENDING_RESPAWN_ARMED.remove(player.getUUID())) {
            return;
        }
        Float fraction = PENDING_RESPAWN_HEALTH.remove(player.getUUID());
        if (fraction == null) {
            return;
        }
        // Re-apply the modifiers first: the target is derived from the adapted max health, and
        // the restore can fire on a tick where the 1 Hz applyStats pass has not run yet.
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

    /**
     * Accumulate existence progress only for bosses the player has already fought
     * (first hit dealt or received), while that boss type remains nearby.
     * A single entity scan per tick feeds every accumulated boss type at once.
     */
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

    private static void noteBossEncounter(PlayerAdaption data, String bossPath) {
        data.bossCombatTicks.putIfAbsent(bossPath, 0);
    }

    /**
     * Per-tick cache of the entity types recognized as bosses around the player.
     * The scan is the single most expensive existence operation; without the cache
     * it ran once per tracked boss per tick plus once more inside every sync.
     */
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
     * Grant existence adaptation to a boss.
     * Removes related tasks, sets contact + offense to max, grants one-time existence flag.
     */
    private static void grantExistenceAdaptation(ServerPlayer player, PlayerAdaption data, String bossPath) {
        String existenceConcept = Concepts.existence(bossPath);
        String contactConcept = Concepts.contact(bossPath);
        String offenseConcept = Concepts.offense(bossPath);

        // Remove running tasks related to this boss (superseded by existence)
        data.tasks.removeIf(t -> t.concept.equals(contactConcept) || t.concept.equals(offenseConcept));

        // Set contact and offense to max level
        data.levels.put(contactConcept, PlayerAdaption.MAX_LEVEL);
        data.levels.put(offenseConcept, PlayerAdaption.MAX_LEVEL);

        // Set max level for the damage type this boss primarily uses
        // Wither → WITHER, EnderDragon → MAGIC, Warden → MAGIC (sonic_boom),
        // Chaos Guardian → EXPLOSION + PROJECTILE + FIRE (fireballs, laser, implosion).
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

        // Grant the existence one-time adaptation
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
    }

    private static boolean isInDarkness(Player player) {
        return player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 4;
    }

    // ================= MUTATIONS (combo adaptations) =================

    private static final int THERMAL_REGEN_INTERVAL_TICKS = 40;
    /** Per-player cached proximity-heat result; the block scan runs only every 15 ticks. */
    private static final Map<UUID, Float> PROXIMITY_HEAT_CACHE = new HashMap<>();

    /**
     * Grants a combo mutation unlocked by conditions over other completed
     * adaptations. Pure combo concepts: no new damage type, no immunities —
     * each mutation carries its own passive ability.
     *
     * <p>Public because some combos are unlocked by an action rather than by a passive tick
     * condition — Fist Mastery for instance completes the instant a stone block is broken
     * bare-handed at max Labor.</p>
     */
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

    /** Celebration burst around the wearer, themed per mutation. */
    private static void spawnMutationBurst(ServerPlayer player, String concept) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        var particle = switch (concept) {
            case Concepts.MUTATION_THERMAL -> net.minecraft.core.particles.ParticleTypes.FLAME;
            case Concepts.MUTATION_AQUATIC -> net.minecraft.core.particles.ParticleTypes.SPLASH;
            case Concepts.MUTATION_IMPACT -> net.minecraft.core.particles.ParticleTypes.POOF;
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

    /**
     * Heat-scaled regeneration. temperatureFactor: 0 normal ambient, ~0.15 hot biome,
     * ~0.35 near fire/lava/magma, 0.7 on fire, 1.0 in lava. Heals a controlled amount
     * every THERMAL_REGEN_INTERVAL_TICKS; never at full HP.
     */
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
            // getOrDefault: no autoboxing NPE on the very first ticks before any entry exists.
            float proximity = PROXIMITY_HEAT_CACHE.getOrDefault(player.getUUID(), 0f);
            factor = proximity > 0f ? 0.35f : 0f;
            if (factor <= 0f && player.level().getBiome(player.blockPosition()).value().getBaseTemperature() >= 1.5f) {
                factor = 0.15f;
            }
        }
        return factor;
    }

    /**
     * Impact Mastery: a hard landing (fall distance above the configured
     * threshold) detonates into a shockwave that damages and hurls every living
     * creature around the wearer. The wearer themselves is untouched — they are
     * already adapted to falls and knockback by the parent adaptations.
     */
    private static void tickImpactStomp(ServerPlayer player, PlayerAdaption data) {
        boolean grounded = player.onGround();
        float lastFall = data.impactLastFallDistance;
        double minFall = SynergyEffects.impactMinFall(player, AdaptionConfig.IMPACT_STOMP_MIN_FALL.get());
        if (grounded && !data.impactWasOnGround && !player.isInWater() && !player.isInLava()
                && lastFall >= minFall) {
            triggerImpactShockwave(player, lastFall);
        }
        data.impactLastFallDistance = grounded ? 0f : player.fallDistance;
        data.impactWasOnGround = grounded || player.isInWater() || player.isInLava() || player.onClimbable();
    }

    private static void triggerImpactShockwave(ServerPlayer player, float fallDistance) {
        ServerLevel level = (ServerLevel) player.level();
        double radius = SynergyEffects.impactRadius(player, AdaptionConfig.IMPACT_STOMP_RADIUS.get());
        float damage = Math.max(2f, (float) ((fallDistance - AdaptionConfig.IMPACT_STOMP_MIN_FALL.get() * 0.5)
                * AdaptionConfig.IMPACT_STOMP_DAMAGE_PER_BLOCK.get()));

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player));
        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 away = target.position().subtract(player.position());
            double horizontal = Math.max(0.25, Math.sqrt(away.x * away.x + away.z * away.z));
            target.push(away.x / horizontal * 1.2, 0.5, away.z / horizontal * 1.2);
        }

        // Visual ring + low thump so the ability reads as weight, not an explosion.
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

    /** Cheap 5x4x5 box scan for nearby heat sources (fire blocks, lava, magma). */
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

    /**
     * Enchanting-table glyphs around the floating wheel: sparse idle shimmer that grows with the
     * adaptation count, denser shimmer plus glyphs converging into the wheel while an analysis is
     * running. Port of MahoragaWheelLayer.cs (dust 228, noGravity).
     *
     * <p>Swapped from END_ROD/CRIT to {@link net.minecraft.core.particles.ParticleTypes#ENCHANT}
     * because the glyph sprite actually reads as arcane. An end rod is a generic white streak
     * that could be coming off any source; the wheel is a magical analyser, and the runic
     * characters sell that where the streak did not.</p>
     */
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
            // count 0 with a non-zero speed spawns exactly one particle moving along that
            // vector, which is what makes the glyphs stream in toward the wheel.
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

    /**
     * Server-side mirror of the client lava-swim correction so both simulations
     * stay consistent. Cobweb is handled by WebBlockMixin on both sides.
     */
    private static void applySurfaceEffects(ServerPlayer player, PlayerAdaption data) {
        if (!data.isAdapted(Concepts.ENV_LAVA) || !player.isInLava()) return;
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

    /**
     * Apply all passive env adaptation effects. Called even during adversity.
     *
     * <p>{@code Env_Darkness} is deliberately absent: it is granted as a client-side gamma lift by
     * {@code client/DarknessGamma}, not as a status effect. A rolling night-vision window had to
     * be topped up as it ran down, and the last second of every window visibly flickered — the
     * screen went dark and came back once per cycle. Gamma has no duration, so it cannot
     * flicker, and it costs no effect packets.</p>
     */
    private static void applyEnvEffects(ServerPlayer player, PlayerAdaption data) {
        if (data.isAdapted(Concepts.ENV_DROWN)) {
            player.setAirSupply(player.getMaxAirSupply());
        }
        if (data.isAdapted(Concepts.ENV_LAVA)) {
            player.clearFire();
        }
        if (data.isAdapted(Concepts.ENV_STARVE) && player.tickCount % 20 == 0) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20f);
            resetExhaustion(player.getFoodData());
        }
    }

    /** Reset FoodData.exhaustionLevel to 0 via reflection. */
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
        return key != null ? key.getPath() : "unknown";
    }

    // ================= TASKS =================

    /**
     * Extra analysis time per already-reached level. Action-fed discomforts
     * (mining labor, combat rhythm) retrain 3x faster than passive exposure
     * concepts — each hit/break already feeds the task directly.
     */
    private static int levelPenaltyTicks(String concept) {
        return concept.startsWith("Mine_") || concept.startsWith("Combat_") ? 60 : 180;
    }

    /** Starts (or keeps) an analysis task; public so trigger subsystems can request analyses. */
    public static void startTask(ServerPlayer player, PlayerAdaption data, String concept, int timer) {
        // Adversity freezes the tasks that were already running. Do not let a
        // secondary trigger sneak a new task into the frozen set.
        if (data.adversityActive || data.isAdapted(concept) || data.level(concept) >= PlayerAdaption.MAX_LEVEL) return;
        // Wheel awakening: a family the wheel has not reached yet cannot be analysed at all.
        // Placed here rather than at each of the dozen trigger sites because this is the one
        // place every analysis in the mod goes through, so one check covers all of them — and a
        // second site is a second chance to forget it.
        if (AdaptionConfig.WHEEL_TIERS_ENABLED.get()
                && !ru.adaptionwheel.category.WheelTier.familyUnlocked(
                        concept, ru.adaptionwheel.category.WheelTier.forCount(data.getAdaptCount()))) {
            return;
        }
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

    /** Starts or accelerates an analysis task; public so trigger subsystems can request analyses. */
    public static void startOrAccelerate(ServerPlayer player, PlayerAdaption data, String concept, int baseTicks, boolean accelerate) {
        startOrAccelerate(player, data, concept, baseTicks, accelerate,
                (int) (AdaptionConfig.DEFENSE_ACCELERATION_SECONDS.get() * 20));
    }

    private static void startOrAccelerate(ServerPlayer player, PlayerAdaption data, String concept, int baseTicks,
                                          boolean accelerate, int accelerationTicks) {
        // Keep both the task list and each timer completely frozen during
        // Adversity; completion happens atomically when the challenge ends.
        if (data.adversityActive || data.isAdapted(concept) || data.level(concept) >= PlayerAdaption.MAX_LEVEL) return;
        AdaptionTask existing = null;
        for (AdaptionTask task : data.tasks) {
            if (task.concept.equals(concept)) { existing = task; break; }
        }
        if (existing == null) {
            if (data.tasks.size() >= AdaptionConfig.MAX_SIMULTANEOUS_ADAPTATIONS.get()) return;
            // A shed concept re-analyses in a fraction of the time, because the wheel remembers
            // what it already worked out. Applied here rather than at each trigger because this is
            // the one place every analysis goes through.
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
        // A one-time adaptation can be granted while its own analysis is still running (the
        // /grant command does exactly that). Don't announce it twice when the timer lands.
        if (data.isAdapted(concept) && !Concepts.isLevelBased(concept)) {
            return;
        }
        grantConceptLevel(player, data, concept);
    }

    /**
     * Advances a leveled concept by one level (or sets the flag for a one-time one) with the
     * full completion ceremony: message, heal, history, wheel rotation, voice, API event,
     * item save and sync. Public so progression that is fed by counters rather than by an
     * analysis timer — the fist tiers — can reuse it without faking an {@link AdaptionTask}.
     */
    public static void grantConceptLevel(ServerPlayer player, PlayerAdaption data, String concept) {
        grantConceptUpTo(player, data, concept, -1);
    }

    /**
     * The same ceremony, for a concept bought at a chosen level rather than advanced one step.
     *
     * @param targetLevel the level to reach, or {@code -1} to mean "one more than it is now" —
     *                    which is what an analysis completing means. Ignored for a
     *                    {@code Drop_NPC_} concept, whose level is derived from its kill count:
     *                    buying one to eight would leave that counter as decoration.
     */
    public static void grantConceptUpTo(ServerPlayer player, PlayerAdaption data, String concept,
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
        // An adaptation that grants a passive effect must grant it NOW, not whenever the next
        // refresh window happens to fall. Night vision landing up to two seconds late is the
        // difference between adapting to the dark and standing in a cave wondering why nothing
        // happened.
        applyEnvEffects(player, data);
        saveToItem(player, data);
        sync(player, data, true);
    }

    /**
     * Buys a {@code Drop_NPC_} adaptation by paying in kills rather than setting its level.
     *
     * <p>A drop level is <em>derived</em> from a kill count — everywhere else in the mod it is
     * {@code dropLevelFromKills(kills)} and nothing else — so a trade that assigned the level
     * directly would leave that counter lying about why the player holds it: an eighth level of
     * loot-luck having killed one chicken. Paying in kills keeps one rule, one table, and one
     * number.</p>
     *
     * @param targetLevel the level to reach; the kill count is topped up to whatever the table says
     *                    that level costs
     */
    public static void grantKillsToward(ServerPlayer player, PlayerAdaption data, String concept,
                                        int targetLevel) {
        int wanted = (int) Math.ceil(AdaptionConfig.lootKills(targetLevel));
        int have = data.kills(concept);
        if (wanted <= have) {
            // Already earned by that route; still run the ceremony so it announces itself rather
            // than silently doing nothing.
            grantConceptUpTo(player, data, concept, -1);
            return;
        }
        data.killCounts.merge(concept, wanted - have, Integer::sum);
        data.addHistory(concept);
        // -1 asks for "one more than now", which for a Drop concept means "recompute from kills".
        grantConceptUpTo(player, data, concept, -1);
    }

    /** Instantly max all adaptations. Requires the Mahoraga Wheel (All Adaption item). */
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

        String[] envConcepts = {
                Concepts.ENV_LAVA, Concepts.ENV_FALL, Concepts.ENV_KNOCKBACK, Concepts.ENV_LIQUID,
                Concepts.ENV_DARKNESS, Concepts.ENV_DROWN, Concepts.ENV_THORNS, Concepts.ENV_SUFFOCATE,
                Concepts.ENV_VOID, Concepts.ENV_STARVE, Concepts.ENV_ICE, Concepts.ENV_SLIME,
                Concepts.ENV_COBWEB
        };
        String[] moveConcepts = {
                Concepts.MOVE_SOUL_SAND, Concepts.MOVE_HONEY, Concepts.MOVE_POWDER_SNOW,
                Concepts.MOVE_BERRY_BUSH, Concepts.MOVE_BUBBLE_COLUMN
        };
        String[] discomfortLeveled = { Concepts.MINE_LABOR, Concepts.COMBAT_COOLDOWN };
        String[] discomfortOneTime = { Concepts.COMBAT_SHIELD_LOCK, Concepts.PERCEP_STEADY_GAZE,
                Concepts.COMBAT_SKILL_ISSUE, Concepts.DIMENSION_DESTROY };
        for (String concept : envConcepts) {
            data.adapted.add(concept);
            data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
            data.addHistory(concept);
        }
        for (String concept : moveConcepts) {
            data.adapted.add(concept);
            data.addHistory(concept);
        }
        for (String concept : discomfortLeveled) {
            data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
            data.addHistory(concept);
        }
        for (String concept : discomfortOneTime) {
            data.adapted.add(concept);
            data.addHistory(concept);
        }

        data.adapted.add(Concepts.ADVERSITY);
        data.levels.put(Concepts.ADVERSITY, PlayerAdaption.MAX_LEVEL);
        data.addHistory(Concepts.ADVERSITY);
        data.levels.put(Concepts.SELF_DAMAGE, PlayerAdaption.MAX_LEVEL);
        data.addHistory(Concepts.SELF_DAMAGE);
        data.adapted.add(Concepts.MUTATION_THERMAL);
        data.addHistory(Concepts.MUTATION_THERMAL);
        data.adapted.add(Concepts.MUTATION_AQUATIC);
        data.addHistory(Concepts.MUTATION_AQUATIC);
        data.adapted.add(Concepts.MUTATION_IMPACT);
        data.addHistory(Concepts.MUTATION_IMPACT);
        data.adapted.add(Concepts.MUTATION_FIST);
        data.addHistory(Concepts.MUTATION_FIST);
        for (int tier = 0; tier < ru.adaptionwheel.category.FistTiers.TIER_COUNT; tier++) {
            data.levels.put(ru.adaptionwheel.category.FistTiers.concept(tier), PlayerAdaption.MAX_LEVEL);
            data.addHistory(ru.adaptionwheel.category.FistTiers.concept(tier));
        }

        for (AdaptionCategory category : AdaptionCategory.values()) {
            String concept = Concepts.type(category);
            data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
            data.addHistory(concept);
        }

        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            if (effect.isBeneficial()) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key == null) {
                continue;
            }
            String concept = Concepts.debuff(key.getPath());
            data.adapted.add(concept);
            data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
            data.addHistory(concept);
        }

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null) {
                continue;
            }
            String path = key.toString();
            data.levels.put(Concepts.contact(path), PlayerAdaption.MAX_LEVEL);
            data.levels.put(Concepts.offense(path), PlayerAdaption.MAX_LEVEL);
            data.levels.put(Concepts.drop(path), PlayerAdaption.MAX_LEVEL);
            data.addHistory(Concepts.contact(path));

            if (type.is(Tags.EntityTypes.BOSSES)
                    || DraconicCompat.GUARDIAN_ID.equals(path)
                    || path.contains("wither")
                    || path.contains("ender_dragon")
                    || path.contains("warden")) {
                String existenceConcept = Concepts.existence(path);
                data.adapted.add(existenceConcept);
                data.existenceAdapted.add(path);
                data.addHistory(existenceConcept);
            }
        }

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

    /** Per-player throttle so stacked completions can't overlap into a wall of sound. */
    private static final Map<UUID, Long> LAST_VOICE_TICK = new HashMap<>();
    private static final int VOICE_COOLDOWN_TICKS = 15;

    private static void playAdaptVoice(ServerPlayer player, float pitch, float volume) {
        playSoundThrottled(player, ModSounds.ADAPT_VOICE.get(), pitch, volume);
    }

    /**
     * Solemn completion voice for a genuinely completed adaptation.
     *
     * <p>This intentionally uses the same adaptation voice as an ordinary
     * completion, only with a lower pitch. Keep one helper for level 8,
     * existence, combo unlocks, and the All Adaptations consumable so all of
     * those milestones use the same adaptation sound with a weightier tone.</p>
     */
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
        // Idempotent: a third-party caller reaching the public API must not be able to farm
        // the heal / voice / rotation / completion event by re-granting an existing flag.
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

    // ================= DEBUG SUPPORT (used by /adaptionwheel) =================

    /** Grants a concept surgically (no heal/voice/event): leveled concepts take the given level. */
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

    /**
     * Drops a single adaptation: clears the one-time flag or zeroes the level, and tidies up
     * anything that was counting on it. The counterpart to {@code debugGrant} — without it the
     * only way to undo a grant was a full {@code reset}, which throws away every other
     * adaptation too.
     *
     * @return {@code true} if something was actually removed.
     */
    public static boolean debugUngrant(ServerPlayer player, String concept) {
        PlayerAdaption d = data(player);
        boolean had = d.adapted.remove(concept);
        Integer level = d.levels.remove(concept);
        if (!had && level == null) {
            return false;
        }
        d.invalidateAdaptCount();
        applyStats(player, d);
        // Recomputing stat modifiers needs the level gone first, and passive effects have to be
        // re-evaluated or a removed adaptation keeps its night vision for the rest of the window.
        applyEnvEffects(player, d);
        // The fist's stance and block counter are runtime-only state keyed off the mutation; if
        // the mutation is what went away, they have to go with it or the next unlock starts with
        // a stale tally.
        if (ru.adaptionwheel.category.Concepts.MUTATION_FIST.equals(concept)) {
            FistMastery.forget(player.getUUID());
        }
        saveToItem(player, d);
        sync(player, d, true);
        return true;
    }

    /** Full wipe of both player attachment and wheel item data. */
    public static void debugReset(ServerPlayer player) {
        PlayerAdaption d = data(player);
        d.reset();
        applyStats(player, d);
        applyEnvEffects(player, d);
        FistMastery.forget(player.getUUID());
        wipeWheelItem(player, d);
        sync(player, d, false);
    }

    // ================= KNOCKBACK =================

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (!wearingWheel(player)) {
            return;
        }
        PlayerAdaption data = data(player);
        if (data.isAdapted(Concepts.ENV_KNOCKBACK)) {
            // Full adaptation: impacts cannot move the wearer at all.
            event.setCanceled(true);
        } else if (AdaptionConfig.ENABLE_ENVIRONMENT.get() && !data.adversityActive) {
            // Being knocked around trains the adaptation (it was previously unobtainable).
            startOrAccelerate(player, data, Concepts.ENV_KNOCKBACK,
                    (int) (AdaptionConfig.DEFENSE_ANALYSIS_SECONDS.get() * 20), true);
        }
    }

    // ================= DEBUFF IMMUNITY =================

    /**
     * Env_Liquid: while submerged, vanilla also divides dig speed by 5 when not on
     * ground (swimming). Cancel that part so adapted players mine underwater at
     * land parity (the base water penalty is already removed via attribute).
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        // Fist Mastery first: it replaces the bare-hand base speed with the equivalent tool's
        // speed, so Mine_Labor's trained multiplier has to come after it to compose correctly.
        float speed = FistMastery.breakSpeed(player, event.getNewSpeed(), event.getState());
        // Mine_Labor: trained mining speed bonus (any block, any conditions).
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
        // Mutation_Aquatic: underwater mining is markedly faster even when grounded.
        if (SurfaceAdaptations.hasAquaticMastery(player)) {
            event.setNewSpeed(event.getNewSpeed() * 1.5f);
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
                if (data(player).isAdapted(concept)) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                }
            }
        }
    }

    // ================= PASSIVE STATS =================

    /**
     * Announces a wheel awakening the moment it happens.
     *
     * <p>Reads the derived tier and compares it with the last announced one, so it fires on the
     * tick the count crosses a threshold and never again until the next crossing. The threshold
     * message names the family that just opened, because "you are now tier 3" tells the player
     * nothing about what to go and do — "Contact defence is awake" does.</p>
     */
    private static void announceWheelTier(ServerPlayer player, PlayerAdaption data) {
        if (!AdaptionConfig.WHEEL_TIERS_ENABLED.get() || !wearingWheel(player)) {
            return;
        }
        int tier = ru.adaptionwheel.category.WheelTier.forCount(data.getAdaptCount());
        if (tier == data.lastTierAnnounced) {
            return;
        }
        // A wheel swap or a logout brings the marker back to -1 with the data intact, so the
        // player is re-told their tier. That is wanted, not a repeat: they just put the wheel on.
        data.lastTierAnnounced = tier;
        if (tier <= 0) {
            return;
        }
        int next = ru.adaptionwheel.category.WheelTier.nextThreshold(tier);
        player.displayClientMessage(net.minecraft.network.chat.Component
                .translatable("adaptionwheel.msg.wheel_tier",
                        Component.translatable(ru.adaptionwheel.category.WheelTier.nameKey(tier)))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        if (next > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component
                    .translatable("adaptionwheel.msg.wheel_tier_next", next)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        player.level().playSound(null, player.blockPosition(), ModSounds.REF.get(),
                SoundSource.PLAYERS, 0.7f, 1.6f);
        spawnWheelParticles(player, data);
        sync(player, data, true);
    }

    private static void applyStats(ServerPlayer player, PlayerAdaption data) {
        int count = data.getAdaptCount();
        // Wheel awakening stacks on top of the per-adaptation bonus. Separate rather than folded
        // into `count` so that a tier is a legible step up in its own right: reaching Resonant
        // should feel like something, not like quietly owning six more adaptations.
        int tier = AdaptionConfig.WHEEL_TIERS_ENABLED.get()
                ? ru.adaptionwheel.category.WheelTier.forCount(count) : 0;
        double tierBonus = ru.adaptionwheel.category.WheelTier.statBonus(tier);
        // Permanent (persisted) modifiers: transient ones vanish on logout, letting
        // the game clamp saved health down to the vanilla max before we re-apply.
        applyStat(player.getAttribute(Attributes.MAX_HEALTH), HP_MODIFIER,
                (count * AdaptionConfig.BONUS_HP_PCT.get() / 100.0) + tierBonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        applyStat(player.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER,
                count * AdaptionConfig.BONUS_ARMOR_FLAT.get() + (float) (tierBonus * 20.0),
                AttributeModifier.Operation.ADD_VALUE);
        // Env_Liquid: comfortable swimming slightly BELOW land pace (tuned down per feedback;
        // Aquatic Mastery on top restores full dolphin-grade speed).
        boolean liquid = data.isAdapted(Concepts.ENV_LIQUID);
        applyStat(player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY), SWIM_MODIFIER,
                liquid ? 0.6 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        applyStat(player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), LIQUID_SPEED_MODIFIER,
                liquid ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        // Env_Liquid: no underwater mining penalty (same mechanism as Aqua Affinity).
        applyStat(player.getAttribute(Attributes.SUBMERGED_MINING_SPEED), SUBMERGED_MINING_MODIFIER,
                liquid ? 0.8 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        // Mutation_Aquatic: dolphin-grade swimming on top of the liquid baseline.
        boolean aquatic = AdaptionConfig.ENABLE_MUTATION_AQUATIC.get() && data.isAdapted(Concepts.MUTATION_AQUATIC);
        applyStat(player.getAttribute(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED), AQUATIC_SWIM_SPEED_MODIFIER,
                aquatic ? AdaptionConfig.AQUATIC_SWIM_SPEED_BONUS.get() : 0.0, AttributeModifier.Operation.ADD_VALUE);
        applyStat(player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY), AQUATIC_SWIM_EFFICIENCY_MODIFIER,
                aquatic ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
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

    // ================= ANVIL =================

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

    // ================= SYNC =================

    private static void sync(ServerPlayer player, PlayerAdaption data, boolean wearing) {
        syncAdaption(player, data, wearing);
    }

    /** Public alias so runtime-stance handlers can push a correction without duplicating the payload. */
    public static void syncAdaption(ServerPlayer player, PlayerAdaption data, boolean wearing) {
        // Only send existence progress for bosses that are nearby and not yet adapted.
        // Reuses the per-tick proximity scan instead of issuing its own entity query.
        Map<String, Integer> existenceProgress = null;
        if (AdaptionConfig.ENABLE_EXISTENCE.get() && !data.bossCombatTicks.isEmpty()) {
            Set<String> nearby = nearbyBossPaths(player);
            for (Map.Entry<String, Integer> entry : data.bossCombatTicks.entrySet()) {
                String path = entry.getKey();
                if (data.existenceAdapted.contains(path) || !nearby.contains(path)) continue;
                int ticks = entry.getValue();
                if (ticks > 0) {
                    if (existenceProgress == null) existenceProgress = new HashMap<>();
                    existenceProgress.put(path, ticks);
                }
            }
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
                FistMastery.instabreakStance(player),
                FistMastery.tierProgress(player.getUUID()),
                fistProgressTotal(data)
        );
        AdaptionSyncPayload.sendTo(player, payload);
    }

    /** Blocks needed for the wearer's next fist level; {@code 0} when nothing is in progress. */
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