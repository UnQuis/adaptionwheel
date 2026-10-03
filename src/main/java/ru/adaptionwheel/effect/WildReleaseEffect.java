package ru.adaptionwheel.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import ru.adaptionwheel.AdaptionWheel;

public class WildReleaseEffect extends MobEffect {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wild_release");

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

    private static Identifier id(String suffix) {
        return Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wild_release_" + suffix);
    }
}
