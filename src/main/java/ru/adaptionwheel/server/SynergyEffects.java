package ru.adaptionwheel.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForgeMod;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.Synergies;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the synergies in {@link Synergies} actually do.
 *
 * <p>Split by where each one has to act, because there is no single place a combination could
 * live: attacks read the set in the damage pipeline, defences in the incoming one, retargeting in
 * {@code LivingSetAttackTargetEvent}, and the two mining synergies at break and drop time.</p>
 *
 * <p>The active set is recomputed once per second from the wearer tick and cached, because it can
 * only change when an adaptation completes — which happens at most once a second — while the damage
 * pipeline asks about it on every single hit.</p>
 *
 * <p>Every one of these is additive. Nothing here can cost the wearer anything, in keeping with the
 * wheel being a promise of omnipotence rather than a ledger.</p>
 */
public final class SynergyEffects {

    private SynergyEffects() {
    }

    // ------------------------------------------------------------------ cache

    private static final Map<UUID, Set<String>> ACTIVE = new HashMap<>();
    /** Guards Stormcall's recursion; the burst clears it on the next server tick. */
    private static final Set<UUID> CHAINED = new HashSet<>();
    /** Per-attacker cooldown so a multi-pass damage pipeline does not re-apply short effects. */
    private static final Map<UUID, Long> LAST_ON_FIRE = new HashMap<>();

    public static void forget(UUID id) {
        ACTIVE.remove(id);
        CHAINED.remove(id);
        LAST_ON_FIRE.remove(id);
    }

    public static void refresh(ServerPlayer player, PlayerAdaption data) {
        ACTIVE.put(player.getUUID(), new HashSet<>(Synergies.activeIds(data)));
    }

    public static boolean isActive(ServerPlayer player, Synergies.Synergy synergy) {
        Set<String> active = ACTIVE.get(player.getUUID());
        return active != null && active.contains(synergy.id());
    }

    public static Set<String> activeIds(UUID id) {
        Set<String> active = ACTIVE.get(id);
        return active == null ? Set.of() : active;
    }

    // ------------------------------------------------------------------ passive

