package ru.adaptionwheel.category;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

import java.util.Locale;

public enum AdaptionCategory {

    FALL(0xA8A8A8),
    WITHER(0x5A2A6A),
    FIRE(0xFF8C00),
    DROWN(0x3FB6FF),
    FREEZE(0x9FF5FF),
    LIGHTNING(0xFFE94F),
    EXPLOSION(0xFF5555),
    PROJECTILE(0xC0A878),
    MAGIC(0xE84FFF),
    MOB(0x55FF55),
    PLAYER(0xFFB0B0),
    CONTACT(0x8B3A3A),
    STARVE(0xE5FFB0),
    SUFFOCATE(0x8FA8B8),
    VOID(0x7700FF),
    OTHER(0xFFFFFF);

    public static final int COUNT = values().length;

    private final int color;

    AdaptionCategory(int color) {
        this.color = color;
    }

    public String getKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int getColor() {
        return color;
    }

    private static final TagKey<DamageType> IS_WITHER = tag("is_wither");
    private static final TagKey<DamageType> IS_FALL = tag("is_fall");
    private static final TagKey<DamageType> IS_FIRE = tag("is_fire");
    private static final TagKey<DamageType> IS_DROWNING = tag("is_drowning");
    private static final TagKey<DamageType> IS_FREEZING = tag("is_freezing");
    private static final TagKey<DamageType> IS_EXPLOSION = tag("is_explosion");
    private static final TagKey<DamageType> IS_PROJECTILE = tag("is_projectile");

    private static TagKey<DamageType> tag(String name) {
        return TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", name));
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, AdaptionCategory> MATCH_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static AdaptionCategory match(DamageSource source) {
        String registered = source.typeHolder().getRegisteredName();
        AdaptionCategory cached = MATCH_CACHE.get(registered);
        if (cached != null) {
            return cached;
        }
        AdaptionCategory result = computeMatch(source, registered);
        if (MATCH_CACHE.size() < 512) {
            MATCH_CACHE.put(registered, result);
        }
        return result;
    }

    private static AdaptionCategory computeMatch(DamageSource source, String registered) {
        int colon = registered.indexOf(':');
        String id = colon >= 0 ? registered.substring(colon + 1) : registered;

        if (source.is(IS_WITHER) || id.equals("wither") || id.equals("wither_skull")) return WITHER;
        if (source.is(IS_FALL) || id.equals("fall")) return FALL;
        if (source.is(IS_FIRE) || id.equals("hot_floor")) return FIRE;
        if (source.is(IS_DROWNING) || id.equals("drown")) return DROWN;
        if (source.is(IS_FREEZING) || id.equals("freeze")) return FREEZE;
        if (id.equals("lightning_bolt")) return LIGHTNING;
        if (source.is(IS_EXPLOSION)) return EXPLOSION;
        if (source.is(IS_PROJECTILE)) return PROJECTILE;
        if (id.equals("magic") || id.equals("indirect_magic") || id.equals("thorns") || id.equals("sonic_boom")) return MAGIC;
        if (id.equals("mob_attack") || id.equals("mob_attack_no_aggro") || id.equals("sting")) return MOB;
        if (id.equals("player_attack") || id.equals("mace_smash")) return PLAYER;
        if (id.equals("cacti") || id.equals("sweet_berry_bush") || id.equals("stalagmite")) return CONTACT;
        if (id.equals("starve")) return STARVE;
        if (id.equals("suffocation") || id.equals("in_wall") || id.equals("fly_into_wall")) return SUFFOCATE;
        if (id.equals("out_of_world")) return VOID;
        return OTHER;
    }
}
