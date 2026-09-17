package ru.adaptionwheel.adapt;

import net.minecraft.network.chat.Component;

/**
 * High-level grouping of adaptations, used by the GUI, HUD sorting and the
 * debug command. Domains are purely organizational — they never change how a
 * concept is stored or synced, so adding a new domain is save-compatible.
 *
 * <p>The mapping from concept key to domain lives in {@link AdaptationRegistry}.</p>
 */
public enum AdaptationDomain {

    /** Incoming damage patterns: Type_*, Proj_*, DamageClass_*. */
    DAMAGE("damage", 0xFFFFA500),
    /** Harmful status effects: Debuff_*. */
    EFFECT("effect", 0xFFFFFF00),
    /** Block-imposed movement restrictions: Move_* (soul sand, honey, ...). */
    MOVEMENT("movement", 0xFF90EE90),
    /** Physics forces: knockback, ice friction, slime bounce, cobweb drag, falls. */
    PHYSICS("physics", 0xFF6CA6E0),
    /** Mining / interaction-with-blocks restrictions. */
    MINING("mining", 0xFFB0B0B0),
    /** Environmental hazards: lava, drowning, void, darkness, starvation... */
    ENVIRONMENT("environment", 0xFF00FFFF),
    /** Combat-side restrictions (attack cooldown, shield rules...). */
    COMBAT("combat", 0xFFFF6B6B),
    /** Perception limitations: darkness, blindness, obscured vision. */
    PERCEPTION("perception", 0xFFE0B0FF),
    /** Per-entity adaptations: Contact_*, Offense_NPC_*, Drop_NPC_*. */
    ENTITY("entity", 0xFFDDA0DD),
    /** Absolute threats posed by specific beings' existence. */
    EXISTENCE("existence", 0xFFFFFFFF),
    /** Everything that does not fit elsewhere: mutations, adversity, injuries. */
    SPECIAL("special", 0xFF9370DB);

    private final String key;
    private final int color;

    AdaptationDomain(String key, int color) {
        this.key = key;
        this.color = color;
    }

    /** Translation key suffix under "adaptionwheel.domain.". */
    public String getKey() {
        return key;
    }

    public Component translation() {
        return Component.translatable("adaptionwheel.domain." + key);
    }

    /** Default display color for concepts in this domain. */
    public int getColor() {
        return color;
    }
}