    private static final ResourceLocation WALTZ_SWIM_SPEED =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "synergy_waltz_swim");

    /**
     * Drowned Waltz: while actually submerged, the air meter stays full and the water gets faster.
     *
     * <p>Refilling on the wearer tick rather than cancelling {@code LivingBreatheEvent}: the cancel
     * fights the vanilla bubble overlay, which then pops anyway, and this produces the same result
     * with nothing visible at all.</p>
     */
    public static void tickPassive(ServerPlayer player) {
        double swim = 0.0;
        if (isActive(player, Synergies.DROWNED_WALTZ) && player.isInWater()) {
            player.setAirSupply(player.getMaxAirSupply());
            swim = AdaptionConfig.AQUATIC_SWIM_SPEED_BONUS.get() + 1.5;
        }
        applyStat(player.getAttribute(NeoForgeMod.SWIM_SPEED), WALTZ_SWIM_SPEED, swim);
    }

    /** Skybreaker: the landing shockwave doubles, and arms from half the fall distance. */
    public static double impactRadius(ServerPlayer player, double configured) {
        return isActive(player, Synergies.SKYBREAKER) ? configured * 2.0 : configured;
    }

    public static double impactMinFall(ServerPlayer player, double configured) {
        return isActive(player, Synergies.SKYBREAKER) ? configured * 0.5 : configured;
    }

    // ------------------------------------------------------------------ attacking

    /**
     * Ashwalker, Glacierblood and Stormcall, applied together on a landed hit.
     *
     * <p>Rate-limited per attacker per second. The damage pipeline runs more than once per swing
     * (armour, absorption, resistance each re-enter it), so applying a four-second effect on every
     * pass would let a single hit light the target up several times over.</p>
     */
    public static void onHit(ServerPlayer attacker, LivingEntity target, DamageSource source) {
        Set<String> active = ACTIVE.get(attacker.getUUID());
        if (active == null || active.isEmpty() || attacker.level().isClientSide()) {
            return;
        }
        long now = attacker.level().getGameTime();

        if (active.contains(Synergies.ASHWALKER.id())) {
            Long last = LAST_ON_FIRE.get(attacker.getUUID());
            if (last == null || now - last >= 20) {
                LAST_ON_FIRE.put(attacker.getUUID(), now);
                // setRemainingFireTicks rather than setSecondsOnFire: the latter is on Entity in
                // some versions and not others, and this one is what the decompile actually has.
                target.setRemainingFireTicks(80);
            }
        }
        if (active.contains(Synergies.GLACIERBLOOD.id())) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, true, false));
        }
        if (active.contains(Synergies.STORMCALL.id())) {
            chainLightning(attacker, target, now);
        }
    }

    /**
     * Stormcall: the hit arcs to up to two more mobs nearby.
     *
     * <p>Dealt as {@code indirectMagic} rather than {@code playerAttack}, which does two jobs at
     * once. It is not a playerAttack, so the wearer's own Offense adaptation cannot multiply the
     * chained hit — otherwise a single strike at full offence would delete everything nearby at
     * that same multiplier. And it does not re-enter this method's caller, so there is no
     * recursion to guard; the per-tick set is a second line of defence, not the primary one.</p>
     */
    private static void chainLightning(ServerPlayer attacker, LivingEntity first, long now) {
        if (!CHAINED.add(attacker.getUUID())) {
            return;
        }
        attacker.server.execute(() -> CHAINED.remove(attacker.getUUID()));
        try {
            AABB box = attacker.getBoundingBox().inflate(6.0);
            List<LivingEntity> near = attacker.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != attacker && e != first && e.isAlive() && !e.isAlliedTo(attacker));
            int chained = 0;
            for (LivingEntity other : near) {
                if (chained >= 2) {
                    break;
                }
                other.hurt(attacker.damageSources().indirectMagic(attacker, first),
                        Math.max(1.0F, first.getHealth() * 0.08F));
                chained++;
            }
            if (chained > 0) {
                attacker.level().playSound(null, attacker.blockPosition(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4f, 2.0f);
                ((ServerLevel) attacker.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        first.getX(), first.getY(0.5), first.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
            }
        } finally {
            if (now < 0) {
                CHAINED.remove(attacker.getUUID());
            }
        }
    }

    // ------------------------------------------------------------------ defending

    /**
     * Unmaker: void damage heals you instead of hurting you.
     *
     * <p>Cancelling the incoming event is deliberate, and is the only place the heal survives: the
     * ordinary damage reduction runs after this point and would otherwise take it back out. It also
     * skips every other reduction for that hit, which is what "the void does not touch you" has
     * to mean if it is to be true rather than nearly true.</p>
     *
     * <p>Void is matched through {@link AdaptionCategory}, the mod's existing sixteen-way damage
     * taxonomy, instead of naming a vanilla damage type — that way a modded void source counts
     * too, for free.</p>
     */
    public static boolean tryAbsorbVoid(ServerPlayer player, DamageSource source) {
        if (!isActive(player, Synergies.UNMAKER)) {
            return false;
        }
        if (AdaptionCategory.VOID != AdaptionCategory.match(source)) {
            return false;
        }
        player.heal(4.0F);
        return true;
    }

    /** Gravebloom: part of what a mob does to you goes back into it. */
    public static void onHurtTaken(ServerPlayer player, LivingEntity attacker, float amount) {
        if (amount <= 0 || player.level().isClientSide() || !isActive(player, Synergies.GRAVEBLOOM)) {
            return;
        }
        if (attacker != null && attacker.isAlive() && !attacker.isAlliedTo(player)) {
            attacker.hurt(player.damageSources().playerAttack(player), amount * 0.25F);
        }
    }

    /**
     * Unseen: a mob cannot decide that you are the thing it was looking for.
     *
     * <p>Cancelling {@code LivingChangeTargetEvent} is the entire implementation. It is the one
     * point at which a mob acquires a player target, so cancelling there is both cheaper and more
     * thorough than any per-tick forgetting distance. A mob that already had you keeps you, which
     * is what makes this a reward for the pairing rather than an invisibility cloak.</p>
     *
     * <p>Only the mob-to-player direction is refused: the event also fires when a player retargets,
     * and that must keep working.</p>
     *
     * @return true when the target acquisition was refused
     */
    public static boolean refuseTarget(ServerPlayer player, LivingEntity candidate) {
        return isActive(player, Synergies.UNSEEN) && candidate == player;
    }

    // ------------------------------------------------------------------ mining

    /** Astral Mine: breaking a block pays a little back. */
    public static void onBlockBroken(ServerPlayer player, BlockState state) {
        if (isActive(player, Synergies.ASTRAL_MINE)) {
            player.heal(0.5F);
        }
    }

    /**
     * Goliath: the fist smelts what it breaks.
     *
     * <p>An explicit ore-to-ingot table rather than a smelting-recipe lookup. The fist already
     * knows exactly which materials it works, a recipe lookup drags in
     * {@code RecipeAccess}/{@code SingleRecipeInput} plumbing that differs between Minecraft
     * versions, and a list of six ores is something a reader can check at a glance.</p>
     *
     * <p>Applied to the drop list, not by replacing the block, so it composes with Fist Luck
     * instead of fighting it: the ore becomes an ingot and the tier's multiplier then applies to
     * the ingots.</p>
     */
    public static void smeltDrops(ServerPlayer player, List<ItemEntity> drops) {
        if (drops.isEmpty() || !isActive(player, Synergies.GOLIATH)) {
            return;
        }
        int total = 0;
        ItemStack result = null;
        for (ItemEntity drop : drops) {
            ItemStack smelted = smeltedResult(drop.getItem());
            if (smelted == null) {
                return; // anything not in the table: leave the whole drop list alone
            }
            if (result == null) {
                result = smelted;
            } else if (!ItemStack.isSameItemSameComponents(result, smelted)) {
                return; // mixed drops: smelting would destroy part of them
            }
            total += smelted.getCount();
        }
        if (result == null) {
            return;
        }
        result.setCount(total);
        drops.clear();
        drops.add(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), result));
    }

    private static final Map<String, ItemStack> SMELTING = Map.of(
            "minecraft:raw_iron", new ItemStack(Items.IRON_INGOT),
            "minecraft:raw_gold", new ItemStack(Items.GOLD_INGOT),
            "minecraft:raw_copper", new ItemStack(Items.COPPER_INGOT),
            "minecraft:nether_gold_ore", new ItemStack(Items.GOLD_NUGGET),
            "minecraft:ancient_debris", new ItemStack(Items.NETHERITE_SCRAP),
            "minecraft:crimson_ore", new ItemStack(Items.IRON_INGOT),
            "minecraft:warped_ore", new ItemStack(Items.IRON_INGOT)
    );

    private static ItemStack smeltedResult(ItemStack stack) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries
                .ITEM.getKey(stack.getItem());
        ItemStack result = id == null ? null : SMELTING.get(id.toString());
        return result == null ? null : result.copy();
    }

    // ------------------------------------------------------------------ attribute helper

    /**
     * Mirrors {@code AdaptionEvents.applyStat}, which is private there.
     *
     * <p>Permanent modifier plus a change guard, for the reason that class gives: a transient
     * modifier vanishes on logout and lets the game clamp saved health back to the vanilla max.</p>
     */
    private static void applyStat(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(id);
        if (existing != null && existing.amount() == amount) {
            return;
        }
        attribute.removeModifier(id);
        if (amount != 0) {
            attribute.addPermanentModifier(new AttributeModifier(id, amount,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
