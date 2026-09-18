package ru.adaptionwheel.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Soft integration with Draconic Evolution's Chaos Guardian (Хранитель Хаоса).
 *
 * Purely string/registry based: no compile-time or runtime class dependency on
 * Draconic Evolution. Every helper degrades to a safe no-op when the mod is not
 * installed (registry lookups simply return null/false).
 *
 * The guardian fight consists of:
 * - the multi-part body ({@code draconicevolution:draconic_guardian} + PartEntity children),
 * - guardian fireballs ({@code guardian_projectile}, owned by the guardian),
 * - the laser beam ({@code guardian_laser}; its fully charged twin beam bypasses all
 *   damage events by calling {@code setHealth} directly — see {@link GuardianDirectDamage}),
 * - summoned guardian withers ({@code guardian_wither}) and crystals
 *   ({@code guardian_crystal}),
 * - the death implosion ({@code chaos_implosion}) and crystal push damage
 *   ({@code crystal_move}).
 */
public final class DraconicCompat {

    public static final String GUARDIAN_ID = "draconicevolution:draconic_guardian";
    public static final String GUARDIAN_WITHER_ID = "draconicevolution:guardian_wither";
    public static final String GUARDIAN_CRYSTAL_ID = "draconicevolution:guardian_crystal";

    /** Every damage type dealt by the guardian and its fight mechanics. */
    private static final Set<String> GUARDIAN_DAMAGE_IDS = Set.of(
            "draconicevolution:guardian",            // charge / body contact / wing sweep
            "draconicevolution:guardian_laser",      // laser beam
            "draconicevolution:guardian_projectile", // fireball detonation
            "draconicevolution:chaos_implosion",     // death implosion / chaos crystal placer
            "draconicevolution:crystal_move");       // guardian crystal push

    private static Identifier guardianKey() {
        return Identifier.tryParse(GUARDIAN_ID);
    }

    /** True when Draconic Evolution is loaded and the guardian entity type exists. */
    public static boolean available() {
        Identifier id = guardianKey();
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    /** Registry id of an entity ("namespace:path") or null. */
    public static String entityId(Entity entity) {
        if (entity == null) {
            return null;
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null ? key.toString() : null;
    }

    /** True for the Chaos Guardian itself (body parts resolve through {@link BossHelper}). */
    public static boolean isGuardian(LivingEntity entity) {
        return entity != null && GUARDIAN_ID.equals(entityId(entity));
    }

    /**
     * Canonical existence-adaptation path for any guardian-linked damage source:
     * the body, its parts, projectiles it owns, guardian withers and crystals.
     * All of them map to {@link #GUARDIAN_ID} so adapting to the guardian's existence
     * covers its entire arsenal, mirroring the original mod's ParentBossType chain.
     * Returns null when the source is not linked to the guardian.
     */
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

    /** True when the damage was dealt by the guardian or its fight machinery. */
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
