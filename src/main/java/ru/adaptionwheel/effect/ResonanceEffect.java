package ru.adaptionwheel.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Resonance: what wearing the wheel near other adapted players is worth.
 *
 * <p>The mod is entirely solitary by construction — one player, one wheel, one adaptation at a
 * time, and every gain comes from your own damage. This is the one effect that makes a second
 * adapted player matter, and it does so without any bookkeeping: stand near someone who has done
 * the work and you are stronger for it.</p>
 *
 * <p>Per amplifier, as with the Wild Release: vanilla scales each modifier by
 * {@code amplifier + 1}, so the whole range of "how many people are near me" is one registration.
 * Kept modest per rung on purpose — the point is an incentive to travel together, not a second
 * progression track that outpaces the adaptations themselves.</p>
 */
public class ResonanceEffect extends MobEffect {

    /** How many other adapted players it takes to reach the top rung. */
    public static final int MAX_AMPLIFIER = 3;

    public ResonanceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF3D8B8B);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, id("damage"),
                0.06, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.ARMOR, id("armor"),
                1.5, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, id("move"),
                0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        addAttributeModifier(Attributes.MAX_HEALTH, id("health"),
                1.0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static Identifier id(String suffix) {
        return Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "resonance_" + suffix);
    }
}
