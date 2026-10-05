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
import net.minecraft.world.entity.Mob;
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
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SynergyEffects {

    private SynergyEffects() {
    }

    private static final Map<UUID, Set<String>> ACTIVE = new HashMap<>();

    private static final Map<UUID, Map<String, Double>> STRENGTH = new HashMap<>();

    private static final Set<UUID> CHAINED = new HashSet<>();

    private static final Map<UUID, Long> LAST_ON_FIRE = new HashMap<>();

    /**
     * Drops everything this class remembers about a player, including the stat bonus it applied.
     *
     * <p>Forgetting the bookkeeping is not enough on its own: the swim-speed bonus lives in an
     * attribute modifier, not in the attachment, so an unequipped player kept it until they logged
     * out. Same shape as flight's {@code mayfly} — see {@link FlightAbility}. The one modifier this
     * class installs is named here rather than inferred, so adding a second one without revoking it
     * is a visible omission rather than a silent one.
     */
    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        ACTIVE.remove(id);
        STRENGTH.remove(id);
        CHAINED.remove(id);
        LAST_ON_FIRE.remove(id);
        player.getAttribute(NeoForgeMod.SWIM_SPEED).removeModifier(WALTZ_SWIM_SPEED);
    }

    public static void refresh(ServerPlayer player, PlayerAdaption data) {
        UUID id = player.getUUID();
        ACTIVE.put(id, new HashSet<>(Synergies.activeIds(data)));
        Map<String, Double> strengths = new HashMap<>();
        for (Synergies.Synergy synergy : Synergies.ALL) {
            strengths.put(synergy.id(), strengthOf(data, synergy));
        }
        STRENGTH.put(id, strengths);
    }

    public static double strengthOf(PlayerAdaption data, Synergies.Synergy synergy) {
        int max = PlayerAdaption.MAX_LEVEL;
        double sum = 0.0;
        int counted = 0;
        for (String[] requirement : synergy.requires()) {
            if (requirement[1].equals(Synergies.Requirement.MAXED.name())) {
                continue;
            }
            int level = data.levels.getOrDefault(requirement[0], 0);
            if (level <= 0) {
                continue;
            }
            sum += Math.min(1.0, (double) level / max);
            counted++;
        }
        double progress = counted == 0 ? Math.min(1.0, data.getAdaptCount() / 60.0) : sum / counted;
        return 1.0 + 3.0 * progress;
    }

    public static double strength(ServerPlayer player, Synergies.Synergy synergy) {
        Map<String, Double> strengths = STRENGTH.get(player.getUUID());
        if (strengths == null) {
            return 1.0;
        }
        return strengths.getOrDefault(synergy.id(), 1.0);
    }

    public static boolean isActive(ServerPlayer player, Synergies.Synergy synergy) {
        Set<String> active = ACTIVE.get(player.getUUID());
        return active != null && active.contains(synergy.id());
    }

    public static Set<String> activeIds(UUID id) {
        Set<String> active = ACTIVE.get(id);
        return active == null ? Set.of() : active;
    }

    private static final ResourceLocation WALTZ_SWIM_SPEED =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "synergy_waltz_swim");

    public static void tickPassive(ServerPlayer player) {
        double swim = 0.0;
        // The air refill is gated on the Drowning adaptation as well as on the synergy. The synergy
        // is derived from the wheel's contents, so it happily survives the player switching the
        // adaptation off -- and a synergy that refills your air every tick is drowning immunity by
        // another name. This is the second of two gates for that one effect; the first is the
        // damage cancellation in AdaptionEvents.
        if (isActive(player, Synergies.DROWNED_WALTZ) && player.isInWater()
                && AdaptionEvents.dataOf(player).active(Concepts.ENV_DROWN)) {
            player.setAirSupply(player.getMaxAirSupply());
            swim = (AdaptionConfig.AQUATIC_SWIM_SPEED_BONUS.get() + 1.5)
                    * strength(player, Synergies.DROWNED_WALTZ);
        }
        applyStat(player.getAttribute(NeoForgeMod.SWIM_SPEED), WALTZ_SWIM_SPEED, swim);
    }

    public static double impactRadius(ServerPlayer player, double configured) {
        return isActive(player, Synergies.SKYBREAKER)
                ? configured * strength(player, Synergies.SKYBREAKER) : configured;
    }

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
                ignite(target, Math.max(20,
                        (int) Math.round(40 * strength(attacker, Synergies.ASHWALKER))));
            }
        }
        if (active.contains(Synergies.GOLIATH.id())) {
            ignite(target, Math.max(20,
                    (int) Math.round(20 * strength(attacker, Synergies.GOLIATH))));
        }
        if (active.contains(Synergies.GLACIERBLOOD.id())) {
            double power = strength(attacker, Synergies.GLACIERBLOOD);
            int amplifier = Math.min(4, (int) Math.round(power));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    Math.max(20, (int) Math.round(20 * power)), amplifier, true, false));
        }
        if (active.contains(Synergies.STORMCALL.id())) {
            chainLightning(attacker, target, (int) strength(attacker, Synergies.STORMCALL));
        }
    }

    private static void chainLightning(ServerPlayer attacker, LivingEntity first, double power) {
        if (!CHAINED.add(attacker.getUUID())) {
            return;
        }
        attacker.server.execute(() -> CHAINED.remove(attacker.getUUID()));
        boolean scheduled = true;
        try {
            AABB box = attacker.getBoundingBox().inflate(6.0);
            List<LivingEntity> near = attacker.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != attacker && e != first && e.isAlive() && !e.isAlliedTo(attacker));
            int chained = 0;
            int limit = Math.min(5, Math.max(1, (int) Math.round(power)));
            for (LivingEntity other : near) {
                if (chained >= limit) {
                    break;
                }
                other.hurt(attacker.damageSources().indirectMagic(attacker, first),
                        Math.max(1.0F, (float) (first.getHealth() * 0.08 * power)));
                chained++;
            }
            if (chained > 0) {
                attacker.level().playSound(null, attacker.blockPosition(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4f, 2.0f);
                ((ServerLevel) attacker.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        first.getX(), first.getY(0.5), first.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
            }
        } finally {
            if (!scheduled) {
                CHAINED.remove(attacker.getUUID());
            }
        }
    }

    public static boolean tryAbsorbVoid(ServerPlayer player, DamageSource source) {
        if (!isActive(player, Synergies.UNMAKER)) {
            return false;
        }
        if (AdaptionCategory.VOID != AdaptionCategory.match(source)) {
            return false;
        }
        player.heal((float) (2.0 * strength(player, Synergies.UNMAKER)));
        return true;
    }

    public static void onHurtTaken(ServerPlayer player, LivingEntity attacker, float amount) {
        if (amount <= 0 || player.level().isClientSide() || !isActive(player, Synergies.GRAVEBLOOM)) {
            return;
        }
        if (attacker != null && attacker.isAlive() && !attacker.isAlliedTo(player)) {
            attacker.hurt(player.damageSources().playerAttack(player),
                    (float) (amount * 0.12 * strength(player, Synergies.GRAVEBLOOM)));
        }
    }

    public static boolean refuseTarget(ServerPlayer player, Mob mob) {
        if (!isActive(player, Synergies.UNSEEN) || !mob.isAlive()) {
            return false;
        }
        return mob.distanceToSqr(player) > 36.0 * strength(player, Synergies.UNSEEN);
    }

    private static void ignite(LivingEntity target, int ticks) {
        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), ticks));
    }

    public static void onBlockBroken(ServerPlayer player, BlockState state) {
        if (isActive(player, Synergies.ASTRAL_MINE)) {
            player.heal((float) (0.25 * strength(player, Synergies.ASTRAL_MINE)));
        }
    }

    public static void smeltDrops(ServerPlayer player, List<ItemEntity> drops) {
        if (drops.isEmpty() || !isActive(player, Synergies.GOLIATH)) {
            return;
        }
        int total = 0;
        ItemStack result = null;
        for (ItemEntity drop : drops) {
            ItemStack smelted = smeltedResult(drop.getItem());
            if (smelted == null) {
                return;
            }
            if (result == null) {
                result = smelted;
            } else if (!ItemStack.isSameItemSameComponents(result, smelted)) {
                return;
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
