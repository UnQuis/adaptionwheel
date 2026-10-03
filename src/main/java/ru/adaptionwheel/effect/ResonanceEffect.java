package ru.adaptionwheel.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import ru.adaptionwheel.AdaptionWheel;

public class ResonanceEffect extends MobEffect {

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
