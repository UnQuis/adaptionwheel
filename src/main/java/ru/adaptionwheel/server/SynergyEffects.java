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

public final class SynergyEffects {

    private SynergyEffects() {
    }

    private static final Map<UUID, Set<String>> ACTIVE = new HashMap<>();

    private static final Set<UUID> CHAINED = new HashSet<>();

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

    private static final ResourceLocation WALTZ_SWIM_SPEED =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "synergy_waltz_swim");

    public static void tickPassive(ServerPlayer player) {
        double swim = 0.0;
        if (isActive(player, Synergies.DROWNED_WALTZ) && player.isInWater()) {
            player.setAirSupply(player.getMaxAirSupply());
            swim = AdaptionConfig.AQUATIC_SWIM_SPEED_BONUS.get() + 1.5;
        }
        applyStat(player.getAttribute(NeoForgeMod.SWIM_SPEED), WALTZ_SWIM_SPEED, swim);
    }

    public static double impactRadius(ServerPlayer player, double configured) {
        return isActive(player, Synergies.SKYBREAKER) ? configured * 2.0 : configured;
    }

    public static double impactMinFall(ServerPlayer player, double configured) {
        return isActive(player, Synergies.SKYBREAKER) ? configured * 0.5 : configured;
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

    public static void onHurtTaken(ServerPlayer player, LivingEntity attacker, float amount) {
        if (amount <= 0 || player.level().isClientSide() || !isActive(player, Synergies.GRAVEBLOOM)) {
            return;
        }
        if (attacker != null && attacker.isAlive() && !attacker.isAlliedTo(player)) {
            attacker.hurt(player.damageSources().playerAttack(player), amount * 0.25F);
        }
    }

    public static boolean refuseTarget(ServerPlayer player, LivingEntity candidate) {
        return isActive(player, Synergies.UNSEEN) && candidate == player;
    }

    public static void onBlockBroken(ServerPlayer player, BlockState state) {
        if (isActive(player, Synergies.ASTRAL_MINE)) {
            player.heal(0.5F);
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
