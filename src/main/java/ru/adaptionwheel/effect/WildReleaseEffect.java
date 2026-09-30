package ru.adaptionwheel.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Wild Release: the burst that paying an adaptation away buys.
 *
 * <p>Shedding an adaptation is a real, voluntary act, so it needs to feel like one. A vanilla
 * {@link MobEffect} rather than a set of hand-applied attribute modifiers, for three reasons that
 * all showed up as problems with the hand-rolled version: the modifiers arrive and leave with the
 * effect and cannot be left behind on logout, the player gets vanilla's own buff readout instead
 * of a mod-invented one, and a half-cleaned-up modifier is the exact class of bug that silently
 * halves a player's damage after a relog.</p>
 *
 * <p>Amounts are the <em>per amplifier</em> step, not the total: vanilla multiplies each one by
 * {@code amplifier + 1} through {@code AttributeTemplate.create}, so a single registration covers
 * every strength and shedding a high-level adaptation really is stronger than shedding a low one.</p>
 *
 * <p>Nothing here is a drawback, which is deliberate. The wheel is a promise of omnipotence, so
 * spending an adaptation has to hand back more than it takes, not less.</p>
 */
public class WildReleaseEffect extends MobEffect {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "wild_release");

    /** The loudest amplifier shedding can produce. Beyond this the bonuses stop being legible. */
    public static final int MAX_AMPLIFIER = 3;

    public WildReleaseEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFB03D8B);
        addAttributeModifier(Attributes.ATTACK_DAMAGE, id("damage"),
                0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, id("speed"),
                0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, id("move"),
                0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ARMOR, id("armor"),
                6.0, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, id("break"),
                0.30, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.MAX_HEALTH, id("health"),
                2.0, AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * One modifier id per attribute, not one shared id.
     *
     * <p>{@code addAttributeModifier} keys its template map by attribute, and vanilla removes each
     * modifier by its own id when the effect is cleared — a single shared id would let clearing
     * one attribute's modifier leave the others attached.</p>
     */
    private static ResourceLocation id(String suffix) {
        return ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "wild_release_" + suffix);
    }
}
