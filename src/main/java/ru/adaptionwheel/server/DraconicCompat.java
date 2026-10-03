package ru.adaptionwheel.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

public final class DraconicCompat {

    public static final String GUARDIAN_ID = "draconicevolution:draconic_guardian";
    public static final String GUARDIAN_WITHER_ID = "draconicevolution:guardian_wither";
    public static final String GUARDIAN_CRYSTAL_ID = "draconicevolution:guardian_crystal";

    private static final Set<String> GUARDIAN_DAMAGE_IDS = Set.of(
            "draconicevolution:guardian",
            "draconicevolution:guardian_laser",
            "draconicevolution:guardian_projectile",
            "draconicevolution:chaos_implosion",
            "draconicevolution:crystal_move");

    private static ResourceLocation guardianKey() {
        return ResourceLocation.tryParse(GUARDIAN_ID);
    }

    public static boolean available() {
        ResourceLocation id = guardianKey();
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    public static String entityId(Entity entity) {
        if (entity == null) {
            return null;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null ? key.toString() : null;
    }

    public static boolean isGuardian(LivingEntity entity) {
        return entity != null && GUARDIAN_ID.equals(entityId(entity));
    }

    public static String guardianLinkedPath(Entity source) {
        LivingEntity root = BossHelper.resolveLiving(source);
        if (root == null) {
            return null;
        }
        String id = entityId(root);
        if (GUARDIAN_ID.equals(id) || GUARDIAN_WITHER_ID.equals(id) || GUARDIAN_CRYSTAL_ID.equals(id)) {
            return GUARDIAN_ID;
        }
        return null;
    }

    public static boolean isGuardianDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        if (GUARDIAN_DAMAGE_IDS.contains(source.typeHolder().getRegisteredName())) {
            return true;
        }
        return guardianLinkedPath(source.getDirectEntity()) != null
                || guardianLinkedPath(source.getEntity()) != null;
    }

    private DraconicCompat() {
    }
}
